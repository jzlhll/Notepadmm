package com.allan.atools.tools.modulenotepad.log

import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.CancellationException

/** 已编辑的加载正文与磁盘尾部组成一个固定版本，供搜索和完整保存共用。 */
class LogDocumentSnapshot(
    val path: Path,
    val encoding: String,
    val readState: LogReadState?,
    val prefix: String,
    private val size: Long,
    private val lastModified: Long,
    private val fileKey: Any? = null
) {
    fun openReader(cancelled: () -> Boolean): Reader {
        val source = LogFileSource(path, encoding)
        try {
            check(source.size >= size && (source.size > size || source.lastModified == lastModified)
                && (fileKey == null || source.fileKey == fileKey)) { "文件已变化，请重新加载后重试" }
            return DocumentReader(source, readState ?: LogReadState(source.first), prefix, cancelled)
        } catch (e: Exception) {
            source.close()
            throw e
        }
    }

    /** 临时文件写完才替换目标，返回加载正文在新文件中的字节边界。 */
    fun writeTo(target: Path, charset: Charset): Long {
        val parent = target.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".atools-log-save-", ".tmp")
        try {
            var prefixBytes = 0L
            openReader { Thread.currentThread().isInterrupted }.use { reader ->
                CountingOutput(Files.newOutputStream(temporary)).use { output ->
                    OutputStreamWriter(output, charset.newEncoder()).use { writer ->
                        writer.write(prefix)
                        writer.flush()
                        prefixBytes = output.count
                        // prefix 已写入；Reader 的尾部仍从同一解码状态继续。
                        var skipped = 0
                        val buffer = CharArray(32 * 1024)
                        while (skipped < prefix.length) {
                            val count = reader.read(buffer, 0, Math.min(buffer.size, prefix.length - skipped))
                            check(count > 0) { "加载正文读取失败" }
                            skipped += count
                        }
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            writer.write(buffer, 0, count)
                        }
                    }
                }
            }
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            return prefixBytes
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private class CountingOutput(output: OutputStream) : FilterOutputStream(output) {
        var count = 0L
            private set
        override fun write(value: Int) { out.write(value); count++ }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            out.write(bytes, offset, length)
            count += length
        }
    }

    private class DocumentReader(
        private val source: LogFileSource,
        initial: LogReadState,
        prefix: String,
        private val cancelled: () -> Boolean
    ) : Reader() {
        private val lease = LogMemoryBudget.reserve(LogMemoryBudget.TASK_RESERVATION)
        private val reader = try { LogChunkReader(source, initial) }
            catch (e: Exception) { lease.close(); throw e }
        private val chunks = ArrayDeque<String>()
        private var text = prefix
        private var offset = 0
        private var closed = false
        private val endOffset = source.size
        private val lastModified = source.lastModified

        override fun read(buffer: CharArray, start: Int, length: Int): Int {
            check(!closed) { "读取器已关闭" }
            require(start >= 0 && length >= 0 && start <= buffer.size - length)
            if (length == 0) return 0
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
            while (offset == text.length) {
                if (chunks.isEmpty()) {
                    source.checkVersion()
                    check(source.size == endOffset && source.lastModified == lastModified) { "搜索或保存期间文件已变化，请重试" }
                    if (reader.state.position.offset >= endOffset) {
                        check(reader.state.pending.isEmpty()) { "文件末尾编码字符不完整，请稍后重试" }
                        return -1
                    }
                    reader.readBatch(endOffset, cancelled) { next, _ -> if (next.isNotEmpty()) chunks.add(next) }
                }
                text = chunks.pollFirst() ?: ""
                offset = 0
            }
            val count = Math.min(length, text.length - offset)
            text.toCharArray(buffer, start, offset, offset + count)
            offset += count
            return count
        }

        override fun close() {
            if (closed) return
            closed = true
            try { reader.close() } finally {
                try { source.close() } finally { chunks.clear(); lease.close() }
            }
        }
    }
}

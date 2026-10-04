package com.allan.atools.tools.modulenotepad.log

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.StandardOpenOption
import java.util.concurrent.CancellationException

/** 字节游标和块尾状态一起保存，取消后从最后一次界面提交处继续。 */
data class LogReadState(val position: LogPosition, val pending: ByteArray = byteArrayOf(), val afterCr: Boolean = false)

object LogIo {
    fun readFully(channel: FileChannel, buffer: ByteBuffer, offset: Long, cancelled: () -> Boolean): Int {
        var count = 0
        var emptyReads = 0
        while (buffer.hasRemaining()) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
            val n = channel.read(buffer, offset + count)
            if (n < 0) throw EOFException("File truncated during read")
            if (n == 0) {
                if (++emptyReads >= 16) throw java.io.IOException("File read made no progress")
                Thread.yield()
            } else { count += n; emptyReads = 0 }
        }
        return count
    }
}

/** 每次读取最多 5 MiB 源字节，保留跨块字符与 CRLF；界面消费后才继续解码。 */
class LogChunkReader(private val source: LogFileSource, initial: LogReadState) : AutoCloseable {
    private val channel = FileChannel.open(source.path, StandardOpenOption.READ)
    private val buffer = ByteBuffer.allocate(LogMemoryBudget.READ_BYTES + 8)
    private val chars = CharBuffer.allocate(LogMemoryBudget.MAX_PAGE_CHARS)
    private val decoder = source.charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    var state = initial
        private set

    fun readBatch(endOffset: Long, cancelled: () -> Boolean, consume: (String, LogReadState) -> Unit): Boolean {
        source.checkVersion()
        val start = state.position.offset
        val count = Math.min(LogMemoryBudget.READ_BYTES.toLong(), endOffset - start).toInt()
        if (count <= 0) return false
        val prefix = state.pending.size
        buffer.clear()
        buffer.put(state.pending)
        buffer.limit(prefix + count)
        LogIo.readFully(channel, buffer, start, cancelled)
        buffer.flip()
        var line = state.position.line
        var column = state.position.column
        var afterCr = state.afterCr
        while (true) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
            chars.clear()
            // 到达当前文件尾时仍保留不足字符，后续文件追加后继续解码。
            val result = decoder.decode(buffer, chars, false)
            if (result.isError) throw java.io.IOException("解码失败（${source.charset.name()}，字节 ${start - prefix + buffer.position()}），请切换编码后重新加载")
            chars.flip()
            val text = StringBuilder(chars.remaining())
            while (chars.hasRemaining()) {
                val ch = chars.get()
                if (ch == '\n' && afterCr) { afterCr = false; continue }
                afterCr = ch == '\r'
                if (ch == '\r' || ch == '\n') { text.append('\n'); line++; column = 0 }
                else { text.append(ch); column++ }
            }
            val tail = if (result.isUnderflow) ByteArray(buffer.remaining()).also { buffer.get(it) } else byteArrayOf()
            val position = LogPosition(start - prefix + buffer.position(), line, column)
            state = LogReadState(position, tail, afterCr)
            consume(text.toString(), state)
            if (result.isUnderflow) break
        }
        source.checkVersion()
        return true
    }

    override fun close() = channel.close()
}

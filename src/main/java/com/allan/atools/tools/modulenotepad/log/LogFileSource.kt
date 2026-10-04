package com.allan.atools.tools.modulenotepad.log

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** 分块读取的数据源，校验文件版本并识别编码头；不绑定编辑器界面。 */
class LogFileSource(val path: Path, encoding: String) : AutoCloseable {
    private val version = Files.readAttributes(path, BasicFileAttributes::class.java)
    @Volatile private var observedVersion = version
    @Volatile private var closed = false
    @Volatile var size = version.size()
        private set
    val lastModified: Long get() = observedVersion.lastModifiedTime().toMillis()
    val fileKey: Any? get() = version.fileKey()
    val charset: Charset = Charset.forName(if (encoding == "UTF-8-NO-BOM") "UTF-8" else encoding)
    val first: LogPosition

    init {
        val bom = Files.newInputStream(path).use { it.readNBytes(4) }
        val expected = when (charset.name()) {
            "UTF-8" -> byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
            "UTF-16LE" -> byteArrayOf(-1, -2)
            "UTF-16BE" -> byteArrayOf(-2, -1)
            "UTF-32LE" -> byteArrayOf(-1, -2, 0, 0)
            "UTF-32BE" -> byteArrayOf(0, 0, -2, -1)
            else -> byteArrayOf()
        }
        val skip = if (expected.isNotEmpty() && bom.size >= expected.size
            && expected.indices.all { bom[it] == expected[it] }) expected.size else 0
        first = LogPosition(skip.toLong())
    }

    @Synchronized
    fun checkVersion(): Boolean {
        check(!closed) { "日志已关闭" }
        val now = Files.readAttributes(path, BasicFileAttributes::class.java)
        check(now.fileKey() == version.fileKey() && now.size() >= observedVersion.size()
            && (now.size() > observedVersion.size() || now.lastModifiedTime() == observedVersion.lastModifiedTime())) {
            "文件已被覆盖、截断或替换，请重新加载后重试"
        }
        val appended = now.size() > size
        size = now.size()
        observedVersion = now
        return appended
    }

    @Synchronized
    override fun close() { closed = true }
}

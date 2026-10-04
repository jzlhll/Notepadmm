package com.allan.atools.tools.modulenotepad.log

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** 有界编码探测，优先 BOM 和合法 UTF-8；采样末尾允许保留未完成的多字节字符。 */
object LogEncoding {
    @JvmStatic
    fun detect(path: Path): String {
        val bytes = Files.newInputStream(path).use { it.readNBytes(64 * 1024) }
        fun starts(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { bytes[it].toInt() and 255 == prefix[it] }
        if (starts(0xff, 0xfe, 0, 0)) return "UTF-32LE"
        if (starts(0, 0, 0xfe, 0xff)) return "UTF-32BE"
        if (starts(0xff, 0xfe)) return "UTF-16LE"
        if (starts(0xfe, 0xff)) return "UTF-16BE"
        if (starts(0xef, 0xbb, 0xbf)) return "UTF-8"
        val result = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes), CharBuffer.allocate(bytes.size), bytes.size.toLong() == Files.size(path))
        return if (result.isError) "GBK" else "UTF-8"
    }
}

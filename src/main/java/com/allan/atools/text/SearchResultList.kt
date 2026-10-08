package com.allan.atools.text

import com.allan.atools.beans.ResultItemWrap
import java.io.BufferedReader
import java.io.IOException
import java.util.function.BooleanSupplier

/** 在创建匹配对象前限制结果规模，截断状态随结果传给定位栏和结果窗口。 */
class SearchResultList : ArrayList<ResultItemWrap>() {
    var truncated = false
        private set
    private var matches = 0
    private var estimatedBytes = 0L

    fun acceptMatch(lineLength: Int, matchLength: Int, newLine: Boolean): Boolean {
        val cost = 192L + matchLength * 2L + if (newLine) 256L + lineLength * 4L else 0L
        if (matches >= 100_000 || estimatedBytes + cost > 16L * 1024 * 1024) {
            truncated = true
            return false
        }
        matches++
        estimatedBytes += cost
        return true
    }

    fun truncate() {
        truncated = true
    }

    companion object {
        const val MAX_LINE_CHARS = 1_048_576

        /** 不允许 readLine 在没有换行的巨大文件上无限扩容。 */
        @JvmStatic
        @Throws(IOException::class)
        fun readLine(reader: BufferedReader, result: SearchResultList, cancelled: BooleanSupplier): String? {
            val line = StringBuilder()
            while (true) {
                if (line.length % 4096 == 0 && cancelled.asBoolean) return null
                val ch = reader.read()
                if (ch == -1) return if (line.isEmpty()) null else line.toString()
                if (ch == '\n'.code) return line.toString()
                if (ch == '\r'.code) {
                    reader.mark(1)
                    if (reader.read() != '\n'.code) reader.reset()
                    return line.toString()
                }
                if (line.length == MAX_LINE_CHARS) {
                    result.truncate()
                    return null
                }
                line.append(ch.toChar())
            }
        }
    }
}

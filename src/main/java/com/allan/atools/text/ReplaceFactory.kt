package com.allan.atools.text

import com.allan.atools.bean.SearchParams
import com.allan.atools.text.beans.CharDefineHelper
import java.util.concurrent.CancellationException
import java.util.regex.Matcher
import java.util.regex.Pattern

/** 按搜索选项准备替换结果，计算期间不修改编辑器或磁盘文件。 */
class ReplaceFactory private constructor() {
    data class Match(val start: Int, val end: Int)
    data class Change(val start: Int, val end: Int, val text: String, val count: Int)

    private class InterruptibleText(private val text: String) : CharSequence {
        override val length: Int get() = text.length
        override fun get(index: Int): Char {
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            return text[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
        override fun toString(): String = text
    }

    companion object {
        @JvmStatic
        fun pattern(params: SearchParams): Pattern {
            var expression = when (params.type) {
                SearchParams.Type.Normal -> Pattern.quote(params.words)
                SearchParams.Type.FakeRegex -> params.words.split('|').joinToString("|") { word ->
                    word.split(".*").joinToString(".*?") { Pattern.quote(it) }
                }
                else -> params.words
            }
            if (params.type == SearchParams.Type.Regex && params.useWholeWords) expression = "\\b(?:$expression)\\b"
            val flags = Pattern.MULTILINE or (if (params.useCaseMatch) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            return Pattern.compile(expression, flags)
        }

        private fun accepts(source: String, matcher: Matcher, params: SearchParams): Boolean {
            if (!params.useWholeWords || params.type == SearchParams.Type.Regex || matcher.start() == matcher.end()) return true
            val start = matcher.start()
            val end = matcher.end()
            val first = CharDefineHelper.define(source[start])
            val last = CharDefineHelper.define(source[end - 1])
            return !(first > 0 && start > 0 && CharDefineHelper.define(source[start - 1]) == first ||
                last > 0 && end < source.length && CharDefineHelper.define(source[end]) == last)
        }

        fun findNext(source: String, params: SearchParams, from: Int): Match? {
            val matcher = pattern(params).matcher(InterruptibleText(source))
            var first: Match? = null
            while (matcher.find()) {
                if (Thread.currentThread().isInterrupted) throw CancellationException()
                if (!accepts(source, matcher, params)) continue
                val match = Match(matcher.start(), matcher.end())
                if (match.start >= from) return match
                if (first == null) first = match
            }
            return first
        }

        fun replace(source: String, params: SearchParams, replacement: String, selection: Match?,
                    maxOutputChars: Int): Change? {
            val matcher = pattern(params).matcher(InterruptibleText(source))
            val output = StringBuilder()
            val value = if (params.type == SearchParams.Type.Regex) replacement else Matcher.quoteReplacement(replacement)
            var first = -1
            var last = 0
            var count = 0
            while (matcher.find()) {
                if (Thread.currentThread().isInterrupted) throw CancellationException()
                if (selection != null && matcher.start() > selection.start) break
                if (!accepts(source, matcher, params)) continue
                if (selection != null && (matcher.start() != selection.start || matcher.end() != selection.end)) continue
                if (first < 0) first = matcher.start()
                matcher.appendReplacement(output, value)
                last = matcher.end()
                count++
                require(output.length.toLong() + source.length - last <= maxOutputChars) { "resultTooLarge" }
                if (selection != null) break
            }
            if (count == 0) return null
            // 去掉未修改的首尾，只回写实际替换范围，保留其余段落和样式。
            return Change(first, last, output.substring(first), count)
        }
    }
}

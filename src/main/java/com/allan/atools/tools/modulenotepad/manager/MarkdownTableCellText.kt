package com.allan.atools.tools.modulenotepad.manager

/** 格内换行与源码的双向映射；代码片段中的 HTML 保持字面量。 */
object MarkdownTableCellText {
    private val breaks = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val htmlTag = Regex("""</?[A-Za-z][A-Za-z0-9-]*(?:\s+[A-Za-z_:][A-Za-z0-9_.:-]*(?:\s*=\s*(?:'[^']*'|"[^"]*"|[^\s"'=<>`]+))?)*\s*/?>""")

    private fun linkEnd(source: String, begin: Int): Int? {
        var index = begin
        while (source.getOrNull(index)?.isWhitespace() == true) index++
        val titleOnly = index > begin && (source.getOrNull(index) == '\'' || source.getOrNull(index) == '"')
        if (source.getOrNull(index) == '<') {
            index++
            while (index < source.length && source[index] != '>') {
                if (source[index] == '\\') index++
                index++
            }
            if (index == source.length) return null
            index++
        } else if (!titleOnly) {
            var depth = 0
            while (index < source.length) {
                val c = source[index]
                if (c == '\\') { index += 2; continue }
                if (c == '(') depth++
                if (c == ')') {
                    if (depth == 0) return index + 1
                    depth--
                }
                if (c.isWhitespace()) break
                index++
            }
            if (depth != 0) return null
        }
        while (source.getOrNull(index)?.isWhitespace() == true) index++
        if (source.getOrNull(index) == ')') return index + 1
        val quote = source.getOrNull(index) ?: return null
        if (quote != '\'' && quote != '"' && quote != '(') return null
        val closing = if (quote == '(') ')' else quote
        index++
        while (index < source.length && source[index] != closing) {
            if (source[index] == '\\') index++
            index++
        }
        if (index >= source.length) return null
        index++
        while (source.getOrNull(index)?.isWhitespace() == true) index++
        return if (source.getOrNull(index) == ')') index + 1 else null
    }

    private fun breakRanges(source: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var index = 0
        var labels = 0
        while (index < source.length) {
            if (source[index] == '\\' && index + 1 < source.length) {
                index += 2
                continue
            }
            if (source[index] == '[') labels++
            if (source[index] == ']' && labels > 0) {
                labels--
                val end = if (source.getOrNull(index + 1) == '(') linkEnd(source, index + 2) else null
                if (end != null) { index = end; continue }
            }
            if (source[index] == '`') {
                var end = index + 1
                while (end < source.length && source[end] == '`') end++
                val ticks = source.substring(index, end)
                var close = source.indexOf(ticks, end)
                while (close >= 0 && (source.getOrNull(close - 1) == '`' || source.getOrNull(close + ticks.length) == '`')) {
                    close = source.indexOf(ticks, close + ticks.length)
                }
                if (close >= 0) {
                    index = close + ticks.length
                    continue
                }
                index = end
                continue
            }
            val match = if (source[index] == '<') breaks.matchAt(source, index) else null
            if (match != null) {
                ranges.add(match.range)
                index = match.range.last + 1
            } else {
                // 标签属性和注释中的 <br> 不代表格内换行。
                val tag = if (source[index] == '<') htmlTag.matchAt(source, index) else null
                val commentEnd = if (source.startsWith("<!--", index)) source.indexOf("-->", index + 4) else -1
                index = when {
                    tag != null -> tag.range.last + 1
                    commentEnd >= 0 -> commentEnd + 3
                    else -> index + 1
                }
            }
        }
        return ranges
    }

    @JvmStatic
    fun decode(source: String): String {
        val result = StringBuilder()
        var start = 0
        for (range in breakRanges(source)) {
            result.append(source, start, range.first).append('\n')
            start = range.last + 1
        }
        return result.append(source, start, source.length).toString()
    }

    @JvmStatic
    fun sourceOffset(source: String, offset: Int): Int {
        var value = offset.coerceIn(0, source.length)
        for (range in breakRanges(source)) {
            if (offset > range.first) value -= (offset - range.first).coerceAtMost(range.count()) - 1
        }
        return value
    }

    @JvmStatic
    fun encode(value: String, original: String): String {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        val old = decode(original)
        var prefix = 0
        while (prefix < old.length && prefix < normalized.length && old[prefix] == normalized[prefix]) prefix++
        var suffix = 0
        while (suffix < old.length - prefix && suffix < normalized.length - prefix &&
            old[old.lastIndex - suffix] == normalized[normalized.lastIndex - suffix]) suffix++
        // 未改动的换行沿用原标签，避免编辑其他文字时改写 <BR /> 等形式。
        val tags = HashMap<Int, String>()
        var removed = 0
        for (range in breakRanges(original)) {
            val oldIndex = range.first - removed
            val newIndex = when {
                oldIndex < prefix -> oldIndex
                oldIndex >= old.length - suffix -> normalized.length - (old.length - oldIndex)
                else -> -1
            }
            if (newIndex >= 0) tags[newIndex] = original.substring(range)
            removed += range.count() - 1
        }
        return buildString {
            normalized.forEachIndexed { index, c ->
                when (c) {
                    '\n' -> append(tags[index] ?: "<br>")
                    '|' -> {
                        var slashes = 0
                        var cursor = length - 1
                        while (cursor >= 0 && this[cursor] == '\\') { slashes++; cursor-- }
                        if (slashes % 2 == 0) append('\\')
                        append(c)
                    }
                    else -> append(c)
                }
            }
        }
    }
}

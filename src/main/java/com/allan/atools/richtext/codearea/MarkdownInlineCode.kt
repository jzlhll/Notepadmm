package com.allan.atools.richtext.codearea

/** 正文、表格和 HTML 导入共用代码跨度边界及保护空格规则。 */
object MarkdownInlineCode {
    data class Edit(val start: Int, val end: Int, val text: String, val selectionStart: Int, val selectionEnd: Int)
    private val ticks = Regex("`+")

    fun encode(value: String): String {
        val delimiter = "`".repeat((ticks.findAll(value).map { it.value.length }.maxOrNull() ?: 0) + 1)
        val body = if (needsPadding(value)) " $value " else value
        return delimiter + body + delimiter
    }

    private fun needsPadding(value: String) = value.startsWith('`') || value.endsWith('`') ||
        value.startsWith(' ') && value.endsWith(' ') && value.any { it != ' ' }

    @JvmStatic
    fun toggle(source: String, start: Int, end: Int): Edit {
        val selected = source.substring(start, end)
        // 优先识别选区外的跨度，避免把内容自身的反引号当成外层分隔符。
        for (padding in 1 downTo 0) {
            if (padding == 1 && (!needsPadding(selected) || source.getOrNull(start - 1) != ' ' || source.getOrNull(end) != ' ')) continue
            val bodyStart = start - padding
            val bodyEnd = end + padding
            var from = bodyStart
            var to = bodyEnd
            while (from > 0 && source[from - 1] == '`') from--
            while (to < source.length && source[to] == '`') to++
            val count = bodyStart - from
            if (count > 0 && to - bodyEnd == count && ticks.findAll(selected).none { it.value.length == count })
                return Edit(from, to, selected, from, from + selected.length)
        }
        val opening = ticks.find(selected)?.takeIf { it.range.first == 0 }?.value.orEmpty()
        if (opening.isNotEmpty() && selected.length >= opening.length * 2 && selected.endsWith(opening)
            && selected.getOrNull(selected.length - opening.length - 1) != '`') {
            var body = selected.substring(opening.length, selected.length - opening.length)
            if (ticks.findAll(body).none { it.value.length == opening.length }) {
                if (body.startsWith(' ') && body.endsWith(' ') && body.any { it != ' ' }) body = body.substring(1, body.length - 1)
                return Edit(start, end, body, start, start + body.length)
            }
        }
        val result = encode(selected)
        val count = (ticks.findAll(selected).map { it.value.length }.maxOrNull() ?: 0) + 1
        val selectedStart = start + count + if (needsPadding(selected)) 1 else 0
        return Edit(start, end, result, selectedStart, selectedStart + selected.length)
    }
}

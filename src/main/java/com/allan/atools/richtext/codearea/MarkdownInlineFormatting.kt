package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot

/** 正文和表格共用 AST 格式切换，返回源码变更及变更后的选区。 */
object MarkdownInlineFormatting {
    @JvmStatic
    fun toggle(state: MarkdownStructureSnapshot, start: Int, end: Int, mark: String): MarkdownInlineCode.Edit? {
        if (state.intersectsLiteral(start, end, mark == "`")) return null
        if (mark == "`") return MarkdownInlineCode.toggle(state.text, start, end)
        if (start == end) {
            val length = mark.length
            if (start >= length && end + length <= state.text.length && state.text.substring(start - length, start) == mark && state.text.substring(end, end + length) == mark)
                return MarkdownInlineCode.Edit(start - length, end + length, "", start - length, start - length)
            else return MarkdownInlineCode.Edit(start, end, mark + mark, start + length, start + length)
        }
        val wrappers = state.elements.filter {
            when (mark) {
                "**" -> it.node is org.commonmark.node.StrongEmphasis
                "*" -> it.node is org.commonmark.node.Emphasis
                "~~" -> it.node is org.commonmark.ext.gfm.strikethrough.Strikethrough
                else -> false
            }
        }.mapNotNull { entry ->
            val first = entry.ranges.firstOrNull() ?: return@mapNotNull null
            val last = entry.ranges.last()
            MarkdownStructureSnapshot.Range(first.start, last.end)
        }
        fun wrapper(from: Int, to: Int) = wrappers.firstOrNull {
            from in it.start..(it.start + mark.length) && to in (it.end - mark.length)..it.end ||
                from >= it.start + mark.length && to <= it.end - mark.length &&
                state.text.substring(it.start + mark.length, from).all { character -> character in "*_~" } &&
                state.text.substring(to, it.end - mark.length).all { character -> character in "*_~" }
        }
        // 已有跨行强调只解除自身分隔符，保留容器前缀和原有换行。
        wrapper(start, end)?.let {
            val body = state.text.substring(it.start + mark.length, it.end - mark.length)
            val selectedStart = Math.max(it.start, start - mark.length)
            val selectedEnd = Math.min(it.start + body.length, end - mark.length)
            return MarkdownInlineCode.Edit(it.start, it.end, body, selectedStart, Math.max(selectedStart, selectedEnd))
        }
        val parts = ArrayList<Pair<MarkdownStructureSnapshot.Range, MarkdownStructureSnapshot.Range?>>()
        for (entry in state.elements) {
            if (entry.node !is org.commonmark.node.Paragraph && entry.node !is org.commonmark.node.Heading) continue
            val spans = ArrayList<org.commonmark.node.SourceSpan>()
            var child = entry.node.firstChild
            while (child != null) { spans.addAll(child.sourceSpans); child = child.next }
            if (spans.isEmpty()) continue
            val contentStart = spans.first().inputIndex
            val contentEnd = spans.last().let { it.inputIndex + it.length }
            for (span in entry.node.sourceSpans) {
                var from = Math.max(start, Math.max(contentStart, span.inputIndex))
                val line = state.lines[span.lineIndex]
                if (line.taskOffset >= 0) from = Math.max(from, line.taskOffset + 2)
                var to = Math.min(end, Math.min(contentEnd, span.inputIndex + span.length))
                while (from < to && state.text[from].isWhitespace()) from++
                while (to > from && state.text[to - 1].isWhitespace()) to--
                if (from < to) parts.add(MarkdownStructureSnapshot.Range(from, to) to wrapper(from, to))
            }
        }
        if (parts.isEmpty()) return null
        val remove = parts.all { it.second != null }
        val edits = parts.mapNotNull { (range, existing) ->
            if (remove && existing != null) Triple(existing.start, existing.end,
                state.text.substring(existing.start + mark.length, existing.end - mark.length))
            else if (existing == null) Triple(range.start, range.end, mark + state.text.substring(range.start, range.end) + mark)
            else null
        }.sortedBy { it.first }
        if (edits.isEmpty()) return null
        val from = Math.min(start, edits.first().first)
        val to = Math.max(end, edits.last().second)
        val result = StringBuilder(state.text.substring(from, to))
        edits.asReversed().forEach { (begin, finish, value) -> result.replace(begin - from, finish - from, value) }
        if (parts.size == 1) {
            val edit = edits.first()
            val selectedStart = edit.first + if (remove) 0 else mark.length
            val selectedEnd = edit.first + edit.third.length - if (remove) 0 else mark.length
            return MarkdownInlineCode.Edit(from, to, result.toString(), selectedStart, selectedEnd)
        } else return MarkdownInlineCode.Edit(from, to, result.toString(), from, from + result.length)
    }
}

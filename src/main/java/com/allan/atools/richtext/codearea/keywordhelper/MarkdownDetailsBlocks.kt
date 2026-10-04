package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline

/** 只配对 AST 中的 HTML 标签，代码示例及未闭合结构保持源码显示。 */
object MarkdownDetailsBlocks {
    data class Block(val start: Int, val end: Int, val firstLine: Int, val lastLine: Int, val source: String)

    private val tags = Regex("<!--[\\s\\S]*?(?:-->|$)|<(pre|code|script|style)\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>[\\s\\S]*?(?:</\\1\\s*>|$)|</?[a-z][\\w:-]*(?=[\\s/>])(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", RegexOption.IGNORE_CASE)
    private val detailsTag = Regex("^</?details(?=[\\s/>])", RegexOption.IGNORE_CASE)

    fun parse(state: MarkdownStructureSnapshot): List<Block> {
        val tokens = sortedMapOf<Int, String>()
        for (element in state.elements) {
            if (element.node !is HtmlBlock && element.node !is HtmlInline) continue
            val first = element.ranges.firstOrNull() ?: continue
            val last = element.ranges.last()
            for (match in tags.findAll(state.text.substring(first.start, last.end))) {
                if (detailsTag.containsMatchIn(match.value)) {
                    tokens[first.start + match.range.first] = match.value
                }
            }
        }
        val blocks = ArrayList<Block>()
        val stack = ArrayDeque<Int>()
        for ((offset, tag) in tokens) {
            if (!tag.startsWith("</")) {
                stack.addLast(offset)
            } else if (stack.isNotEmpty()) {
                val start = stack.removeLast()
                if (stack.isNotEmpty()) continue
                val end = offset + tag.length
                val firstLine = state.lineAt(start)
                val lastLine = state.lineAt(end)
                // 整段预览占用原段落；同行外侧还有正文时保留源码，避免吞掉相邻内容。
                if (state.text.substring(state.lines[firstLine].start, start).isBlank()
                    && state.text.substring(end, state.lines[lastLine].end).isBlank()) {
                    blocks.add(Block(start, end, firstLine, lastLine, state.text.substring(start, end)))
                }
            }
        }
        return blocks
    }
}

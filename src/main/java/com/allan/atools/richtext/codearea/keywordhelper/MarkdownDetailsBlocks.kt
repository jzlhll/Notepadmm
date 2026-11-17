package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.BlockQuote
import org.commonmark.node.ListItem
import org.commonmark.node.Node

/** 只配对 AST 中的 HTML 标签，代码示例及未闭合结构保持源码显示。 */
object MarkdownDetailsBlocks {
    data class Block(val start: Int, val end: Int, val firstLine: Int, val lastLine: Int, val source: String)
    private data class Tag(val offset: Int, val literal: String, val container: Node?)

    private val tags = Regex("<!--[\\s\\S]*?(?:-->|$)|<(pre|code|script|style)\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>[\\s\\S]*?(?:</\\1\\s*>|$)|</?[a-z][\\w:-]*(?=[\\s/>])(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", RegexOption.IGNORE_CASE)
    private val detailsTag = Regex("^</?details(?=[\\s/>])", RegexOption.IGNORE_CASE)

    fun parse(state: MarkdownStructureSnapshot): List<Block> {
        val tokens = sortedMapOf<Int, Tag>()
        for (element in state.elements) {
            if (element.node !is HtmlBlock && element.node !is HtmlInline) continue
            val first = element.ranges.firstOrNull() ?: continue
            val last = element.ranges.last()
            var container = element.node.parent
            while (container != null && container !is ListItem && container !is BlockQuote) container = container.parent
            for (match in tags.findAll(state.text.substring(first.start, last.end))) {
                if (detailsTag.containsMatchIn(match.value)) {
                    val offset = first.start + match.range.first
                    tokens[offset] = Tag(offset, match.value, container)
                }
            }
        }
        val blocks = ArrayList<Block>()
        val stack = ArrayDeque<Tag>()
        for (tag in tokens.values) {
            if (!tag.literal.startsWith("</")) {
                stack.addLast(tag)
            } else if (stack.isNotEmpty()) {
                // 跨 AST 块配对，但不能跨到另一个列表项或引用容器。
                if (stack.last().container !== tag.container) continue
                val start = stack.removeLast().offset
                if (stack.isNotEmpty()) continue
                val end = tag.offset + tag.literal.length
                val firstLine = state.lineAt(start)
                val lastLine = state.lineAt(end)
                blocks.add(Block(start, end, firstLine, lastLine, state.text.substring(start, end)))
            }
        }
        return blocks
    }
}

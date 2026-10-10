package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.node.CustomNode
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.SourceSpans
import org.commonmark.node.Text
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.ParsedInline

/** 删除线允许分隔符内侧留白，仍由行内解析器保护代码、转义和链接地址。 */
object MarkdownStrikethroughParser {
    private class Delimiter : CustomNode()

    private val factory = object : InlineContentParserFactory {
        override fun getTriggerCharacters() = setOf('~')

        override fun create() = InlineContentParser { state ->
            val scanner = state.scanner()
            val start = scanner.position()
            // 单波浪符保留下标语义，其他长度仍交给原有分隔符处理器。
            if (scanner.peekPreviousCodePoint() == '~'.code || scanner.matchMultiple('~') != 2) {
                ParsedInline.none()
            } else {
                val node = Delimiter()
                node.sourceSpans = scanner.getSource(start, scanner.position()).sourceSpans
                ParsedInline.of(node, scanner.position())
            }
        }
    }

    @JvmStatic
    fun create(context: InlineParserContext): InlineParser {
        val extended = object : InlineParserContext by context {
            override fun getCustomInlineContentParserFactories() = context.customInlineContentParserFactories + factory
        }
        val delegate = MarkdownAstCache.createCommonMarkInlineParser(extended)
        return InlineParser { lines, parent ->
            delegate.parse(lines, parent)
            resolve(parent)
        }
    }

    private fun resolve(root: Node) {
        val parents = ArrayList<Node>()
        parents.add(root)
        var index = 0
        while (index < parents.size) {
            var child = parents[index++].firstChild
            while (child != null) {
                if (child.firstChild != null) parents.add(child)
                child = child.next
            }
        }
        // 先处理标签和强调内部，避免跨越语法容器边界配对分隔符。
        for (parent in parents.asReversed()) {
            var opening: Delimiter? = null
            var hasContent = false
            var child = parent.firstChild
            while (child != null) {
                val next = child.next
                if (child is Delimiter) {
                    val first = opening
                    if (first != null && hasContent) {
                        val strike = Strikethrough("~~")
                        val spans = SourceSpans()
                        spans.addAll(first.sourceSpans)
                        first.insertAfter(strike)
                        var content = strike.next
                        while (content != null && content !== child) {
                            val following = content.next
                            spans.addAll(content.sourceSpans)
                            strike.appendChild(content)
                            content = following
                        }
                        spans.addAll(child.sourceSpans)
                        strike.sourceSpans = spans.sourceSpans
                        first.unlink()
                        child.unlink()
                        opening = null
                    } else opening = child
                    hasContent = false
                } else if (child !is SoftLineBreak && child !is HardLineBreak &&
                    (child !is Text || child.literal.isNotBlank())) hasContent = true
                child = next
            }
            // 未闭合和仅包含空白的片段保持普通文本，缓存和输出不留下临时节点。
            child = parent.firstChild
            while (child != null) {
                val next = child.next
                if (child is Delimiter) {
                    val text = Text("~~")
                    text.sourceSpans = child.sourceSpans
                    child.insertAfter(text)
                    child.unlink()
                }
                child = next
            }
        }
    }
}

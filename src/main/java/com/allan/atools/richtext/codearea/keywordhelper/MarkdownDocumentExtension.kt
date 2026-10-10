package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.CustomNode
import org.commonmark.node.Node
import org.commonmark.node.SourceSpans
import org.commonmark.parser.Parser
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer

class MarkdownMath(val literal: String, val display: Boolean) : CustomNode()
class MarkdownMathBlock(var literal: String = "", var closed: Boolean = false) : org.commonmark.node.CustomBlock()
class MarkdownDecoration(val tag: String, val delimiter: String) : CustomNode()
class MarkdownEmoji(val shortcode: String, val literal: String) : CustomNode()

/** 扩展参与同一 AST：代码和转义内容不会被二次正则解析为公式或强调。 */
class MarkdownDocumentExtension : Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {
    override fun extend(builder: Parser.Builder) {
        builder.inlineParserFactory(MarkdownStrikethroughParser::create)
        builder.customBlockParserFactory(object : org.commonmark.parser.block.AbstractBlockParserFactory() {
            override fun tryStart(state: org.commonmark.parser.block.ParserState, matched: org.commonmark.parser.block.MatchedBlockParser): org.commonmark.parser.block.BlockStart? {
                if (state.indent >= 4 || state.line.content.subSequence(state.nextNonSpaceIndex, state.line.content.length).toString().trim() != "$$")
                    return org.commonmark.parser.block.BlockStart.none()
                return org.commonmark.parser.block.BlockStart.of(MathBlockParser()).atIndex(state.line.content.length)
            }
        })
        builder.customInlineContentParserFactory(object : InlineContentParserFactory {
            override fun getTriggerCharacters() = setOf('$')
            override fun create() = InlineContentParser { state -> parseMath(state) }
        })
        builder.customInlineContentParserFactory(object : InlineContentParserFactory {
            override fun getTriggerCharacters() = setOf(':')
            override fun create() = InlineContentParser { state ->
                val scanner = state.scanner()
                val start = scanner.position()
                scanner.next()
                var length = 0
                while (scanner.hasNext() && length < 64 &&
                    (scanner.peek() in 'a'..'z' || scanner.peek() in 'A'..'Z' || scanner.peek() in '0'..'9' || scanner.peek() in "_+-")) {
                    scanner.next()
                    length++
                }
                if (length == 0 || !scanner.next(':')) ParsedInline.none() else {
                    val source = scanner.getSource(start, scanner.position())
                    val emoji = MarkdownEmojiShortcodes.resolve(source.content)
                    if (emoji == null) ParsedInline.none() else {
                        val node = MarkdownEmoji(source.content, emoji)
                        node.sourceSpans = source.sourceSpans
                        ParsedInline.of(node, scanner.position())
                    }
                }
            }
        })
        builder.customDelimiterProcessor(DecorationProcessor('=', 2, "mark"))
        builder.customDelimiterProcessor(DecorationProcessor('^', 1, "sup"))
        builder.customDelimiterProcessor(DecorationProcessor('~', 1, "sub"))
    }

    private class MathBlockParser : org.commonmark.parser.block.AbstractBlockParser() {
        private val node = MarkdownMathBlock()
        private val content = StringBuilder()
        private var opening = true
        override fun getBlock() = node
        override fun tryContinue(state: org.commonmark.parser.block.ParserState): org.commonmark.parser.block.BlockContinue {
            if (state.line.content.subSequence(state.nextNonSpaceIndex, state.line.content.length).toString().trim() == "$$") {
                state.line.sourceSpan?.let { node.addSourceSpan(it.subSpan(state.index)) }
                node.closed = true
                return org.commonmark.parser.block.BlockContinue.finished()
            }
            return org.commonmark.parser.block.BlockContinue.atIndex(state.index)
        }
        override fun addLine(line: org.commonmark.parser.SourceLine) {
            if (opening) { opening = false; return }
            content.append(line.content).append('\n')
        }
        override fun closeBlock() { node.literal = content.toString().trimEnd('\n') }
    }

    private fun parseMath(state: InlineParserState): ParsedInline? {
        val scanner = state.scanner()
        val start = scanner.position()
        scanner.next()
        val display = scanner.next('$')
        if (!display && (scanner.peek().isWhitespace() || scanner.peek() == '$')) return ParsedInline.none()
        val content = scanner.position()
        var previous = '\u0000'
        while (scanner.hasNext()) {
            val close = scanner.position()
            val character = scanner.peek()
            if (character == '\n' && !display) return ParsedInline.none()
            scanner.next()
            if (character == '\\') {
                if (scanner.hasNext()) scanner.next()
                previous = 'x'
                continue
            }
            if (character == '$' && (!display || scanner.next('$'))) {
                if (!display && (previous.isWhitespace() || scanner.peek().isDigit())) continue
                val literal = scanner.getSource(content, close).content
                if (literal.isBlank()) return ParsedInline.none()
                val node = MarkdownMath(literal, display)
                node.sourceSpans = scanner.getSource(start, scanner.position()).sourceSpans
                return ParsedInline.of(node, scanner.position())
            }
            previous = character
        }
        return ParsedInline.none()
    }

    private class DecorationProcessor(private val marker: Char, private val count: Int, private val tag: String) : DelimiterProcessor {
        override fun getOpeningCharacter() = marker
        override fun getClosingCharacter() = marker
        override fun getMinLength() = count
        override fun process(opening: DelimiterRun, closing: DelimiterRun): Int {
            if (opening.length() < count || closing.length() < count) return 0
            if (marker == '~' && (opening.originalLength() != 1 || closing.originalLength() != 1)) return 0
            val opener = opening.opener
            val closer = closing.closer
            val nodes = ArrayList<Node>()
            var child = opener.next
            while (child != null && child !== closer) { nodes.add(child); child = child.next }
            if (nodes.isEmpty()) return 0
            // 上下标只接受无空白的片段，避免把正文中的比较或波浪符吞掉。
            if (count == 1 && nodes.any { it is org.commonmark.node.Text && it.literal.any(Char::isWhitespace) }) return 0
            val decoration = MarkdownDecoration(tag, marker.toString().repeat(count))
            val spans = SourceSpans()
            spans.addAllFrom(opening.getOpeners(count))
            spans.addAllFrom(nodes)
            spans.addAllFrom(closing.getClosers(count))
            decoration.sourceSpans = spans.sourceSpans
            nodes.forEach { decoration.appendChild(it) }
            opener.insertAfter(decoration)
            return count
        }
    }

    override fun extend(builder: HtmlRenderer.Builder) {
        builder.nodeRendererFactory { context -> Renderer(context) }
    }

    private class Renderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
        override fun getNodeTypes() = setOf(MarkdownMath::class.java, MarkdownMathBlock::class.java, MarkdownDecoration::class.java, MarkdownEmoji::class.java)
        override fun render(node: Node) {
            val writer = context.writer
            if (node is MarkdownEmoji) {
                writer.text(node.literal)
            } else if (node is MarkdownMath || node is MarkdownMathBlock) {
                if (node is MarkdownMathBlock && !node.closed) {
                    writer.tag("pre", context.extendAttributes(node, "pre", emptyMap()))
                    writer.tag("code")
                    writer.text("$$\n" + node.literal)
                    writer.tag("/code")
                    writer.tag("/pre")
                    return
                }
                val display = node is MarkdownMathBlock || (node as MarkdownMath).display
                val literal = if (node is MarkdownMathBlock) node.literal else (node as MarkdownMath).literal
                val tag = if (node is MarkdownMathBlock) "div" else "span"
                writer.tag(tag, context.extendAttributes(node, tag, mapOf("class" to "md-math", "data-display" to display.toString())))
                writer.text(literal)
                writer.tag("/$tag")
            } else if (node is MarkdownDecoration) {
                writer.tag(node.tag, context.extendAttributes(node, node.tag, emptyMap()))
                var child = node.firstChild
                while (child != null) { context.render(child); child = child.next }
                writer.tag("/${node.tag}")
            }
        }
    }
}

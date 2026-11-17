package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

private val opening = Regex("(?is)^<([a-z][\\w:-]*)(?=[\\s/>])")
private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

fun markdownHtmlInlineEnd(node: HtmlInline): Node? {
    val literal = node.literal.trim()
    if (literal.startsWith("<!--")) return null
    val tag = opening.find(literal)?.groupValues?.get(1)?.lowercase() ?: return null
    if (tag in voidTags || literal.endsWith("/>")) return node
    val close = Regex("(?is)^</$tag\\s*>$")
    var depth = 1
    var sibling = node.next
    while (sibling != null) {
        if (sibling is HtmlInline) {
            val value = sibling.literal.trim()
            if (close.matches(value)) depth--
            else if (opening.find(value)?.groupValues?.get(1)?.equals(tag, true) == true && !value.endsWith("/>")) depth++
            if (depth == 0) return sibling
        }
        sibling = sibling.next
    }
    return null
}

/** HTML 只按显式闭合的元素或注释取片段，不包含 CommonMark 归入同一块的尾随正文。 */
fun collectMarkdownHtmlFragments(state: MarkdownStructureSnapshot, node: HtmlBlock): List<HtmlBlock> {
    val spans = node.sourceSpans
    val starts = ArrayList<Int>()
    val source = buildString {
        spans.forEachIndexed { index, span ->
            if (index > 0) append('\n')
            starts.add(length)
            append(state.text, span.inputIndex, span.inputIndex + span.length)
        }
    }
    val result = ArrayList<HtmlBlock>()
    fun add(start: Int, end: Int) {
        if (start < 0 || end <= start || end > source.length) return
        val mapped = ArrayList<SourceSpan>()
        spans.forEachIndexed { index, span ->
            val left = Math.max(start, starts[index])
            val right = Math.min(end, starts[index] + span.length)
            if (left < right) mapped.add(span.subSpan(left - starts[index], right - starts[index]))
        }
        if (mapped.isNotEmpty()) result.add(HtmlBlock().apply {
            literal = source.substring(start, end)
            sourceSpans = mapped
        })
    }
    fun visit(html: org.jsoup.nodes.Node) {
        val start = html.sourceRange()
        if (html is Comment && start.isTracked && !start.isImplicit) {
            if (source.substring(start.start().pos(), start.end().pos()).endsWith("-->"))
                add(start.start().pos(), start.end().pos())
            return
        }
        if (html is Element && start.isTracked && !start.isImplicit) {
            val end = html.endSourceRange()
            if (html.normalName() in voidTags || source.substring(start.start().pos(), start.end().pos()).endsWith("/>")) {
                add(start.start().pos(), start.end().pos())
                return
            }
            if (end.isTracked && !end.isImplicit) {
                add(start.start().pos(), end.end().pos())
                return
            }
        }
        html.childNodes().forEach { visit(it) }
    }
    visit(Parser.htmlParser().setTrackPosition(true).parseInput(source, ""))
    return result
}

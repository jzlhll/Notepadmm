package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.node.Block
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

private val opening = Regex("(?is)^<([a-z][\\w:-]*)(?=[\\s/>])")
private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
private val rawTextOpening = Regex("(?i)^<(?:script|style|textarea|title|iframe|xmp|noembed|noframes|plaintext)(?=[\\s/>])")
private const val HTML_WHITESPACE = " \t\n\r\u000c"

fun normalizeHtmlImageDestination(value: String): String =
    value.trim { it in HTML_WHITESPACE }.replace("\t", "").replace("\r", "").replace("\n", "")

private class MarkdownHtmlSource(document: String, private val spans: List<SourceSpan>) {
    private val spanStarts = IntArray(spans.size)
    val text = buildString {
        spans.forEachIndexed { index, span ->
            if (index > 0) append('\n')
            spanStarts[index] = length
            append(document, span.inputIndex, span.inputIndex + span.length)
        }
    }

    fun sourceSpans(start: Int, end: Int): List<SourceSpan> = buildList {
        val found = spanStarts.binarySearch(start)
        var index = if (found >= 0) found else -found - 2
        if (index < 0 || spanStarts[index] + spans[index].length <= start) index++
        while (index < spans.size && spanStarts[index] < end) {
            val spanStart = spanStarts[index]
            val span = spans[index]
            val left = Math.max(start, spanStart)
            val right = Math.min(end, spanStart + span.length)
            if (left < right) add(span.subSpan(left - spanStart, right - spanStart))
            index++
        }
    }

    fun documentRange(start: Int, end: Int): MarkdownStructureSnapshot.Range? {
        if (start < 0 || end <= start || end > text.length) return null
        val first = spanAt(start)
        val last = spanAt(end - 1)
        if (first < 0 || last < 0) return null
        return MarkdownStructureSnapshot.Range(
            spans[first].inputIndex + start - spanStarts[first],
            spans[last].inputIndex + end - spanStarts[last]
        )
    }

    private fun spanAt(position: Int): Int {
        val found = spanStarts.binarySearch(position)
        val index = if (found >= 0) found else -found - 2
        return if (index >= 0 && position - spanStarts[index] < spans[index].length) index else -1
    }
}

data class MarkdownHtmlImage(
    val range: MarkdownStructureSnapshot.Range,
    val destination: String,
    val sourceValue: SourceValue?
) {
    data class SourceValue(val segments: List<Segment>, val quote: Char?) {
        data class Segment(val range: MarkdownStructureSnapshot.Range, val fillsLine: Boolean)
    }
}

/** 用 HTML 解析器区分图片、注释和属性文本，再把标签及 src 的位置映射回 Markdown。 */
fun collectMarkdownHtmlImages(state: MarkdownStructureSnapshot, start: Int, end: Int): List<MarkdownHtmlImage> {
    val result = ArrayList<MarkdownHtmlImage>()
    val inlineScopes = LinkedHashMap<Node, MutableList<HtmlInline>>()

    fun overlaps(node: Node): Boolean {
        val first = node.sourceSpans.firstOrNull() ?: return false
        val last = node.sourceSpans.last()
        return first.inputIndex < end && last.inputIndex + last.length > start
    }

    fun addImages(spans: List<SourceSpan>) {
        val source = MarkdownHtmlSource(state.text, spans)
        val html = Parser.htmlParser().setTrackPosition(true).parseInput(source.text, "")
        for (image in html.select("img[src]")) {
            val tag = image.sourceRange()
            if (!tag.isTracked || tag.isImplicit || !source.text.startsWith("<img", tag.startPos(), ignoreCase = true)) continue
            val range = source.documentRange(tag.startPos(), tag.endPos()) ?: continue
            if (range.start < start || range.end > end) continue
            val value = image.attributes().sourceRange("src").valueRange()
            var sourceValue: MarkdownHtmlImage.SourceValue? = null
            if (value.isTracked && value.startPos() > tag.startPos() && value.endPos() < tag.endPos()) {
                val valueStart = value.startPos()
                val valueEnd = value.endPos()
                val quote = source.text[valueStart - 1].takeIf { it == '"' || it == '\'' }
                val openingLine = source.documentRange(valueStart - 1, valueStart)?.let { state.lineAt(it.start) }
                val closingLine = source.documentRange(valueEnd, valueEnd + 1)?.let { state.lineAt(it.start) }
                // 按物理行保留地址两侧的排版空白、换行和容器前缀。
                val segments = source.sourceSpans(valueStart, valueEnd).mapNotNull { span ->
                    var first = span.inputIndex
                    var last = first + span.length
                    while (first < last && state.text[first] in HTML_WHITESPACE) first++
                    while (last > first && state.text[last - 1] in HTML_WHITESPACE) last--
                    if (first == last) return@mapNotNull null
                    val fillsLine = quote != null && openingLine != null && closingLine != null &&
                        span.lineIndex > openingLine && span.lineIndex < closingLine
                    MarkdownHtmlImage.SourceValue.Segment(MarkdownStructureSnapshot.Range(first, last), fillsLine)
                }
                if (segments.isNotEmpty()) sourceValue = MarkdownHtmlImage.SourceValue(segments, quote)
            }
            result.add(MarkdownHtmlImage(range, normalizeHtmlImageDestination(image.attr("src")), sourceValue))
        }
    }

    for (element in state.elements) {
        when (val node = element.node) {
            is HtmlBlock -> if (overlaps(node) && node.literal.contains("<img", ignoreCase = true)) addImages(node.sourceSpans)
            is HtmlInline -> {
                var scope = node.parent
                while (scope != null && scope !is Block && scope !is TableCell) scope = scope.parent
                val context = scope?.takeIf { it.sourceSpans.isNotEmpty() } ?: node
                if (overlaps(context)) inlineScopes.getOrPut(context) { ArrayList() }.add(node)
            }
        }
    }
    for (nodes in inlineScopes.values) {
        val images = nodes.filter { overlaps(it) && it.literal.startsWith("<img", ignoreCase = true) }
        if (images.isEmpty()) continue
        // CommonMark 会拆开行内原始文本容器和其中的标签，保留同一块内的解析上下文。
        if (nodes.any { rawTextOpening.containsMatchIn(it.literal) }) addImages(nodes.flatMap { it.sourceSpans })
        else images.forEach { addImages(it.sourceSpans) }
    }
    return result
}

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
    val htmlSource = MarkdownHtmlSource(state.text, node.sourceSpans)
    val source = htmlSource.text
    val result = ArrayList<HtmlBlock>()
    fun add(start: Int, end: Int) {
        if (start < 0 || end <= start || end > source.length) return
        val mapped = htmlSource.sourceSpans(start, end)
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

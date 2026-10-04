package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.node.*

/** 同一源码的不可变结构快照，供排版、键盘操作、目录和定位共用。 */
class MarkdownStructureSnapshot(val text: String, val root: Node) {
    data class Range(val start: Int, val end: Int)
    data class Element(val node: Node, val parent: Int, val ranges: List<Range>)
    data class HeadingEntry(val level: Int, val title: String, val line: Int, val anchor: String)
    data class SyntaxGroup(val start: Int, val end: Int, val markers: List<Range>)
    data class Line(
        val start: Int, val end: Int, val heading: Int = 0, val quoteDepth: Int = 0,
        val listDepth: Int = 0, val code: Boolean = false, val rule: Boolean = false,
        val taskOffset: Int = -1, val blockStart: Boolean = false, val blockEnd: Boolean = false,
        val codeEdge: String? = null, val codeEmpty: Boolean = false
    )

    val lines: List<Line>
    val elements: List<Element>
    val markers: List<Range>
    val headings: List<HeadingEntry>
    val syntaxGroups: List<SyntaxGroup>
    private val protectedRanges = ArrayList<Range>()

    init {
        val lineData = ArrayList<Line>()
        var start = 0
        do {
            val next = text.indexOf('\n', start)
            val end = if (next < 0) text.length else next
            lineData.add(Line(start, end))
            start = if (next < 0) text.length + 1 else next + 1
        } while (start <= text.length)
        val nodes = ArrayList<Element>()
        val syntax = ArrayList<Range>()
        val groups = ArrayList<SyntaxGroup>()
        val titles = ArrayList<HeadingEntry>()
        val anchors = HashMap<String, Int>()
        val usedAnchors = HashSet<String>()

        fun mark(begin: Int, end: Int) {
            if (begin >= 0 && end > begin && end <= text.length) syntax.add(Range(begin, end))
        }
        fun descendants(node: Node): String = buildString {
            fun appendNode(value: Node) {
                when (value) {
                    is Text -> append(value.literal)
                    is Code -> append(value.literal)
                    is MarkdownMath -> append(value.literal)
                    is MarkdownMathBlock -> append(value.literal)
                    is SoftLineBreak, is HardLineBreak -> append(' ')
                }
                var child = value.firstChild
                while (child != null) { appendNode(child); child = child.next }
            }
            appendNode(node)
        }
        fun visit(node: Node, parent: Int, quote: Int, list: Int) {
            val ranges = node.sourceSpans.map { Range(it.inputIndex, it.inputIndex + it.length) }
            val id = nodes.size
            nodes.add(Element(node, parent, ranges))
            val quoteDepth = quote + if (node is BlockQuote) 1 else 0
            val listDepth = list + if (node is ListItem) 1 else 0
            val literal = node is Code || node is FencedCodeBlock || node is IndentedCodeBlock || node is HtmlBlock || node is MarkdownMath || node is MarkdownMathBlock || node is org.commonmark.ext.front.matter.YamlFrontMatterBlock
            if (literal) protectedRanges.addAll(ranges)
            if (node is Block) {
                val first = node.sourceSpans.firstOrNull()?.lineIndex
                val last = node.sourceSpans.lastOrNull()?.lineIndex
                if (first != null && last != null) for (index in first..last) {
                    if (index !in lineData.indices) continue
                    val old = lineData[index]
                    lineData[index] = old.copy(
                        heading = if (node is Heading) node.level else old.heading,
                        quoteDepth = Math.max(old.quoteDepth, quoteDepth),
                        listDepth = Math.max(old.listDepth, listDepth),
                        code = old.code || node is FencedCodeBlock || node is IndentedCodeBlock,
                        codeEdge = if (node is FencedCodeBlock || node is IndentedCodeBlock) {
                            when { first == last -> "single"; index == first -> "first"; index == last -> "last"; else -> "mid" }
                        } else old.codeEdge,
                        codeEmpty = old.codeEmpty || ((node is FencedCodeBlock || node is IndentedCodeBlock)
                            && text.substring(old.start, old.end).isBlank()),
                        rule = old.rule || node is ThematicBreak,
                        blockStart = old.blockStart || (index == first && node is Paragraph),
                        blockEnd = old.blockEnd || (index == last && node is Paragraph)
                    )
                }
            }
            val first = node.sourceSpans.firstOrNull()
            val last = node.sourceSpans.lastOrNull()
            if (first != null && last != null) {
                val begin = first.inputIndex
                val end = last.inputIndex + last.length
                val markerStart = syntax.size
                when (node) {
                    is MarkdownMathBlock -> {
                        val opening = text.substring(begin, begin + first.length).indexOf("$$")
                        if (opening >= 0) mark(begin + opening, begin + opening + 2)
                        if (node.closed) {
                            val closing = Regex("\\$\\$[ \t]*$").find(text.substring(last.inputIndex, end))
                            if (closing != null) mark(last.inputIndex + closing.range.first, last.inputIndex + closing.range.first + 2)
                        }
                    }
                    is MarkdownMath -> {
                        val count = if (node.display) 2 else 1
                        mark(begin, begin + count); mark(end - count, end)
                    }
                    is MarkdownDecoration -> {
                        mark(begin, begin + node.delimiter.length); mark(end - node.delimiter.length, end)
                    }
                    is Heading -> {
                        val title = descendants(node)
                        val base = title.lowercase(java.util.Locale.ROOT).trim()
                            .replace(Regex("[^\\p{L}\\p{N}_\\-\\s]"), "").replace(Regex("\\s+"), "-")
                        var occurrence = anchors.getOrDefault(base, 0)
                        var anchor = base + if (occurrence == 0) "" else "-$occurrence"
                        while (!usedAnchors.add(anchor)) {
                            occurrence++
                            anchor = "$base-$occurrence"
                        }
                        anchors[base] = occurrence + 1
                        titles.add(HeadingEntry(node.level, title, first.lineIndex, anchor))
                        val atx = Regex("^[ \\t]*#{1,6}(?=[ \\t]|$)").find(text.substring(begin, begin + first.length))
                        if (atx != null) {
                            var content = begin + atx.range.last + 1
                            while (content < begin + first.length && (text[content] == ' ' || text[content] == '\t')) content++
                            mark(begin + atx.range.first, content)
                            val close = Regex("[ \\t]+#+[ \\t]*$").find(text.substring(begin, begin + first.length))
                            if (close != null) mark(begin + close.range.first, begin + close.range.last + 1)
                        } else mark(last.inputIndex, end)
                    }
                    is BlockQuote -> node.sourceSpans.forEach { span ->
                        val source = text.substring(span.inputIndex, span.inputIndex + span.length)
                        val match = Regex("^[ \\t]*>").find(source)
                        if (match != null) {
                            val marker = span.inputIndex + match.range.last
                            val after = marker + 1
                            mark(marker, after + if (text.getOrNull(after) == ' ' || text.getOrNull(after) == '\t') 1 else 0)
                        }
                    }
                    is ListItem -> {
                        val source = text.substring(begin, begin + first.length)
                        val item = Regex("^[ \\t]*(?:[-+*]|[0-9]{1,9}[.)])(?=[ \\t]|$)").find(source)
                        if (item != null) {
                            mark(begin + item.range.first, begin + item.range.last + 1)
                            val task = Regex("^[ \\t]+\\[([ xX])]([ \\t]|$)").find(source.substring(item.value.length))
                            if (task != null) {
                                val offset = begin + item.value.length + task.groups[1]!!.range.first
                                lineData[first.lineIndex] = lineData[first.lineIndex].copy(taskOffset = offset)
                            }
                        }
                    }
                    is Code, is Emphasis, is StrongEmphasis, is Strikethrough -> {
                        val delimiter = when (node) { is Code -> '`'; is Strikethrough -> '~'; else -> text[begin] }
                        val count = when (node) { is StrongEmphasis, is Strikethrough -> 2; is Code -> {
                            var offset = begin
                            while (offset < end && text[offset] == '`') offset++
                            offset - begin
                        }; else -> 1 }
                        if (count > 0 && end - begin >= count * 2 && text[begin] == delimiter) {
                            val body = text.substring(begin + count, end - count)
                            val padding = if (node is Code && body.startsWith(' ') && body.endsWith(' ')
                                && body.any { it != ' ' && it != '\n' && it != '\r' }) 1 else 0
                            mark(begin, begin + count + padding); mark(end - count - padding, end)
                        }
                    }
                    is FencedCodeBlock -> {
                        mark(begin, begin + first.length)
                        if (node.closingFenceLength != null) mark(last.inputIndex, end)
                    }
                    is ThematicBreak, is LinkReferenceDefinition -> ranges.forEach { mark(it.start, it.end) }
                    is Link, is Image -> {
                        // 标签外语法与内容分开记录，强调和字面符号由各自节点处理。
                        if (text.getOrNull(begin) == '<' && text.getOrNull(end - 1) == '>') {
                            mark(begin, begin + 1); mark(end - 1, end)
                        } else if (text.getOrNull(begin) == '[' || text.startsWith("![", begin)) {
                            val labelStart = begin + if (node is Image) 2 else 1
                            mark(begin, labelStart)
                            val labelEnd = node.lastChild?.sourceSpans?.lastOrNull()?.let { it.inputIndex + it.length }
                                ?: if (text.getOrNull(labelStart) == ']') labelStart else null
                            if (labelEnd != null && labelEnd < end) mark(labelEnd, end)
                        }
                    }
                }
                // 列表保留可见项目符号；未闭合代码和未原生渲染的公式仍显示完整源码。
                val collapsible = node is Heading || node is BlockQuote || node is Code || node is Emphasis ||
                    node is StrongEmphasis || node is Strikethrough || node is MarkdownDecoration ||
                    node is Link || node is ThematicBreak || node is FencedCodeBlock && node.closingFenceLength != null
                if (collapsible && syntax.size > markerStart) {
                    groups.add(SyntaxGroup(begin, end, syntax.subList(markerStart, syntax.size).toList()))
                }
            }
            var child = node.firstChild
            while (child != null) { visit(child, id, quoteDepth, listDepth); child = child.next }
        }
        visit(root, -1, 0, 0)
        lines = lineData.toList()
        elements = nodes.toList()
        markers = syntax.distinct().sortedBy { it.start }
        headings = titles.toList()
        syntaxGroups = groups.toList()
    }

    fun lineAt(position: Int): Int {
        var low = 0
        var high = lines.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (lines[mid].start <= position) low = mid else high = mid - 1
        }
        return low
    }

    fun isLiteral(position: Int): Boolean = protectedRanges.any { position >= it.start && position < it.end }

    fun linkAt(position: Int): Link? = elements.firstOrNull { element ->
        element.node is Link && element.ranges.any { position >= it.start && position < it.end }
    }?.node as? Link
}

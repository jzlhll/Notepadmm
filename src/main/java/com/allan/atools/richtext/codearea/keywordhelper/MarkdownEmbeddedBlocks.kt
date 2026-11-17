package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.*
import java.util.TreeMap

/** 只收集技术内容自身；正文和容器始终保留原生段落，行内内容的预览另起一行。 */
object MarkdownEmbeddedBlocks {
    data class Block(
        val start: Int, val end: Int, val firstLine: Int, val lastLine: Int,
        val nodes: List<Node> = emptyList(), val replacesSource: Boolean = true,
        val detailStarts: List<Int> = emptyList()
    ) {
        val graphicLine: Int get() = if (replacesSource) firstLine else lastLine
    }

    fun collect(state: MarkdownStructureSnapshot): List<Block> {
        val result = TreeMap<Int, Block>()
        state.details.forEach {
            val replacesSource = state.text.substring(state.lines[it.firstLine].start, it.start).isBlank() &&
                state.text.substring(it.end, state.lines[it.lastLine].end).isBlank()
            result[it.start] = Block(it.start, it.end, it.firstLine, it.lastLine,
                replacesSource = replacesSource, detailStarts = listOf(it.start))
        }
        val referencedNotes = state.elements.mapNotNull { (it.node as? FootnoteReference)?.label }.toSet()

        fun add(node: Node, endNode: Node = node, replace: Boolean = false) {
            val first = node.sourceSpans.firstOrNull() ?: return
            val last = endNode.sourceSpans.lastOrNull { it.length > 0 } ?: return
            val start = first.inputIndex
            val end = last.inputIndex + last.length
            if (end <= start || end - start > 64_000 || last.lineIndex - first.lineIndex >= 500) return
            if ((result.floorEntry(start)?.value?.end ?: start) > start ||
                (result.ceilingEntry(start)?.key ?: end) < end) return
            // 同行有正文或列表／引用前缀时不隐藏源码，预览放到本行文字下方。
            val replacesSource = replace && state.text.substring(state.lines[first.lineIndex].start, start).isBlank() &&
                state.text.substring(end, state.lines[last.lineIndex].end).isBlank()
            result[start] = Block(start, end, first.lineIndex, last.lineIndex, listOf(node), replacesSource)
        }

        fun tableNeedsWeb(node: Node): Boolean {
            if (node is MarkdownMath || node is FootnoteReference ||
                node is MarkdownDecoration && node.tag in setOf("sub", "sup")) return true
            // 原生表格已经支持图片和 br，不能因此接管表格外的内容。
            if (node is HtmlInline && !node.literal.trim().matches(Regex("(?is)<(?:br|img)\\b[^>]*?/?>"))) return true
            var child = node.firstChild
            while (child != null) { if (tableNeedsWeb(child)) return true; child = child.next }
            return false
        }

        fun visit(parent: Node) {
            var node = parent.firstChild
            while (node != null) {
                var next = node.next
                val start = node.sourceSpans.firstOrNull()?.inputIndex
                val covered = start != null && result.floorEntry(start)?.value?.let { start < it.end } == true
                if (!covered) when (node) {
                    is TableBlock -> if (tableNeedsWeb(node)) add(node, replace = true)
                    is FencedCodeBlock, is IndentedCodeBlock, is Code -> Unit
                    is FootnoteDefinition -> if (node.label in referencedNotes) add(node, replace = true)
                    is MarkdownMathBlock -> if (node.closed) add(node, replace = true)
                    is HtmlBlock -> collectMarkdownHtmlFragments(state, node).forEach { add(it, replace = true) }
                    is Image -> if (!state.isStandaloneImageLine(node) || node.destination.startsWith("data:", true)) add(node)
                    is MarkdownMath, is FootnoteReference -> add(node)
                    is MarkdownDecoration -> if (node.tag in setOf("sub", "sup")) add(node) else visit(node)
                    is HtmlInline -> {
                        val end = markdownHtmlInlineEnd(node)
                        if (end != null) { add(node, end); next = end.next }
                    }
                    is Paragraph -> {
                        val source = node.sourceSpans.joinToString("\n") {
                            state.text.substring(it.inputIndex, it.inputIndex + it.length)
                        }
                        if (source.trim().equals("[TOC]", true)) add(node, replace = true) else visit(node)
                    }
                    else -> visit(node)
                }
                node = next
            }
        }
        visit(state.root)
        val blocks = TreeMap<Int, Block>()
        val supplements = TreeMap<Int, Block>()
        for (block in result.values) {
            if (block.replacesSource) blocks[block.start] = block else {
                val previous = supplements[block.graphicLine]
                supplements[block.graphicLine] = if (previous == null) block else previous.copy(
                    end = block.end, nodes = previous.nodes + block.nodes, detailStarts = previous.detailStarts + block.detailStarts
                )
            }
        }
        supplements.values.forEach { blocks[it.start] = it }
        return blocks.values.filter { it.end - it.start <= 64_000 && it.lastLine - it.firstLine < 500 }
    }
}

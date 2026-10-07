package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.node.*

/** 需要完整块布局的内容在编辑区内呈现，编辑、选择或搜索时恢复对应源码。 */
object MarkdownEmbeddedBlocks {
    data class Block(val start: Int, val end: Int, val firstLine: Int, val lastLine: Int)

    fun collect(state: MarkdownStructureSnapshot): List<Block> {
        val result = state.details.map { Block(it.start, it.end, it.firstLine, it.lastLine) }.toMutableList()
        fun technical(node: Node): Boolean {
            if (node is MarkdownMath || node is FootnoteReference || node is Image || node is HtmlInline ||
                node is MarkdownDecoration && node.tag in setOf("sub", "sup")) return true
            var child = node.firstChild
            while (child != null) { if (technical(child)) return true; child = child.next }
            return false
        }
        val referencedNotes = state.elements.mapNotNull { (it.node as? FootnoteReference)?.label }.toSet()
        var node = state.root.firstChild
        while (node != null) {
            val first = node.sourceSpans.firstOrNull()
            val last = node.sourceSpans.lastOrNull()
            if (first != null && last != null) {
                val start = state.lines[first.lineIndex].start
                val end = state.lines[last.lineIndex].end
                val standaloneImage = node is Paragraph && (node.firstChild is Image || node.firstChild is HtmlInline &&
                    (node.firstChild as HtmlInline).literal.trim().startsWith("<img", true)) && node.firstChild.next == null
                val needed = node is BlockQuote || node is ListBlock || node is FootnoteDefinition && node.label in referencedNotes || node is HtmlBlock ||
                    node is MarkdownMathBlock && node.closed || node is Paragraph &&
                    (!standaloneImage && technical(node) || state.text.substring(start, end).trim().equals("[TOC]", true))
                if (needed && result.none { start < it.end && end > it.start }) {
                    result.add(Block(start, end, first.lineIndex, last.lineIndex))
                }
            }
            node = node.next
        }
        // 大块保留源码，避免一个预览控件承载无界高度和大量 DOM。
        return result.filter { it.end - it.start <= 64_000 && it.lastLine - it.firstLine < 500 }.sortedBy { it.start }
    }
}

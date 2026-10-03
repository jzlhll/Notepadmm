package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot

/** 只更新当前版本的段落装饰，不替换源码或创建文本撤销记录。 */
class MarkdownPresentation(private val area: EditorArea) {
    private var styledLines = emptySet<Int>()
    var snapshot: MarkdownStructureSnapshot? = null
        private set

    fun apply(value: MarkdownStructureSnapshot) {
        snapshot = value
        val next = HashSet<Int>()
        for ((index, line) in value.lines.withIndex()) {
            val classes = ArrayList<String>()
            if (area.markdownPreviewEnabled) {
                classes.add("md-body-paragraph")
                if (line.heading > 0) classes.add("md-heading-${line.heading}")
                if (line.rule) classes.add("md-thematic-break")
                if (line.quoteDepth > 0) classes.add("md-quote-paragraph")
                if (line.listDepth > 0) classes.add("md-list-paragraph")
                if (line.taskOffset >= 0) classes.add("md-task-paragraph")
                val top = if (line.heading > 0 || line.blockStart) 6 else 0
                val bottom = if (line.blockEnd) 5 else 0
                classes.add("md-layout:${line.quoteDepth}:${line.listDepth}:$top:$bottom:${line.code}")
            }
            merge(index, classes)
            if (classes.isNotEmpty()) next.add(index)
        }
        (styledLines - next).forEach { merge(it, emptyList()) }
        styledLines = next
        area.requestLayout()
    }

    private fun merge(index: Int, classes: List<String>) {
        if (index !in area.paragraphs.indices) return
        val old = area.getParagraph(index).paragraphStyle
        val merged = old.filterNot { it.startsWith("md-layout:") || it in OWNED_CLASSES || it.startsWith("md-heading-") } + classes
        if (old != merged) area.setParagraphStyle(index, merged)
    }

    fun clear() {
        styledLines.forEach { merge(it, emptyList()) }
        styledLines = emptySet()
        snapshot = null
    }

    companion object {
        private val OWNED_CLASSES = setOf("md-body-paragraph", "md-thematic-break", "md-quote-paragraph",
            "md-list-paragraph", "md-task-paragraph")
    }
}

package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot

/** 只更新当前版本的段落装饰，不替换源码或创建文本撤销记录。 */
class MarkdownPresentation(private val area: EditorArea) {
    private val appliedLines = ArrayList<MarkdownStructureSnapshot.Line?>()
    private var appliedPreview: Boolean? = null
    var snapshot: MarkdownStructureSnapshot? = null
        private set

    fun apply(value: MarkdownStructureSnapshot) {
        snapshot = value
        val preview = area.markdownPreviewEnabled
        for ((index, line) in value.lines.withIndex()) {
            val previous = appliedLines.getOrNull(index)
            if (appliedPreview == preview && previous != null && sameLayout(previous, line)) continue
            val classes = ArrayList<String>()
            if (preview) {
                classes.add("md-body-paragraph")
                if (line.heading > 0) classes.add("md-heading-${line.heading}")
                if (line.rule) classes.add("md-thematic-break")
                if (line.quoteDepth > 0) classes.add("md-quote-paragraph")
                if (line.listDepth > 0) classes.add("md-list-paragraph")
                if (line.taskOffset >= 0) classes.add("md-task-paragraph")
                line.codeEdge?.let { classes.add("md-code-block-$it") }
                if (line.codeEmpty) classes.add("md-code-block-empty")
                val top = if (line.heading > 0 || line.blockStart) 6 else 0
                val bottom = if (line.blockEnd) 5 else 0
                classes.add("md-layout:${line.quoteDepth}:${line.listDepth}:$top:$bottom:${line.code}")
            }
            merge(index, classes)
        }
        appliedLines.clear()
        appliedLines.addAll(value.lines)
        appliedPreview = preview
        area.markdownSyntax.apply(value)
    }

    /** 段落样式随文本移动；只失效编辑触及的段落，结构变化再由新快照扩展更新范围。 */
    fun onTextChanged(position: Int, removed: String, inserted: String) {
        if (appliedLines.isEmpty()) return
        val first = area.offsetToPosition(Math.min(position, area.length), org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).major
        if (first >= appliedLines.size) return
        val count = Math.min(removed.count { it == '\n' } + 1, appliedLines.size - first)
        appliedLines.subList(first, first + count).clear()
        appliedLines.addAll(first, List(inserted.count { it == '\n' } + 1) { null })
    }

    private fun sameLayout(a: MarkdownStructureSnapshot.Line, b: MarkdownStructureSnapshot.Line) =
        a.heading == b.heading && a.quoteDepth == b.quoteDepth && a.listDepth == b.listDepth &&
            a.code == b.code && a.rule == b.rule && (a.taskOffset >= 0) == (b.taskOffset >= 0) &&
            a.blockStart == b.blockStart && a.blockEnd == b.blockEnd &&
            a.codeEdge == b.codeEdge && a.codeEmpty == b.codeEmpty

    private fun merge(index: Int, classes: List<String>) {
        if (index !in area.paragraphs.indices) return
        val old = area.getParagraph(index).paragraphStyle
        val merged = old.filterNot { it.startsWith("md-layout:") || it in OWNED_CLASSES ||
            it.startsWith("md-heading-") || it.startsWith("md-code-block-") } + classes
        if (old != merged) area.setParagraphStyle(index, merged)
    }

    fun clear() {
        area.markdownSyntax.clear()
        if (appliedPreview != null) area.paragraphs.indices.forEach { merge(it, emptyList()) }
        appliedLines.clear()
        appliedPreview = null
        snapshot = null
    }

    companion object {
        private val OWNED_CLASSES = setOf("md-body-paragraph", "md-thematic-break", "md-quote-paragraph",
            "md-list-paragraph", "md-task-paragraph")
    }
}

package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot

/** 只更新当前版本的段落装饰，不替换源码或创建文本撤销记录。 */
class MarkdownPresentation(private val area: EditorArea) {
    private val details = MarkdownDetailsPresentation(area)
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
                if (line.frontMatter) classes.add("md-front-matter")
                if (line.frontMatterDelimiter) classes.add("md-front-matter-delimiter")
                line.frontMatterEdge?.let { classes.add("md-front-matter-$it") }
                val top = if (line.frontMatterEdge == "first" || line.frontMatterEdge == "single") 14
                    else if (line.heading > 0 || line.blockStart) 6 else 0
                val bottom = if (line.frontMatterEdge == "last" || line.frontMatterEdge == "single") 14
                    else if (line.blockEnd) 5 else 0
                classes.add("md-layout:${line.quoteDepth}:${line.listDepth}:$top:$bottom:${line.code || line.frontMatter}")
            }
            merge(index, classes)
        }
        appliedLines.clear()
        appliedLines.addAll(value.lines)
        appliedPreview = preview
        area.markdownSyntax.apply(value)
        if (preview) details.apply(value) else details.clear()
    }

    /** 段落样式随文本移动；只失效编辑触及的段落，结构变化再由新快照扩展更新范围。 */
    fun onTextChanged(position: Int, removed: String, inserted: String) {
        details.clear()
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
            a.codeEdge == b.codeEdge && a.codeEmpty == b.codeEmpty && a.frontMatter == b.frontMatter &&
            a.frontMatterEdge == b.frontMatterEdge && a.frontMatterDelimiter == b.frontMatterDelimiter

    /** 隐藏分隔符时同时收起段落高度，进入元数据或命中搜索后恢复源码行。 */
    fun refreshFrontMatterDelimiters() {
        val state = snapshot ?: return
        if (!area.markdownPreviewEnabled) return
        for (block in state.frontMatter) {
            if (!block.closed) continue
            for (index in listOf(block.firstLine, block.lastLine)) {
                if (index !in area.paragraphs.indices) continue
                val line = state.lines[index]
                val visible = area.getStyleSpans(line.start, line.end).any { span ->
                    "markdown-syntax-expanded" in span.style || "search" in span.style || "temporary" in span.style
                }
                val old = area.getParagraph(index).paragraphStyle
                val styles = old.filterNot { it == "md-front-matter-delimiter-collapsed" }.toMutableList()
                if (!visible) styles.add("md-front-matter-delimiter-collapsed")
                if (old != styles) area.setParagraphStyle(index, styles)
            }
        }
    }

    private fun merge(index: Int, classes: List<String>) {
        if (index !in area.paragraphs.indices) return
        val old = area.getParagraph(index).paragraphStyle
        val merged = old.filterNot { it.startsWith("md-layout:") || it in OWNED_CLASSES ||
            it.startsWith("md-heading-") || it.startsWith("md-code-block-") || it.startsWith("md-front-matter") } + classes
        if (old != merged) area.setParagraphStyle(index, merged)
    }

    fun clear() {
        details.clear()
        area.markdownSyntax.clear()
        if (appliedPreview != null) area.paragraphs.indices.forEach { merge(it, emptyList()) }
        appliedLines.clear()
        appliedPreview = null
        snapshot = null
    }

    fun destroy() {
        clear()
        details.destroy()
    }

    companion object {
        private val OWNED_CLASSES = setOf("md-body-paragraph", "md-thematic-break", "md-quote-paragraph",
            "md-list-paragraph", "md-task-paragraph")
    }
}

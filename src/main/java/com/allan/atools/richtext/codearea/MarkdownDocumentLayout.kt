package com.allan.atools.richtext.codearea

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Insets
import java.util.Locale

/** Markdown 正文限制最大宽度，窄窗口保留最小留白；代码和普通文本沿用原边距。 */
class MarkdownDocumentLayout(private val area: EditorArea) {
    companion object {
        private const val MAX_TEXT_WIDTH = 1250.0
        private const val MIN_MARGIN = 48.0
        private const val DEFAULT_MARGIN = 85.0
        private val extensions = setOf(
            "md", "markdown", "mdown", "mmd", "rmarkdown", "mkd", "mdwn",
            "mdtxt", "rmd", "qmd", "mdtext", "mdx"
        )
    }
    private var markdown = false
    private val margin = SimpleDoubleProperty(DEFAULT_MARGIN)

    init {
        // 固定由布局参数驱动，避免主题或字号触发 CSS 时把动态边距覆盖为静态值。
        area.paddingProperty().bind(Bindings.createObjectBinding(
            { Insets(0.0, margin.get(), 0.0, 0.0) }, margin
        ))
    }

    fun apply(fileName: String) {
        markdown = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions
        val styleClass = "markdown-document-editor"
        if (markdown) {
            if (styleClass !in area.styleClass) area.styleClass.add(styleClass)
        } else area.styleClass.remove(styleClass)
        refresh()
    }

    fun refresh() {
        val padding = area.padding
        val desiredMargin = if (markdown) {
            val gutterWidth = area.lookup(".lineno")?.prefWidth(-1.0) ?: 0.0
            // 编辑区已扣除外部滚动条；再扣掉行号与自身边框，左右留白围绕正文对称。
            val available = area.width - area.insets.left - (area.insets.right - padding.right) - gutterWidth
            val desired = Math.ceil((available - MAX_TEXT_WIDTH) / 2.0)
            if (desired > MIN_MARGIN) desired else MIN_MARGIN
        } else DEFAULT_MARGIN
        if (margin.get() != desiredMargin) margin.set(desiredMargin)
    }
}

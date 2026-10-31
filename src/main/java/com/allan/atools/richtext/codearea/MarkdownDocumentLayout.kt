package com.allan.atools.richtext.codearea

import com.allan.atools.SettingPreferences
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Insets
import javafx.scene.control.Label
import java.util.Locale

/** 按文档类型和编辑区尺寸动态留白，分别限制水平和垂直留白范围。 */
class MarkdownDocumentLayout(private val area: EditorArea) {
    companion object {
        private const val CONTENT_WIDTH_BASELINE = 1250.0
        private const val TEXT_MIN_MARGIN = 48.0
        private const val TEXT_MAX_MARGIN = 120.0
        private const val DEFAULT_MIN_MARGIN = 36.0
        private const val DEFAULT_MAX_MARGIN = 66.0
        private const val MIN_VERTICAL_PADDING = 36.0
        private const val TEXT_MAX_VERTICAL_PADDING = 80.0
        private const val DEFAULT_MAX_VERTICAL_PADDING = 56.0
        private const val TEXT_VERTICAL_PADDING_RATIO = 0.075
        private const val DEFAULT_VERTICAL_PADDING_RATIO = 0.06
        private val extensions = setOf(
            "md", "markdown", "mdown", "mmd", "rmarkdown", "mkd", "mdwn",
            "mdtxt", "rmd", "qmd", "mdtext", "mdx"
        )
    }
    private val margin = SimpleDoubleProperty(DEFAULT_MIN_MARGIN)
    private var textDocument = false
    private var gutterWidth = 0.0
    private val verticalPadding = SimpleDoubleProperty(MIN_VERTICAL_PADDING)

    init {
        // 固定由布局参数驱动，避免主题或字号触发 CSS 时把动态边距覆盖为静态值。
        area.paddingProperty().bind(Bindings.createObjectBinding(
            { Insets(verticalPadding.get(), margin.get(), verticalPadding.get(), 0.0) },
            margin, verticalPadding
        ))
    }

    fun apply(documentState: EditorDocumentState) {
        val extension = documentState.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        textDocument = documentState.isUntitled || extension in extensions || extension == "txt"
        val styleClass = "markdown-document-editor"
        if (textDocument) {
            if (styleClass !in area.styleClass) area.styleClass.add(styleClass)
        } else area.styleClass.remove(styleClass)
        refresh()
    }

    /** 留白与整栏背景共用有效宽度，输入法替换段落时不采用临时缺失或空白行号的尺寸。 */
    fun lineNumberWidth(): Double {
        // Markdown 装饰器也会保留图形工厂，关闭行号时仍需清除整栏背景宽度。
        if (area.paragraphGraphicFactory == null
            || !SettingPreferences.getBoolean(SettingPreferences.editorLineNumberVisibleKey)
        ) gutterWidth = 0.0
        else {
            val lineNumber = area.lookup(".lineno") as? Label
            if (lineNumber != null && !lineNumber.text.isNullOrEmpty()) {
                val width = lineNumber.layoutBounds.width
                if (width > 0.0) gutterWidth = width
            }
        }
        return gutterWidth
    }

    fun refresh() {
        val padding = area.padding
        val viewport = area.parent as? EditorZoomViewport
        // 纵向滚动条显隐不能改变留白，否则两侧取整可能让变窄的视口反而多出正文宽度。
        val layoutWidth = viewport?.documentLayoutWidth ?: area.width
        val availableWidth = layoutWidth - area.insets.left - (area.insets.right - padding.right) - lineNumberWidth()
        val minMargin = if (textDocument) TEXT_MIN_MARGIN else DEFAULT_MIN_MARGIN
        val maxMargin = if (textDocument) TEXT_MAX_MARGIN else DEFAULT_MAX_MARGIN
        // 基准宽度仅用于计算留白；留白达到上限后，正文继续随窗口增宽。
        val calculatedMargin = Math.ceil((availableWidth - CONTENT_WIDTH_BASELINE) / 2.0)
        val desiredMargin = when {
            calculatedMargin < minMargin -> minMargin
            calculatedMargin > maxMargin -> maxMargin
            else -> calculatedMargin
        }
        if (margin.get() != desiredMargin) margin.set(desiredMargin)

        // 按外层容器完整高度计算留白，横向滚动条只缩小视口，不参与留白计算。
        // 否则显隐滚动条会改变两侧取整后的 padding，临界行高下会反复切换滚动条。
        val layoutHeight = viewport?.documentLayoutHeight ?: area.height
        val availableHeight = layoutHeight - (area.insets.top - padding.top) - (area.insets.bottom - padding.bottom)
        val paddingRatio = if (textDocument) TEXT_VERTICAL_PADDING_RATIO else DEFAULT_VERTICAL_PADDING_RATIO
        val maxPadding = if (textDocument) TEXT_MAX_VERTICAL_PADDING else DEFAULT_MAX_VERTICAL_PADDING
        val desiredPadding = Math.ceil(availableHeight * paddingRatio)
        val nextPadding = when {
            desiredPadding < MIN_VERTICAL_PADDING -> MIN_VERTICAL_PADDING
            desiredPadding > maxPadding -> maxPadding
            else -> desiredPadding
        }
        if (verticalPadding.get() != nextPadding) verticalPadding.set(nextPadding)
    }
}

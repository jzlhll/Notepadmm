package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownEmojiShortcodes
import javafx.geometry.VPos
import javafx.scene.layout.Pane
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment

/** 普通单元格与 TextArea 一样使用独立 Text，保持换行、行高和字体回退一致。 */
class MarkdownTablePlainText : Pane() {
    private val text = Text().apply {
        styleClass.setAll("text", "markdown-table-preview-content")
        textOrigin = VPos.TOP
    }

    init {
        setMinSize(0.0, 0.0)
        isMouseTransparent = true
        children.add(text)
        text.wrappingWidthProperty().bind(widthProperty())
    }

    fun apply(cell: MarkdownTableLayout.Cell, alignment: TextAlignment) {
        text.text = cell.plainText
        if (text.font != cell.textFont) text.font = cell.textFont
        if (cell.header) {
            if ("markdown-bold" !in text.styleClass) text.styleClass.add("markdown-bold")
        } else text.styleClass.remove("markdown-bold")
        text.textAlignment = alignment
    }

    companion object {
        @JvmStatic
        fun plainText(source: String, runs: List<MarkdownTableLayout.Run>): String? {
            if (runs.any { it.bold() || it.italic() || it.strike() || it.link() || it.code() || it.image() != null }) return null
            val value = MarkdownTableCellText.decode(source)
            if (value.isNotEmpty() && runs.joinToString("") { it.text() } != value) return null
            var offset = 0
            while (offset < value.length) {
                // 表情继续使用 TextFlow 的字体分段和高清覆盖渲染。
                if (MarkdownEmojiShortcodes.unicodeEnd(value, offset) > offset) return null
                offset += Character.charCount(value.codePointAt(offset))
            }
            return value
        }

        @JvmStatic
        fun measureHeight(measure: Text, value: String, font: Font, width: Double): Double {
            measure.font = font
            // 不追加零宽字符；它会改变 JavaFX 的文本度量，使中文行高与实际编辑内容不同。
            measure.text = value.ifEmpty { " " }
            measure.wrappingWidth = Math.max(1.0, width - MarkdownTableLayout.HORIZONTAL_INSETS)
            return Math.ceil(measure.layoutBounds.height + MarkdownTableLayout.VERTICAL_INSETS)
        }
    }
}

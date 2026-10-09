package com.allan.atools.tools.modulenotepad.manager

import javafx.scene.shape.Path
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.fxmisc.richtext.TextExt
import java.util.IdentityHashMap

/** 表格预览按实际换行范围绘制行内代码背景，复用正文的主题样式。 */
class MarkdownTableTextFlow : TextFlow() {
    private val backgrounds = IdentityHashMap<TextExt, Path>()

    fun clearCodeBackgrounds() {
        backgrounds.values.forEach { it.fillProperty().unbind() }
        children.removeAll(backgrounds.values)
        backgrounds.clear()
    }

    override fun layoutChildren() {
        super.layoutChildren()
        var offset = 0
        val added = ArrayList<Path>()
        for (node in childrenUnmodifiable) {
            if (!node.isManaged) continue
            // TextFlow 将图片等内嵌节点作为一个字符参与范围计算。
            val length = if (node is Text) node.text.length else 1
            if (node is TextExt && length > 0 && "markdown-inline-code" in node.styleClass) {
                val background = backgrounds.getOrPut(node) {
                    Path().apply {
                        isManaged = false
                        isMouseTransparent = true
                        viewOrder = 1.0
                        stroke = null
                        fillProperty().bind(node.backgroundColorProperty())
                        added.add(this)
                    }
                }
                background.elements.setAll(*rangeShape(offset, offset + length))
            }
            offset += length
        }
        children.addAll(added)
    }
}

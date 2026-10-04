package com.allan.atools.tools.modulenotepad.manager

import javafx.beans.InvalidationListener
import javafx.scene.Node
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import java.util.function.DoubleConsumer

/** 图片从当前源码文字的实际下沿开始排版，并回填段落高度，避免字号、留白或折行造成重叠。 */
class MarkdownImageGraphic(
    private val base: Node?,
    private val image: Node,
    private val fallbackSourceHeight: Double,
    private val topGap: Double,
    private val bottomGap: Double,
    private val sourceHeightChanged: DoubleConsumer
) : Pane() {
    private var source: TextFlow? = null
    private val sourceChanged = InvalidationListener {
        if (source?.isNeedsLayout == true) requestLayout()
    }

    init {
        val baseWidth = base?.prefWidth(-1.0) ?: 0.0
        minWidth = baseWidth
        prefWidth = baseWidth
        maxWidth = baseWidth
        children.add(image)
        if (base != null) children.add(base)
        image.layoutY = fallbackSourceHeight + topGap
        parentProperty().addListener { _, _, _ ->
            source?.needsLayoutProperty()?.removeListener(sourceChanged)
            source = null
            requestLayout()
        }
    }

    override fun layoutChildren() {
        super.layoutChildren()
        val flow = parent?.childrenUnmodifiable?.filterIsInstance<TextFlow>()?.firstOrNull()
        if (source !== flow) {
            source?.needsLayoutProperty()?.removeListener(sourceChanged)
            source = flow
            source?.needsLayoutProperty()?.addListener(sourceChanged)
        }
        // ParagraphText 的总高度含图片占位，只测文字节点下沿，避免把占位反复叠加。
        flow?.layout()
        val textBottom = flow?.children?.filterIsInstance<Text>()?.map { it.boundsInParent.maxY }?.maxOrNull()
        val sourceHeight = if (flow != null && textBottom != null) {
            flow.layoutY + textBottom + flow.insets.bottom + flow.lineSpacing / 2
        } else fallbackSourceHeight
        image.layoutY = sourceHeight + topGap
        if (base is Region) {
            val reserved = sourceHeight + topGap + image.prefHeight(-1.0) + bottomGap
            base.minHeight = reserved
            base.prefHeight = reserved
            base.maxHeight = reserved
        }
        if (flow != null && textBottom != null) sourceHeightChanged.accept(sourceHeight)
    }
}

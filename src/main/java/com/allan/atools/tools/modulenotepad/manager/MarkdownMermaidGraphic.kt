package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.MarkdownEditorSupport.textLeftPadding
import javafx.beans.binding.Bindings
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.shape.Rectangle

/** 流程图首段自带常显操作栏，源码与预览都在段落内预留空间并裁剪内容。 */
class MarkdownMermaidGraphic(
    area: EditorArea,
    private val base: Node?,
    private val header: HBox,
    private val view: MermaidDiagramView?,
    private val availableWidth: () -> Double
) : Pane() {
    private val viewport = Pane()

    init {
        header.isManaged = false
        viewport.isManaged = false
        viewport.isPickOnBounds = false
        viewport.children.add(header)
        viewport.clip = Rectangle().apply {
            widthProperty().bind(viewport.widthProperty())
            heightProperty().bind(viewport.heightProperty())
        }
        // 段落图形跟随行号固定在视口左侧，横向滚动源码时操作栏仍保持可见。
        viewport.layoutXProperty().bind(Bindings.createDoubleBinding(
            { width + textLeftPadding(area) },
            widthProperty(), area.paddingProperty()
        ))
        if (view != null) {
            view.isManaged = false
            viewport.children.add(view)
        }
        if (base != null) {
            if (view != null) base.opacity = 0.0
            children.add(base)
        }
        children.add(viewport)
    }

    override fun computePrefWidth(height: Double): Double = base?.prefWidth(height) ?: 0.0

    override fun layoutChildren() {
        val contentWidth = Math.max(0.0, availableWidth())
        val top = if (view == null) HEADER_HEIGHT else 0.0
        base?.resizeRelocate(0.0, top, width, Math.max(0.0, height - top))
        viewport.resize(contentWidth, if (view == null) Math.min(height, HEADER_HEIGHT) else height)
        header.resizeRelocate(0.0, 0.0, contentWidth, HEADER_HEIGHT)
        if (view != null) {
            val viewWidth = Math.min(contentWidth, view.prefWidth(-1.0))
            view.resizeRelocate(Math.max(0.0, (contentWidth - viewWidth) / 2.0), HEADER_HEIGHT + 8.0,
                viewWidth, view.prefHeight(-1.0))
        }
    }

    companion object {
        // 同时用于源码顶部留白与预览首段占高，避免切换模式后遮挡相邻段落。
        const val HEADER_HEIGHT = 28.0
    }
}

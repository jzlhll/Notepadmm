package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.MarkdownEditorSupport.textLeftPadding
import com.allan.atools.utils.Locales
import javafx.beans.binding.Bindings
import javafx.scene.Node
import javafx.scene.layout.Pane
import java.util.function.DoubleSupplier
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.shape.Rectangle

/** 操作栏属于表格首行，随文档布局和缩放，不另建悬浮窗口。 */
class MarkdownTableToolbar(val tableId: String, optimize: Runnable, toggle: Runnable, exit: Runnable) : HBox(2.0) {
    companion object {
        // 与源码首段的 CSS 顶部留白保持一致。
        const val HEIGHT = 28.0
    }

    private val optimizeButton = button("markdownTableToolbarOptimize", optimize)
    private val modeButton = button("markdownTableToolbarSource", toggle)
    private val exitButton = button("markdownTableExitAfter", exit)
    private val optimizeTip = Tooltip(Locales.str("markdownTableOptimize"))
    private val modeTip = Tooltip(Locales.str("markdownTableShowSource"))
    private val exitTip = Tooltip(Locales.str("markdownTableExitAfter"))

    init {
        styleClass.add("markdown-table-toolbar")
        alignment = Pos.CENTER_LEFT
        minWidth = 0.0
        children.addAll(optimizeButton, modeButton, exitButton)
        optimizeButton.tooltip = optimizeTip
        modeButton.tooltip = modeTip
        exitButton.tooltip = exitTip
        clip = Rectangle().apply {
            widthProperty().bind(this@MarkdownTableToolbar.widthProperty())
            heightProperty().bind(this@MarkdownTableToolbar.heightProperty())
        }
        // 按钮先处理点击，再阻止外层编辑器据此移动光标或选择源码。
        addEventHandler(MouseEvent.MOUSE_PRESSED) { it.consume() }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { it.consume() }
    }

    fun refresh(editable: Boolean, valid: Boolean, pending: Boolean, source: Boolean) {
        optimizeButton.isDisable = !editable || !valid || pending
        modeButton.isDisable = !valid || pending
        exitButton.isDisable = !valid || pending
        modeButton.text = Locales.str(if (source) "markdownTableToolbarPreview" else "markdownTableToolbarSource")
        val reason = when {
            !editable -> Locales.str("markdownTableReadonly")
            !valid -> Locales.str("markdownTableInvalid")
            else -> Locales.str("markdownTableOptimize")
        }
        optimizeTip.text = reason
        modeTip.text = Locales.str(if (source) "markdownTableShowTable" else "markdownTableShowSource")
    }

    private fun button(key: String, action: Runnable) = Button(Locales.str(key)).apply {
        isFocusTraversable = false
        styleClass.add("markdown-table-toolbar-button")
        setOnAction { action.run() }
    }
}

/** 源码首行的操作区与段落顶部留白对应，不改变 Markdown 文本。 */
class MarkdownTableSourceHeader(
    area: EditorArea,
    private val base: Node?,
    private val header: MarkdownTableToolbar,
    private val viewportWidth: DoubleSupplier
) : Pane() {
    init {
        header.isManaged = false
        header.layoutXProperty().bind(Bindings.createDoubleBinding(
            { width + textLeftPadding(area) - area.estimatedScrollXProperty().value },
            widthProperty(), area.paddingProperty(), area.estimatedScrollXProperty()
        ))
        children.add(header)
        if (base != null) children.add(base)
    }

    override fun computePrefWidth(height: Double): Double = base?.prefWidth(height) ?: 0.0

    override fun layoutChildren() {
        val top = MarkdownTableToolbar.HEIGHT
        base?.resizeRelocate(0.0, top, width, Math.max(0.0, height - top))
        header.resize(Math.max(0.0, viewportWidth.asDouble), top)
    }
}

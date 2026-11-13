package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.MarkdownImageActions
import com.allan.atools.utils.Locales
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.shape.Rectangle

/** 图片操作栏随图片占高和滚动，导航只移动光标，缩放只更新视图状态。 */
class MarkdownImageToolbar(area: EditorArea, destination: String, target: () -> MarkdownImageActions.Target?) : HBox(2.0) {
    companion object { const val HEIGHT = 28.0 }

    init {
        styleClass.add("markdown-image-toolbar")
        alignment = Pos.CENTER_LEFT
        minWidth = 0.0
        prefHeight = HEIGHT
        minHeight = HEIGHT
        maxHeight = HEIGHT
        fun button(key: String, action: (Button) -> Unit): Button = Button(Locales.str(key)).apply {
            styleClass.add("markdown-table-toolbar-button")
            minWidth = 0.0
            isFocusTraversable = false
            setOnAction { action(this) }
        }
        children.add(button("markdown.imageBefore") { target()?.let { area.markdownImageActions.jump(it, false) } })
        children.add(button("markdown.imageAfter") { target()?.let { area.markdownImageActions.jump(it, true) } })
        children.add(button("markdown.imageZoom") { button -> target()?.let { area.markdownImageActions.showZoom(button, it) } }.apply {
            text = "${Locales.str("markdown.imageZoom")}${area.markdownImageActions.percent(destination) ?: 100}%"
        })
        if (area.markdownImageActions.localFile(destination) != null) {
            children.add(button("markdown.imageEdit") { target()?.let { area.markdownImageActions.editImage(it) } }.apply {
                disableProperty().bind(area.editableProperty().not())
            })
        }
        clip = Rectangle().apply {
            widthProperty().bind(this@MarkdownImageToolbar.widthProperty())
            heightProperty().bind(this@MarkdownImageToolbar.heightProperty())
        }
        addEventHandler(MouseEvent.MOUSE_PRESSED) { it.consume() }
        addEventHandler(MouseEvent.DRAG_DETECTED) { it.consume() }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { it.consume() }
        addEventHandler(MouseEvent.MOUSE_RELEASED) { it.consume() }
        addEventHandler(MouseEvent.MOUSE_CLICKED) { it.consume() }
    }
}

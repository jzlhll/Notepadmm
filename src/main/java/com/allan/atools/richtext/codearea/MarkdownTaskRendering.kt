package com.allan.atools.richtext.codearea

import com.allan.uilibs.richtexts.CodeArea
import javafx.geometry.BoundingBox
import javafx.scene.Cursor
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Polyline
import javafx.scene.shape.Rectangle
import javafx.scene.shape.StrokeLineCap
import javafx.scene.shape.StrokeLineJoin
import javafx.scene.shape.StrokeType
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import java.util.IdentityHashMap

/** 图标直接挂在原段落内，滚动与缩放共用同一变换；原 Text 保留命中与源码坐标。 */
class MarkdownTaskRendering(private val area: EditorArea) : Pane() {
    private class Entry(val opacity: Double, val cursor: Cursor?, val flow: TextFlow) {
        val box = Rectangle().apply { strokeType = StrokeType.INSIDE }
        val tick = Polyline().apply {
            fill = null
            stroke = Color.WHITE
            strokeLineCap = StrokeLineCap.ROUND
            strokeLineJoin = StrokeLineJoin.ROUND
        }
        val view = Group(box, tick).apply {
            isManaged = false
            isMouseTransparent = true
        }
    }
    private val entries = IdentityHashMap<Text, Entry>()

    init {
        isManaged = false
        isMouseTransparent = true
    }

    fun refresh(texts: List<Text>) {
        if (scene == null || !area.markdownPreviewEnabled || area.editor.isRealtimeProcessingLimitReached) {
            clear()
            return
        }
        var ancestor: Node? = area
        while (ancestor != null) {
            if (!ancestor.isVisible || ancestor.opacity == 0.0) {
                clear()
                return
            }
            ancestor = ancestor.parent
        }
        val visible = texts.filter { CodeArea.MARKDOWN_TASK_RENDERED_CLASS in it.styleClass &&
            it.isVisible && it.scene === scene && it.parent is TextFlow }.toSet()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (text, entry) = iterator.next()
            if (text !in visible || text.parent !== entry.flow) {
                text.opacity = entry.opacity
                text.cursor = entry.cursor
                entry.flow.children.remove(entry.view)
                iterator.remove()
            }
        }
        for (text in visible) {
            val bounds = text.layoutBounds
            if (bounds.width <= 0.0 || bounds.height <= 0.0) continue
            val entry = entries.getOrPut(text) {
                Entry(text.opacity, text.cursor, text.parent as TextFlow).also { it.flow.children.add(it.view) }
            }
            val size = text.font.size * 0.9
            val local = BoundingBox(bounds.minX + (bounds.width - size) / 2,
                bounds.minY + (bounds.height - size) / 2, size, size)
            val target = text.localToParent(local)
            val checked = "markdown-task-checked" in text.styleClass
            if (entry.box.width != target.width || entry.box.height != target.height) {
                entry.tick.points.setAll(target.width * 0.23, target.height * 0.52,
                    target.width * 0.43, target.height * 0.73, target.width * 0.78, target.height * 0.29)
            }
            entry.box.apply {
                width = target.width
                height = target.height
                arcWidth = target.width * 0.3
                arcHeight = target.height * 0.3
                fill = if (checked) text.fill else Color.TRANSPARENT
                stroke = if (checked) null else text.fill
                strokeWidth = target.width * 0.1
            }
            entry.tick.apply {
                isVisible = checked
                strokeWidth = target.width * 0.11
            }
            entry.view.layoutX = target.minX
            entry.view.layoutY = target.minY
            entry.view.opacity = entry.opacity
            text.cursor = if ("markdown-task-example" !in text.styleClass && area.isEditable && !area.markdownComposing) Cursor.HAND else Cursor.TEXT
            text.opacity = 0.0
        }
    }

    fun clear() {
        entries.forEach { (text, entry) ->
            text.opacity = entry.opacity
            text.cursor = entry.cursor
            entry.flow.children.remove(entry.view)
        }
        entries.clear()
        children.clear()
    }
}

package com.allan.atools.richtext.codearea

import com.allan.uilibs.richtexts.CodeArea
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
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
import java.util.IdentityHashMap

/** 矢量任务复选框随正文缩放；原 Text 负责命中、手形光标和源码坐标。 */
class MarkdownTaskRendering(private val area: EditorArea) : Pane() {
    private class Entry(val opacity: Double, val cursor: Cursor?) {
        val box = Rectangle().apply { strokeType = StrokeType.INSIDE }
        val tick = Polyline().apply {
            fill = null
            stroke = Color.WHITE
            strokeLineCap = StrokeLineCap.ROUND
            strokeLineJoin = StrokeLineJoin.ROUND
        }
        val view = Group(box, tick).apply {
            isManaged = false
            clip = Rectangle()
        }
    }
    private val entries = IdentityHashMap<Text, Entry>()

    init {
        isManaged = false
        isMouseTransparent = true
    }

    fun refresh() {
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
        val visible = area.lookupAll(".${CodeArea.MARKDOWN_TASK_RENDERED_CLASS}")
            .filterIsInstance<Text>().filter { it.isVisible && it.scene === scene }.toSet()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (text, entry) = iterator.next()
            if (text !in visible) {
                text.opacity = entry.opacity
                text.cursor = entry.cursor
                children.remove(entry.view)
                iterator.remove()
            }
        }
        for (text in visible) {
            val bounds = text.layoutBounds
            if (bounds.width <= 0.0 || bounds.height <= 0.0) continue
            val entry = entries.getOrPut(text) {
                Entry(text.opacity, text.cursor).also { children.add(it.view) }
            }
            val size = text.font.size * 0.9
            val local = BoundingBox(bounds.minX + (bounds.width - size) / 2,
                bounds.minY + (bounds.height - size) / 2, size, size)
            val target = sceneToLocal(text.localToScene(local))
            val checked = "markdown-task-checked" in text.styleClass
            if (entry.box.width != target.width || entry.box.height != target.height) {
                entry.tick.points.setAll(target.width * 0.23, target.height * 0.52,
                    target.width * 0.43, target.height * 0.73, target.width * 0.78, target.height * 0.29)
            }
            entry.box.apply {
                width = target.width
                height = target.height
                arcWidth = target.width * 0.35
                arcHeight = target.height * 0.35
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
            var clipBounds: Bounds = target
            var opacity = entry.opacity
            var shown = true
            ancestor = text
            while (ancestor != null && ancestor !== parent) {
                val node = ancestor
                if (!node.isVisible) shown = false
                if (node !== text) opacity *= node.opacity
                node.clip?.let { clip ->
                    val clipped = sceneToLocal(node.localToScene(clip.boundsInParent))
                    val left = Math.max(clipBounds.minX, clipped.minX)
                    val top = Math.max(clipBounds.minY, clipped.minY)
                    val right = Math.min(clipBounds.maxX, clipped.maxX)
                    val bottom = Math.min(clipBounds.maxY, clipped.maxY)
                    clipBounds = BoundingBox(left, top, Math.max(0.0, right - left), Math.max(0.0, bottom - top))
                }
                ancestor = node.parent
            }
            (entry.view.clip as Rectangle).apply {
                x = clipBounds.minX - target.minX
                y = clipBounds.minY - target.minY
                width = clipBounds.width
                height = clipBounds.height
            }
            entry.view.isVisible = shown
            entry.view.opacity = opacity
            text.cursor = if (area.isEditable && !area.markdownComposing) Cursor.HAND else Cursor.TEXT
            text.opacity = 0.0
        }
    }

    fun clear() {
        entries.forEach { (text, entry) ->
            text.opacity = entry.opacity
            text.cursor = entry.cursor
        }
        entries.clear()
        children.clear()
    }
}

package com.allan.atools.richtext.codearea

import javafx.scene.AccessibleRole
import javafx.scene.layout.Region
import javafx.scene.shape.Polyline
import javafx.scene.shape.Rectangle
import javafx.scene.shape.StrokeLineCap
import javafx.scene.shape.StrokeLineJoin
import javafx.scene.shape.StrokeType

/** 表格中的状态示例使用矢量图标，测量与实际显示保持同一尺寸。 */
class MarkdownTaskIcon(fontSize: Double, checked: Boolean) : Region() {
    private val size = fontSize * 0.9

    init {
        val box = Rectangle(size, size).apply {
            x = fontSize * 0.12
            arcWidth = size * 0.3
            arcHeight = size * 0.3
            strokeType = StrokeType.INSIDE
            strokeWidth = size * 0.1
            styleClass.add(if (checked) "markdown-task-icon-checked" else "markdown-task-icon-empty")
        }
        val tick = Polyline(size * 0.23, size * 0.52, size * 0.43, size * 0.73, size * 0.78, size * 0.29).apply {
            layoutX = fontSize * 0.12
            strokeWidth = size * 0.11
            strokeLineCap = StrokeLineCap.ROUND
            strokeLineJoin = StrokeLineJoin.ROUND
            styleClass.add("markdown-task-icon-tick")
            isVisible = checked
        }
        setMinSize(size + fontSize * 0.24, size)
        setPrefSize(size + fontSize * 0.24, size)
        setMaxSize(size + fontSize * 0.24, size)
        children.addAll(box, tick)
        accessibleRole = AccessibleRole.TEXT
        accessibleText = if (checked) "[x]" else "[ ]"
    }

    override fun getBaselineOffset() = size * 0.92
}

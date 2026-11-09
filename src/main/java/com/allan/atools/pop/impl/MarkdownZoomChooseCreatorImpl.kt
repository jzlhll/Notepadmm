package com.allan.atools.pop.impl

import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.pop.AppContextMenu
import com.allan.atools.richtext.codearea.EditorScrollPane
import com.allan.baseparty.Action
import javafx.geometry.Orientation
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.Slider
import javafx.scene.layout.Region
import javafx.util.StringConverter
import kotlin.math.roundToInt

/** Markdown 缩放选择：菜单和竖向滑条共用编辑器原有范围与步长。 */
class MarkdownZoomChooseCreatorImpl(currentPercent: Double) : AbstractMenuCreator<Int>() {
    private val selectedPercent = (currentPercent / EditorScrollPane.ZOOM_STEP_PERCENT).roundToInt() *
        EditorScrollPane.ZOOM_STEP_PERCENT

    override fun createMenu(action: Action<Int>): ContextMenu = AppContextMenu().apply {
        val range = EditorScrollPane.MIN_ZOOM_PERCENT..EditorScrollPane.MAX_ZOOM_PERCENT
        for (percent in range step EditorScrollPane.ZOOM_STEP_PERCENT) {
            items.add(CheckMenuItem("$percent%").apply {
                isSelected = percent == selectedPercent
                setOnAction { action.invoke(percent) }
            })
        }
    }

    override fun createPop(action: Action<Int>?): Region {
        val slider = Slider(EditorScrollPane.MIN_ZOOM_PERCENT.toDouble(), EditorScrollPane.MAX_ZOOM_PERCENT.toDouble(),
            selectedPercent.toDouble()).apply {
            styleClass.add("markdown-zoom-slider")
            orientation = Orientation.VERTICAL
            blockIncrement = EditorScrollPane.ZOOM_STEP_PERCENT.toDouble()
            majorTickUnit = EditorScrollPane.ZOOM_STEP_PERCENT * 5.0
            minorTickCount = 4
            isShowTickMarks = true
            isShowTickLabels = true
            isSnapToTicks = true
            labelFormatter = object : StringConverter<Double>() {
                override fun toString(value: Double) = "${value.roundToInt()}%"
                override fun fromString(value: String) = value.removeSuffix("%").toDouble()
            }
            // 在浮层显示前固定最终尺寸，不随滑动中的数值重新测量。
            setMinSize(96.0, 260.0)
            setPrefSize(96.0, 260.0)
            setMaxSize(96.0, 260.0)
        }
        slider.valueProperty().addListener { _, _, value ->
            val percent = (value.toDouble() / EditorScrollPane.ZOOM_STEP_PERCENT).roundToInt() *
                EditorScrollPane.ZOOM_STEP_PERCENT
            action?.invoke(percent)
        }
        slider.setOnScroll { event ->
            if (event.deltaY != 0.0) {
                val step = if (event.deltaY > 0.0) EditorScrollPane.ZOOM_STEP_PERCENT
                    else -EditorScrollPane.ZOOM_STEP_PERCENT
                slider.adjustValue(slider.value + step)
                event.consume()
            }
        }
        return slider
    }
}

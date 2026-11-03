package com.allan.atools.ui

import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.css.PseudoClass
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.layout.HBox
import javafx.scene.text.Font
import javafx.scene.text.Text

/**
 * 分段切换按钮：一行内平铺若干互斥选项，选中项以强调色填充，点击或左右方向键切换。
 *
 * [totalWidth] 为 0 时按最长文案自适应，否则把总宽等分给各段；
 * 文案在等分宽度里放不下时以「最长文案 + 余量」为底线整体加宽，保证不裁字。
 * 两种情况下宽度都在创建时一次算定，显示后不再重新测量。
 */
class SegmentedControl @JvmOverloads constructor(
    items: List<String>,
    totalWidth: Double = 0.0,
) : HBox() {
    private val selectedIndex = SimpleIntegerProperty(-1)
    private val segments = ArrayList<Label>(items.size)

    init {
        styleClass.add("segment-group")
        alignment = Pos.CENTER_LEFT
        isFocusTraversable = true

        val minRequired = requiredWidth(items)
        val width = if (totalWidth > 0) maxOf(totalWidth, minRequired) else minRequired
        val segmentWidth = (width - GROUP_INSETS) / items.size
        items.forEachIndexed { index, text ->
            val segment = Label(text)
            segment.styleClass.add("segment-item")
            segment.setMinWidth(segmentWidth)
            segment.setPrefWidth(segmentWidth)
            segment.setMaxWidth(segmentWidth)
            segment.setOnMouseClicked {
                requestFocus()
                select(index)
            }
            segments.add(segment)
            children.add(segment)
        }
        setMinWidth(width)
        setPrefWidth(width)
        setMaxWidth(width)

        setOnKeyPressed { event ->
            if (event.code == KeyCode.LEFT) {
                step(-1)
            } else if (event.code == KeyCode.RIGHT) {
                step(1)
            } else {
                return@setOnKeyPressed
            }
            event.consume()
        }
    }

    /** 选中下标，供调用方监听用户操作；程序内调用 [select] 也会发出同样的变化。 */
    fun selectedIndexProperty(): ReadOnlyIntegerProperty = selectedIndex

    fun selectedIndex(): Int = selectedIndex.get()

    fun select(index: Int) {
        if (index < 0 || index >= segments.size || index == selectedIndex.get()) {
            return
        }
        selectedIndex.set(index)
        segments.forEachIndexed { i, segment ->
            segment.pseudoClassStateChanged(SELECTED, i == index)
        }
    }

    private fun step(delta: Int) {
        val current = selectedIndex.get()
        val next = if (current < 0) 0 else current + delta
        select(next)
    }

    companion object {
        private val SELECTED: PseudoClass = PseudoClass.getPseudoClass("selected")

        private const val TEXT_SIZE = 15.0
        private const val HORIZONTAL_SLACK = 26.0
        private const val MIN_SEGMENT_WIDTH = 68.0

        /** 边框 1 + 内边距 2，左右各一份。 */
        private const val GROUP_INSETS = 6.0

        /**
         * 控件不被裁字所需的最小总宽，供调用方把同类控件对齐到同一宽度。
         */
        @JvmStatic
        fun requiredWidth(items: List<String>): Double =
            measureSegmentWidth(items) * items.size + GROUP_INSETS

        /** 按最长文案给出统一段宽，中英文混排时各段仍等宽且不裁字。 */
        private fun measureSegmentWidth(items: List<String>): Double {
            val probe = Text()
            probe.font = Font.font(TEXT_SIZE)
            var widest = 0.0
            items.forEach { text ->
                probe.text = text
                val width = probe.layoutBounds.width
                if (width > widest) {
                    widest = width
                }
            }
            val width = widest + HORIZONTAL_SLACK
            return if (width < MIN_SEGMENT_WIDTH) MIN_SEGMENT_WIDTH else width
        }
    }
}

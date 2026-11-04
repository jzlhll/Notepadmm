package com.allan.atools.ui

import javafx.animation.FadeTransition
import javafx.animation.Interpolator
import javafx.animation.TranslateTransition
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.css.PseudoClass
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.util.Duration

/**
 * 开关控件：圆角矩形轨道内嵌方块滑块，开启时轨道填充强调色；底色、边框、圆角与强调色与 [SegmentedControl] 同一套。
 *
 * 整行（含右侧文案）可点击，聚焦后按空格切换；[selectedProperty] 同时反映用户操作与 [setSelected] 的调用。
 * 轨道尺寸在创建时固定，滑块位移只由尺寸常量算出，显示后不再依赖布局测量。
 */
class SwitchControl @JvmOverloads constructor(text: String = "") : HBox() {
    private val selected = SimpleBooleanProperty(false)
    private val trackFill = Region()
    private val thumb = Region()
    private val label = Label()

    var text: String
        get() = label.text
        set(value) {
            label.text = value
        }

    private val slide = TranslateTransition(Duration.millis(ANIMATION_MILLIS), thumb).apply {
        interpolator = Interpolator.EASE_BOTH
    }
    private val fade = FadeTransition(Duration.millis(ANIMATION_MILLIS), trackFill).apply {
        interpolator = Interpolator.EASE_BOTH
    }

    init {
        styleClass.add("switch-row")
        alignment = Pos.CENTER_LEFT
        spacing = ROW_SPACING
        isFocusTraversable = true

        trackFill.styleClass.add("switch-track-fill")
        trackFill.isMouseTransparent = true
        trackFill.opacity = 0.0
        trackFill.maxWidth = Double.MAX_VALUE
        trackFill.maxHeight = Double.MAX_VALUE

        thumb.styleClass.add("switch-thumb")
        thumb.isMouseTransparent = true
        thumb.minWidth = THUMB_SIZE
        thumb.prefWidth = THUMB_SIZE
        thumb.maxWidth = THUMB_SIZE
        thumb.minHeight = THUMB_SIZE
        thumb.prefHeight = THUMB_SIZE
        thumb.maxHeight = THUMB_SIZE
        StackPane.setAlignment(thumb, Pos.CENTER_LEFT)
        StackPane.setMargin(thumb, Insets(0.0, 0.0, 0.0, THUMB_INSET))

        val track = StackPane(trackFill, thumb)
        track.styleClass.add("switch-track")
        track.minWidth = TRACK_WIDTH
        track.prefWidth = TRACK_WIDTH
        track.maxWidth = TRACK_WIDTH
        track.minHeight = TRACK_HEIGHT
        track.prefHeight = TRACK_HEIGHT
        track.maxHeight = TRACK_HEIGHT

        label.styleClass.add("switch-label")
        children.addAll(track, label)
        this.text = text

        selected.addListener { _, _, newValue -> render(newValue, animated = true) }

        setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY && !isDisabled) {
                requestFocus()
                toggle()
            }
        }
        setOnKeyPressed { event ->
            if (event.code == KeyCode.SPACE) {
                toggle()
                event.consume()
            }
        }
    }

    /** 当前开关状态，程序内调用 [setSelected] 与用户操作发出同样的变化。 */
    fun selectedProperty(): ReadOnlyBooleanProperty = selected

    fun isSelected(): Boolean = selected.get()

    fun setSelected(value: Boolean) {
        selected.set(value)
    }

    private fun toggle() {
        selected.set(!selected.get())
    }

    /** 控件尚未显示时直接落位，避免初始化阶段产生无意义的滑动动画。 */
    private fun render(on: Boolean, animated: Boolean) {
        pseudoClassStateChanged(SELECTED, on)
        val travel = if (on) THUMB_TRAVEL else 0.0
        val fillOpacity = if (on) 1.0 else 0.0

        slide.stop()
        fade.stop()
        if (!animated || scene == null) {
            thumb.translateX = travel
            trackFill.opacity = fillOpacity
            return
        }
        slide.toX = travel
        fade.toValue = fillOpacity
        slide.play()
        fade.play()
    }

    companion object {
        private val SELECTED: PseudoClass = PseudoClass.getPseudoClass("selected")

        private const val TRACK_WIDTH = 44.0
        private const val TRACK_HEIGHT = 24.0
        private const val THUMB_SIZE = 18.0

        /** 滑块与轨道左右两侧的留白：(24 - 18) / 2。 */
        private const val THUMB_INSET = (TRACK_HEIGHT - THUMB_SIZE) / 2

        /** 滑块从关闭到开启的位移：轨道宽减去两侧留白与滑块自身。 */
        private const val THUMB_TRAVEL = TRACK_WIDTH - THUMB_SIZE - THUMB_INSET * 2

        private const val ROW_SPACING = 10.0
        private const val ANIMATION_MILLIS = 150.0
    }
}

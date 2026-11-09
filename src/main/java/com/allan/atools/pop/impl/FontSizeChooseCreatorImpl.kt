package com.allan.atools.pop.impl

import com.allan.atools.UIContext
import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.pop.AppContextMenu
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.AllStagesManager
import com.allan.atools.utils.CacheLocation
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.allan.baseparty.Action
import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.util.Duration
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * 字号选择：浮层分两段，上段是编辑区字号，下段是搜索区字号（默认比编辑区小 1 号）。
 * 两段都是「常规 A + 粗体 A 预览 + − 数值 +」步进器，预览字号跟随本段当前值；
 * 范围 12–48、每步 1 号，按住 ± 时每 250ms 连续步进一档，松开即停，尺寸固定、无滚动。
 * 菜单形态按字号逐项列出，供需要直接跳转的场景使用。
 */
class FontSizeChooseCreatorImpl : AbstractMenuCreator<Void>() {
    private val fontSizes = (MIN_SIZE..MAX_SIZE step STEP).toList()
    private var mRunnable: Runnable? = null
    private var mSize = 0

    @Throws(IOException::class)
    private fun changeCodeAreaFont(fontSize: Int) {
        val replaceWords = "-fx-font-size:$fontSize;"
        val customcss = ResLocation.getRealPath("css", "font_size.css")
        val path = Path.of(customcss)
        val lines = Files.readAllLines(path)
        var i = 0
        val count = lines.size
        while (i < count) {
            if (lines[i].contains("-fx-font-size")) {
                lines[i] = replaceWords
                break
            }
            i++
        }
        Files.write(Path.of(CacheLocation.get_font_size_cust_dot_css()), lines)
    }

    override fun createMenu(action: Action<Void>): ContextMenu {
        val menu = AppContextMenu()
        val selected = nearestIndex(fontSizes, UIContext.getFontSizeProperty().get())
        fontSizes.forEachIndexed { index, fontSize ->
            val item = CheckMenuItem(fontSize.toString())
            item.isSelected = index == selected
            item.setOnAction { applyFontSize(fontSize) }
            menu.items.add(item)
        }
        return menu
    }

    override fun createPop(action: Action<Void>?): Region {
        val editor = Section(fontSizes, UIContext.getFontSizeProperty().get(), ::applyFontSize)
        val search = Section(fontSizes, UIContext.getSearchFontSize(), ::applySearchFontSize)

        val editorTitle = sectionTitle(Locales.str("head.editorFontSize"))
        val searchTitle = sectionTitle(Locales.str("head.searchFontSize"))
        val divider = Region().apply {
            styleClass.add("app-menu-stepper-divider")
            setSize(this, CONTENT_WIDTH - 18, DIVIDER_HEIGHT)
        }

        val contentNodes = ArrayList<Region>(7)
        contentNodes.add(editorTitle)
        contentNodes.addAll(editor.nodes)
        contentNodes.add(divider)
        contentNodes.add(searchTitle)
        contentNodes.addAll(search.nodes)

        val box = VBox().apply {
            styleClass.add("app-menu-stepper-box")
            alignment = Pos.CENTER
            isFocusTraversable = true
            children.setAll(contentNodes)
            // 尺寸一次给全，浮层显示后不再重新测量。
            setSize(this, CONTENT_WIDTH, boxHeight())
        }

        box.setOnKeyPressed { event ->
            when (event.code) {
                KeyCode.LEFT, KeyCode.DOWN -> editor.step(-1)
                KeyCode.RIGHT, KeyCode.UP -> editor.step(1)
                else -> return@setOnKeyPressed
            }
            event.consume()
        }
        // 浮层被移除时不能留下仍在跑的重复计时。
        box.sceneProperty().addListener { _, _, scene ->
            if (scene == null) {
                editor.stopRepeat()
                search.stopRepeat()
            }
        }

        return box
    }

    /**
     * 一段字号选择：常规/粗体 A 预览行 +「− 数值 +」。
     * 两段共用同一套渲染与步进逻辑，只是回调不同。
     */
    private inner class Section(
        private val sizes: List<Int>,
        current: Int,
        private val onApply: (Int) -> Unit
    ) {
        private var index: Int = sizes.indexOf(current).takeIf { it >= 0 }
                ?: nearestIndex(sizes, current)
        private val normal = previewLabel()
        private val bold = previewLabel()
        private val minus = stepperButton("\u2212")
        private val plus = stepperButton("+")
        private val value = Label().apply {
            styleClass.add("app-menu-stepper-value")
            setSize(this, VALUE_WIDTH, BUTTON_SIZE)
        }

        private var repeatTimeline: Timeline? = null
        private var releaseFilter: EventHandler<MouseEvent>? = null
        private var filterScene: Scene? = null

        /** 由外部按顺序加入浮层的节点。 */
        val nodes: List<Region>

        init {
            val controls = HBox(minus, value, plus).apply {
                styleClass.add("app-menu-stepper")
                alignment = Pos.CENTER
            }
            val previews = HBox(normal, bold).apply {
                styleClass.add("app-menu-stepper-previews")
                alignment = Pos.CENTER
                minHeight = PREVIEW_HEIGHT
                prefHeight = PREVIEW_HEIGHT
                maxHeight = PREVIEW_HEIGHT
            }
            nodes = listOf(previews, controls)
            minus.setOnMousePressed { event ->
                if (event.button == MouseButton.PRIMARY) startRepeat(minus, -1)
            }
            plus.setOnMousePressed { event ->
                if (event.button == MouseButton.PRIMARY) startRepeat(plus, 1)
            }
            render()
        }

        /**
         * 按下立即走一档；按住不松时每 250ms 再走一档（每秒 4 档）。
         * 释放监听挂在场景上，避免按钮到达边界被禁用后收不到 MOUSE_RELEASED。
         */
        private fun startRepeat(button: Label, delta: Int) {
            stopRepeat()
            if (!step(delta)) {
                return
            }
            val scene = button.scene ?: return
            val filter = EventHandler<MouseEvent> { stopRepeat() }
            scene.addEventFilter(MouseEvent.MOUSE_RELEASED, filter)
            releaseFilter = filter
            filterScene = scene
            repeatTimeline = Timeline(KeyFrame(Duration.millis(REPEAT_INTERVAL),
                    EventHandler<ActionEvent> { if (!step(delta)) stopRepeat() })).apply {
                cycleCount = Animation.INDEFINITE
                play()
            }
        }

        fun stopRepeat() {
            repeatTimeline?.stop()
            repeatTimeline = null
            releaseFilter?.let { filterScene?.removeEventFilter(MouseEvent.MOUSE_RELEASED, it) }
            releaseFilter = null
            filterScene = null
        }

        /** 到达边界时返回 false，长按据此自行停止。 */
        fun step(delta: Int): Boolean {
            val next = index + delta
            if (next !in sizes.indices) return false
            index = next
            render()
            onApply(sizes[index])
            return true
        }

        private fun render() {
            val size = sizes[index]
            value.text = size.toString()
            normal.style = "-fx-font-size:$size;"
            bold.style = "-fx-font-size:$size;-fx-font-weight:bold;"
            minus.isDisable = index == 0
            plus.isDisable = index == sizes.lastIndex
        }
    }

    private fun sectionTitle(text: String) = Label(text).apply {
        styleClass.add("app-menu-stepper-title")
        alignment = Pos.CENTER
        setSize(this, CONTENT_WIDTH, TITLE_HEIGHT)
    }

    private fun previewLabel() = Label("A").apply {
        styleClass.add("app-menu-stepper-preview")
        alignment = Pos.CENTER
        minHeight = PREVIEW_HEIGHT
        prefHeight = PREVIEW_HEIGHT
        maxHeight = PREVIEW_HEIGHT
    }

    private fun stepperButton(text: String) = Label(text).apply {
        styleClass.add("app-menu-stepper-button")
        setSize(this, BUTTON_SIZE, BUTTON_SIZE)
        alignment = Pos.CENTER
        isFocusTraversable = false
    }

    /** 当前字号可能是旧版本留下的越界值，取最接近的一档。 */
    private fun nearestIndex(sizes: List<Int>, size: Int): Int {
        var index = 0
        var distance = Int.MAX_VALUE
        sizes.forEachIndexed { position, value ->
            val current = if (value > size) value - size else size - value
            if (current < distance) {
                distance = current
                index = position
            }
        }
        return index
    }

    private fun setSize(node: Region, width: Double, height: Double) {
        node.minWidth = width
        node.prefWidth = width
        node.maxWidth = width
        node.minHeight = height
        node.prefHeight = height
        node.maxHeight = height
    }

    /** 连续点击只重建一次字体 CSS，停顿后生效。 */
    private fun applyFontSize(fontSize: Int) {
        mSize = fontSize
        if (mRunnable == null) {
            mRunnable = Runnable {
                Platform.runLater {
                    Log.d("!!!!!click real load!")
                    AllStagesManager.getInstance().replaceCustom(
                        CacheLocation.CustomFontSize,
                        CacheLocation.get_font_size_cust_dot_css()
                    )
                    UIContext.updateFontSize(mSize)
                }
            }
        } else {
            Log.d("##remove runnable")
            ThreadUtils.globalHandler().removeCallback(mRunnable)
        }
        try {
            changeCodeAreaFont(mSize)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return
        }
        Log.d("##delay runnable $mSize")
        ThreadUtils.globalHandler().postDelayed(mRunnable, 250)
    }

    /** 搜索区字号只影响结果区的文本样式，直接改属性并落盘即可，不用重建样式文件。 */
    private fun applySearchFontSize(fontSize: Int) {
        UIContext.updateSearchFontSize(fontSize)
    }

    private companion object {
        const val MIN_SIZE = 12
        const val MAX_SIZE = 48
        const val STEP = 1
        /** 长按每秒步进 4 档。 */
        const val REPEAT_INTERVAL = 250.0

        const val CONTENT_WIDTH = 122.0
        /** 48 号字行高约 56px，留一点余量。 */
        const val PREVIEW_HEIGHT = 60.0
        const val BUTTON_SIZE = 30.0
        const val VALUE_WIDTH = 46.0
        const val GAP = 6.0
        const val TITLE_HEIGHT = 18.0
        const val DIVIDER_HEIGHT = 1.0

        /** 两段各有标题、预览、步进器，中间一条分隔线，共 7 个节点、6 个 6px 间距。 */
        fun boxHeight(): Double {
            return (TITLE_HEIGHT + PREVIEW_HEIGHT + BUTTON_SIZE) * 2 +
                    DIVIDER_HEIGHT + GAP * 6
        }
    }
}

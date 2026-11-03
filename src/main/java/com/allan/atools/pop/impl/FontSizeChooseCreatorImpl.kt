package com.allan.atools.pop.impl

import com.allan.atools.UIContext
import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.pop.AppContextMenu
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.AllStagesManager
import com.allan.atools.utils.CacheLocation
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.allan.baseparty.Action
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * 字号选择：浮层是「常规 A + 粗体 A 预览 − 数值 +」步进器，范围 12–48、每步 2 号，
 * 尺寸固定、无滚动；菜单形态按字号逐项列出，供需要直接跳转的场景使用。
 */
class FontSizeChooseCreatorImpl : AbstractMenuCreator<Void>() {
    private val fontSizes = (12..48 step 2).toList()
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
        val selected = nearestIndex(UIContext.getFontSizeProperty().get())
        fontSizes.forEachIndexed { index, fontSize ->
            val item = CheckMenuItem(fontSize.toString())
            item.isSelected = index == selected
            item.setOnAction { applyFontSize(fontSize) }
            menu.items.add(item)
        }
        return menu
    }

    override fun createPop(action: Action<Void>?): Region {
        var index = nearestIndex(UIContext.getFontSizeProperty().get())

        // 常规与粗体两个 A 随字号同步变化，容器高度锁死，避免浮层跟着变大变小。
        val normal = previewLabel()
        val bold = previewLabel()
        val previews = HBox(normal, bold).apply {
            styleClass.add("app-menu-stepper-previews")
            alignment = Pos.CENTER
            minHeight = PREVIEW_HEIGHT
            prefHeight = PREVIEW_HEIGHT
            maxHeight = PREVIEW_HEIGHT
        }
        val minus = stepperButton("\u2212")
        val plus = stepperButton("+")
        val value = Label(fontSizes[index].toString()).apply {
            styleClass.add("app-menu-stepper-value")
            setSize(this, VALUE_WIDTH, BUTTON_SIZE)
        }
        val controls = HBox(minus, value, plus).apply {
            styleClass.add("app-menu-stepper")
            alignment = Pos.CENTER
        }
        val box = VBox(previews, controls).apply {
            styleClass.add("app-menu-stepper-box")
            alignment = Pos.CENTER
            isFocusTraversable = true
            // 尺寸一次给全，浮层显示后不再重新测量。
            setSize(this, CONTENT_WIDTH, PREVIEW_HEIGHT + GAP + BUTTON_SIZE)
        }

        fun render() {
            val size = fontSizes[index]
            value.text = size.toString()
            normal.style = "-fx-font-size:$size;"
            bold.style = "-fx-font-size:$size;-fx-font-weight:bold;"
            minus.isDisable = index == 0
            plus.isDisable = index == fontSizes.lastIndex
        }

        fun step(delta: Int) {
            val next = index + delta
            if (next !in fontSizes.indices) return
            index = next
            render()
            applyFontSize(fontSizes[index])
        }

        minus.setOnMouseClicked { step(-1) }
        plus.setOnMouseClicked { step(1) }
        box.setOnKeyPressed { event ->
            when (event.code) {
                KeyCode.LEFT, KeyCode.DOWN -> step(-1)
                KeyCode.RIGHT, KeyCode.UP -> step(1)
                else -> return@setOnKeyPressed
            }
            event.consume()
        }

        render()
        return box
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

    /** 当前字号可能不在步进序列里（例如旧版本存了奇数号），取最接近的一档。 */
    private fun nearestIndex(size: Int): Int {
        var index = 0
        var distance = Int.MAX_VALUE
        fontSizes.forEachIndexed { position, value ->
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

    private companion object {
        const val CONTENT_WIDTH = 122.0
        /** 48 号字行高约 56px，留一点余量。 */
        const val PREVIEW_HEIGHT = 60.0
        const val BUTTON_SIZE = 30.0
        const val VALUE_WIDTH = 46.0
        const val GAP = 6.0
    }
}

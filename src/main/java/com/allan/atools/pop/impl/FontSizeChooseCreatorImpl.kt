package com.allan.atools.pop.impl

import com.allan.atools.UIContext
import com.allan.atools.Colors
import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.AllStagesManager
import com.allan.atools.utils.CacheLocation
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.allan.baseparty.Action
import com.jfoenix.controls.JFXListView
import javafx.application.Platform
import javafx.beans.value.ObservableValue
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.geometry.Pos
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class FontSizeChooseCreatorImpl : AbstractMenuCreator<Void>() {
    private val fontSizes = (16..24).toList() + listOf(26, 28, 32)
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
        TODO("Not yet implemented")
    }

    override fun createPop(action: Action<Void>?): Region {
        val vBox = VBox()
        //vBox.getChildren().add(createLabel("当前" + GlobalProfs.getFontSizeProperty().get(), 14));
        val list = JFXListView<Label>()
        list.minWidth = 73.3
        list.prefWidth = 73.3
        list.maxWidth = 73.3
        list.minHeight = 510.0
        list.prefHeight = 510.0
        list.maxHeight = 510.0
        fontSizes.forEach { fontSize ->
            val previewFontSize = if (fontSize < 22) fontSize else 22
            val label = createLabel(fontSize.toString(), previewFontSize)
            label.alignment = Pos.CENTER_LEFT
            label.style = "-fx-font-size:$previewFontSize;-fx-text-alignment:left;-fx-text-fill: ${Colors.TextColor.invoke()};"
            list.items.add(label)
        }

        val curFont = UIContext.getFontSizeProperty().get()
        val index = fontSizes.indexOf(curFont).let { if (it >= 0) it else 0 }

        list.selectionModel.select(index)
        list.selectionModel.selectedItemProperty()
            .addListener { _: ObservableValue<out Label>?, _: Label?, newValue: Label ->
                try {
                    mSize = newValue.text.trim().toInt()
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
                    changeCodeAreaFont(mSize)
                    Log.d("##delay runnable $mSize")
                    ThreadUtils.globalHandler().postDelayed(mRunnable, 250)
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        vBox.children.add(list)
        return vBox
    }
}

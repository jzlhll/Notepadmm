package com.allan.atools.pop.impl

import com.allan.atools.UIContext
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
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class FontSizeChooseCreatorImpl : AbstractMenuCreator<Void>() {
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
        list.maxWidth = 80.0
        list.prefHeight = 366.0
        list.maxHeight = 370.0
        list.items.add(createLabel("    12", 12))
        list.items.add(createLabel("   14", 14))
        list.items.add(createLabel("  15", 15))
        list.items.add(createLabel("  16", 16))
        list.items.add(createLabel("  17", 17))
        list.items.add(createLabel("  18", 18))
        list.items.add(createLabel("  19", 19))
        list.items.add(createLabel("  20", 20))
        list.items.add(createLabel("  22", 22))

        val curFont = UIContext.getFontSizeProperty().get()
        val index = when (curFont) {
            14 -> 1
            15 -> 2
            16 -> 3
            17 -> 4
            18 -> 5
            19 -> 6
            20 -> 7
            22 -> 8
            else -> 0
        }

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
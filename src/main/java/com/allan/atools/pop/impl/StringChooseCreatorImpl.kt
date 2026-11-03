package com.allan.atools.pop.impl

import com.allan.atools.Colors
import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.pop.AppContextMenu
import com.allan.baseparty.Action
import com.jfoenix.controls.JFXListView
import javafx.beans.value.ObservableValue
import javafx.geometry.Pos
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.layout.Region

/**
 * 纯文本下拉：显示名即值，选中回调给出下标。
 * 面板与尺寸规则和 AppThemeChooseCreatorImpl 一致，供语言等下拉使用。
 */
class StringChooseCreatorImpl @JvmOverloads constructor(
    private val items: List<String>,
    private val selectedIndex: Int,
    private val contentWidth: Double = 200.0,
    private val fontSize: Int = 15,
    private val cellHeight: Double = 32.0,
) : AbstractMenuCreator<Int>() {
    override fun createMenu(action: Action<Int>): ContextMenu {
        val menu = AppContextMenu()
        items.forEachIndexed { index, text ->
            val item = CheckMenuItem(text)
            item.isSelected = index == selectedIndex
            item.setOnAction { action.invoke(index) }
            menu.items.add(item)
        }
        return menu
    }

    override fun createPop(action: Action<Int>?): Region {
        val list = JFXListView<Label>()
        // 列表尺寸一次性给全，避免浮层显示后再次测量造成尺寸跳动。
        val height = cellHeight * items.size + 2
        list.minWidth = contentWidth
        list.prefWidth = contentWidth
        list.maxWidth = contentWidth
        list.fixedCellSize = cellHeight
        list.minHeight = height
        list.prefHeight = height
        list.maxHeight = height
        items.forEach { text ->
            val label = createLabel(text)
            label.alignment = Pos.CENTER_LEFT
            label.style = "-fx-font-size:$fontSize;-fx-text-alignment:left;-fx-text-fill: ${Colors.TextColor};"
            list.items.add(label)
        }

        list.selectionModel.select(if (selectedIndex in items.indices) selectedIndex else 0)
        list.selectionModel.selectedItemProperty()
            .addListener { _: ObservableValue<out Label>?, _: Label?, newValue: Label? ->
                val index = list.items.indexOf(newValue)
                if (index >= 0) action?.invoke(index)
            }
        return list
    }
}

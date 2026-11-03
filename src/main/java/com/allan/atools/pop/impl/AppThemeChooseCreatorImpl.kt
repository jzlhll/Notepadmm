package com.allan.atools.pop.impl

import com.allan.atools.AppTheme
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

/** 应用主题下拉：菜单与浮层共用同一份主题列表，浮层由场景内 MenuOverlay 以固定尺寸显示。 */
class AppThemeChooseCreatorImpl @JvmOverloads constructor(private val contentWidth: Double = 200.0)
    : AbstractMenuCreator<AppTheme>() {
    override fun createMenu(action: Action<AppTheme>): ContextMenu {
        val menu = AppContextMenu()
        val current = AppTheme.current()
        AppTheme.entries.forEach { theme ->
            val item = CheckMenuItem(theme.toString())
            item.isSelected = theme == current
            item.setOnAction { action.invoke(theme) }
            menu.items.add(item)
        }
        return menu
    }

    override fun createPop(action: Action<AppTheme>?): Region {
        val themes = AppTheme.entries
        val list = JFXListView<Label>()
        // 列表尺寸一次性给全，避免浮层显示后再次测量造成尺寸跳动。
        val height = CELL_HEIGHT * themes.size + 2
        list.minWidth = contentWidth
        list.prefWidth = contentWidth
        list.maxWidth = contentWidth
        list.fixedCellSize = CELL_HEIGHT
        list.minHeight = height
        list.prefHeight = height
        list.maxHeight = height
        themes.forEach { theme ->
            val label = createLabel(theme.toString())
            label.alignment = Pos.CENTER_LEFT
            label.style = "-fx-font-size:$FONT_SIZE;-fx-text-alignment:left;-fx-text-fill: ${Colors.TextColor};"
            list.items.add(label)
        }

        val current = themes.indexOf(AppTheme.current())
        list.selectionModel.select(if (current >= 0) current else 0)
        list.selectionModel.selectedItemProperty()
            .addListener { _: ObservableValue<out Label>?, _: Label?, newValue: Label? ->
                val index = list.items.indexOf(newValue)
                if (index >= 0) action?.invoke(themes[index])
            }
        return list
    }

    private companion object {
        const val CELL_HEIGHT = 34.0
        const val FONT_SIZE = 17
    }
}

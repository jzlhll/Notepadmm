package com.allan.atools.pop.impl

import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.utils.EncodingUtil
import com.allan.atools.utils.Locales
import com.allan.baseparty.Action
import com.jfoenix.controls.JFXListView
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.layout.Region
import javafx.event.EventHandler

class JSONChooseCreatorImpl : AbstractMenuCreator<String>() {
    override fun createMenu(action: Action<String>): ContextMenu {
        val contextMenu = ContextMenu()
        contextMenu.items.addAll(
            createMenuItem(action, Locales.str("removeUnknownSymbols")),
            createMenuItem(action, "Format"),
            //SeparatorMenuItem(),
        )
        return contextMenu
    }

    override fun createPop(action: Action<String>?): Region {
        val list = JFXListView<Label>()
        list.maxWidth = 170.0
        list.maxHeight = 470.0

        list.items.add(createLabel(Locales.str("removeUnknownSymbols")))
        list.items.add(createLabel("Format"))

        list.selectionModel.selectedItemProperty()
            .addListener { observable: ObservableValue<out Label>?, oldValue: Label?, newValue: Label ->
                action?.invoke(newValue.text)
            }
        return list
    }

    private fun createMenuItem(action: Action<String>, actionStr:String) : MenuItem {
        val item = MenuItem(actionStr)
        item.onAction = EventHandler { event: ActionEvent? -> action.invoke(actionStr) }
        return item
    }
}
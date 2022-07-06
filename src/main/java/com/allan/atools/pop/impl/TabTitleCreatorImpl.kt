package com.allan.atools.pop.impl

import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.Colors
import com.allan.atools.utils.Locales
import com.allan.baseparty.Action
import com.jfoenix.controls.JFXListView
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class TabTitleCreatorImpl : AbstractMenuCreator<String>(){
    override fun createPop(action: Action<String>?): Region {
        val vBox = VBox()
        val list: JFXListView<Label>
        list = JFXListView()
        list.style = "-fx-background-color:" + Colors.SearchBgColor.invoke() + ";"
        list.maxWidth = 280.0
        list.prefHeight = 110.0

        var label: Label = createLabel(Locales.str("modifyName"))
        list.items.add(label)

        label = createLabel(Locales.str("closeOthers"))
        list.items.add(label)

        label = createLabel(Locales.str("editor.openHereDir"))
        list.items.add(label)

        list.selectionModel.selectedIndexProperty()
            .addListener { observable: ObservableValue<out Number>?, oldValue: Number?, newValue: Number ->
                if (newValue.toInt() == 0) {
                    action?.invoke(EVENT_MODIFY_NAME)
                } else if (newValue.toInt() == 1) {
                    action?.invoke(EVENT_CLOSE_OTHERS)
                } else if (newValue.toInt() == 2) {
                    action?.invoke(EVENT_OPEN_TO_EXPLORE)
                }
            }

        vBox.children.add(list)
        return vBox
    }

    override fun createMenu(action: Action<String>): ContextMenu {
        val contextMenu = ContextMenu()
        val menu0 = MenuItem(Locales.str("modifyName"))
        menu0.onAction = EventHandler {
            action.invoke(
                EVENT_MODIFY_NAME
            )
        }

        val menu1 = MenuItem(Locales.str("closeOthers"))
        menu1.onAction = EventHandler {
            action.invoke(
                EVENT_CLOSE_OTHERS
            )
        }
        val menu2 = MenuItem(Locales.str("editor.openHereDir"))
        menu2.onAction = EventHandler {
            action.invoke(
                EVENT_OPEN_TO_EXPLORE
            )
        }
        contextMenu.items.addAll(menu0, menu1, menu2)
        return contextMenu
    }

    companion object {
        @kotlin.jvm.JvmField
        var EVENT_CLOSE_OTHERS: String = "closeOthers"

        @kotlin.jvm.JvmField
        var EVENT_MODIFY_NAME: String = "modifyName"

        @kotlin.jvm.JvmField
        var EVENT_OPEN_TO_EXPLORE: String = "openFileToExplore"
    }

}
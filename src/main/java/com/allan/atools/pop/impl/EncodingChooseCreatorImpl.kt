package com.allan.atools.pop.impl

import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.utils.EncodingUtil
import com.allan.baseparty.Action
import com.jfoenix.controls.JFXListView
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.Region

class EncodingChooseCreatorImpl : AbstractMenuCreator<String>() {
    override fun createMenu(action: Action<String>): ContextMenu {
        val contextMenu = ContextMenu()
        contextMenu.items.addAll(
            createMenuItem(action, EncodingUtil.CHOISE_ENCODING_UTF8, EncodingUtil.CHOISE_ENCODING_UTF8),
            createMenuItem(action, EncodingUtil.CHOISE_ENCODING_UTF8_NO_BOM, EncodingUtil.CHOISE_ENCODING_UTF8_NO_BOM),
            createMenuItem(action, EncodingUtil.CHOISE_ENCODING_UTF32BE, EncodingUtil.CHOISE_ENCODING_UTF32BE),
            createMenuItem(action, EncodingUtil.CHOISE_ENCODING_UTF32LE, EncodingUtil.CHOISE_ENCODING_UTF32LE),
            createMenuItem(action, EncodingUtil.CHOISE_ENCODING_UTF16BE, EncodingUtil.CHOISE_ENCODING_UTF16BE),
            createMenuItem(action, EncodingUtil.CHOISE_ENCODING_UTF16LE, EncodingUtil.CHOISE_ENCODING_UTF16LE),
            createMenuItem(action, EncodingUtil.CHOISE_ENCODING_GBK, EncodingUtil.CHOISE_ENCODING_GBK),
            //SeparatorMenuItem(),
        )
        return contextMenu
    }

    override fun createPop(action: Action<String>?): Region {
        val list = JFXListView<Label>()
        list.maxWidth = 170.0
        list.maxHeight = 470.0

        list.items.add(createLabel(EncodingUtil.CHOISE_ENCODING_UTF8))
        list.items.add(createLabel(EncodingUtil.CHOISE_ENCODING_UTF8_NO_BOM))
        list.items.add(createLabel(EncodingUtil.CHOISE_ENCODING_UTF32BE))
        list.items.add(createLabel(EncodingUtil.CHOISE_ENCODING_UTF32LE))
        list.items.add(createLabel(EncodingUtil.CHOISE_ENCODING_UTF16BE))
        list.items.add(createLabel(EncodingUtil.CHOISE_ENCODING_UTF16LE))
        list.items.add(createLabel(EncodingUtil.CHOISE_ENCODING_GBK))

        list.selectionModel.selectedItemProperty()
            .addListener { observable: ObservableValue<out Label>?, oldValue: Label?, newValue: Label ->
                action?.invoke(newValue.text)
            }
        return list
    }

    private fun createMenuItem(action: Action<String>, menuStr:String, actionStr:String) : MenuItem {
        val item = MenuItem(menuStr)
        item.onAction = EventHandler { event: ActionEvent? -> action.invoke(actionStr) }
        return item
    }
}
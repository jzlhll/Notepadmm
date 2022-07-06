package com.allan.atools.pop

import com.allan.atools.Colors
import com.allan.baseparty.Action
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.layout.Region

abstract class AbstractMenuCreator<T> {
    abstract fun createMenu(action:Action<T>): ContextMenu
    abstract fun createPop(action:Action<T> ?): Region

    internal fun createLabel(text: String, fontSize: Int): Label {
        val label = Label(text)
        val textColor = Colors.TextColor.invoke()!!
        label.style = "-fx-font-size:$fontSize;-fx-text-alignment:center;-fx-text-fill: $textColor;"
        return label
    }

    internal fun createLabel(text: String): Label {
        return createLabel(text, 15)
    }
}
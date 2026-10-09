package com.allan.atools.pop.impl

import com.allan.atools.utils.Locales
import com.allan.baseparty.Action
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.Tab

/** 标签菜单由标签头统一处理右键请求，各项动作以固定标识关联。 */
class TabTitleCreatorImpl {
    fun createMenu(action: Action<String>): ContextMenu {
        val contextMenu = ContextMenu()
        // 切换标签或再次右键时，关闭旧菜单的鼠标事件继续交给目标节点。
        contextMenu.setConsumeAutoHidingEvents(false)
        listOf(
            EVENT_MODIFY_NAME to "modifyName",
            EVENT_MOVE_TO_FRONT to "editor.moveTabToFront",
            EVENT_CLOSE_OTHERS to "closeOthers",
            EVENT_OPEN_TO_EXPLORE to "editor.openHereDir",
            EVENT_COPY_FULL_PATH to "editor.copyFullPath",
            EVENT_PIN_RECENT_FILE to "editor.pinRecentFile",
            EVENT_OPEN_IN_TYPORA to "editor.openInTypora"
        ).forEach { (event, key) ->
            contextMenu.items.add(MenuItem(Locales.str(key)).apply {
                userData = event
                setOnAction { action.invoke(event) }
            })
        }
        return contextMenu
    }

    companion object {
        @JvmStatic
        fun moveToFront(tab: Tab?) {
            val pane = tab?.tabPane ?: return
            val index = pane.tabs.indexOf(tab)
            if (index <= 0) return
            val selected = pane.selectionModel.selectedItem
            // 沿用标签的移除／添加通知，重建标题节点时同步刷新原有绑定。
            pane.tabs.removeAt(index)
            pane.tabs.add(0, tab)
            if (selected != null) pane.selectionModel.select(selected)
        }

        @JvmField
        val EVENT_MOVE_TO_FRONT: String = "moveTabToFront"

        @kotlin.jvm.JvmField
        var EVENT_CLOSE_OTHERS: String = "closeOthers"

        @kotlin.jvm.JvmField
        var EVENT_MODIFY_NAME: String = "modifyName"

        @kotlin.jvm.JvmField
        var EVENT_OPEN_TO_EXPLORE: String = "openFileToExplore"

        @kotlin.jvm.JvmField
        var EVENT_COPY_FULL_PATH: String = "copyFullPath"

        @kotlin.jvm.JvmField
        var EVENT_PIN_RECENT_FILE: String = "pinRecentFile"

        @kotlin.jvm.JvmField
        val EVENT_OPEN_IN_TYPORA: String = "openInTypora"
    }

}

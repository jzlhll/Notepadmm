package com.allan.atools.pop.impl

import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager
import com.allan.atools.utils.Locales
import com.allan.baseparty.Action
import com.jfoenix.controls.JFXListView
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.Region
import java.io.File

class NotepadFileCreatorImpl : AbstractMenuCreator<Int>() {

    override fun createMenu(action: Action<Int>): ContextMenu {
        val contextMenu = ContextMenu()
        val menu0 = MenuItem(Locales.str("openFile"))
        menu0.onAction = EventHandler {
            action.invoke(
                0
            )
        }

        val menu1 = MenuItem(Locales.str("newFile"))
        menu1.onAction = EventHandler {
            action.invoke(
                1
            )
        }

        val menu2 = MenuItem(Locales.str("openDirAsWorkspace"))
        menu2.onAction = EventHandler {
            action.invoke(
                2
            )
        }

        val menu3 = MenuItem(Locales.str("openLastWorkspace"))
        menu3.onAction = EventHandler {
            action.invoke(
                3
            )
        }

        val recentFilesMenu = Menu(Locales.str("allRecentFiles"))
        val list = AllEditorsManager.saveOrReadRecentFiles(null)
        if (list != null && list.size > 0) {
            for (f in list) {
                val item = MenuItem(f)
                item.onAction = EventHandler {
                    AllEditorsManager.Instance.openFile(
                        File(f),
                        true,
                        true
                    )
                }
                recentFilesMenu.items.add(item)
            }
        }
        contextMenu.items.addAll(menu0, menu1, SeparatorMenuItem(), menu2, menu3, SeparatorMenuItem(), recentFilesMenu)
        return contextMenu
    }

    override fun createPop(action: Action<Int>?): Region {
        throw java.lang.RuntimeException("not support create pop in NotepadFileCreatorImpl")
    }
}
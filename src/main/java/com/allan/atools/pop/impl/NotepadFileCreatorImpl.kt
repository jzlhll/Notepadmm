package com.allan.atools.pop.impl

import com.allan.atools.UIContext
import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager
import com.allan.atools.tools.modulenotepad.workspace.WorkspaceManager
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

        val menu2 = MenuItem(Locales.str("openDirAsWorkspace"))
        menu2.onAction = EventHandler {
            action.invoke(
                2
            )
        }

        val recentWorkspacesMenu = Menu(Locales.str("recentWorkspaces"))
        val workspaces = WorkspaceManager.saveOrReadRecentWorkspaces(null)
        if (workspaces != null && workspaces.size > 0) {
            for (w in workspaces) {
                val item = MenuItem(w)
                item.onAction = EventHandler {
                    UIContext.context().workspaceManager.openRecentWorkspace(w)
                }
                recentWorkspacesMenu.items.add(item)
            }
        }

        contextMenu.items.addAll(menu0, menu1, recentFilesMenu, SeparatorMenuItem(), menu2, recentWorkspacesMenu)

        val fontSize = UIContext.context().getMainMenuFontSize()
        applyFontSize(contextMenu.items, fontSize)

        return contextMenu
    }

    /** 递归给菜单项设置字号，覆盖默认 15px，使一级和二级菜单都跟随尺寸级别 */
    private fun applyFontSize(items: List<MenuItem>, size: Int) {
        for (item in items) {
            if (item is SeparatorMenuItem) continue
            item.style = "-fx-font-size: ${size}px;"
            if (item is Menu) {
                applyFontSize(item.items, size)
            }
        }
    }

    override fun createPop(action: Action<Int>?): Region {
        throw java.lang.RuntimeException("not support create pop in NotepadFileCreatorImpl")
    }
}

package com.allan.atools.pop.impl

import com.allan.atools.GlobalCfgStores
import com.allan.atools.UIContext
import com.allan.atools.pop.AbstractMenuCreator
import com.allan.atools.pop.AppContextMenu
import com.allan.atools.pop.MenuOverlay
import com.allan.atools.pop.showPopupContent
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager
import com.allan.atools.tools.modulenotepad.workspace.WorkspaceManager
import com.allan.atools.utils.Locales
import com.allan.baseparty.Action
import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXTextField
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** 文件菜单及路径输入浮层，复用编辑器的文件打开与最近记录管理。 */
class NotepadFileCreatorImpl : AbstractMenuCreator<Int>() {

    override fun createMenu(action: Action<Int>): ContextMenu {
        val contextMenu = AppContextMenu()
        val menu0 = MenuItem(Locales.str("openFile"))
        menu0.onAction = EventHandler {
            action.invoke(
                0
            )
        }

        val openByPath = MenuItem(Locales.str("openFileByPath"))
        openByPath.setOnAction {
            var overlay: MenuOverlay? = null
            val content = createPop { overlay?.close() }
            overlay = showPopupContent(UIContext.context().notepadMainActionBarFileOpenBtn, content)
        }

        val menu1 = MenuItem(Locales.str("newFile"))
        menu1.onAction = EventHandler {
            action.invoke(
                1
            )
        }

        val save = MenuItem(Locales.str("save"))
        save.setOnAction {
            UIContext.currentAreaProp.get()?.editor?.saveContent(null, false)
        }

        val recentFilesMenu = Menu(Locales.str("allRecentFiles"))
        val pinnedList = AllEditorsManager.readPinnedRecentFiles()
        for (f in pinnedList) {
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
        if (pinnedList.isNotEmpty()) {
            recentFilesMenu.items.add(SeparatorMenuItem())
        }

        val list = AllEditorsManager.saveOrReadRecentFiles(null)
        val recentList = list?.filter { !pinnedList.contains(it) } ?: emptyList()
        for (f in recentList) {
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

        val menu2 = MenuItem(Locales.str("openDirAsWorkspace"))
        menu2.onAction = EventHandler {
            action.invoke(
                2
            )
        }

        val workspaces = WorkspaceManager.saveOrReadRecentWorkspaces(null)

        val recentWorkspacesMenu = Menu(Locales.str("recentWorkspaces"))
        if (workspaces != null && workspaces.size > 0) {
            for (w in workspaces) {
                val item = MenuItem(w)
                item.onAction = EventHandler {
                    UIContext.context().workspaceManager.openRecentWorkspace(w)
                }
                recentWorkspacesMenu.items.add(item)
            }
        }

        contextMenu.items.addAll(menu0, openByPath, menu1, save)
        if (recentList.isNotEmpty() || pinnedList.isNotEmpty()) {
            contextMenu.items.add(recentFilesMenu)
        }
        contextMenu.items.addAll(SeparatorMenuItem(), menu2)
        if (!workspaces.isNullOrEmpty()) {
            contextMenu.items.add(recentWorkspacesMenu)
        }

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
        val pathInput = JFXTextField().apply {
            styleClass.add("app-menu-path-input")
            promptText = Locales.str("filePathPrompt")
            accessibleText = Locales.str("filePath")
            minWidth = 0.0
            HBox.setHgrow(this, Priority.ALWAYS)
        }
        // 预留两行提示空间，校验失败时保持浮层尺寸不变。
        val error = Label().apply {
            styleClass.add("app-menu-path-error")
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }
        val openButton = JFXButton(Locales.str("open")).apply {
            styleClass.add("app-menu-path-open")
            isDisable = true
        }

        fun openPath() {
            val value = pathInput.text.trim().removeSurrounding("\"").removeSurrounding("'")
            if (value.isBlank()) {
                error.text = Locales.str("filePathInvalid")
                return
            }
            val expanded = when {
                value == "~" -> System.getProperty("user.home")
                value.startsWith("~/") || value.startsWith("~\\") ->
                    File(System.getProperty("user.home"), value.substring(2)).path
                else -> value
            }
            val file = try {
                Path.of(expanded).toAbsolutePath().normalize().toFile()
            } catch (_: InvalidPathException) {
                error.text = Locales.str("filePathInvalid")
                return
            }
            if (!file.isFile || !file.canRead()) {
                error.text = Locales.str("filePathUnavailable")
                return
            }
            action?.invoke(0)
            GlobalCfgStores.user().setString("lastOpenDirs", file.parent)
            AllEditorsManager.Instance.openFile(file, true, true)
        }

        pathInput.textProperty().addListener { _, _, value ->
            openButton.isDisable = value.isNullOrBlank()
            error.text = ""
        }
        pathInput.setOnAction { openPath() }
        openButton.setOnAction { openPath() }

        val inputRow = HBox(10.0, pathInput, openButton).apply {
            alignment = Pos.CENTER_LEFT
        }
        return VBox(10.0, Label(Locales.str("filePath")), inputRow, error).apply {
            styleClass.add("app-menu-open-path")
        }
    }
}

package com.allan.atools.tools.modulenotepad.workspace

import com.allan.atools.controller.NotepadController
import com.allan.atools.tools.modulenotepad.manager.NotepadHeadButtons
import com.allan.atools.ui.JfoenixDialogUtils
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import java.io.File
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

/** 工作区目录导航与操作菜单，新建操作统一放在更多菜单中。 */
class WorkspaceToolbar(
    private val controller: NotepadController,
    private val currentDirectory: Supplier<File?>,
    private val openDirectory: Consumer<File>,
    private val sortByName: BooleanSupplier,
    private val toggleSort: Runnable,
    private val chooseDirectory: Runnable
) {
    init {
        controller.workspaceGoUpBtn.apply {
            tooltip = Tooltip(Locales.str("goUpDir"))
            accessibleText = Locales.str("goUpDir")
            setOnAction {
                val parent = currentDirectory.get()?.parentFile
                if (parent?.isDirectory == true) openDirectory.accept(parent)
            }
        }
        controller.workspaceMoreBtn.apply {
            tooltip = Tooltip(Locales.str("workspaceMore"))
            accessibleText = Locales.str("workspaceMore")
            items.setAll(moreItems())
            setBeforeShow { items.setAll(moreItems()) }
        }
        controller.workspaceText.apply {
            items.setAll(directoryItems())
            setBeforeShow { items.setAll(directoryItems()) }
        }
    }

    fun creationItems(directory: File?): List<MenuItem> = listOf(
        MenuItem(Locales.str("newFile")).apply {
            isDisable = directory?.isDirectory != true
            setOnAction {
                if (directory?.isDirectory == true) {
                    NotepadHeadButtons.newATempFile(directory.absolutePath)
                }
            }
        },
        MenuItem(Locales.str("newDir")).apply {
            isDisable = directory?.isDirectory != true
            setOnAction {
                if (directory?.isDirectory == true) createDirectory(directory)
            }
        }
    )

    private fun createDirectory(directory: File) {
        val workspace = currentDirectory.get()
        JfoenixDialogUtils.editInput(Locales.str("newDir"), "") { name ->
            if (!name.isNullOrEmpty()) {
                val folder = File(directory, name)
                when {
                    folder.exists() -> SnackbarUtils.show(Locales.str("thisDirAlreadyExist"))
                    !folder.mkdir() -> SnackbarUtils.show(Locales.str("thisDirCreateFail"))
                    workspace != null && currentDirectory.get() == workspace -> openDirectory.accept(workspace)
                }
            }
        }
    }

    private fun moreItems(): List<MenuItem> = buildList {
        val directory = currentDirectory.get()
        addAll(creationItems(directory))
        add(SeparatorMenuItem())
        add(Menu(Locales.str("workspaceSort")).apply {
            val group = ToggleGroup()
            items.add(RadioMenuItem(Locales.str("workspaceSortByName")).apply {
                toggleGroup = group
                isSelected = sortByName.asBoolean
                setOnAction { if (!sortByName.asBoolean) toggleSort.run() }
            })
            items.add(RadioMenuItem(Locales.str("workspaceSortByModified")).apply {
                toggleGroup = group
                isSelected = !sortByName.asBoolean
                setOnAction { if (sortByName.asBoolean) toggleSort.run() }
            })
        })
        add(MenuItem(Locales.str("refresh")).apply {
            isDisable = directory == null
            setOnAction { directory?.let(openDirectory::accept) }
        })
        add(SeparatorMenuItem())
        add(MenuItem(Locales.str("openDirAsWorkspace")).apply {
            setOnAction { chooseDirectory.run() }
        })
    }

    private fun directoryItems(): List<MenuItem> = buildList {
        WorkspaceManager.saveOrReadRecentWorkspaces(null)
            .asSequence()
            .filter { File(it).isDirectory }
            .take(5)
            .forEach { path ->
                add(MenuItem(path).apply {
                    setOnAction { controller.workspaceManager.openRecentWorkspace(path) }
                })
            }
        if (isNotEmpty()) add(SeparatorMenuItem())
        add(MenuItem(Locales.str("openWorkspace")).apply {
            setOnAction { chooseDirectory.run() }
        })
    }.onEach { it.style = "-fx-font-size: 16px;" }
}

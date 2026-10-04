package com.allan.atools.ui

import com.allan.atools.Colors
import com.allan.atools.MarkdownThemes
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXRadioButton
import javafx.beans.InvalidationListener
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import java.awt.Desktop
import java.nio.file.Files

/** 主题选择与用户 CSS 管理入口，明暗模式由应用总开关决定。 */
class MarkdownThemeDialog private constructor(owner: Stage) {
    private val stage = Stage()
    private val list = VBox(10.0)
    private val hint = Label()
    private val error = Label()
    private val changed = InvalidationListener { refresh() }

    init {
        stage.initOwner(owner)
        stage.initModality(Modality.WINDOW_MODAL)
        stage.title = Locales.str("markdown.theme")
        stage.minWidth = 600.0
        stage.minHeight = 420.0
        error.styleClass.add("markdown-theme-error")
        error.isWrapText = true
        hint.styleClass.add("normal-desc-label")
        hint.isWrapText = true
        val help = Label(Locales.str("markdown.themeHelp")).apply {
            styleClass.add("normal-desc-label")
            isWrapText = true
        }
        val heading = VBox(8.0, hint, help).apply { padding = Insets(16.0) }
        val scroll = ScrollPane(list).apply {
            isFitToWidth = true
            styleClass.add("custom-main-bg")
        }
        list.padding = Insets(0.0, 16.0, 12.0, 16.0)
        val actions = FlowPane(8.0, 8.0)
        fun button(key: String, action: () -> Unit): JFXButton = JFXButton(Locales.str(key)).apply {
            styleClass.add("custom-jfx-button-nobg")
            setOnAction {
                try { action() }
                catch (problem: Exception) {
                    Log.e("Manage markdown theme failed", problem)
                    error.text = "${Locales.str("markdown.themeOperationFailed")} ${problem.message.orEmpty()}"
                }
            }
        }
        actions.children.addAll(
            button("markdown.themeImport") {
                val chooser = FileChooser().apply {
                    title = Locales.str("markdown.themeImport")
                    extensionFilters.add(FileChooser.ExtensionFilter("CSS", "*.css", "*.CSS"))
                }
                val source = chooser.showOpenDialog(stage)
                if (source != null) {
                    val imported = MarkdownThemes.importCss(source.toPath())
                    if (imported.dark == Colors.isDark()) MarkdownThemes.select(imported)
                }
            },
            button("markdown.themeReload") { MarkdownThemes.reload() },
            button("markdown.themeFolder") {
                val directory = MarkdownThemes.directory()
                Files.createDirectories(directory)
                Desktop.getDesktop().open(directory.toFile())
            },
            button("markdown.themeTemplate") {
                val chooser = FileChooser().apply {
                    title = Locales.str("markdown.themeTemplate")
                    initialFileName = if (Colors.isDark()) "my-markdown-dark.css" else "my-markdown.css"
                    extensionFilters.add(FileChooser.ExtensionFilter("CSS", "*.css"))
                }
                chooser.showSaveDialog(stage)?.let { MarkdownThemes.exportTemplate(it.toPath()) }
            },
            button("close") { stage.close() }
        )
        val footer = VBox(8.0, error, actions).apply { padding = Insets(12.0, 16.0, 16.0, 16.0) }
        val root = BorderPane(scroll, heading, null, footer, null).apply { styleClass.add("custom-main-bg") }
        stage.scene = Scene(root, 660.0, 560.0).apply {
            stylesheets.addAll(owner.scene.stylesheets)
        }
        MarkdownThemes.revisionProperty().addListener(changed)
        stage.setOnHidden { MarkdownThemes.revisionProperty().removeListener(changed) }
        refresh()
    }

    private fun refresh() {
        val dark = Colors.isDark()
        val current = MarkdownThemes.current()
        hint.text = Locales.str(if (dark) "markdown.themeDarkHint" else "markdown.themeLightHint")
        error.text = MarkdownThemes.problems().joinToString("\n")
        val group = ToggleGroup()
        list.children.clear()
        for (theme in MarkdownThemes.available()) {
            val selectable = theme.dark == dark
            val choice = JFXRadioButton(theme.name).apply {
                toggleGroup = group
                isSelected = theme.id == current.id
                isDisable = !selectable
                setOnAction { if (isSelected) MarkdownThemes.select(theme) }
            }
            val mode = Locales.str(if (theme.dark) "markdown.themeDark" else "markdown.themeLight")
            val origin = Locales.str(if (theme.builtIn) "markdown.themeBuiltIn" else "markdown.themeCustom")
            val status = if (selectable) "" else " · " + Locales.str(
                if (dark) "markdown.themeRequiresLight" else "markdown.themeRequiresDark")
            val description = Label("$mode · $origin$status").apply {
                styleClass.add("normal-desc-label")
                isWrapText = true
            }
            val text = VBox(4.0, choice, description)
            HBox.setHgrow(text, Priority.ALWAYS)
            val swatches = HBox(5.0).apply { alignment = Pos.CENTER_RIGHT }
            for (key in listOf("-au-editor-bg-color", "-au-editor-text-color", "-au-md-link", "-au-md-code-bg")) {
                val color = theme.palette.getValue(key)
                swatches.children.add(Region().apply {
                    minWidth = 22.0; prefWidth = 22.0; maxWidth = 22.0
                    minHeight = 22.0; prefHeight = 22.0; maxHeight = 22.0
                    style = "-fx-background-color: $color; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: -au-main-window-border-color;"
                })
            }
            val card = HBox(12.0, text, swatches).apply {
                alignment = Pos.CENTER_LEFT
                styleClass.add("markdown-theme-card")
                if (!selectable) styleClass.add("markdown-theme-unavailable")
                if (!theme.builtIn) Tooltip.install(this, Tooltip(theme.path.toString()))
                setOnMouseClicked { if (selectable && !choice.isSelected) MarkdownThemes.select(theme) }
            }
            list.children.add(card)
        }
    }

    companion object {
        @JvmStatic fun show(owner: Stage) { MarkdownThemes.initialize(); MarkdownThemeDialog(owner).stage.show() }
    }
}

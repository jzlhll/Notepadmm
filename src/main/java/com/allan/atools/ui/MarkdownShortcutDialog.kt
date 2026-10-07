package com.allan.atools.ui

import com.allan.atools.richtext.codearea.MarkdownShortcuts
import com.allan.atools.utils.Locales
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.stage.Stage

/** 顶部操作栏的快捷键速查窗口，只列出现有键位。 */
object MarkdownShortcutDialog {
    private var window: Stage? = null

    @JvmStatic fun show(owner: Stage) {
        window?.let { it.show(); it.toFront(); it.requestFocus(); return }
        val body = VBox(14.0).apply { padding = Insets(18.0) }
        fun section(key: String, rows: List<Pair<String, String>>) {
            body.children.add(Label(Locales.str(key)).apply { styleClass.add("normal-label") })
            val grid = GridPane().apply { hgap = 24.0; vgap = 8.0 }
            rows.forEachIndexed { index, (description, keys) ->
                grid.add(Label(description).apply { isWrapText = true; maxWidth = 350.0 }, 0, index)
                grid.add(Label(keys).apply { styleClass.add("normal-desc-label") }, 1, index)
            }
            body.children.add(grid)
        }
        section("markdown.shortcutBody", MarkdownShortcuts.entries.map {
            (if (it.heading > 0) "H${it.heading}" else Locales.str(it.labelKey)) to it.display()
        } + listOf(
            Locales.str("markdown.shortcutContinue") to "Enter",
            Locales.str("markdown.shortcutHardBreak") to "Shift + Enter",
            Locales.str("markdown.shortcutIndent") to "Tab / Shift + Tab",
            Locales.str("markdown.shortcutPrefix") to "Backspace",
            Locales.str("markdown.shortcutMove") to "Alt + ↑ / ↓",
            Locales.str("markdown.shortcutOpenLink") to MarkdownShortcuts.modifier() + Locales.str("markdown.shortcutClick")
        ))
        section("markdown.shortcutTable", listOf(MarkdownShortcuts.BOLD, MarkdownShortcuts.ITALIC,
            MarkdownShortcuts.INLINE_CODE, MarkdownShortcuts.STRIKE).map { Locales.str(it.labelKey) to it.display() } + listOf(
            Locales.str("markdown.shortcutCellMove") to "Tab / Shift + Tab",
            Locales.str("markdown.shortcutCellRow") to MarkdownShortcuts.modifier() + "Enter",
            Locales.str("markdown.shortcutCellExit") to "Esc",
            Locales.str("markdown.pastePlain") to MarkdownShortcuts.PASTE_PLAIN.display(),
            Locales.str("markdown.shortcutUndo") to MarkdownShortcuts.modifier() + "Z",
            Locales.str("markdown.shortcutRedo") to MarkdownShortcuts.modifier() + "Shift + Z / " + MarkdownShortcuts.modifier() + "Y"
        ))
        body.children.add(Label(Locales.str("markdown.shortcutHint")).apply {
            isWrapText = true; styleClass.add("normal-desc-label")
        })
        val stage = Stage().apply {
            initOwner(owner)
            title = Locales.str("markdown.shortcuts")
            minWidth = 540.0
            minHeight = 360.0
            scene = Scene(ScrollPane(body).apply { isFitToWidth = true; styleClass.add("custom-main-bg") }, 760.0, 650.0).apply {
                stylesheets.addAll(owner.scene.stylesheets)
            }
            setOnHidden { window = null }
        }
        window = stage
        stage.show()
    }
}

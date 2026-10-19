package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.MarkdownShortcuts
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState
import com.allan.atools.utils.Locales
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.ToggleGroup
import javafx.scene.input.Clipboard

/** 管理表格菜单和工具栏，并将操作交给编辑组件。 */
class MarkdownTableMenus(private val state: MarkdownTablePreviewState) {
    fun showCellMenu(table: PreviewTable, row: Int, column: Int, screenX: Double, screenY: Double): Unit = with(state) {
        if (editing.deferWhileComposing { showCellMenu(table, row, column, screenX, screenY) }) return
        val area = currentArea ?: return
        if (!MarkdownTableRendering.hasShowingWindow(area)) return
        hideCellMenu()
        val undo = MenuItem(Locales.str("markdownTableUndo"))
        val redo = MenuItem(Locales.str("markdownTableRedo"))
        val cut = MenuItem(Locales.str("markdownTableCut"))
        val copy = MenuItem(Locales.str("markdownTableCopy"))
        val copyRow = MenuItem(Locales.str("markdownTableCopyRow"))
        val paste = MenuItem(Locales.str("markdownTablePaste"))
        val selectAll = MenuItem(Locales.str("markdownTableSelectAll"))
        val pasteCells = MenuItem(Locales.str("markdownTablePasteCells"))
        val pasteText = MenuItem(Locales.str("markdownTablePasteText"))
        val insertAbove = MenuItem(Locales.str("markdownTableInsertAbove"))
        val insertBelow = MenuItem(Locales.str("markdownTableInsertBelow"))
        val insertLeft = MenuItem(Locales.str("markdownTableInsertLeft"))
        val insertRight = MenuItem(Locales.str("markdownTableInsertRight"))
        val duplicateRow = MenuItem(Locales.str("markdownTableDuplicateRow"))
        val moveUp = MenuItem(Locales.str("markdownTableMoveUp"))
        val moveDown = MenuItem(Locales.str("markdownTableMoveDown"))
        val moveLeft = MenuItem(Locales.str("markdownTableMoveLeft"))
        val moveRight = MenuItem(Locales.str("markdownTableMoveRight"))
        val copyAs = Menu(Locales.str("markdownTableCopyData"))
        copyAs.items.addAll(copyRow,
            tableCopyItem("markdownTableCopyRowTsv", table, MarkdownTableCopyScope.ROW, row, column, true),
            tableCopyItem("markdownTableCopyColumnMarkdown", table, MarkdownTableCopyScope.COLUMN, row, column, false),
            tableCopyItem("markdownTableCopyColumnTsv", table, MarkdownTableCopyScope.COLUMN, row, column, true),
            tableCopyItem("markdownTableCopyTableMarkdown", table, MarkdownTableCopyScope.TABLE, row, column, false),
            tableCopyItem("markdownTableCopyTableTsv", table, MarkdownTableCopyScope.TABLE, row, column, true))
        val exitBefore = MenuItem(Locales.str("markdownTableExitBefore"))
        val exitAfter = MenuItem(Locales.str("markdownTableExitAfter"))
        val alignment = Menu(Locales.str("markdownTableAlignment"))
        val alignmentGroup = ToggleGroup()
        for (value in listOf(MarkdownTableDocumentState.Alignment.LEFT, MarkdownTableDocumentState.Alignment.CENTER, MarkdownTableDocumentState.Alignment.RIGHT)) {
            val key = when (value) {
                MarkdownTableDocumentState.Alignment.LEFT -> "markdownTableAlignLeft"
                MarkdownTableDocumentState.Alignment.CENTER -> "markdownTableAlignCenter"
                else -> "markdownTableAlignRight"
            }
            val item = RadioMenuItem(Locales.str(key))
            item.toggleGroup = alignmentGroup
            val currentAlignment = table.alignments()[column]
            item.isSelected = currentAlignment == value || value == MarkdownTableDocumentState.Alignment.LEFT && currentAlignment == MarkdownTableDocumentState.Alignment.DEFAULT
            item.setOnAction { editing.applyTableEdit(table) { target -> MarkdownTableEdits.alignColumn(target, row, column, value) } }
            alignment.items.add(item)
        }
        val deleteRow = MenuItem(Locales.str("markdownTableDeleteRow"))
        val deleteColumn = MenuItem(Locales.str("markdownTableDeleteColumn"))
        val rowActions = Menu(Locales.str("markdownTableRowActions"))
        rowActions.items.addAll(insertAbove, insertBelow, duplicateRow, moveUp, moveDown, SeparatorMenuItem(), deleteRow)
        val columnActions = Menu(Locales.str("markdownTableColumnActions"))
        columnActions.items.addAll(insertLeft, insertRight, moveLeft, moveRight, alignment, SeparatorMenuItem(), deleteColumn)
        val formats = Menu(Locales.str("markdown.format"))
        val names = listOf("markdown.bold", "markdown.italic", "markdown.strike", "markdown.inlineCode")
        val marks = listOf("**", "*", "~~", "`")
        for (index in marks.indices) {
            val item = MenuItem(Locales.str(names[index]))
            for (shortcut in MarkdownShortcuts.entries) {
                if (shortcut.labelKey == names[index]) item.graphic = Label(shortcut.display())
            }
            item.setOnAction { editing.wrapCellSelection(marks[index]) }
            formats.items.add(item)
        }
        val editLink = MenuItem(Locales.str("markdown.editLink"))
        editLink.setOnAction { editing.editCellLink() }
        formats.items.add(editLink)
        formats.isDisable = !area.isEditable || structurePending
        val menu = ContextMenu(undo, redo, SeparatorMenuItem(), cut, copy, copyAs,
            paste, pasteCells, pasteText, selectAll, SeparatorMenuItem(), formats, rowActions, columnActions,
            SeparatorMenuItem(), exitBefore, exitAfter)
        cellMenu = menu
        undo.setOnAction { editing.performUndoRedo(false) }
        redo.setOnAction { editing.performUndoRedo(true) }
        cut.setOnAction { cellEditor.cut() }
        copy.setOnAction { cellEditor.copy() }
        copyRow.setOnAction { copyMarkdownTableRow(area, table.id(), row) }
        paste.setOnAction { cellEditor.paste() }
        pasteCells.setOnAction { editing.pasteClipboard(true) }
        pasteText.setOnAction { editing.pastePlainText() }
        insertAbove.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.insertRow(it, row, column) } }
        insertBelow.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.insertRow(it, row + 1, column) } }
        insertLeft.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.insertColumn(it, column, row) } }
        insertRight.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.insertColumn(it, column + 1, row) } }
        duplicateRow.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.duplicateRow(it, row, column) } }
        moveUp.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.moveRow(it, row, column, -1) } }
        moveDown.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.moveRow(it, row, column, 1) } }
        moveLeft.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.moveColumn(it, row, column, -1) } }
        moveRight.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.moveColumn(it, row, column, 1) } }
        exitBefore.setOnAction { editing.exitTable(table, -1) }
        exitAfter.setOnAction { editing.exitTable(table, 1) }
        selectAll.setOnAction { cellEditor.selectAll() }
        deleteRow.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.deleteRow(it, row, column) } }
        deleteColumn.setOnAction { editing.applyTableEdit(table) { MarkdownTableEdits.deleteColumn(it, row, column) } }
        menu.setOnShowing {
            val editable = area.isEditable && !structurePending
            undo.isDisable = !editable || !area.undoManager.isUndoAvailable
            redo.isDisable = !editable || !area.undoManager.isRedoAvailable
            cut.isDisable = !editable || cellEditor.selectedText.isEmpty()
            copy.isDisable = cellEditor.selectedText.isEmpty()
            val clipboard = Clipboard.getSystemClipboard()
            paste.isDisable = !editable || (!clipboard.hasString() && !clipboard.hasHtml() && !clipboard.hasImage() && !clipboard.hasFiles() && !hasTableCells(clipboard))
            pasteCells.isDisable = paste.isDisable
            pasteText.isDisable = !editable || !clipboard.hasString()
            insertAbove.isDisable = !editable || row == 0
            insertBelow.isDisable = !editable
            insertLeft.isDisable = !editable
            insertRight.isDisable = !editable
            alignment.isDisable = !editable
            copyAs.isDisable = structurePending
            duplicateRow.isDisable = !editable
            moveUp.isDisable = !editable || row <= 1
            moveDown.isDisable = !editable || row == 0 || row == table.rows().size - 1
            moveLeft.isDisable = !editable || column == 0
            moveRight.isDisable = !editable || column == table.alignments().size - 1
            exitBefore.isDisable = structurePending
            exitAfter.isDisable = structurePending
            deleteRow.isDisable = !editable || row == 0
            deleteColumn.isDisable = !editable || table.alignments().size == 1
        }
        menu.setOnHidden { if (cellMenu === menu) cellMenu = null }
        menu.show(area, screenX, screenY)
    }

    private fun tableCopyItem(key: String, table: PreviewTable, scope: MarkdownTableCopyScope, row: Int, column: Int, tsv: Boolean): MenuItem {
        val item = MenuItem(Locales.str(key))
        item.setOnAction { state.currentArea?.let { area -> copyMarkdownTableData(area, table.id(), scope, row, column, tsv) } }
        return item
    }

    fun hideCellMenu() {
        val menu = state.cellMenu
        state.cellMenu = null
        menu?.hide()
    }

    fun createToolbar(table: PreviewTable): MarkdownTableToolbar = with(state) {
        val bar = MarkdownTableToolbar(table.id(), { editing.optimize(table) }, { editing.toggleMode(table) }, { editing.exitTable(table, 1) })
        toolbars.add(bar)
        refreshToolbar(bar, table)
        bar
    }

    fun refreshToolbar(bar: MarkdownTableToolbar, table: PreviewTable?): Unit = with(state) {
        bar.refresh(currentArea?.isEditable == true, table?.valid() == true, structurePending,
            table != null && searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.SOURCE,
            table != null && searchPresentation.isExpanded(table))
    }

    fun updateToolbar(): Unit = with(state) {
        for (bar in toolbars.toList()) {
            if (bar.scene == null) continue
            refreshToolbar(bar, tables.firstOrNull { it.id() == bar.tableId })
        }
    }
}

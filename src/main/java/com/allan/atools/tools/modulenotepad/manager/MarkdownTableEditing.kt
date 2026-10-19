package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.richtext.codearea.MarkdownAttachments
import com.allan.atools.richtext.codearea.MarkdownClipboard
import com.allan.atools.richtext.codearea.MarkdownInlineFormatting
import com.allan.atools.richtext.codearea.MarkdownLinkEditing
import com.allan.atools.richtext.codearea.MarkdownShortcuts
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState
import com.allan.atools.richtext.codearea.MarkdownTableParser
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownInlineSnapshot
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.ScrollBar
import javafx.scene.input.Clipboard
import javafx.scene.input.InputMethodEvent
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.text.Font
import java.util.function.Consumer

/** 处理单元格输入、焦点恢复、结构编辑与撤销，不持有布局调度器。 */
class MarkdownTableEditing(private val state: MarkdownTablePreviewState) {
    companion object {
        fun isDescendant(target: Any?, ancestor: Node): Boolean {
            var node = target as? Node
            while (node != null) { if (node === ancestor) return true; node = node.parent }
            return false
        }
        private fun isDescendantOfType(target: Any?, type: Class<out Node>): Boolean {
            var node = target as? Node
            while (node != null) { if (type.isInstance(node)) return true; node = node.parent }
            return false
        }
    }
    data class CellTarget(val table: PreviewTable, val row: Int, val column: Int)
    val cancelCellEditorFocusRestore = EventHandler<MouseEvent> { event -> with(state) {
        if (activeTable != null && !isDescendant(event.target, cellEditor)) {
            cellEditorFocusPending = false
            pendingInput.clear()
            // 用户主动点击优先于行重建和单元格激活时排队的焦点请求。
            cellActivationRevision++
        }
    } }

    fun configureCellEditor(): Unit = with(state) {
        cellEditor.styleClass.add("markdown-table-cell-editor")
        cellEditor.isWrapText = true
        cellEditor.prefRowCount = 1
        cellEditor.setMinSize(0.0, 0.0)
        cellEditor.textProperty().addListener { _, _, value -> writeActiveCell(value) }
        cellEditor.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) { event ->
            val area = currentArea
            val revision = ++inputMethodRevision
            handlingInputMethod = true
            composingText = event.composed.isNotEmpty()
            Platform.runLater {
                if (destroyed || currentArea !== area || revision != inputMethodRevision) return@runLater
                handlingInputMethod = false
                pendingInput.resume()
                if (composingText) return@runLater
                committingInputMethod = true
                try { writeActiveCell(cellEditor.text) } finally { committingInputMethod = false }
                if (layoutPending || layoutJobs.isNotEmpty()) rendering.layoutTimer.start()
                val action = afterComposition
                if (action != null) { afterComposition = null; action.run() }
            }
        }
        cellEditor.caretPositionProperty().addListener(InvalidationListener { menus.hideCellMenu() })
        cellEditor.selectionProperty().addListener(InvalidationListener { menus.hideCellMenu() })
        cellEditor.addEventFilter(KeyEvent.KEY_PRESSED, ::onCellKeyPressed)
        cellEditor.addEventFilter(KeyEvent.KEY_TYPED) { event ->
            if (!composingText && !handlingInputMethod && structurePending) event.consume()
        }
        cellEditor.addEventFilter(ScrollEvent.SCROLL) { event ->
            val area = currentArea
            if (area != null && event.deltaX == 0.0 && !event.isShiftDown) { area.scrollYBy(-event.deltaY); event.consume() }
        }
        cellEditor.contextMenu = null
        cellEditor.setOnContextMenuRequested { event ->
            val table = activeTable
            if (table != null && activeRow >= 0 && activeColumn >= 0) {
                menus.showCellMenu(table, activeRow, activeColumn, event.screenX, event.screenY)
                event.consume()
            }
        }
        cellEditor.sceneProperty().addListener { _, previous, scene ->
            // 正文替换会回收虚拟段落，暂时脱离场景不代表用户结束格内编辑。
            if (previous != null && previous.focusOwner === cellEditor && activeTable != null) cellEditorFocusPending = true
            if (scene != null) {
                if (cellEditorFocusScene !== scene) {
                    cellEditorFocusScene?.removeEventFilter(MouseEvent.MOUSE_PRESSED, cancelCellEditorFocusRestore)
                    cellEditorFocusScene = scene
                    scene.addEventFilter(MouseEvent.MOUSE_PRESSED, cancelCellEditorFocusRestore)
                }
                scheduleCellEditorFocusRestore()
            }
        }
        cellEditor.focusedProperty().addListener { _, _, focused ->
            if (focused) cellEditorFocusPending = false else {
                if (activeTable != null && (movingCellEditor || writingCell || writingRow != null || cellEditor.scene == null)) cellEditorFocusPending = true
                val activation = cellActivationRevision
                Platform.runLater {
                    if (activation != cellActivationRevision) return@runLater
                    restoreCellEditorFocus()
                    if (currentArea?.isFocused == true && !cellEditor.isFocused && !cellEditorFocusPending && !structurePending) endCellEditing(false)
                    menus.updateToolbar()
                }
            }
        }
    }

    fun activateCell(table: PreviewTable, row: Int, column: Int, clickX: Double): Unit = with(state) {
        if (structurePending || deferWhileComposing { activateCell(table, row, column, clickX) }) return
        val latest = session.latestTable(table)
        if (latest !== table) { if (latest != null) activateCell(latest, row, column, clickX); return }
        val layout = layouts[table.id()]
        val cells = layout?.cells
        if (!table.valid() || searchPresentation.mode(table) != MarkdownTableDocumentState.Mode.TABLE || layout == null || cells == null
            || row < 0 || row >= cells.size || column >= layout.widths.size) return
        val area = currentArea!!
        menus.hideCellMenu()
        val previousTable = activeTable
        val previousRow = activeRow
        val sameCell = previousTable === table && activeRow == row && activeColumn == column
        if (!sameCell) {
            area.undoManager.preventMerge()
            activeTable = table
            activeRow = row
            activeColumn = column
            updatingCellEditor = true
            cellEditor.text = MarkdownTableCellText.decode(table.rows()[row].cells()[column].source())
            updatingCellEditor = false
            cellEditor.positionCaret(if (clickX == Double.MAX_VALUE) cellEditor.length else 0)
        }
        cellEditor.isEditable = area.isEditable && !structurePending
        if (previousTable != null && (previousTable !== table || previousRow != row)) updateRowHeight(previousTable, previousRow, true)
        updateActiveRowHeight()
        rendering.refreshRowGraphics(table, row)
        if (previousTable != null && previousTable.id() != table.id()) rendering.queueLayouts(false)
        area.showParagraphInViewport(table.rows()[row].line())
        var left = 0.0
        for (index in 0 until column) left += layout.widths[index]
        val right = left + layout.widths[column]
        var offset = table.horizontalOffset()
        if (left < offset) offset = left else if (right > offset + layout.viewportWidth) offset = right - layout.viewportWidth
        offset = Math.max(0.0, Math.min(offset, Math.max(0.0, layout.totalWidth - layout.viewportWidth)))
        table.setHorizontalOffset(offset)
        layout.applyOffset(offset)
        val activation = ++cellActivationRevision
        Platform.runLater {
            val current = currentArea
            val active = activeTable
            if (current != null && activation == cellActivationRevision && active?.id() == table.id() && activeRow == row && activeColumn == column) {
                current.applyCss()
                current.layout()
                rendering.refreshRowGraphics(active, row)
                cellEditor.requestFocus()
            }
        }
        menus.updateToolbar()
    }

    fun reparentCellEditor(target: StackPane): Unit = with(state) {
        if (cellEditor.parent === target) return
        cellEditorFocusPending = cellEditorFocusPending || cellEditor.isFocused
        movingCellEditor = true
        try {
            (cellEditor.parent as? Pane)?.children?.remove(cellEditor)
            target.children.setAll(cellEditor)
            cellEditor.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)
            StackPane.setMargin(cellEditor, Insets(-10.0, -13.0, -10.0, -13.0))
            cellEditor.applyCss()
            // 禁用视口缓存，避免滚动到小数坐标时文字插值发虚。
            cellEditor.lookup(".scroll-pane > .viewport")?.isCache = false
        } finally { movingCellEditor = false }
        scheduleCellEditorFocusRestore()
    }
    private fun scheduleCellEditorFocusRestore(): Unit = with(state) {
        if (!cellEditorFocusPending) return
        val activation = cellActivationRevision
        Platform.runLater { if (activation == cellActivationRevision) restoreCellEditorFocus() }
    }
    private fun restoreCellEditorFocus(): Unit = with(state) {
        if (!cellEditorFocusPending || movingCellEditor || destroyed || currentArea == null || activeTable == null) return
        val scene = cellEditor.scene ?: return
        if (scene !== currentArea!!.scene) return
        // 节点暂时脱离场景时焦点可能转给任意控件，不能据此判断用户已离开。
        cellEditorFocusPending = false
        cellEditor.requestFocus()
    }

    fun endCellEditing(focusDocument: Boolean): Unit = with(state) {
        if (currentArea != null && deferWhileComposing { endCellEditing(focusDocument) }) return
        menus.hideCellMenu()
        pendingInput.clear()
        cellActivationRevision++
        cellEditorFocusPending = false
        val previousTable = activeTable
        val previousRow = activeRow
        activeTable = null
        activeRow = -1
        activeColumn = -1
        movingCellEditor = true
        (cellEditor.parent as? Pane)?.children?.remove(cellEditor)
        movingCellEditor = false
        pendingTableId = null
        pendingRow = -1
        pendingColumn = -1
        pendingSourceOffset = -1
        pendingUndoCaretOffset = -1
        if (previousTable != null && currentArea != null && !destroyed) {
            updateRowHeight(previousTable, previousRow, true)
            rendering.queueLayouts(false)
        }
        if (focusDocument) currentArea?.requestFocus()
    }

    private fun writeActiveCell(value: String): Unit = with(state) {
        val table = activeTable ?: return
        val area = currentArea ?: return
        if (updatingCellEditor || handlingInputMethod || composingText || structurePending || !area.isEditable
            || !table.valid() || activeRow < 0 || activeRow >= table.rows().size) return
        val row = table.rows()[activeRow]
        if (activeColumn < 0 || activeColumn >= row.cells().size) return
        val cell = row.cells()[activeColumn]
        var encoded = MarkdownTableCellText.encode(value, cell.source())
        if (cell.synthetic()) {
            val replacement = MarkdownTableParser.materializeRow(row, activeColumn, encoded)
            cellEditorFocusPending = cellEditorFocusPending || cellEditor.isFocused
            writingRow = replacement.row
            try { area.replaceText(row.startOffset(), row.endOffset(), replacement.source); updateActiveRowHeight() }
            finally { writingRow = null; scheduleCellEditorFocusRestore() }
            return
        }
        // 紧邻分隔符时保留空白，末尾反斜线不能转义列分隔符。
        if (encoded.endsWith("\\") && cell.endOffset() < area.length && area.getText(cell.endOffset(), cell.endOffset() + 1) == "|") encoded += " "
        if (cell.startOffset() > area.length || cell.endOffset() > area.length) { endCellEditing(true); return }
        val current = area.getText(cell.startOffset(), cell.endOffset())
        if (current == encoded) return
        // 正文替换前记录焦点，脱离场景后可能无法读取原焦点归属。
        cellEditorFocusPending = cellEditorFocusPending || cellEditor.isFocused
        val replacement = cellEditor.activeReplacement
        val original = MarkdownTableCellText.decode(current)
        val change = if (replacement != null && original == replacement.original) {
            MarkdownTableCellText.change(current, encoded, MarkdownTableCellText.editorOffset(current, replacement.start), MarkdownTableCellText.editorOffset(current, replacement.end))
        } else if (committingInputMethod) {
            val delta = value.length - original.length
            val start = Math.max(0, cellEditor.caretPosition - Math.max(0, delta))
            val end = Math.min(original.length, start + Math.max(0, -delta))
            MarkdownTableCellText.change(current, encoded, MarkdownTableCellText.editorOffset(current, start), MarkdownTableCellText.editorOffset(current, end))
        } else MarkdownTableCellText.change(current, encoded)
        writingCell = true
        try {
            area.replaceText(cell.startOffset() + change.start, cell.startOffset() + change.end, change.text)
            cell.setSource(encoded)
            updateActiveRowHeight()
        } finally { writingCell = false; scheduleCellEditorFocusRestore() }
    }

    private fun updateActiveRowHeight(): Unit = with(state) {
        val table = activeTable ?: return
        if (composingText || handlingInputMethod || activeRow < 0 || font == null) return
        val layout = layouts[table.id()] ?: return
        val cells = layout.cells ?: return
        if (activeRow >= cells.size || activeColumn < 0 || activeColumn >= layout.widths.size) return
        val source = table.rows()[activeRow].cells()[activeColumn].source()
        if (cells[activeRow][activeColumn].source != source) {
            val runs = inlineContents[source] ?: listOf(MarkdownTableLayout.Run(cellEditor.text, false, false, false, false, false))
            val cell = MarkdownTableLayout.Cell(source, runs, layout.font, activeRow == 0)
            cell.measureHeight(measureFlow, layout.widths[activeColumn])
            cells[activeRow][activeColumn] = cell
        }
        updateRowHeight(table, activeRow, true)
    }
    private fun updateRowHeight(table: PreviewTable, row: Int, present: Boolean): Unit = with(state) {
        val layout = layouts[table.id()] ?: return
        val cells = layout.cells ?: return
        if (row < 0 || row >= cells.size || row >= table.rows().size) return
        var height = layout.font.size + MarkdownTableLayout.VERTICAL_INSETS
        for (column in cells[row].indices) height = Math.max(height, measuredCellHeight(table, row, column, cells[row][column], layout.widths[column], layout.font))
        val line = table.rows()[row].line()
        layout.rowHeights[row] = Math.ceil(height)
        if (present && currentArea != null && searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.TABLE) {
            rendering.setPreviewStyle(line, rendering.paragraphHeight(table, line), false)
            rendering.refreshRowGraphics(table, row)
        }
    }
    fun measuredCellHeight(table: PreviewTable, row: Int, column: Int, cell: MarkdownTableLayout.Cell, width: Double, measuredFont: Font): Double = with(state) {
        if (activeTable?.id() != table.id() || activeRow != row || activeColumn != column) return cell.height
        editorMeasureText.font = measuredFont
        editorMeasureText.text = if (cellEditor.text.isEmpty()) " " else cellEditor.text + "\u200b"
        editorMeasureText.wrappingWidth = Math.max(1.0, width - MarkdownTableLayout.HORIZONTAL_INSETS)
        editorMeasureText.layoutBounds.height + MarkdownTableLayout.VERTICAL_INSETS
    }

    fun syncEditorFromDocument(): Unit = with(state) {
        val table = activeTable ?: return
        if (composingText || handlingInputMethod || structurePending || !table.valid() || activeRow < 0 || activeRow >= table.rows().size) return
        val row = table.rows()[activeRow]
        if (activeColumn < 0 || activeColumn >= row.cells().size) { endCellEditing(false); return }
        val cell = row.cells()[activeColumn]
        val area = currentArea!!
        if (cell.startOffset() < 0 || cell.endOffset() > area.length || cell.startOffset() > cell.endOffset()) { endCellEditing(false); return }
        val value = MarkdownTableCellText.decode(area.getText(cell.startOffset(), cell.endOffset()))
        if (cellEditor.text != value) {
            val caret = cellEditor.caretPosition
            updatingCellEditor = true
            cellEditor.text = value
            cellEditor.positionCaret(Math.min(caret, value.length))
            updatingCellEditor = false
            updateActiveRowHeight()
        }
    }

    private fun onCellKeyPressed(event: KeyEvent): Unit = with(state) {
        if (composingText || handlingInputMethod || event.isAltDown || event.isControlDown && event.isMetaDown) return
        if (event.isShortcutDown && (event.code == KeyCode.Z || event.code == KeyCode.Y && !event.isShiftDown)) {
            event.consume(); performUndoRedo(event.isShiftDown || event.code == KeyCode.Y); return
        }
        if (structurePending) { event.consume(); return }
        if (event.code == KeyCode.ESCAPE) { event.consume(); exitTable(activeTable, 1); return }
        if (event.isShortcutDown && event.code == KeyCode.V && event.isShiftDown) { event.consume(); pastePlainText(); return }
        if (event.isShortcutDown && !event.isShiftDown && event.code == KeyCode.ENTER) {
            event.consume()
            val row = activeRow
            val column = activeColumn
            applyTableEdit(activeTable) { MarkdownTableEdits.insertRow(it, row + 1, column) }
            return
        }
        if (!event.isShortcutDown && event.code == KeyCode.TAB) { event.consume(); moveCell(if (event.isShiftDown) -1 else 1); return }
        val mark = when (MarkdownShortcuts.match(event)) {
            MarkdownShortcuts.BOLD -> "**"
            MarkdownShortcuts.ITALIC -> "*"
            MarkdownShortcuts.STRIKE -> "~~"
            MarkdownShortcuts.INLINE_CODE -> "`"
            else -> return
        }
        event.consume()
        wrapCellSelection(mark)
    }

    fun editCellLink(): Unit = with(state) {
        val area = currentArea
        if (!canModify(activeTable) || composingText || handlingInputMethod) return
        area!!
        val activation = cellActivationRevision
        val version = area.editor.contentVersion
        val source = cellEditor.text
        val selection = cellEditor.selection
        val document = (area.editor as EditorAreaMgrCode).markdownSnapshot(area.text)
        val edit = MarkdownLinkEditing.request(source, selection.start, selection.end, cellEditor.caretPosition, document) ?: return
        if (destroyed || currentArea !== area || activation != cellActivationRevision || version != area.editor.contentVersion
            || source != cellEditor.text || !canModify(activeTable) || composingText || handlingInputMethod) return
        area.undoManager.preventMerge()
        cellEditor.replaceText(edit.start, edit.end, edit.text)
        cellEditor.selectRange(edit.start, edit.start + edit.text.length)
        area.undoManager.preventMerge()
    }
    fun wrapCellSelection(mark: String): Unit = with(state) {
        val area = currentArea ?: return
        if (!area.isEditable || structurePending || composingText || handlingInputMethod) return
        val edit = MarkdownInlineFormatting.toggle(MarkdownInlineSnapshot.create(cellEditor.text), cellEditor.selection.start, cellEditor.selection.end, mark) ?: return
        area.undoManager.preventMerge()
        cellEditor.replaceText(edit.start, edit.end, edit.text)
        cellEditor.selectRange(edit.selectionStart, edit.selectionEnd)
        area.undoManager.preventMerge()
    }
    private fun moveCell(direction: Int): Unit = with(state) {
        val table = activeTable ?: return
        val columns = table.alignments().size
        val index = activeRow * columns + activeColumn + direction
        currentArea!!.undoManager.preventMerge()
        if (index < 0 || index >= table.rows().size * columns) { leaveTable(table, direction); return }
        activateCell(table, index / columns, index % columns, if (direction < 0) Double.MAX_VALUE else 12.0)
    }
    fun exitTable(table: PreviewTable?, direction: Int): Unit = with(state) {
        if (deferWhileComposing { exitTable(table, direction) } || structurePending) return
        val target = session.latestTable(table)
        if (target != null && target.valid() && currentArea != null) leaveTable(target, direction)
    }
    private fun leaveTable(table: PreviewTable, direction: Int): Unit = with(state) {
        val area = currentArea!!
        val adjacent = if (direction > 0) table.lastLine() + 1 else table.firstLine() - 1
        val paragraph = adjacent + direction
        val reusable = adjacent >= 0 && adjacent < area.paragraphs.size && paragraph >= 0 && paragraph < area.paragraphs.size
            && area.getParagraph(adjacent).length() == 0 && paragraph !in tableByLine
        val target: Int
        if (reusable) {
            target = area.getAbsolutePosition(paragraph, if (direction > 0) 0 else area.getParagraph(paragraph).length())
        } else if (area.isEditable) {
            val position = if (direction > 0) table.endOffset() else table.startOffset()
            // 留下永久空白分隔行，避免输入仍被 GFM 识别为表格内容。
            target = if (direction > 0) position + table.lineEnding().length * 2 else position
            endCellEditing(false)
            area.undoManager.preventMerge()
            area.insertText(position, table.lineEnding() + table.lineEnding())
            area.undoManager.preventMerge()
        } else target = if (direction > 0) Math.min(area.length, table.endOffset() + table.lineEnding().length) else Math.max(0, table.startOffset() - 1)
        endCellEditing(false)
        area.moveTo(target)
        area.requestFollowCaret()
        area.requestFocus()
    }

    fun resumePendingUndo(): Unit = with(state) {
        if (structurePending || pendingUndo.isEmpty() || currentArea == null) return
        val area = currentArea
        Platform.runLater {
            if (currentArea === area && !structurePending && pendingUndo.isNotEmpty()) {
                performUndoRedo(pendingUndo.removeFirst())
                if (!structurePending) resumePendingUndo()
            }
        }
    }
    fun performUndoRedo(redo: Boolean): Unit = with(state) {
        val area = currentArea ?: return
        if (!area.isEditable) return
        if (structurePending) { pendingUndo.addLast(redo); return }
        if (!(if (redo) area.undoManager.isRedoAvailable else area.undoManager.isUndoAvailable)) return
        if (deferWhileComposing { performUndoRedo(redo) }) return
        area.undoManager.preventMerge()
        activeTable?.let { pendingTableId = it.id(); pendingRow = activeRow; pendingColumn = activeColumn }
        structurePending = true
        cellEditorFocusPending = cellEditorFocusPending || cellEditor.isFocused
        pendingUndoCaretOffset = -1
        undoChangesInCells = true
        applyingUndoRedo = true
        writingStructure = true
        try { if (redo) area.undoManager.redo() else area.undoManager.undo() }
        finally { applyingUndoRedo = false; writingStructure = false }
        area.undoManager.preventMerge()
        val target = if (undoChangesInCells && pendingUndoCaretOffset >= 0) MarkdownTableUndoSupport.findCell(area.markdownTableDocumentState, pendingUndoCaretOffset) else null
        if (target != null) {
            val cell = target.table.rows()[target.row].cells()[target.column]
            val caret = MarkdownTableCellText.sourceOffset(cell.source(), pendingUndoCaretOffset - cell.startOffset())
            pendingUndoCaretOffset = -1
            pendingTableId = null
            pendingRow = -1
            pendingColumn = -1
            pendingSourceOffset = -1
            structurePending = false
            activateCell(target.table, target.row, target.column, 12.0)
            syncEditorFromDocument()
            cellEditor.positionCaret(caret)
            updateActiveRowHeight()
            resumePendingUndo()
        } else cellEditor.isEditable = false
        session.refreshNow()
    }

    fun onAreaMouseDragged(event: MouseEvent): Unit = with(state) {
        if (isDescendant(event.target, cellEditor) || isDescendantOfType(event.target, ScrollBar::class.java)
            || isDescendantOfType(event.target, MarkdownTableToolbar::class.java)) return
        val area = currentArea
        if (!MarkdownTableRendering.hasVisibleParagraph(area)) return
        area!!
        val point = area.screenToLocal(event.screenX, event.screenY)
        val hit = area.hit(point.x, point.y).characterIndex
        if (hit.isEmpty) return
        val table = tables.firstOrNull { hit.asInt >= it.startOffset() && hit.asInt <= it.endOffset() } ?: return
        if (searchPresentation.mode(table) != MarkdownTableDocumentState.Mode.TABLE) return
        val anchor = area.anchor
        if (anchor < table.startOffset()) { area.selectRange(anchor, table.startOffset()); event.consume() }
        else if (anchor > table.endOffset()) { area.selectRange(anchor, table.endOffset()); event.consume() }
    }
    fun selectedCell(): CellTarget? = with(state) {
        val area = currentArea!!
        val selection = area.selection
        if (selection.length == 0) return null
        val line = area.offsetToPosition(selection.start, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).major
        val table = tableByLine[line]
        val row = rowIndexByLine[line] ?: -1
        if (table == null || !table.valid() || searchPresentation.mode(table) != MarkdownTableDocumentState.Mode.TABLE || row < 0) return null
        for ((column, cell) in table.rows()[row].cells().withIndex()) {
            if (selection.start >= cell.startOffset() && selection.end <= cell.endOffset()) return CellTarget(table, row, column)
        }
        null
    }
    fun selectionHitsCell(table: PreviewTable): Boolean = with(state) {
        val selection = currentArea!!.selection
        var low = 0
        var high = table.rows().size
        while (low < high) {
            val middle = (low + high) ushr 1
            val cells = table.rows()[middle].cells()
            if (cells.last().endOffset() <= selection.start) low = middle + 1 else high = middle
        }
        for (row in low until table.rows().size) for (cell in table.rows()[row].cells()) {
            if (cell.startOffset() >= selection.end) return false
            if (selection.start < cell.endOffset()) return true
        }
        false
    }

    fun toggleMode(table: PreviewTable?): Unit = with(state) {
        if (deferWhileComposing { toggleMode(table) }) return
        if (table == null || structurePending || searchPresentation.isExpanded(table)) return
        val latest = session.latestTable(table)
        if (latest !== table) { if (latest != null) toggleMode(latest); return }
        if (table.mode() == MarkdownTableDocumentState.Mode.SOURCE && !table.valid()) return
        table.setMode(if (table.mode() == MarkdownTableDocumentState.Mode.TABLE) MarkdownTableDocumentState.Mode.SOURCE else MarkdownTableDocumentState.Mode.TABLE)
        if (table.mode() == MarkdownTableDocumentState.Mode.SOURCE) {
            endCellEditing(false)
            currentArea!!.moveTo(Math.min(table.startOffset(), currentArea!!.length))
            currentArea!!.requestFocus()
        }
        rendering.updatePresentation()
    }
    fun applyTableEdit(table: PreviewTable?, operation: (PreviewTable) -> MarkdownTableEdits.Edit?): Unit = with(state) {
        if (deferWhileComposing { applyTableEdit(table, operation) }) return
        val target = session.latestTable(table)
        if (!canModify(target)) return
        val edit = operation(target!!)
        if (edit != null && edit.source != safeTableSource(target)) replaceTable(target, edit.source, edit.row, edit.column)
    }
    fun pastePlainText(): Unit = with(state) {
        if (deferWhileComposing(::pastePlainText)) return
        if (canModify(activeTable) && Clipboard.getSystemClipboard().hasString()) cellEditor.replaceSelection(Clipboard.getSystemClipboard().string)
    }
    private fun importCellImages(importer: (Consumer<String>) -> Unit): Unit = with(state) {
        val area = currentArea
        val table = activeTable!!
        val row = activeRow
        val column = activeColumn
        val activation = cellActivationRevision
        val selection = cellEditor.selection
        importer(Consumer { markdown ->
            if (currentArea === area && activeTable?.id() == table.id() && activeRow == row && activeColumn == column
                && cellActivationRevision == activation && canModify(activeTable) && !composingText && !handlingInputMethod) {
                cellEditor.replaceText(selection.start, selection.end, markdown)
                cellEditor.positionCaret(selection.start + markdown.length)
            } else SnackbarUtils.show(Locales.str("markdown.imageInsertChanged"))
        })
    }
    fun pasteClipboard(forceCells: Boolean): Unit = with(state) {
        if (deferWhileComposing { pasteClipboard(forceCells) } || !canModify(activeTable)) return
        val clipboard = Clipboard.getSystemClipboard()
        val markdownCells = markdownClipboardCells(clipboard)
        if (markdownCells != null) { pasteTableValues(markdownCells, true); return }
        val tsv = tableClipboardTsv(clipboard)
        if (tsv == null && hasTableCells(clipboard)) { SnackbarUtils.show(Locales.str("markdown.tablePasteLimit")); return }
        val value = tsv ?: clipboard.string
        if (value != null && (forceCells || tsv != null || '\t' in value)) {
            val matrix = MarkdownTableEdits.parseTsv(value)
            if (matrix != null) pasteTableValues(matrix, false) else SnackbarUtils.show(Locales.str("markdown.tablePasteLimit"))
            return
        }
        if (!forceCells) {
            if (clipboard.hasImage()) { importCellImages { currentArea!!.markdownAttachments.importClipboardImage(clipboard.image, it) }; return }
            if (clipboard.hasFiles() && clipboard.files.all(MarkdownAttachments::isImage)) {
                importCellImages { currentArea!!.markdownAttachments.importFiles(clipboard.files, it) }; return
            }
            if (clipboard.hasHtml() && clipboard.html.length <= 2 * 1024 * 1024) {
                val cells = MarkdownClipboard.htmlTableCells(clipboard.html)
                if (cells != null) pasteTableValues(cells, true) else cellEditor.replaceSelection(MarkdownClipboard.fromHtml(clipboard.html))
                return
            }
        }
        if (value != null) cellEditor.replaceSelection(value)
    }
    private fun pasteTableValues(values: List<List<String>>, markdown: Boolean): Unit = with(state) {
        val target = session.latestTable(activeTable)
        if (!canModify(target)) return
        val edit = if (markdown) MarkdownTableEdits.pasteMarkdown(target!!, activeRow, activeColumn, values)
            else MarkdownTableEdits.paste(target!!, activeRow, activeColumn, values)
        if (edit == null) SnackbarUtils.show(Locales.str("markdown.tablePasteLimit"))
        else if (edit.source != safeTableSource(target)) replaceTable(target, edit.source, edit.row, edit.column)
    }
    fun optimize(table: PreviewTable?): Unit = with(state) {
        if (deferWhileComposing { optimize(table) }) return
        val latest = session.latestTable(table)
        if (latest !== table) { if (latest != null) optimize(latest); return }
        if (!canModify(table)) return
        val source = safeTableSource(table)
        val formatted = source?.let { MarkdownTableOptimizeManager.format(currentArea, it) }
        if (formatted != null && formatted != source) replaceTable(table!!, formatted, activeRow, activeColumn)
    }
    private fun canModify(table: PreviewTable?): Boolean = with(state) {
        table != null && table.valid() && currentArea?.isEditable == true && !structurePending
    }
    fun deferWhileComposing(action: Runnable): Boolean = with(state) {
        if (!composingText && !handlingInputMethod) return false
        afterComposition = action
        true
    }
    private fun replaceTable(table: PreviewTable, replacement: String?, row: Int, column: Int): Unit = with(state) {
        if (replacement == null || !canModify(table)) return
        structurePending = true
        cellEditor.isEditable = false
        pendingTableId = table.id()
        pendingRow = row
        pendingColumn = column
        val area = currentArea!!
        pendingSourceOffset = if (searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.SOURCE) area.caretPosition - table.startOffset() else -1
        area.undoManager.preventMerge()
        activeTable = table
        writingStructure = true
        try { area.replaceText(table.startOffset(), table.endOffset(), replacement) } finally { writingStructure = false }
        area.undoManager.preventMerge()
        session.refreshNow()
    }

    fun restorePendingCell(): Unit = with(state) {
        if (pendingTableId == null) { syncActiveCellAfterParse(); return }
        for (table in tables) {
            if (table.id() != pendingTableId || !table.valid()) continue
            val layout = layouts[table.id()]
            if (searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.TABLE
                && (layout == null || layout.cells == null || layout.table !== table)) return
            structurePending = false
            cellEditor.isEditable = currentArea!!.isEditable
            resumePendingUndo()
            var row = pendingRow
            var column = pendingColumn
            pendingTableId = null
            pendingRow = -1
            pendingColumn = -1
            val sourceOffset = pendingSourceOffset
            pendingSourceOffset = -1
            val undoCaretOffset = pendingUndoCaretOffset
            pendingUndoCaretOffset = -1
            if (row >= 0 && table.rows().isNotEmpty()) {
                if (row >= table.rows().size) row = table.rows().size - 1
                if (column < 0) column = 0 else if (column >= table.alignments().size) column = table.alignments().size - 1
                if (searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.TABLE) {
                    activateCell(table, row, column, 12.0)
                    if (undoCaretOffset >= 0) {
                        val cell = table.rows()[row].cells()[column]
                        cellEditor.positionCaret(MarkdownTableCellText.sourceOffset(cell.source(), undoCaretOffset - cell.startOffset()))
                    }
                } else {
                    endCellEditing(false)
                    currentArea!!.moveTo(table.rows()[row].cells()[column].startOffset())
                    currentArea!!.requestFocus()
                }
            } else {
                val target = if (sourceOffset < 0) table.startOffset() else table.startOffset() + Math.min(sourceOffset, table.endOffset() - table.startOffset())
                endCellEditing(false)
                currentArea!!.moveTo(Math.min(target, currentArea!!.length))
                currentArea!!.requestFocus()
            }
            pendingInput.resume()
            return
        }
        pendingTableId = null
        pendingRow = -1
        pendingColumn = -1
        pendingSourceOffset = -1
        structurePending = false
        endCellEditingAtFallback()
        resumePendingUndo()
    }
    fun syncActiveCellAfterParse(): Unit = with(state) {
        val active = activeTable ?: return
        for (table in tables) {
            if (table.id() == active.id() && table.valid() && activeRow < table.rows().size && activeColumn < table.alignments().size) {
                activeTable = table
                if (searchPresentation.isExpanded(table)) { endCellEditing(cellEditor.isFocused); return }
                syncEditorFromDocument()
                return
            }
        }
        endCellEditingAtFallback()
    }
    fun endCellEditingAtFallback(): Unit = with(state) {
        var target = currentArea?.caretPosition ?: 0
        val table = activeTable
        if (table != null && activeRow >= 0 && activeRow < table.rows().size && activeColumn >= 0 && activeColumn < table.alignments().size) {
            target = table.rows()[activeRow].cells()[activeColumn].startOffset()
        }
        endCellEditing(false)
        currentArea?.let {
            if (target < 0) target = 0 else if (target > it.length) target = it.length
            it.moveTo(target)
            it.requestFocus()
        }
    }
    private fun safeTableSource(table: PreviewTable?): String? = with(state) {
        if (table == null || table.startOffset() < 0 || table.endOffset() > currentArea!!.length || table.startOffset() > table.endOffset()) return null
        currentArea!!.getText(table.startOffset(), table.endOffset())
    }
}

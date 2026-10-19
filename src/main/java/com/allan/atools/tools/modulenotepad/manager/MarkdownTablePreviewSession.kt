package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.richtext.codearea.MarkdownEditorSupport.supportsMarkdown
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState
import com.allan.atools.richtext.codearea.MarkdownTableParser
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent

/** 绑定文档、合并解析请求，并协调表格状态与布局生命周期。 */
class MarkdownTablePreviewSession(private val state: MarkdownTablePreviewState) {
    private val refreshScheduler = LatestRefreshScheduler(180, 420, ::startRefresh)
    private val searchStylesChanged = InvalidationListener { state.rendering.updateSearchPresentation() }
    private val layoutChanged = InvalidationListener { state.rendering.requestLayoutRefresh() }
    private val selectionChanged = InvalidationListener {
        state.menus.hideCellMenu()
        state.rendering.updateSelection()
    }
    private val editableChanged = InvalidationListener {
        state.cellEditor.isEditable = state.currentArea?.isEditable == true && !state.structurePending
        state.menus.updateToolbar()
    }
    private val areaMouseDragged = EventHandler<MouseEvent> { state.editing.onAreaMouseDragged(it) }

    fun refreshCurrentFile(area: EditorArea?): Unit = with(state) {
        if (area != null && area === currentArea && textChanges != null && area.markdownPreviewEnabled && boundFile == area.editor.sourceFile) return
        unbindEditor()
        currentArea = area
        if (area == null || !supportsMarkdown(area) || !area.markdownPreviewEnabled) return
        boundFile = area.editor.sourceFile
        textChanges = area.plainTextChanges().subscribe { onTextChanged(it.position, it.removed, it.inserted) }
        viewportChanges = area.viewportDirtyEvents().subscribe { rendering.requestLayoutRefresh() }
        area.caretPositionProperty().addListener(selectionChanged)
        area.selectionProperty().addListener(selectionChanged)
        area.markdownPresentation.styleRevisionProperty().addListener(searchStylesChanged)
        area.editableProperty().addListener(editableChanged)
        area.widthProperty().addListener(layoutChanged)
        area.sceneProperty().addListener(layoutChanged)
        area.paddingProperty().addListener(layoutChanged)
        area.wrapTextProperty().addListener(layoutChanged)
        area.paragraphGraphicFactoryProperty().addListener(layoutChanged)
        UIContext.getFontSizeProperty().addListener(layoutChanged)
        UIContext.getFontThemeProperty().addListener(layoutChanged)
        area.addEventFilter(MouseEvent.MOUSE_DRAGGED, areaMouseDragged)
        area.addParagraphGraphicDecorator(this@MarkdownTablePreviewSession, rendering::createGraphic)
        val saved = savedLayouts.remove(area)
        if (saved != null && saved.version == area.editor.contentVersion && saved.file == boundFile && !area.editor.isRealtimeProcessingLimitReached) {
            tables = saved.tables
            inlineContents = saved.contents
            layouts.putAll(saved.layouts)
            layoutWidth = saved.width
            graphicWidth = saved.graphicWidth
            textPadding = saved.textPadding
            font = saved.font
            cellEditor.font = font
            parsedVersion = saved.version
            rebuildLineIndex()
            rendering.updateSearchPresentation()
            rendering.updatePresentation()
            rendering.requestLayoutRefresh()
        } else refreshScheduler.startNow()
    }

    fun destroy(): Unit = with(state) {
        destroyed = true
        unbindEditor()
        savedLayouts.clear()
        refreshScheduler.dispose()
    }

    private fun unbindEditor(): Unit = with(state) {
        saveLayouts()
        parsedVersion = -1L
        boundFile = null
        inputMethodRevision++
        composingText = false
        handlingInputMethod = false
        structurePending = false
        pendingUndo.clear()
        writingRow = null
        cellActivationRevision++
        afterComposition = null
        rendering.layoutTimer.stop()
        layoutJobs.clear()
        presentationJobs.clear()
        layoutGeneration++
        layoutPending = false
        refreshScheduler.invalidate()
        parseTask?.cancel(true)
        parseTask = null
        textChanges?.unsubscribe()
        textChanges = null
        viewportChanges?.unsubscribe()
        viewportChanges = null
        val area = currentArea
        if (area != null) {
            area.caretPositionProperty().removeListener(selectionChanged)
            area.selectionProperty().removeListener(selectionChanged)
            area.markdownPresentation.styleRevisionProperty().removeListener(searchStylesChanged)
            area.editableProperty().removeListener(editableChanged)
            area.widthProperty().removeListener(layoutChanged)
            area.sceneProperty().removeListener(layoutChanged)
            area.paddingProperty().removeListener(layoutChanged)
            area.wrapTextProperty().removeListener(layoutChanged)
            area.paragraphGraphicFactoryProperty().removeListener(layoutChanged)
            UIContext.getFontSizeProperty().removeListener(layoutChanged)
            UIContext.getFontThemeProperty().removeListener(layoutChanged)
            area.removeEventFilter(MouseEvent.MOUSE_DRAGGED, areaMouseDragged)
            rendering.clearPresentation()
            area.removeParagraphGraphicDecorator(this@MarkdownTablePreviewSession)
        }
        currentArea = null
        editing.endCellEditing(false)
        cellEditorFocusScene?.removeEventFilter(MouseEvent.MOUSE_PRESSED, editing.cancelCellEditorFocusRestore)
        cellEditorFocusScene = null
        toolbars.clear()
        rendering.layoutTimer.stop()
        layoutJobs.clear()
        presentationJobs.clear()
        tables = emptyList()
        searchPresentation.clear()
        tableByLine.clear()
        rowIndexByLine.clear()
        inlineContents = emptyMap()
        tableImages.clear()
        measureFlow.children.clear()
        editorMeasureText.text = ""
        cellEditor.clear()
        layouts.clear()
        layoutWidth = -1.0
    }

    /** 标签切换只缓存测量数据，不持有离屏节点；大表和未完成的布局不进入缓存。 */
    private fun saveLayouts(): Unit = with(state) {
        savedLayouts.keys.removeIf { it.editor.isDestroyed() }
        val area = currentArea ?: return
        val measuredFont = font ?: return
        if (destroyed || area.editor.isDestroyed() || parsedVersion != area.editor.contentVersion
            || layoutJobs.isNotEmpty() || presentationJobs.isNotEmpty()) return
        var count = 0
        val copies = HashMap<String, MarkdownTableMeasuredLayout>()
        for ((id, layout) in layouts) {
            val cells = layout.cells ?: return
            if (layout.frozenWidths) return
            for (row in cells) count += row.size
            if (count > 20_000) return
            val copy = MarkdownTableMeasuredLayout()
            copy.table = layout.table
            copy.cells = cells
            copy.widths = layout.widths
            copy.totalWidth = layout.totalWidth
            copy.viewportWidth = layout.viewportWidth
            copy.font = layout.font
            copy.alignments = layout.alignments
            copy.rowHeights.putAll(layout.rowHeights)
            copy.measuredVersion = layout.measuredVersion
            copies[id] = copy
        }
        savedLayouts[area] = MarkdownTableSavedLayouts(parsedVersion, boundFile, System.nanoTime(), count,
            tables, inlineContents, copies, layoutWidth, graphicWidth, textPadding, measuredFont)
        while (savedLayouts.size > 4 || savedLayouts.values.sumOf { it.cellCount } > 20_000) {
            var oldest: EditorArea? = null
            var time = Long.MAX_VALUE
            for ((owner, saved) in savedLayouts) {
                if (saved.savedAt < time) { oldest = owner; time = saved.savedAt }
            }
            savedLayouts.remove(oldest)
        }
    }

    private fun onTextChanged(position: Int, removed: String, inserted: String): Unit = with(state) {
        val area = currentArea ?: return
        if (area.editor.isRealtimeProcessingLimitReached) {
            layoutJobs.clear()
            presentationJobs.clear()
            structurePending = false
            pendingUndo.clear()
            inlineContents = emptyMap()
            area.markdownTableDocumentState.reset()
            tables = emptyList()
            rebuildLineIndex()
            rendering.updatePresentation()
            editing.endCellEditing(false)
            toolbars.clear()
            return
        }
        if (area.markdownTableDocumentState.tables.isEmpty() && tables.isNotEmpty()) {
            tables = emptyList()
            rebuildLineIndex()
        }
        val lineDelta = inserted.count { it == '\n' } - removed.count { it == '\n' }
        val shiftedTables = if (lineDelta == 0) emptyList() else tables.filter { position + removed.length <= it.startOffset() }
        val undoCell = applyingUndoRedo && undoChangesInCells
            && MarkdownTableUndoSupport.applyCellChange(area.markdownTableDocumentState, position, removed, inserted)
        if (applyingUndoRedo) {
            val change = MarkdownTableCellText.change(removed, inserted)
            pendingUndoCaretOffset = position + change.start + change.text.length
            undoChangesInCells = undoChangesInCells && undoCell
        }
        if (undoCell) {
            MarkdownTableUndoSupport.findCell(area.markdownTableDocumentState, pendingUndoCaretOffset)?.let {
                pendingTableId = it.table.id()
                pendingRow = it.row
                pendingColumn = it.column
            }
        } else if ((writingCell || writingStructure || writingRow != null) && activeTable != null) {
            val active = activeTable!!
            val row = writingRow
            if (row != null) {
                area.markdownTableDocumentState.applyRowChange(active.id(), activeRow, row)
            } else if (writingCell) {
                area.markdownTableDocumentState.applyCellChange(active.id(), activeRow, activeColumn, position, removed, inserted)
            } else {
                area.markdownTableDocumentState.applyKnownTableChange(active.id(), position, removed, inserted)
                rebuildLineIndex()
            }
        } else {
            area.markdownTableDocumentState.applyTextChange(position, removed, inserted)
            rebuildLineIndex()
            if ('\n' in removed || '\n' in inserted || tables.any { !it.valid() }) rendering.updatePresentation()
        }
        // 前方换行仅重新绑定发生位移的表格行，单元格布局继续复用。
        for (table in shiftedTables) {
            if (table.valid() && searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.TABLE && rendering.hasTableLayout(table)) {
                rendering.recreateTableGraphics(table)
            }
        }
        if (!writingCell && !writingStructure && writingRow == null) editing.syncEditorFromDocument()
        refreshScheduler.request()
        menus.updateToolbar()
    }

    private fun startRefresh(requestId: Long): Unit = with(state) {
        val area = currentArea
        if (destroyed || area == null || area.editor.isRealtimeProcessingLimitReached) {
            refreshScheduler.complete(requestId, null)
            return
        }
        val version = area.editor.contentVersion
        val text = area.text
        parseTask = ThreadUtils.submit {
            var parsed: List<PreviewTable>? = null
            val contents = HashMap<String, List<MarkdownTableLayout.Run>>()
            try {
                val snapshot = (area.editor as EditorAreaMgrCode).markdownSnapshot(text)
                parsed = MarkdownTableParser.parse(text, snapshot.root) { source, node ->
                    contents[source] = MarkdownTableLayout.parse(node)
                }?.filter { !snapshot.isEmbeddedLine(it.firstLine()) }
            } catch (e: RuntimeException) {
                Log.e("parse markdown tables failed", e)
            }
            val result = parsed
            Platform.runLater {
                refreshScheduler.complete(requestId) apply@{
                    parseTask = null
                    if (destroyed || area !== currentArea || version != area.editor.contentVersion || area.editor.isRealtimeProcessingLimitReached) return@apply
                    if (result == null) {
                        structurePending = false
                        pendingUndo.clear()
                        editing.endCellEditingAtFallback()
                        return@apply
                    }
                    if (pendingTableId == null) structurePending = false
                    cellEditor.isEditable = area.isEditable && !structurePending
                    area.markdownTableDocumentState.reconcile(result)
                    parsedVersion = version
                    tables = area.markdownTableDocumentState.tables
                    inlineContents = contents.toMap()
                    rebuildLineIndex()
                    rendering.updateSearchPresentation()
                    if (pendingUndoCaretOffset >= 0) {
                        val target = MarkdownTableUndoSupport.findCell(area.markdownTableDocumentState, pendingUndoCaretOffset)
                        if (target != null) {
                            pendingTableId = target.table.id()
                            pendingRow = target.row
                            pendingColumn = target.column
                        } else {
                            val offset = Math.min(pendingUndoCaretOffset, area.length)
                            structurePending = false
                            editing.endCellEditing(false)
                            area.moveTo(offset)
                            area.requestFocus()
                        }
                    }
                    if (pendingTableId != null && tables.none { it.id() == pendingTableId && it.valid() }) {
                        structurePending = false
                        editing.endCellEditingAtFallback()
                    }
                    if (pendingTableId == null) editing.syncActiveCellAfterParse()
                    rendering.applyParsedLayout()
                    if (!structurePending) editing.resumePendingUndo()
                }
            }
        }
    }

    fun refreshNow() = refreshScheduler.startNow()

    fun latestTable(previous: PreviewTable?): PreviewTable? =
        if (previous == null) null else state.tables.firstOrNull { it.id() == previous.id() }

    private fun rebuildLineIndex(): Unit = with(state) {
        tableByLine.clear()
        rowIndexByLine.clear()
        val ids = HashSet<String>()
        for (table in tables) {
            ids.add(table.id())
            if (!table.valid()) continue
            for (line in table.firstLine()..table.lastLine()) tableByLine[line] = table
            for ((row, value) in table.rows().withIndex()) rowIndexByLine[value.line()] = row
            val layout = layouts[table.id()]
            if (layout != null && layout.cells?.size == table.rows().size && layout.widths.size == table.alignments().size) layout.table = table
        }
        layouts.keys.removeIf { it !in ids }
    }
}

package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.richtext.codearea.MarkdownEditorSupport.textLeftPadding
import com.allan.atools.richtext.codearea.MarkdownParagraphWrapSupport
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState
import com.allan.uilibs.richtexts.CodeArea
import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ScrollBar
import javafx.scene.control.skin.TextAreaSkin
import javafx.scene.input.InputMethodEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment

/** 分帧测量表格，并维护可见行的节点和段落呈现。 */
class MarkdownTableRendering(private val state: MarkdownTablePreviewState) {
    companion object {
        private const val PREVIEW_CLASS = "markdown-table-preview-para"
        private const val HEIGHT_PREFIX = CodeArea.PARAGRAPH_PREVIEW_HEIGHT_PREFIX
        private const val SOURCE_HEADER_CLASS = "markdown-table-source-header"

        fun hasShowingWindow(node: Node?): Boolean = node?.scene?.window?.isShowing == true
        fun hasVisibleParagraph(area: EditorArea?): Boolean = hasShowingWindow(area) && area!!.visibleParagraphs.isNotEmpty()
        private fun setStyleClass(node: Node, style: String, enabled: Boolean) {
            val existing = style in node.styleClass
            if (enabled && !existing) node.styleClass.add(style) else if (!enabled && existing) node.styleClass.remove(style)
        }
    }

    val layoutTimer = object : AnimationTimer() {
        override fun handle(now: Long): Unit = with(state) {
            if (composingText || handlingInputMethod) { stop(); return }
            if (layoutPending) { layoutPending = false; refreshLayout() }
            val deadline = System.nanoTime() + 4_000_000
            val area = currentArea
            if (area != null && presentationJobs.isNotEmpty()) {
                val visible = hasVisibleParagraph(area)
                val first = if (visible) area.firstVisibleParToAllParIndex() else 0
                val last = if (visible) area.lastVisibleParToAllParIndex() else -1
                var priority: PresentationJob? = null
                for (job in presentationJobs) {
                    job.updateViewport(first, last)
                    if (priority == null && job.hasVisibleWork()) priority = job
                }
                if (priority != null && presentationJobs.peek() !== priority) {
                    presentationJobs.remove(priority)
                    presentationJobs.addFirst(priority)
                }
            }
            area?.suspendVisibleParsWhileInvoke {
                while ((presentationJobs.isNotEmpty() || layoutJobs.isNotEmpty()) && System.nanoTime() < deadline) {
                    if (presentationJobs.isNotEmpty() && (layoutJobs.isEmpty() || presentationJobs.peek().hasVisibleWork())) {
                        val job = presentationJobs.peek()
                        if (!job.isCurrent() || job.step()) presentationJobs.remove(job)
                    } else {
                        val job = layoutJobs.peek()
                        if (!job.isCurrent()) layoutJobs.remove()
                        else if (job.step()) { layoutJobs.remove(); job.apply() }
                    }
                }
            }
            if (layoutJobs.isEmpty() && presentationJobs.isEmpty() && !layoutPending) stop()
        }
    }

    fun requestLayoutRefresh(): Unit = with(state) {
        if (currentArea != null && !destroyed) { layoutPending = true; layoutTimer.start() }
    }

    private fun refreshLayout(): Unit = with(state) {
        val area = currentArea ?: return
        if (tables.isEmpty() || area.width <= 0 || area.scene == null) return
        val nextGraphicWidth = area.lookup(".lineno")?.prefWidth(-1.0) ?: 0.0
        val nextTextPadding = textLeftPadding(area)
        val nextFont = Font.font("JetBrains Mono", UIContext.getFontSizeProperty().get().toDouble())
        val available = Math.floor(Math.max(1.0, area.width - area.insets.left - area.insets.right - nextGraphicWidth - nextTextPadding))
        if (layoutWidth == available && graphicWidth == nextGraphicWidth && textPadding == nextTextPadding && nextFont == font) return
        layoutWidth = available
        graphicWidth = nextGraphicWidth
        textPadding = nextTextPadding
        font = nextFont
        cellEditor.font = font
        queueLayouts(true)
    }

    fun applyParsedLayout(): Unit = with(state) {
        if (layoutWidth <= 0 || font == null) refreshLayout() else queueLayouts(false)
    }

    fun queueLayouts(resize: Boolean): Unit = with(state) {
        layoutGeneration++
        layoutJobs.clear()
        presentationJobs.clear()
        updatePresentation()
        for (table in tables) {
            if (!table.valid()) continue
            val layout = tableLayout(table)
            if (!resize && table.id() != pendingTableId && canReuseLayout(table, layout)) {
                layout.table = table
                for (graphic in layout.graphics.toList()) graphic.updateSelectionStyle()
                continue
            }
            val editing = activeTable?.id() == table.id()
            layoutJobs.add(LayoutJob(table, layout, editing && !resize))
        }
        menus.updateToolbar()
        layoutTimer.start()
    }

    /** 内容、样式和宽度都未变化时，只更新源码映射。 */
    private fun canReuseLayout(table: PreviewTable, layout: MarkdownTableMeasuredLayout): Boolean = with(state) {
        val cells = layout.cells ?: return false
        if (cells.size != table.rows().size || layout.viewportWidth != layoutWidth || font != layout.font || table.alignments() != layout.alignments) return false
        if (layout.frozenWidths && activeTable?.id() != table.id()) return false
        for ((row, values) in cells.withIndex()) {
            val sourceRow = table.rows()[row]
            if (values.size != sourceRow.cells().size) return false
            for ((column, cell) in values.withIndex()) {
                val source = sourceRow.cells()[column].source()
                if (cell.source != source || cell.header != sourceRow.header() || cell.runs != inlineContents[source]) return false
            }
        }
        true
    }

    /** 测量完整表格，完成后一次性替换布局数值。 */
    inner class LayoutJob(private val table: PreviewTable, private val layout: MarkdownTableMeasuredLayout, freezeWidths: Boolean) {
        private val generation = state.layoutGeneration
        private val documentVersion = state.currentArea!!.editor.contentVersion
        private val measuredFont = state.font!!
        private val available = state.layoutWidth
        private val natural = DoubleArray(table.alignments().size)
        private val cells: List<MutableList<MarkdownTableLayout.Cell>> = List(table.rows().size) { ArrayList(natural.size) }
        private val freezeWidths = freezeWidths && layout.widths.size == natural.size
        private var widths = doubleArrayOf()
        private var row = 0
        private var column = 0
        private var measuringHeights = false
        private val measuredRowHeights = HashMap<Int, Double>()
        private var rowHeight = 0.0

        fun isCurrent(): Boolean = with(state) {
            currentArea != null && !destroyed && generation == layoutGeneration && documentVersion == currentArea!!.editor.contentVersion
        }

        fun step(): Boolean = with(state) {
            if (row == cells.size) {
                if (measuringHeights) return true
                widths = if (freezeWidths) layout.widths.clone()
                    else MarkdownTableLayout.distribute(natural, MarkdownTableLayout.characterWidth(measuredFont), available)
                measuringHeights = true
                row = 0
                rowHeight = measuredFont.size + MarkdownTableLayout.VERTICAL_INSETS
            }
            if (!measuringHeights) {
                val sourceRow = table.rows()[row]
                val source = sourceRow.cells()[column].source()
                val previous = layout.cells?.getOrNull(row)?.getOrNull(column)
                // 已知输入的预览尚未回填时保留文字，下一次解析替换行内样式。
                val runs = inlineContents[source] ?: listOf(MarkdownTableLayout.Run(MarkdownTableCellText.decode(source), false, false, false, false, false))
                val cell = if (previous != null && previous.source == source && previous.font == measuredFont
                    && previous.header == sourceRow.header() && previous.runs == runs) previous.copy()
                    else MarkdownTableLayout.Cell(source, runs, measuredFont, sourceRow.header())
                cell.measureNatural(measureFlow)
                cells[row].add(cell)
                natural[column] = Math.max(natural[column], cell.natural)
            } else {
                cells[row][column].measureHeight(measureFlow, widths[column])
                rowHeight = Math.max(rowHeight, editing.measuredCellHeight(table, row, column, cells[row][column], widths[column], measuredFont))
            }
            if (++column == natural.size) {
                if (measuringHeights) {
                    measuredRowHeights[row] = Math.ceil(rowHeight)
                    rowHeight = measuredFont.size + MarkdownTableLayout.VERTICAL_INSETS
                }
                column = 0
                row++
            }
            false
        }

        fun apply(): Unit = with(state) {
            val previous = layout.cells
            val structureChanged = previous != null && (previous.size != cells.size || layout.widths.size != widths.size)
            layout.table = table
            layout.cells = cells
            layout.widths = widths
            layout.totalWidth = widths.sum()
            layout.viewportWidth = available
            layout.font = measuredFont
            layout.alignments = table.alignments().toList()
            layout.frozenWidths = freezeWidths
            layout.rowHeights = measuredRowHeights
            layout.revision++
            layout.measuredVersion = documentVersion
            if (structureChanged) layout.structureRevision++
            table.setHorizontalOffset(Math.min(table.horizontalOffset(), Math.max(0.0, layout.totalWidth - available)))
            // 行高、可见节点和源码呈现继续按帧提交。
            presentationJobs.addFirst(PresentationJob(table))
        }
    }

    fun tableLayout(table: PreviewTable): MarkdownTableMeasuredLayout = state.layouts.getOrPut(table.id()) { MarkdownTableMeasuredLayout().also { it.table = table } }
    fun hasTableLayout(table: PreviewTable): Boolean {
        val layout = state.layouts[table.id()] ?: return false
        return layout.table === table && layout.cells?.size == table.rows().size && layout.widths.size == table.alignments().size
    }

    fun updateSelection(): Unit = with(state) {
        if (structurePending || writingStructure || writingCell || writingRow != null || movingCellEditor || cellEditorFocusPending) return
        if (!cellEditor.isFocused) {
            val selected = editing.selectedCell()
            if (selected != null && currentArea!!.isFocused) {
                val selection = currentArea!!.selection
                val cell = selected.table.rows()[selected.row].cells()[selected.column]
                val start = MarkdownTableCellText.sourceOffset(cell.source(), selection.start - cell.startOffset())
                val end = MarkdownTableCellText.sourceOffset(cell.source(), selection.end - cell.startOffset())
                editing.activateCell(selected.table, selected.row, selected.column, 12.0)
                Platform.runLater { cellEditor.selectRange(start, end) }
                return
            }
            for (layout in layouts.values) for (graphic in layout.graphics.toList()) {
                if (graphic.scene != null) graphic.updateSelectionStyle()
            }
            menus.updateToolbar()
        }
    }

    fun updateSearchPresentation(): Unit = with(state) {
        val area = currentArea ?: return
        if (editing.deferWhileComposing(Runnable(::updateSearchPresentation))) return
        if (!searchPresentation.refresh(area, tables)) return
        val active = activeTable
        if (!structurePending && active != null && searchPresentation.isExpanded(active)) editing.endCellEditing(cellEditor.isFocused)
        updatePresentation()
        menus.updateToolbar()
    }

    fun updatePresentation(): Unit = with(state) {
        if (currentArea == null) return
        presentationJobs.clear()
        if (tables.isEmpty() && presentationLines.isEmpty()) return
        presentationJobs.add(PresentationJob(null))
        layoutTimer.start()
    }

    /** 可见行优先，离屏样式和旧范围清理也拆成逐行步骤。 */
    inner class PresentationJob(private val target: PreviewTable?) {
        private val generation = state.layoutGeneration
        private val version = state.currentArea!!.editor.contentVersion
        private val targets = if (target == null) state.tables.toList() else listOf(target)
        private val oldLines = if (target == null) state.presentationLines.toList() else emptyList()
        private var visibleFirst = -1
        private var visibleLast = -1
        private var visibleLine = 0
        private var oldIndex = 0
        private var tableIndex = 0
        private var sourceLine = -1

        init {
            val area = state.currentArea!!
            val visible = hasVisibleParagraph(area)
            updateViewport(if (visible) area.firstVisibleParToAllParIndex() else 0, if (visible) area.lastVisibleParToAllParIndex() else -1)
        }
        fun isCurrent(): Boolean = with(state) {
            currentArea != null && !destroyed && generation == layoutGeneration && version == currentArea!!.editor.contentVersion
        }
        fun hasVisibleWork(): Boolean = visibleLine <= visibleLast
        fun updateViewport(first: Int, last: Int) {
            val nextFirst = if (target != null) Math.max(first, target.firstLine()) else first
            val nextLast = if (target != null) Math.min(last, target.lastLine()) else last
            if (visibleFirst == nextFirst && visibleLast == nextLast) return
            visibleFirst = nextFirst
            visibleLast = nextLast
            visibleLine = nextFirst
        }
        fun step(): Boolean = with(state) {
            if (visibleLine <= visibleLast) {
                val line = visibleLine++
                val table = tableByLine[line]
                if ((target == null || table === target) && (table != null || line in presentationLines)) present(table, line, true)
                return false
            }
            if (oldIndex < oldLines.size) {
                val line = oldLines[oldIndex++]
                if (line < visibleFirst || line > visibleLast) {
                    if (!presented(tableByLine[line])) setPreviewStyle(line, null, false)
                }
                return false
            }
            while (tableIndex < targets.size) {
                val table = targets[tableIndex]
                if (sourceLine < 0) sourceLine = table.firstLine()
                if (sourceLine > table.lastLine()) { tableIndex++; sourceLine = -1; continue }
                val line = sourceLine++
                if (line < visibleFirst || line > visibleLast) present(table, line, false)
                return false
            }
            val pendingLayout = pendingTableId?.let { layouts[it] }
            if (pendingLayout != null && pendingLayout.measuredVersion == version && parsedVersion == version) editing.restorePendingCell()
            menus.updateToolbar()
            true
        }
        private fun presented(table: PreviewTable?): Boolean = with(state) {
            table != null && table.valid() && (searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.SOURCE
                || searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.TABLE && hasTableLayout(table))
        }
        private fun present(table: PreviewTable?, line: Int, visible: Boolean): Unit = with(state) {
            val preview = table != null && table.valid() && searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.TABLE && hasTableLayout(table)
            val source = table != null && table.valid() && searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.SOURCE
            setPreviewStyle(line, if (preview) paragraphHeight(table, line) else null, source)
            if (!visible || !preview) return
            val layout = layouts[table.id()]!!
            for (graphic in layout.graphics.toList()) {
                if (graphic.sourceLine != line || graphic.scene == null) continue
                if (graphic.structureRevision != layout.structureRevision) recreateParagraphGraphic(line)
                else if (graphic.layoutRevision != layout.revision) graphic.refresh()
                return
            }
            // 撤销恢复映射后，即使样式和布局版本未变，也须补建缺失的表格行。
            recreateParagraphGraphic(line)
        }
    }

    fun clearPresentation(): Unit = with(state) {
        for (line in presentationLines.toList()) setPreviewStyle(line, null, false)
        presentationLines.clear()
    }

    fun setPreviewStyle(line: Int, height: Double?, sourceLine: Boolean): Boolean = with(state) {
        val area = currentArea ?: return false
        if (line < 0 || line >= area.paragraphs.size) { presentationLines.remove(line); return false }
        val table = tableByLine[line]
        val sourceHeader = sourceLine && table != null && line == table.firstLine()
        val preview = height != null
        val wasPresented = line in presentationLines
        val existing = area.getParagraph(line).paragraphStyle
        val styles = ArrayList(existing)
        styles.removeIf { it == PREVIEW_CLASS || it == SOURCE_HEADER_CLASS || it == MarkdownParagraphWrapSupport.TABLE_SOURCE_CLASS
            || it.startsWith(HEIGHT_PREFIX) || it.startsWith(CodeArea.TABLE_SOURCE_HEADER_HEIGHT_PREFIX) }
        if (preview) { styles.add(PREVIEW_CLASS); styles.add(HEIGHT_PREFIX + height) }
        if (sourceHeader) { styles.add(SOURCE_HEADER_CLASS); styles.add(CodeArea.TABLE_SOURCE_HEADER_HEIGHT_PREFIX + MarkdownTableToolbar.HEIGHT) }
        if (sourceLine) styles.add(MarkdownParagraphWrapSupport.TABLE_SOURCE_CLASS)
        val presented = preview || sourceLine
        if (presented) presentationLines.add(line) else presentationLines.remove(line)
        val modeChanged = existing.contains(PREVIEW_CLASS) != preview || existing.contains(SOURCE_HEADER_CLASS) != sourceHeader
        val stylesChanged = styles != existing
        if (stylesChanged) area.setParagraphStyle(line, styles)
        if (modeChanged || wasPresented != presented) area.recreateParagraphGraphic(line)
        stylesChanged
    }

    fun createGraphic(line: Int, base: Node?): Node? = with(state) {
        val table = tableByLine[line]
        val layout = table?.let { layouts[it.id()] }
        if (table != null && table.valid() && searchPresentation.mode(table) == MarkdownTableDocumentState.Mode.SOURCE && line == table.firstLine()) {
            return MarkdownTableSourceHeader(currentArea!!, base, menus.createToolbar(table)) { layoutWidth }
        }
        if (table == null || !table.valid() || searchPresentation.mode(table) != MarkdownTableDocumentState.Mode.TABLE || layout == null || !hasTableLayout(table)) return base
        RowGraphic(layout, rowIndexByLine[line] ?: -1, base)
    }

    /** 只为虚拟列表中的行持有节点，直接更新高度、列宽和选择状态。 */
    inner class RowGraphic(val layout: MarkdownTableMeasuredLayout, val row: Int, private val base: Node?) : Pane() {
        val sourceLine = if (row < 0) layout.table.firstLine() + 1 else layout.table.rows()[row].line()
        val structureRevision = layout.structureRevision
        var layoutRevision = 0L
            private set
        private val content = HBox()
        private val viewport = Pane(content)
        private val clipRect = Rectangle()
        private var scrollBar: ScrollBar? = null
        private var syncingScrollBar = false
        private val header = if (row == 0) state.menus.createToolbar(layout.table) else null

        init {
            val area = state.currentArea!!
            content.isManaged = false
            viewport.isManaged = false
            viewport.clip = clipRect
            viewport.layoutXProperty().bind(Bindings.createDoubleBinding(
                { width + textLeftPadding(state.currentArea) - state.currentArea!!.estimatedScrollXProperty().value },
                widthProperty(), area.paddingProperty(), area.estimatedScrollXProperty()))
            children.add(viewport)
            if (header != null) {
                header.isManaged = false
                header.layoutXProperty().bind(viewport.layoutXProperty())
                children.add(header)
            }
            layout.contents.add(content)
            layout.graphics.add(this)
            if (row >= 0) {
                content.styleClass.add("markdown-table-preview-row")
                if (row == 0) content.styleClass.add("markdown-table-preview-header")
                else if (row % 2 == 0) content.styleClass.add("markdown-table-preview-alternate")
                for (column in layout.widths.indices) content.children.add(CellGraphic(this, column))
            }
            addEventFilter(ScrollEvent.SCROLL) { scrollTable(layout.table, it) }
            if (base != null) {
                styleClass.add("markdown-table-line-gutter")
                base.opacity = 0.0
                children.add(base)
            }
            refresh()
        }

        private fun line(): Int = if (ready()) {
            if (row < 0) layout.table.firstLine() + 1 else layout.table.rows()[row].line()
        } else sourceLine
        fun ready(): Boolean = with(state) {
            val cells = layout.cells ?: return false
            layout.table.valid() && searchPresentation.mode(layout.table) == MarkdownTableDocumentState.Mode.TABLE
                && structureRevision == layout.structureRevision && row < cells.size && cells.size == layout.table.rows().size
                && layout.widths.size == layout.table.alignments().size
        }
        fun applyOffset(value: Double) {
            content.translateX = -value
            scrollBar?.let { syncingScrollBar = true; it.value = value; syncingScrollBar = false }
        }
        fun refresh() {
            if (!ready()) return
            content.translateX = -layout.table.horizontalOffset()
            if (header != null) state.menus.refreshToolbar(header, layout.table)
            for (node in content.children) (node as CellGraphic).refresh()
            val showBar = line() == layout.table.lastLine() && layout.totalWidth > layout.viewportWidth
            if (showBar && scrollBar == null) {
                scrollBar = ScrollBar().also { bar ->
                    bar.isManaged = false
                    bar.orientation = Orientation.HORIZONTAL
                    bar.styleClass.add("markdown-table-scroll-bar")
                    bar.layoutXProperty().bind(viewport.layoutXProperty())
                    bar.valueProperty().addListener { _, _, value ->
                        if (!syncingScrollBar) { layout.table.setHorizontalOffset(value.toDouble()); layout.applyOffset(value.toDouble()) }
                    }
                    children.add(bar)
                }
            }
            scrollBar?.let { bar ->
                syncingScrollBar = true
                val maxOffset = Math.max(0.0, layout.totalWidth - layout.viewportWidth)
                bar.isVisible = showBar
                bar.max = maxOffset
                bar.visibleAmount = if (maxOffset == 0.0) 0.0 else maxOffset * layout.viewportWidth / layout.totalWidth
                bar.value = layout.table.horizontalOffset()
                syncingScrollBar = false
            }
            updateSelectionStyle()
            layoutRevision = layout.revision
            requestLayout()
        }
        fun updateSelectionStyle() {
            if (!ready()) return
            val selection = state.currentArea!!.selection
            val table = layout.table
            setStyleClass(content, "markdown-table-selection-hit", selection.length > 0 && selection.start < table.endOffset()
                && selection.end > table.startOffset() && !state.editing.selectionHitsCell(table))
            for (node in content.children) {
                val cell = node as CellGraphic
                val source = table.rows()[row].cells()[cell.column]
                setStyleClass(cell, "markdown-table-selected-cell", selection.length > 0 && selection.start < source.endOffset() && selection.end > source.startOffset())
            }
        }
        override fun computePrefWidth(height: Double): Double = base?.prefWidth(height) ?: 0.0
        override fun computePrefHeight(width: Double): Double = if (ready()) paragraphHeight(layout.table, line()) else height
        override fun layoutChildren() {
            // 结构变化分帧提交，旧行不能读取新结构的越界坐标。
            if (!ready()) return
            val height = layout.rowHeights[row] ?: 2.0
            base?.resizeRelocate(0.0, 0.0, width, getHeight())
            clipRect.width = Math.min(layout.viewportWidth, layout.totalWidth)
            clipRect.height = height
            val top = if (header == null) 0.0 else MarkdownTableToolbar.HEIGHT
            viewport.layoutY = top
            viewport.resize(clipRect.width, height)
            content.resize(layout.totalWidth, height)
            header?.resize(layout.viewportWidth, top)
            scrollBar?.let { it.resize(layout.viewportWidth, MarkdownTableLayout.SCROLL_HEIGHT); it.layoutY = top + height }
        }
    }

    /** 普通输入只更新单元格内容，不重新挂载活动编辑框。 */
    private inner class CellGraphic(private val graphic: RowGraphic, val column: Int) : StackPane() {
        private val text = MarkdownTableTextFlow()
        private var displayed: MarkdownTableLayout.Cell? = null
        init {
            sceneProperty().addListener { _, _, scene -> if (scene != null) refresh() }
            text.minWidth = 0.0
            text.maxWidth = Double.MAX_VALUE
            alignment = Pos.TOP_LEFT
            padding = Insets(if (graphic.row == 0) 9.0 else 10.0, 12.0, 9.0, if (column == 0) 12.0 else 13.0)
            styleClass.add("markdown-table-preview-cell")
            if (column == 0) styleClass.add("markdown-table-preview-first-cell")
            // 先让 TextArea 皮肤处理，再截断冒泡，避免外层编辑器重复写入组合文字。
            addEventHandler(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) { it.consume() }
            setOnMousePressed { event -> with(state) {
                if (!graphic.ready()) { event.consume(); return@setOnMousePressed }
                val table = graphic.layout.table
                if (event.button == MouseButton.PRIMARY) {
                    if (event.isShortcutDown && event.target is Node) {
                        var value = event.target as Node?
                        while (value != null && value !== this@CellGraphic) {
                            val destination = value.userData as? String
                            if (destination != null) {
                                event.consume()
                                (currentArea!!.editor as EditorAreaMgrCode).openMarkdownDestination(destination)
                                return@setOnMousePressed
                            }
                            value = value.parent
                        }
                    }
                    if (MarkdownTableEditing.isDescendant(event.target, cellEditor)) return@setOnMousePressed
                    event.consume()
                    editing.activateCell(table, graphic.row, column, 12.0)
                    val screenX = event.screenX
                    val screenY = event.screenY
                    Platform.runLater {
                        if (activeTable?.id() == table.id() && activeRow == graphic.row && activeColumn == column) {
                            cellEditor.applyCss()
                            cellEditor.layout()
                            val point = cellEditor.screenToLocal(screenX, screenY)
                            val skin = cellEditor.skin as? TextAreaSkin
                            if (point != null && skin != null) skin.positionCaret(skin.getIndex(point.x, point.y), false)
                        }
                    }
                } else if (event.button == MouseButton.SECONDARY) {
                    event.consume()
                    if (activeTable !== table || activeRow != graphic.row || activeColumn != column) editing.activateCell(table, graphic.row, column, 12.0)
                    menus.showCellMenu(table, graphic.row, column, event.screenX, event.screenY)
                }
            } }
        }
        fun refresh(): Unit = with(state) {
            if (!graphic.ready()) return
            val layout = graphic.layout
            val cell = layout.cells!![graphic.row][column]
            val previous = displayed
            if (previous == null || previous.runs != cell.runs || previous.font != cell.font || previous.header != cell.header) {
                MarkdownTableLayout.fill(text, cell.runs, cell.font, cell.header) { run -> tableImages.node(run.image(), run.text(), currentArea!!.editor.sourceFile) }
            }
            displayed = cell
            text.textAlignment = when (layout.table.alignments()[column]) {
                MarkdownTableDocumentState.Alignment.CENTER -> TextAlignment.CENTER
                MarkdownTableDocumentState.Alignment.RIGHT -> TextAlignment.RIGHT
                else -> TextAlignment.LEFT
            }
            val width = layout.widths[column]
            minWidth = width
            prefWidth = width
            maxWidth = width
            if (activeTable?.id() == layout.table.id() && activeRow == graphic.row && activeColumn == column) {
                // 构造中的新行尚未挂载，不能提前将有焦点的输入框移到离屏节点。
                if (scene != null) editing.reparentCellEditor(this@CellGraphic)
            } else if (children.size != 1 || children[0] !== text) children.setAll(text)
        }
    }

    private fun scrollTable(table: PreviewTable, event: ScrollEvent) {
        var delta = event.deltaX
        if (delta == 0.0 && event.isShiftDown) delta = event.deltaY
        val layout = tableLayout(table)
        if (delta == 0.0 || layout.totalWidth <= layout.viewportWidth) return
        val value = Math.max(0.0, Math.min(table.horizontalOffset() - delta, layout.totalWidth - layout.viewportWidth))
        table.setHorizontalOffset(value)
        layout.applyOffset(value)
        event.consume()
    }

    fun refreshRowGraphics(table: PreviewTable, row: Int) {
        val layout = state.layouts[table.id()] ?: return
        for (graphic in layout.graphics.toList()) if (graphic.row == row && graphic.scene != null) graphic.refresh()
    }
    fun paragraphHeight(table: PreviewTable, line: Int): Double {
        val layout = tableLayout(table)
        var height = layout.rowHeights[state.rowIndexByLine[line] ?: -1] ?: 2.0
        if (line == table.firstLine()) height += MarkdownTableToolbar.HEIGHT
        if (line == table.lastLine() && layout.totalWidth > layout.viewportWidth) height += MarkdownTableLayout.SCROLL_HEIGHT
        return height
    }
    fun recreateTableGraphics(table: PreviewTable) {
        val area = state.currentArea
        if (!hasVisibleParagraph(area)) return
        val first = Math.max(table.firstLine(), area!!.firstVisibleParToAllParIndex())
        val last = Math.min(table.lastLine(), area.lastVisibleParToAllParIndex())
        for (line in first..last) recreateParagraphGraphic(line)
    }
    private fun recreateParagraphGraphic(line: Int) {
        val area = state.currentArea ?: return
        if (line >= 0 && line < area.paragraphs.size) area.recreateParagraphGraphic(line)
    }
}

package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState
import javafx.scene.Scene
import javafx.scene.control.ContextMenu
import javafx.scene.layout.HBox
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.reactfx.Subscription
import java.io.File
import java.util.ArrayDeque
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Future

typealias PreviewTable = MarkdownTableDocumentState.Table

/** 单个表格预览会话的共享状态；由协调、布局和编辑组件共同维护。 */
class MarkdownTablePreviewState {
    lateinit var session: MarkdownTablePreviewSession
    lateinit var rendering: MarkdownTableRendering
    lateinit var editing: MarkdownTableEditing
    lateinit var menus: MarkdownTableMenus
    val searchPresentation = MarkdownTableSearchPresentation()
    val cellEditor = object : MarkdownTableCellEditor() {
        override fun paste() = editing.pasteClipboard(false)
    }
    val pendingInput = MarkdownTablePendingInput(cellEditor,
        { this.structurePending },
        { !this.structurePending && !this.destroyed && this.currentArea != null && this.activeTable != null
            && cellEditor.isEditable && !this.handlingInputMethod })
    val tableByLine = HashMap<Int, PreviewTable>()
    val presentationLines = HashSet<Int>()
    val rowIndexByLine = HashMap<Int, Int>()
    var inlineContents: Map<String, List<MarkdownTableLayout.Run>> = emptyMap()
    val measureFlow = TextFlow()
    val tableImages = MarkdownTableImageCache()
    val editorMeasureText = Text()
    val layoutJobs = ArrayDeque<MarkdownTableRendering.LayoutJob>()
    val presentationJobs = ArrayDeque<MarkdownTableRendering.PresentationJob>()
    var layoutGeneration = 0L
    var movingCellEditor = false
    var cellEditorFocusPending = false
    var cellEditorFocusScene: Scene? = null
    var handlingInputMethod = false
    var composingText = false
    var committingInputMethod = false
    var inputMethodRevision = 0L
    var afterComposition: Runnable? = null
    var structurePending = false
    var applyingUndoRedo = false
    var undoChangesInCells = false
    var pendingUndoCaretOffset = -1
    val pendingUndo = ArrayDeque<Boolean>()
    var cellActivationRevision = 0L
    var currentArea: EditorArea? = null
    var textChanges: Subscription? = null
    var viewportChanges: Subscription? = null
    var parseTask: Future<*>? = null
    var tables: List<PreviewTable> = emptyList()
    var cellMenu: ContextMenu? = null
    val toolbars: MutableSet<MarkdownTableToolbar> = Collections.newSetFromMap(WeakHashMap())
    var activeTable: PreviewTable? = null
    var activeRow = -1
    var activeColumn = -1
    var pendingTableId: String? = null
    var pendingRow = -1
    var pendingColumn = -1
    var pendingSourceOffset = -1
    var destroyed = false
    var updatingCellEditor = false
    var writingCell = false
    var writingRow: MarkdownTableDocumentState.Row? = null
    var writingStructure = false
    var layoutPending = false
    var graphicWidth = 0.0
    var textPadding = 0.0
    var layoutWidth = -1.0
    var font: Font? = null
    var parsedVersion = -1L
    var boundFile: File? = null
    val savedLayouts = WeakHashMap<EditorArea, MarkdownTableSavedLayouts>()
    val layouts = HashMap<String, MarkdownTableMeasuredLayout>()
}

/** 当前测量结果及可见行的弱引用，不持有离屏单元格节点。 */
class MarkdownTableMeasuredLayout {
    lateinit var table: PreviewTable
    var cells: List<MutableList<MarkdownTableLayout.Cell>>? = null
    var widths = doubleArrayOf()
    var totalWidth = 0.0
    var viewportWidth = 0.0
    lateinit var font: Font
    var alignments: List<MarkdownTableDocumentState.Alignment> = emptyList()
    var frozenWidths = false
    var rowHeights = HashMap<Int, Double>()
    var revision = 0L
    var structureRevision = 0L
    var measuredVersion = -1L
    val contents: MutableSet<HBox> = Collections.newSetFromMap(WeakHashMap())
    val graphics: MutableSet<MarkdownTableRendering.RowGraphic> = Collections.newSetFromMap(WeakHashMap())

    fun applyOffset(value: Double) {
        for (graphic in graphics.toList()) graphic.applyOffset(value)
    }
}

data class MarkdownTableSavedLayouts(
    val version: Long, val file: File?, val savedAt: Long, val cellCount: Int,
    val tables: List<PreviewTable>, val contents: Map<String, List<MarkdownTableLayout.Run>>,
    val layouts: Map<String, MarkdownTableMeasuredLayout>, val width: Double,
    val graphicWidth: Double, val textPadding: Double, val font: Font
)

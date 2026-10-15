package com.allan.atools.tools.modulenotepad.workspace

import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.StringConverter
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.model.ReadOnlyStyledDocumentBuilder
import org.fxmisc.richtext.model.SegmentOps
import org.fxmisc.richtext.model.StyledSegment
import java.io.File
import java.util.concurrent.FutureTask
import java.util.function.IntFunction

/** 只读 Git 差异窗口，以左右对比展示文件内容，支持折叠上下文与变更跳转。 */
class GitDiffWindow private constructor(owner: Stage, private val directory: File, private val target: File) {
    private val stage = Stage()
    private val filePicker = ComboBox<GitDiffDocument>()
    private val mode = ComboBox<GitDiffMode>()
    private val collapse = ToggleButton(Locales.str("git.collapseUnchanged")).apply {
        isSelected = true
        tooltip = Tooltip(Locales.str("git.collapseHint"))
    }
    private val state = Label()
    private val summary = Label()
    private val leftTitle = Label()
    private val rightTitle = Label()
    private val left = createArea()
    private val right = createArea()
    private val split = SplitPane()
    private val content = BorderPane()
    private val previous = Button("↑ " + Locales.str("git.previousHunk"))
    private val next = Button("↓ " + Locales.str("git.nextHunk"))
    private val open = Button(Locales.str("git.openFile"))
    private var current: GitDiffDocument? = null
    private var rows: List<GitDiffRow> = emptyList()
    private var hunks = emptyList<Int>()
    private var hunkIndex = -1
    private var syncing = false
    private var task: FutureTask<Unit>? = null

    init {
        stage.initOwner(owner)
        stage.title = Locales.str("git.diffTitle") + " · " + target.name
        stage.icons.addAll(owner.icons)
        stage.minWidth = 880.0
        stage.minHeight = 520.0
        mode.items.setAll(GitDiffMode.entries)
        mode.converter = object : StringConverter<GitDiffMode>() {
            override fun toString(value: GitDiffMode?) = value?.let { Locales.str(it.key) }.orEmpty()
            override fun fromString(value: String?) = GitDiffMode.ALL
        }
        mode.value = GitDiffMode.ALL
        mode.setOnAction { reload() }
        collapse.setOnAction { reload() }
        val refresh = Button(Locales.str("refresh")).apply { setOnAction { reload() } }
        previous.setOnAction { navigate(-1) }
        next.setOnAction { navigate(1) }
        open.setOnAction {
            val file = current?.file
            if (file?.isFile == true) AllEditorsManager.Instance.openFile(file, true, true)
        }
        filePicker.converter = object : StringConverter<GitDiffDocument>() {
            override fun toString(value: GitDiffDocument?) = value?.path.orEmpty()
            override fun fromString(value: String?): GitDiffDocument? = null
        }
        filePicker.prefWidth = 280.0
        filePicker.isVisible = false
        filePicker.isManaged = false
        filePicker.valueProperty().addListener { _, _, document -> current = document; render() }
        val toolbar = FlowPane(8.0, 6.0, mode, filePicker, collapse, previous, next, refresh, open).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(10.0, 12.0, 10.0, 12.0)
            styleClass.add("git-diff-toolbar")
        }
        val leftBox = VBox(leftTitle, VirtualizedScrollPane(left)).apply { VBox.setVgrow(children[1], Priority.ALWAYS) }
        val rightBox = VBox(rightTitle, VirtualizedScrollPane(right)).apply { VBox.setVgrow(children[1], Priority.ALWAYS) }
        listOf(leftTitle, rightTitle).forEach { it.styleClass.add("git-diff-side-title"); it.maxWidth = Double.MAX_VALUE }
        split.items.setAll(leftBox, rightBox)
        split.setDividerPositions(0.5)
        left.estimatedScrollYProperty().addListener { _, _, value ->
            if (!syncing) { syncing = true; try { right.estimatedScrollYProperty().value = value } finally { syncing = false } }
        }
        right.estimatedScrollYProperty().addListener { _, _, value ->
            if (!syncing) { syncing = true; try { left.estimatedScrollYProperty().value = value } finally { syncing = false } }
        }
        val statusBar = HBox(16.0, state, summary).apply {
            padding = Insets(7.0, 12.0, 7.0, 12.0); alignment = Pos.CENTER_LEFT
            styleClass.add("git-diff-status-bar")
            state.styleClass.add("normal-desc-label"); summary.styleClass.add("normal-label")
            HBox.setHgrow(state, Priority.ALWAYS)
        }
        val root = BorderPane(content, toolbar, null, statusBar, null).apply {
            styleClass.addAll("custom-main-bg", "git-diff-window")
        }
        stage.scene = Scene(root, 1240.0, 780.0).apply {
            stylesheets.addAll(owner.scene.stylesheets)
            stylesheets.add(ResLocation.getURL("css", "git.css").toExternalForm())
            addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                when {
                    event.code == KeyCode.F7 -> { navigate(if (event.isShiftDown) -1 else 1); event.consume() }
                    event.code == KeyCode.ESCAPE -> stage.close()
                }
            }
        }
        stage.setOnHidden {
            task?.cancel(true); task = null
            left.dispose(); right.dispose()
        }
        stage.show()
        reload()
    }

    private fun createArea(): CodeArea {
        val area = CodeArea()
        area.isEditable = false
        area.isWrapText = false
        area.styleClass.add("git-diff-area")
        area.contextMenu = ContextMenu(MenuItem(Locales.str("copy")).apply { setOnAction { area.copy() } },
            MenuItem(Locales.str("git.selectAll")).apply { setOnAction { area.selectAll() } })
        return area
    }

    private fun reload() {
        task?.cancel(true)
        val selected = current?.path
        current = null
        filePicker.items.clear()
        filePicker.isVisible = false
        filePicker.isManaged = false
        render()
        state.text = Locales.str("git.loading")
        val comparison = mode.value
        val folded = collapse.isSelected
        lateinit var request: FutureTask<Unit>
        request = FutureTask {
            try {
                val result = GitRepository.diff(directory, target, comparison, folded)
                Platform.runLater {
                    if (task !== request || request.isCancelled || !stage.isShowing || ThreadUtils.sBeClosing) return@runLater
                    filePicker.items.setAll(result.documents)
                    filePicker.isVisible = result.documents.size > 1
                    filePicker.isManaged = filePicker.isVisible
                    val document = result.documents.firstOrNull { it.path == selected } ?: result.documents.firstOrNull()
                    filePicker.value = document
                    if (document == null) render()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                if (!request.isCancelled) {
                    Log.e("Git diff failed", e)
                    Platform.runLater {
                        if (task === request && !request.isCancelled && stage.isShowing && !ThreadUtils.sBeClosing) {
                            state.text = Locales.str("git.diffFailed")
                            content.center = TextArea(Locales.str("git.diffFailed") + "\n\n" + e.message).apply {
                                isEditable = false; isWrapText = true
                            }
                        }
                    }
                }
            }
            Unit
        }
        task = request
        GitRepository.execute(request)
    }

    private fun render() {
        val document = current
        open.isDisable = document == null || !document.file.isFile ||
            com.allan.atools.ui.controls.DirAndFileJFXTreeCell.IsSupportOpenFile(document.file) !=
                com.allan.atools.ui.controls.DirAndFileJFXTreeCell.OpenMode.Text
        summary.text = ""
        hunkIndex = -1
        if (document == null) {
            rows = emptyList(); hunks = emptyList()
            state.text = Locales.str("git.noChanges")
            previous.isDisable = true; next.isDisable = true
            content.center = Label(Locales.str("git.noChanges")).apply { styleClass.add("normal-desc-label") }
            return
        }
        rows = document.aligned
        hunks = rows.indices.filter { rows[it].kind == "change" && (it == 0 || rows[it - 1].kind != "change") }
        previous.isDisable = hunks.isEmpty(); next.isDisable = hunks.isEmpty()
        val before = when {
            document.emptyBase -> Locales.str("git.emptyBase")
            document.conflictBase -> Locales.str("git.conflictBase")
            mode.value == GitDiffMode.WORKTREE -> Locales.str("git.index")
            else -> "HEAD"
        }
        val after = if (mode.value == GitDiffMode.INDEX) Locales.str("git.index") else Locales.str("git.worktree")
        leftTitle.text = before + " · " + (if (mode.value == GitDiffMode.WORKTREE) document.path else document.previous ?: document.path)
        rightTitle.text = after + " · " + document.path
        leftTitle.tooltip = Tooltip(leftTitle.text)
        rightTitle.tooltip = Tooltip(rightTitle.text)
        syncing = true
        try { fill(left, rows, -1); fill(right, rows, 1) } finally { syncing = false }
        content.center = split
        state.text = "+${document.additions}  −${document.deletions}   " + Locales.str("git.hunks") + ": ${hunks.size}" +
            if (collapse.isSelected) "   ·   " + Locales.str("git.context50") else ""
        if (hunks.isNotEmpty()) navigate(1)
    }

    private fun fill(area: CodeArea, data: List<GitDiffRow>, side: Int) {
        val builder = ReadOnlyStyledDocumentBuilder<Collection<String>, String, Collection<String>>(
            SegmentOps.styledTextOps<Collection<String>>(), emptyList())
        area.paragraphGraphicFactory = IntFunction { index ->
            val row = data.getOrNull(index)
            val number = when {
                side < 0 -> row?.oldLine?.toString().orEmpty()
                else -> row?.newLine?.toString().orEmpty()
            }
            Label(number).apply { styleClass.add("git-diff-number"); minWidth = 64.0; alignment = Pos.CENTER_RIGHT }
        }
        for (row in data) {
            val kind = when {
                row.kind == "change" -> if (side < 0 && row.left != null) "deleted" else if (side > 0 && row.right != null) "added" else "gap"
                else -> row.kind
            }
            val text = if (row.kind == "fold") "⋯ " + Locales.str("git.foldedLines").replace("%d", row.foldedLines.toString()) + " ⋯"
                else if (side < 0) row.left.orEmpty() else row.right.orEmpty()
            val segments = mutableListOf<StyledSegment<String, Collection<String>>>()
            // 配对的修改行突出不同的字符区间，保留整行背景作为主要标记。
            if (row.kind == "change" && row.left != null && row.right != null) {
                val a = row.left; val b = row.right
                var prefix = 0
                while (prefix < a.length && prefix < b.length && a[prefix] == b[prefix]) prefix++
                var suffix = 0
                while (suffix < a.length - prefix && suffix < b.length - prefix && a[a.length - suffix - 1] == b[b.length - suffix - 1]) suffix++
                if (prefix > 0 && prefix < a.length && Character.isLowSurrogate(a[prefix])) prefix--
                if (suffix > 0 && Character.isLowSurrogate(a[a.length - suffix])) suffix--
                val end = (if (side < 0) a.length else b.length) - suffix
                if (end > prefix) {
                    if (prefix > 0) segments.add(StyledSegment(text.substring(0, prefix), emptyList()))
                    segments.add(StyledSegment(text.substring(prefix, end), listOf("git-diff-word-$kind")))
                    if (end < text.length) segments.add(StyledSegment(text.substring(end), emptyList()))
                }
            }
            if (segments.isEmpty()) segments.add(StyledSegment(text, emptyList()))
            builder.addParagraph(segments, listOf("git-diff-$kind"))
        }
        if (data.isEmpty()) builder.addParagraph("", emptyList(), emptyList())
        // 文本、行背景和字符高亮一次性替换，避免逐行样式更新反复触发布局。
        area.replace(0, area.length, builder.build())
        area.undoManager.forgetHistory()
        area.moveTo(0)
        area.estimatedScrollXProperty().value = 0.0
        area.estimatedScrollYProperty().value = 0.0
    }

    private fun navigate(direction: Int) {
        if (hunks.isEmpty()) return
        hunkIndex = if (hunkIndex < 0) { if (direction > 0) 0 else hunks.lastIndex }
            else (hunkIndex + direction + hunks.size) % hunks.size
        val line = hunks[hunkIndex]
        left.moveTo(line, 0); right.moveTo(line, 0)
        left.showParagraphAtCenter(line); right.showParagraphAtCenter(line)
        summary.text = "${hunkIndex + 1} / ${hunks.size} " + Locales.str("git.hunks")
    }

    companion object {
        @JvmStatic fun show(owner: Stage, directory: File, target: File) { GitDiffWindow(owner, directory, target) }
    }
}

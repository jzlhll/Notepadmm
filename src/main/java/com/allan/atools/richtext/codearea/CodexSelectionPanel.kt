package com.allan.atools.richtext.codearea

import com.allan.atools.pop.impl.StringChooseCreatorImpl
import com.allan.atools.GlobalCfgStores
import com.allan.atools.tools.modulenotepad.session.CodexFileHistory
import com.allan.atools.tools.modulenotepad.session.CodexFileRange
import com.allan.atools.ui.IconfontCreator
import com.allan.atools.utils.Locales
import com.allan.atools.utils.ResLocation
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.css.PseudoClass
import javafx.geometry.Pos
import javafx.geometry.Side
import javafx.scene.control.Button
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.fxmisc.richtext.model.TwoDimensional.Bias.Forward
import org.reactfx.Subscription
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID

/** 跟随当前编辑器选区，并保留已发送请求的原始写回位置。 */
class CodexSelectionPanel {
    private companion object {
        const val ASSISTANT_KEY = "selectionAssistant"
        const val EXCERPT_LENGTH = 12
        const val HISTORY_LOAD_FAILED = "editor.codex.historyLoadFailed"
        val ERROR: PseudoClass = PseudoClass.getPseudoClass("error")
        val STOPPING: PseudoClass = PseudoClass.getPseudoClass("stopping")
        val EDITING: PseudoClass = PseudoClass.getPseudoClass("editing")
    }

    private data class Selection(
        val editor: WeakReference<EditorArea>,
        val file: File?,
        val version: Long,
        val start: Int,
        val end: Int,
        val firstLine: Int,
        val lastLine: Int,
        val firstExcerpt: String,
        val lastExcerpt: String
    ) {
        fun matches(other: Selection): Boolean {
            val area = editor.get() ?: return false
            return area === other.editor.get() && file == other.file && version == other.version &&
                start == other.start && end == other.end
        }
    }

    private data class Context(val target: Selection?, val range: CodexFileRange)
    private data class Session(val threadId: String, val context: Context)
    private data class Status(val key: String, val error: Boolean = false, val detail: String? = null)

    private class FileChat(val assistant: SelectionAssistant, val fileKey: String?) {
        // Codex 沿用原缓存键，OpenCode 另存，保证旧会话可直接恢复。
        val historyKey = fileKey?.let { if (assistant == SelectionAssistant.CODEX) it else "opencode\u0000$it" }
        val history = mutableListOf<CodexFileHistory.Message>()
        var draft = ""
        var session: Session? = null
        var replacement: String? = null
        var turn: Turn? = null
        var status: Status? = null
        var cacheError: String? = null
        var scrollPosition = 1.0
        var loading = fileKey != null
        var clearing = false
    }

    private class Turn(val userText: String, val context: Context) {
        lateinit var worker: Thread
        var stopRequested = false
    }

    private var observedArea: EditorArea? = null
    private var textChanges: Subscription? = null
    private var selectionRefreshPending = false
    private val selectionChanged = InvalidationListener { scheduleSelectionRefresh() }
    private var selected: Selection? = null
    private var assistant = SelectionAssistant.entries.firstOrNull {
        it.name == GlobalCfgStores.user().getString(ASSISTANT_KEY, "")
    } ?: SelectionAssistant.CODEX
    private val chats = mutableMapOf<Pair<SelectionAssistant, String>, FileChat>()
    private var activeChat: FileChat? = null
    private var disposed = false
    private var modelLoader: Thread? = null
    private val options = CodexOptionsButton()

    private val clearHistoryButton = Button(Locales.str("editor.codex.clearHistory")).apply {
        tooltip = Tooltip(Locales.str("editor.codex.resetChat"))
        styleClass.add("codex-quiet-button")
        setOnAction { activeChat?.let(::clearHistory) }
    }
    private val assistantName = Label(assistant.title).apply { styleClass.add("codex-assistant-name") }
    private val settings = Button("⚙").apply {
        styleClass.addAll("codex-quiet-button", "codex-settings")
        accessibleText = Locales.str("editor.codex.chooseAssistant")
        tooltip = Tooltip(accessibleText)
        setOnAction {
            val choices = SelectionAssistant.entries
            StringChooseCreatorImpl(choices.map { it.title }, choices.indexOf(assistant)).createMenu { index ->
                val choice = choices[index]
                if (assistant != choice) {
                    assistant = choice
                    GlobalCfgStores.user().setString(ASSISTANT_KEY, choice.name)
                    assistantName.text = choice.title
                    followSelection(observedArea)
                    refreshModels()
                }
            }.show(this, Side.BOTTOM, 0.0, 0.0)
        }
    }
    private val messages = VBox(12.0).apply { styleClass.add("codex-messages") }
    private val scroll = ScrollPane(messages).apply {
        isFitToWidth = true
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        styleClass.add("codex-scroll")
        visibleProperty().bind(Bindings.isNotEmpty(messages.children))
    }
    private val empty = Label(Locales.str("editor.codex.empty")).apply {
        isWrapText = true
        maxWidth = Double.MAX_VALUE
        styleClass.add("codex-empty")
        visibleProperty().bind(scroll.visibleProperty().not())
    }
    private val replace = Button(Locales.str("editor.codex.replace")).apply {
        styleClass.add("codex-quiet-button")
        setOnAction { applyReplacement() }
    }
    private val reference = Label("", IconfontCreator.createLabel("file", 12, "")).apply {
        isWrapText = true
        minWidth = 0.0
        maxWidth = Region.USE_PREF_SIZE
        styleClass.add("codex-reference")
    }
    private val referenceRow = StackPane(reference).apply {
        alignment = Pos.CENTER_LEFT
        managedProperty().bind(visibleProperty())
    }
    private val instructions = TextArea().apply {
        prefRowCount = 3
        minWidth = 0.0
        isWrapText = true
        styleClass.add("codex-input")
        accessibleText = Locales.str("editor.codex.instructions")
        textProperty().addListener { _, _, text ->
            activeChat?.draft = text
            updateControls()
        }
        addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ENTER && event.isShortcutDown && activeChat?.turn == null) {
                event.consume()
                send()
            }
        }
    }
    private val sendButton = Button().apply {
        styleClass.add("codex-send")
        setOnAction {
            val chat = activeChat ?: return@setOnAction
            val current = chat.turn
            if (current == null) send() else stop(chat, current)
        }
    }
    private val status = Label().apply {
        isWrapText = true
        minWidth = 0.0
        styleClass.add("codex-status")
    }
    private val progress = ProgressIndicator().apply {
        setMinSize(13.0, 13.0)
        setPrefSize(13.0, 13.0)
        setMaxSize(13.0, 13.0)
        styleClass.add("codex-progress")
        managedProperty().bind(visibleProperty())
    }
    private val locate = Hyperlink().apply {
        setOnAction { locateExecutable() }
        styleClass.add("codex-locate")
        managedProperty().bind(visibleProperty())
    }
    private val feedback = VBox(2.0, HBox(6.0, progress, status).apply {
        alignment = Pos.CENTER_LEFT
        HBox.setHgrow(status, Priority.ALWAYS)
    }, locate).apply { managedProperty().bind(visibleProperty()) }
    private val composition = VBox(7.0, referenceRow, instructions, HBox(4.0, options,
        Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }, sendButton).apply {
        alignment = Pos.CENTER_LEFT
        styleClass.add("codex-entry-actions")
    }).apply {
        styleClass.add("codex-entry")
        instructions.focusedProperty().addListener { _, _, focused -> pseudoClassStateChanged(EDITING, focused) }
    }
    val view = VBox(
        HBox(5.0, assistantName, Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }, clearHistoryButton, settings).apply {
            alignment = Pos.CENTER_RIGHT
            styleClass.add("codex-header")
        },
        StackPane(scroll, empty).apply { VBox.setVgrow(this, Priority.ALWAYS) },
        VBox(6.0, feedback, composition).apply { styleClass.add("codex-composer") }
    ).apply {
        minWidth = 0.0
        styleClass.add("codex-sidebar")
    }

    init {
        renderChat()
        refreshSelection()
    }

    private fun executable(tool: SelectionAssistant): File? {
        val configured = File(GlobalCfgStores.user().getString(tool.executableKey, ""))
        return configured.takeIf { it.isAbsolute && it.isFile && it.canExecute() } ?: when (tool) {
            SelectionAssistant.CODEX -> CodexSelectionCli.findExecutable()
            SelectionAssistant.OPENCODE -> OpenCodeSelectionCli.findExecutable()
        }
    }

    fun refreshModels() {
        modelLoader?.interrupt()
        options.isDisable = true
        val tool = assistant
        modelLoader = Thread.startVirtualThread {
            val worker = Thread.currentThread()
            val models = runCatching {
                when (tool) {
                    SelectionAssistant.CODEX -> CodexSelectionCli.readModels(executable(tool))
                    SelectionAssistant.OPENCODE -> OpenCodeSelectionCli.readModels(executable(tool))
                }
            }.getOrDefault(emptyList())
            Platform.runLater {
                if (!disposed && modelLoader === worker) {
                    options.setCatalog(tool, models)
                    options.isDisable = false
                    modelLoader = null
                    updateControls()
                }
            }
        }
        updateControls()
    }

    fun followSelection(area: EditorArea?) {
        if (disposed) return
        val sameEditor = observedArea === area
        if (!sameEditor) {
            observedArea?.selectionProperty()?.removeListener(selectionChanged)
            textChanges?.unsubscribe()
            observedArea = area
            area?.selectionProperty()?.addListener(selectionChanged)
            textChanges = area?.plainTextChanges()?.subscribe { scheduleSelectionRefresh() }
        }
        val next = area?.let {
            val key = it.editor.sourceFile?.let(::fileKey)
            val untitled = "untitled:" + it.editor.documentState.sessionId
            val chat = chats.getOrPut(assistant to (key ?: untitled)) { FileChat(assistant, key).also(::loadHistory) }
            // 未命名文档首次保存后，各助手在下次进入时各自接回输入草稿。
            if (key != null) {
                val previous = chats.remove(assistant to untitled)
                if (previous != null && chat.draft.isEmpty()) chat.draft = previous.draft
            }
            chat
        }
        val previous = activeChat
        if (next !== previous) {
            if (previous != null) previous.scrollPosition = scroll.vvalue
            activeChat = next
            renderChat()
        }
        refreshSelection()
    }

    fun dispose() {
        disposed = true
        modelLoader?.interrupt()
        observedArea?.selectionProperty()?.removeListener(selectionChanged)
        textChanges?.unsubscribe()
        observedArea = null
        textChanges = null
        chats.values.forEach {
            it.turn?.worker?.interrupt()
            it.turn = null
        }
        chats.clear()
        activeChat = null
    }

    private fun fileKey(file: File): String {
        val path = file.toPath().toAbsolutePath().normalize().toString()
        return if (ResLocation.isWindow) path.lowercase(Locale.ROOT) else path
    }

    private fun loadHistory(chat: FileChat) {
        val key = chat.historyKey ?: return
        CodexFileHistory.load(key) { result ->
            if (disposed) return@load
            chat.loading = false
            result.onSuccess { snapshot ->
                if (snapshot != null) {
                    chat.history.addAll(snapshot.messages)
                    chat.session = snapshot.session?.let { cached ->
                        runCatching {
                            val id = when (chat.assistant) {
                                SelectionAssistant.CODEX -> UUID.fromString(cached.threadId).toString()
                                SelectionAssistant.OPENCODE -> cached.threadId.takeIf(OpenCodeSelectionCli.sessionIdPattern::matches)
                            }
                            if (id != null && fileKey(File(cached.range.filePath)) == chat.fileKey) Session(id, Context(null, cached.range)) else null
                        }.getOrNull()
                    }
                }
            }.onFailure {
                chat.cacheError = HISTORY_LOAD_FAILED
            }
            if (activeChat === chat) renderChat()
        }
    }

    private fun saveHistory(chat: FileChat) {
        val key = chat.historyKey ?: return
        val session = chat.session?.let { CodexFileHistory.Session(it.threadId, it.context.range) }
        CodexFileHistory.save(key, CodexFileHistory.Snapshot(chat.history.toList(), session)) { result ->
            if (disposed || chat.clearing) return@save
            chat.cacheError = if (result.isSuccess) null else "editor.codex.historySaveFailed"
            if (activeChat === chat) renderStatus()
        }
    }

    private fun clearHistory(chat: FileChat) {
        if (chat.loading || chat.clearing) return
        chat.turn?.worker?.interrupt()
        chat.turn = null
        chat.clearing = true
        setStatus(chat, "editor.codex.historyClearing")
        val finished: (Result<Unit>) -> Unit = { result ->
            if (!disposed) {
                chat.clearing = false
                if (result.isSuccess) {
                    chat.history.clear()
                    chat.draft = ""
                    chat.session = null
                    chat.replacement = null
                    chat.status = null
                    chat.cacheError = null
                    chat.scrollPosition = 1.0
                } else {
                    chat.status = Status("editor.codex.historyClearFailed", error = true)
                }
                if (activeChat === chat) renderChat()
            }
        }
        val key = chat.historyKey
        if (key == null) finished(Result.success(Unit)) else CodexFileHistory.clear(key, finished)
    }

    private fun renderChat() {
        val chat = activeChat
        (replace.parent as? VBox)?.children?.remove(replace)
        messages.children.clear()
        if (chat != null) {
            val replyIndex = if (chat.replacement == null) -1 else chat.history.indexOfLast { !it.fromUser }
            chat.history.forEachIndexed { index, message -> renderMessage(message, index == replyIndex) }
        }
        instructions.text = chat?.draft.orEmpty()
        instructions.promptText = Locales.str(if (chat?.session == null) "editor.codex.prompt" else "editor.codex.followUp")
        renderStatus()
        restoreScroll(chat)
    }

    private fun scheduleSelectionRefresh() {
        if (selectionRefreshPending) return
        selectionRefreshPending = true
        Platform.runLater {
            selectionRefreshPending = false
            if (!disposed) refreshSelection()
        }
    }

    private fun refreshSelection() {
        selected = observedArea?.let(::captureSelection)
        val current = selected
        referenceRow.isVisible = current != null
        if (current != null) {
            reference.text = String.format(Locales.str("editor.codex.reference"), current.firstLine, current.lastLine,
                displayExcerpt(current.firstExcerpt), displayExcerpt(current.lastExcerpt))
            reference.tooltip = Tooltip(listOfNotNull(current.file?.absolutePath, reference.text,
                Locales.str(if (assistant == SelectionAssistant.CODEX) "editor.codex.data" else "editor.opencode.data")).joinToString("\n"))
        } else {
            reference.text = ""
            reference.tooltip = null
        }
        updateControls()
    }

    private fun captureSelection(area: EditorArea): Selection? {
        val editor = area.editor
        val selection = area.selection
        if (editor.isDestroyed() || area.isDisabled || area.markdownComposing || selection.length == 0 ||
            selection.end > area.length) return null
        return captureRange(area, selection.start, selection.end)
    }

    private fun captureRange(area: EditorArea, start: Int, end: Int): Selection {
        // 只取边界附近的 UTF-16 单元，避免拖选时复制整个选区。
        val excerptUnits = Math.min(end - start, EXCERPT_LENGTH * 2)
        val head = area.getText(start, start + excerptUnits)
        val tail = area.getText(end - excerptUnits, end)
        val headCount = Math.min(EXCERPT_LENGTH, Character.codePointCount(head, 0, head.length))
        val tailCount = Math.min(EXCERPT_LENGTH, Character.codePointCount(tail, 0, tail.length))
        return Selection(WeakReference(area), area.editor.sourceFile, area.editor.contentVersion, start, end,
            area.offsetToPosition(start, Forward).major + 1,
            area.offsetToPosition(end - 1, Forward).major + 1,
            head.substring(0, Character.offsetByCodePoints(head, 0, headCount)),
            tail.substring(Character.offsetByCodePoints(tail, tail.length, -tailCount)))
    }

    private fun displayExcerpt(text: String): String =
        text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")

    private fun send() {
        followSelection(observedArea)
        val chat = activeChat ?: return
        val userText = instructions.text
        if (chat.turn != null || chat.loading || chat.clearing || chat.cacheError == HISTORY_LOAD_FAILED || userText.isBlank()) return
        if (modelLoader != null) return
        val previous = chat.session
        val currentSelection = selected
        val continued = previous?.takeIf { currentSelection == null || it.context.target?.matches(currentSelection) == true }
        val context = if (continued != null) {
            val saved = continued.context
            val target = saved.target?.takeIf { it.editor.get() === observedArea } ?: restoreTarget(saved.range)
            saved.copy(target = target)
        } else {
            readContext(chat, currentSelection ?: return) ?: return
        }
        val tool = chat.assistant
        val source = if (tool == SelectionAssistant.OPENCODE && continued == null) {
            val target = context.target ?: return
            target.editor.get()?.getText(target.start, target.end) ?: return
        } else null
        val current = Turn(userText, context)
        val requestOptions = options.snapshot()
        chat.turn = current
        instructions.clear()
        appendMessage(chat, CodexFileHistory.Message(userText, true))
        saveHistory(chat)
        setStatus(chat, "editor.codex.connecting")
        current.worker = Thread.startVirtualThread {
            val result = runCatching {
                val onPhase: (CodexSelectionCli.Phase) -> Unit = { phase ->
                    Platform.runLater {
                        if (chat.turn === current && !current.stopRequested) setStatus(chat, phase.messageKey)
                    }
                }
                when (tool) {
                    SelectionAssistant.CODEX -> CodexSelectionCli.rewrite(executable(tool), previous?.threadId, context.range,
                        userText, continued != null, requestOptions, onPhase)
                    SelectionAssistant.OPENCODE -> OpenCodeSelectionCli.rewrite(executable(tool), previous?.threadId,
                        context.range, source, userText, requestOptions.model, onPhase)
                }
            }
            Platform.runLater {
                if (chat.turn === current) finish(chat, current, result)
            }
        }
    }

    private fun readContext(chat: FileChat, target: Selection): Context? {
        val area = target.editor.get() ?: return null
        val editor = area.editor
        val file = target.file
        val state = editor.documentState
        if (file == null || state.isDirty) {
            setStatus(chat, "editor.codex.saveFirst", error = true)
            return null
        }
        if (state.externalState != EditorDocumentState.ExternalState.UNCHANGED) {
            setStatus(chat, "editor.codex.diskChanged", error = true)
            return null
        }
        val from = area.offsetToPosition(target.start, Forward)
        val to = area.offsetToPosition(target.end, Forward)
        val encoding = editor.getState().fileEncoding ?: state.encoding ?: Charsets.UTF_8.name()
        val range = CodexFileRange(file.absolutePath, encoding, target.firstLine, target.lastLine, from.minor + 1,
            to.major + 1, to.minor + 1, target.firstExcerpt, target.lastExcerpt, state.baseLastModified, state.baseFileSize)
        return Context(target, range)
    }

    private fun restoreTarget(range: CodexFileRange): Selection? {
        val area = observedArea ?: return null
        val editor = area.editor
        val file = editor.sourceFile ?: return null
        val state = editor.documentState
        if (editor.isDestroyed() || state.isDirty || state.externalState != EditorDocumentState.ExternalState.UNCHANGED
            || state.baseLastModified != range.lastModified || state.baseFileSize != range.size
            || fileKey(file) != fileKey(File(range.filePath))) return null
        val first = range.startLine - 1
        val last = range.endLine - 1
        val firstColumn = range.startColumn - 1
        val lastColumn = range.endColumn - 1
        if (first !in area.paragraphs.indices || last !in area.paragraphs.indices
            || firstColumn !in 0..area.getParagraph(first).length() || lastColumn !in 0..area.getParagraph(last).length()) return null
        val start = area.getAbsolutePosition(first, firstColumn)
        val end = area.getAbsolutePosition(last, lastColumn)
        if (start >= end) return null
        return captureRange(area, start, end).takeIf {
            it.firstExcerpt == range.startExcerpt && it.lastExcerpt == range.endExcerpt
        }
    }

    private fun stop(chat: FileChat, current: Turn) {
        if (current.stopRequested) return
        current.stopRequested = true
        current.worker.interrupt()
        setStatus(chat, "editor.codex.stopping")
    }

    private fun finish(chat: FileChat, current: Turn, result: Result<CodexSelectionCli.Reply>) {
        chat.turn = null
        if (current.stopRequested) {
            setStatus(chat, "editor.codex.stopped")
            return
        }
        result.fold(onSuccess = { reply ->
            chat.session = Session(reply.threadId, current.context)
            chat.replacement = if (current.context.target == null) null else reply.replacement
            appendMessage(chat, CodexFileHistory.Message(reply.replacement, false))
            saveHistory(chat)
            if (activeChat === chat) instructions.promptText = Locales.str("editor.codex.followUp")
            if (current.context.target == null) setStatus(chat, "editor.codex.selectionChanged") else setStatus(chat)
        }, onFailure = {
            if (chat.draft.isBlank()) {
                chat.draft = current.userText
                if (activeChat === chat) instructions.text = chat.draft
            }
            val failure = it as? CodexSelectionCli.Failure
            setStatus(chat, failure?.messageKey ?: "editor.codex.failed", error = true, detail = it.message)
        })
    }

    private fun appendMessage(chat: FileChat, message: CodexFileHistory.Message) {
        chat.history.add(message)
        chat.scrollPosition = 1.0
        if (activeChat === chat) {
            renderMessage(message, !message.fromUser && chat.replacement != null)
            restoreScroll(chat)
        }
    }

    private fun renderMessage(entry: CodexFileHistory.Message, canApply: Boolean) {
        val (text, fromUser) = entry
        val label = Label(if (!fromUser && text.isEmpty()) Locales.str("editor.codex.emptyReplacement") else text).apply {
            isWrapText = true
            minWidth = 0.0
            styleClass.add(if (fromUser) "codex-user-message" else "codex-reply")
        }
        val message = VBox(5.0, label).apply {
            if (fromUser) {
                alignment = Pos.CENTER_RIGHT
                isFillWidth = false
                label.maxWidthProperty().bind(messages.widthProperty().subtract(35.0))
            } else {
                label.maxWidth = Double.MAX_VALUE
                (replace.parent as? VBox)?.children?.remove(replace)
                if (canApply) children.add(replace)
            }
        }
        messages.children.add(message)
    }

    private fun restoreScroll(chat: FileChat?) {
        Platform.runLater {
            if (activeChat === chat) scroll.vvalue = chat?.scrollPosition ?: 1.0
        }
    }

    private fun setStatus(chat: FileChat, key: String? = null, error: Boolean = false, detail: String? = null) {
        chat.status = key?.let { Status(it, error, detail) }
        if (activeChat === chat) renderStatus()
    }

    private fun renderStatus() {
        val chat = activeChat
        val current = chat?.status ?: chat?.cacheError?.let { Status(it, error = true) }
        val key = current?.key
        status.text = key?.let { String.format(Locales.str(it), assistant.title) }.orEmpty()
        locate.text = String.format(Locales.str("editor.codex.locate"), assistant.title)
        status.pseudoClassStateChanged(ERROR, current?.error == true)
        status.tooltip = current?.detail?.let(::Tooltip)
        locate.isVisible = current?.error == true && (key == "editor.codex.notFound" || key == "editor.codex.failed")
        feedback.isVisible = key != null
        updateControls()
    }

    private fun updateControls() {
        val chat = activeChat
        val current = chat?.turn
        val waiting = chat == null || chat.loading || chat.clearing
        sendButton.text = Locales.str(if (current == null) "editor.codex.send" else "editor.codex.stop")
        sendButton.pseudoClassStateChanged(STOPPING, current != null)
        sendButton.isDisable = waiting || chat?.cacheError == HISTORY_LOAD_FAILED ||
            (current?.stopRequested ?: (modelLoader != null || instructions.text.isBlank() || (selected == null && chat?.session == null)))
        replace.isDisable = waiting || current != null || chat?.replacement == null
        instructions.isDisable = chat == null || chat.clearing
        clearHistoryButton.isManaged = chat != null
        clearHistoryButton.isVisible = chat != null
        clearHistoryButton.isDisable = waiting || (chat?.history.isNullOrEmpty() && chat?.draft.isNullOrEmpty() && chat?.cacheError == null)
        progress.isVisible = current != null || chat?.clearing == true
    }

    private fun applyReplacement() {
        val chat = activeChat ?: return
        if (chat.turn != null || chat.clearing) return
        val conversation = chat.session ?: return
        val target = conversation.context.target ?: return
        val text = chat.replacement ?: return
        val area = target.editor.get()
        val editor = area?.editor
        if (area == null || editor == null || editor.isDestroyed() || !area.isEditable || area.isDisabled
            || area.markdownComposing || editor.contentVersion != target.version || editor.sourceFile != target.file
            || target.end > area.length) {
            chat.replacement = null
            setStatus(chat, "editor.codex.selectionChanged", error = true)
            return
        }
        if (editor.documentState.externalState != EditorDocumentState.ExternalState.UNCHANGED) {
            chat.replacement = null
            setStatus(chat, "editor.codex.diskChanged", error = true)
            return
        }
        if (text != area.getText(target.start, target.end)) {
            val lengthBefore = area.length
            area.undoManager.preventMerge()
            area.replaceText(target.start, target.end, text)
            area.undoManager.preventMerge()
            val updated = target.copy(version = editor.contentVersion, end = target.end + area.length - lengthBefore)
            chat.session = conversation.copy(context = conversation.context.copy(target = updated))
        }
        chat.replacement = null
        refreshSelection()
        setStatus(chat, "editor.codex.applied")
    }

    private fun locateExecutable() {
        val executable = FileChooser().apply {
            title = String.format(Locales.str("editor.codex.locate"), assistant.title)
            if (ResLocation.isWindow) extensionFilters.add(FileChooser.ExtensionFilter("${assistant.title} CLI (*.exe)", "*.exe"))
        }.showOpenDialog(view.scene.window) ?: return
        GlobalCfgStores.user().setString(assistant.executableKey, executable.absolutePath)
        send()
        refreshModels()
    }
}

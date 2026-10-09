package com.allan.atools.tools.modulenotepad.log

import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.tools.modulenotepad.session.EditorSessionManager
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.scene.input.ScrollEvent
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

/** 在现有编辑器中按需追加日志，切换标签保留正文和修改，不增加专用界面。 */
class LargeLogController(
    private val area: EditorArea,
    initialState: LogReadState? = null,
    private var restoreBytes: Long = 0,
    private var restoreCaret: Int = 0
) : AutoCloseable {
    private val request = AtomicLong()
    @Volatile private var closed = false
    @Volatile private var source: LogFileSource? = null
    private val sourceLock = Any()
    private var task: Future<*>? = null
    private var state = initialState
    private var active = false
    private var loading = false
    private var saving = false
    private var started = initialState != null
    private var wantedChars = 0
    private val displayLeases = ArrayList<LogMemoryBudget.Lease>()
    private val pendingJumps = ArrayList<Pair<Int, Runnable>>()
    private val scrollChanged = ChangeListener<Number> { _, _, _ -> loadNearEnd() }
    private val scrollEvent = EventHandler<ScrollEvent> { event -> if (event.deltaY < 0) loadNearEnd() }
    var loadingText = false
        private set

    val readState: LogReadState? get() = state

    init {
        area.largeLog = this
        area.editor.enableLargeLog()
        area.estimatedScrollYProperty().addListener(scrollChanged)
        area.addEventFilter(ScrollEvent.SCROLL, scrollEvent)
    }

    private fun loadNearEnd() {
        if (closed || !active || loading || saving || loadingText || !started || area.visibleParagraphs.isEmpty()) return
        if (area.lastVisibleParToAllParIndex() >= area.paragraphs.size - 3) loadMore()
    }

    fun setActive(value: Boolean) {
        if (closed || active == value) return
        active = value
        if (!value) cancelLoading()
        else if (!started) loadMore()
        else if (wantedChars > area.length) loadMore()
    }

    fun ensureLoaded(end: Int, action: Runnable) {
        if (closed) return
        if (area.length >= end) { action.run(); return }
        pendingJumps.add(end to action)
        wantedChars = Math.max(wantedChars, end)
        if (!loading) loadMore()
    }

    fun snapshot(prefix: String): LogDocumentSnapshot {
        val file = area.editor.documentState
        return LogDocumentSnapshot(file.sourceFile.toPath(), file.encoding, state, prefix,
            source?.size ?: file.baseFileSize, source?.lastModified ?: file.baseLastModified, source?.fileKey)
    }

    fun search(params: Array<com.allan.atools.bean.SearchParams>, action: com.allan.baseparty.Action<com.allan.atools.text.beans.OneFileSearchResults>) {
        if (!Platform.isFxApplicationThread()) { Platform.runLater { search(params, action) }; return }
        val snapshot = snapshot(area.text)
        val version = area.editor.contentVersion
        val rules = params.map { it.copy() }.toTypedArray()
        val result = com.allan.atools.text.beans.OneFileSearchResults()
            .addFile(area.editor.sourceFile).addSessionId(area.editor.documentState.sessionId)
            .addDisplayName(area.editor.documentState.displayName).addArea(area.editor).addTotalLen(area.length)
        val numbered = com.allan.atools.SettingPreferences.getBoolean(com.allan.atools.SettingPreferences.searchResultHasNumberKey)
        val work = Runnable {
            try {
                snapshot.openReader { closed || area.editor.isDestroyed || area.editor.contentVersion != version }.use { reader ->
                    result.addResults(com.allan.atools.text.FinderFactory.find(reader, numbered, rules, intArrayOf(0)))
                }
            } catch (_: CancellationException) {
                result.addResults(emptyList())
            } catch (e: Exception) {
                result.addResults(emptyList())
                Log.e("advanced log search failed", e)
                Platform.runLater { if (!closed) SnackbarUtils.show(e.message ?: "搜索失败，请重试") }
            } finally { action.invoke(result) }
        }
        try { LogTasks.scans.execute(work) }
        catch (e: Exception) {
            Log.e("advanced log search rejected", e)
            SnackbarUtils.show(e.message ?: "搜索任务繁忙，请稍后重试")
            action.invoke(result.addResults(emptyList()))
        }
    }

    fun prepareSave(prefix: String): LogDocumentSnapshot {
        cancelLoading()
        saving = true
        return snapshot(prefix)
    }

    fun finishSave(target: Path?, encoding: String?, prefixBytes: Long) {
        saving = false
        if (target != null && encoding != null) {
            synchronized(sourceLock) { source?.close(); source = null }
            state = LogReadState(LogPosition(prefixBytes))
            started = true
        }
        if (active && wantedChars > area.length) loadMore()
    }

    private fun cancelLoading() {
        request.incrementAndGet()
        LogTasks.cancel(task)
        loading = false
    }

    private fun onUi(id: Long, action: () -> Unit) {
        val finished = CompletableFuture<Unit>()
        Platform.runLater {
            try {
                if (closed || !active || id != request.get()) throw CancellationException()
                action()
                finished.complete(Unit)
            } catch (e: Throwable) { finished.completeExceptionally(e) }
        }
        finished.get()
    }

    private fun loadMore() {
        if (closed || !active || loading || saving) return
        val fileSource = source
        val progress = state
        if (fileSource != null && progress != null && progress.position.offset >= fileSource.size) {
            if (pendingJumps.isNotEmpty()) {
                pendingJumps.clear()
                wantedChars = 0
                SnackbarUtils.show("文件内容已变化，请重新搜索")
            }
            return
        }
        loading = true
        val id = request.incrementAndGet()
        val initial = state
        val firstLoad = !started
        val contentVersion = area.editor.contentVersion
        val path = area.editor.sourceFile.toPath()
        val encoding = area.editor.documentState.encoding
        try {
            task = LogTasks.pages.submit {
                try {
                    val cancelled = { closed || request.get() != id || Thread.currentThread().isInterrupted }
                    val file = synchronized(sourceLock) {
                        if (cancelled()) throw CancellationException()
                        source ?: LogFileSource(path, encoding).also { source = it }
                    }
                    file.checkVersion()
                    val begin = initial ?: LogReadState(file.first)
                    val target = Math.min(file.size, Math.max(restoreBytes,
                        if (firstLoad) file.first.offset + LogMemoryBudget.INITIAL_BYTES
                        else begin.position.offset + LogMemoryBudget.READ_BYTES))
                    LogMemoryBudget.reserve(LogMemoryBudget.TASK_RESERVATION).use {
                        LogChunkReader(file, begin).use { reader ->
                            while (reader.state.position.offset < target) {
                                reader.readBatch(target, cancelled) { text, progress ->
                                    val cost = text.length * 6L + text.count { it == '\n' } * 256L
                                    val lease = if (cost > 0) LogMemoryBudget.reserve(cost) else null
                                    val owned = java.util.concurrent.atomic.AtomicBoolean()
                                    try {
                                        onUi(id) {
                                            val anchor = area.anchor
                                            val caret = area.caretPosition
                                            loadingText = true
                                            try {
                                                if (text.isNotEmpty()) area.replaceText(area.length, area.length, text)
                                                state = progress
                                                area.selectRange(anchor, caret)
                                            } finally { loadingText = false }
                                            lease?.let(displayLeases::add)
                                            owned.set(true)
                                        }
                                    } finally { if (!owned.get()) lease?.close() }
                                }
                            }
                        }
                    }
                    onUi(id) {
                        started = true
                        loading = false
                        restoreBytes = 0
                        if (firstLoad && area.editor.contentVersion == contentVersion) {
                            val caret = Math.min(restoreCaret, area.length)
                            area.moveTo(caret)
                            if (caret > 0) area.requestFollowCaret() else area.showParagraphAtTop(0)
                            restoreCaret = 0
                        }
                        val sourceChanged = area.editor.documentState.baseFileSize != file.size
                            || area.editor.documentState.baseLastModified != file.lastModified
                        area.editor.refreshLargeLogState(sourceChanged)
                        area.bottomSearchBtnsMgr.refreshLoadedContent(sourceChanged)
                        area.editor.documentState.baseFileSize = file.size
                        area.editor.documentState.baseLastModified = file.lastModified
                        EditorSessionManager.getInstance().onCaretOrStructureChanged()
                        val ready = pendingJumps.filter { it.first <= area.length }
                        pendingJumps.removeAll(ready.toSet())
                        ready.forEach { it.second.run() }
                        if (wantedChars > area.length && (state?.position?.offset ?: 0) < file.size) loadMore()
                        else if (pendingJumps.isNotEmpty()) {
                            pendingJumps.clear()
                            wantedChars = 0
                            SnackbarUtils.show("文件内容已变化，请重新搜索")
                        }
                    }
                } catch (_: CancellationException) {
                    // 已提交的正文和解码状态保留，下次激活继续读取。
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    if (request.get() == id) {
                        Log.e("log load failed", e)
                        Platform.runLater {
                            if (!closed && request.get() == id) {
                                started = state != null
                                loading = false
                                pendingJumps.clear()
                                wantedChars = 0
                                SnackbarUtils.show(e.cause?.message ?: e.message ?: "日志读取失败，请重试")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            loading = false
            SnackbarUtils.show(e.message ?: "日志读取失败，请重试")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        area.estimatedScrollYProperty().removeListener(scrollChanged)
        area.removeEventFilter(ScrollEvent.SCROLL, scrollEvent)
        cancelLoading()
        pendingJumps.clear()
        synchronized(sourceLock) { source?.close(); source = null }
        displayLeases.forEach { it.close() }
        displayLeases.clear()
    }
}

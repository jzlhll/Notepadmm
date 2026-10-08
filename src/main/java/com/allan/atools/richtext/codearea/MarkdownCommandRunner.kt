package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.event.Event
import javafx.event.EventDispatcher
import javafx.scene.Node
import javafx.scene.input.InputMethodEvent
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import java.util.concurrent.Future

/** 结构命令在后台准备当前版本的 AST，等待期间按顺序保留编辑输入。 */
class MarkdownCommandRunner(private val area: EditorArea) {
    private val originalDispatcher = area.eventDispatcher
    private val deferred = ArrayDeque<() -> Unit>()
    private var pending = false
    private var disposed = false
    private var task: Future<*>? = null
    private var ready: MarkdownStructureSnapshot? = null
    private var readyVersion = -1L
    var nativeKey = false
        private set
    private val dispatcher = EventDispatcher { event, tail ->
        val editingInput = event is KeyEvent || event is InputMethodEvent || event is MouseEvent &&
            event.eventType in setOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_CLICKED, MouseEvent.MOUSE_DRAGGED)
        if (pending && editingInput) {
            val copy = event.copyFor(event.source, event.target)
            deferred.add {
                val target = (copy.target as? Node)?.takeIf { it.scene === area.scene } ?: area
                Event.fireEvent(target, copy)
            }
            event.consume()
            null
        } else originalDispatcher.dispatchEvent(event, tail)
    }

    init { area.eventDispatcher = dispatcher }

    fun current(): MarkdownStructureSnapshot? {
        if (readyVersion == area.editor.contentVersion) return ready
        return (area.editor as EditorAreaMgrCode).currentMarkdownSnapshot()
    }

    fun snapshot(): MarkdownStructureSnapshot = checkNotNull(current())

    fun defer(action: () -> Unit): Boolean {
        if (current() != null && !pending) return false
        run(action)
        return true
    }

    fun deferKey(event: KeyEvent): Boolean {
        if (current() != null && !pending) return false
        val copy = event.copyFor(area, area)
        submit({ Event.fireEvent(area, copy) }, {
            // 解析失败或文档被外部替换时，普通按键仍交给原生编辑行为，不能丢失输入。
            nativeKey = true
            try { Event.fireEvent(area, copy) } finally { nativeKey = false }
        })
        return true
    }

    fun run(action: () -> Unit) = submit(action, null)

    private fun submit(action: () -> Unit, fallback: (() -> Unit)?) {
        if (disposed || area.editor.isDestroyed) return
        if (pending) { deferred.add { submit(action, fallback) }; return }
        if (current() != null) { action(); return }
        if (area.markdownComposing || area.editor.isRealtimeProcessingLimitReached) { fallback?.invoke(); return }
        val version = area.editor.contentVersion
        val anchor = area.anchor
        val caret = area.caretPosition
        val file = area.editor.sourceFile
        val document = area.content.snapshot()
        val manager = area.editor as EditorAreaMgrCode
        pending = true
        task = ThreadUtils.submit {
            val result = try { manager.markdownSnapshot(document.text) }
            catch (error: Exception) {
                if (!Thread.currentThread().isInterrupted) Log.e("Prepare markdown command failed", error)
                null
            }
            Platform.runLater {
                if (disposed || area.editor.isDestroyed) return@runLater
                task = null
                pending = false
                try {
                    if (result != null && version == area.editor.contentVersion && anchor == area.anchor &&
                        caret == area.caretPosition && file == area.editor.sourceFile && !area.markdownComposing && !area.editor.isRealtimeProcessingLimitReached) {
                        ready = result
                        readyVersion = version
                        action()
                    } else {
                        fallback?.invoke()
                        SnackbarUtils.show(Locales.str("markdown.commandRetry"))
                    }
                } finally {
                    while (!pending && !disposed && deferred.isNotEmpty()) deferred.removeFirst().invoke()
                }
            }
        }
    }

    fun destroy() {
        disposed = true
        task?.cancel(true)
        task = null
        deferred.clear()
        ready = null
        if (area.eventDispatcher === dispatcher) area.eventDispatcher = originalDispatcher
    }
}

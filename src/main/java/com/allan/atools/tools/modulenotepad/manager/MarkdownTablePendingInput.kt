package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import javafx.application.Platform
import javafx.event.Event
import javafx.event.EventDispatcher
import javafx.scene.input.InputMethodEvent
import javafx.scene.input.KeyEvent
import java.util.function.BooleanSupplier

/** 结构刷新期间按顺序保留单元格输入，只向恢复后的编辑器回放。 */
class MarkdownTablePendingInput(
    private val editor: MarkdownTableCellEditor,
    private val pending: BooleanSupplier,
    private val ready: BooleanSupplier
) {
    private val events = ArrayDeque<Event>()
    private var characters = 0
    private var scheduled = false
    private var replaying = false
    private var generation = 0L
    private val original = editor.eventDispatcher

    init {
        editor.eventDispatcher = EventDispatcher { event, tail ->
            if (!replaying && (pending.asBoolean || events.isNotEmpty() || scheduled) &&
                (event is KeyEvent || event is InputMethodEvent)) {
                val size = inputLength(event)
                if (events.size < 512 && characters + size <= 65_536) {
                    events.addLast(event.copyFor(editor, editor))
                    characters += size
                    resume()
                } else SnackbarUtils.show(Locales.str("markdown.commandRetry"))
                event.consume()
                null
            } else original.dispatchEvent(event, tail)
        }
    }

    fun resume() {
        if (scheduled || events.isEmpty() || !ready.asBoolean) return
        scheduled = true
        val token = generation
        Platform.runLater {
            if (token != generation) return@runLater
            scheduled = false
            if (!ready.asBoolean || events.isEmpty()) return@runLater
            val event = events.removeFirst()
            characters -= inputLength(event)
            replaying = true
            try { Event.fireEvent(editor, event) } finally { replaying = false }
            resume()
        }
    }

    fun clear() {
        if (events.isNotEmpty()) SnackbarUtils.show(Locales.str("markdown.commandRetry"))
        events.clear()
        characters = 0
        scheduled = false
        generation++
    }

    private fun inputLength(event: Event): Int = when (event) {
        is KeyEvent -> event.character.length
        is InputMethodEvent -> event.committed.length + event.composed.sumOf { it.text.length }
        else -> 0
    }
}

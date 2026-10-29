package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.UIContext
import com.allan.atools.bean.SearchParams
import com.allan.atools.controller.NotepadFindController
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.MarkdownEditorSupport
import com.allan.atools.text.ReplaceFactory
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.log.LogMemoryBudget
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.value.ChangeListener
import javafx.stage.Stage
import javafx.stage.WindowEvent
import org.reactfx.Subscription
import java.util.concurrent.CancellationException
import java.util.concurrent.FutureTask
import java.util.regex.PatternSyntaxException

/** 搜索弹窗的当前文档替换，后台计算后校验文档和输入状态再回写。 */
class FindReplaceManager(private val controller: NotepadFindController, stage: Stage) {
    private enum class Operation { FIND, REPLACE, ALL }
    private var task: FutureTask<Unit>? = null
    private var area: EditorArea? = null
    private var textChanges: Subscription? = null
    private var selectedMatch: ReplaceFactory.Match? = null
    private var matchVersion = -1L
    private val changed = InvalidationListener { invalidate() }
    private val areaChanged = ChangeListener<EditorArea> { _, _, value -> bind(value) }

    init {
        controller.findTextField.textProperty().addListener(changed)
        controller.replaceTextField.textProperty().addListener(changed)
        controller.caseMatchCheckBox.selectedProperty().addListener(changed)
        controller.allWordsCheckBox.selectedProperty().addListener(changed)
        controller.findMode0NormalBtn.selectedProperty().addListener(changed)
        controller.findMode1RegexBtn.selectedProperty().addListener(changed)
        controller.findMode2FakeRegexBtn.selectedProperty().addListener(changed)
        controller.tabPane.selectionModel.selectedIndexProperty().addListener(changed)
        controller.replaceBtn.setOnAction { execute(Operation.REPLACE) }
        controller.replaceAllBtn.setOnAction { execute(Operation.ALL) }
        controller.replaceTextField.setOnAction { execute(Operation.REPLACE) }
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN) {
            UIContext.currentAreaProp.addListener(areaChanged)
            bind(UIContext.currentAreaProp.get())
        }
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN) {
            UIContext.currentAreaProp.removeListener(areaChanged)
            bind(null)
        }
        refreshState()
    }

    private fun bind(value: EditorArea?) {
        cancel()
        textChanges?.unsubscribe()
        area?.editableProperty()?.removeListener(changed)
        area?.disabledProperty()?.removeListener(changed)
        area = value
        value?.editableProperty()?.addListener(changed)
        value?.disabledProperty()?.addListener(changed)
        textChanges = value?.plainTextChanges()?.subscribe { invalidate() }
        invalidate()
    }

    private fun invalidate() {
        cancel()
        selectedMatch = null
        controller.replaceStatusLabel.text = ""
        refreshState()
    }

    fun cancel() {
        task?.cancel(true)
        task = null
        refreshState()
    }

    fun findNext() = execute(Operation.FIND)

    private fun refreshState() {
        val current = area
        val replaceTab = controller.tabPane.selectionModel.selectedIndex == 2
        val unavailable = current == null || current.editor.isDestroyed() || current.isDisabled ||
            MarkdownEditorSupport.supportsMarkdown(current)
        val busy = task != null
        val empty = controller.findTextField.text.isNullOrEmpty()
        controller.startBtn.isDisable = replaceTab && (unavailable || busy || empty)
        controller.replaceBtn.isDisable = unavailable || busy || empty || current?.isEditable != true
        controller.replaceAllBtn.isDisable = controller.replaceBtn.isDisable
        controller.replaceScopeLabel.text = Locales.str(if (current?.largeLog != null)
            "findWindow.replaceLoadedScope" else "findWindow.replaceScope")
        controller.replaceAllBtn.text = Locales.str(if (current?.largeLog != null)
            "findWindow.replaceLoadedAll" else "allReplace")
        if (replaceTab && current != null && !current.isEditable) {
            controller.replaceStatusLabel.text = Locales.str("findWindow.replaceReadOnly")
        }
    }

    private fun execute(operation: Operation, fromOverride: Int? = null, statusPrefix: String = "") {
        cancel()
        val current = area ?: return
        if (controller.tabPane.selectionModel.selectedIndex != 2 || current.editor.isDestroyed() ||
            current.isDisabled || MarkdownEditorSupport.supportsMarkdown(current)) return
        if (operation != Operation.FIND && !current.isEditable) { refreshState(); return }
        val words = controller.findTextField.text.orEmpty()
        if (words.isEmpty()) return
        val mode = when {
            controller.findMode1RegexBtn.isSelected -> SearchParams.Type.Regex
            controller.findMode2FakeRegexBtn.isSelected -> SearchParams.Type.FakeRegex
            else -> SearchParams.Type.Normal
        }
        val params = SearchParams.generate(words, controller.caseMatchCheckBox.isSelected,
            controller.allWordsCheckBox.isSelected, mode)
        val replacement = controller.replaceTextField.text.orEmpty().replace("\r\n", "\n").replace('\r', '\n')
        val version = current.editor.contentVersion
        val file = current.editor.sourceFile
        val length = current.length
        val selection = ReplaceFactory.Match(current.selection.start, current.selection.end)
        val knownEmptyMatch = selectedMatch == selection && matchVersion == version
        if (operation == Operation.REPLACE && selection.start == selection.end && !knownEmptyMatch) {
            execute(Operation.FIND)
            return
        }
        val from = fromOverride ?: (current.caretPosition + if (knownEmptyMatch && selection.start == selection.end) 1 else 0)
        val snapshot = current.content.snapshot()
        lateinit var work: FutureTask<Unit>
        work = FutureTask {
            try {
                LogMemoryBudget.reserve(length * 12L + LogMemoryBudget.TASK_RESERVATION).use {
                    val source = snapshot.text
                    val match = if (operation == Operation.FIND) ReplaceFactory.findNext(source, params, from) else null
                    val limit = Math.min(Int.MAX_VALUE.toLong(), length * 2L + 1024 * 1024).toInt()
                    val change = if (operation == Operation.FIND) null else ReplaceFactory.replace(source, params,
                        replacement, if (operation == Operation.REPLACE) selection else null, limit)
                    if (Thread.currentThread().isInterrupted) throw CancellationException()
                    Platform.runLater {
                        if (task !== work) return@runLater
                        task = null
                        refreshState()
                        if (current !== area || current.editor.isDestroyed() || current.isDisabled ||
                            current.editor.contentVersion != version || current.length != length ||
                            current.editor.sourceFile != file || current.selection.start != selection.start ||
                            current.selection.end != selection.end) {
                            controller.replaceStatusLabel.text = Locales.str("findWindow.replaceChanged")
                            return@runLater
                        }
                        if (operation != Operation.FIND && !current.isEditable) { refreshState(); return@runLater }
                        controller.saveOneWords(words)
                        if (operation == Operation.FIND) {
                            selectedMatch = match
                            matchVersion = version
                            if (match != null) {
                                current.selectRange(match.start, match.end)
                                current.requestFollowCaret()
                            }
                            controller.replaceStatusLabel.text = statusPrefix + Locales.str(
                                if (match == null) "findWindow.replaceNoMatch" else "findWindow.replaceFound")
                        } else if (change == null) {
                            if (operation == Operation.REPLACE) execute(Operation.FIND)
                            else controller.replaceStatusLabel.text = Locales.str("findWindow.replaceNoMatch")
                        } else {
                            current.undoManager.preventMerge()
                            try {
                                current.replaceText(change.start, change.end, change.text)
                                current.selectRange(change.start + change.text.length, change.start + change.text.length)
                            } finally {
                                current.undoManager.preventMerge()
                            }
                            current.requestFollowCaret()
                            val message = Locales.str("findWindow.replaceCount").replace("%d", change.count.toString())
                            controller.replaceStatusLabel.text = message
                            if (operation == Operation.REPLACE) {
                                val next = change.start + change.text.length + if (change.start == change.end) 1 else 0
                                execute(Operation.FIND, next, "$message ")
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                // 参数、文档或窗口已变化，丢弃本轮结果。
            } catch (error: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e("Prepare find replacement failed", error)
                    Platform.runLater {
                        if (task !== work) return@runLater
                        task = null
                        refreshState()
                        controller.replaceStatusLabel.text = when {
                            error is PatternSyntaxException -> Locales.str("findWindow.replaceInvalidPattern") + error.description
                            error.message == "resultTooLarge" -> Locales.str("findWindow.replaceTooLarge")
                            else -> Locales.str("findWindow.replaceFailed") + error.message.orEmpty()
                        }
                    }
                }
            }
            Unit
        }
        task = work
        controller.replaceStatusLabel.text = Locales.str("findWindow.replaceWorking")
        refreshState()
        try {
            ThreadUtils.execute(work)
        } catch (error: Exception) {
            task = null
            refreshState()
            Log.e("Submit find replacement failed", error)
            controller.replaceStatusLabel.text = Locales.str("findWindow.replaceFailed") + error.message.orEmpty()
        }
    }
}

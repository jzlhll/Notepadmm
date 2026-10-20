package com.allan.atools.richtext.codearea

import com.allan.atools.pop.AppContextMenu
import com.allan.atools.pop.impl.TabTitleCreatorImpl
import com.allan.atools.text.IEditorAreaState
import com.allan.atools.ui.JfoenixDialogUtils
import com.allan.atools.bean.SearchParams
import com.allan.atools.beans.ReplaceParams
import com.allan.atools.text.FinderFactory
import com.allan.atools.text.IEditorAreaEx
import com.allan.atools.text.beans.OneFileSearchResults
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.GlobalCfgStores
import com.allan.atools.SettingPreferences
import com.allan.atools.SettingPreferences.editorLineNumberVisibleKey
import com.allan.atools.tools.modulenotepad.Highlight
import com.allan.atools.tools.modulejson.JsonFormatLog
import com.allan.atools.UIContext
import com.allan.atools.tools.modulenotepad.base.ITextFindAndReplace
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager
import com.allan.atools.tools.modulenotepad.manager.TyporaManager
import com.allan.atools.tools.modulenotepad.session.EditorSessionManager
import com.allan.atools.tools.modulenotepad.session.SaveResult
import com.allan.atools.tools.modulenotepad.session.SessionCommitResult
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.*
import com.allan.baseparty.*
import com.allan.uilibs.richtexts.MyLineNumFactory
import com.allan.baseparty.handler.TextUtils
import com.allan.baseparty.memory.RefWatcher
import com.jfoenix.skins.JFXTabPaneSkin
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.event.ActionEvent
import javafx.scene.control.ContextMenu
import javafx.scene.control.IndexRange
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.control.Tab
import javafx.scene.control.Tooltip
import javafx.stage.FileChooser
import org.fxmisc.richtext.GenericStyledArea
import org.fxmisc.richtext.model.TwoDimensional.Bias.Backward
import org.fxmisc.richtext.model.TwoDimensional.Bias.Forward
import org.reactfx.Subscription
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** 管理编辑器文档状态、标签交互、文件保存及内容变化通知。 */
@JvmSuppressWildcards
open class EditorAreaMgr(
    initialArea: EditorArea, sourceFile: File?, initialTab: Tab?, restoredState: EditorDocumentState?
) : IEditorAreaEx<@JvmSuppressWildcards Collection<String>, String, @JvmSuppressWildcards Collection<String>>, ITextFindAndReplace {
    companion object {
        const val TAG = "Editor"
        private val SAVE_EXECUTOR = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "editor-save-io").apply { isDaemon = true }
        }
    }

    val documentState: EditorDocumentState = restoredState ?: if (sourceFile == null)
        EditorDocumentState.untitled("New", null, StandardCharsets.UTF_8.name())
        else EditorDocumentState.named(sourceFile, StandardCharsets.UTF_8.name())
    private val editorState: EditorAreaState
    private var areaReference: EditorArea? = null
    private val area: EditorArea get() = areaReference!!
    override fun getArea(): GenericStyledArea<Collection<String>, String, Collection<String>>? = areaReference
    override fun getState(): IEditorAreaState = editorState
    override fun isEditorCodeMode(): Boolean = false

    open inner class TextChanged : BaseChanged<Action0>() {
        private var textChangedSubscription: Subscription? = null

        private fun onTextChanged() {
            if (area.largeLog?.loadingText == true) return
            contentVersionCounter.incrementAndGet()
            mContentSizeReachedStyleLimit = area.length >= processingLimits.maxSize
            mLineCountReachedStyleLimit = area.paragraphs.size >= processingLimits.maxLines
            disableStylerIfNeeded(null)
            if (EditorArea.DEBUG_EDITOR) Log.v("text changed !!")
            mActions?.forEach { it.invoke() }
            updateDirtyFromContent(false)
            if (area.largeLog != null) {
                // 分块日志没有完整正文基线，等编辑或撤销入栈完毕后再读取保存标记。
                Platform.runLater { if (!isDestroyed()) updateDirtyFromContent(true) }
            }
        }
        override fun modifiedActions() {}
        override fun destroy() {
            mActions?.clear()
            mActions = null
            textChangedSubscription?.unsubscribe()
            textChangedSubscription = null
        }
        override fun init() {
            textChangedSubscription = area.plainTextChanges().subscribe { onTextChanged() }
        }
    }

    open inner class VisibleParagraphChanged : BaseChanged<Action0>() {
        private var lastFirstVisibleParagraph = -1
        private var lastLastVisibleParagraph = -1
        private val yChanged = ChangeListener<Double> { _, _, _ ->
            val first = area.firstVisibleParToAllParIndex()
            val last = area.lastVisibleParToAllParIndex()
            if (lastFirstVisibleParagraph != first || lastLastVisibleParagraph != last) {
                if (EditorArea.DEBUG_EDITOR) Log.v("area: visible paragraphs changed $first-$last")
                lastFirstVisibleParagraph = first
                lastLastVisibleParagraph = last
                mActions?.forEach { it.invoke() }
            }
        }
        override fun modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.estimatedScrollYProperty().addListener(yChanged)
                    isSet = true
                }
            } else if (isSet) {
                area.estimatedScrollYProperty().removeListener(yChanged)
                isSet = false
                lastFirstVisibleParagraph = -1
                lastLastVisibleParagraph = -1
            }
        }
        override fun destroy() {
            mActions?.clear()
            mActions = null
            if (isSet) {
                area.estimatedScrollYProperty().removeListener(yChanged)
                isSet = false
            }
        }
        override fun init() {}
    }

    open inner class SelectionChanged : BaseChanged<Action<String?>>() {
        private var notificationPending = false
        private val selectionChanged = ChangeListener<IndexRange> { _, _, _ ->
            if (mActions != null && !notificationPending) {
                notificationPending = true
                // 输入法替换文本时，等当前事件结束后读取最新选区。
                Platform.runLater {
                    notificationPending = false
                    if (isDestroyed() || !isSet || mActions == null) return@runLater
                    val selection = area.selection
                    if (EditorArea.DEBUG_EDITOR) Log.v("selection changed " + selection.length)
                    val text = if (selection.length == 0) null else area.selectedText
                    mActions.forEach { it.invoke(text) }
                }
            }
        }
        override fun modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.selectionProperty().addListener(selectionChanged)
                    isSet = true
                }
            } else if (isSet) {
                area.selectionProperty().removeListener(selectionChanged)
                isSet = false
            }
        }
        override fun destroy() {
            mActions.clear()
            mActions = null
            if (isSet) {
                area.selectionProperty().removeListener(selectionChanged)
                isSet = false
            }
        }
        override fun init() {}
    }

    open inner class CaretPosChanged : BaseChanged<Action5<Int, Int, Int, Int, Int>>() {
        private val listener = ChangeListener<Int> { _, _, newValue ->
            if (EditorArea.DEBUG_EDITOR) Log.v("caret pos changed $newValue")
            val selection = area.selection
            val start = area.offsetToPosition(selection.start, Forward)
            val end = area.offsetToPosition(selection.end, Backward)
            mActions?.forEach {
                it.invoke(area.caretPosition, start.major, start.minor, selection.length, end.major - start.major + 1)
            }
        }
        override fun modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.caretPositionProperty().addListener(listener)
                    isSet = true
                }
            } else if (isSet) {
                area.caretPositionProperty().removeListener(listener)
                isSet = false
            }
        }
        override fun destroy() {
            mActions.clear()
            mActions = null
            if (isSet) {
                area.caretPositionProperty().removeListener(listener)
                isSet = false
            }
        }
        override fun init() {}
    }

    @JvmField val textChanged: BaseChanged<Action0> = TextChanged()
    @JvmField val visibleParagraphChanged: BaseChanged<Action0> = VisibleParagraphChanged()
    @JvmField val selectionChanged: BaseChanged<Action<String?>> = SelectionChanged()
    @JvmField val caretPosChanged: BaseChanged<Action5<Int, Int, Int, Int, Int>> = CaretPosChanged()
    private val editorFocus = EditorBaseFocus(this)
    val sourceFile: File? get() = documentState.sourceFile
    var tab: Tab? = null
        private set
    private val activeTab: Tab get() = tab!!
    private val contentVersionCounter = AtomicLong()
    val contentVersion: Long get() = contentVersionCounter.get()
    private val saveLock = Any()
    private var saveChain: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
    @Volatile private var pendingSave = CompletableFuture.completedFuture(SaveResult.SUCCESS_CLEAN)
    private var programmaticReplace = false
    @Volatile private lateinit var processingLimits: EditorProcessingLimits.Limits
    @Volatile private var mSourceFileSizeReachedStyleLimit = false
    @Volatile private var mContentSizeReachedStyleLimit = false
    @Volatile private var mLineCountReachedStyleLimit = false
    private val mStylerWasAllowed = AtomicBoolean()
    private var tabLabel: Label? = null
    private var tabHeaderSubscription = javafx.util.Subscription.EMPTY
    private val lineNumberChanged = ChangeListener<Boolean> { _, _, value -> updateLineNumberVisible(value) }
    private enum class ConfirmStat { Normal, DialogOpened }
    private var mConfirmStat = ConfirmStat.Normal

    init {
        val file = documentState.sourceFile
        refreshProcessingLimits(file, initialArea)
        editorState = EditorAreaState(initialArea, documentState)
        editorState.setFileEncoding(documentState.encoding)
        if (file != null) {
            UIContext.allOpenedFileList.add(file)
            Log.w("new EditorBase:: " + file.lastModified())
        }
        tab = initialTab
        activeTab.userData = documentState
        updateTabTitle()
        activeTab.setOnCloseRequest { it.consume(); requestClose() }
        areaReference = initialArea
        initArea()
        if (documentState.isDirty) documentState.invalidateSavedUndoPosition() else establishUndoBaseline()
        markCurrentFileTs()
        RefWatcher.watchs(this, file?.path ?: documentState.displayName)
    }

    /** 文件块不进入撤销历史，用户编辑仍按原有文档状态处理。 */
    open fun enableLargeLog() {
        val changes = area.multiPlainChanges().filter { area.largeLog?.loadingText != true }
        area.undoManager = org.fxmisc.undo.UndoManagerFactory.unlimitedHistoryMultiChangeUM(
            changes, org.fxmisc.richtext.model.PlainTextChange::invert,
            org.fxmisc.richtext.util.UndoUtils.applyMultiPlainTextChange(area),
            org.fxmisc.richtext.model.PlainTextChange::mergeWith,
            org.fxmisc.richtext.model.PlainTextChange::isIdentity,
            org.fxmisc.richtext.util.UndoUtils.DEFAULT_PREVENT_MERGE_DELAY)
        if (!documentState.isDirty) {
            val position = area.undoManager.currentPosition
            position.mark()
            documentState.setSavedUndoPosition(position)
        } else documentState.invalidateSavedUndoPosition()
        documentState.savedText = null
        disableStylerIfNeeded(null)
    }

    open fun refreshLargeLogState(sourceChanged: Boolean) {
        refreshProcessingLimits(sourceFile, area)
        disableStylerIfNeeded(null)
        if (sourceChanged) contentVersionCounter.incrementAndGet()
    }

    open val isRealtimeProcessingLimitReached: Boolean
        get() = areaReference?.largeLog != null || mSourceFileSizeReachedStyleLimit
            || mContentSizeReachedStyleLimit || mLineCountReachedStyleLimit

    /** 打开、重载或变更文件类型时同步全部阈值，避免沿用上一个文件类型的降级状态。 */
    private fun refreshProcessingLimits(sourceFile: File?, targetArea: EditorArea) {
        processingLimits = EditorProcessingLimits.forName(sourceFile?.name ?: documentState.displayName)
        mSourceFileSizeReachedStyleLimit = sourceFile != null && sourceFile.length() >= processingLimits.maxSize
        mContentSizeReachedStyleLimit = targetArea.length >= processingLimits.maxSize
        mLineCountReachedStyleLimit = targetArea.paragraphs.size >= processingLimits.maxLines
    }

    /** 统一限制语法高亮和 Markdown 目录等全文实时处理。 */
    open fun disableStylerIfNeeded(endAction: Action0?): Boolean {
        if (!isRealtimeProcessingLimitReached) {
            mStylerWasAllowed.set(true)
            return false
        }
        val shouldClearStyle = mStylerWasAllowed.getAndSet(false)
        val disableAction = Runnable {
            if (shouldClearStyle && isRealtimeProcessingLimitReached && areaReference != null && area.length > 0) {
                area.setStyle(0, area.length, area.initialTextStyle)
                area.markdownPresentation.clear()
            }
            endAction?.invoke()
        }
        if (Platform.isFxApplicationThread()) disableAction.run()
        else if (shouldClearStyle || endAction != null) Platform.runLater(disableAction)
        return true
    }

    private fun updateLineNumberVisible(visible: Boolean) {
        areaReference?.paragraphGraphicFactory = if (visible) areaReference?.let { MyLineNumFactory.get(it) } else null
    }

    override fun destroy() {
        SettingPreferences.getBoolProp(editorLineNumberVisibleKey).removeListener(lineNumberChanged)
        tabHeaderSubscription.unsubscribe()
        tabHeaderSubscription = javafx.util.Subscription.EMPTY
        tab?.contextMenu?.hide()
        tab?.contextMenu = null
        tabLabel?.tooltip = null
        tabLabel = null
        textChanged.destroy()
        visibleParagraphChanged.destroy()
        selectionChanged.destroy()
        caretPosChanged.destroy()
        editorFocus.removeFocusChanged()
        areaReference?.let { EditorSessionManager.getInstance().onDestroyed(it) }
        sourceFile?.let { UIContext.allOpenedFileList.remove(it) }
        tab = null
        areaReference = null
    }

    override fun isDestroyed(): Boolean = areaReference == null || tab == null
    open fun closeTab() { requestClose() }

    open fun requestClose() {
        if (mConfirmStat != ConfirmStat.Normal || isDestroyed()) return
        mConfirmStat = ConfirmStat.DialogOpened
        if (!documentState.isDirty) {
            closeAfterCommit()
            return
        }
        JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("ifUwantCloseIt").replace("%s", documentState.displayName), 0, 0,
            JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, Locales.str("save"), {
                save().whenComplete { saveResult, throwable ->
                    Platform.runLater {
                        if (isDestroyed()) return@runLater
                        if (throwable == null && saveResult == SaveResult.SUCCESS_CLEAN && !documentState.isDirty) {
                            closeAfterCommit()
                        } else {
                            mConfirmStat = ConfirmStat.Normal
                            if (throwable != null) JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("saveFileFailed"))
                        }
                    }
                }
            }),
            JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Extra, Locales.str("notSave"), ::closeAfterCommit),
            JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, { mConfirmStat = ConfirmStat.Normal }))
    }

    /** 会话提交成功后关闭标签；失败则恢复确认状态并提示。 */
    private fun closeAfterCommit() {
        if (isDestroyed()) return
        val wasEditable = area.isEditable
        val wasDisabled = area.isDisable
        val closingVersion = contentVersionCounter.get()
        area.isEditable = false
        area.isDisable = true
        EditorSessionManager.getInstance().untrackAndCommit(area).whenComplete { commitResult, throwable ->
            Platform.runLater {
                if (isDestroyed()) return@runLater
                val committed = throwable == null && commitResult == SessionCommitResult.SUCCESS
                if (committed && closingVersion == contentVersionCounter.get()) {
                    closeWithoutConfirm()
                } else {
                    if (committed) {
                        // 后台命令仍可能改变正文；重新纳入会话，保留关闭期间产生的编辑。
                        EditorSessionManager.getInstance().track(area)
                        updateDirtyFromContent(true)
                    }
                    area.isEditable = wasEditable
                    area.isDisable = wasDisabled
                    mConfirmStat = ConfirmStat.Normal
                    if (!committed) JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("sessionWriteFailed"))
                }
            }
        }
    }

    private fun closeWithoutConfirm() {
        mConfirmStat = ConfirmStat.Normal
        AllEditorsManager.Instance.closeTabImmediately(tab)
    }

    private fun establishUndoBaseline() {
        val position = area.undoManager.currentPosition
        position.mark()
        documentState.setSavedUndoPosition(position)
        documentState.savedText = area.text
        documentState.isDirty = false
        updateTabTitle()
    }

    /** 将会话内容作为一次独立编辑恢复，使首次撤销回到磁盘保存状态。 */
    open fun restoreUnsavedText(text: String) {
        if (area.text == text) return
        val undoManager = area.undoManager
        undoManager.preventMerge()
        area.replaceText(text)
        undoManager.preventMerge()
    }

    private fun updateDirtyFromContent(undoStateReady: Boolean) {
        if (programmaticReplace) return
        val dirty = !isCurrentContentSaved(undoStateReady)
        if (dirty != documentState.isDirty) {
            documentState.isDirty = dirty
            updateTabTitle()
        }
        EditorSessionManager.getInstance().onTextChanged(area, contentVersionCounter.get())
    }

    private fun isCurrentContentSaved(undoStateReady: Boolean): Boolean {
        val savedText = documentState.savedText
        // 普通文档直接比较保存正文；撤销通知中的标记属性可能仍缓存着上一次结果。
        if (undoStateReady && area.largeLog != null && !area.undoManager.isPerformingAction
            && documentState.isSavedUndoPositionValid && area.undoManager.isAtMarkedPosition
        ) return true
        return savedText != null && area.matchesSavedText(savedText)
    }

    private fun updateTabTitle() {
        tab?.text = documentState.displayName + if (documentState.isDirty) " *" else ""
    }

    private fun markCurrentFileTs() {
        val sourceFile = sourceFile
        editorFocus.mLastFileChangedTs = sourceFile?.lastModified() ?: 0L
        editorFocus.mLastFileSize = sourceFile?.length() ?: 0L
    }

    open fun save(): CompletionStage<SaveResult> {
        if (isDestroyed()) return CompletableFuture.completedFuture(SaveResult.FAILED)
        if (!documentState.isDirty && !documentState.isUntitled
            && documentState.externalState == EditorDocumentState.ExternalState.UNCHANGED
        ) return CompletableFuture.completedFuture(SaveResult.SUCCESS_CLEAN)
        val result = CompletableFuture<SaveResult>()
        resolveSaveTarget(false, result)
        pendingSave = result
        return result
    }

    open fun saveContent(event: ActionEvent?, forceSave: Boolean) {
        if (!forceSave) { save(); return }
        val result = CompletableFuture<SaveResult>()
        val sourceFile = sourceFile
        if (sourceFile == null) resolveSaveTarget(false, result) else prepareSave(sourceFile, result)
        pendingSave = result
    }

    private fun resolveSaveTarget(forceSaveAs: Boolean, result: CompletableFuture<SaveResult>) {
        if (isDestroyed()) { result.complete(SaveResult.FAILED); return }
        val target = if (forceSaveAs || documentState.isUntitled) chooseSaveTarget() else sourceFile
        if (target == null) { result.complete(SaveResult.CANCELLED); return }
        val openedArea = AllEditorsManager.Instance.getAreaByFilePath(target)
        if (openedArea != null && openedArea !== area) {
            JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("fileOpenedInOtherTab"))
            result.complete(SaveResult.CANCELLED)
            return
        }
        if (!forceSaveAs && documentState.externalState != EditorDocumentState.ExternalState.UNCHANGED) {
            val acceptText = Locales.str(if (documentState.externalState == EditorDocumentState.ExternalState.DELETED) "reCreateFile" else "overwriteDiskFile")
            JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("fileChangedOnDisk"), 0, 0,
                JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, acceptText, { prepareSave(target, result) }),
                JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Extra, Locales.str("saveAsFile"), { resolveSaveTarget(true, result) }),
                JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, { result.complete(SaveResult.CANCELLED) }))
            return
        }
        prepareSave(target, result)
    }

    private fun chooseSaveTarget(): File? {
        val chooser = FileChooser()
        chooser.title = Locales.str("editor.saveFile")
        chooser.initialFileName = if (documentState.isUntitled) documentState.displayName + ".txt" else documentState.displayName
        firstWritableDirectory()?.let { chooser.initialDirectory = it }
        var selected = chooser.showSaveDialog(UIContext.mainWindow) ?: return null
        val name = selected.name
        val extensionIndex = name.lastIndexOf('.')
        if (extensionIndex <= 0 || extensionIndex == name.length - 1) selected = File(selected.parentFile, "$name.txt")
        return selected.toPath().toAbsolutePath().normalize().toFile()
    }

    private fun firstWritableDirectory(): File? {
        val initial = documentState.initialSaveDirectory
        if (initial != null) {
            val directory = File(initial)
            if (directory.isDirectory && directory.canRead() && directory.canWrite()) return directory
        }
        val last = GlobalCfgStores.user().getString(SettingPreferences.lastSaveDirKey, "")
        if (last.isNotBlank()) {
            val directory = File(last)
            if (directory.isDirectory && directory.canRead() && directory.canWrite()) return directory
        }
        val home = File(System.getProperty("user.home"))
        return if (home.isDirectory && home.canRead() && home.canWrite()) home else null
    }

    private fun prepareSave(target: File, result: CompletableFuture<SaveResult>) {
        if (isDestroyed()) { result.complete(SaveResult.FAILED); return }
        val sourceCode = MarkdownAttachments.rebaseForSaveAs(area, sourceFile, target)
        val encoding = editorState.fileEncoding ?: StandardCharsets.UTF_8.name()
        val charset = try {
            Charset.forName(encoding)
        } catch (e: RuntimeException) {
            JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("encodingInvalid"))
            result.complete(SaveResult.FAILED)
            return
        }
        if (!charset.newEncoder().canEncode(sourceCode)) {
            JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("encodingCannotRepresent"), 0, 0,
                JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, Locales.str("saveWithUtf8"), {
                    editorState.setFileEncoding(StandardCharsets.UTF_8.name())
                    startSave(target, sourceCode, StandardCharsets.UTF_8.name(), result)
                }),
                JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, { result.complete(SaveResult.CANCELLED) }))
            return
        }
        startSave(target, sourceCode, encoding, result)
    }

    private fun startSave(target: File, sourceCode: String, encoding: String, result: CompletableFuture<SaveResult>) {
        if (isDestroyed()) { result.complete(SaveResult.FAILED); return }
        val savingArea = area
        val largeLog = area.largeLog
        val logSnapshot = largeLog?.prepareSave(sourceCode)
        val prefixBytes = AtomicLong()
        val undoManager = area.undoManager
        undoManager.preventMerge()
        val savedPosition = if (area.text == sourceCode) undoManager.currentPosition else null
        val referenceSourceFile = sourceFile
        val rebasedSave = referenceSourceFile != null && referenceSourceFile.parentFile != target.parentFile
            && MarkdownEditorSupport.isMarkdownFile(referenceSourceFile)
        val write: CompletableFuture<Boolean>
        synchronized(saveLock) {
            write = saveChain.handle<Void> { _, _ -> null }.thenApplyAsync({
                if (logSnapshot == null) {
                    writeSourceFile(target, sourceCode, encoding)
                } else {
                    try {
                        prefixBytes.set(logSnapshot.writeTo(target.toPath(), Charset.forName(encoding)))
                        true
                    } catch (e: Exception) {
                        Log.e("save log failed: $target", e)
                        false
                    }
                }
            }, SAVE_EXECUTOR)
            saveChain = write.handle { _, _ -> null }
        }
        write.whenComplete { success, throwable ->
            Platform.runLater {
                if (isDestroyed()) { result.complete(SaveResult.FAILED); return@runLater }
                savingArea.runAfterMarkdownComposition {
                    if (throwable != null || success != true || isDestroyed()) {
                        if (largeLog != null && !isDestroyed()) largeLog.finishSave(null, null, 0L)
                        if (throwable != null) Log.e("save content failed: $target", throwable)
                        if (!isDestroyed()) JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("saveFileFailed"))
                        result.complete(SaveResult.FAILED)
                        return@runAfterMarkdownComposition
                    }
                    if (rebasedSave && referenceSourceFile == sourceFile) {
                        MarkdownAttachments.applyRebaseForSaveAs(area, referenceSourceFile, target)
                    }
                    bindSavedFile(target)
                    largeLog?.finishSave(target.toPath(), encoding, prefixBytes.get())
                    documentState.updateBaseFileMetadata()
                    documentState.externalState = EditorDocumentState.ExternalState.UNCHANGED
                    documentState.savedText = sourceCode
                    if (rebasedSave) {
                        if (sourceCode == area.text) {
                            val currentPosition = undoManager.currentPosition
                            currentPosition.mark()
                            documentState.setSavedUndoPosition(currentPosition)
                        } else documentState.invalidateSavedUndoPosition()
                    } else if (savedPosition != null && savedPosition.isValid) {
                        savedPosition.mark()
                        documentState.setSavedUndoPosition(savedPosition)
                    } else documentState.invalidateSavedUndoPosition()
                    val clean = isCurrentContentSaved(true)
                    documentState.isDirty = !clean
                    updateTabTitle()
                    val saveResult = if (clean) SaveResult.SUCCESS_CLEAN else SaveResult.SUCCESS_DIRTY
                    EditorSessionManager.getInstance().onSaved(area, saveResult)
                    AllEditorsManager.delayToSaveRecentFile(target.absolutePath)
                    notifyWorkspaceRefreshDelayed(target)
                    result.complete(saveResult)
                }
            }
        }
    }

    private fun writeSourceFile(target: File, sourceCode: String, encoding: String): Boolean {
        return try {
            com.allan.atools.tools.modulenotepad.session.EditorFileWriter.write(target.toPath(), sourceCode, Charset.forName(encoding))
            true
        } catch (e: IOException) {
            Log.e("save content failed: " + target.absolutePath, e)
            false
        } catch (e: RuntimeException) {
            Log.e("save content failed: " + target.absolutePath, e)
            false
        }
    }

    private fun bindSavedFile(target: File) {
        val oldFile = sourceFile
        if (oldFile != null) UIContext.allOpenedFileList.remove(oldFile)
        documentState.bindSourceFile(target)
        val styleLimitReached = isRealtimeProcessingLimitReached
        refreshProcessingLimits(target, area)
        val refreshStyles = sourceFile != oldFile || isRealtimeProcessingLimitReached != styleLimitReached
        documentState.displayName = target.name
        editorState.setWrap(editorState.isWrap)
        UIContext.allOpenedFileList.add(target)
        tab?.userData = documentState
        if (refreshStyles && this is EditorAreaMgrCode) bindKeywordHelper(target)
        target.parentFile?.let { GlobalCfgStores.user().setString(SettingPreferences.lastSaveDirKey, it.absolutePath) }
        markCurrentFileTs()
        tabLabel?.tooltip = Tooltip(target.absolutePath)
        if (refreshStyles && UIContext.currentAreaProp.get() === area) UIContext.context().refreshCurrentDocumentInfo()
    }

    private fun initArea() {
        val lineNumberVisibleProperty = SettingPreferences.getBoolProp(editorLineNumberVisibleKey)
        updateLineNumberVisible(lineNumberVisibleProperty.get())
        lineNumberVisibleProperty.addListener(lineNumberChanged)
        textChanged.init()
        visibleParagraphChanged.init()
        caretPosChanged.init()
        selectionChanged.init()
        caretPosChanged.addAction { caretPos, lineNum, colNum, length, lineCount ->
            Log.d("area: caretPos changed!")
            editorState.selectLineCount = lineCount
            val message = if (length > 0) String.format(Locales.str("editor.caretIndicate"), lineNum, colNum, length, lineCount)
                else String.format(Locales.str("editor.caretIndicate.short"), lineNum, colNum)
            editorState.selectedLength = length
            editorState.currentCaretPos = caretPos
            editorState.currentCaretColNum = colNum
            editorState.currentCaretLineNum = lineNum
            UIContext.bottomIndicateProp.set(message)
        }
        area.contextMenu = createMenu()
        // 标签皮肤已在整个标签头处理右键请求，不再只绑定标题文字的鼠标点击。
        val titleMenu = TabTitleCreatorImpl().createMenu { event ->
            val sourceFile = sourceFile
            when (event) {
                TabTitleCreatorImpl.EVENT_MODIFY_NAME -> rename()
                TabTitleCreatorImpl.EVENT_MOVE_TO_FRONT -> TabTitleCreatorImpl.moveToFront(tab)
                TabTitleCreatorImpl.EVENT_CLOSE_OTHERS -> AllEditorsManager.Instance.removeAllOtherTabs(tab)
                TabTitleCreatorImpl.EVENT_OPEN_TO_EXPLORE -> openCurrentFolder(null)
                TabTitleCreatorImpl.EVENT_COPY_FULL_PATH -> {
                    val path = sourceFile?.absolutePath.orEmpty()
                    if (path.isNotEmpty()) {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(path), null)
                        SnackbarUtils.show(Locales.str("fullPathCopied"))
                    }
                }
                TabTitleCreatorImpl.EVENT_PIN_RECENT_FILE -> if (sourceFile != null) {
                    val pinnedNow = AllEditorsManager.togglePinnedRecentFile(sourceFile.absolutePath)
                    SnackbarUtils.show(Locales.str(if (pinnedNow) "editor.pinnedRecentFileDone" else "editor.unpinnedRecentFileDone"))
                }
                TabTitleCreatorImpl.EVENT_OPEN_IN_TYPORA -> TyporaManager.getInstance().openEditor(this)
            }
        }
        titleMenu.setBeforeShow {
            val sourceFile = sourceFile
            val pinned = sourceFile != null && AllEditorsManager.isPinnedRecentFile(sourceFile.absolutePath)
            for (item in titleMenu.items) {
                when (item.userData) {
                    TabTitleCreatorImpl.EVENT_MOVE_TO_FRONT -> item.isDisable = activeTab.tabPane == null || activeTab.tabPane.tabs.indexOf(tab) <= 0
                    TabTitleCreatorImpl.EVENT_PIN_RECENT_FILE -> item.text = Locales.str(if (pinned) "editor.unpinRecentFile" else "editor.pinRecentFile")
                    TabTitleCreatorImpl.EVENT_OPEN_IN_TYPORA -> item.isVisible = TyporaManager.getInstance().isAvailable()
                        && (sourceFile == null || TyporaManager.getInstance().supports(sourceFile))
                }
            }
        }
        activeTab.contextMenu = titleMenu
        tabHeaderSubscription = activeTab.tabPaneProperty().flatMap { it.skinProperty() }
            .subscribe { _ -> Platform.runLater(::initTitleClick) }
    }

    private fun initTitleClick() {
        if (areaReference == null || tab == null) return
        tabLabel?.tooltip = null
        tabLabel = (activeTab.tabPane?.skin as? JFXTabPaneSkin)?.getTabLabel(tab)
        val label = tabLabel ?: return
        sourceFile?.let { label.tooltip = Tooltip(it.absolutePath) }
    }

    override fun rename() {
        val sourceFile = sourceFile ?: return
        JfoenixDialogUtils.editInput(Locales.ALERT(), sourceFile.name) { name ->
            if (!TextUtils.isEmpty(name)) {
                if (!pendingSave.isDone) return@editInput
                val answer = Utils.rename(sourceFile, name)
                if (answer == null) {
                    SnackbarUtils.show("maybe you do not save this file!")
                } else if (answer.newFullPath() == null && answer.run() != null) {
                    JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("doUWantReplaceOldFile"), 0, 0,
                        JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, null, action@{
                            if (!pendingSave.isDone) return@action
                            answer.run().invoke()?.let { afterRename(it) }
                        }),
                        JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, null))
                } else if (answer.newFullPath() != null) {
                    Log.d("changed name " + answer.newFullPath())
                    afterRename(answer.newFullPath())
                }
            }
        }
    }

    override fun resetText(text: String) {
        refreshProcessingLimits(sourceFile, area)
        programmaticReplace = true
        try {
            area.markdownTableDocumentState.reset()
            if (area.text != text) area.replaceText(text)
            area.undoManager.forgetHistory()
            area.undoManager.mark()
            establishUndoBaseline()
        } finally {
            programmaticReplace = false
        }
        documentState.externalState = EditorDocumentState.ExternalState.UNCHANGED
        documentState.updateBaseFileMetadata()
        markCurrentFileTs()
        EditorSessionManager.getInstance().onSaved(area, SaveResult.SUCCESS_CLEAN)
    }

    override fun canClosed(): Boolean = tab == null || !documentState.isDirty
    override fun removeStageFocus() = editorFocus.removeFocusChanged()
    override fun addStageFocus() = editorFocus.addFocusChanged()
    override fun checkFileIfChanged() {
        Log.d(TAG, "check file if changed!!!")
        editorFocus.checkFileChangedTs()
    }

    private fun afterRename(newFile: String) {
        editorFocus.removeFocusChanged()
        val newf = File(newFile)
        sourceFile?.let { UIContext.allOpenedFileList.remove(it) }
        documentState.bindSourceFile(newf)
        refreshProcessingLimits(newf, area)
        editorState.setWrap(editorState.isWrap)
        UIContext.allOpenedFileList.add(newf)
        activeTab.userData = documentState
        updateTabTitle()
        if (this is EditorAreaMgrCode) bindKeywordHelper(newf)
        UIContext.context().refreshCurrentDocumentInfo()
        AllEditorsManager.delayToSaveRecentFile(newFile)
        EditorSessionManager.getInstance().onCaretOrStructureChanged()
        markCurrentFileTs()
        tabLabel?.let { label ->
            if (label.tooltip == null) label.tooltip = Tooltip(newf.absolutePath) else label.tooltip.text = newf.absolutePath
        }
        ThreadUtils.globalHandler().postDelayed(editorFocus::addFocusChanged, 180)
        notifyWorkspaceRefreshDelayed(newf)
    }

    private fun notifyWorkspaceRefreshDelayed(changedFile: File) {
        ThreadUtils.globalHandler().postDelayed({
            Platform.runLater { UIContext.context().workspaceManager.ifRefreshWorkspace(changedFile) }
        }, 200)
    }

    override fun createMenu(): ContextMenu {
        val copy = MenuItem(Locales.str("editor.copySelect"))
        val copyLine = MenuItem(Locales.str("editor.copyLine"))
        val openFolder = MenuItem(Locales.str("editor.openHereDir"))
        val openTerminal = MenuItem(Locales.str("editor.programTerminal"))
        val openTerminalHere = MenuItem(Locales.str("editor.directoryTerminal"))
        val reload = MenuItem(Locales.str("editor.reload"))
        val screenShot = MenuItem(Locales.str("editor.screenshot"))
        val removeUnknownSymbols = MenuItem(Locales.str("removeUnknownSymbols"))
        val formatJson = MenuItem(Locales.str("jsonFormat"))
        val print = MenuItem(Locales.str("document.print"))
        print.setOnAction { com.allan.atools.tools.modulenotepad.manager.TextPrintSupport.print(area) }
        copyLine.setOnAction {
            val text = area.getText(Highlight.getCurrentCaretLineNum(area))
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
        copy.setOnAction { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(area.selectedText), null) }
        openFolder.setOnAction(::openCurrentFolder)
        openTerminal.setOnAction(::openTerminal)
        openTerminalHere.setOnAction(::openTerminalHere)
        reload.setOnAction { reloadFromDisk() }
        screenShot.setOnAction(::captureScreenShot)
        removeUnknownSymbols.setOnAction {
            replaceSelectedTextWithPadding(JsonFormatLog().removeFanxieExtraQuote(area.selectedText))
        }
        formatJson.setOnAction {
            val formatter = JsonFormatLog()
            replaceSelectedTextWithPadding(formatter.format(formatter.removeEnter(area.selectedText)))
        }
        val menu = AppContextMenu(copyLine, screenShot, copy, removeUnknownSymbols, formatJson, openTerminalHere, openFolder, reload, print)
        menu.setBeforeShow {
            val hasSelection = area.selectedText.isNotEmpty()
            copy.isVisible = hasSelection
            removeUnknownSymbols.isVisible = hasSelection
            formatJson.isVisible = hasSelection
            copyLine.isVisible = !hasSelection
            screenShot.isVisible = !hasSelection
            val named = sourceFile != null
            openTerminalHere.isVisible = !hasSelection && named
            openFolder.isVisible = !hasSelection && named
            reload.isVisible = !hasSelection && named
            print.isVisible = !MarkdownEditorSupport.supportsMarkdown(area)
        }
        return menu
    }

    private fun reloadFromDisk() {
        val sourceFile = sourceFile ?: return
        if (!sourceFile.isFile) return
        val reload = Runnable { AllEditorsManager.Instance.reOpenCurrentFile(tab, sourceFile, editorState.fileEncoding) }
        if (!documentState.isDirty) { reload.run(); return }
        JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("confirmReloadDiscard"), 0, 0,
            JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, null, reload::run),
            JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, null))
    }

    private fun replaceSelectedTextWithPadding(newText: String?) {
        if (!area.isEditable) return
        val selection = area.selection
        if (selection.length == 0) return
        val fullText = area.text
        val before = fullText.substring(0, selection.start)
        val after = fullText.substring(selection.end)
        val replacement = StringBuilder()
        if (before.isNotBlank()) {
            val lineBreaks = countBoundaryLineBreaks(before, before.length - 1, -1)
            if (lineBreaks < 4) replacement.append("\n".repeat(4 - lineBreaks))
        }
        replacement.append(newText)
        if (after.isNotBlank()) {
            val lineBreaks = countBoundaryLineBreaks(after, 0, 1)
            if (lineBreaks < 4) replacement.append("\n".repeat(4 - lineBreaks))
        }
        area.replaceText(selection.start, selection.end, replacement.toString())
    }

    private fun countBoundaryLineBreaks(text: String, index: Int, step: Int): Int {
        var lineBreaks = 0
        var i = index
        while (i >= 0 && i < text.length) {
            if (text[i] == '\n') lineBreaks++ else if (!Character.isWhitespace(text[i])) break
            i += step
        }
        return lineBreaks
    }

    private fun captureScreenShot(event: ActionEvent?) = Platform.runLater(Utils::captureScreenShot)
    private fun openCurrentFolder(event: ActionEvent?) {
        val sourceFile = sourceFile
        if (sourceFile?.parentFile != null) Utils.openFolderExplore(sourceFile)
    }
    private fun openTerminal(event: ActionEvent?) = Utils.openTerminal()
    private fun openTerminalHere(event: ActionEvent?) {
        val sourceFile = sourceFile
        if (sourceFile?.parentFile != null) Utils.openTerminalHere(sourceFile.parentFile)
    }

    override fun find(params: SearchParams, action: Action<OneFileSearchResults>) = findAdvance(arrayOf(params), action)
    override fun findAdvance(params: Array<SearchParams>, action: Action<OneFileSearchResults>) {
        val largeLog = area.largeLog
        if (largeLog != null) { largeLog.search(params, action); return }
        ThreadUtils.execute {
            val text = area.text
            val results = FinderFactory.find(text, SettingPreferences.getBoolean(SettingPreferences.searchResultHasNumberKey), params, intArrayOf(0))
            val result = OneFileSearchResults().addFile(sourceFile).addSessionId(documentState.sessionId)
                .addDisplayName(documentState.displayName).addArea(this).addTotalLen(text?.length ?: 0).addResults(results)
            action.invoke(result)
        }
    }

    override fun replace(params: ReplaceParams): StringBuilder? = null
    open fun trigger(temporaryText: SearchParams?, searchText: SearchParams?, end: Action0?) {
        throw RuntimeException("base should not call trigger in EditorBase")
    }

    private class EditorBaseFocus(private val editor: EditorAreaMgr) {
        private var mIsAddedFocusChanged = false
        var mLastFileChangedTs = 0L
        var mLastFileSize = 0L
        private var mFocused: Action0? = null

        fun checkFileChangedTs() {
            val sourceFile = editor.sourceFile ?: return
            if (!sourceFile.exists()) {
                if (editor.documentState.externalState == EditorDocumentState.ExternalState.DELETED) return
                editor.documentState.externalState = EditorDocumentState.ExternalState.DELETED
                if (editor.documentState.isDirty) {
                    JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("sourceDeletedKept"))
                    return
                }
                JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("fileNotExsit"), 0, 0,
                    JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, Locales.str("reCreateFile"), {
                        removeFocusChanged()
                        editor.saveContent(null, true)
                        ThreadUtils.globalHandler().postDelayed(::addFocusChanged, 250)
                    }),
                    JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, Locales.str("close"), {
                        // 用户放弃文档时先移除监听，再关闭标签。
                        removeFocusChanged()
                        editor.closeTab()
                    }))
                return
            }
            if (mLastFileChangedTs == sourceFile.lastModified() && mLastFileSize == sourceFile.length()) return
            if (editor.documentState.isDirty) {
                if (editor.documentState.externalState != EditorDocumentState.ExternalState.MODIFIED) {
                    editor.documentState.externalState = EditorDocumentState.ExternalState.MODIFIED
                    JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("sourceChangedKept"))
                }
                return
            }
            AllEditorsManager.Instance.reOpenCurrentFile(editor.tab, sourceFile, null)
        }
        fun removeFocusChanged() {
            if (mIsAddedFocusChanged) {
                Log.d(TAG, "remove focusChanged " + editor.sourceFile)
                UIContext.context().removeMainStageFocused(mFocused)
                mFocused = null
                mIsAddedFocusChanged = false
            }
        }
        fun addFocusChanged() {
            if (mFocused == null) mFocused = Action0(::checkFileChangedTs)
            if (!mIsAddedFocusChanged) {
                Log.d(TAG, "add focusChanged " + editor.sourceFile)
                UIContext.context().addMainStageFocused(mFocused)
                mIsAddedFocusChanged = true
            }
        }
    }
}

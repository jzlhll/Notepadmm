package com.allan.atools.richtext.codearea

import com.allan.atools.FontTheme
import com.allan.atools.UIContext
import com.allan.atools.tools.modulenotepad.Highlight
import com.allan.atools.tools.modulenotepad.bottom.BottomSearchBtnsMgr
import com.allan.atools.tools.modulenotepad.session.EditorSessionManager
import com.allan.atools.utils.Log
import com.allan.baseparty.Action
import com.allan.baseparty.memory.RefWatcher
import com.allan.uilibs.richtexts.CodeArea
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.control.Tab
import javafx.scene.control.TextInputControl
import javafx.scene.web.WebView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.InputMethodEvent
import javafx.scene.input.MouseEvent
import java.io.File

class EditorArea @JvmOverloads constructor(
    sourceFile: File?,
    tab: Tab?,
    text: String,
    documentState: EditorDocumentState? = null
) :
    CodeArea(text, true) {

    val editor: EditorAreaMgr
    val bottomSearchBtnsMgr: BottomSearchBtnsMgr
    val fontThemeChanged: ChangeListener<Number>
    val multiSelections: EditorAreaMultiSelectionsMgr
    val markdownTableDocumentState = MarkdownTableDocumentState()
    val viewPosition: EditorViewPosition
    private var followZoomCaretRequested = false
    private val paragraphWrapping = MarkdownParagraphWrapSupport(this)
    val markdownPresentation = MarkdownPresentation(this)
    val markdownSyntax = MarkdownSyntaxPresentation(this)
    val markdownEditing: MarkdownEditingActions
    val markdownAttachments by lazy { MarkdownAttachments(this) }
    val markdownClipboard by lazy { MarkdownClipboard(this) }
    val markdownCodeActions by lazy { MarkdownCodeActions(this) }
    private val afterMarkdownComposition = ArrayDeque<Runnable>()
    var markdownComposing = false
        private set
    var markdownPreviewEnabled = true
        private set

    fun runAfterMarkdownComposition(action: Runnable) {
        if (markdownComposing) afterMarkdownComposition.add(action) else action.run()
    }

    fun toggleMarkdownPreview() {
        val top = if (visibleParagraphs.isEmpty()) -1 else firstVisibleParToAllParIndex()
        markdownPreviewEnabled = !markdownPreviewEnabled
        if (!markdownPreviewEnabled) markdownPresentation.clear()
        else markdownPresentation.snapshot?.let { if (it.text == text) markdownPresentation.apply(it) }
        (editor as EditorAreaMgrCode).trigger(null, null, null)
        UIContext.context().refreshCurrentDocumentInfo()
        if (top >= 0) javafx.application.Platform.runLater {
            if (!editor.isDestroyed && top < paragraphs.size) showParagraphAtTop(top)
        }
    }

    companion object {
        @JvmStatic
        private val TAG = "EditorAreaImpl"

        @JvmField
        val DEBUG_EDITOR = true && UIContext.DEBUG

        // 半角→全角标点映射，仅"中文标点模式"总开关开启时生效：
        // macOS 部分第三方输入法（如微信输入法）无法往 JavaFX 编辑器提交全角标点，用此表在 KEY_TYPED 层兜底转换
        private val FULLWIDTH_PUNCTUATION = mapOf(
            ',' to '，', '.' to '。', '?' to '？', '!' to '！',
            ':' to '：', ';' to '；', '(' to '（', ')' to '）',
            '[' to '【', ']' to '】'
        )

    }

    private fun createEditorAreaMgr(
        area: EditorArea,
        sourceFile: File?,
        tab: Tab?,
        documentState: EditorDocumentState?
    ): EditorAreaMgr {
        return EditorAreaMgrCode(area, sourceFile, tab, documentState)
    }

    init {
        styleClass.add("editor-area")
        editor = createEditorAreaMgr(this, sourceFile, tab, documentState)
        markdownEditing = MarkdownEditingActions(this)
        markdownEditing.installMenu()
        multiSelections = EditorAreaMultiSelectionsMgr(this)
        bottomSearchBtnsMgr = BottomSearchBtnsMgr(this)
        Highlight.initGenericAreaFont(this)
        //Editor的Fontsize不是那样来的。所以不用。设置fontSize监听
        fontThemeChanged =
            ChangeListener { _: ObservableValue<out Number>?, oldValue: Number, _: Number? ->
                val newfm = FontTheme.fontFamily()
                val fm = FontTheme.fontFamily(oldValue.toInt())
                Log.d(TAG, "update font theme : old is : $fm, newOne: $newfm")
                Highlight.updateGenericAreaFont(this, newfm, fm)
            }
        UIContext.getFontThemeProperty().addListener(fontThemeChanged)

        //setUseInitialStyleForInsertion(false);
        Highlight.jumpToHead(this)
        viewPosition = EditorViewPosition(this)
        markdownSyntax.install()

        addEventFilter(MouseEvent.MOUSE_CLICKED) { event ->
            if (event.target !is TextInputControl && event.button == MouseButton.PRIMARY && !event.isShortcutDown
                && event.clickCount == 1 && event.isStillSincePress && markdownEditing.toggleTask(event)) {
                event.consume()
                return@addEventFilter
            }
            if (event.target is TextInputControl || !isMarkdownDocument() || event.button != MouseButton.PRIMARY
                || !event.isShortcutDown || event.isAltDown || event.isShiftDown
                || event.clickCount != 1 || !event.isStillSincePress
            ) {
                return@addEventFilter
            }
            val position = hit(event.x, event.y).characterIndex
            if (position.isPresent && getStyleOfChar(position.asInt).contains("markdown-link")) {
                event.consume()
                (editor as EditorAreaMgrCode).openMarkdownLinkAt(position.asInt)
            }
        }

        addEventFilter(MouseEvent.MOUSE_PRESSED) {
            bottomSearchBtnsMgr.cancelPendingSearchJump()
        }
        focusedProperty().addListener { _, _, focused ->
            if (focused) bottomSearchBtnsMgr.cancelPendingSearchJump()
        }
        caretPositionProperty().addListener { _, _, _ ->
            if (isFocused) bottomSearchBtnsMgr.cancelPendingSearchJump()
        }
        addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) {
            markdownComposing = it.composed.isNotEmpty()
            if (!markdownComposing) javafx.application.Platform.runLater {
                if (!markdownComposing) while (afterMarkdownComposition.isNotEmpty()) afterMarkdownComposition.removeFirst().run()
            }
            bottomSearchBtnsMgr.cancelPendingSearchJump()
            markdownSyntax.requestRefresh()
        }

        addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            bottomSearchBtnsMgr.cancelPendingSearchJump()
            if (event.target is TextInputControl || event.target is WebView) return@addEventFilter
            if (!isEditable || markdownComposing) return@addEventFilter
            if (markdownEditing.handleKey(event)) {
                event.consume()
                return@addEventFilter
            }
            if (event.isAltDown && !event.isControlDown && !event.isMetaDown && !event.isShiftDown
                && (event.code == KeyCode.UP || event.code == KeyCode.DOWN)
            ) {
                event.consume()
                moveSelectedLines(event.code == KeyCode.UP)
                return@addEventFilter
            }
            if (!isMarkdownDocument() || event.isAltDown || event.isShiftDown
                || !event.isShortcutDown
            ) {
                return@addEventFilter
            }
            when (event.code) {
                KeyCode.B -> {
                    event.consume()
                    markdownEditing.wrap("**")
                }
                KeyCode.BACK_QUOTE -> {
                    event.consume()
                    markdownEditing.wrap("`")
                }
                else -> {
                    val level = markdownHeadingLevel(event.code) ?: return@addEventFilter
                    event.consume()
                    markdownEditing.heading(level)
                }
            }
        }

        // 中文标点模式：本 tab 开启时把 KEY_TYPED 收到的半角标点替换为全角（只读时跳过，与默认输入行为一致）
        addEventFilter(KeyEvent.KEY_TYPED) { e ->
            if (e.target is TextInputControl || e.target is WebView) return@addEventFilter
            if (isEditable && editor.getState().isChinesePunctuation()) {
                val text = e.character
                if (text.length == 1) {
                    val mapped = FULLWIDTH_PUNCTUATION[text[0]]
                    if (mapped != null) {
                        e.consume()
                        replaceSelection(mapped.toString())
                    }
                }
            }
        }

        addEventFilter(javafx.scene.input.DragEvent.DRAG_OVER) { event ->
            if (isEditable && isMarkdownDocument() && event.dragboard.hasFiles()
                && event.dragboard.files.all(MarkdownAttachments::isImage)) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY)
                event.consume()
            }
        }
        addEventFilter(javafx.scene.input.DragEvent.DRAG_DROPPED) { event ->
            if (isEditable && isMarkdownDocument() && event.dragboard.hasFiles()
                && event.dragboard.files.all(MarkdownAttachments::isImage)) {
                markdownAttachments.importFiles(event.dragboard.files)
                event.isDropCompleted = true
                event.consume()
            }
        }

        RefWatcher.watchs(this, if (editor.sourceFile == null) "" else editor.sourceFile.path)
        EditorSessionManager.getInstance().track(this)
    }

    override fun paste() {
        if (!isEditable || markdownComposing) return
        if (!MarkdownEditorSupport.supportsMarkdown(this) || !markdownClipboard.paste()) super.paste()
    }

    override fun requestFollowCaret() {
        followZoomCaretRequested = true
        super.requestFollowCaret()
    }

    override fun layoutChildren() {
        val followZoomCaret = followZoomCaretRequested
        followZoomCaretRequested = false
        val wrapMarkdown = isMarkdownDocument() && !editor.isRealtimeProcessingLimitReached
        paragraphWrapping.refresh(wrapMarkdown)
        super.layoutChildren()
        paragraphWrapping.refresh(wrapMarkdown)
        // 等原有光标跟随完成布局，再处理缩放视口的横向裁剪。
        if (followZoomCaret) {
            (parent as? EditorZoomViewport)?.followCaret()
        }
    }

    private fun isMarkdownDocument(): Boolean {
        val name = editor.documentState.displayName
        return name.endsWith(".md", true) || name.endsWith(".markdown", true)
    }

    private fun markdownHeadingLevel(code: KeyCode): Int? {
        return when (code) {
            KeyCode.DIGIT0, KeyCode.NUMPAD0 -> 0
            KeyCode.DIGIT1, KeyCode.NUMPAD1 -> 1
            KeyCode.DIGIT2, KeyCode.NUMPAD2 -> 2
            KeyCode.DIGIT3, KeyCode.NUMPAD3 -> 3
            KeyCode.DIGIT4, KeyCode.NUMPAD4 -> 4
            KeyCode.DIGIT5, KeyCode.NUMPAD5 -> 5
            KeyCode.DIGIT6, KeyCode.NUMPAD6 -> 6
            else -> null
        }
    }

    private fun moveSelectedLines(up: Boolean) {
        val contentText = text
        val range = selectedLineRange(contentText)
        val blockStart = range.first
        val blockEnd = range.second
        val block = contentText.substring(blockStart, blockEnd)
        val selectionStartOffset = selection.start - blockStart
        val selectionEnd = if (selection.end < blockEnd) selection.end else blockEnd
        val selectionEndOffset = selectionEnd - blockStart
        val hadSelection = selection.length > 0
        val newStart: Int
        if (up) {
            if (blockStart == 0) return
            val previousStart = contentText.lastIndexOf('\n', blockStart - 2) + 1
            val previous = contentText.substring(previousStart, blockStart - 1)
            replaceText(previousStart, blockEnd, block + "\n" + previous)
            newStart = previousStart
        } else {
            if (blockEnd == contentText.length) return
            val nextStart = blockEnd + 1
            val nextLineEnd = contentText.indexOf('\n', nextStart)
            val nextEnd = if (nextLineEnd < 0) contentText.length else nextLineEnd
            val next = contentText.substring(nextStart, nextEnd)
            replaceText(blockStart, nextEnd, next + "\n" + block)
            newStart = blockStart + next.length + 1
        }
        if (hadSelection) {
            selectRange(newStart + selectionStartOffset, newStart + selectionEndOffset)
        } else {
            moveTo(newStart + selectionStartOffset)
        }
    }

    private fun selectedLineRange(text: String): Pair<Int, Int> {
        val start = selection.start
        val end = selection.end
        val effectiveEnd = if (end > start && text[end - 1] == '\n') end - 1 else end
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val nextLineEnd = text.indexOf('\n', effectiveEnd)
        val lineEnd = if (nextLineEnd < 0) text.length else nextLineEnd
        return lineStart to lineEnd
    }

    fun destroy() {
//        try {
//            CaretNode node = (CaretNode) ReflectionUtils.getPrivateField(getCaretSelectionBind(), "delegateCaret");
//            node.dispose();
//        } catch (NoSuchFieldException | IllegalAccessException e) {
//            e.printStackTrace();
//        }
        viewPosition.destroy()
        markdownSyntax.destroy()
        paragraphWrapping.clear()
        markdownPresentation.clear()
        dispose()
        UIContext.getFontThemeProperty().removeListener(fontThemeChanged)
        multiSelections.destroy()
        editor.destroy()
        while (afterMarkdownComposition.isNotEmpty()) afterMarkdownComposition.removeFirst().run()
        bottomSearchBtnsMgr.destroy()
    }
}

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
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
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

        private val MARKDOWN_HEADING = Regex("^( {0,3})#{1,6}(?:[ \\t]+|$)")
        private val MARKDOWN_INDENT = Regex("^ {0,3}(?! )")
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

        addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (!isEditable) return@addEventFilter
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
                    toggleMarkdownWrap("**")
                }
                KeyCode.BACK_QUOTE -> {
                    event.consume()
                    toggleMarkdownWrap("`")
                }
                else -> {
                    val level = markdownHeadingLevel(event.code) ?: return@addEventFilter
                    event.consume()
                    setMarkdownHeading(level)
                }
            }
        }

        // 中文标点模式：本 tab 开启时把 KEY_TYPED 收到的半角标点替换为全角（只读时跳过，与默认输入行为一致）
        addEventFilter(KeyEvent.KEY_TYPED) { e ->
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

        RefWatcher.watchs(this, if (editor.sourceFile == null) "" else editor.sourceFile.path)
        EditorSessionManager.getInstance().track(this)
    }

    private fun isMarkdownDocument(): Boolean {
        val name = editor.documentState.displayName
        return name.endsWith(".md", true) || name.endsWith(".markdown", true)
    }

    private fun toggleMarkdownWrap(mark: String) {
        val start = selection.start
        val end = selection.end
        val markLength = mark.length
        if (start == end) {
            if (start >= markLength && start + markLength <= length
                && getText(start - markLength, start) == mark
                && getText(start, start + markLength) == mark
            ) {
                replaceText(start - markLength, start + markLength, "")
                moveTo(start - markLength)
            } else {
                replaceText(start, start, mark + mark)
                moveTo(start + markLength)
            }
            return
        }

        val selected = getText(start, end)
        if (start >= markLength && end + markLength <= length
            && getText(start - markLength, start) == mark
            && getText(end, end + markLength) == mark
        ) {
            replaceText(start - markLength, end + markLength, selected)
            selectRange(start - markLength, end - markLength)
        } else if (selected.length >= markLength * 2
            && selected.startsWith(mark) && selected.endsWith(mark)
        ) {
            val inner = selected.substring(markLength, selected.length - markLength)
            replaceText(start, end, inner)
            selectRange(start, start + inner.length)
        } else {
            replaceText(start, end, mark + selected + mark)
            selectRange(start + markLength, end + markLength)
        }
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

    private fun setMarkdownHeading(level: Int) {
        val contentText = text
        val range = selectedLineRange(contentText)
        val block = contentText.substring(range.first, range.second)
        val hadSelection = selection.length > 0
        val caretOffset = selection.start - range.first
        val replaced = block.split("\n").joinToString("\n") { line ->
            val match = MARKDOWN_HEADING.find(line)
            val indent = match?.groupValues?.get(1) ?: MARKDOWN_INDENT.find(line)?.value.orEmpty()
            val content = match?.let { line.substring(it.value.length) } ?: line.substring(indent.length)
            if (level == 0) indent + content else indent + "#".repeat(level) + " " + content
        }
        replaceText(range.first, range.second, replaced)
        if (hadSelection) {
            selectRange(range.first, range.first + replaced.length)
        } else {
            val target = range.first + caretOffset + replaced.length - block.length
            moveTo(if (target < range.first) range.first else target)
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
        dispose()
        UIContext.getFontThemeProperty().removeListener(fontThemeChanged)
        multiSelections.destroy()
        editor.destroy()
        bottomSearchBtnsMgr.destroy()
    }
}

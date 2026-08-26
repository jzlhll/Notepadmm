package com.allan.atools.richtext.codearea

import com.allan.atools.FontTheme
import com.allan.atools.UIContext
import com.allan.atools.tools.modulenotepad.Highlight
import com.allan.atools.tools.modulenotepad.bottom.BottomSearchBtnsMgr
import com.allan.atools.utils.Log
import com.allan.baseparty.Action
import com.allan.baseparty.memory.RefWatcher
import com.allan.uilibs.richtexts.CodeArea
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.control.Tab
import javafx.scene.input.KeyEvent
import java.io.File

class EditorArea(sourceFile: File?, tab: Tab?, isFake: Boolean, text: String) :
    CodeArea(
        text,
        sourceFile != null && EditorKeywordHelperFactory.sFilePathToExtension.invoke(sourceFile) == "markdown"
    ) {

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
    }

    private fun createEditorAreaMgr(area: EditorArea, sourceFile: File?, tab: Tab?, isFake: Boolean): EditorAreaMgr {
        assert(sourceFile != null)
        val shortcutType = EditorKeywordHelperFactory.sFilePathToExtension.invoke(sourceFile)
        return if (shortcutType != null) {
            EditorAreaMgrCode(area, sourceFile, tab, isFake)
        } else EditorAreaMgr(area, sourceFile, tab, isFake)
    }

    init {
        styleClass.add("editor-area")
        editor = createEditorAreaMgr(this, sourceFile, tab, isFake)
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

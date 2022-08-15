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
import java.io.File

class EditorArea(sourceFile: File?, tab: Tab?, isFake: Boolean, text: String, beforeInitTextAction: Action<CodeArea>) :
    CodeArea(text, beforeInitTextAction) {

    val editor: EditorAreaMgr
    val bottomSearchBtnsMgr: BottomSearchBtnsMgr
    val fontThemeChanged: ChangeListener<Number>
    val multiSelections: EditorAreaMultiSelectionsMgr

    companion object {
        @JvmStatic
        private val TAG = "EditorAreaImpl"

        @JvmField
        val DEBUG_EDITOR = true && UIContext.DEBUG
    }

    private fun build(area: EditorArea, sourceFile: File?, tab: Tab?, isFake: Boolean): EditorAreaMgr {
        assert(sourceFile != null)
        val shortcutType = EditorKeywordHelperFactory.sFilePathToExtension.invoke(sourceFile)
        return if (shortcutType != null) {
            EditorAreaMgrCode(area, sourceFile, tab, isFake)
        } else EditorAreaMgr(area, sourceFile, tab, isFake)
    }

    init {
        editor = build(this, sourceFile, tab, isFake)
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

        if (RefWatcher.getInstance() != null) {
            RefWatcher.getInstance().watch(this, if (editor.sourceFile == null) "" else editor.sourceFile.path)
        }
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
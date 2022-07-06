package com.allan.atools.richtext.codearea

import com.allan.atools.FontTheme
import com.allan.atools.UIContext
import com.allan.atools.tools.modulenotepad.Highlight
import com.allan.atools.tools.modulenotepad.bottom.BottomSearchButtons
import com.allan.atools.utils.Log
import com.allan.baseparty.Action
import com.allan.baseparty.memory.RefWatcher
import com.allan.uilibs.richtexts.MyCodeArea
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.control.Tab
import org.fxmisc.richtext.GenericStyledArea
import java.io.File
import java.util.*

class EditorAreaImpl(sourceFile: File?, tab: Tab?, isFake: Boolean, text: String, beforeInitTextAction: Action<MyCodeArea>) :
    MyCodeArea(text, beforeInitTextAction) {

    private var mEditor: EditorBase
    private var bottomSearchButtons: BottomSearchButtons
    private var fontThemeChanged: ChangeListener<Number>
    val multiSelections: EditorAreaImplMultiSelections

    companion object {
        @JvmStatic
        private val TAG = "EditorAreaImpl"

        @JvmField
        val DEBUG_EDITOR = true && UIContext.DEBUG
    }

    init {
        mEditor = EditorBase.build(this, sourceFile, tab, isFake)
        multiSelections = EditorAreaImplMultiSelections(this)
        bottomSearchButtons = BottomSearchButtons(this)
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
            RefWatcher.getInstance().watch(this, if (mEditor.sourceFile == null) "" else mEditor.sourceFile.path)
        }
    }

    fun getSourceFile(): File? {
        return mEditor.sourceFile
    }

    fun getEditor(): EditorBase {
        return mEditor
    }

    fun getBottom(): BottomSearchButtons {
        return bottomSearchButtons
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
        getEditor().destroy()
        getBottom().destroy()
    }
}
package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.UIContext;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.EditorAreaMgr;
import com.allan.atools.richtext.codearea.EditorAreaMgrCode;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.StaticsProf;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.utils.Log;

/**
 * Styler是BottomSearchBtnsMgr的一个对象。则每一个Editor有一个它。
 */
final class Styler {
    static final boolean DEBUG_STYLER = (true || EditorArea.DEBUG_EDITOR) && UIContext.DEBUG;
    final EditorArea area;
    private final StylerActionFull mFullAction;
    private final StylerActionPartial mPartAction;

    private final StylerActionCode mCodeAction;

    Styler(BottomSearchBtnsMgr out) {
        this.area = out.editorArea;
        mFullAction = new StylerActionFull(out);
        mPartAction = new StylerActionPartial(out);
        mCodeAction = new StylerActionCode(out);
    }

    /**
     * 将Temp模式的搜索结果和Search模式的结果都做颜色匹配生成Styler配色
     */
    void stylingNormal(final long flag, OneFileSearchResults items,
                                       BottomHandler.ClickType clickType, ShowType showType) {
        if (DEBUG_STYLER) {
            Log.d("Styler: temporary SearchEndCallback flag=" + flag);
        }
        if (items.totalLen >= StaticsProf.getMaxFileSizeForStyle()) { //这里可能是大文件概率卡顿的原因。
            if(DEBUG_STYLER) Log.d("Styler: tempAndSearch end back: big file");
            mPartAction.setVisibleParaChanged();
            mPartAction.action(area, flag, items, clickType, showType);
        } else {
            if(DEBUG_STYLER) Log.d("Styler: tempAndSearch end back: small file ");
            mPartAction.removeVisibleParaChanged();
            mFullAction.action(area, flag, items, clickType, showType);
        }
    }

    void stylingCode(BottomHandler.ClickType clickType, SearchParams curTempParams, SearchParams curParams) {
        mCodeAction.action(clickType, curTempParams, curParams);
    }

    void destroy() {
        mPartAction.destroy();
        mFullAction.destroy();
        mCodeAction.destroy();
    }
}

package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.StaticsProf;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.utils.Log;

/**
 * Styler是BottomSearchButtons的一个对象。则每一个Editor有一个它。
 */
final class Styler {
    static final boolean DEBUG_STYLER = (true || EditorArea.DEBUG_EDITOR) && UIContext.DEBUG;
    final BottomSearchButtons out;
    private final StylerAction mFullAction, mPartAction;

    Styler(BottomSearchButtons out) {
        this.out = out;
        mFullAction = new StylerActionFull(out);
        mPartAction = new StylerActionPartial(out);
    }

    void temporaryAndSearchEndCallback(EditorArea area, final long flag, OneFileSearchResults items,
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

    void destroy() {
        mPartAction.destroy();
        mFullAction.destroy();
    }
}

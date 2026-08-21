package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.utils.Log;

abstract class StylerAction {
    final BottomSearchBtnsMgr out;
    StylerAction(BottomSearchBtnsMgr out) {
        this.out = out;
    }

    void destroy(){}

    protected final void onStyleOver(BottomHandler.ClickType clickType) {
        if (clickType == BottomHandler.ClickType.Search) {
            if(Styler.DEBUG_STYLER) Log.w(">>>>>>jump To Next<<<<");
            out.jumpToNext(out.editorArea, false, true);
        }
    }
}

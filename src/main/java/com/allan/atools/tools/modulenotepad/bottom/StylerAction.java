package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.utils.Log;

abstract class StylerAction {
    final BottomSearchBtnsMgr out;
    StylerAction(BottomSearchBtnsMgr out) {
        this.out = out;
    }

    void destroy(){}

    protected final void onStyleOver(long flag, BottomHandler.ClickType clickType) {
        if (flag != out.lastChangeSearchFlag.get() || out.editorArea.getEditor().isDestroyed()) {
            return;
        }
        if (clickType == BottomHandler.ClickType.Search) {
            if(Styler.DEBUG_STYLER) Log.w(">>>>>>jump To Next<<<<");
            out.jumpToNext(out.editorArea, false, true);
        } else {
            out.refreshIndicator();
        }
    }
}

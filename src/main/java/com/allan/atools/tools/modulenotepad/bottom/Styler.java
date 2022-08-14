package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.utils.Log;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.StaticsProf;
import com.allan.baseparty.handler.HandlerThread;

/**
 * Styler是BottomSearchButtons的一个对象。则每一个Editor有一个它。
 */
final class Styler {
    static final boolean DEBUG_STYLER = (true || EditorArea.DEBUG_EDITOR) && UIContext.DEBUG;

    abstract static class IStylerAction {
        abstract void setVisibleParaChanged();

        abstract void removeVisibleParaChanged();

        /**
         * 没有必要所有Editor下面的bottom下面的Styler都有一个。
         * 共用一个。我们也就不用关注他是否释放了。
         */
        private static volatile ThreadUtils.ClosedDroppedHandler sHandler;
        static ThreadUtils.ClosedDroppedHandler getHandler() {
            if (sHandler == null) {
                synchronized (Styler.class) {
                    if (sHandler == null) {
                        var th = new HandlerThread("bottom-styler");
                        th.start();
                        sHandler = new ThreadUtils.ClosedDroppedHandler(th.getLooper());
                    }
                }
            }
            return sHandler;
        }

        final Styler mStyler;
        IStylerAction(Styler styler) {
            mStyler = styler;
        }

        void destroy(){}

        abstract void action(EditorArea area, final long flag, OneFileSearchResults items, BottomHandler.ClickType clickType, ShowType showType);
    }

    final BottomSearchButtons out;
    private final IStylerAction mFullAction, mPartAction;

    Styler(BottomSearchButtons out) {
        this.out = out;
        mFullAction = new StylerFull(this);
        mPartAction = new StylerPartial(this);
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

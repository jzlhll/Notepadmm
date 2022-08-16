package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.bean.SearchParams;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ManualGC;
import com.allan.baseparty.handler.HandlerThread;

abstract class StylerAction {
    public interface INormalAction {
        void action(EditorArea area, final long flag, OneFileSearchResults items, BottomHandler.ClickType clickType, ShowType showType);
    }

    public interface ICodeAction {
        void action(BottomHandler.ClickType clickType, SearchParams curTempParams, SearchParams curParams);
    }

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
        ManualGC.lessGC();
    }
}

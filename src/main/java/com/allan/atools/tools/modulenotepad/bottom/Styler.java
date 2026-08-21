package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.UIContext;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.utils.Log;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

/**
 * Styler是BottomSearchBtnsMgr的一个对象。则每一个Editor有一个它。
 */
final class Styler {
    static final boolean DEBUG_STYLER = (true || EditorArea.DEBUG_EDITOR) && UIContext.DEBUG;
    final EditorArea area;
    private final StylerActionFull mFullAction;

    private final StylerActionCode mCodeAction;

    Styler(BottomSearchBtnsMgr out) {
        this.area = out.editorArea;
        mFullAction = new StylerActionFull(out);
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
        if (area.getEditor().disableStylerIfNeeded(() -> mFullAction.onStyleOver(clickType))) {
            if(DEBUG_STYLER) Log.d("Styler: styling disabled by limit");
            return;
        }
        mFullAction.action(area, flag, items, clickType, showType);
    }

    void stylingCode(BottomHandler.ClickType clickType, SearchParams curTempParams,
                     SearchParams curParams, String text, long contentVersion,
                     StyleSpans<Collection<String>> currentSpans) {
        mCodeAction.action(clickType, curTempParams, curParams,
                text, contentVersion, currentSpans);
    }

    void destroy() {
        mFullAction.destroy();
        mCodeAction.destroy();
    }
}

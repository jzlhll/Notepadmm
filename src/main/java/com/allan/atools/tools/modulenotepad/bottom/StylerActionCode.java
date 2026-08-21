package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.bean.SearchParams;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

final class StylerActionCode extends StylerAction {
    StylerActionCode(BottomSearchBtnsMgr out) {
        super(out);
    }

    void action(BottomHandler.ClickType clickType, SearchParams curTempParams,
                SearchParams curParams, String text, long contentVersion,
                StyleSpans<Collection<String>> currentSpans) {
        out.editorArea.getEditor().triggerWithSnapshot(
                text, contentVersion, currentSpans, curTempParams, curParams, () -> {
            onStyleOver(clickType);
        });
    }
}

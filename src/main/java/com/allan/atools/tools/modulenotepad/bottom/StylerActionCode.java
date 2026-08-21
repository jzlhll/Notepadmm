package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.bean.SearchParams;
import com.allan.atools.tools.modulenotepad.bottom.StylerAction.ICodeAction;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

public class StylerActionCode extends StylerAction implements ICodeAction {
    StylerActionCode(BottomSearchBtnsMgr out) {
        super(out);
    }

    @Override
    void setVisibleParaChanged() {

    }

    @Override
    void removeVisibleParaChanged() {

    }

    @Override
    public void action(BottomHandler.ClickType clickType, SearchParams curTempParams, SearchParams curParams) {
        out.editorArea.getEditor().trigger(curTempParams, curParams, ()->{
            onStyleOver(clickType);
        });
    }

    void actionMarkdown(BottomHandler.ClickType clickType, SearchParams curTempParams,
                        SearchParams curParams, String text, long contentVersion,
                        StyleSpans<Collection<String>> currentSpans) {
        out.editorArea.getEditor().triggerWithSnapshot(
                text, contentVersion, currentSpans, curTempParams, curParams, () -> {
            onStyleOver(clickType);
        });
    }
}

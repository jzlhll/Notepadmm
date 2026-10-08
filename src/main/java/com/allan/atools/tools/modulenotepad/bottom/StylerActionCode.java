package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.bean.SearchParams;
import javafx.application.Platform;

final class StylerActionCode extends StylerAction {
    StylerActionCode(BottomSearchBtnsMgr out) {
        super(out);
    }

    void action(long flag, long contentVersion, BottomHandler.ClickType clickType,
                SearchParams curTempParams, SearchParams curParams) {
        Platform.runLater(() -> {
            if (!isCurrent(flag, contentVersion)) {
                return;
            }
            ((com.allan.atools.richtext.codearea.EditorAreaMgrCode) out.editorArea.getEditor()).triggerSearch(curTempParams, curParams, () -> {
                if (isCurrent(flag, contentVersion)) {
                    onStyleOver(flag, clickType);
                }
            });
        });
    }

    private boolean isCurrent(long flag, long contentVersion) {
        return flag == out.lastChangeSearchFlag.get()
                && contentVersion == out.editorArea.getEditor().getContentVersion();
    }
}

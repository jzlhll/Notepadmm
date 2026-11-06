package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import javafx.scene.layout.AnchorPane;

final class ResultUpdaterSplitPaneImpl extends AbstractResultUpdater {

    @Override
    public boolean bringToFront() {
        UIContext.mainController.getStage().toFront();
        return true;
    }

    @Override
    void assetRoot() {
        if (mResultRoot == null) {
            mResultRoot = new ResultSessionView();
            mResultRoot.setOnEmpty(this::close);
            AnchorPane.setLeftAnchor(mResultRoot, 0.0);
            AnchorPane.setRightAnchor(mResultRoot, 0.0);
            AnchorPane.setTopAnchor(mResultRoot, 0.0);
            AnchorPane.setBottomAnchor(mResultRoot, 0.0);
            UIContext.context().getNotepadMainResultLayout().getChildren().add(mResultRoot);
        }
    }

    @Override
    void afterShown() {
    }

    @Override
    public void close() {
        destroyResultRoot();
        mResultRoot = null;

        UIContext.context().removeResultLayout();
    }
}

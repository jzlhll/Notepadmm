package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.ManualGC;
import javafx.application.Platform;
import javafx.scene.control.Accordion;
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
            mResultRoot = new Accordion();
            AnchorPane.setLeftAnchor(mResultRoot, 0.0);
            AnchorPane.setRightAnchor(mResultRoot, 0.0);
            AnchorPane.setTopAnchor(mResultRoot, 0.0);
            AnchorPane.setBottomAnchor(mResultRoot, 0.0);
            UIContext.context().getNotepadMainResultLayout().getChildren().add(mResultRoot);

            initPropertiesListener();
        }
    }

    @Override
    void afterShown() {
        ThreadUtils.globalHandler().postDelayedCheckClosed(() -> {
            Platform.runLater(() -> {
                mResultRoot.getPanes().get(0).setExpanded(true);
                ManualGC.triplyGC();
            });
        }, 100L);
    }

    @Override
    public void close() {
        mResultRoot.getPanes().removeAll();
        mResultRoot = null;

        UIContext.context().removeResultLayout();
        ManualGC.directlyGC();
    }
}

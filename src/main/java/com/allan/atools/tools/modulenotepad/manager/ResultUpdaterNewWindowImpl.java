package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.controllerwindow.ResultNewWindow;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.ManualGC;
import javafx.application.Platform;

public class ResultUpdaterNewWindowImpl extends AbstractResultUpdater {
    private static ResultNewWindow sNewWindow;

    @Override
    public boolean bringToFront() {
        sNewWindow.getController().getStage().toFront();
        return true;
    }

    @Override
    void assetRoot() {
        if (sNewWindow == null) {
            sNewWindow = ResultNewWindow.createInstance();
            mResultRoot = sNewWindow.getController().getResultRoot();

            initPropertiesListener();
        }
    }

    @Override
    void afterShown() {
        Platform.runLater(() -> {
            sNewWindow.show();
            ThreadUtils.globalHandler().postDelayedCheckClosed(() -> {
                Platform.runLater(()-> {
                    mResultRoot.getPanes().get(0).setExpanded(true);
                    ManualGC.triplyGC();
                });
            }, 100L);
        });
    }

    @Override
    public void close() {
        mResultRoot.getPanes().removeAll();
        mResultRoot = null;

        if (sNewWindow != null) {
            sNewWindow.hide();
            sNewWindow = null;
        }

        ManualGC.directlyGC();
    }

    @Override
    void requestFocus() {
        sNewWindow.getController().getStage().requestFocus();
    }
}

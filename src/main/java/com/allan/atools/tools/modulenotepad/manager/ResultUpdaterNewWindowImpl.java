package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.controllerwindow.ResultNewWindow;
import com.allan.atools.threads.ThreadUtils;
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
            if (sNewWindow == null) {
                return;
            }
            sNewWindow.show();
            ThreadUtils.globalHandler().postDelayedCheckClosed(() -> {
                Platform.runLater(()-> {
                    if (mResultRoot != null && !mResultRoot.getPanes().isEmpty()) {
                        mResultRoot.getPanes().get(0).setExpanded(true);
                    }
                });
            }, 100L);
        });
    }

    @Override
    public void close() {
        destroyResultRoot();
        mResultRoot = null;

        if (sNewWindow != null) {
            sNewWindow.hide();
            sNewWindow = null;
        }
    }

    @Override
    void requestFocus() {
        sNewWindow.getController().getStage().requestFocus();
    }
}

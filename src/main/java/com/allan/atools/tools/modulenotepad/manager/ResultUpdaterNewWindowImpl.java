package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.controllerwindow.ResultNewWindow;
import javafx.application.Platform;

public class ResultUpdaterNewWindowImpl extends AbstractResultUpdater {
    private static ResultNewWindow sNewWindow;

    @Override
    public boolean bringToFront() {
        if (sNewWindow == null || sNewWindow.getController() == null
                || sNewWindow.getController().getStage() == null) {
            return false;
        }
        sNewWindow.getController().getStage().toFront();
        return true;
    }

    @Override
    void assetRoot() {
        if (sNewWindow == null) {
            sNewWindow = ResultNewWindow.createInstance();
            mResultRoot = sNewWindow.getController().getResultRoot();
            mResultRoot.setOnEmpty(this::close);
        }
    }

    @Override
    void afterShown() {
        Platform.runLater(() -> {
            if (sNewWindow != null) {
                sNewWindow.show();
            }
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
        if (sNewWindow != null && sNewWindow.getController().getStage() != null) {
            sNewWindow.getController().getStage().requestFocus();
        }
    }
}

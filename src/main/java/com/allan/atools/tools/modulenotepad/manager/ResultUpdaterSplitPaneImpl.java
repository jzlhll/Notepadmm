package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.threads.ThreadUtils;
import javafx.application.Platform;
import javafx.scene.control.Accordion;
import javafx.scene.layout.AnchorPane;

final class ResultUpdaterSplitPaneImpl extends AbstractResultUpdater {
    private static final double MIN_EXPANDED_CONTENT_HEIGHT = 180;

    @Override
    public boolean bringToFront() {
        UIContext.mainController.getStage().toFront();
        return true;
    }

    @Override
    void assetRoot() {
        if (mResultRoot == null) {
            mResultRoot = new Accordion() {
                @Override
                protected double computeMinHeight(double width) {
                    // 标题栏之外保留结果内容高度，避免分隔栏将展开区域压成一条滚动条。
                    return super.computeMinHeight(width)
                            + (getExpandedPane() == null ? 0 : MIN_EXPANDED_CONTENT_HEIGHT);
                }
            };
            mResultRoot.expandedPaneProperty().addListener((observable, oldPane, newPane) ->
                    mResultRoot.requestLayout());
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
                if (mResultRoot != null && !mResultRoot.getPanes().isEmpty()) {
                    mResultRoot.getPanes().get(0).setExpanded(true);
                }
            });
        }, 100L);
    }

    @Override
    public void close() {
        destroyResultRoot();
        mResultRoot = null;

        UIContext.context().removeResultLayout();
    }
}

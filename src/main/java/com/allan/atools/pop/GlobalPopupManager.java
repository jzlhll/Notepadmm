package com.allan.atools.pop;

import com.allan.atools.UIContext;
import com.allan.atools.controller.NotepadController;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.lang.ref.WeakReference;

public final class GlobalPopupManager extends Popup {
    private static GlobalPopupManager instance;
    private static boolean isInitWindowChanged = false;

    private static void initWindowChangedOnlyOnce() {
        if (!isInitWindowChanged) { //init once
            isInitWindowChanged = true;
            NotepadController.sizeXyChangedProp.addListener((observable, oldValue, newValue) -> {
                if (instance != null && instance.isShowing()) {
                    instance.hide();
                    instance = null;
                }
            });
        }
    }

    private VBox vBox;

    private GlobalPopupManager() {
        initWindowChangedOnlyOnce();
    }

    public static GlobalPopupManager instance() {
        if (instance == null) {
            instance = new GlobalPopupManager();
            instance.setAutoHide(true);
            instance.setHideOnEscape(true);
        }
        return instance;
    }

    @Override
    public void hide() {
        if (instance != null) {
            super.hide();
            instance = null;
        }
    }

    private static final class WrapRefAction<T> implements Action<T> {
        final WeakReference<Action<T>> actionRef;
        WrapRefAction(Action<T> action) {
            actionRef = new WeakReference<>(action);
        }

        @Override
        public void invoke(T t) {
            var real = actionRef.get();
            if (real != null) {
                real.invoke(t);
            }

            if (instance != null) {
                instance.hide();
            }
        }
    }

    public <T> GlobalPopupManager setContent(Region region) {
        boolean isNew = false;
        if (vBox == null) {
            vBox = new VBox();
            vBox.setSpacing(5);// 设置行与行之间的间距
            vBox.setAlignment(Pos.CENTER);
            vBox.setMinSize(350, 150);
            vBox.setMaxSize(350, 150);
            vBox.setStyle("-fx-background-color:#FFFFFF;-fx-background-radius:3 3 3 3;-fx-border-color:#aaaaaa; -fx-border-radius:3 3 3 3;");
            isNew = true;
        }

        vBox.getChildren().clear();
        vBox.getChildren().add(region);

        vBox.setMinSize(region.getPrefWidth(), region.getPrefHeight());
        vBox.setMaxSize(region.getPrefWidth(), region.getPrefHeight());

        if (isNew) instance.getContent().add(vBox);
        return this;
    }

    public GlobalPopupManager setHeight(int height) {
        super.setHeight(height);
        return this;
    }

    public GlobalPopupManager setWidth(int width) {
        super.setWidth(width);
        return this;
    }

    public void show(Node node) {
        show(node, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, 0, 0);
    }

    /**
     * show the popup according to the specified position
     *
     * @param vAlign can be TOP/BOTTOM
     * @param hAlign can be LEFT/RIGHT
     */
    public void show(Node node, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign) {
        show(node, vAlign, hAlign, 0, 0);
    }

    /**
     * show the popup according to the specified position with a certain offset
     *
     * @param vAlign      can be TOP/BOTTOM
     * @param hAlign      can be LEFT/RIGHT
     * @param initOffsetX on the x axis
     * @param initOffsetY on the y axis
     */
    public void show(Node node, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign, double initOffsetX, double initOffsetY) {
        if (!isShowing()) {
            if (node.getScene() == null || node.getScene().getWindow() == null) {
                throw new IllegalStateException("Can not show popup. The node must be attached to a scene/window.");
            }
            Window parent = node.getScene().getWindow();
            final Point2D origin = node.localToScene(0, 0);
            final double anchorX = parent.getX() + origin.getX()
                    + node.getScene().getX() + (hAlign == JFXPopup.PopupHPosition.RIGHT ? ((Region) node).getWidth() : 0);
            final double anchorY = parent.getY() + origin.getY()
                    + node.getScene()
                    .getY() + (vAlign == JFXPopup.PopupVPosition.BOTTOM ? ((Region) node).getHeight() : 0);
            super.show(parent, anchorX, anchorY);
        }
    }
}

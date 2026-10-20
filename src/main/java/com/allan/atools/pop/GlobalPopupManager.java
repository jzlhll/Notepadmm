package com.allan.atools.pop;

import com.allan.atools.controller.NotepadController;
import com.jfoenix.controls.JFXPopup;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** 通用内容浮层入口，与菜单共用窗口内的无动画浮层。 */
public final class GlobalPopupManager {
    private static GlobalPopupManager instance;
    private static boolean windowListenerInstalled;
    private final VBox content = new VBox(5);
    private MenuOverlay presentation;

    private GlobalPopupManager() {
        content.setAlignment(Pos.CENTER);
        if (!windowListenerInstalled) {
            windowListenerInstalled = true;
            NotepadController.sizeXyChangedProp.addListener((observable, oldValue, newValue) -> {
                if (instance != null) instance.hide();
            });
        }
    }

    public static GlobalPopupManager instance() {
        if (instance == null) instance = new GlobalPopupManager();
        return instance;
    }

    public boolean isShowing() {
        return presentation != null && presentation.isOpen();
    }

    public void hide() {
        if (presentation != null) presentation.close();
        if (instance == this) instance = null;
    }

    public GlobalPopupManager setContent(Region region) {
        content.getChildren().setAll(region);
        return this;
    }

    public GlobalPopupManager setHeight(int height) {
        content.setPrefHeight(height);
        return this;
    }

    public GlobalPopupManager setWidth(int width) {
        content.setPrefWidth(width);
        return this;
    }

    public void show(Node node) {
        show(node, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, 0, 0);
    }

    public void show(Node node, JFXPopup.PopupVPosition vertical, JFXPopup.PopupHPosition horizontal) {
        show(node, vertical, horizontal, 0, 0);
    }

    public void show(Node node, JFXPopup.PopupVPosition vertical, JFXPopup.PopupHPosition horizontal,
                     double offsetX, double offsetY) {
        if (isShowing()) return;
        double dx = offsetX + (horizontal == JFXPopup.PopupHPosition.RIGHT ? node.getBoundsInLocal().getWidth() : 0);
        double dy = offsetY - (vertical == JFXPopup.PopupVPosition.TOP ? node.getBoundsInLocal().getHeight() : 0);
        presentation = PopupMenus.showPopupContent(node, content, Side.BOTTOM, dx, dy, () -> {
            presentation = null;
            if (instance == this) instance = null;
        });
    }
}

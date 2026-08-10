package com.allan.atools.ui;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.controller.SettingController;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * 设置页抽屉：从主窗口右侧滑入/滑出，顶部与 actionbar 平齐。
 * 通过在 Scene 层把原根节点包裹进 StackPane，实现覆盖整窗的蒙层与抽屉层叠。
 */
public final class SettingDrawer {
    private static final double DRAWER_WIDTH = 550;
    private static final double HEADER_HEIGHT = 40;
    private static final double BOTTOM_PADDING = 18;
    private static final double CONTENT_BOTTOM_MARGIN = 24;
    private static final Duration OPEN_DURATION = Duration.millis(250);
    private static final Duration CLOSE_DURATION = Duration.millis(200);

    private static final String WRAPPER_MARK_KEY = "setting-drawer-wrapper";

    private final Stage stage;
    private final StackPane host;
    private final Region mask;
    private final StackPane drawerBox;
    private SettingController controller;
    private boolean initialized = false;
    private boolean shown = false;
    private EventHandler<KeyEvent> escHandler;

    public SettingDrawer(Stage stage) {
        this.stage = stage;
        this.host = ensureWrapperStackPane(stage.getScene());

        this.mask = new Region();
        this.mask.getStyleClass().add("setting-drawer-mask");
        this.mask.setVisible(false);
        this.mask.setOpacity(0);
        this.mask.setOnMouseClicked(e -> close());
        this.mask.setPickOnBounds(true);

        this.drawerBox = new StackPane();
        this.drawerBox.getStyleClass().addAll("custom-main-bg", "setting-drawer");
        this.drawerBox.setPrefWidth(DRAWER_WIDTH);
        this.drawerBox.setMinWidth(DRAWER_WIDTH);
        this.drawerBox.setMaxWidth(DRAWER_WIDTH);
        this.drawerBox.setVisible(false);
        this.drawerBox.setPickOnBounds(true);
        StackPane.setAlignment(this.drawerBox, Pos.CENTER_RIGHT);
        this.drawerBox.setTranslateX(DRAWER_WIDTH);

        host.getChildren().addAll(mask, drawerBox);
    }

    /**
     * 把 Scene 原根节点包裹进 StackPane（如果还没包过），
     * 返回可用于层叠的 StackPane 宿主。
     */
    private static StackPane ensureWrapperStackPane(Scene scene) {
        Parent oldRoot = scene.getRoot();
        if (oldRoot.getProperties().get(WRAPPER_MARK_KEY) != null) {
            return (StackPane) oldRoot;
        }
        StackPane wrapper = new StackPane();
        wrapper.getProperties().put(WRAPPER_MARK_KEY, Boolean.TRUE);
        wrapper.getChildren().add(oldRoot);
        scene.setRoot(wrapper);
        return wrapper;
    }

    /** 懒加载设置页内容，首次打开时才初始化 Controller 和顶部关闭栏 */
    private void ensureLoaded() {
        if (initialized) return;
        initialized = true;
        try {
            controller = AbstractController.load(SettingController.class);
            Stage mainStage = AllStagesManager.getInstance().getMainStage();
            controller.init(mainStage);

            //顶部栏：左标题 + 中间占位 + 右关闭按钮
            HBox header = new HBox();
            header.getStyleClass().add("setting-drawer-header");
            header.setPrefHeight(HEADER_HEIGHT);
            header.setMinHeight(HEADER_HEIGHT);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setPadding(new Insets(0, 8, 0, 16));

            Label titleLabel = new Label(Locales.str("setting"));
            titleLabel.getStyleClass().add("setting-drawer-title");

            Pane spacer = new Pane();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label closeBtn = new Label("✕");
            closeBtn.getStyleClass().add("setting-drawer-close");
            closeBtn.setCursor(Cursor.HAND);
            closeBtn.setOnMouseClicked(e -> close());

            header.getChildren().addAll(titleLabel, spacer, closeBtn);

            //settings 内容底部留白（两层：内容 ScrollPane 内部底部 padding + 外部 VBox 底部 margin）
            Parent content = controller.getRootView();
            if (content instanceof Region) {
                var r = (Region) content;
                Insets old = r.getPadding() == null ? Insets.EMPTY : r.getPadding();
                r.setPadding(new Insets(old.getTop(), old.getRight(),
                        old.getBottom() + BOTTOM_PADDING, old.getLeft()));
            }

            VBox drawerContent = new VBox(header, content);
            VBox.setVgrow(content, Priority.ALWAYS);
            VBox.setMargin(content, new Insets(0, 0, CONTENT_BOTTOM_MARGIN, 0));
            drawerBox.getChildren().add(drawerContent);
        } catch (Exception e) {
            Log.e("SettingDrawer load error", e);
        }
    }

    public void open() {
        if (shown) return;
        ensureLoaded();
        shown = true;

        mask.setVisible(true);
        drawerBox.setVisible(true);
        drawerBox.setTranslateX(DRAWER_WIDTH);
        mask.setOpacity(0);

        var slide = new TranslateTransition(OPEN_DURATION, drawerBox);
        slide.setFromX(DRAWER_WIDTH);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        var fade = new FadeTransition(OPEN_DURATION, mask);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        escHandler = e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
            }
        };
        if (host.getScene() != null) {
            host.getScene().addEventFilter(KeyEvent.KEY_PRESSED, escHandler);
        }

        slide.play();
        fade.play();
    }

    public void close() {
        if (!shown) return;
        shown = false;

        var slide = new TranslateTransition(CLOSE_DURATION, drawerBox);
        slide.setFromX(drawerBox.getTranslateX());
        slide.setToX(DRAWER_WIDTH);
        slide.setInterpolator(Interpolator.EASE_IN);
        slide.setOnFinished(e -> {
            drawerBox.setVisible(false);
            mask.setVisible(false);
        });

        var fade = new FadeTransition(CLOSE_DURATION, mask);
        fade.setFromValue(mask.getOpacity());
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);

        if (escHandler != null && host.getScene() != null) {
            host.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, escHandler);
            escHandler = null;
        }

        slide.play();
        fade.play();
    }

    public void toggle() {
        if (shown) close();
        else open();
    }

    public boolean isShown() {
        return shown;
    }
}

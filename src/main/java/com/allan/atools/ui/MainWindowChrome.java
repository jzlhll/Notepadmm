package com.allan.atools.ui;

import javafx.event.EventTarget;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** 主窗口自绘标题栏的移动、缩放与窗口按钮行为。 */
public final class MainWindowChrome {
    private static final double RESIZE_MARGIN = 5;
    private static final double WINDOW_CLIP_ARC = 32;

    private final Stage stage;
    private final Region root;
    private final Node header;

    private Cursor resizeCursor = Cursor.DEFAULT;
    private boolean resizing;
    private boolean moving;
    private boolean maximized;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;
    private double pressedScreenX;
    private double pressedScreenY;
    private double pressedStageX;
    private double pressedStageY;
    private double pressedStageWidth;
    private double pressedStageHeight;

    public MainWindowChrome(Stage stage, Region root, Node header,
                            Node closeButton, Node minimizeButton, Node maximizeButton) {
        this.stage = stage;
        this.root = root;
        this.header = header;

        Rectangle windowClip = new Rectangle();
        windowClip.widthProperty().bind(root.widthProperty());
        windowClip.heightProperty().bind(root.heightProperty());
        windowClip.setArcWidth(WINDOW_CLIP_ARC);
        windowClip.setArcHeight(WINDOW_CLIP_ARC);
        root.setClip(windowClip);

        closeButton.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                event.consume();
            }
        });
        minimizeButton.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                stage.setIconified(true);
                event.consume();
            }
        });
        maximizeButton.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                toggleMaximized();
                event.consume();
            }
        });

        header.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onHeaderPressed);
        header.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onHeaderDragged);
        header.addEventFilter(MouseEvent.MOUSE_CLICKED, this::onHeaderClicked);
        root.addEventFilter(MouseEvent.MOUSE_MOVED, this::onRootMoved);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onRootPressed);
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onRootDragged);
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            resizing = false;
            moving = false;
        });
    }

    private void onHeaderPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || isHeaderAction(event.getTarget())) {
            return;
        }
        if (maximized) {
            restoreForDrag(event);
        } else if (stage.isMaximized()) {
            stage.setMaximized(false);
        }
        rememberStageBounds(event);
        moving = true;
    }

    private void onHeaderDragged(MouseEvent event) {
        if (!moving || resizing || stage.isFullScreen()) {
            return;
        }
        stage.setX(pressedStageX + event.getScreenX() - pressedScreenX);
        stage.setY(pressedStageY + event.getScreenY() - pressedScreenY);
        event.consume();
    }

    private void onHeaderClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2
                && !isHeaderAction(event.getTarget())) {
            toggleMaximized();
            event.consume();
        }
    }

    private void onRootMoved(MouseEvent event) {
        if (isMaximized() || stage.isFullScreen() || !stage.isResizable()) {
            resizeCursor = Cursor.DEFAULT;
        } else {
            resizeCursor = resolveResizeCursor(event.getSceneX(), event.getSceneY());
        }
        root.setCursor(resizeCursor);
    }

    private void onRootPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || Cursor.DEFAULT.equals(resizeCursor)) {
            return;
        }
        rememberStageBounds(event);
        resizing = true;
        moving = false;
        event.consume();
    }

    private void onRootDragged(MouseEvent event) {
        if (!resizing || isMaximized() || stage.isFullScreen()) {
            return;
        }

        double deltaX = event.getScreenX() - pressedScreenX;
        double deltaY = event.getScreenY() - pressedScreenY;
        double minWidth = Math.max(stage.getMinWidth(), 1);
        double minHeight = Math.max(stage.getMinHeight(), 1);

        if (Cursor.E_RESIZE.equals(resizeCursor)
                || Cursor.NE_RESIZE.equals(resizeCursor)
                || Cursor.SE_RESIZE.equals(resizeCursor)) {
            stage.setWidth(Math.max(minWidth, pressedStageWidth + deltaX));
        }
        if (Cursor.S_RESIZE.equals(resizeCursor)
                || Cursor.SE_RESIZE.equals(resizeCursor)
                || Cursor.SW_RESIZE.equals(resizeCursor)) {
            stage.setHeight(Math.max(minHeight, pressedStageHeight + deltaY));
        }
        if (Cursor.W_RESIZE.equals(resizeCursor)
                || Cursor.NW_RESIZE.equals(resizeCursor)
                || Cursor.SW_RESIZE.equals(resizeCursor)) {
            double width = Math.max(minWidth, pressedStageWidth - deltaX);
            stage.setX(pressedStageX + pressedStageWidth - width);
            stage.setWidth(width);
        }
        if (Cursor.N_RESIZE.equals(resizeCursor)
                || Cursor.NE_RESIZE.equals(resizeCursor)
                || Cursor.NW_RESIZE.equals(resizeCursor)) {
            double height = Math.max(minHeight, pressedStageHeight - deltaY);
            stage.setY(pressedStageY + pressedStageHeight - height);
            stage.setHeight(height);
        }
        event.consume();
    }

    private Cursor resolveResizeCursor(double x, double y) {
        boolean left = x <= RESIZE_MARGIN;
        boolean right = x >= root.getWidth() - RESIZE_MARGIN;
        boolean top = y <= RESIZE_MARGIN;
        boolean bottom = y >= root.getHeight() - RESIZE_MARGIN;

        if (left && top) return Cursor.NW_RESIZE;
        if (right && top) return Cursor.NE_RESIZE;
        if (left && bottom) return Cursor.SW_RESIZE;
        if (right && bottom) return Cursor.SE_RESIZE;
        if (left) return Cursor.W_RESIZE;
        if (right) return Cursor.E_RESIZE;
        if (top) return Cursor.N_RESIZE;
        if (bottom) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }

    private void rememberStageBounds(MouseEvent event) {
        pressedScreenX = event.getScreenX();
        pressedScreenY = event.getScreenY();
        pressedStageX = stage.getX();
        pressedStageY = stage.getY();
        pressedStageWidth = stage.getWidth();
        pressedStageHeight = stage.getHeight();
    }

    private void toggleMaximized() {
        if (maximized) {
            restoreWindow();
        } else {
            restoreX = stage.getX();
            restoreY = stage.getY();
            restoreWidth = stage.getWidth();
            restoreHeight = stage.getHeight();

            var screens = Screen.getScreensForRectangle(
                    stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
            Rectangle2D bounds = screen.getVisualBounds();
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            maximized = true;
        }
    }

    private boolean isMaximized() {
        return maximized || stage.isMaximized();
    }

    private void restoreWindow() {
        stage.setX(restoreX);
        stage.setY(restoreY);
        stage.setWidth(restoreWidth);
        stage.setHeight(restoreHeight);
        maximized = false;
    }

    private void restoreForDrag(MouseEvent event) {
        double widthRatio = event.getSceneX() / stage.getWidth();
        if (widthRatio < 0) {
            widthRatio = 0;
        } else if (widthRatio > 1) {
            widthRatio = 1;
        }
        maximized = false;
        stage.setWidth(restoreWidth);
        stage.setHeight(restoreHeight);
        stage.setX(event.getScreenX() - restoreWidth * widthRatio);
        stage.setY(event.getScreenY() - event.getSceneY());
    }

    private boolean isHeaderAction(EventTarget target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        while (node != null) {
            if (node.getStyleClass().contains("window-header-action")) {
                return true;
            }
            Parent parent = node.getParent();
            if (node == header || parent == null) {
                break;
            }
            node = parent;
        }
        return false;
    }
}

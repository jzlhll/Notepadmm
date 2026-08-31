package com.allan.atools.ui;

import javafx.collections.ListChangeListener;
import javafx.event.EventTarget;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** 主窗口自绘标题栏的移动、缩放与窗口按钮行为。 */
public final class MainWindowChrome {
    private static final double RESIZE_MARGIN = 5;
    private static final double WINDOW_POSITION_MARGIN = 200;

    private final Stage stage;
    private final Region root;
    private final Node header;

    private DragMode dragMode = DragMode.NONE;
    private WindowBounds restoreBounds;
    private boolean maximized;
    private double pressedScreenX;
    private double pressedScreenY;
    private WindowBounds pressedBounds;

    public MainWindowChrome(Stage stage, Region root, Node header,
                            Node closeButton, Node minimizeButton, Node maximizeButton) {
        this.stage = stage;
        this.root = root;
        this.header = header;

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

        root.addEventFilter(MouseEvent.MOUSE_MOVED, this::onMouseMoved);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> finishDrag());
        root.addEventFilter(MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> ensureWindowVisible());
        Screen.getScreens().addListener((ListChangeListener<Screen>) change -> onScreensChanged());
    }

    private void onMouseMoved(MouseEvent event) {
        if (isInteractionBlocked() || !stage.isResizable()) {
            root.setCursor(Cursor.DEFAULT);
            return;
        }
        root.setCursor(resolveResizeMode(event.getSceneX(), event.getSceneY()).cursor);
    }

    private void onMousePressed(MouseEvent event) {
        finishDrag();
        if (event.getButton() != MouseButton.PRIMARY || isInteractionBlocked()) {
            return;
        }

        if (stage.isResizable()) {
            dragMode = resolveResizeMode(event.getSceneX(), event.getSceneY());
        }
        if (dragMode == DragMode.NONE && isDraggableHeaderTarget(event.getTarget())) {
            dragMode = DragMode.MOVE;
        }
        if (dragMode == DragMode.NONE) {
            return;
        }

        pressedScreenX = event.getScreenX();
        pressedScreenY = event.getScreenY();
        pressedBounds = currentBounds();
        event.consume();
    }

    private void onMouseDragged(MouseEvent event) {
        if (dragMode == DragMode.NONE || isInteractionBlocked()) {
            finishDrag();
            return;
        }
        if (!event.isPrimaryButtonDown()) {
            finishDrag();
            return;
        }

        if (dragMode == DragMode.MOVE) {
            moveWindow(event);
        } else {
            resizeWindow(event);
        }
        event.consume();
    }

    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY
                || event.getClickCount() != 2
                || !event.isStillSincePress()
                || !isDraggableHeaderTarget(event.getTarget())
                || resolveResizeMode(event.getSceneX(), event.getSceneY()) != DragMode.NONE) {
            return;
        }
        toggleMaximized();
        event.consume();
    }

    private void moveWindow(MouseEvent event) {
        double newX = pressedBounds.x() + event.getScreenX() - pressedScreenX;
        double newY = pressedBounds.y() + event.getScreenY() - pressedScreenY;
        Rectangle2D bounds = screenForPosition(
                newX, newY, event.getScreenX(), event.getScreenY()).getBounds();
        double maxX = bounds.getMaxX() - WINDOW_POSITION_MARGIN;
        double maxY = bounds.getMaxY() - WINDOW_POSITION_MARGIN;
        if (maxX < bounds.getMinX()) {
            maxX = bounds.getMinX();
        }
        if (maxY < bounds.getMinY()) {
            maxY = bounds.getMinY();
        }
        if (newX < bounds.getMinX()) {
            newX = bounds.getMinX();
        } else if (newX > maxX) {
            newX = maxX;
        }
        if (newY < bounds.getMinY()) {
            newY = bounds.getMinY();
        } else if (newY > maxY) {
            newY = maxY;
        }
        stage.setX(newX);
        stage.setY(newY);
    }

    private void resizeWindow(MouseEvent event) {
        double deltaX = event.getScreenX() - pressedScreenX;
        double deltaY = event.getScreenY() - pressedScreenY;
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            return;
        }

        double left = pressedBounds.x();
        double right = pressedBounds.x() + pressedBounds.width();
        double top = pressedBounds.y();
        double bottom = pressedBounds.y() + pressedBounds.height();
        if (dragMode.left) {
            left += deltaX;
        } else if (dragMode.right) {
            right += deltaX;
        }
        if (dragMode.top) {
            top += deltaY;
        } else if (dragMode.bottom) {
            bottom += deltaY;
        }

        if (right - left < stage.getMinWidth()) {
            if (dragMode.left) {
                left = right - stage.getMinWidth();
            } else {
                right = left + stage.getMinWidth();
            }
        }
        if (bottom - top < stage.getMinHeight()) {
            if (dragMode.top) {
                top = bottom - stage.getMinHeight();
            } else {
                bottom = top + stage.getMinHeight();
            }
        }

        if (dragMode.left) {
            stage.setX(left);
        }
        if (dragMode.top) {
            stage.setY(top);
        }
        if (dragMode.left || dragMode.right) {
            stage.setWidth(right - left);
        }
        if (dragMode.top || dragMode.bottom) {
            stage.setHeight(bottom - top);
        }
    }

    private DragMode resolveResizeMode(double x, double y) {
        boolean left = x >= 0 && x < RESIZE_MARGIN;
        boolean right = x <= root.getWidth() && x > root.getWidth() - RESIZE_MARGIN;
        boolean top = y >= 0 && y < RESIZE_MARGIN;
        boolean bottom = y <= root.getHeight() && y > root.getHeight() - RESIZE_MARGIN;

        if (left && top) return DragMode.NORTH_WEST;
        if (right && top) return DragMode.NORTH_EAST;
        if (left && bottom) return DragMode.SOUTH_WEST;
        if (right && bottom) return DragMode.SOUTH_EAST;
        if (left) return DragMode.WEST;
        if (right) return DragMode.EAST;
        if (top) return DragMode.NORTH;
        if (bottom) return DragMode.SOUTH;
        return DragMode.NONE;
    }

    private void toggleMaximized() {
        finishDrag();
        root.setCursor(Cursor.DEFAULT);
        if (maximized) {
            maximized = false;
            if (restoreBounds != null) {
                applyBounds(restoreBounds);
                restoreBounds = null;
            }
            ensureWindowVisible();
            return;
        }

        ensureWindowVisible();
        restoreBounds = currentBounds();
        maximized = true;
        Rectangle2D bounds = screenForWindow().getVisualBounds();
        applyBounds(new WindowBounds(
                bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()));
    }

    private void onScreensChanged() {
        finishDrag();
        if (maximized) {
            Rectangle2D bounds = screenForWindow().getVisualBounds();
            applyBounds(new WindowBounds(
                    bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()));
        } else {
            ensureWindowVisible();
        }
    }

    private void ensureWindowVisible() {
        if (maximized || stage.isFullScreen()) {
            return;
        }

        Screen screen = screenContaining(stage.getX(), stage.getY());
        if (screen == null) {
            screen = screenForWindow();
        }
        Rectangle2D bounds = screen.getBounds();
        double newX = stage.getX();
        double newY = stage.getY();
        double maxX = bounds.getMaxX() - WINDOW_POSITION_MARGIN;
        double maxY = bounds.getMaxY() - WINDOW_POSITION_MARGIN;
        if (maxX < bounds.getMinX()) {
            maxX = bounds.getMinX();
        }
        if (maxY < bounds.getMinY()) {
            maxY = bounds.getMinY();
        }
        if (!Double.isFinite(newX) || newX < bounds.getMinX()) {
            newX = bounds.getMinX();
        } else if (newX > maxX) {
            newX = maxX;
        }

        if (!Double.isFinite(newY) || newY < bounds.getMinY()) {
            newY = bounds.getMinY();
        } else if (newY > maxY) {
            newY = maxY;
        }
        if (Double.compare(stage.getX(), newX) != 0) {
            stage.setX(newX);
        }
        if (Double.compare(stage.getY(), newY) != 0) {
            stage.setY(newY);
        }
    }

    private Screen screenContaining(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return null;
        }
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            if (x >= bounds.getMinX() && x < bounds.getMaxX()
                    && y >= bounds.getMinY() && y < bounds.getMaxY()) {
                return screen;
            }
        }
        return null;
    }

    private Screen screenForPosition(double x, double y, double pointerX, double pointerY) {
        Screen screen = screenContaining(x, y);
        if (screen != null) {
            return screen;
        }
        var screens = Screen.getScreensForRectangle(pointerX, pointerY, 1, 1);
        return screens.isEmpty() ? screenForWindow() : screens.get(0);
    }

    private Screen screenForWindow() {
        double x = stage.getX();
        double y = stage.getY();
        double width = stage.getWidth();
        double height = stage.getHeight();
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            return Screen.getPrimary();
        }

        var screens = Screen.getScreensForRectangle(x, y, width, height);
        if (screens.isEmpty()) {
            return Screen.getPrimary();
        }

        Screen result = screens.get(0);
        double resultArea = intersectionArea(result.getBounds(), x, y, width, height);
        for (int i = 1; i < screens.size(); i++) {
            Screen screen = screens.get(i);
            double area = intersectionArea(screen.getBounds(), x, y, width, height);
            if (area > resultArea) {
                result = screen;
                resultArea = area;
            }
        }
        return result;
    }

    private double intersectionArea(Rectangle2D screenBounds,
                                    double x, double y, double width, double height) {
        double left = x > screenBounds.getMinX() ? x : screenBounds.getMinX();
        double top = y > screenBounds.getMinY() ? y : screenBounds.getMinY();
        double right = x + width < screenBounds.getMaxX() ? x + width : screenBounds.getMaxX();
        double bottom = y + height < screenBounds.getMaxY() ? y + height : screenBounds.getMaxY();
        return right > left && bottom > top ? (right - left) * (bottom - top) : 0;
    }

    private boolean isInteractionBlocked() {
        return maximized || stage.isFullScreen();
    }

    private boolean isDraggableHeaderTarget(EventTarget target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        while (node != null) {
            if (node.getStyleClass().contains("window-header-action")) {
                return false;
            }
            if (node == header) {
                return true;
            }
            Parent parent = node.getParent();
            if (node == root || parent == null) {
                return false;
            }
            node = parent;
        }
        return false;
    }

    private WindowBounds currentBounds() {
        return new WindowBounds(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    private void applyBounds(WindowBounds bounds) {
        stage.setX(bounds.x());
        stage.setY(bounds.y());
        stage.setWidth(bounds.width());
        stage.setHeight(bounds.height());
    }

    private void finishDrag() {
        dragMode = DragMode.NONE;
        pressedBounds = null;
    }

    private record WindowBounds(double x, double y, double width, double height) {
    }

    private enum DragMode {
        NONE(false, false, false, false, Cursor.DEFAULT),
        MOVE(false, false, false, false, Cursor.DEFAULT),
        NORTH(false, false, true, false, Cursor.N_RESIZE),
        NORTH_EAST(false, true, true, false, Cursor.NE_RESIZE),
        EAST(false, true, false, false, Cursor.E_RESIZE),
        SOUTH_EAST(false, true, false, true, Cursor.SE_RESIZE),
        SOUTH(false, false, false, true, Cursor.S_RESIZE),
        SOUTH_WEST(true, false, false, true, Cursor.SW_RESIZE),
        WEST(true, false, false, false, Cursor.W_RESIZE),
        NORTH_WEST(true, false, true, false, Cursor.NW_RESIZE);

        private final boolean left;
        private final boolean right;
        private final boolean top;
        private final boolean bottom;
        private final Cursor cursor;

        DragMode(boolean left, boolean right, boolean top, boolean bottom, Cursor cursor) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.cursor = cursor;
        }
    }
}

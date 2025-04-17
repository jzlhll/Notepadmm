package com.allan.uilibs.controls;

import com.allan.baseparty.Action;
import javafx.event.Event;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;

/**
 * 可拖拽滚动面板
 */
public class Drag2ScrollPane extends ScrollPane {
    /** 是否正在拖拽 */
    private boolean isDragging;
    private double startX;
    private double startY;
    private double startHvalue;
    private double startVvalue;

    public Drag2ScrollPane() {
    }

    public Action<Event> clickAction;

    public void addDragEvent() {
        if (getContent() != null) {
            var content = getContent();
            content.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                startX = event.getSceneX();
                startY = event.getSceneY();
                startHvalue = getHvalue();
                startVvalue = getVvalue();
                isDragging = false;
                if (clickAction != null) {
                    clickAction.invoke(event);
                }
            });

            content.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
                if (event.isPrimaryButtonDown()) {
                    double moveX = startX - event.getSceneX();
                    double moveY = startY - event.getSceneY();
                    setHvalue(startHvalue + moveX / (content.getLayoutBounds().getWidth() - getWidth()));
                    setVvalue(startVvalue + moveY / (content.getLayoutBounds().getHeight() - getHeight()));
                    isDragging = true;
                }
            });
        }
    }

    public boolean getIsDragging() {
        return isDragging;
    }
}

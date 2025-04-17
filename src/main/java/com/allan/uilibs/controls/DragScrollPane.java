package com.allan.uilibs.controls;

import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

/**
 * 可拖拽滚动面板
 */
public class DragScrollPane extends ScrollPane {
    private static class RegionOrImageView {
        Region region;
        ImageView imageView;
    }

    /** 是否正在拖拽 */
    private boolean mIsDragging;
    private double startX;
    private double startY;
    private double startHvalue;
    private double startVvalue;

    private RegionOrImageView content;

    public DragScrollPane() {
    }

    public DragScrollPane(Region content) {
        setContentWrap(content);
    }

    public DragScrollPane(ImageView content) {
        setContentWrap(content);
    }

    public void setContentWrap(Region content) {
        var ri = new RegionOrImageView();
        ri.region = content;
        this.content = ri;
        setContent(content);
        addDragEvent();
    }

    public void setContentWrap(ImageView content) {
        var ri = new RegionOrImageView();
        ri.imageView = content;
        this.content = ri;
        setContent(content);
        addDragEvent();
    }

    private void addDragEvent() {
        if (content.imageView != null) {
            content.imageView.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                startX = event.getSceneX();
                startY = event.getSceneY();
                startHvalue = getHvalue();
                startVvalue = getVvalue();
                mIsDragging = false;
            });

            content.imageView.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
                if (event.isPrimaryButtonDown()) {
                    double moveX = startX - event.getSceneX();
                    double moveY = startY - event.getSceneY();
                    setHvalue(startHvalue + moveX / (content.imageView.getFitWidth() - getWidth()));
                    setVvalue(startVvalue + moveY / (content.imageView.getFitHeight() - getHeight()));
                    mIsDragging = true;
                }
            });
        }

        if (content.region != null) {
            content.region.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                startX = event.getSceneX();
                startY = event.getSceneY();
                startHvalue = getHvalue();
                startVvalue = getVvalue();
                mIsDragging = false;
            });

            content.region.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
                if (event.isPrimaryButtonDown()) {
                    double moveX = startX - event.getSceneX();
                    double moveY = startY - event.getSceneY();
                    setHvalue(startHvalue + moveX / (content.region.getWidth() - getWidth()));
                    setVvalue(startVvalue + moveY / (content.region.getHeight() - getHeight()));
                    mIsDragging = true;
                }
            });
        }
    }

    public boolean mIsDragging() {
        return mIsDragging;
    }
}

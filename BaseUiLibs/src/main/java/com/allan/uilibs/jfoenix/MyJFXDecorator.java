package com.allan.uilibs.jfoenix;

import com.jfoenix.controls.JFXDecorator;
import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Windows 窗口装饰器，保留现有入口并隐藏全屏按钮。
 */
public final class MyJFXDecorator extends JFXDecorator {
    public MyJFXDecorator(Stage stage, Node node) {
        this(stage, node, true, true);
    }

    public MyJFXDecorator(Stage stage, Node node, boolean max, boolean min) {
        super(stage, node, false, max, min);
    }
}

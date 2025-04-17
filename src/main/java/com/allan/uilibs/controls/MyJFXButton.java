package com.allan.uilibs.controls;

import com.jfoenix.controls.JFXButton;
import javafx.scene.Node;

public class MyJFXButton extends JFXButton {
    public Object ex;
    public MyJFXButton() {
        super();
    }

    public MyJFXButton(String text, Node graphic) {
        super(text, graphic);
    }
}

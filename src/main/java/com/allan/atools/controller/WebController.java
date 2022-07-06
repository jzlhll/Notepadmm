package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class WebController extends AbstractController {
    public Label myName;
    public Label myDesc;

    @Override
    public void init(Stage stage) {
        super.init(stage);
    }
}

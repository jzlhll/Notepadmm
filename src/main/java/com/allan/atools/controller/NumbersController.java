package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.jfoenix.controls.JFXButton;
import javafx.scene.control.Label;
import javafx.stage.Stage;

@XmlPaths(paths = {"pages", "content_numbers.fxml"})
public final class NumbersController extends AbstractController {


    public Label randomLabel;

    public void init(Stage stage) {
        super.init(stage);
        System.out.println("tiem: " + System.currentTimeMillis());
    }
}

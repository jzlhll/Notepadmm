package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.ui.listener.DisAndEnableChangeListener;
import com.allan.atools.utils.DigitsLimit;
import com.allan.atools.utils.Log;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXColorPicker;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

@XmlPaths(paths = {"pages", "content_asm.fxml"})
public final class ASMController extends AbstractController {


    public JFXButton enterBtn;

    public void init(Stage stage) {
        super.init(stage);
        System.out.println("tiem: " + System.currentTimeMillis());
        enterBtn.setOnMouseClicked(mouseEvent -> {

        });
    }
}

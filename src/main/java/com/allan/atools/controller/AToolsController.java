package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.tools.AToolsControllerInitial;
import com.allan.atools.ui.IconfontCreator;
import com.allan.atools.utils.Log;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

@XmlPaths(paths = {"main", "atools.fxml"})
public final class AToolsController extends AbstractController {
    @FXML
    public ScrollPane pageHolderPanel;
    @FXML
    public HBox mainSplitPane;
    @FXML
    public AnchorPane leftMenuHolder;

    private AToolsControllerInitial mInit;
    public void setMainControllerInit(AToolsControllerInitial init) {
        mInit = init;
    }

    public void init(Stage stage) {
        super.init(stage);
        this.leftMenuListView.setItems(mInit.getLeftMenu());

        this.leftMenuListView.getSelectionModel().select(0);
        this.leftMenuListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            int index = this.leftMenuListView.getSelectionModel().getSelectedIndex();
            System.out.println("newValue : " + newValue + " " + index);
            mInit.replacePageByIndex(index);
        });

        {
            //搜索按钮
            JFXButton searchBtn = IconfontCreator.createJFXButton("discount", 18);
            searchBtn.setLayoutX(10.0D);
            searchBtn.setLayoutY(520.0D);
            searchBtn.setPrefWidth(70.0D);
            searchBtn.setPrefHeight(30.0D);
            searchBtn.setMaxHeight(33.0D);
            BackgroundFill backgroundFill = new BackgroundFill(Paint.valueOf("#8FBC8F"), new CornerRadii(8.0D), Insets.EMPTY);
            Background background = new Background(backgroundFill);
            searchBtn.setBackground(background);

            this.leftMenuHolder.getChildren().add(searchBtn);
        }

        stage.setWidth(1100);
        stage.setHeight(680);
        stage.setResizable(false);
    }

    @FXML
    public JFXListView<String> leftMenuListView;

    @FXML
    public void onAction(ActionEvent actionEvent) {
        Log.d("xxx" + actionEvent);
    }

    public void showGlobalDialog() {
    }

}

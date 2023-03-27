package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.tools.AToolsControllerInitial;
import com.jfoenix.controls.JFXListView;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

@XmlPaths(paths = {"main", "atools.fxml"})
public final class AToolsController extends AbstractController {
    public static final int WIDTH = 1100;
    public static final int HEIGHT = 680;

    public static final int OFFSET_Y = 28 + 5;

    public static int pageWidth;

    @FXML
    public HBox mainSplitPane;
    @FXML
    public Separator separator;
    public ScrollPane pageHolderPanel;

    @FXML
    public JFXListView<String> leftMenuListView;

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

        stage.setWidth(WIDTH);
        stage.setHeight(HEIGHT);
        stage.setResizable(false);

        mainSplitPane.setPrefWidth(WIDTH);
        mainSplitPane.setPrefHeight(HEIGHT);
        separator.setPrefHeight(HEIGHT);
        leftMenuListView.setPrefWidth(120);

        pageWidth = WIDTH - 120 - 15;
    }
}

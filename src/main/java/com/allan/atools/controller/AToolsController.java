package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.tools.AToolsControllerInitial;
import com.jfoenix.controls.JFXListView;
import javafx.beans.property.SimpleLongProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

@XmlPaths(paths = {"main", "atools.fxml"})
public final class AToolsController extends AbstractController {
    /**
     * a tools: xy 变化
     */
    public final SimpleLongProperty sizeXyChangedProp = new SimpleLongProperty();

    @FXML
    public HBox mainSplitPane;
    @FXML
    public Separator separator;

    @FXML
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
        stage.setResizable(true);

        sizeXyChangedProp.addListener((observable, oldValue, newValue) -> {
            var s = getStage();
            if (s != null) {
                mainSplitPane.setPrefWidth(s.getWidth());
                mainSplitPane.setPrefHeight(s.getHeight());
                separator.setPrefHeight(s.getHeight());
            }
        });

        leftMenuListView.setPrefWidth(120);
    }
}

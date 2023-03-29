package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.tools.moduleffmpeg.FfmpegSettings;
import com.allan.atools.tools.moduleffmpeg.FfmpegTable;
import com.allan.atools.ui.IconfontCreator;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTabPane;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

@XmlPaths(paths = {"pages", "ffmpeg.fxml"})
public class FfmpegController extends AbstractController {

    public Label ffmpegDirLabel;
    public JFXButton selectFfmpegDir;
    public JFXButton coverSureVideoBtn;
    public Label coverSureVideoLabel;
    public VBox listFileFlowPane;
    public JFXButton refreshFileListBtn;
    public JFXSlider compressCrfSlide;
    public Label compressCrfLabel;
    public JFXSlider compressSpeedSlide;
    public JFXButton compressStartBtn;
    public Label compressSpeedLabel;
    public JFXButton openToExploreBtn;
    public Label compressStartSureFileLabel;
    public JFXButton selectADirBtn;
    public JFXTabPane tabPane;
    public SplitPane splitPane;
    public Label selectADirLabel;
    public Label combineCoverLabel;
    public JFXButton coverTotalSecondMinusBtn;
    public Label coverTotalSecondLabel;
    public JFXButton coverTotalSecondPlusBtn;
    public JFXButton coverStartSecondPlusBtn;
    public Label coverStartSecondLabel;
    public JFXButton coverStartSecondMinusBtn;

    public FfmpegSettings mSetting;
    public Label coverToastLabel;
    public VBox rightListFileBox;
    public HBox rightHBox;
    public AnchorPane selectADirBtnHost;
    public AnchorPane selectFfmpegDirHost;
    public Label compressStartHint;
    public Label combineCoverHint;
    public Label selectADirLabel2;
    public JFXButton combineCover2Btn;
    public Label videoInfoLabel;
    private FfmpegTable mTable;

    public void init(Stage stage) {
        super.init(stage);

        openToExploreBtn = IconfontCreator.createJFXButton("filefolder", 18, "To Explorer...");
        rightHBox.getChildren().add(openToExploreBtn);
        openToExploreBtn.getStyleClass().add("custom-jfx-button-raised");

        selectADirBtn = IconfontCreator.createJFXButton("filefolder", 18, "Select folder...");
        selectADirBtnHost.getChildren().add(selectADirBtn);
        selectADirBtn.getStyleClass().add("custom-jfx-button-raised");

        selectFfmpegDir = IconfontCreator.createJFXButton("filefolder", 18, "Select folder...");
        selectFfmpegDirHost.getChildren().add(selectFfmpegDir);
        selectFfmpegDir.getStyleClass().add("custom-jfx-button-raised");

        mSetting = new FfmpegSettings(this);
        mTable = new FfmpegTable(this);
        mSetting.getOnFileSelectedListenerArray().add(mTable);

        splitPane.setPrefWidth(AToolsController.pageWidth);
        splitPane.setPrefHeight(AToolsController.HEIGHT - AToolsController.OFFSET_Y);

        selectADirLabel2.setWrapText(true);
        videoInfoLabel.setWrapText(true);

        tabPane.setPrefWidth(640);
        tabPane.setMinWidth(600);
        tabPane.setMaxWidth(660);
    }

}

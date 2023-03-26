package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.tools.moduleffmpeg.FfmpegSettings;
import com.allan.atools.tools.moduleffmpeg.FfmpegTable;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTabPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

@XmlPaths(paths = {"pages", "ffmpeg.fxml"})
public class FfmpegController extends AbstractController {

    public Label ffmpegDirLabel;
    public Hyperlink selectFfmpegDir;
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
    public JFXButton combineCoverBtn;
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
    private FfmpegTable mTable;

    public void init(Stage stage) {
        super.init(stage);

        mSetting = new FfmpegSettings(this);
        mTable = new FfmpegTable(this);
        mSetting.getOnFileSelectedListenerArray().add(mTable);
    }

}

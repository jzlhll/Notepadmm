package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.tools.RleZipTester;
import com.jfoenix.controls.JFXButton;
import javafx.fxml.FXML;
import javafx.stage.Stage;

@XmlPaths(paths = {"pages", "content_test.fxml"})
public final class TestController extends AbstractController {
    @FXML
    public JFXButton group1Btn;
    @FXML
    public JFXButton group2Btn;
    @FXML
    public JFXButton group3Btn;
    @FXML
    public JFXButton group4Btn;
    @FXML
    public JFXButton group5Btn;

    @Override
    public void init(Stage stage) {
        super.init(stage);
        // 游程编码 + 压缩属 CPU 任务，放子线程执行；结果通过标准输出打印
        group1Btn.setOnMouseClicked(e -> new Thread(RleZipTester::runGroup1).start());
        group2Btn.setOnMouseClicked(e -> new Thread(RleZipTester::runGroup2).start());
        group3Btn.setOnMouseClicked(e -> new Thread(RleZipTester::runGroup3).start());
        group4Btn.setOnMouseClicked(e -> new Thread(RleZipTester::runGroup4).start());
        group5Btn.setOnMouseClicked(e -> new Thread(RleZipTester::runGroup5).start());
    }
}

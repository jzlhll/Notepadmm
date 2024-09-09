package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextArea;
import com.allan.atools.tools.modulejson.IJsonFormat;
import com.allan.atools.tools.modulejson.JsonFormatLog;
import com.allan.atools.utils.Log;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import javafx.scene.control.Label;
import javafx.stage.Stage;

@XmlPaths(paths = {"pages", "content_jsonformat.fxml"})
public final class JsonFormatController extends AbstractController {
    public JFXTextArea outReceiverText;
    public JFXButton ignoreEnterTextBtn;
    public JFXButton removePercentBtn;
    public JFXButton addPercentBtn;
    public JFXButton removeFanxieBtn;
    public JFXButton restoreBackupButton;
    private String mBackup;
    private IJsonFormat mFormat;

    public void init(Stage stage) {
        super.init(stage);

        if (this.mFormat == null) {
            this.mFormat = new JsonFormatLog();
        }

        this.ignoreEnterTextBtn.setOnMouseClicked(event -> {
            var editText = this.outReceiverText.getText();
            this.mBackup = editText;

            String str = this.mFormat.removeEnter(editText);
            String fmtStr = this.mFormat.format(str);
            this.outReceiverText.setText(fmtStr);
        });

        this.removeFanxieBtn.setOnMouseClicked(event -> {
            var editText = this.outReceiverText.getText();
            this.mBackup = editText;

            var str = this.mFormat.removeFanxieExtraQuote(editText);
            this.outReceiverText.setText(str);
        });

        this.removePercentBtn.setOnMouseClicked(e -> {
            var editText = this.outReceiverText.getText();
            this.mBackup = editText;

            var s = URLDecoder.decode(editText);
            this.outReceiverText.setText(s);
        });
        this.addPercentBtn.setOnMouseClicked(e -> {
            var editText = this.outReceiverText.getText();
            this.mBackup = editText;

            var s = URLEncoder.encode(editText);
            this.outReceiverText.setText(s);
        });

        this.restoreBackupButton.setOnMouseClicked(e-> {
            String last = mBackup;
            mBackup = this.outReceiverText.getText();
            this.outReceiverText.setText(last);
        });

        assert UIContext.toolsController != null;
        UIContext.toolsController.sizeXyChangedProp.addListener((observable, oldValue, newValue) -> {
            changedEditBoxSize();
        });

        changedEditBoxSize();
    }

    private void changedEditBoxSize() {
        if (UIContext.toolsController != null && UIContext.toolsController.getStage() != null) {
            var width = UIContext.toolsController.getStage().getWidth() - 200;
            var height = UIContext.toolsController.getStage().getHeight() - 150;

            outReceiverText.setPrefWidth(width);
            outReceiverText.setPrefHeight(height);

            outReceiverText.setMaxWidth(width);
            outReceiverText.setMaxHeight(height);

            outReceiverText.setMinWidth(width);
            outReceiverText.setMinHeight(height);
            Log.d("width " + width + " height " + height);
        }
    }
}

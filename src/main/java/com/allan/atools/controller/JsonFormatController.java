package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.JFXTextField;
import com.allan.atools.tools.modulejson.IJsonFormat;
import com.allan.atools.tools.modulejson.JsonFormatLog;
import com.allan.atools.utils.Log;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.stage.Stage;
@XmlPaths(paths = {"pages", "content_jsonformat.fxml"})
public final class JsonFormatController extends AbstractController {
    public JFXTextField outReceiverText;
    public JFXTextArea outReceiverTextCvted;
    public JFXButton ignoreEnterTextBtn;
    public JFXButton removePercentBtn;
    public JFXButton addPercentBtn;
    public Label statusLabel;
    private final StringProperty statusProperty = new SimpleStringProperty();
    public JFXButton douhaoBtn;
    public JFXButton removeFanxieBtn;
    private String mInputStatus = "";
    private String mParsedStatus = "";

    private String mLastInput;
    private String mLastParsed;
    private IJsonFormat mFormat;

    public void init(Stage stage) {
        super.init(stage);
        this.statusProperty.set("状态：ready");
        this.statusLabel.textProperty().bind(this.statusProperty);

        if (this.mFormat == null) {
            this.mFormat = new JsonFormatLog();
        }

        this.outReceiverText.textProperty().addListener(observable -> {
            if (observable instanceof StringProperty) {
                StringProperty s = (StringProperty) observable;
                String last = this.mLastInput;
                String cur = s.getValue();
                this.mLastInput = cur;
                synchronized (this.statusProperty) {
                    if (cur.equals(last)) {
                        this.mInputStatus = "状态：似乎输入没有变化";
                    } else {
                        this.mInputStatus = "状态：" + Log.time() + ", 字数：" + cur.length();
                    }
                    this.statusProperty.set(this.mInputStatus + this.mInputStatus);
                }
            }
        });


   
        this.ignoreEnterTextBtn.setOnMouseClicked(event -> {
            String newjson = this.mFormat.formatWithoutEnter(this.outReceiverText.getText());

            this.outReceiverTextCvted.setText(newjson);
            this.mLastParsed = newjson;
        });

        this.removeFanxieBtn.setOnMouseClicked(event -> {
            var origin = this.outReceiverText.getText();
            origin = origin.replace("\\", "");
            this.outReceiverTextCvted.setText(origin);
        });

        this.removePercentBtn.setOnMouseClicked(e -> {
            String s = this.outReceiverText.getText();
            this.outReceiverTextCvted.setText(URLDecoder.decode(s));
        });
        this.addPercentBtn.setOnMouseClicked(e -> {
            String s = this.outReceiverTextCvted.getText();
            this.outReceiverText.setText(URLEncoder.encode(s));
        });

        this.douhaoBtn.setOnMouseClicked(e-> {
            String s = this.outReceiverText.getText();
            String[] lines = s.split("\n");
            var singles = new ArrayList<String>();
            for(var line : lines) {
                if (!line.contains(",")) {
                    singles.add(line);
                } else {
                    var temps = line.split(",");
                    singles.addAll(Arrays.asList(temps));
                }
            }
            var l = singles.stream().distinct().collect(Collectors.toList());
            String r = String.join(",", l);
            this.outReceiverTextCvted.setText(r);
        });

    }
}

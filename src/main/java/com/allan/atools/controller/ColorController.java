package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.ui.listener.DisAndEnableChangeListener;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.DigitsLimit;
import com.allan.atools.utils.Log;
import com.jfoenix.controls.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

@XmlPaths(paths = {"pages", "content_color.fxml"})
public final class ColorController extends AbstractController {
    @FXML
    public JFXTextField hexColorInput;

    public JFXTextField fR;

    public JFXTextField fB;

    public JFXTextField fG;

    public JFXTextField sA;

    public Circle circle;

    public JFXColorPicker colorPicker;

    private final SimpleObjectProperty<Paint> circleColor = new SimpleObjectProperty<>();

    private final DisAndEnableChangeListener<String> hexColorInputListener = new DisAndEnableChangeListener<>();

    private final DisAndEnableChangeListener<String> fRColorInputListener = new DisAndEnableChangeListener<>();

    private final DisAndEnableChangeListener<String> fGColorInputListener = new DisAndEnableChangeListener<>();

    private final DisAndEnableChangeListener<String> fBColorInputListener = new DisAndEnableChangeListener<>();

    private final DisAndEnableChangeListener<Color> colorPickerListener = new DisAndEnableChangeListener<>();

    public Label fALabel;

    public Button enterForRGBBtn;

    public Label exampleLabel;

    public Label exampleLabel2;

    public Label exampleLabel3;
    public FlowPane flowPane;
    public JFXButton enterForHexBtn;
    public JFXComboBox<String> chooseAlphaModeCombo;
    public Label errorInfo;

    private class OtherListenerForPicker implements DisAndEnableChangeListener.OtherControlChangeListener<Color> {
        public void onRemoveBeforeSetValue() {
            ColorController.this.hexColorInput.textProperty().removeListener(hexColorInputListener);
            ColorController.this.fR.textProperty().removeListener(fRColorInputListener);
            ColorController.this.fG.textProperty().removeListener(fGColorInputListener);
            ColorController.this.fB.textProperty().removeListener(fBColorInputListener);
        }

        public void onSetValue(Color newValueFromMe) {
            String c = colorToHex(newValueFromMe, chooseAlphaModeCombo.getSelectionModel().getSelectedIndex());
            Log.d("colorPicker changed: " + c);
            ColorController.this.hexColorInput.setText(c);
            ColorController.this.fR.setText(String.format("%.0f", newValueFromMe.getRed() * 255.0D));
            ColorController.this.fG.setText(String.format("%.0f", newValueFromMe.getGreen() * 255.0D));
            ColorController.this.fB.setText(String.format("%.0f", newValueFromMe.getBlue() * 255.0D));
            ColorController.this.circleColor.set(newValueFromMe);
        }

        public void onAfterSetValue() {
            ColorController.this.hexColorInput.textProperty().addListener(hexColorInputListener);
            ColorController.this.fR.textProperty().addListener(fRColorInputListener);
            ColorController.this.fG.textProperty().addListener(fGColorInputListener);
            ColorController.this.fB.textProperty().addListener(fBColorInputListener);
        }
    }

    private class OtherListenerForInput implements DisAndEnableChangeListener.OtherControlChangeListener<String> {
        public void onRemoveBeforeSetValue() {
            ColorController.this.colorPicker.valueProperty().removeListener(colorPickerListener);
        }

        public void onSetValue(String newValueFromMe) {
            ColorController.this.colorPicker.setValue(Color.valueOf(newValueFromMe));
        }

        public void onAfterSetValue() {
            ColorController.this.colorPicker.valueProperty().addListener(colorPickerListener);
        }
    }

    public void init(Stage stage) {
        super.init(stage);
        System.out.println("tiem: " + System.currentTimeMillis());
        this.circle.fillProperty().bind(this.circleColor);
        this.exampleLabel.textFillProperty().bind(this.circleColor);
        this.exampleLabel2.textFillProperty().bind(this.circleColor);
        this.exampleLabel3.textFillProperty().bind(this.circleColor);
        System.out.println("tiem: " + System.currentTimeMillis());

        chooseAlphaModeCombo.getItems().add("noAlpha");
        chooseAlphaModeCombo.getItems().add("Alpha0%-100%");
        chooseAlphaModeCombo.getItems().add("Alpha 0.0-1.0");
        chooseAlphaModeCombo.getItems().add("Alpha 0-255");

        chooseAlphaModeCombo.getSelectionModel().select(0);

        chooseAlphaModeCombo.getSelectionModel().selectedIndexProperty().addListener((observableValue, oldVal, newVal) -> {
            if (newVal.intValue() == 0) {
                this.fALabel.setVisible(false);
                this.sA.setVisible(false);
            } else {
                this.fALabel.setVisible(true);
                this.sA.setVisible(true);
            }
        });

        System.out.println("tiem: " + System.currentTimeMillis());

        this.enterForHexBtn.setOnMouseClicked(event -> {
            String currentWithoutEnter = this.hexColorInput.getText();
            if (currentWithoutEnter.startsWith("#")) {
                currentWithoutEnter = currentWithoutEnter.substring(1);
            }

            this.hexColorInput.setText(currentWithoutEnter);
            double opacity = 1.0;
            var alphaIndex = chooseAlphaModeCombo.getSelectionModel().getSelectedIndex();
            if (currentWithoutEnter.length() == 6 && alphaIndex == 0) {

            } else if (alphaIndex > 0 && currentWithoutEnter.length() == 8) {
                var s = currentWithoutEnter.substring(0, 2);
                opacity = Integer.parseInt(s, 16) / 255f;
                currentWithoutEnter = currentWithoutEnter.substring(2);
            } else {
                JfoenixDialogUtils.alert("错误hex参数。", "");
                return;
            }

            setColorByHex(currentWithoutEnter, opacity, alphaIndex);
        });

        this.enterForRGBBtn.setOnMouseClicked(event -> {
            double alpha = 1f;
            if (this.fR.getText().length() == 0)
                this.fR.setText("255");
            if (this.fG.getText().length() == 0)
                this.fG.setText("255");
            if (this.fB.getText().length() == 0)
                this.fB.setText("255");

            String alphaStr = sA.getText();
            if (alphaStr.endsWith("%")) {
                alphaStr = alphaStr.substring(0, alphaStr.length() - 1);
            }

            if (chooseAlphaModeCombo.getSelectionModel().getSelectedIndex() == 1) {
                alpha = Float.parseFloat(alphaStr) / 100;
            } else if (chooseAlphaModeCombo.getSelectionModel().getSelectedIndex() == 2) {
                alpha = Float.parseFloat(alphaStr);
            } else if (chooseAlphaModeCombo.getSelectionModel().getSelectedIndex() == 3) {
                alpha = Float.parseFloat(alphaStr) / 255;
            }

            double r = Double.parseDouble(this.fR.getText()) / 255.0D;
            double g = Double.parseDouble(this.fG.getText()) / 255.0D;
            double b = Double.parseDouble(this.fB.getText()) / 255.0D;
            Color c = new Color(r, g, b, alpha);
            setColorByColor(c, chooseAlphaModeCombo.getSelectionModel().getSelectedIndex());
        });
        System.out.println("tiem: " + System.currentTimeMillis());
        ThreadUtils.globalHandler().post(() -> {
            this.colorPickerListener.setListener(new OtherListenerForPicker());
            this.colorPicker.valueProperty().addListener(colorPickerListener);
            this.colorPicker.valueProperty().setValue(Color.WHITE);
            this.circleColor.set(Color.WHITE);
            //this.sA.setTextFormatter(DigitsLimit.createDigitsLimit());
            this.fR.setTextFormatter(DigitsLimit.createDigitsLimit());
            this.fG.setTextFormatter(DigitsLimit.createDigitsLimit());
            this.fB.setTextFormatter(DigitsLimit.createDigitsLimit());
            System.out.println("tiem: " + System.currentTimeMillis());
        });
    }

    private static String colorToHex(Color c, int alphaIndex) {
        int r = (int) Math.round(c.getRed() * 255.0D);
        int g = (int) Math.round(c.getGreen() * 255.0D);
        int b = (int) Math.round(c.getBlue() * 255.0D);
        if (alphaIndex > 0) {
            int o = (int) Math.round(c.getOpacity() * 255.0D);
            return String.format("%02x%02x%02x%02x", o, r, g, b);
        }
        return String.format("%02x%02x%02x", r, g, b);
    }

    private void setColorByHex(String hex, double opacity, int alphaIndex) {
        Color c;
        c = Color.web(hex, opacity);

        this.colorPicker.setValue(c);
        int r = (int) Math.round(c.getRed() * 255.0D);
        int g = (int) Math.round(c.getGreen() * 255.0D);
        int b = (int) Math.round(c.getBlue() * 255.0D);
        if (alphaIndex == 1) { //%
            double a = Math.round(c.getOpacity() / 255.0D);
            int percent = (int)(a * 100);
            this.sA.setText(percent + "%");
        } else if (alphaIndex == 2) { //0.0-1.0
            double a = Math.round(c.getOpacity() / 255.0D);
            this.sA.setText(String.format("%.2f", a));
        } else if (alphaIndex == 3) {//1-255
            int a = (int) Math.round(c.getOpacity() * 255.0D);
            this.sA.setText(String.valueOf(a));
        }
        this.fR.setText(String.valueOf(r));
        this.fG.setText(String.valueOf(g));
        this.fB.setText(String.valueOf(b));
    }

    private void setColorByColor(Color c, int alphaIndex) {
        this.colorPicker.setValue(c);
        String hex = colorToHex(c, alphaIndex);
        this.hexColorInput.setText(hex);
    }
}

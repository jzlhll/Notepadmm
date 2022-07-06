package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.ui.listener.DisAndEnableChangeListener;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.DigitsLimit;
import com.allan.atools.utils.Log;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXColorPicker;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.property.SimpleObjectProperty;
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

    public JFXToggleButton useAlphaToggle;

    public Label fALabel;

    public Label jinzhiLabel;

    public Label jinzhiAlphaLabel;

    public Button enterForRGBBtn;

    public Label exampleLabel;

    public Label exampleLabel2;

    public Label exampleLabel3;
    public FlowPane flowPane;
    public JFXButton enterForHexBtn;
    public Label sALabel;

    private class OtherListenerForPicker implements DisAndEnableChangeListener.OtherControlChangeListener<Color> {
        public void onRemoveBeforeSetValue() {
            ColorController.this.hexColorInput.textProperty().removeListener(hexColorInputListener);
            ColorController.this.fR.textProperty().removeListener(fRColorInputListener);
            ColorController.this.fG.textProperty().removeListener(fGColorInputListener);
            ColorController.this.fB.textProperty().removeListener(fBColorInputListener);
        }

        public void onSetValue(Color newValueFromMe) {
            String c = ColorController.toString(newValueFromMe, useAlphaToggle.isSelected());
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
        this.useAlphaToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            this.fALabel.setVisible(newValue);
            this.sA.setVisible(newValue);
            this.sALabel.setVisible(newValue);
            this.jinzhiAlphaLabel.setVisible(newValue);
            this.jinzhiLabel.setVisible(oldValue);
        });
        System.out.println("tiem: " + System.currentTimeMillis());

        this.enterForHexBtn.setOnMouseClicked(event -> {
            String currentWithoutEnter = this.hexColorInput.getText();
            if (currentWithoutEnter.startsWith("#")) {
                currentWithoutEnter = currentWithoutEnter.substring(1);
            }

            this.hexColorInput.setText(currentWithoutEnter);
            double opacity = 1.0;
            var isUseAlpha = useAlphaToggle.isSelected();
            if (currentWithoutEnter.length() == 6 && !isUseAlpha) {
            } else if (isUseAlpha && currentWithoutEnter.length() == 8) {
                var s = currentWithoutEnter.substring(0, 2);
                opacity = Integer.parseInt(s, 16) / 255f;
                currentWithoutEnter = currentWithoutEnter.substring(2);
            } else {
                JfoenixDialogUtils.alert("错误hex参数。", "");
                return;
            }

            setColorByHex(currentWithoutEnter, opacity, useAlphaToggle.isSelected());
        });

        this.enterForRGBBtn.setOnMouseClicked(event -> {
            if (this.sA.getText().length() == 0)
                this.sA.setText("255");
            if (this.fR.getText().length() == 0)
                this.fR.setText("255");
            if (this.fG.getText().length() == 0)
                this.fG.setText("255");
            if (this.fB.getText().length() == 0)
                this.fB.setText("255");

            String alphaStr = sA.getText();
            if (alphaStr.endsWith("%")) {
                alphaStr = alphaStr.substring(0, alphaStr.length() - 1);
                var alphaSize = Float.parseFloat(alphaStr);
                int color = (int) (alphaSize * 255);
                sA.setText("" + color);
            }
            double alpha = Integer.parseInt(this.sA.getText()) / 255.0D;
            double r = Integer.parseInt(this.fR.getText()) / 255.0D;
            double g = Integer.parseInt(this.fG.getText()) / 255.0D;
            double b = Integer.parseInt(this.fB.getText()) / 255.0D;
            Color c = new Color(r, g, b, alpha);
            setColorByColor(c, this.useAlphaToggle.isSelected());
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

    private static String toString(Color c, boolean withAlpha) {
        int r = (int) Math.round(c.getRed() * 255.0D);
        int g = (int) Math.round(c.getGreen() * 255.0D);
        int b = (int) Math.round(c.getBlue() * 255.0D);
        if (withAlpha) {
            int o = (int) Math.round(c.getOpacity() * 255.0D);
            return String.format("%02x%02x%02x%02x", o, r, g, b);
        }
        return String.format("%02x%02x%02x", r, g, b);
    }

    private void setColorByHex(String hex, double opacity, boolean withAlpha) {
        Color c;
        c = Color.web(hex, opacity);

        this.colorPicker.setValue(c);
        int r = (int) Math.round(c.getRed() * 255.0D);
        int g = (int) Math.round(c.getGreen() * 255.0D);
        int b = (int) Math.round(c.getBlue() * 255.0D);
        if (withAlpha) {
            int a = (int) Math.round(c.getOpacity() * 255.0D);
            this.sA.setText("" + a);
        }
        this.fR.setText("" + r);
        this.fG.setText("" + g);
        this.fB.setText("" + b);
    }

    private void setColorByColor(Color c, boolean withAlpha) {
        this.colorPicker.setValue(c);
        String hex = toString(c, withAlpha);
        this.hexColorInput.setText(hex);
    }
}

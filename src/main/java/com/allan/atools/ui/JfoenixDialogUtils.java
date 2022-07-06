package com.allan.atools.ui;

import com.allan.atools.Colors;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action;
import com.allan.baseparty.Action0;
import com.allan.baseparty.handler.TextUtils;
import com.jfoenix.animation.alert.JFXAlertAnimation;
import com.jfoenix.controls.JFXAlert;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

public final class JfoenixDialogUtils {
    public enum ConfirmMode {
        Cancel,
        Accept,
        Extra
    }

    public record DialogActionInfo(ConfirmMode mode, String str, Action0 action0) {}

    private static String modeToStr(ConfirmMode mode) {
        return switch (mode) {
            case Cancel -> Locales.str("cancel");
            case Accept -> Locales.str("sure");
            default -> null;
        };
    }

    private static Window mWindow;

    private static synchronized Window getWindow() {
        return mWindow;
    }

    public static synchronized void setWindow(Window window) {
        mWindow = window;
    }

    public static void alert(String head, String body) {
        alert(head, body, null, 0);
    }

    public static void alert(String head, String body, Action0 ex) {
        alert(head, body, ex, 0);
    }

    public static void alert(String head, String body, Action0 ex, int width) {
        JFXAlert alert = new JFXAlert(getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        alert.setAnimation(JFXAlertAnimation.NO_ANIMATION);
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setStyle("-fx-background-color:" + Colors.SearchBgColor.invoke() + ";");

        var headLabel = new Label(head);
        headLabel.setStyle("-fx-font-size:13;-fx-text-fill: " + Colors.ColorHeadButton.invoke() + ";");
        layout.setHeading(headLabel);
        var bod = new Label(body);
        bod.setStyle("-fx-font-size:16;-fx-text-fill: " + Colors.ColorHeadButton.invoke() + ";");
        layout.setBody(bod);

        if (width > 0) {
            layout.setPrefWidth(width);
        }
        JFXButton closeButton = new JFXButton(Locales.str("sure2"));
        closeButton.setStyle(String.format("-fx-font-size: 15px;-fx-text-fill: %s;-fx-font-weight: BOLD;-fx-padding: 0.7em 0.8em;", Colors.ColorBottomBtnHighLight.invoke()));
        closeButton.setOnAction(event -> {
            if (ex != null) {
                ex.invoke();
            }
            alert.hideWithAnimation();
        });
        layout.setActions(closeButton);
        alert.setContent(layout);
        layout.requestFocus();
        alert.show();
    }

    public static void confirm(String head, String body, int smallBodySize, int width, DialogActionInfo... infos) {
        JFXAlert alert = new JFXAlert(getWindow());

        smallBodySize = smallBodySize == 0 ? 16 : smallBodySize;
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        alert.setAnimation(JFXAlertAnimation.NO_ANIMATION);
        JFXDialogLayout layout = new JFXDialogLayout();

        layout.setStyle("-fx-background-color:" + Colors.SearchBgColor.invoke() + ";");

        var headLabel = new Label(head);
        headLabel.setStyle("-fx-font-size:" + (smallBodySize - 3) + ";-fx-text-fill: " + Colors.ColorHeadButton.invoke() + ";");
        layout.setHeading(headLabel);
        var bod = new Label(body);
        bod.setStyle("-fx-font-size:" + smallBodySize + ";-fx-text-fill: " + Colors.ColorHeadButton.invoke() + ";");
        layout.setBody(bod);
        if(width > 0) layout.setPrefWidth(width);

        List<JFXButton> btns = new ArrayList<>();
        for (int i = 0; infos != null && i < infos.length; i++) {
            var curInfo = infos[i];

            var modeStr = TextUtils.isEmpty(curInfo.str) ? modeToStr(curInfo.mode) : curInfo.str;
            var btn = new JFXButton(modeStr);
            if (curInfo.mode == ConfirmMode.Accept) {
                btn.setStyle(String.format("-fx-font-size: 15px;-fx-text-fill: %s;-fx-font-weight: BOLD;-fx-padding: 0.7em 0.8em;", Colors.ColorBottomBtnHighLight.invoke()));
            } else {
                btn.setStyle(String.format("-fx-font-size: 15px;-fx-text-fill: %s;-fx-font-weight: BOLD;-fx-padding: 0.7em 0.8em;", Colors.ColorHeadButton.invoke()));
            }
            final var action = curInfo.action0;
            btn.setOnAction(event -> {
                if (action != null) action.invoke();
                alert.hideWithAnimation();
            });
            btns.add(btn);
        }
        layout.setActions(btns);
        alert.setContent(layout);
        layout.requestFocus();
        alert.show();
    }

    public static void editInput(String head, String defaultStr, Action<String> action) {
        Log.d("show editInput");
        JFXAlert alert = new JFXAlert(getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        alert.setAnimation(JFXAlertAnimation.NO_ANIMATION);
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setStyle("-fx-background-color:" + Colors.SearchBgColor.invoke() + ";");

        var headLabel = new Label(head);
        headLabel.setStyle("-fx-font-size:13;-fx-text-fill: " + Colors.ColorHeadButton.invoke() + ";");
        layout.setHeading(headLabel);

        final var edit = new JFXTextField();
        edit.setStyle("-fx-font-size:15;-fx-text-fill: " + Colors.ColorHeadButton.invoke() + ";");
        edit.setText(defaultStr);
        edit.setPrefWidth(120);
        layout.setBody(edit);
        JFXButton closeButton = new JFXButton(Locales.str("cancel"));
        closeButton.setStyle(String.format("-fx-font-size: 15px;-fx-text-fill: %s;-fx-font-weight: BOLD;-fx-padding: 0.7em 0.8em;", Colors.ColorHeadButton.invoke()));
        closeButton.setOnAction(event -> alert.hideWithAnimation());

        JFXButton confirmButton = new JFXButton(Locales.str("sure"));
        confirmButton.setStyle(String.format("-fx-font-size: 15px;-fx-text-fill: %s;-fx-font-weight: BOLD;-fx-padding: 0.7em 0.8em;", Colors.ColorBottomBtnHighLight.invoke()));
        confirmButton.setOnAction(event -> {
            if (!edit.getText().equals(defaultStr)) {
                action.invoke(edit.getText());
            }
            alert.hideWithAnimation();
        });
        layout.setActions(closeButton, confirmButton);
        alert.setContent(layout);
        layout.requestFocus();
        alert.show();
    }

}

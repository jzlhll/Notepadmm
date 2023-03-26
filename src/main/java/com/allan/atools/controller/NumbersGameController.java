package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.threads.ThreadUtils;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.stage.Stage;

@XmlPaths(paths = {"pages", "content_numbers_game.fxml"})
public class NumbersGameController extends AbstractController {

    public JFXButton startBtn;
    public Label numbersLabel;
    public JFXComboBox<String> countSelectBox;
    public JFXComboBox<String> countDownBox;
    public Label titleLabel;
    public Label numbersCountDownLabel;

    private static final int LENGTH = 8;
    private static final int COUNT_SECOND = 8;

    private String randomNumbers() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            sb.append("").append((int) (Math.random() * 10));
        }
        return sb.toString();
    }

    private boolean isStart = false;
    public void init(Stage stage) {
        super.init(stage);

        startBtn.setOnMouseClicked(mouseEvent -> {
            if (isStart) return;
            isStart = true;
            numbersLabel.setVisible(true);
            numbersLabel.setText(randomNumbers());
            ThreadUtils.execute(() -> {
                int c = COUNT_SECOND;
                for (int i = 0; i < COUNT_SECOND;i++) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    c--;
                    int finalC = c;
                    Platform.runLater(()->{
                        numbersCountDownLabel.setText(String.valueOf(finalC));
                    });
                }
                Platform.runLater(()->{
                    numbersLabel.setVisible(false);
                    numbersCountDownLabel.setVisible(false);
                    isStart = false;
                });

            });
        });
    }
}

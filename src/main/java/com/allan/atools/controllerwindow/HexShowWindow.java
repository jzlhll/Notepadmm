package com.allan.atools.controllerwindow;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.beans.SubWindowCreatorInfo;
import com.allan.atools.controller.HexShowController;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.utils.ResLocation;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.File;

public final class HexShowWindow {
    private HexShowWindow() {
    }

    public static void show(File f) {
        var window = new HexShowWindow();
        window.init(f);
        window.stage.show();
    }

    public static void show(String fullPath) {
        show(new File(fullPath));
    }

    HexShowController controller;
    Stage stage;

    private void init(File pathFile) {
        if (!Platform.isFxApplicationThread()) {
            throw new RuntimeException("can not show image from other thread!");
        }

        SubWindowCreatorInfo info = new SubWindowCreatorInfo();
        Parent root;
        try {
            controller = AbstractController.load(HexShowController.class);
            root = controller.getRootView();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("notepad find fxml error!");
        }

        stage = AllStagesManager.getInstance().newStage(info, root, true, () -> {
        });

        controller.init(stage);
    }

}

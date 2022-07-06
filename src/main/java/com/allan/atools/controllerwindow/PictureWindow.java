package com.allan.atools.controllerwindow;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.beans.SubWindowCreatorInfo;
import com.allan.atools.controller.PictureController;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.utils.ResLocation;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.File;

public final class PictureWindow {
    private PictureWindow() {
    }

    public static void show(File f) {
        var window = new PictureWindow();
        window.init(f);
        window.controller.getStage().show();
    }

    public static void show(String fullPath) {
        show(new File(fullPath));
    }

    PictureController controller;

    private void init(File pathFile) {
        if (!Platform.isFxApplicationThread()) {
            throw new RuntimeException("can not show image from other thread!");
        }

        SubWindowCreatorInfo info = new SubWindowCreatorInfo();
        Parent root;
        try {
            controller = PictureController.load(PictureController.class);
            root = controller.getRootView();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("notepad find fxml error!");
        }

        controller.imageMgr.loadImage(pathFile);
        var imageInfo = controller.imageMgr.getImageWindowSize();

        info.title = pathFile.getAbsolutePath();
        info.width = imageInfo.getPrepareWindowWidth();
        info.height = imageInfo.getPrepareWindowHeight();
        info.iconPath = ResLocation.getURLStr("pictures", "icon28.png");
        info.resizable = true;
        info.alwaysTop = false;
        var stage = AllStagesManager.getInstance().newStage(info, root, true, () -> {
            controller.setAfterShown();
        });

        controller.init(stage);
    }

}

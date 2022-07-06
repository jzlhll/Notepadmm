package com.allan.atools.controllerwindow;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.beans.SubWindowCreatorInfo;
import com.allan.atools.controller.SettingController;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.ResLocation;
import javafx.scene.Parent;
import javafx.stage.Stage;

public final class SettingWindow {
    private SettingWindow() {
    }

    public static void show() {
        var window = new SettingWindow();
        window.init();
        window.controller.getStage().show();
    }

    SettingController controller;
    private void init() {
        SubWindowCreatorInfo info = new SubWindowCreatorInfo();
        info.title = Locales.str("setting");
        info.width = 550;
        info.height = 720;
        info.iconPath = ResLocation.getURLStr("pictures", "icon28.png");
        info.resizable = false;
        info.alwaysTop = false;
        Parent root;
        try {
            controller = AbstractController.load(SettingController.class);
            root = controller.getRootView();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("notepad find fxml error!");
        }
        var stage = AllStagesManager.getInstance().newStage(info, root, true, null);
        controller.init(stage);
    }
}

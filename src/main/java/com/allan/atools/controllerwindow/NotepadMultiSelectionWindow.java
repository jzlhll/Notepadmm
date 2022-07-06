package com.allan.atools.controllerwindow;

import com.allan.atools.beans.SubWindowCreatorInfo;
import com.allan.atools.controller.NotepadMultiSelectionController;
import com.allan.atools.controller.PictureController;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.ResLocation;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.stage.Stage;

public final class NotepadMultiSelectionWindow {
    NotepadMultiSelectionController controller;
    Stage stage;

    private NotepadMultiSelectionWindow() {
    }

    public static void show() {
        var window = new NotepadMultiSelectionWindow();
        window.init();
        window.stage.show();

        window.controller.outVbox.requestFocus();
    }

    private void init() {
        if (!Platform.isFxApplicationThread()) {
            throw new RuntimeException("can not show image from other thread!");
        }

        SubWindowCreatorInfo info = new SubWindowCreatorInfo();
        Parent root;
        try {
            controller = NotepadMultiSelectionController.load(NotepadMultiSelectionController.class);
            root = controller.getRootView();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("notepad find fxml error!");
        }

        info.title = Locales.str("altMultiSelection");
        info.width = 420;
        info.height = 285;
        info.iconPath = ResLocation.getURLStr("pictures", "icon28.png");
        info.resizable = false;
        info.alwaysTop = false;
        stage = AllStagesManager.getInstance().newStage(info, root, true, () -> {
        });

        controller.init(stage);
    }
}

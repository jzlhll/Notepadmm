package com.allan.atools.controllerwindow;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.beans.SubWindowCreatorInfo;
import com.allan.atools.controller.ResultNewController;
import com.allan.atools.keyevent.KeyEventDispatcher;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.ResLocation;
import javafx.scene.Parent;
import javafx.stage.Stage;

public class ResultNewWindow {
    private ResultNewWindow() {}

    public static ResultNewWindow createInstance() {
        var window = new ResultNewWindow();
        window.init();
        return window;
    }

    ResultNewController controller;
    public ResultNewController getController() {
        return controller;
    }

    public void show() {
        if(controller != null && controller.getStage() != null) controller.getStage().show();
    }

    public void hide() {
        if(controller != null && controller.getStage() != null) controller.getStage().hide();
        if (controller != null) controller.destroy();
        controller = null;
    }

    private void init() {
        SubWindowCreatorInfo info = new SubWindowCreatorInfo();
        info.title = Locales.str("result.result");
        info.width = 480;
        info.height = 400;
        info.iconPath = ResLocation.getURLStr("pictures", "icon28.png");
        info.resizable = true;
        info.sizeAndLocateCachePrefixName = "resultsNewWindow_";
        Parent root;
        try {
            controller = AbstractController.load(ResultNewController.class);
            assert controller != null;
            root = controller.getRootView();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("notepad find fxml error!");
        }
        var stage = AllStagesManager.getInstance().newStage(info, root, false, null);
        stage.focusedProperty().addListener((observable, oldValue, newValue) -> UIContext.focus.notifyResultNewWindowFocusChanged(newValue));
        KeyEventDispatcher.instance.init(root);
        controller.init(stage);
    }
}

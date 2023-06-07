package com.allan.atools.toolsstartupimpl;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.keyevent.KeyEventDispatcher;
import com.allan.atools.richtext.GenericStyledAreaBehaviorReflector;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager;
import com.allan.atools.toolsstartup.IStartupInit;
import com.allan.atools.toolsstartup.StartupEntro;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.atools.utils.*;
import com.allan.atools.beans.WindowCreatorInfo;
import com.allan.atools.controller.NotepadController;
import com.allan.uilibs.jfoenix.MyJFXDecorator;
import com.allan.baseparty.handler.TextUtils;
import com.allan.baseparty.memory.RefWatcher;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

@StartupEntro
public final class StartupNotepadInitImp implements IStartupInit {
    private void foreInit() {
        System.out.println("111");
        UIContext.uiMainThread = Thread.currentThread();
        System.out.println("444");
        // start is called on the FX Application Thread,
        // so Thread.currentThread() is the FX application thread:
        UIContext.uiMainThread.setUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("global exception: ", throwable);
            var log = Log.getStackTraceString(throwable);
            var logs = log.split("\n");
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (var lo : logs) {
                if (i++ >= 12) {
                    break;
                }
                sb.append(lo).append("\n");
            }
            Platform.runLater(()-> JfoenixDialogUtils.alert(Locales.str("notification"),
                    Locales.str("globalExceptionAlert") + sb, null, 1200));
        });
        System.out.println("application 22");
    }

    @Override
    public void beforeStart(Stage stage) {
        foreInit();

        DirectFileLog.append2("application 33");
        //初始化css
        var paths = getCssPaths();
        System.out.println("application 44");

        Map<String, String> customMap = new HashMap<>(2);
        customMap.put(CacheLocation.CustomFontSize, CacheLocation.fontSizeFile());
        customMap.put(CacheLocation.CustomFontFamily, CacheLocation.fontFamilyFile(-1));
        AllStagesManager.getInstance(paths, customMap);
        DirectFileLog.append2("application 55");
        AllStagesManager.getInstance().setMainStage(stage);
        DirectFileLog.append2("application 66");

        //here add your before init code
        DirectFileLog.append2("Startup main thid:" + Thread.currentThread().getId());

        GenericStyledAreaBehaviorReflector.action();
    }

    @Override
    public void createMainView(Stage stage) {
        Parent root;

        DirectFileLog.append2("createMainView 111");
        NotepadController mainController;
        try {
            mainController = AbstractController.load(NotepadController.class);
            root = mainController.getRootView();
        } catch (Exception e) {
            Log.e("主window main fxml error, e: ", e);
            DirectFileLog.append2("主window main fxml error");
            throw new RuntimeException("主window main fxml error!");
        }

        DirectFileLog.append2("createMainView 222");
        UIContext.mainController = mainController;
        //初始化controller代码
        mainController.init(stage);

        //Stage初始化
        WindowCreatorInfo createInfo = new WindowCreatorInfo();
        createInfo.width = 1000;
        createInfo.height = 650;
        createInfo.resizable = true;
        createInfo.title = "atools";
        createInfo.iconPath = ResLocation.getURLStr("pictures", "icon.png");
        createInfo.alwaysTop = false;
        createInfo.isSystemWindow = false;
        createInfo.sizeAndLocateCachePrefixName = "notepad_main_";

        DirectFileLog.append2("createMainView 333");
        //初始化主Stage（主window）
        if (!UIContext.CAN_DECORATOR) {
            createInfo.isSystemWindow = true;
        } else {
            MyJFXDecorator decorator = new MyJFXDecorator(stage, root, true, true);
            var imageView = new ImageView(new Image(ResLocation.getURLStr("pictures", "icon28.png")));
            imageView.setFitHeight(MyJFXDecorator.HEIGHT_BUTTONS_IMAGE_HEIGHT);
            imageView.setFitWidth(MyJFXDecorator.HEIGHT_BUTTONS_IMAGE_HEIGHT);
            decorator.setGraphic(imageView);

            root = decorator;
            UIContext.context().setIsDecorate();
        }
        AllStagesManager.getInstance().initMainStage(stage, createInfo, root, (sz) -> {
            NotepadController.sizeXyChangedProp.set(NotepadController.sizeXyChangedProp.getValue() + 1);
        });
        UIContext.mainWindow = stage.getScene().getWindow();
        KeyEventDispatcher.instance.init(root);

        DirectFileLog.append2("createMainView 444");
        stage.setMinHeight(480);
        stage.setMinWidth(720);

        stage.focusedProperty().addListener((observable, oldValue, newValue) -> UIContext.focus.notifyMainStageFocusChanged(newValue));

        stage.setOnCloseRequest(event -> {
            var areas = AllEditorsManager.Instance.getAllAreas();
            boolean hasCannotClose = false;
            if (areas != null) {
                for (var area : areas) {
                    if (!area.getEditor().canClosed()) {
                        hasCannotClose = true;
                        break;
                    }
                }
            }

            if (hasCannotClose) {
                event.consume();
                SnackbarUtils.show(Locales.str("hasUnClosedFile"));
            } else {
                mainController.destroy();
            }
        });
    }

    @Override
    public String[] getCssPaths() {
        return new String[] {
                SettingPreferences.getBoolean(SettingPreferences.appVisionKey) ?
                        ResLocation.getRealPath("css", "colors_dark.css") :
                        ResLocation.getRealPath("css", "colors.css"),
                ResLocation.getRealPath("css", "main_default.css"),
                ResLocation.getRealPath("css", "editor.css"),
                ResLocation.getRealPath("css", "panes.css"),
                ResLocation.getRealPath("css", "main_custom.css"),
        };
    }
}

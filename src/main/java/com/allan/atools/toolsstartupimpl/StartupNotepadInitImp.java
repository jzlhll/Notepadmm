package com.allan.atools.toolsstartupimpl;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.keyevent.KeyEventDispatcher;
import com.allan.atools.richtext.GenericStyledAreaBehaviorReflector;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.SettingPreferences;
import com.allan.atools.toolsstartup.IStartupInit;
import com.allan.atools.toolsstartup.ATools;
import com.allan.atools.toolsstartup.StartupEntro;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.utils.*;
import com.allan.atools.beans.WindowCreatorInfo;
import com.allan.atools.controller.NotepadController;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.HashMap;
import java.util.Map;

@StartupEntro
public final class StartupNotepadInitImp implements IStartupInit {
    private void foreInit() {
        UIContext.uiMainThread = Thread.currentThread();
        Log.e("startup: UI thread registered");
        // start is called on the FX Application Thread,
        // so Thread.currentThread() is the FX application thread:
        UIContext.uiMainThread.setUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("startup: global exception", throwable);
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
        Log.e("startup: global exception handler registered");
    }

    @Override
    public void beforeStart(Stage stage) {
        foreInit();

        //初始化css
        var paths = getCssPaths();
        Log.e("startup: CSS paths resolved");

        Map<String, String> customMap = new HashMap<>(2);
        customMap.put(CacheLocation.CustomFontSize, CacheLocation.fontSizeFile());
        customMap.put(CacheLocation.CustomFontFamily, CacheLocation.fontFamilyFile(-1));
        AllStagesManager.getInstance(paths, customMap);
        Log.e("startup: stage manager initialized");
        AllStagesManager.getInstance().setMainStage(stage);
        Log.e("startup: main stage registered");

        //here add your before init code
        GenericStyledAreaBehaviorReflector.action();
        Log.e("startup: RichTextFX reflection initialized");
    }

    @Override
    public void createMainView(Stage stage) {
        Parent root;

        Log.e("startup: main FXML load begin");
        NotepadController mainController;
        try {
            mainController = AbstractController.load(NotepadController.class);
            root = mainController.getRootView();
        } catch (Exception e) {
            Log.e("startup: main FXML load failed", e);
            throw new RuntimeException("主window main fxml error!");
        }

        Log.e("startup: main FXML loaded");
        UIContext.mainController = mainController;
        stage.initStyle(StageStyle.TRANSPARENT);
        //初始化controller代码
        mainController.init(stage);
        Log.e("startup: main controller initialized");

        //Stage初始化
        WindowCreatorInfo createInfo = new WindowCreatorInfo();
        createInfo.width = 1000;
        createInfo.height = 650;
        createInfo.resizable = true;
        createInfo.title = "ATools";
        createInfo.iconPath = ResLocation.getURLStr("pictures", "icon.png");
        createInfo.alwaysTop = false;
        createInfo.isSystemWindow = false;
        createInfo.sizeAndLocateCachePrefixName = "notepad_main_";

        //主窗口统一使用页面内自绘标题栏，子窗口维持现有平台策略
        AllStagesManager.getInstance().initMainStage(stage, createInfo, root, (sz) -> {
            NotepadController.sizeXyChangedProp.set(NotepadController.sizeXyChangedProp.getValue() + 1);
        });
        stage.getScene().setFill(Color.TRANSPARENT);
        UIContext.mainWindow = stage.getScene().getWindow();
        KeyEventDispatcher.instance.init(root);

        Log.e("startup: main stage content initialized");
        stage.setMinHeight(480);
        stage.setMinWidth(720);

        stage.focusedProperty().addListener((observable, oldValue, newValue) -> UIContext.focus.notifyMainStageFocusChanged(newValue));

        stage.setOnCloseRequest(event -> {
            mainController.destroy();
            ATools.shutdownAfterMainWindowClosed();
        });
    }

    @Override
    public String[] getCssPaths() {
        return new String[] {
                //markdown 编辑器皮肤需先于主题文件加载，深色主题在 colors_dark.css 中覆盖其变量
                ResLocation.getRealPath("css", "editor_markdown.css"),
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

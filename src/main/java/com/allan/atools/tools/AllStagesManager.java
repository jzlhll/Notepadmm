package com.allan.atools.tools;

import com.allan.atools.UIContext;
import com.allan.atools.bases.SizeAndXySaverImpl;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.allan.atools.beans.SizeAndXy;
import com.allan.atools.beans.SubWindowCreatorInfo;
import com.allan.atools.beans.WindowCreatorInfo;
import com.allan.uilibs.jfoenix.MyJFXDecorator;
import com.allan.baseparty.Action;
import com.allan.baseparty.Action0;
import com.allan.baseparty.handler.TextUtils;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

public final class AllStagesManager {
    private static AllStagesManager mInstance;
    private AllStagesManager(String[] cssPaths, Map<String, String> cssCustomPaths) {
        init(cssPaths);
        for (var n : cssCustomPaths.keySet()) {
            initCustom(n, cssCustomPaths.get(n));
        }
    }

    public static AllStagesManager getInstance(String[] cssPaths, Map<String, String> cssCustomPaths) {
        if(mInstance == null) mInstance = new AllStagesManager(cssPaths, cssCustomPaths);
        return mInstance;
    }

    public static AllStagesManager getInstance() {
        return mInstance;
    }

    private String[] cssForms;
    private final Map<String, String> cssCustomForms = new HashMap<>(2);

    private Stage mainStage;

    private void init(String[] cssPaths) {
        try {
            cssForms = new String[cssPaths.length];
            for (int i = 0; i < cssPaths.length; i++) {
                cssForms[i] = ResLocation.getURLByRealPath(cssPaths[i]).toExternalForm();
            }
        } catch (Exception e) {
            //ignore
        }
    }

    private void initCustom(String name, String cssCustomPath) {
        try {
            cssCustomForms.put(name, ResLocation.getURLByRealPath(cssCustomPath).toExternalForm());
        } catch (Exception e) {
            //ignore
        }
    }

    public void replaceCustom(String name, String cssCustomPath) {
        String cssForm = cssCustomForms.get(name);
        String newCssForm = cssForm;
        try {
            newCssForm = ResLocation.getURLByRealPath(cssCustomPath).toExternalForm();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        var styles = mainStage.getScene().getStylesheets();
        int i = 0;
        for (; i < styles.size(); i++) {
            var c = styles.get(i);
            if (c.equals(cssForm)) {
                styles.set(i, newCssForm);
                break;
            }
        }

        cssCustomForms.put(name, newCssForm);
    }

    public Stage getMainStage() {
        return mainStage;
    }

    public void setMainStage(Stage main) {
        this.mainStage = main;
    }

    public void initMainStage(Stage mainStage, WindowCreatorInfo info, Parent rootView, Action<SizeAndXy> sizeOrXYChanged) {
        // Create sized scene
        Scene scene;
        if (info.width > 0 && info.height > 0)
            scene = new Scene(rootView, info.width, info.height);
        else
            scene = new Scene(rootView);

        // Create window
        mainStage.setScene(scene);

        //图标
        if (info.iconPath != null && info.iconPath.length() > 0) {
            mainStage.getIcons().add(new Image(info.iconPath));
        }
        /*try (FileInputStream str = new FileInputStream(iconpath)) {
            stage.getIcons().add(new Image(str));
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        //标题
        mainStage.setTitle(info.title);

        // Add style-sheets
        ObservableList<String> stylesheets = mainStage.getScene().getStylesheets();
        stylesheets.addAll(cssForms);
        Log.d("main stage init cssCustomForms ");
        stylesheets.addAll(cssCustomForms.values());
        Log.d("main stage init cssCustomForms end ");

        mainStage.setAlwaysOnTop(info.alwaysTop);

        //窗口位置，大小保存
        if (info.sizeAndLocateCachePrefixName != null && info.sizeAndLocateCachePrefixName.length() > 0) {
            SizeAndXySaverImpl mWindowInfo = new SizeAndXySaverImpl(mainStage, info.sizeAndLocateCachePrefixName);
            mWindowInfo.loadCached();
            mWindowInfo.setSizeAndXy();
            mWindowInfo.afterSetData();
            mWindowInfo.setXyChangedListener(sizeOrXYChanged);
        }

        //style
        if(info.isSystemWindow) mainStage.initStyle(StageStyle.DECORATED);
        //组合拳解决闪屏黑色问题 再恢复透明#2
        mainStage.setOnShown(event -> {
            ThreadUtils.globalHandler().postDelayed(()-> Platform.runLater(()-> mainStage.getScene().getWindow().setOpacity(1d)), 400);
        });
        //组合拳解决闪屏黑色问题 先搞成全透#1
        mainStage.getScene().getWindow().setOpacity(0.0001);
    }

    public Stage newStage(SubWindowCreatorInfo info, Parent rootView, boolean initOwner, Action0 onShownAction) {
        return newStage(info, rootView, initOwner, onShownAction, null);
    }
    public Stage newStage(SubWindowCreatorInfo info, Parent rootView, boolean initOwner, Action0 onShownAction, Action<SizeAndXy> sizeOrXYChanged) {
        // Create sized scene
        Scene scene;
        // Create window
        Stage stage = new Stage();

        if (!UIContext.CAN_DECORATOR) {
            info.isSystemWindow = true;
            //style
            stage.initStyle(info.stageStyle);
        } else {
            MyJFXDecorator decorator = new MyJFXDecorator(stage, rootView, true, true);
            if (!TextUtils.isEmpty(info.iconPath)) {
                var imageView = new ImageView(new Image(info.iconPath));
                imageView.setFitHeight(MyJFXDecorator.HEIGHT_BUTTONS_IMAGE_HEIGHT);
                imageView.setFitWidth(MyJFXDecorator.HEIGHT_BUTTONS_IMAGE_HEIGHT);
                decorator.setGraphic(imageView);
            }
            rootView = decorator;
        }

        if (info.width > 0 && info.height > 0)
            scene = new Scene(rootView, info.width, info.height);
        else
            scene = new Scene(rootView);

        stage.setScene(scene);
        stage.setResizable(info.resizable);

        //图标
        if (info.iconPath != null && info.iconPath.length() > 0) {
            stage.getIcons().add(new Image(info.iconPath));
        }
        /*try (FileInputStream str = new FileInputStream(iconpath)) {
            stage.getIcons().add(new Image(str));
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        //标题
        stage.setTitle(info.title);

        // Add style-sheets
        ObservableList<String> stylesheets = stage.getScene().getStylesheets();
        stylesheets.addAll(cssForms);
        stylesheets.addAll(cssCustomForms.values());

        // Set window owner to main window
        if (initOwner && Screen.getScreens().size() > 1) {
            stage.initOwner(mainStage);
        }

        stage.setAlwaysOnTop(info.alwaysTop);

        //窗口位置，大小保存
        if (info.sizeAndLocateCachePrefixName != null && info.sizeAndLocateCachePrefixName.length() > 0) {
            SizeAndXySaverImpl mWindowInfo = new SizeAndXySaverImpl(stage, info.sizeAndLocateCachePrefixName);
            mWindowInfo.loadCached();
            mWindowInfo.setSizeAndXy();
            mWindowInfo.afterSetData();
            mWindowInfo.setXyChangedListener(sizeOrXYChanged);
        }

        //组合拳解决闪屏黑色问题 再恢复透明#2
        stage.setOnShown(event -> {
            if(onShownAction != null) onShownAction.invoke();
            ThreadUtils.globalHandler().postDelayed(()-> Platform.runLater(()-> stage.getScene().getWindow().setOpacity(1d)), 200);
        });
        //组合拳解决闪屏黑色问题 先搞成全透#1
        stage.getScene().getWindow().setOpacity(0.0001);
        return stage;
    }
}

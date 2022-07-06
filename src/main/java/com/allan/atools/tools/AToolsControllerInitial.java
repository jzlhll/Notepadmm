package com.allan.atools.tools;

import com.allan.atools.AToolsViewsConfigure;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.controller.AToolsController;
import com.allan.atools.utils.ResLocation;
import com.allan.atools.beans.SubWindowCreatorInfo;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public final class AToolsControllerInitial {
    private static class Inner {
        final AbstractController[] pageControls;

        final int CONTROLS_NUM;

        final Map<Integer, Class<? extends AbstractController>> PAGES;

        static final int FIRST_PAGE_INDEX = 0;

        Node emptyPage;

        final ObservableList<String> leftMenuList;

        Inner() {
            this.leftMenuList = FXCollections.observableArrayList();
            PAGES = new HashMap<>();
            int index = 0;
            try {
                index = new AToolsViewsConfigure().load(PAGES, leftMenuList);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            this.CONTROLS_NUM = index;
            this.pageControls = new AbstractController[this.CONTROLS_NUM];
            AbstractController emptyNodeAndCtrl = null;
            try {
                emptyNodeAndCtrl = AbstractController.loadPath("main", "loading.fxml");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (emptyNodeAndCtrl != null)
                this.emptyPage = emptyNodeAndCtrl.getRootView();
        }

        AbstractController initPage(int index) {
            if (this.pageControls[index] == null)
                try {
                    AbstractController ctrl = AbstractController.load(this.PAGES.get(index));
                    this.pageControls[index] = ctrl;
                    ctrl.init(AllStagesManager.getInstance().getMainStage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            return this.pageControls[index];
        }

        void destroy() {
            if (pageControls != null) {
                for (AbstractController c : pageControls) {
                    if (c != null) {
                        c.destroy();
                    }
                }
            }
        }
    }

    private final SimpleObjectProperty<Node> pageContentProp = new SimpleObjectProperty<>();

    Inner mainViewManager;
    public static final int DEFAULT_PAGE_INDEX = Inner.FIRST_PAGE_INDEX;

    public AToolsController createMainView() {
        this.mainViewManager = new Inner();
        Parent root;
        AToolsController mainController;
        try {
            mainController = AbstractController.load(AToolsController.class);
            assert mainController != null;
            root = mainController.getRootView();
        } catch (Exception e) {
            throw new RuntimeException("主window main fxml error!");
        }

        //初始化controller代码
        mainController.pageHolderPanel.contentProperty().bind(this.pageContentProp);
        replacePageWithLoading();

        //Stage初始化
        SubWindowCreatorInfo createInfo = new SubWindowCreatorInfo();
        createInfo.width = 1000;
        createInfo.height = 650;
        createInfo.resizable = true;
        createInfo.title = "";
        createInfo.iconPath = ResLocation.getURLStr("pictures", "icon28.png");
        createInfo.alwaysTop = false;
        createInfo.sizeAndLocateCachePrefixName = "atools_";

        //初始化主Stage（主window）
        var stage = AllStagesManager.getInstance().newStage(createInfo, root, false, () -> {
            replacePageByIndex(DEFAULT_PAGE_INDEX);
        });
        mainController.setMainControllerInit(this);
        mainController.init(stage);

        stage.setOnCloseRequest(ev -> {
            mainViewManager.destroy();
            mainController.destroy();
        });

        return mainController;
    }

    public void replacePageByIndex(int index) {
        Parent pageView = this.mainViewManager.initPage(index).getRootView();
        this.pageContentProp.set(pageView);
    }

    private void replacePageWithLoading() {
        this.pageContentProp.setValue(this.mainViewManager.emptyPage);
    }

    public ObservableList<String> getLeftMenu() {
        return this.mainViewManager.leftMenuList;
    }
}

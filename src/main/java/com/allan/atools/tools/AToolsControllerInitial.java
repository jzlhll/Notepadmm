package com.allan.atools.tools;

import com.allan.atools.AToolsViewsConfigure;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.beans.SizeAndXy;
import com.allan.atools.beans.SubWindowCreatorInfo;
import com.allan.atools.controller.AToolsController;
import com.allan.atools.utils.ResLocation;
import com.allan.baseparty.Action;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

public final class AToolsControllerInitial {
    public interface IToolSizeChanged {
        void onWindowSizeChange(float w, float h);
    }

    private float mToolWidth, mToolHeight;

    public void setHeight(Number newNumber) {
        mToolHeight = newNumber.floatValue();
        notifyWindowSizeChange();
    }

    public void setWidth(Number newNumber) {
        mToolWidth = newNumber.floatValue();
        notifyWindowSizeChange();
    }

    private void notifyWindowSizeChange() {
        for (var ctrl : mainViewManager.pageControls) {
            if (ctrl instanceof IToolSizeChanged c) {
                c.onWindowSizeChange(mToolWidth, mToolHeight);
            }
        }
    }

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

    public AToolsController createAToolsWindow() {
        this.mainViewManager = new Inner();
        Parent root;
        AToolsController aToolsController;
        try {
            aToolsController = AbstractController.load(AToolsController.class);
            assert aToolsController != null;
            root = aToolsController.getRootView();
        } catch (Exception e) {
            throw new RuntimeException("主window main fxml error!");
        }

        //初始化controller代码
        aToolsController.pageHolderPanel.contentProperty().bind(this.pageContentProp);
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
        }, sizeAndXy -> {
            aToolsController.sizeXyChangedProp.set(aToolsController.sizeXyChangedProp.getValue() + 1);
        });
        aToolsController.setMainControllerInit(this);
        aToolsController.init(stage);

        stage.setOnCloseRequest(ev -> {
            mainViewManager.destroy();
            aToolsController.destroy();
        });

        return aToolsController;
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

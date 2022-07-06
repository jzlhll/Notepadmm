package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.SizeAndXyChangedListener;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.controllermgr.PictureControllerImageMgr;
import com.allan.atools.ui.IconfontCreator;
import com.allan.uilibs.controls.RotatablePaneLayouter;
import com.allan.atools.Colors;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.uilibs.controls.Drag2ScrollPane;
import com.allan.uilibs.jfoenix.MyJFXDecorator;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

@XmlPaths(paths = {"notepad", "picture_show.fxml"})
public final class PictureController extends AbstractController {
    public static final String TAG = "PictureControl";
    public Label zoomBigBtn;
    public Label zoomSmallBtn;
    public Label zoomResetBtn;
    public Label rotateBtn;

    public AnchorPane outAnchorPane;
    public Label currentSizeLabel;

    public final PictureControllerImageMgr imageMgr = new PictureControllerImageMgr(this);
    public HBox floatingControlsLayout;
    public Drag2ScrollPane draggerScrollPane;
    public RotatablePaneLayouter rotatablePane;
    public Label pickupColorBtn;
    public Label fixWholeWidthLabel;
    public Label colorInfoLabel;

    public StackPane imageViewBoxStackPane;
    public ImageView imageView;

    private boolean enableRotateBtn = true;

    private double mRotate = 0;
    public void rotate() {
        imageMgr.zoomReset();
        mRotate += 90;
        if (mRotate >= 360) {
            mRotate -= 360;
        }
        Log.d(TAG, "local image size: " + imageView.getImage().getWidth() + " * " + imageView.getImage().getHeight()
            + ": position: " + AnchorPane.getTopAnchor(imageView));
        imageViewBoxStackPane.setRotate(mRotate);
    }

    private void rotateReset() {
        mRotate = 0;

        Log.d(TAG, "rotateReset: ");
        imageViewBoxStackPane.setRotate(mRotate);

        imageMgr.zoomReset();
    }

    public void setAfterShown() {
        var sizeXyChanged = new SizeAndXyChangedListener(getStage());
        var imgInfo = imageMgr.getImageWindowSize();
        Log.d("offsetY: " + draggerScrollPane.getLayoutY());

        imgInfo.setDeltaRealWindowToDefWidth(sizeXyChanged.getWindowInfo().width - imgInfo.getPrepareWindowWidth() + PictureControllerImageMgr.DELTA_SIZE);
        imgInfo.setDeltaRealWindowToDefHeight(sizeXyChanged.getWindowInfo().height - imgInfo.getPrepareWindowHeight() + PictureControllerImageMgr.DELTA_SIZE);

        Log.d(TAG, "onShownWindow: windowInfo: " + imgInfo);
        Log.d(TAG, "onShownWindow: sizeXy: " + sizeXyChanged.getWindowInfo());
        setScrollPaneSize(Math.min(sizeXyChanged.getWindowInfo().width - imageMgr.getImageWindowSize().getDeltaRealWindowToDefWidth(), sizeXyChanged.getWindowInfo().width),
                Math.min(sizeXyChanged.getWindowInfo().height
                        - imageMgr.getImageWindowSize().getDeltaRealWindowToDefHeight(),
                        sizeXyChanged.getWindowInfo().height));
        sizeXyChanged.addListener(sizeAndXy -> {
            Log.d(TAG, "size x y changed!" + sizeAndXy);
            var dragPaneWidth = Math.min(sizeAndXy.width - imageMgr.getImageWindowSize().getDeltaRealWindowToDefWidth(), sizeAndXy.width);
            var dragPaneHeight = Math.min(sizeAndXy.height
                    - imageMgr.getImageWindowSize().getDeltaRealWindowToDefHeight(),
                    sizeAndXy.height);
            Platform.runLater(()-> setScrollPaneSize(dragPaneWidth, dragPaneHeight));
        });
    }

    public static final int OFFSET_Y = (UIContext.CAN_DECORATOR ? MyJFXDecorator.HEIGHT_BUTTONS_CONTAINER : 0);

    //我们为什么要调这个函数？
    //目的是随着window的变化。我们需要让draggerScrollPane的size也跟着变化。
    private void setScrollPaneSize(double w, double h) {
        Log.d(TAG, "set scrollSize: " + w + "*" + h);
        draggerScrollPane.setPrefWidth(w);
        draggerScrollPane.setPrefHeight(h - imageMgr.getImageWindowSize().getScrollPaneOffsetY() - OFFSET_Y);
    }

    @Override
    public void init(Stage stage) {
        super.init(stage);

        zoomBigBtn.setTooltip(new Tooltip(Locales.str("zoomBig")));
        IconfontCreator.setText(zoomBigBtn, "fangda", 24, Colors.ColorHeadButton.invoke());
        zoomBigBtn.setOnMouseClicked(e -> imageMgr.zoomBig());

        zoomSmallBtn.setTooltip(new Tooltip(Locales.str("zoomSmall")));
        IconfontCreator.setText(zoomSmallBtn, "suoxiao", 25, Colors.ColorHeadButton.invoke());
        zoomSmallBtn.setOnMouseClicked(e -> imageMgr.zoomSmall());

        zoomResetBtn.setTooltip(new Tooltip(Locales.str("reset")));
        IconfontCreator.setText(zoomResetBtn, "bx-reset", 19, Colors.ColorHeadButton.invoke());
        zoomResetBtn.setOnMouseClicked(e ->{
            rotateReset();
        });

        rotateBtn.setTooltip(new Tooltip(Locales.str("rotate")));
        IconfontCreator.setText(rotateBtn, "exchangerate", 24, Colors.ColorHeadButton.invoke());
        rotateBtn.setOnMouseClicked(e ->{
            if (enableRotateBtn) {
                rotate();
            }
        });

        pickupColorBtn.setTooltip(new Tooltip(Locales.str("colorPick")));
        IconfontCreator.setText(pickupColorBtn, "xiguan", 24, Colors.ColorHeadButton.invoke());
        pickupColorBtn.setOnMouseClicked(e ->{
            var cur = imageMgr.getEnableColorPickMode();
            imageMgr.setEnableColorPickMode(!cur);
            if (!cur) {
                rotateReset();
                enableRotateBtn = false;
                draggerScrollPane.clickAction = event -> {
                    if (event instanceof MouseEvent me) {
                        Log.d("click: scene[" + me.getSceneX() + ", " + me.getSceneY()
                        + "] xy: " + me.getX() + ", " + me.getY() + ", click: current: " + draggerScrollPane.getLayoutY() + ", " + draggerScrollPane.getHvalue() + ", " + draggerScrollPane.getVvalue());
                        imageMgr.attachColorCloth(me.getX(), me.getY());
                    }
                };

                IconfontCreator.setText(pickupColorBtn, "xiguan", 24, Colors.ColorBottomBtnHighLight.invoke());
            } else {
                enableRotateBtn = true;
                draggerScrollPane.clickAction = null;
                IconfontCreator.setText(pickupColorBtn, "xiguan", 24, Colors.ColorHeadButton.invoke());
            }
        });

        colorInfoLabel.setOnMouseClicked(e->{
            // 获取系统剪贴板
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 封装文本内容
            var trans = new StringSelection("" + imageMgr.getCurrentColorHexAndroid());
            // 把文本内容设置到系统剪贴板
            clipboard.setContents(trans, null);
            SnackbarUtils.showInPane("" + imageMgr.getCurrentColorHexAndroid(), 1000, outAnchorPane);
        });
        fixWholeWidthLabel.setOnMouseClicked(colorInfoLabel.getOnMouseClicked());

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        imageMgr.initZoom();

        imageView.preserveRatioProperty().set(true);
        imageViewBoxStackPane = new StackPane(imageView);

        imageView.fitWidthProperty().bind(imageViewBoxStackPane.widthProperty());
        imageView.fitHeightProperty().bind(imageViewBoxStackPane.heightProperty());

        rotatablePane.addChild(imageViewBoxStackPane);

        //contentAnchorPane.getChildren().add(0, imageView);
        draggerScrollPane.addDragEvent();

        outAnchorPane.requestFocus();

        imageView.setImage(imageMgr.getImageWindowSize().getImage());
    }
}

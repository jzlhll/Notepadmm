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
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.awt.Toolkit;
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
    public StackPane imageContentPane;

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

        applyCheckerboardBackground();
        imageContentPane.setPadding(new Insets(PictureControllerImageMgr.CONTENT_PADDING));
        draggerScrollPane.setStyle("-fx-background-color: transparent;");

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
                if (mRotate != 0) {
                    rotateReset();
                }
                enableRotateBtn = false;
                draggerScrollPane.clickAction = event -> {
                    if (event instanceof MouseEvent me) {
                        Log.d("click: scene[" + me.getSceneX() + ", " + me.getSceneY()
                        + "] xy: " + me.getX() + ", " + me.getY() + ", click: current: " + draggerScrollPane.getLayoutY() + ", " + draggerScrollPane.getHvalue() + ", " + draggerScrollPane.getVvalue());
                        var imagePoint = imageView.sceneToLocal(me.getSceneX(), me.getSceneY());
                        imageMgr.attachColorCloth(imagePoint.getX(), imagePoint.getY());
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

    private void applyCheckerboardBackground() {
        int cellSize = 12;
        int imageSize = cellSize * 2;
        var image = new WritableImage(imageSize, imageSize);
        var pixelWriter = image.getPixelWriter();
        var lightColor = Colors.isDark() ? Color.rgb(58, 58, 58) : Color.rgb(238, 238, 238);
        var darkColor = Colors.isDark() ? Color.rgb(48, 48, 48) : Color.rgb(207, 207, 207);
        for (int y = 0; y < imageSize; y++) {
            for (int x = 0; x < imageSize; x++) {
                boolean isLight = (x / cellSize + y / cellSize) % 2 == 0;
                pixelWriter.setColor(x, y, isLight ? lightColor : darkColor);
            }
        }
        var backgroundImage = new BackgroundImage(image, BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT, BackgroundSize.DEFAULT);
        outAnchorPane.setBackground(new Background(backgroundImage));
    }
}

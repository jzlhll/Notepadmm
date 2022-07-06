package com.allan.atools.ui;

import com.allan.atools.UIContext;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.uilibs.controls.AnchorPaneEx;
import com.allan.baseparty.Action0;
import com.jfoenix.controls.JFXSnackbar;
import com.jfoenix.controls.JFXSnackbarLayout;
import javafx.application.Platform;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;

public final class SnackbarUtils {
    private static int str2width(String str) {
        if (str.length() < 20) {
            return 150;
        }
        if (str.length() < 60) {
            return str.length() * 6;
        }
        return 400;
    }

    public static void show(String title) {
        show(title, 1800, null);
    }

    public static void show(String title, long duration, Action0 action0) {
        var snackbar = new JFXSnackbar(UIContext.context().snackContainer);

        var mainCtrl = UIContext.context();

        if (!mainCtrl.mainPane.getChildren().contains(mainCtrl.snackContainer)) {
            mainCtrl.mainPane.getChildren().add(mainCtrl.snackContainer);
        }

        snackbar.setPrefWidth(str2width(title));
        snackbar.fireEvent(new JFXSnackbar.SnackbarEvent(
                new JFXSnackbarLayout(title, Locales.str("known"), action -> {
                    if (action0 != null) {
                        action0.invoke();
                    }
                    var mainCtrl2 = UIContext.context();
                    mainCtrl2.mainPane.getChildren().remove(mainCtrl2.snackContainer);
                }), Duration.millis(duration), null));

        ThreadUtils.globalHandler().postDelayed(()->
                Platform.runLater(()-> {
                    var mainCtrl2 = UIContext.context();
                    mainCtrl2.mainPane.getChildren().remove(mainCtrl2.snackContainer);
                }),
                duration + 200);
    }

    public static void showInPane(String title, long duration, AnchorPane parent) {
        //<AnchorPane fx:id="snackContainer" StackPane.alignment="BOTTOM_CENTER" prefWidth="600" prefHeight="30" />
        AnchorPaneEx snackbarHost = new AnchorPaneEx();
        snackbarHost.setMinSize(300.0, 30);
        snackbarHost.setMaxSize(300.0, 30);
        AnchorPane.setTopAnchor(snackbarHost, parent.getHeight() * 3 / 4);
        AnchorPane.setLeftAnchor(snackbarHost, (parent.getWidth() - 300) / 2);
        parent.getChildren().add(snackbarHost);
        var snackbar = new JFXSnackbar(snackbarHost);
        snackbar.setPrefWidth(str2width(title));
        snackbar.fireEvent(new JFXSnackbar.SnackbarEvent(
                new JFXSnackbarLayout(title, Locales.str("known"), action -> {
                    parent.getChildren().remove(snackbarHost);
                }), Duration.millis(duration), null));

        ThreadUtils.globalHandler().postDelayed(()->
                        Platform.runLater(()-> {
                            parent.getChildren().remove(snackbarHost);
                        }),
                duration + 200);
    }
//    public static void showWithClose(Pane root, String title) {
//        showWithClose(root, title, Duration.INDEFINITE);
//    }
//
//    public static void showWithClose(Pane root, String title, long duration) {
//        showWithClose(root, title, Duration.millis(duration));
//    }
//
//    private static void showWithClose(Pane root, String title, Duration duration) {
//        var snackbar = new JFXSnackbar(root);
//        snackbar.setPrefWidth(300);
//        snackbar.fireEvent(new JFXSnackbar.SnackbarEvent(
//                new JFXSnackbarLayout(
//                        title,
//                        Locales.str("close"),
//                        action -> snackbar.close()),
//                        duration,
//                        null));
//    }
}

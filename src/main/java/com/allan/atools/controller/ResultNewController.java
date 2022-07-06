package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.controllerwindow.NotepadFindWindow;
import com.allan.atools.keyevent.IKeyDispatcherLeaf;
import com.allan.atools.keyevent.KeyEventDispatcher;
import com.allan.atools.keyevent.ShortCutKeys;
import com.allan.atools.utils.Log;
import javafx.scene.control.Accordion;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

@XmlPaths(paths = {"notepad", "results.fxml"})
public class ResultNewController extends AbstractController implements IKeyDispatcherLeaf {
    private static final String TAG = "ResultNewController";

    public AnchorPane rootPane;
    private Accordion mResultRoot; //只需要一份
    public Accordion getResultRoot() {
        return mResultRoot;
    }

    @Override
    public void init(Stage stage) {
        super.init(stage);
        mResultRoot = new Accordion();
        mResultRoot.getStyleClass().add("custom-main-bg");
        AnchorPane.setLeftAnchor(mResultRoot, 0.0);
        AnchorPane.setRightAnchor(mResultRoot, 0.0);
        AnchorPane.setTopAnchor(mResultRoot, 0.0);
        AnchorPane.setBottomAnchor(mResultRoot, 0.0);
        rootPane.getChildren().add(mResultRoot);

        addKeyListener();
    }

    @Override
    public int level() {
        return KeyEventDispatcher.LEVEL_1_CHILD;
    }

    @Override
    public boolean accept(ShortCutKeys.CombineKey parsedEvent) {
        switch (parsedEvent) {
            case FindS, Replace, ReplaceS -> {
                NotepadFindWindow.getInstance().show();
                Log.d(TAG, "accept show find window");
                return true;
            }
        }
        Log.d(TAG, "not accept");
        return false;
    }

    @Override
    public void destroy() {
        removeKeyListener();
        super.destroy();
    }
}

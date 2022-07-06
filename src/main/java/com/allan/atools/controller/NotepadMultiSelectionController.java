package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.utils.Log;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

@XmlPaths(paths = {"notepad", "notepad_multi_selection.fxml"})
public final class NotepadMultiSelectionController extends AbstractController {

    public JFXButton replaceBtn;
    public JFXButton deleteBtn;
    public JFXTextField textField;
    public VBox outVbox;

    @Override
    public void init(Stage stage) {
        super.init(stage);
        deleteBtn.setOnMouseClicked(e -> {
            var sel = UIContext.currentAreaProp.get().getMultiSelections();
            sel.delete();
        });

        replaceBtn.setOnMouseClicked(e -> {
            var sel = UIContext.currentAreaProp.get().getMultiSelections();
            sel.replace(textField.getText());
        });
    }

    @Override
    public void destroy() {
        Log.d("DESTROY:  NotepadMultiSelection Controller");
    }
}

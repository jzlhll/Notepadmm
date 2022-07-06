package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.jfoenix.controls.JFXButton;
import com.allan.atools.tools.moduletransfer.TransferHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.stage.Stage;
@XmlPaths(paths = {"pages", "content_transfer.fxml"})
public class TransferController extends AbstractController {
    public JFXButton startAServerBtn;
    public Label serverLabel;
    private TransferHelper mTransferHelper;
    private final StringProperty serverLabelText = new SimpleStringProperty("");

    public void init(Stage stage) {
        super.init(stage);
        this.serverLabel.textProperty().bind(this.serverLabelText);

        this.startAServerBtn.setOnMouseClicked(e -> {
            if (this.mTransferHelper == null)
                this.mTransferHelper = new TransferHelper();
            this.startAServerBtn.setDisable(true);
        });
    }
}

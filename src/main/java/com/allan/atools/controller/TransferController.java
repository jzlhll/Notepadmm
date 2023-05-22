package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.utils.Log;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

@XmlPaths(paths = {"pages", "content_transfer.fxml"})
public class TransferController extends AbstractController {
    public JFXButton startAServerBtn;
    public final StringProperty logText = new SimpleStringProperty("");
    public JFXButton startSendFileBtn;
    public JFXTextField sendFileIpEdit;
    public JFXTextField sendFilePortEdit;
    public Label logLabel;
    public JFXTextField sendFileLabel;

    private File dragInFile;

    public String isDragFileOrDirectory() {
        var file = getDragInFile();
        if (file == null) {
            return "";
        }
        if (file.exists()) {
            if (file.isFile()) {
                return "file";
            } else {
                return "dir";
            }
        }
        return "";
    }

    /**
     * 获取被拖入的文件
     */
    public File getDragInFile() {
        return dragInFile;
    }

    public void init(Stage stage) {
        super.init(stage);
        new TransferController2(this);

        logText.set("服务器先去准备。");

        this.logLabel.textProperty().bind(this.logText);

        EventHandler<DragEvent> dragOver = event-> {
            if (event.getDragboard() != null && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
        };
        EventHandler<DragEvent> dragDrop = event -> {
            Log.d("drag dropped!");
            List<File> currentDropped = event.getDragboard().getFiles();
            if (currentDropped != null && currentDropped.size() >= 1) {
                dragInFile = currentDropped.get(0);
                sendFileLabel.setText(dragInFile.getAbsolutePath());
            }
        };
        //因为我们默认它显示；直接上来直接设置tabPane即可。
        sendFileLabel.setOnDragOver(dragOver);
        sendFileLabel.setOnDragDropped(dragDrop);
    }
}

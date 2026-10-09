package com.allan.atools.ui.controls;

import com.jfoenix.controls.JFXTreeView;
import javafx.scene.control.TreeItem;

public final class DirAndFileJFXTreeView<T> extends JFXTreeView<T> {
    public DirAndFileJFXTreeView() {
        super();
        this.setCellFactory((view) -> new DirAndFileJFXTreeCell<>(false));
    }

    public DirAndFileJFXTreeView(TreeItem<T> root) {
        super(root);
        this.setCellFactory((view) -> new DirAndFileJFXTreeCell<>(false));
    }
}

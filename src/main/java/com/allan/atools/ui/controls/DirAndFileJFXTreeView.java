package com.allan.atools.ui.controls;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
public final class DirAndFileJFXTreeView<T> extends TreeView<T> {
    private static final String DEFAULT_STYLE_CLASS = "jfx-tree-view";
    public DirAndFileJFXTreeView() {
        super();
        init();
    }

    public DirAndFileJFXTreeView(TreeItem<T> root) {
        super(root);
        init();
    }

    private void init() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
        this.setCellFactory((view) -> new DirAndFileJFXTreeCell<>(false));
    }
}

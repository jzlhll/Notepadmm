package com.allan.uilibs.controls;

import javafx.scene.Node;
import javafx.scene.control.TreeItem;

public final class TreeItemEx<T> extends TreeItem<T> {
    public Object ex;
    /**
     * Creates an empty TreeItem.
     */
    public TreeItemEx() {
        super();
    }

    /**
     * Creates a TreeItem with the value property set to the provided object.
     *
     * @param value The object to be stored as the value of this TreeItem.
     */
    public TreeItemEx(final T value) {
        super(value);
    }

    /**
     * Creates a TreeItem with the value property set to the provided object, and
     * the graphic set to the provided Node.
     *
     * @param value The object to be stored as the value of this TreeItem.
     * @param graphic The Node to show in the TreeView next to this TreeItem.
     */
    public TreeItemEx(final T value, final Node graphic) {
        super(value, graphic);
    }
}

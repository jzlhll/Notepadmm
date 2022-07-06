package com.allan.atools.text;

import javafx.scene.control.ContextMenu;
import org.fxmisc.richtext.GenericStyledArea;

public interface IAreaEx<PS, SEG, S> {
    ContextMenu createMenu();
    void setFileEncoding(String encoding);
    String getFileEncoding();
    GenericStyledArea<PS, SEG, S> getArea();
    void destroy();
    boolean isDestroyed();
}

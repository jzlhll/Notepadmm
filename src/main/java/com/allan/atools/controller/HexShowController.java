package com.allan.atools.controller;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import javafx.scene.layout.AnchorPane;

@XmlPaths(paths = {"notepad", "hex_show.fxml"})
public final class HexShowController extends AbstractController {
    public AnchorPane outAnchorPane;
}

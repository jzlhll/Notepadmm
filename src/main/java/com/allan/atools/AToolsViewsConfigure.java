package com.allan.atools;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.controller.ColorController;
import com.allan.atools.controller.JsonFormatController;
import javafx.collections.ObservableList;

import java.util.Map;

public final class AToolsViewsConfigure {
    public int load(Map<Integer, Class<? extends AbstractController>> pages, ObservableList<String> names) {
        int indexCount = 0;
        pages.put(indexCount++, JsonFormatController.class);
        names.add("json");
        pages.put(indexCount++, ColorController.class);
        names.add("color");

        return indexCount;
    }
}

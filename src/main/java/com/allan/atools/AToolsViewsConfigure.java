package com.allan.atools;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.controller.ColorController;
import com.allan.atools.controller.JsonFormatController;
import com.allan.atools.controller.TransferController;
import com.allan.atools.utils.ResLocation;
import javafx.collections.ObservableList;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public final class AToolsViewsConfigure {
    public int load(Map<Integer, Class<? extends AbstractController>> pages, ObservableList<String> names) throws MalformedURLException {
        int indexCount = 0;
        pages.put(indexCount++, JsonFormatController.class);
        names.add("json");
        pages.put(indexCount++, ColorController.class);
        names.add("color");
        pages.put(indexCount++, TransferController.class);
        names.add("transfer");

        return indexCount;
    }
}

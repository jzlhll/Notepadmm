package com.allan.atools;

import com.allan.atools.bases.AbstractController;
import com.allan.atools.controller.*;
import javafx.collections.ObservableList;

import java.net.MalformedURLException;
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
        pages.put(indexCount++, NumbersGameController.class);
        names.add("numbers");
        pages.put(indexCount++, FfmpegController.class);
        names.add("ffmpeg");

        return indexCount;
    }
}

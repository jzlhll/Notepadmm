package com.allan.atools.beans;

import com.allan.atools.bean.FileEncodingMap;

import java.io.File;
import java.util.List;

public final class FileEncodingMaps {
    public List<FileEncodingMap> list;

    public void removeNotExist() {
        list.removeIf(map -> !new File(map.file()).exists());
    }

    public void removeDuplicate() {
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(j).file().equals(list.get(i).file())) {
                    list.remove(j);
                    i--;
                    break;
                }
            }
        }
    }
}

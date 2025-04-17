package com.allan.baseparty.content;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class UrglyConfigFileUtils {
    public static final String SPLITS = "#Sv#";
    public static final String KEY_PREFIX = "#Sk#";
    public static final int KEY_PREFIX_LEN = KEY_PREFIX.length();
    public static final int VAL_PREFIX_LEN = SPLITS.length();

    static Map<String, Object> readFromLines(File file) {
        var map = new HashMap<String, Object>();
        if(!file.exists()) return map;
        List<String> lines = null;
        try {
            lines = Files.readAllLines(Paths.get(file.getAbsolutePath()));

        } catch (IOException e) {
            e.printStackTrace();
        }
        if(lines == null || lines.size() == 0)
            return map;
        if (!lines.get(0).startsWith(KEY_PREFIX)) {
            return map;
        }

        for(int i = 0, count = lines.size(); i < count; i++) {
            StringBuilder line = new StringBuilder(lines.get(i));
            while(i + 1 < count && !lines.get(i + 1).startsWith(KEY_PREFIX)) {
                line.append(lines.get(i + 1));
                i++;
            }
            line = new StringBuilder(line.substring(KEY_PREFIX_LEN));
            int valueIndex = line.indexOf(SPLITS);
            if (valueIndex > 0) {
                var key = line.substring(0, valueIndex);
                var value = line.substring(valueIndex + VAL_PREFIX_LEN);
                map.put(key, value);
            }
        }
        return map;
    }

    static boolean writeMap(Map<String, Object> map, File file) {
        if (map != null) {
            try {
                StringBuilder sb = null;
                for (var key : map.keySet()) {
                    Object value = map.get(key);
                    if (sb == null) {
                        sb = new StringBuilder();
                    } else {
                        sb.append('\n');
                    }
                    sb.append(KEY_PREFIX).append(key).append(SPLITS).append(value);
                }
                Files.writeString(Paths.get(file.getAbsolutePath()), sb == null ? "" : sb);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }
}

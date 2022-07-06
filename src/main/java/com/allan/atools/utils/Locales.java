package com.allan.atools.utils;

import com.allan.atools.UIContext;

import java.io.File;
import java.io.FileReader;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public final class Locales {
    private static ResourceBundle resource;

    public static final int INDEX_ENG = 0;
    public static final int INDEX_CN_ZH = 1;
    public static final int INDEX_CN_TW = 2;
    public static final int INDEX_NONE = -1;

    public static final String LOCALES_KEY = "localesIndex";
    public static int getLocalesIndex() {
        var loc = Locale.getDefault();
        var locale = loc.getLanguage() + "_" + loc.getCountry() + ".properties";
        return switch (locale) {
            case "zh_CN.properties" -> INDEX_CN_ZH;
            case "zh_TW.properties" -> INDEX_CN_TW;
            default -> INDEX_ENG;
        };
    }

    private static File getLocaleFile() {
        var index = UIContext.sharedPref.getInt(LOCALES_KEY, INDEX_NONE);

        String locale;
        switch (index) {
            case INDEX_ENG -> locale = "strings.properties";
            case INDEX_CN_ZH -> locale = "strings_zh_CN.properties";
            case INDEX_CN_TW -> locale = "strings_zh_TW.properties";
            default -> {
                var loc = Locale.getDefault();
                locale = loc.getLanguage() + "_" + loc.getCountry() + ".properties";
            }
        }

        var dir = ResLocation.getRealPath("locales");
        var files = new File(dir).listFiles();
        assert files != null;
        for (var file : files) {
            var name = file.getName();
            if (name.endsWith(locale)) {
                return file;
            }
        }

        return new File(ResLocation.getRealPath("locales", "strings.properties"));
    }

    static {
        var loc = getLocaleFile();
        try (var reader = new FileReader(loc)) {
            resource = new PropertyResourceBundle(reader);
        } catch (Exception e) {
            Log.e("加载字体失败", e);
        }
    }

    public static ResourceBundle getResource() {return resource;}

    public static String str(String key) {
        try {
            return resource.getString(key);
        } catch (Exception exception) {
            return "";
        }
    }

    public static String ALERT() {
        try {
            return resource.getString("notification");
        } catch (Exception exception) {
            return "";
        }
    }
}

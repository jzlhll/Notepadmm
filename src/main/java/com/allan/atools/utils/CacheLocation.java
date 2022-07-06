package com.allan.atools.utils;

import com.allan.atools.SettingPreferences;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CacheLocation {
    private static final String ROOT;
    private static final String ROOT_CACHE_PATH;

    static {
        String s = System.getProperty("user.home");
        if (s.endsWith(File.separator)) {
            ROOT = s;
            ROOT_CACHE_PATH = ROOT + ".atools_notepadmm" + File.separatorChar;
        } else {
            ROOT = s + File.separatorChar;
            ROOT_CACHE_PATH = s + File.separatorChar + ".atools_notepadmm" + File.separatorChar;
        }
    }

    private static Boolean isSystemExist = null;

    public static final String CustomFontFamily = "customFontFamily";
    public static final String CustomFontSize = "customFontSize";

    public static String fontSizeFile() {
        File rootDir = new File(CacheLocation.get_font_size_cust_dot_css());
        if (rootDir.exists() && rootDir.isFile()) {
            return rootDir.getAbsolutePath();
        }

        return ResLocation.getRealPath("css", "font_size.css");
    }

    public static String fontFamilyFile(int id) {
        if (id < 0) {
            var fontFamilyIndex = SettingPreferences.getInt(SettingPreferences.fontThemeIdKey);
            if (fontFamilyIndex == 0) {
                return ResLocation.getRealPath("css", "editor_font.css");
            } else {
                String c = get_editor_font_cust_dot_css();
                File rootDir = new File(c);
                if (rootDir.exists() && rootDir.isFile()) {
                    return rootDir.getAbsolutePath();
                }

                return ResLocation.getRealPath("css", "editor_font.css");
            }
        } else {
            if (id == 0) {
                return ResLocation.getRealPath("css", "editor_font.css");
            } else {
                String c = get_editor_font_cust_dot_css();
                File rootDir = new File(c);
                if (rootDir.exists() && rootDir.isFile()) {
                    return rootDir.getAbsolutePath();
                }

                return ResLocation.getRealPath("css", "editor_font.css");
            }
        }
    }

    private static void assetCacheRootPath() {
        if (isSystemExist == null) {
            var f = new File(ROOT);
            if (f.exists() && f.isDirectory()) {
               isSystemExist = Boolean.TRUE;
            } else {
                isSystemExist = Boolean.FALSE;
            }
        }

        if (isSystemExist) {
            if (!new File(ROOT_CACHE_PATH).exists()) {
                try {
                    Files.createDirectory(Path.of(ROOT_CACHE_PATH));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String getCachePath(String... paths) {
        if (paths == null || paths.length == 0)
            return ROOT_CACHE_PATH;
        StringBuilder sb = new StringBuilder(ROOT_CACHE_PATH);
        int i = 0;
        for (int len = paths.length; i < len - 1; i++)
            sb.append(paths[i]).append(File.separatorChar);
        sb.append(paths[i]);
        return sb.toString();
    }

    public static String get(String... s) {
        assetCacheRootPath();
        return getCachePath(s);
    }

    public static String getUserConfigFile() {
        return get("user.cfg");
    }

    public static String get_editor_font_cust_dot_css() {
        return get("editor_font_cust.css");
    }

    public static String get_font_size_cust_dot_css() {
        return get("font_size_cust.css");
    }

    public static String getAdvanceSearchesFile() {
        return get("advanceSearches.cfg");
    }

    public static String getRecentFiles() {
        return get("recentFiles.cfg");
    }

    public static String getMapFileAndEncoding() {
        return get("fileEncodingMappings.cfg");
    }

    public static String getLogRoot() {
        String s = get("log");
        var f = new File(s);
        if (!f.exists()) {
            try {
                Files.createDirectory(Path.of(s));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return s;
    }
}

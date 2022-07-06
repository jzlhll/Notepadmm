package com.allan.atools;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.HashMap;

public final class SettingPreferences {
    static final class SettingProfDef {
        final String type;
        final String keyName;
        final String defaultValue;
        SettingProfDef(String t, String k, String dv) {
            type = t;
            keyName = k;
            defaultValue = dv;
        }
    }

    private static HashMap<String, Property<?>> map;
    private static HashMap<String, Property<?>> getMap() {
        if (map == null) {
            assetInit();
        }
        return map;
    }

    public static final String searchResultAreaIsWrapKey = "searchResultAreaIsWrap"; //***** add一处新名字用于外部调用
    public static final String saveLastOpenedFileKey = "saveLastOpenedFile";
    public static final String resultAreaInNewWindowKey = "resultAreaInNewWindow";
    public static final String searchResultHasNumberKey = "searchResultHasNumber";
    public static final String editHasNumberKey = "editAreaHasNumber";
    public static final String newFileDirKey = "newFileDir";
    public static final String appVisionKey = "appVision";
    public static final String hdScreen2Key = "hdScreen2";
    public static final String cycleNextKey = "bottomCycleNext";
    public static final String fontThemeIdKey = "fontThemeId";
    public static final String editorFontSizeKey = "resultAreaFoldableStyledAreaFontSize";
    public static final String TipsDoubleClickCtrlFKey = "TipsDoubleClickCtrlF1";
    public static final String TipsFakeRuleSupportKey = "TipsFakeRuleSupport1";
    public static final String TipsDoubleClickWordNextKey = "TipsDoubleClickWordNext1";
    public static final String forceBigStylerKey = "forceBigStyler";

    public static final String[] REMOVE_KEYS = { "search1LineOnlyOnce","useFakeRegex",
            "TipsDoubleClickCtrlF","TipsFakeRuleSupport",
            "TipsDoubleClickWordNext" };

    private synchronized static void assetInit() {
        SettingProfDef[] defs = new SettingProfDef[] { //***** add 这2处即可
                new SettingProfDef("bool", searchResultAreaIsWrapKey, "false"),
                new SettingProfDef("bool", saveLastOpenedFileKey, "true"),
                new SettingProfDef("bool", resultAreaInNewWindowKey, "false"),
                //new SettingProfDef("bool", search1LineOnlyOnceKey, "true"),
                new SettingProfDef("bool", searchResultHasNumberKey, "true"),
                new SettingProfDef("bool", editHasNumberKey, "true"),
                new SettingProfDef("str", newFileDirKey, ""),
                new SettingProfDef("bool", appVisionKey, "false"),
                new SettingProfDef("bool", hdScreen2Key, "true"),
                new SettingProfDef("bool", cycleNextKey, "true"),
                new SettingProfDef("bool", forceBigStylerKey, "false"),
                new SettingProfDef("int", fontThemeIdKey, "0"),
                new SettingProfDef("int", editorFontSizeKey, "15"),
                new SettingProfDef("int", TipsDoubleClickCtrlFKey, "0"),
                new SettingProfDef("int", TipsFakeRuleSupportKey, "0"),
                new SettingProfDef("int", TipsDoubleClickWordNextKey, "0"),
        };

        map = new HashMap<>(8);
        var sp = UIContext.sharedPref;
        assert sp != null;

        for (var config : defs) {
            if (sp.contains(config.keyName)) {
                if ("str".equals(config.type)) {
                    var v = sp.getString(config.keyName, config.defaultValue);
                    var prop = new SimpleStringProperty(v);
                    map.put(config.keyName, prop);
                } else if ("int".equals(config.type)) {
                    var v = sp.getInt(config.keyName, Integer.parseInt(config.defaultValue));
                    var prop = new SimpleIntegerProperty(v);
                    map.put(config.keyName, prop);
                } else if ("bool".equals(config.type)) {
                    var v = sp.getBoolean(config.keyName, Boolean.parseBoolean(config.defaultValue));
                    var prop = new SimpleBooleanProperty(v);
                    map.put(config.keyName, prop);
                }
            } else {
                if ("str".equals(config.type)) {
                    var prop = new SimpleStringProperty(config.defaultValue);
                    map.put(config.keyName, prop);
                } else if ("int".equals(config.type)) {
                    var prop = new SimpleIntegerProperty(Integer.parseInt(config.defaultValue));
                    map.put(config.keyName, prop);
                } else if ("bool".equals(config.type)) {
                    var prop = new SimpleBooleanProperty(Boolean.parseBoolean(config.defaultValue));
                    map.put(config.keyName, prop);
                }
            }
        }

        for (var removedKey : REMOVE_KEYS) {
            removeKey(removedKey);
        }
    }

    public static SimpleIntegerProperty getIntProp(String key) {
        return (SimpleIntegerProperty) getMap().get(key);
    }

    public static SimpleBooleanProperty getBoolProp(String key) {
        return (SimpleBooleanProperty) getMap().get(key);
    }

    public static SimpleStringProperty getStringProp(String key) {
        return (SimpleStringProperty) getMap().get(key);
    }

    public static int getInt(String key) {
        SimpleIntegerProperty prop = (SimpleIntegerProperty) getMap().get(key);
        return prop.getValue();
    }

    public static boolean getBoolean(String key) {
        SimpleBooleanProperty prop = (SimpleBooleanProperty) getMap().get(key);
        return prop.getValue();
    }

    public static String getStr(String key) {
        SimpleStringProperty prop = (SimpleStringProperty) getMap().get(key);
        return prop.getValue();
    }

    public static void updateInt(String key, int value) {
        SimpleIntegerProperty prop = (SimpleIntegerProperty) getMap().get(key);
        prop.set(value);
        assert UIContext.sharedPref != null;
        UIContext.sharedPref.edit().putInt(key, value).commit();
    }

    public static void updateBool(String key, boolean value) {
        SimpleBooleanProperty prop = (SimpleBooleanProperty) getMap().get(key);
        prop.set(value);

        assert UIContext.sharedPref != null;
        UIContext.sharedPref.edit().putBoolean(key, value).commit();
    }

    public static void updateStr(String key, String value) {
        SimpleStringProperty prop = (SimpleStringProperty) getMap().get(key);
        prop.set(value);
        assert UIContext.sharedPref != null;
        UIContext.sharedPref.edit().putString(key, value).commit();
    }

    private static void removeKey(String key) {
        assert UIContext.sharedPref != null;
        UIContext.sharedPref.edit().remove(key);
    }
}

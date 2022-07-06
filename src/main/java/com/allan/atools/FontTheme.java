package com.allan.atools;

public final class FontTheme {
    public static String fontFamily() {
        return "styled-text-area";
    }

    public static String fontFamily(int id) {
        return "styled-text-area";
    }
//    public static String fontFamily() {
//        var theme = SettingsProf.getInt(SettingsProf.fontThemeIdKey);
//        boolean hasEditorCustFile = !TextUtils.isEmpty(UIContext.sharedPref.getString("custom_font_path", null));
//        if (hasEditorCustFile && theme == 1) {
//            return "styled-text-area-custom";
//        }
//        return "styled-text-area-jbmono";
//        //return "styled-text-area";
//    }
//
//    public static String fontFamily(int id) {
//        boolean hasEditorCustFile = !TextUtils.isEmpty(UIContext.sharedPref.getString("custom_font_path", null));
//        if (hasEditorCustFile && id == 1) {
//            return "styled-text-area-custom";
//        }
//        return "styled-text-area-jbmono";
//        //return "styled-text-area";
//    }
}

package com.allan.atools;

import com.allan.baseparty.ActionR0;
import javafx.scene.paint.Color;

public final class Colors {
    private Colors() {}

    public static boolean isDark() {
        return SettingPreferences.getBoolean(SettingPreferences.appVisionKey);
    }

    public final static ActionR0<String> ColorBottomBtnNormal = ()-> isDark() ? "#acacac" : "#404040";
    public final static ActionR0<String> ColorBottomBtnGray = () -> isDark() ? "#acacac" : "#cccccc";
    public final static ActionR0<String> ColorBottomBtnHighLight = () -> isDark() ? "#4285F5" : "#4285F4";

    public final static ActionR0<Color> ColorsMultiSelection = () -> Color.LIGHTGOLDENRODYELLOW;

    public final static ActionR0<String> ColorHeadButton = () -> isDark() ? "#cccccc" :"#222222";

    //如下2个必须是colorPickUtils中的数值
    public static final ActionR0<String> SearchTextColor = ()-> isDark() ? "#ffffff" : "#323232";

    public static final ActionR0<String> TextColor = ()-> isDark() ? "#ffffff" : "#000000";

    public static final ActionR0<String> SearchBgColor = ()-> isDark() ? "#323232": "#ffffff";

    public static final ActionR0<Color> DescLineTextColor = ()-> isDark() ? Color.valueOf("333333") : Color.valueOf("#555555");
    public static final ActionR0<Color> DescLineBgColor = ()-> isDark() ?  Color.valueOf("#292a2f") : Color.valueOf("fcfcfe");
}

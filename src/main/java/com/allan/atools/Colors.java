package com.allan.atools;

import com.allan.baseparty.ActionR0;
import javafx.scene.paint.Color;

public final class Colors {
    private Colors() {}

    public static boolean isDark() {
        return SettingPreferences.getBoolean(SettingPreferences.appVisionKey);
    }

    // 界面样式使用 CSS 颜色变量，随主题即时刷新。
    public static final String ColorBottomBtnNormal = "-au-bottom-button-color";
    public static final String ColorBottomBtnGray = "-au-bottom-button-gray-color";
    public static final String ColorBottomBtnHighLight = "-au-button-highlight-color";

    public final static ActionR0<Color> ColorsMultiSelection = () -> Color.LIGHTGOLDENRODYELLOW;

    public static final String ColorHeadButton = "-au-head-button-color";
    public static final String TextColor = "-au-popup-text-color";

    //如下2个必须是colorPickUtils中的数值
    public static final ActionR0<String> SearchTextColor = ()-> isDark() ? "#ffffff" : "#323232";

    public static final ActionR0<String> SearchBgColor = ()-> isDark() ? "#323232": "#ffffff";

    public static final ActionR0<Color> DescLineTextColor = ()-> isDark() ? Color.valueOf("#999999") : Color.valueOf("#555555");
    public static final ActionR0<Color> DescLineBgColor = ()-> isDark() ?  Color.valueOf("#292a2f") : Color.valueOf("fcfcfe");
}

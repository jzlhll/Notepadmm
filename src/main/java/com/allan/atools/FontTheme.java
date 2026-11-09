package com.allan.atools;

/** 编辑器样式类和实际字体名分别提供，避免在 WebView 或字体属性中误用类名。 */
public final class FontTheme {
    public static String fontFamily() {
        return "JetBrains Mono";
    }

    public static String styleClassName() {
        return "styled-text-area";
    }

    public static String styleClassName(int id) {
        return "styled-text-area";
    }
}

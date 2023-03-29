package com.allan.atools.ui;

import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.allan.uilibs.controls.MyJFXButton;
import com.jfoenix.controls.JFXButton;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class IconfontCreator {
    private static Font ICON_FONTS;
    private static String FONT_FAMILY;
    private static final Map<String, String> sGlyphMap = new HashMap<>(8);

    private static String decode(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\\' && chars[i + 1] == 'u') {
                char cc = 0;
                for (int j = 0; j < 4; j++) {
                    char ch = Character.toLowerCase(chars[i + 2 + j]);
                    if ('0' <= ch && ch <= '9' || 'a' <= ch && ch <= 'f') {
                        cc |= (Character.digit(ch, 16) << (3 - j) * 4);
                    } else {
                        cc = 0;
                        break;
                    }
                }
                if (cc > 0) {
                    i += 5;
                    sb.append(cc);
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static void asset() {
        if (ICON_FONTS == null) {
            var p = ResLocation.getURLStr("font", "iconfont.ttf");
            var exist = new File(ResLocation.getRealPath("font", "iconfont.ttf")).exists();
            ICON_FONTS = Font.loadFont(p, 1.0D);
            if (ICON_FONTS != null) {
                FONT_FAMILY = ICON_FONTS.getFamily();
                try {
                    var list = Files.readAllLines(Path.of(ResLocation.getRealPath("font", "map.config")));
                    for (String line : list) {
                        var words = line.split(":");
                        sGlyphMap.put(words[0], words[1]);
                    }
                } catch (IOException | NullPointerException e) {
                    Log.e("can not has font?!", e);
                }
            }
            Log.w("icon font path: " + p + " ICON_FONTS " + ICON_FONTS);
        }
    }

    private static void setText(Labeled button, String iconFontName, int fontSize) {
        asset();
        button.setFont(Font.font(FONT_FAMILY, fontSize));
        String text = sGlyphMap.get(iconFontName);
        button.setText((decode(text)));
    }

    public static void setText(Labeled button, String iconFontName, int fontSize, String color) {
        asset();
        var f = Font.font(FONT_FAMILY, fontSize);
        button.setFont(f);
        button.setStyle("-fx-text-fill:" + color);
        String text = sGlyphMap.get(iconFontName);
        button.setText((decode(text)));
    }

    public static void setTextBold(Labeled button, String iconFontName, int fontSize, String color) {
        asset();
        var f = Font.font(FONT_FAMILY, FontWeight.BOLD, fontSize);
        button.setFont(f);
        button.setStyle("-fx-text-fill:" + color);
        String text = sGlyphMap.get(iconFontName);
        button.setText((decode(text)));
    }

    public static MyJFXButton createJFXButton(String iconFontName, int fontSize, String realName) {
        asset();
        Label lbl = new Label();

        lbl.setFont(Font.font(FONT_FAMILY, fontSize));
        var text = sGlyphMap.get(iconFontName);
        lbl.setText(decode(text));
        MyJFXButton button = new MyJFXButton(realName, lbl);
        button.setWrapText(true);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setButtonType(JFXButton.ButtonType.FLAT);
        return button;
    }

    public static JFXButton createJFXButton(String iconFontName, int fontSize) {
        return createJFXButton(iconFontName, fontSize, "");
    }

    public static Label createLabel(String iconFontName, int fontSize, String realName) {
        asset();
        Label lbl = new Label();

        lbl.setFont(Font.font(FONT_FAMILY, fontSize));
        var text = sGlyphMap.get(iconFontName);
        lbl.setText(decode(text));

        return lbl;
    }
}

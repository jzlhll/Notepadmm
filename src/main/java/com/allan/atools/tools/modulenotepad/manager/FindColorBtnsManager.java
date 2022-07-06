package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.controller.NotepadFindController;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.atools.bean.SearchParams;
import com.allan.uilibs.controls.MyJFXButton;
import com.allan.atools.FontTheme;
import com.allan.atools.SettingPreferences;
import com.allan.baseparty.ActionR3;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public final class FindColorBtnsManager {

    private final NotepadFindController controller;

    private Button actioningColorBtn;

    private SearchParams actioningSearchParam;

    public static final ActionR3<String, String, String, String> ACTIONINGSTYLE =
            (s1, s2, s3) ->
                    String.format("-fx-background-color:%s;-fx-text-fill:%s;-fx-min-height: 16;-fx-font-size: 12.0px;-fx-font-family: %s;", s1, s2, s3);
    private static final ActionR3<String, String, String, String> ACTIONING2STLE =
            (s1, s2, s3) ->
                    String.format("-fx-background-color:%s;-fx-text-fill:%s;-fx-min-height: 16;-fx-font-size: 13.0px;-fx-font-family: %s;", s1, s2, s3);

    public FindColorBtnsManager(NotepadFindController controller) {
        this.controller = controller;
    }

    private String colorStringToMyStr(Color color) {
        int var1 = (int)Math.round(color.getRed() * 255.0D);
        int var2 = (int)Math.round(color.getGreen() * 255.0D);
        int var3 = (int)Math.round(color.getBlue() * 255.0D);
        return String.format("#%02x%02x%02x", var1, var2, var3).toLowerCase();
    }

    private void resetEnterBtnForList() {
        controller.colorPickerEnterBtn.setOnMouseClicked(null);
        controller.colorPickerEnterBtn.setOnMouseClicked(mouseEvent -> {
            controller.floatColorsBox.setVisible(false);
            controller.theFloatWindowPane.setVisible(false);
            controller.advanceBtnsBox.setVisible(true);

            var co =  controller.textColorPicker.getValue();
            var co2 =  controller.bgColorPicker.getValue();
            Log.d("textColorPicker.getValue() " + co);
            Log.d("bgColorPicker.getValue() " + co2);
            actioningSearchParam.textColor = colorStringToMyStr( controller.textColorPicker.getValue());
            actioningSearchParam.bgColor = colorStringToMyStr( controller.bgColorPicker.getValue());
            Log.d("close ： " + actioningSearchParam);
            var style = ACTIONINGSTYLE.invoke(actioningSearchParam.bgColor, actioningSearchParam.textColor, FontTheme.fontFamily());
            actioningColorBtn.setStyle(style);
            controller.saveAdvanceParams();
        });
    }

    private void resetEnterBtnForNormal() {
        controller.colorPickerEnterBtn.setOnMouseClicked(null);
        controller.colorPickerEnterBtn.setOnMouseClicked(mouseEvent -> {
            controller.floatColorsBox.setVisible(false);
            controller.theFloatWindowPane.setVisible(false);
            controller.advanceBtnsBox.setVisible(true);
            controller.normalViewTopViewsHBox.setVisible(true);

            var bg = colorStringToMyStr( controller.bgColorPicker.getValue());
            var text = colorStringToMyStr( controller.textColorPicker.getValue());

            normalBgColor = bg;
            normalTextColor = text;
            var style = ACTIONING2STLE.invoke(bg, text, FontTheme.fontFamily());
            actioningColorBtn.setStyle(style);
            saveNormalColors(new String[]{bg, text});
        });
    }

    public void initListViewColorBtn(MyJFXButton colorBtn) {
        colorBtn.setOnMouseClicked(e -> {
            var btn = (MyJFXButton) e.getSource();
            actioningColorBtn = btn;
            if (btn.ex instanceof SearchParams params) {
                actioningSearchParam = params;
                controller.textColorPicker.setValue(Color.valueOf(params.textColor));
                controller.bgColorPicker.setValue(Color.valueOf(params.bgColor));
            }
            resetEnterBtnForList();
            Log.d("click colors btn: " + actioningSearchParam);

            controller.theFloatWindowPane.setVisible(true);
            controller.floatColorsBox.setVisible(true);
            controller.advanceBtnsBox.setVisible(false);
        });
        colorBtn.setPrefWidth(80);
        colorBtn.setText(Locales.str("colorTests"));
        var params = (SearchParams) colorBtn.ex;
        var style = ACTIONINGSTYLE.invoke(params.bgColor, params.textColor, FontTheme.fontFamily());
        colorBtn.setStyle(style);
    }

    public String normalTextColor, normalBgColor;

    public void initColorBtn(@NotNull Button colorBtn) {
        var colors = getSavedColors();
        normalBgColor = colors[0];
        normalTextColor = colors[1];
        var style = ACTIONINGSTYLE.invoke(colors[0], colors[1], FontTheme.fontFamily());
        colorBtn.setStyle(style);

        colorBtn.setOnMouseClicked(e -> {
            actioningColorBtn = (Button) e.getSource();
            resetEnterBtnForNormal();

            controller.theFloatWindowPane.setVisible(true);
            controller.floatColorsBox.setVisible(true);
            controller.advanceBtnsBox.setVisible(false);
            controller.normalViewTopViewsHBox.setVisible(false);

            controller.textColorPicker.setValue(Color.valueOf(normalTextColor));
            controller.bgColorPicker.setValue(Color.valueOf(normalBgColor));
        });
    }

    private static String[] getSavedColors() {
        boolean isDark = SettingPreferences.getBoolean(SettingPreferences.appVisionKey);
        if (!isDark) {
            String s = UIContext.sharedPref.getString("findNormalColorsLight", "#f9f9f9;#000000"); //backgoud;text
            return s.split(";");
        } else {
            String s = UIContext.sharedPref.getString("findNormalColorsDark", "#101010;#ffffff"); //backgoud;text
            return s.split(";");
        }
    }

    private static void saveNormalColors(String[] colors) {
        boolean isDark = SettingPreferences.getBoolean(SettingPreferences.appVisionKey);
        if (!isDark) {
            UIContext.sharedPref.edit().putString("findNormalColorsLight", colors[0] + ";" + colors[1]).commit(); //backgoud;text
        } else {
            UIContext.sharedPref.edit().putString("findNormalColorsDark", colors[0] + ";" + colors[1]).commit(); //backgoud;text
        }
    }
}

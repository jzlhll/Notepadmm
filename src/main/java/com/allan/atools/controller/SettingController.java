package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.AppTheme;
import com.allan.atools.GlobalCfgStores;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.pop.MenuOverlay;
import com.allan.atools.pop.PopupMenus;
import com.allan.atools.pop.impl.AppThemeChooseCreatorImpl;
import com.allan.atools.pop.impl.StringChooseCreatorImpl;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.ui.SegmentedControl;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.modulenotepad.manager.ResultAreaManager;
import com.allan.atools.utils.CacheLocation;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXScrollPane;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@XmlPaths(paths = {"notepad", "settings.fxml"})
public final class SettingController extends AbstractController {
    public ScrollPane vbox_id;

    public JFXToggleButton resultAreaWrapBtn;
    public JFXToggleButton openLastFileBtn;
    public JFXToggleButton resultAreaInNewBtn;
    public JFXToggleButton resultIfHasNumBtn;
    public JFXToggleButton editorLineNumberVisibleBtn;
    public JFXComboBox<AppTheme> appThemeComboBox;
    public JFXComboBox<String> localesComboBox;
    public JFXToggleButton hdScreenBtn2;
    public JFXToggleButton cycleNextBtn;

    public HBox mainUiSizeSegmentBox;
    public HBox fontThemeSegmentBox;
    public HBox fontCustomLinkBox;
    public Hyperlink fontCustomLink;

    private MenuOverlay appThemeOverlay;
    private MenuOverlay localesOverlay;

    /** 设置页四个控件的统一宽度：分段切换与下拉框框体等宽，纵向对齐。 */
    private static final double SETTING_CONTROL_WIDTH = 240;

    private static final List<KeyCode> POPUP_KEYS =
            List.of(KeyCode.F4, KeyCode.SPACE, KeyCode.UP, KeyCode.DOWN, KeyCode.ENTER);

    private static void fixedWidth(Region control, double width) {
        control.setMinWidth(width);
        control.setPrefWidth(width);
        control.setMaxWidth(width);
    }

    /** 浮层内容宽 = 组合框宽减去面板边框（3×2）与内边距（6×2）。 */
    private static double popupContentWidth(JFXComboBox<?> combo) {
        double comboWidth = combo.getWidth();
        if (comboWidth <= 0) {
            comboWidth = SETTING_CONTROL_WIDTH;
        }
        return Math.max(200, comboWidth - 18);
    }

    /**
     * 组合框只作显示控件：原生下拉在鼠标松开与按键松开时弹出，
     * 所以按下与松开都要拦截，改由场景内浮层显示，避免原生弹窗的缩放与尺寸跳动。
     */
    private void installOverlayCombo(JFXComboBox<?> combo, Runnable toggle) {
        combo.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            combo.requestFocus();
            event.consume();
        });
        combo.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            event.consume();
            toggle.run();
        });
        combo.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!POPUP_KEYS.contains(event.getCode())) {
                return;
            }
            event.consume();
            toggle.run();
        });
        combo.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (POPUP_KEYS.contains(event.getCode())) {
                event.consume();
            }
        });
    }

    private void initAppThemePopup(double width) {
        appThemeComboBox.getItems().setAll(AppTheme.values());
        appThemeComboBox.setValue(AppTheme.current());
        appThemeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                SettingPreferences.updateStr(SettingPreferences.appThemeKey, newValue.getId());
            }
        });
        fixedWidth(appThemeComboBox, width);
        installOverlayCombo(appThemeComboBox, this::toggleAppThemePopup);
    }

    private void toggleAppThemePopup() {
        if (appThemeOverlay != null && appThemeOverlay.isOpen()) {
            appThemeOverlay.close();
            return;
        }
        var content = new AppThemeChooseCreatorImpl(popupContentWidth(appThemeComboBox)).createPop(theme -> {
            if (appThemeOverlay != null) {
                appThemeOverlay.close();
            }
            appThemeComboBox.setValue(theme);
        });
        appThemeOverlay = PopupMenus.showPopupContent(appThemeComboBox, content, Side.BOTTOM, 0, 0);
    }

    private void initLocalesPopup(double width) {
        try {
            var names = Files.readAllLines(Path.of(ResLocation.getRealPath("locales", "locales.list")));
            localesComboBox.getItems().setAll(names);

            int language = GlobalCfgStores.user().getInt(Locales.LOCALES_KEY, -1);
            if (language == -1) {
                language = Locales.getLocalesIndex();
            }
            if (language < 0 || language >= names.size()) {
                language = 0;
            }
            localesComboBox.getSelectionModel().select(language);
            localesComboBox.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
                GlobalCfgStores.user().setInt(Locales.LOCALES_KEY, newValue.intValue());
            });

            fixedWidth(localesComboBox, width);
            installOverlayCombo(localesComboBox, this::toggleLocalesPopup);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void toggleLocalesPopup() {
        if (localesOverlay != null && localesOverlay.isOpen()) {
            localesOverlay.close();
            return;
        }
        var content = new StringChooseCreatorImpl(List.copyOf(localesComboBox.getItems()),
                localesComboBox.getSelectionModel().getSelectedIndex(),
                popupContentWidth(localesComboBox)).createPop(index -> {
            if (localesOverlay != null) {
                localesOverlay.close();
            }
            localesComboBox.getSelectionModel().select(index.intValue());
        });
        localesOverlay = PopupMenus.showPopupContent(localesComboBox, content, Side.BOTTOM, 0, 0);
    }

    /**
     * 自定义字体路径只切换文字本身：所在行始终参与布局并保持单行高度，
     * 这样「默认 / 自定义」来回切不会让下面的内容整体跳动。
     */
    private void visibleFontSelectedMode(boolean v) {
        fontCustomLink.setVisible(v);
        fontCustomLink.setVisited(false);
    }

    private long sFontChangedCount;

    private void initMainUiSizeMode(List<String> items, double width) {
        var control = new SegmentedControl(items, width);

        int mode = SettingPreferences.getInt(SettingPreferences.mainUiSizeModeKey);
        control.select(mode == 1 ? 1 : (mode == 2 ? 2 : 0));
        control.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            int selected = newValue.intValue();
            int newMode = selected == 1 ? 1 : (selected == 2 ? 2 : 0);
            SettingPreferences.updateInt(SettingPreferences.mainUiSizeModeKey, newMode);
            UIContext.context().applyMainUiSizeMode();
        });

        mainUiSizeSegmentBox.getChildren().setAll(control);
    }

    @Override
    public void init(Stage stage) {
        super.init(stage);

        // 四个控件共用同一宽度：以 240 为基准，文案更长（例如英文）时整体加宽，仍保持纵向对齐。
        var sizeItems = List.of(
                Locales.str("setting.mainUiSizeDefault"),
                Locales.str("setting.mainUiSizeLarge"),
                Locales.str("setting.mainUiSizeLarger"));
        double controlWidth = Math.max(SETTING_CONTROL_WIDTH, SegmentedControl.requiredWidth(sizeItems));

        initMainUiSizeMode(sizeItems, controlWidth);
        initLocalesPopup(controlWidth);

        fontCustomLink.setOnMouseClicked(event -> {
            if (sFontChangedCount > 2) {
                JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("oneStartupInitFontCount"));
                return;
            }

            var lastDir = GlobalCfgStores.user().getString("lastOpenDirs", null);
            File initialFile;
            do {
                if (lastDir != null && lastDir.length() >= 1) {
                    initialFile = new File(lastDir);
                    if (initialFile.exists() && initialFile.isDirectory()) {
                        break;
                    }
                }
                var initialDir = System.getProperty("user.dir");
                initialFile = new File(initialDir);
            } while(false);

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(Locales.str("selectFile"));
            fileChooser.setInitialDirectory(initialFile);
            fileChooser.setSelectedExtensionFilter(
                    new FileChooser.ExtensionFilter(Locales.str("fontFile"), "*"));
            File selectedFile = fileChooser.showOpenDialog(getStage());
            if (selectedFile != null) {
                fontCustomLink.setText(selectedFile.getAbsolutePath());
                ThreadUtils.execute(()-> {
                    var selectedFileURIStr = ResLocation.file2UrlStr(selectedFile);
                    var f = Font.loadFont(selectedFileURIStr, 12d);
                    String fontFamily = f != null ? f.getFamily() : null;
                    do {
                        if (fontFamily == null) {
                            break;
                        }
                        String copyName;
                        String copyToFilePath;
                        File copyToFile;
                        do {
                            copyName = String.format("font_custom%s.ttf", (int)(Math.random() * 100));
                            copyToFilePath = CacheLocation.get(copyName);
                            copyToFile = new File(copyToFilePath);
                        } while (copyToFile.exists());

                        var copyToPath = Path.of(copyToFilePath);

                        String editorFontCssFmt =
                                """
                                @font-face {
                                    src: url("%s");
                                }
                                .styled-text-area {
                                  -fx-font-family: "%s";
                                }
                                """;
                        var str = String.format(editorFontCssFmt, copyName, fontFamily);
                        var filePath = CacheLocation.get_editor_font_cust_dot_css();
                        var editorPath = Path.of(filePath);

                        try {
                            Files.writeString(editorPath, str);
                            if(copyToFile.exists()) Files.delete(copyToPath);
                            Files.copy(Path.of(selectedFile.getAbsolutePath()), copyToPath);
                        } catch (IOException e) {
                            e.printStackTrace();
                            break;
                        }

                        GlobalCfgStores.user().setString("custom_font_path", selectedFile.getAbsolutePath());
                        ThreadUtils.globalHandler().postDelayed(()-> {
                            Log.d("load custom font path... " + str);
                            Platform.runLater(()->{
                                sFontChangedCount++;
                                AllStagesManager.getInstance().replaceCustom(CacheLocation.CustomFontFamily, filePath);
                            });
                        }, 200);
                    } while(false);
                });
            }
        });

        fontCustomLink.setWrapText(false);

        var fontTheme = UIContext.getFontThemeProperty().get();
        var fontThemeControl = new SegmentedControl(List.of(
                Locales.str("defaultStr"), Locales.str("custom")), controlWidth);
        int fontThemeId = fontTheme == 1 ? 1 : 0;
        fontThemeControl.select(fontThemeId);
        visibleFontSelectedMode(fontThemeId == 1);

        fontThemeControl.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            int id = newValue.intValue() == 1 ? 1 : 0;
            visibleFontSelectedMode(id == 1);

            GlobalCfgStores.user().setInt(SettingPreferences.fontThemeIdKey, id);
            AllStagesManager.getInstance().replaceCustom(CacheLocation.CustomFontFamily, CacheLocation.fontFamilyFile(id));
            UIContext.updateFontThemeId(id);
        });

        fontThemeSegmentBox.getChildren().setAll(fontThemeControl);

        String customFilePath = GlobalCfgStores.user().getString("custom_font_path", Locales.str("setting.newFileDirectoryHint"));
        fontCustomLink.setText(customFilePath);

        cycleNextBtn.setSelected(SettingPreferences.getBoolean(SettingPreferences.cycleNextKey));
        cycleNextBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.cycleNextKey, newValue);
        });

        hdScreenBtn2.setSelected(SettingPreferences.getBoolean(SettingPreferences.hdScreen2Key));
        hdScreenBtn2.selectedProperty().addListener((observable, oldValue, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.hdScreen2Key, newValue);
        });

        resultAreaWrapBtn.setSelected(SettingPreferences.getBoolean(SettingPreferences.searchResultAreaIsWrapKey));
        resultAreaWrapBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.searchResultAreaIsWrapKey, newValue);
        });

        openLastFileBtn.setSelected(SettingPreferences.getBoolean(
                SettingPreferences.restoreSavedFilesOnStartupKey));
        openLastFileBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.restoreSavedFilesOnStartupKey, newValue);
        });

        resultAreaInNewBtn.setSelected(SettingPreferences.getBoolean(SettingPreferences.resultAreaInNewWindowKey));
        resultAreaInNewBtn.selectedProperty().addListener((observableValue, aBoolean, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.resultAreaInNewWindowKey, newValue);
            ResultAreaManager.Instance.switchResultStyle(newValue);
        });

        resultIfHasNumBtn.setSelected(SettingPreferences.getBoolean(SettingPreferences.searchResultHasNumberKey));
        resultIfHasNumBtn.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            SettingPreferences.updateBool(SettingPreferences.searchResultHasNumberKey, t1);
        });

        editorLineNumberVisibleBtn.setSelected(SettingPreferences.getBoolean(SettingPreferences.editorLineNumberVisibleKey));
        editorLineNumberVisibleBtn.selectedProperty().addListener((observableValue, oldValue, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.editorLineNumberVisibleKey, newValue);
        });

        initAppThemePopup(controlWidth);
        vbox_id.requestFocus();
    }
}

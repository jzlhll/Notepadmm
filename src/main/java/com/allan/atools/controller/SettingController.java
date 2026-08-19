package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.GlobalCfgStores;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.modulenotepad.manager.ResultAreaManager;
import com.allan.atools.utils.CacheLocation;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.allan.baseparty.handler.TextUtils;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXScrollPane;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@XmlPaths(paths = {"notepad", "settings.fxml"})
public final class SettingController extends AbstractController {
    public ScrollPane vbox_id;

    public JFXToggleButton resultAreaWrapBtn;
    public JFXToggleButton openLastFileBtn;
    public JFXToggleButton autoSaveOnExitBtn;
    public JFXToggleButton resultAreaInNewBtn;
    public JFXToggleButton resultIfHasNumBtn;
    public JFXToggleButton editorLineNumberVisibleBtn;
    public JFXToggleButton visionBtn;
    public JFXToggleButton hdScreenBtn2;
    public JFXToggleButton cycleNextBtn;

    public JFXRadioButton fontThemeDefaultBtn; //jb mono
    public JFXRadioButton fontThemeCustomBtn;
    public JFXRadioButton mainUiSizeDefaultBtn;
    public JFXRadioButton mainUiSizeLargeBtn;
    public JFXRadioButton mainUiSizeLargerBtn;
    public Hyperlink fontCustomLink;
    public Hyperlink newFileDirLink;

    public JFXComboBox<Label> localesComboBox;

    private void visibleFontSelectedMode(boolean v) {
        fontCustomLink.setVisible(v);
        fontCustomLink.setVisited(false);
    }

    private long sFontChangedCount;

    private void initMainUiSizeMode() {
        ToggleGroup group = new ToggleGroup();
        mainUiSizeDefaultBtn.setToggleGroup(group);
        mainUiSizeLargeBtn.setToggleGroup(group);
        mainUiSizeLargerBtn.setToggleGroup(group);

        int mode = SettingPreferences.getInt(SettingPreferences.mainUiSizeModeKey);
        if (mode == 1) {
            group.selectToggle(mainUiSizeLargeBtn);
        } else if (mode == 2) {
            group.selectToggle(mainUiSizeLargerBtn);
        } else {
            group.selectToggle(mainUiSizeDefaultBtn);
        }

        group.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            int newMode;
            if (mainUiSizeLargeBtn.isSelected()) {
                newMode = 1;
            } else if (mainUiSizeLargerBtn.isSelected()) {
                newMode = 2;
            } else {
                newMode = 0;
            }
            SettingPreferences.updateInt(SettingPreferences.mainUiSizeModeKey, newMode);
            UIContext.context().applyMainUiSizeMode();
        });
    }

    @Override
    public void init(Stage stage) {
        super.init(stage);

        initMainUiSizeMode();

        try {
           var names = Files.readAllLines(Path.of(ResLocation.getRealPath("locales", "locales.list")));
           for (var name : names) {
               localesComboBox.getItems().add(new Label(name));
           }
        } catch (IOException e) {
            e.printStackTrace();
        }
        var language = GlobalCfgStores.user().getInt(Locales.LOCALES_KEY, -1);
        if (language == -1) {
            language = Locales.getLocalesIndex();
        }
        localesComboBox.getSelectionModel().select(language);
        localesComboBox.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            GlobalCfgStores.user().setInt(Locales.LOCALES_KEY, newValue.intValue());
        });

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

        fontCustomLink.setWrapText(true);

        var fontTheme = UIContext.getFontThemeProperty().get();
        fontThemeDefaultBtn.setSelected(false);
        fontThemeCustomBtn.setSelected(false);

        ToggleGroup toggleGroup = new ToggleGroup();
        fontThemeDefaultBtn.setToggleGroup(toggleGroup);
        fontThemeCustomBtn.setToggleGroup(toggleGroup);

        if (fontTheme == 0) {
            toggleGroup.selectToggle(fontThemeDefaultBtn);
            visibleFontSelectedMode(false);
        } else if (fontTheme == 1) {
            toggleGroup.selectToggle(fontThemeCustomBtn);
            visibleFontSelectedMode(true);
        }

        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            var isDef = fontThemeDefaultBtn.isSelected();
            int id;
            if (isDef) {
                id = 0;
                visibleFontSelectedMode(false);
            } else {
                id = 1;
                visibleFontSelectedMode(true);
            }

            GlobalCfgStores.user().setInt(SettingPreferences.fontThemeIdKey, id);
            AllStagesManager.getInstance().replaceCustom(CacheLocation.CustomFontFamily, CacheLocation.fontFamilyFile(id));
            UIContext.updateFontThemeId(id);
        });

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

        openLastFileBtn.setSelected(SettingPreferences.getBoolean(SettingPreferences.saveLastOpenedFileKey));
        openLastFileBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.saveLastOpenedFileKey, newValue);
        });

        autoSaveOnExitBtn.setSelected(SettingPreferences.getBoolean(SettingPreferences.autoSaveOnExitKey));
        autoSaveOnExitBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.autoSaveOnExitKey, newValue);
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

        var newFileDirLinkText = SettingPreferences.getStr(SettingPreferences.newFileDirKey);
        var HINT = Locales.str("setting.newFileDirectoryHint");
        if (TextUtils.isEmpty(newFileDirLinkText)) {
            newFileDirLinkText = HINT;
        }
        newFileDirLink.setText(newFileDirLinkText);

        newFileDirLink.setOnAction(event -> {
            DirectoryChooser directoryChooser= new DirectoryChooser();
            File file = directoryChooser.showDialog(stage);
            if (file == null) {
                return;
            }
            String s = file.getAbsolutePath();
            if (s.length() <= 0 || HINT.equals(s)) {
                return;
            }
            if (file.exists() && file.isDirectory()) {
                SettingPreferences.updateStr(SettingPreferences.newFileDirKey, s);
                newFileDirLink.setText(s);
            } else {
                JfoenixDialogUtils.alert(Locales.str("error"), Locales.str("setting.dirIsWrong"));
            }
        });

        visionBtn.setSelected(SettingPreferences.getBoolean(SettingPreferences.appVisionKey));
        visionBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            SettingPreferences.updateBool(SettingPreferences.appVisionKey, newValue);
        });
        vbox_id.requestFocus();
    }
}

package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.Colors;
import com.allan.atools.SettingPreferences;
import com.allan.atools.UIContext;
import com.allan.atools.controllerwindow.NotepadFindWindow;
import com.allan.atools.controllerwindow.NotepadMultiSelectionWindow;
import com.allan.atools.pop.impl.FontSizeChooseCreatorImpl;
import com.allan.atools.pop.impl.NotepadFileCreatorImpl;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.*;
import com.allan.atools.ui.IconfontCreator;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.pop.GlobalPopupManager;
import com.allan.atools.ui.SnackbarUtils;
import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Calendar;
import java.util.Optional;

public final class NotepadHeadButtons {
    public static void refreshSize() {
        var main = UIContext.context();
        IconfontCreator.setText(main.notepadMainActionBarAToolsBtn, "smile",
                main.getMainTopIconSize(25), Colors.ColorHeadButton.invoke());
        IconfontCreator.setTextBold(main.notepadMainAltMultiSelectBtn, "align-vertical-top",
                main.getMainTopIconSize(21), Colors.ColorBottomBtnHighLight.invoke());
        IconfontCreator.setText(main.notepadMainActionBarFontBtn, "font-size",
                main.getMainTopIconSize(22), Colors.ColorHeadButton.invoke());
        main.notepadMainInsertEmptyLineBtn.setStyle(
                "-fx-font-size:" + main.getMainTopIconSize(21) + "px;");
        IconfontCreator.setText(main.notepadMainActionBarSettingBtn, "set",
                main.getMainTopIconSize(25), Colors.ColorHeadButton.invoke());
        IconfontCreator.setText(main.notepadMainActionBarSearchBtn, "sousuo",
                main.getMainTopIconSize(21), Colors.ColorHeadButton.invoke());
        IconfontCreator.setText(main.notepadMainActionBarFileOpenBtn, "file",
                main.getMainTopIconSize(22), Colors.ColorHeadButton.invoke());
        IconfontCreator.setText(main.notepadMainActionBarSaveBtn, "save",
                main.getMainTopIconSize(25), Colors.ColorHeadButton.invoke());
        IconfontCreator.setText(main.notepadMainActionBarNewBtn, "add-select",
                main.getMainTopIconSize(25), Colors.ColorHeadButton.invoke());
    }

    void bottomBtns() {
        var mMain = UIContext.context();
        mMain.notepadMainActionBarAToolsBtn.setTooltip(new Tooltip(Locales.str("head.myOtherTools")));
        mMain.notepadMainActionBarAToolsBtn.setOnMouseClicked(e ->{
            if (UIContext.toolsController == null || UIContext.toolsController.getStage() == null) {
                UIContext.toolsController = new AToolsControllerInitial().createAToolsWindow();
                UIContext.toolsController.getStage().show();
            } else {
                UIContext.toolsController.getStage().toFront();
            }
        });
    }

    public void init() {
        var mMain = UIContext.context();
        refreshSize();

        mMain.notepadMainAltMultiSelectBtn.setTooltip(new Tooltip(Locales.str("altMultiSelection")));
        mMain.notepadMainAltMultiSelectBtn.visibleProperty().bind(UIContext.isMultiSelectedProp);
        mMain.notepadMainAltMultiSelectBtn.setOnMouseClicked(e-> {
            NotepadMultiSelectionWindow.show();
        });

        mMain.notepadMainActionBarFontBtn.setTooltip(new Tooltip(Locales.str("head.adjustFontSize")));
        mMain.notepadMainActionBarFontBtn.setOnMouseClicked(e-> {
            if (UIContext.DEBUG && false) {
                SnackbarUtils.show("aadfadf哈哈哈");
            } else {
                var region = new FontSizeChooseCreatorImpl().createPop(null);
                GlobalPopupManager.instance().setContent(region)
                        .setHeight(410)
                        .show(mMain.notepadMainActionBarFontBtn, JFXPopup.PopupVPosition.TOP,
                                JFXPopup.PopupHPosition.LEFT, 0, mMain.getMainTopIconSize(27));
            }
        });

        mMain.notepadMainInsertEmptyLineBtn.setTooltip(new Tooltip(Locales.str("setting.insertEmptyLine")));
        mMain.notepadMainInsertEmptyLineBtn.setOnMouseClicked(e-> {
            var cur = UIContext.currentAreaProp.get();
            if (cur != null) {
                var text = cur.getText();
                var lines = text.split("\n");
                StringBuilder sb = new StringBuilder();
                int i = 0;
                for (int sz = lines.length; i < sz - 1; i++) {
                    sb.append(lines[i]).append("\n\n");
                }
                sb.append(lines[i]);
                cur.replaceText(sb.toString());
            }
        });

        mMain.notepadMainActionBarSettingBtn.setTooltip(new Tooltip(Locales.str("setting")));
        mMain.notepadMainActionBarSettingBtn.setOnMouseClicked(e ->{
            mMain.toggleSettingDrawer();
        });

        mMain.notepadMainActionBarSearchBtn.setTooltip(new Tooltip(Locales.str("searchAndFind")));
        mMain.notepadMainActionBarSearchBtn.setOnMouseClicked(e ->{
            if (UIContext.currentTabProp.get() == null) {
                JfoenixDialogUtils.alert(Locales.str("notification"), Locales.str("noFile"));
                return;
            }
            NotepadFindWindow.getInstance().show(UIContext.currentAreaProp.get().getSelectedText());
        });

        //mMain.notepadMainActionBarFileOpenBtn.setTooltip(new Tooltip(Locales.str("openFile")));
        mMain.notepadMainActionBarFileOpenBtn.setOnMouseClicked(e -> fileOpenClick(e));

        mMain.notepadMainActionBarSaveBtn.setTooltip(new Tooltip(Locales.str("save")));
        mMain.notepadMainActionBarSaveBtn.setOnMouseClicked(e ->{
            Optional.ofNullable(UIContext.currentAreaProp.get())
                    .ifPresent(curArea -> curArea.getEditor().saveContent(null, false));
        });

        mMain.notepadMainActionBarNewBtn.setTooltip(new Tooltip(Locales.str("new")));
        mMain.notepadMainActionBarNewBtn.setOnMouseClicked(e -> newATempFile());

        bottomBtns();
    }

    private static final String LAST_OPEN_DIRS_KEY = "lastOpenDirs";

    private void fileOpenClick(MouseEvent e) {
        var fileBtnMenu = new NotepadFileCreatorImpl().createMenu(index -> {
            switch (index) {
                case 1 -> newATempFile();
                case 2 -> UIContext.context().getWorkspaceManager().selectDirAsWorkspaceDialog();
                case 3 -> UIContext.context().getWorkspaceManager().openLastWorkspace();
                default -> loadDocument();
            }
        });
        fileBtnMenu.show((Label)e.getSource(),
                javafx.geometry.Side.BOTTOM, 0, 0);
    }

    public static void newATempFile(String baseDir) {
        do {
            if (baseDir != null && baseDir.length() > 0) {
                var file = new File(baseDir);
                if (file.exists() && file.isDirectory()) {
                    createNewFile(file);
                    break;
                }
            }
            JfoenixDialogUtils.alert(Locales.str("notification"), Locales.str("pleaseSetNewFileDir"),
                    () -> UIContext.context().openSettingDrawer());
        } while(false);
    }

    public static void newATempFile() {
        newATempFile(SettingPreferences.getStr(SettingPreferences.newFileDirKey));
    }

    private void loadDocument() {
        var lastDir = UIContext.sharedPref.getString(LAST_OPEN_DIRS_KEY, null);
        File initialFile;
        do {
            if (lastDir != null && lastDir.length() >= 1) {
                var initialDir = lastDir;
                initialFile = new File(initialDir);
                if (initialFile.exists() && initialFile.isDirectory()) {
                    break;
                } else {
                    UIContext.sharedPref.edit().putString(LAST_OPEN_DIRS_KEY, null).commit();
                }
            }
            var initialDir = System.getProperty("user.dir");
            initialFile = new File(initialDir);
        } while(false);

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(Locales.str("setting.selectFont"));
        fileChooser.setInitialDirectory(initialFile);
        fileChooser.setSelectedExtensionFilter(
                new FileChooser.ExtensionFilter(Locales.str("textFile"), "*"));
        File selectedFile = fileChooser.showOpenDialog(AllStagesManager.getInstance().getMainStage());
        if (selectedFile != null) {
            String dir = selectedFile.getParent();
            UIContext.sharedPref.edit().putString(LAST_OPEN_DIRS_KEY, dir).commit();
            AllEditorsManager.Instance.openFile(selectedFile, true, true);
        }
    }

    private static String newFileName(File dir) {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int second = c.get(Calendar.SECOND);
        var baseName = String.format("temp%02d_%02d_%02d", hour, minute, second);
        var index = 0;
        while (true) {
            var fileName = index == 0 ? baseName + ".txt" : baseName + "_" + index + ".txt";
            var file = new File(dir, fileName);
            var hiddenFile = new File(dir, "." + fileName);
            if (!file.exists() && !hiddenFile.exists()
                    && AllEditorsManager.Instance.getAreaByFilePath(file) == null) {
                return fileName;
            }
            index++;
        }
    }

    private static void createNewFile(File dir) {
        //TODO 目前是直接创建文件。后面修改为不创建。
        String file = dir.getAbsolutePath();
        if (!file.endsWith(File.separator)) {
            file = file + File.separatorChar;
        }

        String finalFile = file + newFileName(dir);
        ThreadUtils.globalHandler().postDelayedCheckClosed(()-> Platform.runLater(()-> {
            AllEditorsManager.Instance.newFakeFile(new File(finalFile));
        }), 200);
    }
}

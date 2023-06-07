package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractMainController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.controllerwindow.NotepadFindWindow;
import com.allan.atools.pop.impl.EncodingChooseCreatorImpl;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.FileOpenSupportsKt;
import com.allan.atools.tools.modulenotepad.base.IWorkspace;
import com.allan.atools.tools.modulenotepad.bottom.BottomEntry;
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager;
import com.allan.atools.tools.modulenotepad.manager.NotepadHeadButtons;
import com.allan.atools.pop.GlobalPopupManager;
import com.allan.atools.tools.modulenotepad.workspace.WorkspaceManager;
import com.allan.atools.toolsstartup.Startup;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.atools.ui.controls.DirAndFileJFXTreeView;
import com.allan.atools.utils.*;
import com.jfoenix.controls.JFXTabPane;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@XmlPaths(paths = {"notepad", "main_notepad.fxml"})
public final class NotepadController extends AbstractMainController {
    /**
     * 主程序的size xy 变化
     */
    public static final SimpleLongProperty sizeXyChangedProp = new SimpleLongProperty();

    private final SimpleDoubleProperty mHeightProp = new SimpleDoubleProperty();

    //fxml action head buttons
    public Label notepadMainActionBarFontBtn;
    public Label notepadMainActionBarSearchBtn;
    public Label notepadMainAltMultiSelectBtn;
    public Label notepadMainActionBarSettingBtn;
    public Label notepadMainActionBarAToolsBtn;
    public Label notepadMainActionBarFileOpenBtn;
    public Label notepadMainActionBarSaveBtn;
    public Label notepadMainActionBarNewBtn;

    public Label bottomSearchTextCaseBtn;
    public Label bottomSearchTextWholeWordsBtn;
    public Label bottomSearchTextRuleBtn;
    public Label bottomSearchTextUpperBtn;
    public Label bottomSearchTextDownBtn;
    public JFXTextField bottomSearchTextField;

    public HBox notepadMainHeadBox;

    public SplitPane notepadMainSplitPane;

    public JFXTabPane tabPane;
    public Label indicateLabel;
    public Label searchedIndicateLabel;

    public Label notepadMainEncodeLabel;
    public Label notepadReadonlyCheckBtn;
    public HBox notepadMainBottomBox;
    public StackPane mainPane;
    public Label notepadMainNotHasFileText;

    public Label wrapTextCheckBtn;
    public AnchorPane snackContainer;
    public SplitPane notepadSubSplitPane;

    public DirAndFileJFXTreeView<String> workspaceTree;
    public Label workspaceText;
    public Label workspaceCloseBtn;
    public VBox workspaceVBox;
    public Label workspaceRefreshBtn;
    public Label workspaceGoUpBtn;
    public Label workspaceCreateDirBtn;
    public Label workspaceCreateFileBtn;
    public Label workspaceSortBtn;
    public Label notepadMainInsertEmptyLineBtn;
    private AnchorPane notepadMainResultLayout;
    public AnchorPane getNotepadMainResultLayout() {
        if (notepadMainResultLayout == null) {
            notepadMainResultLayout = new AnchorPane();
            notepadMainSplitPane.getItems().add(notepadMainResultLayout);
            notepadMainSplitPane.setDividerPositions(0.7, 0.3);
        }

        return notepadMainResultLayout;
    }

    public int stageWidth;

    private final IWorkspace workspaceManager = new WorkspaceManager();
    public IWorkspace getWorkspaceManager() {
        return workspaceManager;
    }

    public void removeResultLayout() {
        if (notepadMainResultLayout != null) {
            notepadMainSplitPane.getItems().remove(notepadMainResultLayout);
            notepadMainResultLayout = null;
        }
    }

    @Override
    public void destroy() {
        Log.d("DESTROY: notepad controller");
        AllEditorsManager.Instance.saveUnSaved();
        AllEditorsManager.Instance.saveListFilePaths();
        AllEditorsManager.Instance.removeKeyListener();
        NotepadFindWindow.getInstance().hide();
    }

    private void initAfterShownDelayInThread() {
        try {
            var pathStr = CacheLocation.get("editor_font_cust.css");

            var needTtfName = "";
            if (new File(pathStr).exists()) {
                var path = Path.of(pathStr);
                var lines = Files.readAllLines(path);
                for (var line : lines) {
                    if (line.contains("font_custom")) {
                        needTtfName = Utils.getStrBetween(line, "url(\"", "\")");
                    }
                }
            }

            var li = new File(CacheLocation.get()).list();
            var finalNeedTtfname = needTtfName;
            if (li != null) {
                Arrays.stream(li)
                        .filter(s -> s.contains("font_custom") && s.endsWith(".ttf"))
                        .forEach(s -> {
                            boolean deleted;
                            if (!s.contains(finalNeedTtfname)) {
                                deleted = new File(CacheLocation.get(s)).delete();
                                Log.d("delete file " + s + ", " + deleted);
                            } else {
                                //Log.d("not delete file " + s);
                            }
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initAfterShown() {
        AllEditorsManager.Instance.addKeyListener();

        AllEditorsManager.Instance.init();

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        EventHandler<DragEvent> dragOver = event-> {
            if (event.getDragboard() != null && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
        };
        EventHandler<DragEvent> dragDrop = event -> {
            Log.d("drag dropped!");
            List<File> currentDropped = event.getDragboard().getFiles();
            if (currentDropped != null && currentDropped.size() >= 1) {
                for (var file : currentDropped) {
                    AllEditorsManager.Instance.openFile(file, true, true);
                }
            }
        };
        //因为我们默认它显示；直接上来直接设置tabPane即可。
        tabPane.setOnDragOver(dragOver);
        tabPane.setOnDragDropped(dragDrop);
        tabPane.visibleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                tabPane.setOnDragOver(dragOver);
                tabPane.setOnDragDropped(dragDrop);
                notepadMainSplitPane.setOnDragOver(null);
                notepadMainSplitPane.setOnDragDropped(null);
            } else {
                notepadMainSplitPane.setOnDragOver(dragOver);
                notepadMainSplitPane.setOnDragDropped(dragDrop);
                tabPane.setOnDragOver(null);
                tabPane.setOnDragDropped(null);
            }
        });

        new NotepadHeadButtons().init();

        indicateLabel.textProperty().bind(UIContext.bottomIndicateProp);
        searchedIndicateLabel.textProperty().bind(UIContext.bottomSearchedIndicateProp);
        notepadMainEncodeLabel.textProperty().bind(UIContext.fileEncodeIndicateProp);

        BottomEntry.initAfterBottomCreated();

        initEncodingIndicateClick();

        //delay打开之前的文件
        if (SettingPreferences.getBoolean(SettingPreferences.saveLastOpenedFileKey)) {
            ThreadUtils.globalHandler().postDelayedCheckClosed(() -> {
                Platform.runLater(() -> {
                    String lastFiles = UIContext.sharedPref.getString("lastFile", "");
                    String[] lastFilePair = lastFiles.split(";");
                    Log.d("load old files start...");
                    for (var lastFile : lastFilePair) {
                        if (lastFile != null && lastFile.length() > 0 && new File(lastFile).exists()) {
                            AllEditorsManager.Instance.openFile(new File(lastFile), false, false);
                        }
                    }
                    Log.d("load old files end!");
                });
            }, 250);
        }

        //delay打开workspace
        ThreadUtils.globalHandler().postDelayedCheckClosed(() -> {
            Platform.runLater(()-> getWorkspaceManager().initWhenAppStart());
        }, 500);

        //delay打开打开文件参数
        ThreadUtils.globalHandler().postDelayedCheckClosed(()->{
            Platform.runLater(()->{
                if (!ThreadUtils.sBeClosing) {
                    Log.e("Startup sInit Args " + Startup.sInitArgs);
                    if (Startup.sInitArgs != null && Startup.sInitArgs.length > 0) {
                        for (var str : Startup.sInitArgs) {
                            if (!str.isEmpty()) {
                                FileOpenSupportsKt.open(str);
                            }
                        }
                    }
                }
            });
        }, 1000);

        //delay打开提示条
        ThreadUtils.globalHandler().postDelayedCheckClosed(() -> {
            Platform.runLater(()-> runTip(0));
        }, 2000);
    }

    private void runTip(int count) {
        if (!ThreadUtils.sBeClosing) {
            int rid = (int) (Math.random() * 2);
            switch (rid) {
                case 0 -> {
                    var shown = SettingPreferences.getInt(SettingPreferences.TipsDoubleClickCtrlFKey);
                    if (shown <= 2) {
                        SettingPreferences.updateInt(SettingPreferences.TipsDoubleClickCtrlFKey, shown + 1);
                        SnackbarUtils.show(Locales.str("TipsDoubleClickCtrlF"), 8000, null);
                    } else {
                        if(count <= 1) runTip(count + 1);
                    }
                }
                case 1 -> {
                    var shown = SettingPreferences.getInt(SettingPreferences.TipsDoubleClickWordNextKey);
                    if (shown <= 2) {
                        SettingPreferences.updateInt(SettingPreferences.TipsDoubleClickWordNextKey, shown + 1);
                        SnackbarUtils.show(Locales.str("TipsDoubleClickWordsNext"), 8000, null);
                    } else {
                        if(count <= 1) runTip(count + 1);
                    }
                    //只有这样才break
                }
                default -> {
                }
            }
        }
    }

    @Override
    public void init(Stage stage) {
        super.init(stage);
        StackPane.setMargin(snackContainer, new Insets(0, 0, 22, 0));
        mainPane.getChildren().remove(snackContainer);
        getWorkspaceManager().removeWorkspace(false);

        mHeightProp.set(stage.heightProperty().getValue() - 5);
        stage.heightProperty().addListener((observable, oldValue, newValue) ->
                mHeightProp.set(newValue.doubleValue() - 5));
        mainPane.prefHeightProperty().bind(mHeightProp);
        mainPane.prefWidthProperty().bind(stage.widthProperty());

        stage.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue) {
                FileLog.updateDay();
                notifyStageFocused();
            }
        });

        stage.onShownProperty().addListener((observable, oldValue, newValue) -> {
            //往后推迟的初始化
            initAfterShown();
            //继续delay的延迟子线程任务
            ThreadUtils.globalHandler().postDelayed(this::initAfterShownDelayInThread, 20 * 1000L);
        });
    }

    private void initEncodingIndicateClick() {
        if (notepadMainEncodeLabel.getOnMouseClicked() == null) {
            UIContext.currentAreaProp.addListener((observable, oldValue, newValue) -> {
                Log.d("change wrap check");
                if (newValue != null) {
                    UIContext.fileEncodeIndicateProp.set(newValue.getEditor().getState().getFileEncoding());

                    changeBottomTextBtnCheckStyle(wrapTextCheckBtn, newValue.getEditor().getState().isWrap());
                    changeBottomTextBtnCheckStyle(notepadReadonlyCheckBtn, newValue.getEditor().getState().isCurrentReadonly());
                } else {
                    UIContext.fileEncodeIndicateProp.set("");

                    changeBottomTextBtnCheckStyle(wrapTextCheckBtn, null);
                    changeBottomTextBtnCheckStyle(notepadReadonlyCheckBtn, null);
                }
            });

            notepadMainEncodeLabel.setOnMouseClicked(event -> {
                if (UIContext.currentTabProp.get() != null) {
                    var area = UIContext.currentAreaProp.get();
                    var f = area.getEditor().getSourceFile();
                    if (!f.exists() || area.getEditor().getIsFake()) {
                        SnackbarUtils.show(Locales.str("fileIsNotSave"));
                        return;
                    }
                }

                var contentMenu = new EncodingChooseCreatorImpl().createMenu(forceEncoding -> {
                    var curTab = UIContext.currentTabProp.get();
                    if (curTab != null) {
                        var area = UIContext.currentAreaProp.get();
                        var f = area.getEditor().getSourceFile();
                        if (!f.exists() || area.getEditor().getIsFake()) {
                            SnackbarUtils.show(Locales.str("fileIsNotSave"));
                            return;
                        } else {
                            AllEditorsManager.Instance.reOpenCurrentFile(curTab, UIContext.currentAreaProp.get().getEditor().getSourceFile(), forceEncoding);
                        }
                    }
                    GlobalPopupManager.instance().hide();
                });
                contentMenu.show(notepadMainEncodeLabel,
                        javafx.geometry.Side.BOTTOM, -100, 0);
            });

            wrapTextCheckBtn.setOnMouseClicked(ev -> {
                var curArea = UIContext.currentAreaProp.get();
                if (curArea != null) {
                    var w = !curArea.getEditor().getState().isWrap();
                    curArea.getEditor().getState().setWrap(w);
                    curArea.setWrapText(w);
                    changeBottomTextBtnCheckStyle(wrapTextCheckBtn, w);
                }
            });

            notepadReadonlyCheckBtn.setOnMouseClicked(ev->{
                var curArea = UIContext.currentAreaProp.get();
                if (curArea != null) {
                    var s = !curArea.getEditor().getState().isCurrentReadonly();
                    curArea.getEditor().getState().setCurrentReadonly(s);
                    changeBottomTextBtnCheckStyle(notepadReadonlyCheckBtn, s);
                }
            });
        }
    }

    private void changeBottomTextBtnCheckStyle(Label label, Boolean enable) {
        Log.d("" + label + ", enable: " + enable);
        if (enable == null) {
            label.setVisible(false);
        } else {
            label.setVisible(true);
            if (enable) {
                label.getStyleClass().removeAll("small-desc-label", "small-colored-label");
                label.getStyleClass().add("small-colored-label");
            } else {
                label.getStyleClass().removeAll("small-desc-label", "small-colored-label");
                label.getStyleClass().add("small-desc-label");
            }
        }
    }

    @Override
    public void notifyStageFocused() {
        super.notifyStageFocused();
        requestFocus4Jfoenix();
    }

    private boolean isDecorate = false;
    public void setIsDecorate() {
        isDecorate = true;
    }
    /**
     * jfoenix必须在某些情况下，失去焦点；避免JFXDecorator获取到效果。
     */
    public void requestFocus4Jfoenix() {
        if(isDecorate) mainPane.requestFocus();
    }
}

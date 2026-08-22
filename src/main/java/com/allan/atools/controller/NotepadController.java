package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.GlobalCfgStores;
import com.allan.atools.bases.AbstractMainController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.controllerwindow.NotepadFindWindow;
import com.allan.atools.pop.impl.EncodingChooseCreatorImpl;
import com.allan.atools.pop.impl.JSONChooseCreatorImpl;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.FileOpenSupportsKt;
import com.allan.atools.tools.modulejson.JsonFormatLog;
import com.allan.atools.tools.modulenotepad.base.IWorkspace;
import com.allan.atools.tools.modulenotepad.bottom.BottomEntry;
import com.allan.atools.tools.modulenotepad.bottom.BottomSearchBtnsMgr;
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager;
import com.allan.atools.tools.modulenotepad.manager.MarkdownCodeBlockManager;
import com.allan.atools.tools.modulenotepad.manager.MarkdownImageManager;
import com.allan.atools.tools.modulenotepad.manager.MarkdownOutlineManager;
import com.allan.atools.tools.modulenotepad.manager.MarkdownTableOptimizeManager;
import com.allan.atools.tools.modulenotepad.manager.NotepadHeadButtons;
import com.allan.atools.pop.GlobalPopupManager;
import com.allan.atools.tools.modulenotepad.workspace.WorkspaceManager;
import com.allan.atools.toolsstartup.ATools;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.atools.ui.SettingDrawer;
import com.allan.atools.ui.MainWindowChrome;
import com.allan.atools.ui.controls.DirAndFileJFXTreeView;
import com.allan.atools.utils.*;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTabPane;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
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
    public HBox notepadWindowTitleBox;
    public HBox notepadActionBarBox;
    public StackPane notepadWindowCloseBtn;
    public StackPane notepadWindowMinBtn;
    public StackPane notepadWindowMaxBtn;
    public StackPane workspaceSidebarToggleBtn;
    public Label notepadCompactTitleLabel;
    public VBox notepadRoot;

    public SplitPane notepadMainSplitPane;

    public JFXTabPane tabPane;
    public Label indicateLabel;
    public Label searchedIndicateLabel;

    public Label notepadMainEncodeLabel;
    public Label notepadReadonlyCheckBtn;
    public HBox notepadMainBottomBox;
    public Region workspaceBottomExtension;
    public StackPane mainPane;
    public VBox notepadMainNotHasFileText;
    public Label notepadEmptyTitleLabel;
    public Label notepadEmptyDescLabel;
    public Label notepadEmptyHintLabel;

    public Label wrapTextCheckBtn;
    public AnchorPane snackContainer;
    public SplitPane notepadSubSplitPane;

    public DirAndFileJFXTreeView<String> workspaceTree;
    public JFXTabPane workspaceTabPane;
    public GridPane workspaceTabSwitchBox;
    public ToggleButton workspaceFilesTabBtn;
    public ToggleButton workspaceDocTabBtn;
    public Label workspaceText;
    public HBox workspaceToolbarBox;
    public VBox workspaceEmptyBox;
    public Label workspaceEmptyHintLabel;
    public JFXButton workspaceOpenBtn;
    public Label currentDocumentPathText;
    public Label currentDocumentEmptyHintLabel;
    public ListView<MarkdownOutlineManager.MarkdownHeading> currentDocumentOutlineList;
    public VBox workspaceVBox;
    public Label workspaceRefreshBtn;
    public Label workspaceGoUpBtn;
    public Label workspaceCreateDirBtn;
    public Label workspaceCreateFileBtn;
    public Label workspaceSortBtn;
    public Label notepadMainInsertEmptyLineBtn;
    public Label jsonPopBtn;
    private AnchorPane notepadMainResultLayout;

    private SettingDrawer settingDrawer;
    private MarkdownOutlineManager markdownOutlineManager;
    private MarkdownTableOptimizeManager markdownTableOptimizeManager;
    private MarkdownImageManager markdownImageManager;
    private MarkdownCodeBlockManager markdownCodeBlockManager;
    private final ChangeListener<EditorArea> currentDocumentAreaChanged =
            (observable, oldValue, newValue) -> {
                refreshCurrentDocumentPath();
                updateMarkdownTableOptimizeManager(newValue);
                updateMarkdownImageManager(newValue);
                updateMarkdownCodeBlockManager(newValue);
            };

    private int getMainUiSizeMode() {
        int mode = SettingPreferences.getInt(SettingPreferences.mainUiSizeModeKey);
        if (mode == 1 || mode == 2) {
            return mode;
        }
        return 0;
    }

    public int getMainTopIconSize(int defaultSize) {
        int mode = getMainUiSizeMode();
        if (mode == 1) {
            return Math.round(defaultSize * 1.1f);
        }
        if (mode == 2) {
            return Math.round(defaultSize * 1.15f);
        }
        return defaultSize;
    }

    public int getMainBottomSize(int defaultSize) {
        return defaultSize + getMainUiSizeMode();
    }

    /** 打开文件弹出菜单的基础字号，每个尺寸级别 +1 */
    public int getMainMenuFontSize() {
        return 15 + getMainUiSizeMode();
    }

    public int getMainWorkspaceIconSize(int defaultSize) {
        return defaultSize + getMainUiSizeMode();
    }

    public void applyMainUiSizeMode() {
        int mode = getMainUiSizeMode();
        String styleClass;
        if (mode == 1) {
            styleClass = "main-ui-size-large";
        } else if (mode == 2) {
            styleClass = "main-ui-size-larger";
        } else {
            styleClass = "main-ui-size-default";
        }

        notepadMainHeadBox.getStyleClass().removeAll(
                "main-ui-size-default", "main-ui-size-large", "main-ui-size-larger");
        notepadMainBottomBox.getStyleClass().removeAll(
                "main-ui-size-default", "main-ui-size-large", "main-ui-size-larger");
        tabPane.getStyleClass().removeAll(
                "main-ui-size-default", "main-ui-size-large", "main-ui-size-larger");
        workspaceTree.getStyleClass().removeAll(
                "main-ui-size-default", "main-ui-size-large", "main-ui-size-larger");
        workspaceTabPane.getStyleClass().removeAll(
                "main-ui-size-default", "main-ui-size-large", "main-ui-size-larger");
        workspaceTabSwitchBox.getStyleClass().removeAll(
                "main-ui-size-default", "main-ui-size-large", "main-ui-size-larger");
        workspaceVBox.getStyleClass().removeAll(
                "main-ui-size-default", "main-ui-size-large", "main-ui-size-larger");
        notepadMainHeadBox.getStyleClass().add(styleClass);
        notepadMainBottomBox.getStyleClass().add(styleClass);
        tabPane.getStyleClass().add(styleClass);
        workspaceTree.getStyleClass().add(styleClass);
        workspaceTabPane.getStyleClass().add(styleClass);
        workspaceTabSwitchBox.getStyleClass().add(styleClass);
        workspaceVBox.getStyleClass().add(styleClass);

        double extra = mode;
        notepadActionBarBox.setPadding(new Insets(3 + extra));
        notepadMainBottomBox.setPadding(new Insets(3 + extra, 2, 3, 2));
        bottomSearchTextField.setPrefHeight(20 + mode * 2);
        StackPane.setMargin(snackContainer, new Insets(0, 0, 22 + mode * 2, 0));

        NotepadHeadButtons.refreshSize();
        WorkspaceManager.refreshSize();
        BottomEntry.refreshSize();
        BottomSearchBtnsMgr.refreshSize();
    }

    /** 打开右侧设置抽屉 */
    public void openSettingDrawer() {
        if (settingDrawer == null) {
            settingDrawer = new SettingDrawer(getStage());
        }
        settingDrawer.open();
    }

    /** 切换设置抽屉开关 */
    public void toggleSettingDrawer() {
        if (settingDrawer == null) {
            settingDrawer = new SettingDrawer(getStage());
        }
        settingDrawer.toggle();
    }

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
        if (markdownOutlineManager != null) {
            markdownOutlineManager.destroy();
            markdownOutlineManager = null;
        }
        if (markdownTableOptimizeManager != null) {
            markdownTableOptimizeManager.destroy();
            markdownTableOptimizeManager = null;
        }
        if (markdownImageManager != null) {
            markdownImageManager.destroy();
            markdownImageManager = null;
        }
        UIContext.currentAreaProp.removeListener(currentDocumentAreaChanged);
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

        //双击 tab 头空白区域（非 tab 本身）时新建文件
        tabPane.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2
                    && event.getTarget() instanceof Node node
                    && node.getStyleClass().contains("tab-header-background")) {
                NotepadHeadButtons.newATempFile();
            }
        });

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
                    FileOpenSupportsKt.open(file);
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
        applyMainUiSizeMode();

        AllEditorsManager.restoreHiddenTempFiles();

        //delay打开之前的文件
        if (SettingPreferences.getBoolean(SettingPreferences.saveLastOpenedFileKey)) {
            ThreadUtils.globalHandler().postDelayedCheckClosed(() -> {
                Platform.runLater(() -> {
                    var lastFiles = GlobalCfgStores.user().getStringList("lastFile", List.of());
                    Log.d("load old files start...");
                    for (var lastFile : lastFiles) {
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
                    Log.e("ATools init args " + ATools.sInitArgs);
                    if (ATools.sInitArgs != null && ATools.sInitArgs.length > 0) {
                        for (var str : ATools.sInitArgs) {
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
        new MainWindowChrome(stage, notepadRoot, notepadMainHeadBox,
                notepadWindowCloseBtn, notepadWindowMinBtn, notepadWindowMaxBtn);
        setIsDecorate();
        StackPane.setMargin(snackContainer, new Insets(0, 0, 22, 0));
        mainPane.getChildren().remove(snackContainer);

        //空状态三行文案
        if (notepadEmptyTitleLabel != null) {
            notepadEmptyTitleLabel.setText(Locales.str("appNameLine") + " (" + Utils.getAppVersion() + ")");
        }
        if (notepadEmptyDescLabel != null) {
            notepadEmptyDescLabel.setText(Locales.str("appDescLine"));
        }
        if (notepadEmptyHintLabel != null) {
            notepadEmptyHintLabel.setText(Locales.str("dragFileIntoAndOpen"));
        }
        currentDocumentEmptyHintLabel.setText(Locales.str("currentDocumentEmptyHint"));

        //工作区侧栏顶部分段切换，驱动隐藏头部的tab pane
        var workspaceTabSwitchGroup = new ToggleGroup();
        workspaceFilesTabBtn.setToggleGroup(workspaceTabSwitchGroup);
        workspaceDocTabBtn.setToggleGroup(workspaceTabSwitchGroup);
        workspaceFilesTabBtn.setSelected(true);
        workspaceTabSwitchGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == workspaceFilesTabBtn) {
                workspaceTabPane.getSelectionModel().select(0);
            } else if (newValue == workspaceDocTabBtn) {
                workspaceTabPane.getSelectionModel().select(1);
            }
        });
        UIContext.currentAreaProp.addListener(currentDocumentAreaChanged);
        currentDocumentPathText.setCursor(Cursor.HAND);
        currentDocumentPathText.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                var path = currentDocumentPathText.getText();
                if (!path.isEmpty()) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(path), null);
                    SnackbarUtils.show(Locales.str("fullPathCopied"));
                }
            }
        });
        refreshCurrentDocumentPath();
        markdownOutlineManager = new MarkdownOutlineManager(this);
        updateMarkdownTableOptimizeManager(UIContext.currentAreaProp.get());
        updateMarkdownImageManager(UIContext.currentAreaProp.get());
        getWorkspaceManager().initViewState();

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

    public void refreshCurrentDocumentInfo() {
        refreshCurrentDocumentPath();
        if (markdownOutlineManager != null) {
            markdownOutlineManager.refreshCurrentFile();
        }
        updateMarkdownTableOptimizeManager(UIContext.currentAreaProp.get());
        updateMarkdownImageManager(UIContext.currentAreaProp.get());
        updateMarkdownCodeBlockManager(UIContext.currentAreaProp.get());
    }

    private void updateMarkdownTableOptimizeManager(EditorArea area) {
        if (!MarkdownTableOptimizeManager.supports(area)) {
            if (markdownTableOptimizeManager != null) {
                markdownTableOptimizeManager.destroy();
                markdownTableOptimizeManager = null;
            }
            return;
        }
        if (markdownTableOptimizeManager == null) {
            markdownTableOptimizeManager = new MarkdownTableOptimizeManager(area);
        } else {
            markdownTableOptimizeManager.refreshCurrentFile(area);
        }
    }

    private void updateMarkdownImageManager(EditorArea area) {
        if (!MarkdownImageManager.supports(area)) {
            if (markdownImageManager != null) {
                markdownImageManager.destroy();
                markdownImageManager = null;
            }
            return;
        }
        if (markdownImageManager == null) {
            markdownImageManager = new MarkdownImageManager(area);
        } else {
            markdownImageManager.refreshCurrentFile(area);
        }
    }

    private void updateMarkdownCodeBlockManager(EditorArea area) {
        if (!MarkdownImageManager.supports(area)) {
            if (markdownCodeBlockManager != null) {
                markdownCodeBlockManager.destroy();
                markdownCodeBlockManager = null;
            }
            return;
        }
        if (markdownCodeBlockManager == null) {
            markdownCodeBlockManager = new MarkdownCodeBlockManager(area);
        } else {
            markdownCodeBlockManager.refreshCurrentFile(area);
        }
    }

    private void refreshCurrentDocumentPath() {
        var currentArea = UIContext.currentAreaProp.get();
        boolean hasDoc = currentArea != null && currentArea.getEditor().getSourceFile() != null;
        if (hasDoc) {
            currentDocumentPathText.setText(currentArea.getEditor().getSourceFile().getAbsolutePath());
        } else {
            currentDocumentPathText.setText("");
        }
        currentDocumentEmptyHintLabel.setVisible(!hasDoc);
        currentDocumentEmptyHintLabel.setManaged(!hasDoc);
    }

    private void initEncodingIndicateClick() {
        if (notepadMainEncodeLabel.getOnMouseClicked() == null) {
            UIContext.currentAreaProp.addListener((observable, oldValue, newValue) -> {
                Log.d("change wrap check");
                if (newValue != null) {
                    UIContext.fileEncodeIndicateProp.set(newValue.getEditor().getState().getFileEncoding());

                    changeBottomTextBtnCheckStyle(wrapTextCheckBtn, newValue.getEditor().getState().isWrap());
                    changeBottomTextBtnCheckStyle(notepadReadonlyCheckBtn, newValue.getEditor().getState().isCurrentReadonly());
                    jsonPopBtn.setVisible(true);
                } else {
                    UIContext.fileEncodeIndicateProp.set("");

                    changeBottomTextBtnCheckStyle(wrapTextCheckBtn, null);
                    jsonPopBtn.setVisible(false);
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
                    changeBottomTextBtnCheckStyle(wrapTextCheckBtn, w);
                }
            });

            jsonPopBtn.setOnMouseClicked(event -> {
                var contentMenu = new JSONChooseCreatorImpl().createMenu(action -> {
                    var text = UIContext.currentAreaProp.get().getText();
                    var fmt = new JsonFormatLog();
                    if (action.equals(Locales.str("removeUnknownSymbols"))) {
                        var newText = fmt.removeFanxieExtraQuote(text);
                        UIContext.currentAreaProp.get().getEditor().resetText(newText);
                    } else if (action.equals(Locales.str("jsonFormat"))) {
                        var newText = fmt.format(fmt.removeEnter(text));
                        UIContext.currentAreaProp.get().getEditor().resetText(newText);
                    }
                });
                contentMenu.show(jsonPopBtn,
                        javafx.geometry.Side.BOTTOM, -100, 0);
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

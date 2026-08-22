package com.allan.atools.tools.modulenotepad.workspace;

import com.allan.atools.UIContext;
import com.allan.atools.GlobalCfgStores;
import com.allan.atools.controllerwindow.PictureWindow;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.ui.IconfontCreator;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.ui.controls.DirAndFileJFXTreeCell;
import com.allan.atools.utils.CacheLocation;
import com.allan.atools.utils.IO;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.Utils;
import com.allan.uilibs.controls.TreeItemEx;
import com.allan.atools.Colors;
import com.allan.atools.tools.modulenotepad.base.IWorkspace;
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager;
import com.allan.atools.tools.modulenotepad.manager.NotepadHeadButtons;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.baseparty.Action0;
import com.allan.baseparty.handler.TextUtils;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WorkspaceManager implements IWorkspace {
    private static final boolean DEBUG = false;
    private static final String TAG = "WorkspaceManager";

    private static final String KEY_WORKSPACE_FILE = "workspace_dir";
    private static final String KEY_WORKSPACE_IS_OPENED = "workspace_is_opened";
    private static final String KEY_WORKSPACE_SORT_MODE = "workspace_sort";
    private static final String KEY_WORKSPACE_WIDTH = "workspace_width";

    private static final int WIDTH_OF_WORKSPACE_MIN = 126;
    private static final int WIDTH_OF_WORKSPACE_MAX = 400;
    private static final int WIDTH_OF_WORKSPACE_DIVIDER = 1;
    /** 最近打开工作区列表的最大保存数量 */
    private static final int MAX_RECENT_WORKSPACES = 8;
    /** 最近工作区列表在 recent.json 中的顶层 key */
    private static final String KEY_RECENT_WORKSPACES = "workspaces";


    private volatile boolean isWorkspaceShown;
    private File currentDir;
    private boolean isSortByFileOrTime;
    private boolean isWorkspaceVBoxAdded = true;
    private boolean isViewStateInitialized;
    private boolean workspaceWidthReady;

    private TreeItemEx<?> currentItem;
    private ContextMenu rightClickContextMenu;
    private Tooltip sidebarToggleTooltip;
    private File pendingWorkspaceDir;

    private boolean isAddedStageFocus = false;
    private final Action0 stageFocused = ()-> {
        if (isWorkspaceShown) {
            openWorkspace(currentDir, true);
        }
    };

    public static void refreshSize() {
        var c = UIContext.context();
        IconfontCreator.setText(c.workspaceSortBtn, "paixu",
                c.getMainWorkspaceIconSize(18), Colors.ColorHeadButton.invoke());
        IconfontCreator.setText(c.workspaceCreateFileBtn, "add-select",
                c.getMainWorkspaceIconSize(19), Colors.ColorHeadButton.invoke());
        IconfontCreator.setText(c.workspaceCreateDirBtn, "xinjianwenjianjia1",
                c.getMainWorkspaceIconSize(16), Colors.ColorHeadButton.invoke());
        IconfontCreator.setText(c.workspaceGoUpBtn, "arrowup",
                c.getMainWorkspaceIconSize(18), Colors.ColorHeadButton.invoke());
        IconfontCreator.setText(c.workspaceRefreshBtn, "exchangerate",
                c.getMainWorkspaceIconSize(17), Colors.ColorHeadButton.invoke());
    }

    private boolean changeSortByFileOrTime() {
        isSortByFileOrTime = !isSortByFileOrTime;
        GlobalCfgStores.user().setBoolean(KEY_WORKSPACE_SORT_MODE, isSortByFileOrTime);
        return isSortByFileOrTime;
    }

    public WorkspaceManager() {
        isSortByFileOrTime = GlobalCfgStores.user().getBoolean(KEY_WORKSPACE_SORT_MODE, true);
        isWorkspaceShown = GlobalCfgStores.user().getBoolean(KEY_WORKSPACE_IS_OPENED, true);
        workspaceWidth = GlobalCfgStores.user().getInt(KEY_WORKSPACE_WIDTH, 175);
        if (workspaceWidth < WIDTH_OF_WORKSPACE_MIN) {
            workspaceWidth = WIDTH_OF_WORKSPACE_MIN;
        } else if (workspaceWidth > WIDTH_OF_WORKSPACE_MAX) {
            workspaceWidth = WIDTH_OF_WORKSPACE_MAX;
        }
    }

    @Override
    public void initViewState() {
        if (isViewStateInitialized) {
            return;
        }
        isViewStateInitialized = true;
        initOnce();

        var c = UIContext.context();
        c.workspaceSidebarToggleBtn.setOnMouseClicked(event -> toggleVisibility());
        sidebarToggleTooltip = new Tooltip();
        Tooltip.install(c.workspaceSidebarToggleBtn, sidebarToggleTooltip);
        c.workspaceVBox.setPrefWidth(workspaceWidth);
        if (isWorkspaceShown) {
            updateHeaderState();
        } else {
            hideWorkspace(false);
        }
    }

    private void toggleVisibility() {
        if (isWorkspaceShown) {
            hideWorkspace(true);
            return;
        }

        File savedWorkspace = currentDir;
        if (savedWorkspace == null || !savedWorkspace.exists()) {
            var path = GlobalCfgStores.user().getString(KEY_WORKSPACE_FILE, "");
            if (!TextUtils.isEmpty(path)) {
                savedWorkspace = new File(path);
            }
        }
        if (savedWorkspace != null && savedWorkspace.exists() && savedWorkspace.isDirectory()) {
            openWorkspace(savedWorkspace);
        } else {
            showEmptyWorkspaceState();
            GlobalCfgStores.user().setBoolean(KEY_WORKSPACE_IS_OPENED, true);
        }
    }

    private void hideWorkspace(boolean saveState) {
        isWorkspaceShown = false;
        workspaceWidthReady = false;
        pendingWorkspaceDir = null;
        if (isWorkspaceVBoxAdded) {
            UIContext.context().notepadSubSplitPane.getItems().remove(UIContext.context().workspaceVBox);
            isWorkspaceVBoxAdded = false;
        }
        if (isAddedStageFocus) {
            UIContext.context().removeMainStageFocused(stageFocused);
            isAddedStageFocus = false;
        }

        updateHeaderState();
        if (saveState) {
            ThreadUtils.globalHandler().post(()-> {
                GlobalCfgStores.user().setBoolean(KEY_WORKSPACE_IS_OPENED, false);
                Platform.runLater(()-> UIContext.context().requestFocus4Jfoenix());
            });
        }
    }

    @Override
    public void initWhenAppStart() {
        if (!isViewStateInitialized) {
            initViewState();
        }
        var path = GlobalCfgStores.user().getString(KEY_WORKSPACE_FILE, "");
        File savedWorkspace = TextUtils.isEmpty(path) ? null : new File(path);
        if (savedWorkspace != null && savedWorkspace.exists() && savedWorkspace.isDirectory()) {
            currentDir = savedWorkspace;
        }
        if (!isWorkspaceShown) {
            return;
        }
        setSplitPanePosition();
        if (currentDir != null) {
            openWorkspace(currentDir);
        } else {
            showEmptyWorkspaceState();
        }
    }

    /**
     * 没有任何历史工作区时：隐藏路径与按钮行，展示提示与"打开工作区"长条按钮
     */
    private void showEmptyWorkspaceState() {
        var c = UIContext.context();
        initOnce();
        if (!isWorkspaceVBoxAdded) {
            isWorkspaceVBoxAdded = true;
            c.notepadSubSplitPane.getItems().add(0, c.workspaceVBox);

            //面板首次加入时初始化tree root，否则openWorkspace走else分支时getRoot()为null
            TreeItem<String> base = new TreeItem<>();
            base.setExpanded(false);
            c.workspaceTree.setRoot(base);
            c.workspaceTree.setShowRoot(false);
        }
        if (c.workspaceTree.getRoot() == null) {
            TreeItem<String> base = new TreeItem<>();
            base.setExpanded(false);
            c.workspaceTree.setRoot(base);
            c.workspaceTree.setShowRoot(false);
        }
        isWorkspaceShown = true;
        updateHeaderState();
        setSplitPanePosition();
        c.workspaceToolbarBox.setVisible(false);
        c.workspaceToolbarBox.setManaged(false);
        c.workspaceText.setVisible(false);
        c.workspaceText.setManaged(false);
        c.workspaceTree.setVisible(false);
        c.workspaceTree.setManaged(false);
        c.workspaceEmptyBox.setVisible(true);
        c.workspaceEmptyBox.setManaged(true);
    }

    private void restoreWorkspaceViews() {
        var c = UIContext.context();
        c.workspaceToolbarBox.setVisible(true);
        c.workspaceToolbarBox.setManaged(true);
        c.workspaceText.setVisible(true);
        c.workspaceText.setManaged(true);
        c.workspaceTree.setVisible(true);
        c.workspaceTree.setManaged(true);
        c.workspaceEmptyBox.setVisible(false);
        c.workspaceEmptyBox.setManaged(false);
    }

    @Override
    public void ifRefreshWorkspace(File changedFile) {
        if(DEBUG) Log.d(TAG, "refresh workspace " + isWorkspaceShown);
        if (isWorkspaceShown && currentDir != null) {
            var dir = IO.getParentPath(changedFile.getAbsolutePath(), false);
            var curDir = currentDir.getAbsolutePath();
            if (curDir.endsWith("\\") || curDir.endsWith("/")) {
                curDir = curDir.substring(0, curDir.length() - 1);
            }
            if (TextUtils.equals(dir, curDir)) {
                if(DEBUG) Log.d(TAG, "need refresh workspace: " + currentDir);
                openWorkspace(currentDir);
            }
        }
    }

    private int workspaceWidth;
    private final Runnable saveWorkspaceVBoxWidthRunnable = () -> {
        //多偏移2个像素
        GlobalCfgStores.user().setInt(KEY_WORKSPACE_WIDTH, workspaceWidth + 2);
        if(DEBUG) Log.d("save workspace box width " + workspaceWidth);
    };

    private void setSplitPanePosition() {
        double totalWidth = UIContext.context().notepadSubSplitPane.getWidth();
        if (totalWidth <= 0) {
            return;
        }
        double pos = (double) workspaceWidth / totalWidth;
        if(DEBUG) Log.d(TAG, "todo notepad SubSplitPane setDividerPositions " + pos + " workspace Width:" + workspaceWidth + "/ notepadSub SplitPane width: " + totalWidth);
        UIContext.context().notepadSubSplitPane.setDividerPositions(pos);
        workspaceWidthReady = true;
    }

    private void initOnce() {
        if(DEBUG) Log.d(TAG, "init once");
        var c = UIContext.context();
        if (c.workspaceRefreshBtn.getOnMouseClicked() != null) {
            return;
        }


        if(DEBUG) Log.d(TAG, "init once real@ Workspace Width " + workspaceWidth);

        refreshSize();

        c.workspaceVBox.widthProperty().addListener((observable, oldValue, newValue) -> {
            if (!workspaceWidthReady || !isWorkspaceShown || !isWorkspaceVBoxAdded
                    || newValue.doubleValue() < WIDTH_OF_WORKSPACE_MIN) {
                return;
            }
            workspaceWidth = newValue.intValue();
            updateHeaderState();
            if(DEBUG) Log.d(TAG, "todo mWorkspace Width " + newValue.intValue() + " total notepad SubSplitPane: " + UIContext.context().notepadSubSplitPane.getWidth());
            ThreadUtils.globalHandler().removeCallback(saveWorkspaceVBoxWidthRunnable);
            ThreadUtils.globalHandler().postDelayed(saveWorkspaceVBoxWidthRunnable, 1200);
        });

        c.workspaceSortBtn.setTooltip(new Tooltip(Locales.str("sortBtn")));
        c.workspaceSortBtn.setOnMouseClicked(event -> {
            changeSortByFileOrTime();
            openWorkspace(currentDir);
        });

        c.workspaceCreateFileBtn.setTooltip(new Tooltip(Locales.str("newFile")));
        c.workspaceCreateFileBtn.setOnMouseClicked(event -> {
            NotepadHeadButtons.newATempFile(currentDir.getAbsolutePath());
        });

        c.workspaceCreateDirBtn.setTooltip(new Tooltip(Locales.str("newDir")));
        c.workspaceCreateDirBtn.setOnMouseClicked(event ->
                JfoenixDialogUtils.editInput(Locales.str("newDir"), "", s -> {
            if (!TextUtils.isEmpty(s)) {
                File dir = new File(Utils.combine(currentDir.getAbsolutePath(), s));
                if (dir.exists()) {
                    SnackbarUtils.show(Locales.str("thisDirAlreadyExist"));
                } else {
                    boolean suc = dir.mkdir();
                    if (!suc) {
                        SnackbarUtils.show(Locales.str("thisDirCreateFail"));
                    } else {
                        openWorkspace(dir);
                    }
                }
            }
        }));

        c.workspaceGoUpBtn.setTooltip(new Tooltip(Locales.str("goUpDir")));
        c.workspaceGoUpBtn.setOnMouseClicked(event -> {
            File parentDir = null;
            try {
                var parent = IO.getParentPath(currentDir.getAbsolutePath(), false);
                parentDir = new File(parent);
            } catch (Exception e) {}

            openWorkspace(parentDir);
        });

        c.workspaceRefreshBtn.setTooltip(new Tooltip(Locales.str("refresh")));
        c.workspaceRefreshBtn.setOnMouseClicked(event -> openWorkspace(currentDir));

        c.workspaceEmptyHintLabel.setText(Locales.str("workspaceEmptyHint"));
        c.workspaceOpenBtn.setTooltip(new Tooltip(Locales.str("openWorkspace")));
        c.workspaceOpenBtn.setOnMouseClicked(event -> selectDirAsWorkspaceDialog());

        c.workspaceTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                if(c.workspaceTree.getSelectionModel().getSelectedItem() instanceof TreeItemEx<?> treeItemEx) {
                    File f = (File) treeItemEx.ex;
                    if (f.isFile()) {
                        //todo 目前直接打开
                        var openMode = DirAndFileJFXTreeCell.IsSupportOpenFile(f);
                        if (openMode == DirAndFileJFXTreeCell.OpenMode.Text) {
                            AllEditorsManager.Instance.openFile(f, true, true);
                        } else if (openMode == DirAndFileJFXTreeCell.OpenMode.Image) {
                            PictureWindow.show(f);
                        } else {
                            SnackbarUtils.show(Locales.str("cannotOpenThisFile"));
                        }
                    } else {
                        openWorkspace(f);
                    }
                }
            } else if (event.getButton() == MouseButton.SECONDARY) {
                if (c.workspaceTree.getSelectionModel().getSelectedItem() instanceof TreeItemEx<?> treeItemEx) {
                    currentItem = treeItemEx;
                    createDeleteAndRenameContextMenu().show(event.getPickResult().getIntersectedNode(),
                            javafx.geometry.Side.BOTTOM, 0, 0);
                }
            }
        });
    }

    private void openWorkspace(File dir) {
        openWorkspace(dir, false);
    }

    private void openWorkspace(File dir, boolean mustDelayAddedStageFocus) {
        if (dir == null || !dir.exists()) {
            return;
        }

        isWorkspaceShown = true;
        updateHeaderState();

        if (!isAddedStageFocus) {
            isAddedStageFocus = true;
            if (mustDelayAddedStageFocus) {
                ThreadUtils.globalHandler().postDelayed(()-> UIContext.context().addMainStageFocused(stageFocused), 500);
            } else {
                UIContext.context().addMainStageFocused(stageFocused);
            }
        }

        //init once
        initOnce();
        if (!isWorkspaceVBoxAdded) {
            if(DEBUG) Log.d(TAG, "first init treeItems");
            isWorkspaceVBoxAdded = true;
            //等待侧栏恢复宽度后再刷新文件树，避免影响编辑区滚动布局。
            pendingWorkspaceDir = dir;
            if(DEBUG) Log.d(TAG, "treeItems: delay to wait for width change info...>>>...current dir: " + dir + "current width notepad SubSplitPane: " + UIContext.context().notepadSubSplitPane.getWidth());

            var ctrl = UIContext.context();
            ctrl.notepadSubSplitPane.getItems().add(0, ctrl.workspaceVBox);
            //*******
            //经过实际分析，当设置了spitPane的左侧，就会导致界面变化从而导致codeArea无法滚动
            //*******
            setSplitPanePosition();

            //显示tree view
            TreeItem<String> base = new TreeItem<>();
            base.setExpanded(false);

            ctrl.workspaceTree.setRoot(base);
            ctrl.workspaceTree.setShowRoot(false);

            var pendingDir = dir;
            ThreadUtils.executeDelay(100, () -> Platform.runLater(() -> {
                if (isWorkspaceShown && isWorkspaceVBoxAdded && pendingDir.equals(pendingWorkspaceDir)) {
                    pendingWorkspaceDir = null;
                    openWorkspace(pendingDir);
                }
            }));
        } else {
            if(DEBUG) Log.d(TAG, "treeItems: just directly action...>>>...<<<...");
            if (!dir.exists()) {
                return;
            }

            currentDir = dir;
            var path = dir.getAbsolutePath();
            restoreWorkspaceViews();

            if(DEBUG) Log.d(TAG, "treeItems: real doing.....");
            var ctrl = UIContext.context();
            var base = ctrl.workspaceTree.getRoot();
            if (base == null) {
                base = new TreeItem<>();
                base.setExpanded(false);
                ctrl.workspaceTree.setRoot(base);
                ctrl.workspaceTree.setShowRoot(false);
            }
            base.getChildren().clear();

            ctrl.workspaceText.setText(path);
            var listFile = dir.listFiles();
            if (listFile != null) {
                var list = Arrays.asList(listFile);
                List<File> dirs, files;
                if (isSortByFileOrTime) {
                    dirs = list.stream().filter(File::isDirectory).sorted((File o1, File o2) -> {
                        String s = o1.getName().toLowerCase();
                        String s2 = o2.getName().toLowerCase();
                        return s.compareTo(s2);
                    }).toList();

                    files = list.stream().filter(File::isFile).sorted((File o1, File o2) -> {
                        String s = o1.getName().toLowerCase();
                        String s2 = o2.getName().toLowerCase();
                        return s.compareTo(s2);
                    }).toList();
                } else {
                    dirs = list.stream().filter(File::isDirectory)
                            .sorted((o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified())).toList();

                    files = list.stream().filter(File::isFile)
                            .sorted((o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified())).toList();
                }

                for (var d : dirs) {
                    var item = new TreeItemEx<>("> " + d.getName());
                    item.ex = d;
                    item.setExpanded(true);
                    base.getChildren().add(item);
                }

                for (var f : files) {
                    var item = new TreeItemEx<>(f.getName());
                    item.ex = f;
                    base.getChildren().add(item);
                }
            }

            if(DEBUG) Log.d(TAG, "treeItems: real done!");
            ThreadUtils.globalHandler().post(()-> {
                var user = GlobalCfgStores.user();
                user.setString(KEY_WORKSPACE_FILE, path);
                user.setBoolean(KEY_WORKSPACE_IS_OPENED, true);
            });
        }
    }

    private void updateHeaderState() {
        var c = UIContext.context();
        c.notepadCompactTitleLabel.setVisible(!isWorkspaceShown);
        c.notepadCompactTitleLabel.setManaged(!isWorkspaceShown);
        if (sidebarToggleTooltip != null) {
            sidebarToggleTooltip.setText(Locales.str(isWorkspaceShown ? "hideSidebar" : "showSidebar"));
        }

        if (isWorkspaceShown) {
            if (!c.notepadWindowTitleBox.getStyleClass().contains("workspace-column-background")) {
                c.notepadWindowTitleBox.getStyleClass().add("workspace-column-background");
            }
            if (!c.notepadWindowTitleBox.prefWidthProperty().isBound()) {
                c.notepadWindowTitleBox.minWidthProperty().bind(c.workspaceVBox.widthProperty());
                c.notepadWindowTitleBox.prefWidthProperty().bind(c.workspaceVBox.widthProperty());
                c.notepadWindowTitleBox.maxWidthProperty().bind(c.workspaceVBox.widthProperty());
                var bottomWidth = c.workspaceVBox.widthProperty().add(WIDTH_OF_WORKSPACE_DIVIDER);
                c.workspaceBottomExtension.minWidthProperty().bind(bottomWidth);
                c.workspaceBottomExtension.prefWidthProperty().bind(bottomWidth);
                c.workspaceBottomExtension.maxWidthProperty().bind(bottomWidth);
            }
            c.workspaceBottomExtension.setVisible(true);
            c.workspaceBottomExtension.setManaged(true);
        } else {
            c.notepadWindowTitleBox.getStyleClass().remove("workspace-column-background");
            c.notepadWindowTitleBox.minWidthProperty().unbind();
            c.notepadWindowTitleBox.prefWidthProperty().unbind();
            c.notepadWindowTitleBox.maxWidthProperty().unbind();
            c.workspaceBottomExtension.minWidthProperty().unbind();
            c.workspaceBottomExtension.prefWidthProperty().unbind();
            c.workspaceBottomExtension.maxWidthProperty().unbind();
            c.notepadWindowTitleBox.setMinWidth(Region.USE_COMPUTED_SIZE);
            c.notepadWindowTitleBox.setPrefWidth(Region.USE_COMPUTED_SIZE);
            c.notepadWindowTitleBox.setMaxWidth(Region.USE_PREF_SIZE);
            c.workspaceBottomExtension.setVisible(false);
            c.workspaceBottomExtension.setManaged(false);
        }
    }

    @Override
    public void openRecentWorkspace(String path) {
        if (!TextUtils.isEmpty(path)) {
            ThreadUtils.globalHandler().post(() -> saveOrReadRecentWorkspaces(path));
            openWorkspace(new File(path));
        }
    }

    /**
     * @param dir 传入的参数为null，则是读取
     */
    public static List<String> saveOrReadRecentWorkspaces(String dir) {
        var ss = new ArrayList<>(GlobalCfgStores.recent().getStringList(KEY_RECENT_WORKSPACES, List.of()));

        if (dir != null) {
            ss.add(0, dir); //追加新的到最前面
            var newss = ss.stream().distinct().filter(s -> new File(s).exists()).toList();
            int savedCount = Math.min(MAX_RECENT_WORKSPACES, newss.size());
            GlobalCfgStores.recent().setStringList(KEY_RECENT_WORKSPACES, newss.subList(0, savedCount));
            return null;
        } else {
            return ss.stream().distinct().filter(s -> new File(s).exists()).toList();
        }
    }

    @Override
    public void refreshWorkspace() {
        if (isWorkspaceShown) {
            openWorkspace(currentDir);
        }
    }

    @Override
    public void selectDirAsWorkspaceDialog() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        var initDir = GlobalCfgStores.user().getString(KEY_WORKSPACE_FILE, "");
        boolean isOk = false;
        if (!TextUtils.isEmpty(initDir)) {
            var dir = new File(initDir);
            if (dir.exists()) {
                directoryChooser.setInitialDirectory(dir);
                isOk = true;
            }
        }

        if (!isOk) {
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }

        File file = directoryChooser.showDialog(UIContext.context().getStage());
        if (file != null) {
            if (file.exists() && file.isDirectory()) {
                String path = file.getAbsolutePath();
                ThreadUtils.globalHandler().post(() -> saveOrReadRecentWorkspaces(path));
                openWorkspace(file);
            } else {
                JfoenixDialogUtils.alert(Locales.str("error"), Locales.str("setting.dirIsWrong"));
            }
        }
    }

    private ContextMenu createDeleteAndRenameContextMenu() {
        if (rightClickContextMenu != null) {
            return rightClickContextMenu;
        }

        MenuItem openToDir = new MenuItem(Locales.str("editor.openHereDir"));
        openToDir.setOnAction(event -> {
            WorkspaceManager workspaceManager = (WorkspaceManager) UIContext.context().getWorkspaceManager();
            File file = (File) workspaceManager.currentItem.ex;
            Utils.openFolderExplore(file);
        });

        MenuItem usingHexMenu = new MenuItem(Locales.str("usingHexShown"));
        usingHexMenu.setOnAction(event -> {

        });

        MenuItem deleteMenu = new MenuItem(Locales.str("delete"));
        deleteMenu.setOnAction(event -> {
            WorkspaceManager workspaceManager = (WorkspaceManager) UIContext.context().getWorkspaceManager();
            File file = (File) workspaceManager.currentItem.ex;
            if(DEBUG) Log.d(WorkspaceManager.TAG, "" + file);

            if (file.exists() && file.isDirectory()) {
                IO.deleteDirJava(file);
                //虚拟一个系统的focus变化来触发各个部件的刷新检测
                ThreadUtils.executeDelay(50, ()-> Platform.runLater(()-> UIContext.context().notifyStageFocused()));
            } else {
                var area = AllEditorsManager.Instance.getAreaByFilePath(file);
                if (area != null) {
                    SnackbarUtils.show(Locales.str("cannotBeDeleteWhenOpen"));
                } else {
                    try {
                        file.delete();
                    } catch (Exception e) {
                        //ignore
                    }

                    ThreadUtils.executeDelay(50, ()-> Platform.runLater(()-> UIContext.context().getWorkspaceManager().refreshWorkspace()));
                }
            }
        });

        MenuItem modifyNameMenu = new MenuItem(Locales.str("modifyName"));
        modifyNameMenu.setOnAction(event -> {
            WorkspaceManager workspaceManager = (WorkspaceManager) UIContext.context().getWorkspaceManager();
            File file = (File) workspaceManager.currentItem.ex;
            var area = AllEditorsManager.Instance.getAreaByFilePath(file);
            if (area != null) {
                area.getEditor().rename();
            } else {
                JfoenixDialogUtils.editInput(Locales.ALERT(), file.getName(), s -> {
                    if (!TextUtils.isEmpty(s)) {
                        var ans = Utils.rename(file, s);
                        if (ans == null) {
                            SnackbarUtils.show("maybe you do not save this file!");
                        } else if (ans.newFullPath() == null && ans.run() != null) {
                            JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("doUWantReplaceOldFile"),
                                    0, 0,
                                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, null,
                                            () -> {
                                                var newFullPa = ans.run().invoke();
                                                if(DEBUG) Log.d(WorkspaceManager.TAG, "changed name " + newFullPa);
                                            }),
                                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, null)
                            );
                        } else if (ans.newFullPath() != null) {
                            if(DEBUG) Log.d(WorkspaceManager.TAG, "changed name " + ans.newFullPath());
                            UIContext.context().getWorkspaceManager().refreshWorkspace();
                        }
                    }
                });
            }
        });

        var menu = new ContextMenu();
        menu.getItems().addAll(deleteMenu, modifyNameMenu, openToDir);
        rightClickContextMenu = menu;
        return menu;
    }
}

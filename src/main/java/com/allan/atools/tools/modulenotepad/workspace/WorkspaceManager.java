package com.allan.atools.tools.modulenotepad.workspace;

import com.allan.atools.UIContext;
import com.allan.atools.controllerwindow.PictureWindow;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.ui.IconfontCreator;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.ui.controls.DirAndFileJFXTreeCell;
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
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class WorkspaceManager implements IWorkspace {
    private static final String TAG = "WorkspaceManager";

    private static final String KEY_WORKSPACE_FILE = "workspace_dir";
    private static final String KEY_WORKSPACE_IS_OPENED = "workspace_is_opened";
    private static final String KEY_WORKSPACE_SORT_MODE = "workspace_sort";
    private static final String KEY_WORKSPACE_WIDTH = "workspace_width";

    private static final int WIDTH_OF_WORKSPACE_MIN = 49;


    private volatile boolean isWorkspaceShown = true;
    private File currentDir;
    private boolean isSortByFileOrTime;
    private boolean isWorkspaceVBoxAdded = true;

    private TreeItemEx<?> currentItem;
    private ContextMenu rightClickContextMenu;

    private File nextCurrentDir;

    private boolean isAddedStageFocus = false;
    private final Action0 stageFocused = ()-> {
        if (isWorkspaceShown) {
            openWorkspace(currentDir, true);
        }
    };

    private boolean changeSortByFileOrTime() {
        isSortByFileOrTime = !isSortByFileOrTime;
        UIContext.sharedPref.edit().putBoolean(KEY_WORKSPACE_SORT_MODE, isSortByFileOrTime).commit();
        return isSortByFileOrTime;
    }

    public WorkspaceManager() {
        isSortByFileOrTime = UIContext.sharedPref.getBoolean(KEY_WORKSPACE_SORT_MODE, true);
    }

    @Override
    public void removeWorkspace(boolean savedCfg) {
        isWorkspaceShown = false;
        if (isWorkspaceVBoxAdded) {
            UIContext.context().notepadSubSplitPane.getItems().remove(UIContext.context().workspaceVBox);
            isWorkspaceVBoxAdded = false;
        }
        if (isAddedStageFocus) {
            UIContext.context().removeMainStageFocused(stageFocused);
            isAddedStageFocus = false;
        }

        if (savedCfg) {
            ThreadUtils.globalHandler().post(()-> {
                UIContext.sharedPref.edit().putBoolean(KEY_WORKSPACE_IS_OPENED, false).commit();
                Platform.runLater(()-> UIContext.context().requestFocus4Jfoenix());
            });
        }
    }

    @Override
    public void initWhenAppStart() {
        if (UIContext.sharedPref.getBoolean(KEY_WORKSPACE_IS_OPENED, false)) {
           openWorkspace(new File(UIContext.sharedPref.getString(KEY_WORKSPACE_FILE, "")));
        }
    }

    @Override
    public void ifRefreshWorkspace(File changedFile) {
        Log.d(TAG, "refresh workspace " + isWorkspaceShown);
        if (isWorkspaceShown) {
            var dir = IO.getParentPath(changedFile.getAbsolutePath(), false);
            var curDir = currentDir.getAbsolutePath();
            if (curDir.endsWith("\\") || curDir.endsWith("/")) {
                curDir = curDir.substring(0, curDir.length() - 1);
            }
            if (TextUtils.equals(dir, curDir)) {
                Log.d(TAG, "need refresh workspace: " + currentDir);
                openWorkspace(currentDir);
            }
        }
    }

    private int workspaceWidth;
    private final Runnable saveWorkspaceVBoxWidthRunnable = () -> {
        //多偏移2个像素
        UIContext.sharedPref.edit().putInt(KEY_WORKSPACE_WIDTH, workspaceWidth + 2).commit();
        Log.d("save workspace box width " + workspaceWidth);
    };

    private void setSplitPanePosition() {
        double totalWidth = UIContext.context().notepadSubSplitPane.getWidth();
        double pos = (double) workspaceWidth / totalWidth;
        Log.d(TAG, "todo notepad SubSplitPane setDividerPositions " + pos + " workspace Width:" + workspaceWidth + "/ notepadSub SplitPane width: " + totalWidth);
        UIContext.context().notepadSubSplitPane.setDividerPositions(pos);
    }

    private void initOnce() {
        Log.d(TAG, "init once");
        var c = UIContext.context();
        if (c.workspaceRefreshBtn.getOnMouseClicked() != null) {
            return;
        }


        if (workspaceWidth == 0) {
            workspaceWidth = UIContext.sharedPref.getInt(KEY_WORKSPACE_WIDTH, 175);
        }
        Log.d(TAG, "init once real@ Workspace Width " + workspaceWidth);

        c.workspaceVBox.widthProperty().addListener((observable, oldValue, newValue) -> {
            workspaceWidth = newValue.intValue();
            Log.d(TAG, "todo mWorkspace Width " + newValue.intValue() + " total notepad SubSplitPane: " + UIContext.context().notepadSubSplitPane.getWidth());
            if (workspaceWidth < WIDTH_OF_WORKSPACE_MIN) {
                if(c.workspaceVBox.isVisible()) {
                    Log.d(TAG, "change workspace VBox false");
                    c.workspaceVBox.setVisible(false);
                }
            } else {
                if (!c.workspaceVBox.isVisible()) {
                    c.workspaceVBox.setVisible(true);
                    Log.d(TAG, "change workspace VBox true");
                }
            }

            ThreadUtils.globalHandler().removeCallback(saveWorkspaceVBoxWidthRunnable);
            ThreadUtils.globalHandler().postDelayed(saveWorkspaceVBoxWidthRunnable, 1200);
            if (nextCurrentDir != null) {
                var finalNextCurrentDir = nextCurrentDir;
                nextCurrentDir = null;
                ThreadUtils.executeDelay(100, ()-> {
                    Platform.runLater(()-> {
                        Log.d(TAG, "treeItems: delayed to do after width info<<<< " + finalNextCurrentDir);
                        openWorkspace(finalNextCurrentDir);
                    });
                });
            }
        });

        IconfontCreator.setText(c.workspaceSortBtn, "paixu", 18, Colors.ColorHeadButton.invoke());
        c.workspaceSortBtn.setTooltip(new Tooltip(Locales.str("sortBtn")));
        c.workspaceSortBtn.setOnMouseClicked(event -> {
            changeSortByFileOrTime();
            openWorkspace(currentDir);
        });

        IconfontCreator.setText(c.workspaceCreateFileBtn, "add-select", 19, Colors.ColorHeadButton.invoke());
        c.workspaceCreateFileBtn.setTooltip(new Tooltip(Locales.str("newFile")));
        c.workspaceCreateFileBtn.setOnMouseClicked(event -> {
            NotepadHeadButtons.newATempFile(currentDir.getAbsolutePath());
        });

        IconfontCreator.setText(c.workspaceCreateDirBtn, "xinjianwenjianjia1", 16, Colors.ColorHeadButton.invoke());
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

        IconfontCreator.setText(c.workspaceGoUpBtn, "arrowup", 18, Colors.ColorHeadButton.invoke());
        c.workspaceGoUpBtn.setTooltip(new Tooltip(Locales.str("goUpDir")));
        c.workspaceGoUpBtn.setOnMouseClicked(event -> {
            File parentDir = null;
            try {
                var parent = IO.getParentPath(currentDir.getAbsolutePath(), false);
                parentDir = new File(parent);
            } catch (Exception e) {}

            openWorkspace(parentDir);
        });

        IconfontCreator.setText(c.workspaceRefreshBtn, "exchangerate", 17, Colors.ColorHeadButton.invoke());
        c.workspaceRefreshBtn.setTooltip(new Tooltip(Locales.str("refresh")));
        c.workspaceRefreshBtn.setOnMouseClicked(event -> openWorkspace(currentDir));

        IconfontCreator.setText(c.workspaceCloseBtn, "close", 19, "#ff0000");
        c.workspaceCloseBtn.setTooltip(new Tooltip(Locales.str("close")));
        c.workspaceCloseBtn.setOnMouseClicked(event -> removeWorkspace(true));

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
            Log.d(TAG, "first init treeItems");
            isWorkspaceVBoxAdded = true;
            //*** 这里delay去等待width的变化；如果出现removeWorkspace或者快速的二次开启就会有bug。
            //不过我认为不可能出现这种case
            nextCurrentDir = dir; //*** 标记nextCurrentDir 等待 ****
            Log.d(TAG, "treeItems: delay to wait for width change info...>>>...nextCurFile: " + dir + "current width notepad SubSplitPane: " + UIContext.context().notepadSubSplitPane.getWidth());

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
        } else {
            Log.d(TAG, "treeItems: just directly action...>>>...<<<...");
            if (!dir.exists()) {
                return;
            }

            currentDir = dir;
            var path = dir.getAbsolutePath();

            Log.d(TAG, "treeItems: real doing.....");
            var ctrl = UIContext.context();
            var base = ctrl.workspaceTree.getRoot();
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

            isWorkspaceShown = true;
            Log.d(TAG, "treeItems: real done!");
            ThreadUtils.globalHandler().post(()->
                    UIContext.sharedPref.edit()
                            .putString(KEY_WORKSPACE_FILE, path)
                            .putBoolean(KEY_WORKSPACE_IS_OPENED, true).commit());
        }
    }

    @Override
    public void openLastWorkspace() {
        var initDir = UIContext.sharedPref.getString(KEY_WORKSPACE_FILE, "");
        if (!TextUtils.isEmpty(initDir)) {
            openWorkspace(new File(initDir));
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
        var initDir = UIContext.sharedPref.getString(KEY_WORKSPACE_FILE, "");
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
            Utils.openFolderExplore(file.getParentFile());
        });

        MenuItem usingHexMenu = new MenuItem(Locales.str("usingHexShown"));
        usingHexMenu.setOnAction(event -> {

        });

        MenuItem deleteMenu = new MenuItem(Locales.str("delete"));
        deleteMenu.setOnAction(event -> {
            WorkspaceManager workspaceManager = (WorkspaceManager) UIContext.context().getWorkspaceManager();
            File file = (File) workspaceManager.currentItem.ex;
            Log.d(WorkspaceManager.TAG, "" + file);

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
                                                Log.d(WorkspaceManager.TAG, "changed name " + newFullPa);
                                            }),
                                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, null)
                            );
                        } else if (ans.newFullPath() != null) {
                            Log.d(WorkspaceManager.TAG, "changed name " + ans.newFullPath());
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

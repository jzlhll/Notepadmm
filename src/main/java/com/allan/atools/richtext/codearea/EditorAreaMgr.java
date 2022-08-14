package com.allan.atools.richtext.codearea;

import com.allan.atools.pop.impl.TabTitleCreatorImpl;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.beans.ReplaceParams;
import com.allan.atools.text.FinderFactory;
import com.allan.atools.text.IEditorAreaEx;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.modulenotepad.Highlight;
import com.allan.atools.UIContext;
import com.allan.atools.tools.modulenotepad.base.ITextFindAndReplace;
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager;
import com.allan.atools.pop.GlobalPopupManager;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.atools.utils.*;
import com.allan.baseparty.*;
import com.allan.baseparty.utils.ReflectionUtils;
import com.allan.uilibs.richtexts.MyLineNumFactory;
import com.allan.uilibs.richtexts.MyNoneLineNumFactory;
import com.allan.baseparty.handler.TextUtils;
import com.allan.baseparty.memory.RefWatcher;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.skins.JFXTabPaneSkin;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.GenericStyledArea;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.function.IntFunction;

import static com.allan.atools.SettingPreferences.editHasNumberKey;
import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;
import static org.fxmisc.richtext.model.TwoDimensional.Bias.Forward;

public class EditorAreaMgr implements IEditorAreaEx<Collection<String>, String, Collection<String>>, ITextFindAndReplace {
    static final String TAG = "Editor";

    private static final String TEMP_MASK_FILE = " *";

    @Override
    public boolean isEditorCodeFind() {
        return false;
    }

    public class TextChanged extends BaseChanged<Action0> {
        private final ChangeListener<String> _textChanged = (observableValue, s, t1) -> {
            if(EditorArea.DEBUG_EDITOR) Log.v("text changed !!");
            if (mActions != null) {
                for (var a : mActions) {
                    a.invoke();
                }
            }

            textHasChanged();
        };

        @Override
        public void modifiedActions() {
            //do nothing...
        }

        @Override
        public void destroy() {
            mActions.clear();
            mActions = null;
            area.textProperty().removeListener(_textChanged);
        }

        @Override
        public void init() {
            area.textProperty().addListener(_textChanged);
        }
    }

    public class VisibleParagraphChanged extends BaseChanged<Action0>{
        private double lastY = -124.0;
        private final ChangeListener<Double> _yChanged = (observable, oldValue, newValue) -> {
            if(EditorArea.DEBUG_EDITOR) Log.v("area: _y changed " + newValue);
            if (lastY != newValue) {
                lastY = newValue;
                if (mActions != null) {
                    for (var a : mActions) {
                        a.invoke();
                    }
                }
            }
        };

        @Override
        public void modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.estimatedScrollYProperty().addListener(_yChanged);
                    isSet = true;
                }
            } else {
                if (isSet) {
                    area.estimatedScrollYProperty().removeListener(_yChanged);
                    isSet = false;
                }
            }
        }

        @Override
        public void destroy() {
            if (mActions != null) {
                mActions.clear();
                mActions = null;
            }
            if (isSet) {
                area.estimatedScrollYProperty().removeListener(_yChanged);
                isSet = false;
            }
        }

        @Override
        public void init() {
            //do nothing.
        }
    }

    public class SelectionChanged extends BaseChanged<Action<String>> {
        private final ChangeListener<IndexRange> mSelectionChanged = (observable, oldValue, newValue) -> {
            if (EditorArea.DEBUG_EDITOR) Log.v("selection changed " + newValue.getLength());

            if (mActions != null) {
                String s = newValue.getLength() == 0 ? null : area.getSelectedText();
                for (var a : mActions) {
                    a.invoke(s);
                }
            }
        };

        @Override
        public void modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.selectionProperty().addListener(mSelectionChanged);
                    isSet = true;
                }
            } else {
                if (isSet) {
                    area.selectionProperty().removeListener(mSelectionChanged);
                    isSet = false;
                }
            }
        }

        @Override
        public void destroy() {
            mActions.clear();
            mActions = null;
            if (isSet) {
                area.selectionProperty().removeListener(mSelectionChanged);
                isSet = false;
            }
        }

        @Override
        public void init() {
            //do nothing
        }
    }

    public class CaretPosChanged extends BaseChanged<Action5<Integer, Integer, Integer, Integer, Integer>> {
        private final ChangeListener<Integer> mListener = (observable, oldValue, newValue) -> {
            if (EditorArea.DEBUG_EDITOR) Log.v("caret pos changed " + newValue);
            var selection = area.getSelection();
            var s = area.offsetToPosition(selection.getStart(), Forward);
            var s2  = area.offsetToPosition(selection.getEnd(), Backward);

            if (mActions != null) {
                for (var a : mActions) {
                    a.invoke(area.getCaretPosition(),
                            s.getMajor(), s.getMinor(),
                            selection.getLength(),
                            s2.getMajor() - s.getMajor() + 1);
                }
            }
        };

        @Override
        public void modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.caretPositionProperty().addListener(mListener);
                    isSet = true;
                }
            } else {
                if (isSet) {
                    area.caretPositionProperty().removeListener(mListener);
                    isSet = false;
                }
            }
        }

        @Override
        public void destroy() {
            mActions.clear();
            mActions = null;
            if (isSet) {
                area.caretPositionProperty().removeListener(mListener);
                isSet = false;
            }
        }

        @Override
        public void init() {
            //do nothing.
        }
    }

    private GenericStyledArea<Collection<String>, String, Collection<String>> area;

    @Override
    public GenericStyledArea<Collection<String>, String, Collection<String>> getArea() {
        return area;
    }

    public final BaseChanged<Action0> textChanged = new TextChanged();
    public final BaseChanged<Action0> visibleParagraphChanged = new VisibleParagraphChanged();
    public final BaseChanged<Action<String>> selectionChanged = new SelectionChanged();
    final BaseChanged<Action5<Integer, Integer, Integer, Integer, Integer>> caretPosChanged = new CaretPosChanged();

    private final EditorBaseFocus editorFocus = new EditorBaseFocus(this);
    private File sourceFile;
    public File getSourceFile() {
        return sourceFile;
    }

    public int getFileLength() {
        return (int) sourceFile.length();
    }

    String fullPath;
    Tab tab;
    public Tab getTab() {
        return tab;
    }

    private boolean mIsNotSaved = false;
    private Object mLastNextUndo;
    /**
     * 反射找出本tab的title tabLabel
     */
    private Label tabLabel;
    private Button closeBtn;

    private boolean isFake;
    public boolean getIsFake() {return isFake;}

    private String fileEncoding;

    private int currentCaretPos, selectedLength, selectLineCount, currentCaretColNum, currentCaretLineNum;

    public int getCurrentCaretPos() {
        return currentCaretPos;
    }

    public int getSelectLineCount() {return selectLineCount;}

    public int getSelectedLen() {
        return selectedLength;
    }

    /**
     * 新增：底部是wrap的状态。
     */
    public boolean isWrap = false;

    public int getCurrentCaretColNum() {
        return currentCaretColNum;
    }

    public int getCurrentCaretLineNum() {
        return currentCaretLineNum;
    }

    @Override
    public void setFileEncoding(String fileEncoding) {this.fileEncoding = fileEncoding;}

    @Override
    public String getFileEncoding() {return fileEncoding;}

    @Override
    public void destroy() {
        if (tabLabel != null) {
            tabLabel.setOnMouseClicked(null);
            tabLabel.setTooltip(null);
            tabLabel = null;
        }

        textChanged.destroy();
        visibleParagraphChanged.destroy();
        selectionChanged.destroy();
        caretPosChanged.destroy();

        editorFocus.removeFocusChanged();

        tab = null;

        UIContext.allOpenedFileList.remove(sourceFile);
        sourceFile = null;
        area = null;
    }

    @Override
    public boolean isDestroyed() {
        return area == null || sourceFile == null;
    }

    private enum ConfirmStat {
        Normal,
        DialogOpened,
    }

    private ConfirmStat mConfirmStat = ConfirmStat.Normal;

    public void closeTab() {
        Optional.ofNullable(closeBtn).ifPresent(Button::fire);
    }

    EditorAreaMgr(EditorArea area, File sourceFile, Tab tab, boolean isFake) {
        this.sourceFile = sourceFile;
        UIContext.allOpenedFileList.add(sourceFile);
        Log.w("new EditorBase:: " + sourceFile.lastModified());
        this.fullPath = sourceFile.getAbsolutePath();
        this.tab = tab;
        this.isFake = isFake;
        tab.setText(isFake ? sourceFile.getName() + TEMP_MASK_FILE : sourceFile.getName());
        tab.setOnCloseRequest(event -> {
            var text = tab.getText();
            if (mConfirmStat == ConfirmStat.Normal) {
                if (text.endsWith(TEMP_MASK_FILE)) {
                    event.consume();
                    mConfirmStat = ConfirmStat.DialogOpened;
                    JfoenixDialogUtils.confirm(Locales.ALERT(),
                            Locales.str("ifUwantCloseIt").replace("%s", text.replace(TEMP_MASK_FILE, "")),
                            0, 0,
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept,
                                    Locales.str("save"), () -> {
                                saveContent(null, true);
                                closeTab();
                            }),
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Extra,
                                    Locales.str("notSave"), this::closeTab),
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, () -> {
                                mConfirmStat = ConfirmStat.Normal;
                            }));
                }
            }
        });

        markCurrentFileTs();

        this.area = area;
        initArea();

        if (RefWatcher.getInstance() != null) {
            RefWatcher.getInstance().watch(this, sourceFile.getPath());
        }
    }

    private void textSaved() {
        var a = area;
        if (a != null) {
            mIsNotSaved = false;
            tab.setText(isFake ? sourceFile.getName() + TEMP_MASK_FILE : sourceFile.getName());
            mLastNextUndo = a.getUndoManager().getNextUndo();
        }
    }

    private void textHasChanged() {
        //todo 变成更好的文件变化判断
        var nextUndo = area.getUndoManager().getNextUndo();
        if (nextUndo == mLastNextUndo) {
            mIsNotSaved = false;
            tab.setText(sourceFile.getName());
        } else {
            if (!mIsNotSaved) {
                mIsNotSaved = true;
                tab.setText(sourceFile.getName() + TEMP_MASK_FILE);
            }
        }
    }

    private void markCurrentFileTs() {
        editorFocus.mLastFileChangedTs = !isFake ? sourceFile.lastModified() : 0L;
        textSaved();
    }

    private void saveContentInner(boolean forceSave, String sourceCode) {
        try {
            Log.d("Files writeString save content forceSave:" + forceSave);
            Files.writeString(Path.of(sourceFile.getAbsolutePath()), sourceCode, Charset.forName(getFileEncoding()));
            isFake = false;
            markCurrentFileTs();
            notifyWorkspaceRefreshDelayed();
        } catch (IOException e) {
            e.printStackTrace();
        }

        AllEditorsManager.delayToSaveRecentFile(sourceFile.getAbsolutePath());
    }

    public void saveContent(ActionEvent event, boolean forceSave) {
        Log.w("save content forceSave:" + forceSave);
        if (!mIsNotSaved && !forceSave) {
            return;
        }

        if (isFake && !sourceFile.exists()) {
            try {
                Files.createFile(Path.of(sourceFile.getAbsolutePath()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String sourceCode = area.getText();
        if (sourceFile.canWrite()) {
            saveContentInner(forceSave, sourceCode);
        } else {
            var parentDir = IO.getParentPath(sourceFile.getAbsolutePath(), true);
            boolean isOkCreateDir = true;
            try {
                Files.createDirectories(Path.of(parentDir));
            } catch (IOException e) {
                e.printStackTrace();
                isOkCreateDir = false;
            }
            if (isOkCreateDir) {
                saveContentInner(forceSave, sourceCode);
            } else {
                //If File is deleted re create file then update com.base.content again
                if (forceSave) {
                    saveContentInner(forceSave, sourceCode);
                } else {
                    JfoenixDialogUtils.confirm(Locales.str("notification"), Locales.str("ifSaveThisTxtFile"),
                            0, 0,
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, null, ()-> saveContentInner(forceSave, sourceCode)),
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, null));
                }
            }
        }
    }

    private void initArea() {
        IntFunction<Node> lineNumberFactory = SettingPreferences.getBoolean(editHasNumberKey) ? MyLineNumFactory.get(area) : MyNoneLineNumFactory.get();
        area.setParagraphGraphicFactory(lineNumberFactory);

        mLastNextUndo = area.getUndoManager().getNextUndo();

        textChanged.init();
        visibleParagraphChanged.init();
        caretPosChanged.init();
        selectionChanged.init();

        caretPosChanged.addAction((caretPos, lineNum, colNum, length, lineCount) -> {
            Log.d("area: caretPos changed!");
            selectLineCount = lineCount;
            var s = length > 0 ? String.format(Locales.str("editor.caretIndicate"), /*caretPos,*/ lineNum, colNum, length, lineCount)
                    : String.format(Locales.str("editor.caretIndicate.short"), lineNum, colNum);
            selectedLength = length;
            currentCaretPos = caretPos;
            currentCaretColNum = colNum;
            currentCaretLineNum = lineNum;
            UIContext.bottomIndicateProp.set(s);
        });

        ContextMenu contextMenu = createMenu();
        area.setContextMenu(contextMenu);

        initTitleClick();
    }

    private void initTitleClick() {
        ThreadUtils.executeDelay(1000, ()-> {
            if (area == null) {
                return;
            }

            var tp = tab.getTabPane();
            JFXTabPaneSkin skin = null;
            int maxCount = 0;
            do {
                if (tp != null) {
                    skin = (JFXTabPaneSkin) tp.getSkin();
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (ThreadUtils.sBeClosing || tab == null || area == null) {
                    return;
                }
                if(maxCount++ == 100) break;
            } while (skin == null);

            if (skin == null) {
                return;
            }

            try {
                var head = ReflectionUtils.getPrivateFieldValue(skin, "header");
                var headersRegion = ReflectionUtils.getPrivateFieldValue(head, "headersRegion");
                if (headersRegion instanceof StackPane headerRegionStackPane) {
                    for (Node cur : headerRegionStackPane.getChildren()) {
                        var tabInNode = ReflectionUtils.getPrivateFieldValue(cur, "tab");
                        if (tabInNode == this.tab) {
                            var tl = ReflectionUtils.getPrivateFieldValue(cur, "tabLabel");
                            if (tl instanceof Label) {
                                tabLabel = (Label) tl;
                            }

                            var innerObj = ReflectionUtils.getPrivateFieldValue(cur, "inner");
                            if (innerObj instanceof HBox inner) {
                                for (var n : inner.getChildren()) {
                                    if (n instanceof Button) {
                                        closeBtn = (Button) n;
                                        break;
                                    }
                                    if (closeBtn != null) {
                                        break;
                                    }
                                }
                            }

                            break;
                        }
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }

            Platform.runLater(()-> {
                //再init title的点击事件和悬浮提示
                if (tabLabel != null) {
                    tabLabel.setOnMouseClicked(event-> {
                        if (event.getButton() == MouseButton.SECONDARY) {
                            var region = new TabTitleCreatorImpl().createPop(ev -> {
                                Log.d("ev " + ev);
                                if (TabTitleCreatorImpl.EVENT_MODIFY_NAME.equals(ev)) {
                                    rename();
                                } else if (TabTitleCreatorImpl.EVENT_CLOSE_OTHERS.equals(ev)) {
                                    AllEditorsManager.Instance.removeAllOtherTabs(tab);
                                } else if (TabTitleCreatorImpl.EVENT_OPEN_TO_EXPLORE.equals(ev)) {
                                    openCurrentFolder(null);
                                }
                                GlobalPopupManager.instance().hide();
                            });

                            GlobalPopupManager.instance().setContent(region).setHeight(300).show(tabLabel, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT);
                        }
                    });

                    tabLabel.setTooltip(new Tooltip(sourceFile.getAbsolutePath()));
                }
            });
        });
    }

    @Override
    public void rename() {
        JfoenixDialogUtils.editInput(Locales.ALERT(), sourceFile.getName(), s -> {
            if (!TextUtils.isEmpty(s)) {
                var ans = Utils.rename(sourceFile, s);
                if (ans == null) {
                    SnackbarUtils.show("maybe you do not save this file!");
                } else if (ans.newFullPath() == null && ans.run() != null) {
                    JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("doUWantReplaceOldFile"),
                            0, 0,
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, null, () -> {
                                var newFullPa = ans.run().invoke();
                                if (newFullPa != null) {
                                    afterRename(newFullPa);
                                }
                            }),
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, null));
                } else if (ans.newFullPath() != null) {
                    Log.d("changed name " + ans.newFullPath());
                    afterRename(ans.newFullPath());
                }
            }
        });
    }

    @Override
    public void resetText(String text) {
        area.replaceText(text);
        markCurrentFileTs();
    }

    @Override
    public boolean canClosed() {
        return tab == null || !isFake;
    }

    @Override
    public void removeStageFocus() {
        editorFocus.removeFocusChanged();
    }

    @Override
    public void addStageFocus() {
        editorFocus.addFocusChanged();
    }

    @Override
    public void checkFileIfChanged() {
        Log.d(TAG, "check file if changed!!!");
        editorFocus.checkFileChangedTs();
    }

    private void afterRename(String newFile) {
        editorFocus.removeFocusChanged();
        var newf = new File(newFile);
        AllEditorsManager.Instance.reOpenCurrentFile(tab, newf, getFileEncoding());
        var oldFile = sourceFile;
        UIContext.allOpenedFileList.remove(oldFile);
        sourceFile = newf;
        UIContext.allOpenedFileList.add(newf);

        isFake = false;
        AllEditorsManager.delayToSaveRecentFile(newFile);

        markCurrentFileTs();
        if (tabLabel.getTooltip() == null) {
            tabLabel.setTooltip(new Tooltip(newf.getAbsolutePath()));
        } else {
            tabLabel.getTooltip().setText(newf.getAbsolutePath());
        }

        ThreadUtils.globalHandler().postDelayed(editorFocus::addFocusChanged, 180);
        notifyWorkspaceRefreshDelayed();
    }

    private void notifyWorkspaceRefreshDelayed() {
        ThreadUtils.globalHandler().postDelayed(()->
                Platform.runLater(()->
                        UIContext.context().getWorkspaceManager().ifRefreshWorkspace(sourceFile)),
                200);
    }

    @Override
    public ContextMenu createMenu() {
        //Create Menu Items
        //MenuItem save =             new MenuItem("保存");
        MenuItem copy =             new MenuItem(Locales.str("editor.copySelect"));
        MenuItem copyLine =             new MenuItem(Locales.str("editor.copyLine"));
        MenuItem openFolder =       new MenuItem(Locales.str("editor.openHereDir"));
        MenuItem openTerminal =     new MenuItem(Locales.str("editor.programTerminal"));
        MenuItem openTerminalHere = new MenuItem(Locales.str("editor.directoryTerminal"));
        MenuItem screenShot =       new MenuItem(Locales.str("editor.screenshot"));
        //Add Event Handler
        //save.setOnAction(this::saveContent);

        copyLine.setOnAction(e -> {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 封装文本内容
            var s = area.getText(Highlight.getCurrentCaretLineNum(area));
            var trans = new StringSelection(s);
            // 把文本内容设置到系统剪贴板
            clipboard.setContents(trans, null);
        });

        copy.setOnAction(actionEvent -> {
            // 获取系统剪贴板
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 封装文本内容
            var trans = new StringSelection(area.getSelectedText());
            // 把文本内容设置到系统剪贴板
            clipboard.setContents(trans, null);
        });
        openFolder.setOnAction(this::openCurrentFolder);
        openTerminal.setOnAction(this::openTerminal);
        openTerminalHere.setOnAction(this::openTerminalHere);
        screenShot.setOnAction(this::captureScreenShot);

        //Add Menu items in Context Menu
        ContextMenu contextMenu = new ContextMenu();

        //contextMenu.getItems().add(save);
        contextMenu.getItems().add(copyLine);
        contextMenu.getItems().add(screenShot);
        contextMenu.getItems().add(copy);
        // contextMenu.getItems().add(openTerminal);
        contextMenu.getItems().add(openTerminalHere);
        contextMenu.getItems().add(openFolder);

        return contextMenu;
    }

    private void captureScreenShot(ActionEvent event) {
        Platform.runLater(Utils::captureScreenShot);
    }

    private void openCurrentFolder(ActionEvent event) {
        if (sourceFile != null) {
            if (sourceFile.getParentFile() != null) {
                Utils.openFolderExplore(sourceFile.getParentFile());
            }
        }
    }

    private void openTerminal(ActionEvent event) {
        Utils.openTerminal();
    }

    private void openTerminalHere(ActionEvent event) {
        if (sourceFile != null)
            if (sourceFile.getParentFile() != null)
                Utils.openTerminalHere(sourceFile.getParentFile());
    }

    @Override
    public void find(SearchParams params, Action<OneFileSearchResults> action) {
        ThreadUtils.execute(()-> {
            String text = area.getText();
            int[] totalLines = {0};
            var ans = FinderFactory.find(text,
                    SettingPreferences.getBoolean(SettingPreferences.searchResultHasNumberKey),
                    new SearchParams[] {params},
                    totalLines);
            var result = new OneFileSearchResults()
                    .addFile(sourceFile)
                    .addArea(this)
                    .addTotalLen(text == null ? 0 : text.length())
                    .addResults(ans);
            action.invoke(result);
        });
    }

    @Override
    public void findAdvance(SearchParams[] params, Action<OneFileSearchResults> action) {
        ThreadUtils.execute(()-> {
            String text = area.getText();
            int[] totalLines = new int[]{0};
            var ans = FinderFactory.find(text,
                    SettingPreferences.getBoolean(SettingPreferences.searchResultHasNumberKey),
                    params,
                    totalLines);
            var result = new OneFileSearchResults()
                    .addFile(sourceFile)
                    .addArea(this)
                    .addTotalLen(text == null ? 0 : text.length())
                    .addResults(ans);
            action.invoke(result);
        });
    }

    @Override
    public StringBuilder replace(ReplaceParams params) {
        return null;
    }

    public void trigger(SearchParams temporaryText, SearchParams searchText) {
        //do nothing...
        throw new RuntimeException("base should not call trigger in EditorBase");
    }

    private static class EditorBaseFocus {
        private final EditorAreaMgr editor;

        EditorBaseFocus(EditorAreaMgr base) {
            editor = base;
        }

        private boolean mIsAddedFocusChanged = false;

        private long mLastFileChangedTs;

        private Action0 mFocused;

        private void checkFileChangedTs() {
            if (editor.isFake) {
                Log.d("EditorBase","checkFile ChangedTs is fake file, ignored!" + editor.sourceFile);
                return;
            }
            //顺序不得变化
            if (!editor.sourceFile.exists()) {
                JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("fileNotExsit"),
                        0, 0,
                        new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, Locales.str("save"), ()-> {
                            removeFocusChanged();
                            editor.saveContent(null, true);
                            ThreadUtils.globalHandler().postDelayed(this::addFocusChanged, 250);
                        }),
                        new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, Locales.str("close"), ()-> {
                            //说明不想要了。我们先移除监听再说
                            removeFocusChanged();
                            editor.closeTab();
                        }));
                return;
            }

            if (mLastFileChangedTs == editor.sourceFile.lastModified()) {
                //Log.d(TAG, "!@focus no changed this file " + editorBase.sourceFile);
                return;
            }

            //Log.d("!@focus reOpenCurrent： lastModify :  " + lastModify + ", lastFileChangedTs: " + mLastFileChangedTs + " " + sourceFile);
            AllEditorsManager.Instance.reOpenCurrentFile(editor.tab, editor.sourceFile, null);
        }

        private void removeFocusChanged() {
            if (mIsAddedFocusChanged) {
                Log.d(TAG, " remove focusChanged====" + editor.sourceFile);
                UIContext.context().removeMainStageFocused(mFocused);
                mFocused = null;
                mIsAddedFocusChanged = false;
            }
        }

        private void addFocusChanged() {
            if (mFocused == null) {
                mFocused = this::checkFileChangedTs;
            }

            if (!mIsAddedFocusChanged) {
                Log.d(TAG, "add focusChanged====" + editor.sourceFile);
                UIContext.context().addMainStageFocused(mFocused);
                mIsAddedFocusChanged = true;
            }
        }
    }
}

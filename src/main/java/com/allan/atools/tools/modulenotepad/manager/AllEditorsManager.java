package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.GlobalCfgStores;
import com.allan.atools.bean.FileEncodingMap;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.beans.FileEncodingMaps;
import com.allan.atools.beans.ReplaceParams;
import com.allan.atools.controllerwindow.NotepadFindWindow;
import com.allan.atools.keyevent.IKeyDispatcherLeaf;
import com.allan.atools.keyevent.KeyEventDispatcher;
import com.allan.atools.keyevent.ShortCutKeys;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.EditorDocumentState;
import com.allan.atools.tools.modulenotepad.session.EditorSessionManager;
import com.allan.atools.tools.modulenotepad.session.SessionTab;
import com.allan.atools.text.beans.AllFilesSearchResults;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.modulenotepad.base.INotepadMainAreaManager;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.utils.*;
import com.allan.baseparty.Action;
import com.allan.baseparty.exception.UnImplementException;
import com.allan.baseparty.memory.RefWatcher;
import com.allan.uilibs.richtexts.MyVirtualScrollPane;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Tab;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.reflect.TypeToken;

public final class AllEditorsManager implements INotepadMainAreaManager, IKeyDispatcherLeaf {
    private static final String TAG = "EditAreaManager";
    /** 最近打开文件列表的最大保存数量 */
    private static final int MAX_RECENT_FILES = 12;
    /** 最近文件列表在 recent.json 中的顶层 key */
    private static final String KEY_RECENT_FILES = "files";
    /** 文件编码映射在 recent.json 中的顶层 key */
    private static final String KEY_FILE_ENCODINGS = "fileEncodings";
    /** 固定文件列表在 recent.json 中的顶层 key */
    private static final String KEY_PINNED_FILES = "pinnedFiles";
    private static final TypeToken<List<FileEncodingMap>> TYPE_FILE_ENCODINGS = new TypeToken<>() {};

    @Override
    public boolean isCurrentAreaOnFront(EditorArea area) {
        return UIContext.currentAreaProp.get() == area;
    }

    @Override
    public void bringAreaToFront(EditorArea toTopArea) {
        var tabs = UIContext.context().tabPane.getTabs();
        for (Tab tab : tabs) {
            var area = codeAreaExInTab(tab);
            if (area == toTopArea) {
                UIContext.context().tabPane.getSelectionModel().select(tab);
                break;
            }
        }
    }

    private int tabSize;

    private EditorArea codeAreaExInTab(Tab tab) {
        return (EditorArea) ((MyVirtualScrollPane<?>)tab.getContent()).getContent();
    }

    private AllEditorsManager() {
    }

    public static final AllEditorsManager Instance = new AllEditorsManager();

    private void setCurrentArea(EditorArea newArea) {
        var lastArea = UIContext.currentAreaProp.get();
        if (lastArea == newArea) {
            Log.d("EditorBase", "set CurrentArea not change");
        } else {
            Log.d("EditorBase", "set CurrentArea changed!!!");
            if (lastArea != null) lastArea.getEditor().removeStageFocus();
            //追加multiSelection的变化
            UIContext.currentAreaProp.set(newArea);
            if (newArea != null) {
                UIContext.isMultiSelectedProp.set(newArea.getMultiSelections().isMultiSelected());
                newArea.getEditor().checkFileIfChanged();
                newArea.getEditor().addStageFocus();
            } else {
                UIContext.isMultiSelectedProp.set(false);
            }
        }
    }

    private void setCurrentTab(Tab newTab) {
        var lastTab = UIContext.currentTabProp.get();
        if (lastTab == newTab) {
            Log.d("EditorBase", "set current tab not change");
        } else {
            Log.d("EditorBase", "set current tab changed!!!");
            UIContext.currentTabProp.set(newTab);
        }
    }

    @Override
    public void init() {
        UIContext.context().tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            Log.d("selected one tab changed: ");
            setCurrentTab(newTab);
            setCurrentArea(newTab != null ? codeAreaExInTab(newTab) : null);
            UIContext.bottomIndicateProp.set("");
            EditorSessionManager.getInstance().onCaretOrStructureChanged();
        });

        UIContext.context().tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            int newSize = UIContext.context().tabPane.getTabs().size();
            Log.d("tab size changed new size: " + newSize);
            if (tabSize == 0 && newSize == 1) {
                Log.d("to front");
                UIContext.context().tabPane.setVisible(true);
            } else if (newSize == 0) {
                UIContext.context().tabPane.setVisible(false);
                setCurrentTab(null);
                setCurrentArea(null);
            }

            tabSize = newSize;
            EditorSessionManager.getInstance().onCaretOrStructureChanged();
        });
    }

    private Tab isFilePathAlreadyInTabs(File file) {
        var absolutePath = file.toPath().toAbsolutePath().normalize();
        var tabs = UIContext.context().tabPane.getTabs();
        for (var tab : tabs) {
            File fileData = tab.getUserData() instanceof EditorDocumentState state
                    ? state.getSourceFile() : null;
            if (fileData != null && absolutePath.equals(
                    fileData.toPath().toAbsolutePath().normalize())) {
                return tab;
            }
        }
        return null;
    }

    @Override
    public String getCurrentTabFilePath() {
        var tab = UIContext.currentTabProp.get();
        if (tab != null && tab.getUserData() instanceof EditorDocumentState state
                && state.getSourceFile() != null) {
            return state.getSourceFile().getAbsolutePath();
        }

        return null;
    }

    @Override
    public String[] getAllTabsFilePaths() {
        var tabs = UIContext.context().tabPane.getTabs();
        var ss = new ArrayList<String>(tabs.size());
        for (Tab tab : tabs) {
            if (tab.getUserData() instanceof EditorDocumentState state) {
                File file = state.getSourceFile();
                if (file == null) {
                    continue;
                }
                if (file.exists()) {
                    ss.add(file.getAbsolutePath());
                }
            }
        }
        /*
    public <T> T[] toArray(T[] a) {
        if (a.length < size)
            // Make a new array of a's runtime type, but my contents:
            return (T[]) Arrays.copyOf(elementData, size, a.getClass());
        System.arraycopy(elementData, 0, a, 0, size);
        if (a.length > size)
            a[size] = null;
        return a;
    }
    看到没。传入的就是返回的。
         */
        return ss.toArray(new String[0]);
    }

    @Override
    public EditorArea getAreaByFilePath(File fil) {
        if (fil == null) {
            return null;
        }
        var tabs = UIContext.context().tabPane.getTabs();
        for (var tab : tabs) {
            if (tab.getUserData() instanceof EditorDocumentState state) {
                var file = state.getSourceFile();
                if (file == null) {
                    continue;
                }
                if (fil.toPath().toAbsolutePath().normalize().equals(
                        file.toPath().toAbsolutePath().normalize())) {
                    return codeAreaExInTab(tab);
                }
            }
        }
        return null;
    }

    @Override
    public EditorArea getAreaByFilePath(String filePath) {
        var tabs = UIContext.context().tabPane.getTabs();
        for (var tab : tabs) {
            if (tab.getUserData() instanceof EditorDocumentState state) {
                var file = state.getSourceFile();
                if (file == null) {
                    continue;
                }
                if (filePath != null && Path.of(filePath).toAbsolutePath().normalize().equals(
                        file.toPath().toAbsolutePath().normalize())) {
                    return codeAreaExInTab(tab);
                }
            }
        }
        return null;
    }

    @Override
    public void removeAllOtherTabs(Tab tab) {
        var areas = getAllAreas();
        Log.d("remove all other tabs " + areas.length);
        for (var area : areas) {
            var base = area.getEditor();
            if (tab == base.getTab()) {
                Log.d("1111 is this tab");
                continue;
            }
            if (!base.canClosed()) {
                Log.d("222 is can not close");
                continue;
            }
            Log.d("333 close tab");
            base.closeTab();
        }
    }

    @Override
    public void saveListFilePaths() {
        EditorSessionManager.getInstance().onCaretOrStructureChanged();
    }

    @Override
    public Tab getTabByCodeArea(EditorArea editArea) {
        var tabs = UIContext.context().tabPane.getTabs();
        for (var tab : tabs) {
            if (codeAreaExInTab(tab) == editArea) {
                return tab;
            }
        }
        return null;
    }

    @Override
    public EditorArea[] getAllAreas() {
        var tabs = UIContext.context().tabPane.getTabs();
        EditorArea[] codes = new EditorArea[tabs.size()];
        int i = 0;
        for (var tab : tabs) {
            codes[i++] = codeAreaExInTab(tab);
        }
        return codes;
    }

    @Override
    public void openFile(File textFile, boolean checkAlreadyHasFile, boolean toFront) {
        openFile(textFile, checkAlreadyHasFile, toFront, null, null, false);
    }

    @Override
    public EditorArea newUntitledFile(File initialDirectory) {
        int index = 1;
        var usedNames = new java.util.HashSet<String>();
        for (var area : getAllAreas()) {
            usedNames.add(area.getEditor().getDocumentState().getDisplayName());
        }
        while (usedNames.contains("New" + index)) {
            index++;
        }
        var state = EditorDocumentState.untitled(
                "New" + index, initialDirectory, EncodingUtil.CHOISE_ENCODING_UTF8);
        return createEditorTab(state, "", true, true);
    }

    @Override
    public EditorArea restoreSessionEntry(SessionTab entry, String text) {
        var sourceFile = entry.sourcePath == null ? null : new File(entry.sourcePath);
        String encoding = entry.encoding == null ? EncodingUtil.CHOISE_ENCODING_UTF8 : entry.encoding;
        try {
            Charset.forName(encoding);
        } catch (RuntimeException ignored) {
            encoding = EncodingUtil.CHOISE_ENCODING_UTF8;
        }
        var state = new EditorDocumentState(entry.sessionId, entry.displayName, sourceFile,
                entry.untitled, encoding,
                entry.initialSaveDirectory == null ? null : new File(entry.initialSaveDirectory));
        state.setDirty(entry.dirty);
        if (entry.dirty) {
            state.setBaseLastModified(entry.baseLastModified);
            state.setBaseFileSize(entry.baseFileSize);
        } else {
            state.updateBaseFileMetadata();
        }
        if (!entry.untitled && entry.dirty && (sourceFile == null || !sourceFile.exists())) {
            state.setExternalState(EditorDocumentState.ExternalState.DELETED);
        } else if (!entry.untitled && entry.dirty && sourceFile != null
                && (sourceFile.lastModified() != entry.baseLastModified
                || sourceFile.length() != entry.baseFileSize)) {
            state.setExternalState(EditorDocumentState.ExternalState.MODIFIED);
        }
        var area = createEditorTab(state, text == null ? "" : text, false, false);
        if (area != null) {
            int caret = entry.caretPosition;
            if (caret < 0) {
                caret = 0;
            } else if (caret > area.getLength()) {
                caret = area.getLength();
            }
            area.moveTo(caret);
            EditorSessionManager.getInstance().registerRestoredBackup(area, entry);
        }
        return area;
    }

    private EditorArea createEditorTab(EditorDocumentState state, String text,
                                       boolean select, boolean announceError) {
        Tab newTab = new Tab();
        RefWatcher.watchs(newTab, state.getDisplayName());
        newTab.setUserData(state);
        newTab.setOnClosed(event -> onTabCloseAction(newTab));
        try {
            EditorArea editorCodeArea = new EditorArea(
                    state.getSourceFile(), newTab, text, state);
            editorCodeArea.getEditor().getState().setFileEncoding(state.getEncoding());
            editorCodeArea.getBottomSearchBtnsMgr().init();
            var vpane = new MyVirtualScrollPane<>(editorCodeArea);
            vpane.getStyleClass().add("editor-virtualized-scroll-pane");
            newTab.setContent(vpane);
            UIContext.context().tabPane.getTabs().add(newTab);
            if (select) {
                UIContext.context().tabPane.getSelectionModel().select(newTab);
            }
            changeNotHasFileText(false);
            return editorCodeArea;
        } catch (Exception e) {
            String warnMessage = Locales.str("openTabFailed");
            Log.e("openTextIn Tab open failed: " + warnMessage, e);
            if (announceError) {
                JfoenixDialogUtils.alert(Locales.ALERT(), warnMessage);
            }
            return null;
        }
    }

    private void openFile(File textFile, boolean checkAlreadyHasFile, boolean toFront, String forceEncoding, Tab reOpenExistTab, boolean ignoreAlert) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> openFile(textFile, checkAlreadyHasFile, toFront, forceEncoding, reOpenExistTab, ignoreAlert));
            return;
        }
        if (textFile == null) {
            return;
        }
        if (!ignoreAlert && textFile.length() > Util.MAX_ALERT_FILE_SIZE) {
            final var forceEncodingFinal = forceEncoding;
            JfoenixDialogUtils.confirm(Locales.str("notification"), Locales.str("itIsTooBig"), 18, 400,
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, Locales.str("sure"), ()->{
                        openFile(textFile, checkAlreadyHasFile, toFront, forceEncodingFinal, reOpenExistTab, true);
                    }),
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, Locales.str("cancle"), null));
            return;
        }

        Log.d("open file start..... " + textFile);
        Tab targetTab = null;
        if (checkAlreadyHasFile || reOpenExistTab != null) {
            Log.d("check already ");
            targetTab = reOpenExistTab != null ? reOpenExistTab : isFilePathAlreadyInTabs(textFile);
        }

        var targetTabFinal = targetTab;
        var expectedContentVersion = targetTab == null
                ? -1L
                : codeAreaExInTab(targetTab).getEditor().getContentVersion();
        ThreadUtils.executeFileIo(() -> {
            try {
                var encoding = forceEncoding != null
                        ? forceEncoding
                        : readLastFileEncoding(textFile.getAbsolutePath());
                var encodingInfo = encoding == null
                        ? EncodingUtil.ultimateEncodeDetect(textFile.getAbsolutePath())
                        : EncodingUtil.forceEncoding(encoding);
                var detectedEncoding = encodingInfo.encoding;
                var text = Files.readString(textFile.toPath(), Charset.forName(detectedEncoding));
                if (forceEncoding != null) {
                    saveLastFileEncodingMapping(textFile.getAbsolutePath(), forceEncoding);
                }
                if (!ThreadUtils.sBeClosing) {
                    Platform.runLater(() -> applyOpenedFile(textFile, checkAlreadyHasFile, toFront,
                            targetTabFinal, text, detectedEncoding, expectedContentVersion));
                }
            } catch (Exception e) {
                Log.e("open file failed: " + textFile.getAbsolutePath(), e);
                if (!ThreadUtils.sBeClosing) {
                    Platform.runLater(() -> JfoenixDialogUtils.alert(Locales.ALERT(),
                            Locales.str("openTabFailed")));
                }
            }
        });
    }

    private void applyOpenedFile(File textFile, boolean checkAlreadyHasFile, boolean toFront,
                                 Tab reOpenExistTab, String text, String detectedEncoding,
                                 long expectedContentVersion) {
        var tabs = UIContext.context().tabPane.getTabs();
        Tab targetTab = reOpenExistTab;
        if (targetTab != null && !tabs.contains(targetTab)) {
            return;
        }
        if (targetTab == null && checkAlreadyHasFile) {
            targetTab = isFilePathAlreadyInTabs(textFile);
        }
        if (targetTab != null) {
            if (!(targetTab.getContent() instanceof MyVirtualScrollPane<?> pane)
                    || !(pane.getContent() instanceof EditorArea area)) {
                return;
            }
            if (expectedContentVersion < 0L) {
                if (toFront) {
                    UIContext.context().tabPane.getSelectionModel().select(targetTab);
                }
                return;
            }
            if (area.getEditor().getContentVersion() != expectedContentVersion) {
                Log.d("ignore stale opened file: " + textFile.getAbsolutePath());
                return;
            }
            var state = area.getEditor().getDocumentState();
            state.bindSourceFile(textFile);
            targetTab.setUserData(state);
            area.getEditor().getState().setFileEncoding(detectedEncoding);
            var selection = area.getSelection();
            area.getEditor().resetText(text);
            int anchor = selection.getStart();
            int caret = selection.getEnd();
            if (anchor > area.getLength()) {
                anchor = area.getLength();
            }
            if (caret > area.getLength()) {
                caret = area.getLength();
            }
            area.selectRange(anchor, caret);
            UIContext.fileEncodeIndicateProp.set(detectedEncoding);
            if (toFront) {
                UIContext.context().tabPane.getSelectionModel().select(targetTab);
            }
            return;
        }

        Tab newTab = new Tab();
        RefWatcher.watchs(newTab, "openFile");

        newTab.setOnClosed(event -> onTabCloseAction(newTab));

        Log.e(textFile.getAbsolutePath() + " : openTextIn Tab open encode " + detectedEncoding + " " + text.length());
        Log.d("open file: " + textFile);

        try {
            EditorArea editorCodeArea = new EditorArea(textFile, newTab, text);

            editorCodeArea.getEditor().getState().setFileEncoding(detectedEncoding);
            editorCodeArea.getBottomSearchBtnsMgr().init();
            Log.d("change encoding " + detectedEncoding);
            var vpane = new MyVirtualScrollPane<>(editorCodeArea);
            vpane.getStyleClass().add("editor-virtualized-scroll-pane");
            newTab.setContent(vpane);
            UIContext.context().tabPane.getTabs().add(newTab);
            if (toFront) {
                UIContext.context().tabPane.getSelectionModel().select(newTab);
            }
            // position the caret at the beginning
            saveListFilePaths();

            delayToSaveRecentFile(textFile.getAbsolutePath());
            changeNotHasFileText(false);
        } catch (Exception e) {
            e.printStackTrace();
            String warnMessage = Locales.str("openTabFailed");
            Log.e("openTextIn Tab open failed: " + warnMessage, e);
            JfoenixDialogUtils.alert(Locales.ALERT(), warnMessage);
        }
    }

    private static final AtomicInteger mIndexesClosed = new AtomicInteger(0);

    //private ObservableBase<?,?> CaretNode_ALWAYS_TRUE;

    private void onTabCloseAction(Tab tab) {
        if (tab.getContent() instanceof MyVirtualScrollPane vpane) {
            if (vpane.getContent() instanceof EditorArea editorCodeAreaEx) {
                editorCodeAreaEx.destroy();
                vpane.removeContent();
                tab.setContent(null);
            }
        }

        Log.d("on tab closed!! " + tab);
        if (UIContext.context().tabPane.getTabs().size() <= 0) {
            changeNotHasFileText(true);
        }

        Log.w("on tab closed indexes: " + mIndexesClosed.incrementAndGet());

        /*ThreadUtils.execute(()->{
            if (CaretNode_ALWAYS_TRUE == null) {
                CaretNode_ALWAYS_TRUE = (ObservableBase<?,?>) ReflectionUtils.getStaticPrivateField(CaretNode.class, "ALWAYS_TRUE");
                Log.d("");
            }
        });*/
    }

    public void closeTabImmediately(Tab tab) {
        if (tab == null) {
            return;
        }
        tab.setOnClosed(null);
        UIContext.context().tabPane.getTabs().remove(tab);
        onTabCloseAction(tab);
    }

    private void changeNotHasFileText(boolean vis) {
        Platform.runLater(()-> {
            if (vis) {
                if (!UIContext.context().mainPane.getChildren().contains(UIContext.context().notepadMainNotHasFileText)) {
                    UIContext.context().mainPane.getChildren().add(UIContext.context().notepadMainNotHasFileText);
                }
            } else {
                UIContext.context().mainPane.getChildren().remove(UIContext.context().notepadMainNotHasFileText);
            }

            UIContext.context().requestFocus4Jfoenix();
        });
    }

    @Override
    public void multiFind(SearchParams params, Action<AllFilesSearchResults> action) {
        throw new UnImplementException("not impl in multi find");
    }

    @Override
    public StringBuilder multiReplace(ReplaceParams params) {
        throw new UnImplementException("not impl in multi replace");
    }


    @Override
    public int level() {
        return KeyEventDispatcher.LEVEL_1_CHILD;
    }

    private long mLastClickFind = 0L;
    private static final long DOUBLE_CLICK_DELTA_TIME = 250L;

    @Override
    public boolean accept(ShortCutKeys.CombineKey parsedEvent) {
        var curArea = UIContext.currentAreaProp.get();
        switch (parsedEvent) {
            case FindS -> {
                if (curArea != null) {
                    NotepadFindWindow.getInstance().show(curArea.getSelectedText());
                }

                Log.d(TAG, "accept find window with select text");
                return true;
            }
            case Find -> {
                if (curArea != null) {
                    UIContext.context().bottomSearchTextField.setText(curArea.getSelectedText());
                    UIContext.context().bottomSearchTextField.requestFocus();
                }
                long cur = System.currentTimeMillis();
                if (cur - mLastClickFind < DOUBLE_CLICK_DELTA_TIME) {
                    accept(ShortCutKeys.CombineKey.FindS);
                } else {
                    mLastClickFind = cur;
                    if (curArea != null) {
                        UIContext.context().bottomSearchTextField.setText(curArea.getSelectedText());
                        UIContext.context().bottomSearchTextField.requestFocus();
                    }
                }
                Log.d(TAG, "accept set bottom search text");
                return true;
            }
            case Save -> {
                if (curArea != null) {
                    curArea.getEditor().saveContent(null, false);
                    Log.d(TAG, "save!");
                }
                return true;
            }
        }
        //Log.d(TAG, "not accept");
        return false;
    }

    @Override
    public void reOpenCurrentFile(Tab tab, File file, String forceEncoding) {
        openFile(file, true, true, forceEncoding, tab, false);
    }

    /**
     * @param file 传入的参数为null，则是读取
     */
    public static List<String> saveOrReadRecentFiles(String file) {
        var ss = new ArrayList<>(GlobalCfgStores.recent().getStringList(KEY_RECENT_FILES, List.of()));

        if (file != null) {
            ss.add(0, file); //追加新的到最前面
            var newss = ss.stream().distinct().filter(s -> new File(s).exists()).toList();
            int savedCount = Math.min(MAX_RECENT_FILES, newss.size());
            GlobalCfgStores.recent().setStringList(KEY_RECENT_FILES, newss.subList(0, savedCount));
            //保存的时候，最上面的文件就是最新的
            return null;
        }
        else
        {
            return ss.stream().distinct().filter(s -> new File(s).exists()).toList();
        }
    }

    public static void delayToSaveRecentFile(String sourceFilePath) {
        ThreadUtils.globalHandler().postDelayed(()->{
            saveOrReadRecentFiles(sourceFilePath);
        }, 200);
    }

    /** 读取固定文件列表，去重并过滤已不存在的文件 */
    public static List<String> readPinnedRecentFiles() {
        return GlobalCfgStores.recent().getStringList(KEY_PINNED_FILES, List.of())
                .stream().distinct().filter(s -> new File(s).exists()).toList();
    }

    /** 查询路径是否已固定 */
    public static boolean isPinnedRecentFile(String file) {
        return readPinnedRecentFiles().contains(file);
    }

    /** 切换固定/取消固定，返回固定后状态 */
    public static boolean togglePinnedRecentFile(String file) {
        var pinned = new ArrayList<>(readPinnedRecentFiles());
        boolean add;
        if (pinned.contains(file)) {
            pinned.remove(file);
            add = false;
        } else {
            pinned.add(0, file);
            add = true;
        }
        GlobalCfgStores.recent().setStringList(KEY_PINNED_FILES, pinned);
        return add;
    }

    private static String readLastFileEncoding(String file) {
        var maps = GlobalCfgStores.recent().getObject(KEY_FILE_ENCODINGS, TYPE_FILE_ENCODINGS, List.of());
        for (var m : maps) {
            if (file.equals(m.file())) {
                return m.enc();
            }
        }
        return null;
    }

    private static void saveLastFileEncodingMapping(String file, String encoding) {
        FileEncodingMaps maps = new FileEncodingMaps();
        maps.list = new ArrayList<>(GlobalCfgStores.recent()
                .getObject(KEY_FILE_ENCODINGS, TYPE_FILE_ENCODINGS, List.of()));

        var m = new FileEncodingMap(file, encoding);
        maps.list.add(0, m);

        //通过去重和去除不存在的；暂时不需要考虑过期时间
        maps.removeNotExist();
        maps.removeDuplicate();

        GlobalCfgStores.recent().set(KEY_FILE_ENCODINGS, maps.list);
    }
}

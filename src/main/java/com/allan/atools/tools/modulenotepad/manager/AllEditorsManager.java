package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.SettingPreferences;
import com.allan.atools.UIContext;
import com.allan.atools.bean.FileEncodingMap;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.beans.FileEncodingMaps;
import com.allan.atools.beans.ReplaceParams;
import com.allan.atools.controllerwindow.NotepadFindWindow;
import com.allan.atools.keyevent.IKeyDispatcherLeaf;
import com.allan.atools.keyevent.KeyEventDispatcher;
import com.allan.atools.keyevent.ShortCutKeys;
import com.allan.atools.richtext.codearea.EditorAreaAddModifyAction;
import com.allan.atools.richtext.codearea.EditorArea;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class AllEditorsManager implements INotepadMainAreaManager, IKeyDispatcherLeaf {
    private static final String TAG = "EditAreaManager";

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

    public static final INotepadMainAreaManager Instance = new AllEditorsManager();

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
        });

        UIContext.context().tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            int newSize = UIContext.context().tabPane.getTabs().size();
            Log.d("tab size changed new size: " + newSize);
            if (newSize < tabSize) {
                ManualGC.directlyGC();
            }
            if (tabSize == 0 && newSize == 1) {
                Log.d("to front");
                UIContext.context().tabPane.setVisible(true);
            } else if (newSize == 0) {
                UIContext.context().tabPane.setVisible(false);
                setCurrentTab(null);
                setCurrentArea(null);
            }

            tabSize = newSize;
        });
    }

    private Tab isFilePathAlreadyInTabs(String absolutePath, int[] outIndex) {
        var tabs = UIContext.context().tabPane.getTabs();
        int i = 0;
        for (var tab : tabs) {
            File fileData = tab.getUserData() != null ? (File) tab.getUserData() : null;
            if (fileData != null && absolutePath.equals(fileData.getAbsolutePath())) {
                outIndex[0] = i;
                return tab;
            }
            i++;
        }
        return null;
    }

    private Tab isFilePathAlreadyInTabs(File file, int[] outIndex) {
        var absolutePath = file.getAbsolutePath();
        return isFilePathAlreadyInTabs(absolutePath, outIndex);
    }

    @Override
    public String getCurrentTabFilePath() {
        var tab = UIContext.currentTabProp.get();
        if (tab != null) {
            if (tab.getUserData() != null) {
                return ((File)tab.getUserData()).getAbsolutePath();
            }
        }

        return null;
    }

    @Override
    public String[] getAllTabsFilePaths() {
        var tabs = UIContext.context().tabPane.getTabs();
        var ss = new ArrayList<String>(tabs.size());
        for (Tab tab : tabs) {
            if (tab.getUserData() != null) {
                File file = (File)tab.getUserData();
                if (file.exists()) {
                    ss.add(file.getAbsolutePath());
                }
            } else {
                throw new RuntimeException("竟然没有文件地址！");
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
        return ss.toArray(new String[tabs.size()]);
    }

    @Override
    public EditorArea getAreaByFilePath(File fil) {
        var tabs = UIContext.context().tabPane.getTabs();
        for (var tab : tabs) {
            if (tab.getUserData() == fil) {
                return codeAreaExInTab(tab);
            }
            if (tab.getUserData() instanceof File file) {
                if (fil.getAbsolutePath().equals(file.getAbsolutePath())) {
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
            if (tab.getUserData() instanceof File file) {
                if (filePath != null && filePath.equals(file.getAbsolutePath())) {
                    return codeAreaExInTab(tab);
                }
            }
        }
        return null;
    }

    @Override
    public void saveUnSaved() {
        var areas = getAllAreas();
        for (var area : areas) {
            area.getEditor().saveContent(null, false);
        }
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
    public boolean hasAnyUnSaved() {
        var areas = getAllAreas();
        for (var area : areas) {
            var base = area.getEditor();
            if (!base.canClosed()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void saveListFilePaths() {
        if (SettingPreferences.getBoolean(SettingPreferences.saveLastOpenedFileKey)) {
            StringBuilder sb = new StringBuilder();
            for (var filePath : getAllTabsFilePaths()) {
                sb.append(filePath).append(";");
            }
            ThreadUtils.globalHandler().post(()-> UIContext.sharedPref.edit().putString("lastFile", sb.toString()).commit());
        }
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
    public void newFakeFile(File fakeFile) {
        Tab newTab = new Tab();
        if (RefWatcher.getInstance() != null) {
            RefWatcher.getInstance().watch(newTab, "fakeFile " + fakeFile.getPath());
        }

        newTab.setUserData(fakeFile);
        newTab.setOnClosed(event -> onTabCloseAction(newTab));

        final String str = "";
        Log.e(" : open fake file Tab open encode ");
        //textTab.setGraphic(ImageUtils.buildImageView(FILE_ICON));
        try {
            EditorArea editorCodeArea = new EditorArea(fakeFile, newTab, true, str, new EditorAreaAddModifyAction(/*eb*/));
            editorCodeArea.getEditor().setFileEncoding(EncodingUtil.CHOISE_ENCODING_UTF8);
            editorCodeArea.getBottomSearchBtnsMgr().init();
            var vpane = new MyVirtualScrollPane<>(editorCodeArea);
            newTab.setContent(vpane);
            UIContext.context().tabPane.getTabs().add(newTab);
            UIContext.context().tabPane.getSelectionModel().select(newTab);
            // position the caret at the beginning
            changeNotHasFileText(false);
        } catch (Exception e) {
            e.printStackTrace();
            String warnMessage = "openTextIn Tab Can't Open File in Tab pane";
            //Debugging warning
            Log.e("openTextIn Tab open failed: " + warnMessage, e);
            //UI warning
            JfoenixDialogUtils.alert(Locales.ALERT(), warnMessage);
        }
    }

    private synchronized Tab openFile(File textFile, boolean checkAlreadyHasFile, boolean toFront, String forceEncoding, Tab reOpenExistTab, boolean ignoreAlert) {
        if (textFile != null && textFile.length() > Util.MAX_ALERT_FILE_SIZE) {
            final var forceEncodingFinal = forceEncoding;
            JfoenixDialogUtils.confirm(Locales.str("notification"), Locales.str("itIsTooBig"), 18, 400,
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, Locales.str("sure"), ()->{
                        openFile(textFile, checkAlreadyHasFile, toFront, forceEncodingFinal, reOpenExistTab, true);
                    }),
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, Locales.str("cancle"), null));
            return null;
        }

        Log.d("open file start..... " + textFile);
        String origForceEncoding = forceEncoding;
        var backEncode = new String[1];
        if (forceEncoding == null) {
            forceEncoding = readLastFileEncoding(textFile.getAbsolutePath());
        }

        if (checkAlreadyHasFile || reOpenExistTab != null) {
            Log.d("check already ");
            int[] index = {0};
            var alreadyTab = reOpenExistTab != null ? reOpenExistTab : isFilePathAlreadyInTabs(textFile, index);
            if (alreadyTab != null) {//已经存在就提示直接重新load即可
                alreadyTab.setUserData(textFile); //第一时间更新
                StringBuilder sb = FileUtils.readString(textFile.getAbsolutePath(), forceEncoding, backEncode);
                var are = codeAreaExInTab(alreadyTab);
                are.getEditor().setFileEncoding(backEncode[0]);
                are.getEditor().resetText(sb.toString());
                //只有这种情况需要更新当前的编码文字
                UIContext.fileEncodeIndicateProp.set(forceEncoding);
                if (toFront) {
                    setCurrentTab(alreadyTab);
                    setCurrentArea(are);
                    UIContext.context().tabPane.getSelectionModel().select(alreadyTab);
                }
                return alreadyTab;
            }
        }

        Tab newTab = new Tab();
        if (RefWatcher.getInstance() != null) {
            RefWatcher.getInstance().watch(newTab, "openFile");
        }

        newTab.setUserData(textFile);
        newTab.setOnClosed(event -> onTabCloseAction(newTab));

        final String str;
        StringBuilder sb = FileUtils.readString(textFile.getAbsolutePath(), forceEncoding, backEncode);
        Log.e(textFile.getAbsolutePath() + " : openTextIn Tab open encode " + backEncode[0] + " " + sb.length());
        str = sb.toString();

        Log.d("open file: " + textFile);

        //textTab.setGraphic(ImageUtils.buildImageView(FILE_ICON));
        try {
            EditorArea editorCodeArea = new EditorArea(textFile, newTab, false, str, new EditorAreaAddModifyAction(/*eb*/));

            editorCodeArea.getEditor().setFileEncoding(backEncode[0]);
            editorCodeArea.getBottomSearchBtnsMgr().init();
            Log.d("change encoding " + backEncode[0]);
            var vpane = new MyVirtualScrollPane<>(editorCodeArea);
            newTab.setContent(vpane);
            UIContext.context().tabPane.getTabs().add(newTab);
            if (toFront) {
                UIContext.context().tabPane.getSelectionModel().select(newTab);
            }
            // position the caret at the beginning
            saveListFilePaths();

            delayToSaveRecentFile(textFile.getAbsolutePath());

            if (origForceEncoding != null) {
                saveLastFileEncodingMapping(textFile.getAbsolutePath(), origForceEncoding);
            }

            changeNotHasFileText(false);
        } catch (Exception e) {
            e.printStackTrace();
            String warnMessage = "openTextIn Tab Can't Open File in Tab pane";
            //Debugging warning
            Log.e("openTextIn Tab open failed: " + warnMessage, e);
            //UI warning
            JfoenixDialogUtils.alert(Locales.ALERT(), warnMessage);
        }

        return newTab;
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
            ManualGC.decupleGC();
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
        List<String> ss;
        var path = Path.of(CacheLocation.getRecentFiles());
        try {
            ss = Files.readAllLines(path);
        } catch (IOException e) {
            ss = new ArrayList<>();
        }

        if (file != null) {
            ss.add(0, file); //追加新的到最前面
            var newss = ss.stream().distinct().filter(s -> new File(s).exists()).toList();
            try {
                StringBuilder saved = new StringBuilder();
                for (int i = 0; i < 10 && i < newss.size(); i++) { //*******暂时设置为10*****
                    saved.append(newss.get(i)).append('\n');
                }
                Files.writeString(path, saved.substring(0, saved.length() - 1));
                //保存的时候，最上面的文件就是最新的
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
        else
        {
            var r = ss.stream().distinct().filter(s -> new File(s).exists()).toList();
            return r;
        }
    }

    public static void delayToSaveRecentFile(File sourceFile) {
        ThreadUtils.globalHandler().postDelayed(()->{
            saveOrReadRecentFiles(sourceFile.getAbsolutePath());
        }, 200);
    }

    public static void delayToSaveRecentFile(String sourceFilePath) {
        ThreadUtils.globalHandler().postDelayed(()->{
            saveOrReadRecentFiles(sourceFilePath);
        }, 200);
    }

    static String readLastFileEncoding(String file) {
        List<String> ss;
        var path = Path.of(CacheLocation.getMapFileAndEncoding());
        try {
            ss = Files.readAllLines(path);
        } catch (IOException e) {
            //
            return null;
        }
        for (int i = 0, count = ss.size(); i < count; i += 2) {
            var f = ss.get(i);
            var e = ss.get(i + 1);
            if (file.equals(f)) {
                return e;
            }
        }

        return null;
    }

    public static void saveLastFileEncodingMapping(String file, String encoding) {
        List<String> ss;
        var path = Path.of(CacheLocation.getMapFileAndEncoding());
        try {
            ss = Files.readAllLines(path);
        } catch (IOException e) {
            //
            ss = new ArrayList<>();
        }
        FileEncodingMaps maps = new FileEncodingMaps();
        maps.list = new ArrayList<>(4);

        for (int i = 0, count = ss.size(); i < count; i += 2) {
            var f = ss.get(i);
            var e = ss.get(i + 1);
            maps.list.add(new FileEncodingMap(f, e));
        }

        var m = new FileEncodingMap(file, encoding);
        maps.list.add(0, m);

        //通过去重和去除不存在的；暂时不需要考虑过期时间
        maps.removeNotExist();
        maps.removeDuplicate();

        try {
            StringBuilder saved = new StringBuilder();
            maps.list.forEach(map -> {
                saved.append(map.file()).append('\n').append(map.enc()).append('\n');
            });
            var s = saved.substring(0, saved.length() - 1);
            Files.writeString(path, s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

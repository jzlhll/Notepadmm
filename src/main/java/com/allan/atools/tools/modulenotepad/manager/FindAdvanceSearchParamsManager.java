package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.controller.NotepadFindController;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.utils.CacheLocation;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.uilibs.controls.MyHBox;
import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.bean.SearchParams;
import com.allan.baseparty.Action0;
import com.allan.baseparty.ActionR;
import com.google.gson.Gson;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class FindAdvanceSearchParamsManager {
    private final JFXListView<HBox> advanceSearchsListView;
    private final JFXComboBox<Label> advanceSearchesCfgsCobox;
    private final ActionR<SearchParams, HBox> createItemFunc;

    private String currentAdvanceCfgName;
    private int currentAdvanceCfgIndex;

    public FindAdvanceSearchParamsManager(NotepadFindController controller, ActionR<SearchParams, HBox> createItemFunc) {
        advanceSearchsListView = controller.advanceSearchsListView;
        advanceSearchesCfgsCobox = controller.advanceSearchesCfgsCobox;
        this.createItemFunc = createItemFunc;
    }

    public static final int SAVE_DELAY_TS = UIContext.DEBUG ? 2 * 1000 : 5 * 1000;

    private final LinkedHashMap<String, List<SearchParams>> nameAndListParamsMap = new LinkedHashMap<>();

    private final Object LOCK = new Object();

    public int getAdvCfgsSize() {
        synchronized (LOCK) {
            return nameAndListParamsMap.size();
        }
    }

    public SearchParams[] getCurrentParamsUseful() {
        synchronized (LOCK) {
            var searchList = nameAndListParamsMap.get(currentAdvanceCfgName);
            return searchList.stream()
                    .filter(item -> item.words.length() > 0 && item.enable)
                    .toArray(SearchParams[]::new);
        }
    }

    private static final String DEFAULT_CFG_NAME = Locales.str("defaultStr");
    public boolean isDefault() {
        return currentAdvanceCfgIndex == 0;
    }

    private final Gson gson = new Gson();
    private final Runnable mSaveSearchParams = () -> {
        Log.d("save the new find combo box data!");
        StringBuilder sb = new StringBuilder();
        synchronized (LOCK) {
            try {
                var keySet = nameAndListParamsMap.keySet();
                for (var key : keySet) {
                    var v = nameAndListParamsMap.get(key);
                    sb.append("#name#").append(key).append("\n");
                    for (var p : v) {
                        sb.append(gson.toJson(p)).append("\n");
                    }
                }

                if (sb.length() > 0) {
                    Files.writeString(Path.of(CacheLocation.getAdvanceSearchesFile()), sb.substring(0, sb.length() - 1));
                } else {
                    Files.delete(Path.of(CacheLocation.getAdvanceSearchesFile()));
                }
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }
    };

    public void copyItem(SearchParams copiedParam, SearchParams beCopied) {
        synchronized (LOCK) {
            var searchParamList = nameAndListParamsMap.get(currentAdvanceCfgName);
            int i = 0, co = searchParamList.size();
            for (; i < co; i++) {
                if (searchParamList.get(i) == beCopied) {
                    break;
                }
            }

            i++; //往后插入

            if (i == co) {
                searchParamList.add(copiedParam);
                advanceSearchsListView.getItems().add(createItemFunc.invoke(copiedParam));
            } else {
                searchParamList.add(i, copiedParam);
                advanceSearchsListView.getItems().add(i, createItemFunc.invoke(copiedParam));
            }
        }

        saveParam();
    }

//    public void upItem(MyHBox<SearchParams> hbox) {
//        changeNearItem(true, hbox);
//    }
//
//    public void downItem(MyHBox<SearchParams> hbox) {
//        changeNearItem(false, hbox);
//    }
//
//    private void changeNearItem(boolean goUp, MyHBox<SearchParams> hbox) {
//        var param = hbox.getEx();
//        synchronized (LOCK) {
//            var searchParamList = nameAndListParamsMap.get(currentAdvanceCfgName);
//            int i = 0, co = searchParamList.size();
//            for (; i < co; i++) {
//                if (searchParamList.get(i) == param) {
//                    break;
//                }
//            }
//
//            int replaceIndex;
//            if (goUp) {
//                replaceIndex = i == 0 ? 0 : i - 1;
//            } else {
//                replaceIndex = i == co - 1 ? i : i + 1;
//            }
//
//            if (i == replaceIndex) {
//                Log.d("到底或者当顶了");
//                return;
//            }
//
//            var replaceItem = searchParamList.get(replaceIndex);
//            var replaceHBox = advanceSearchsListView.getItems().get(replaceIndex);
//            searchParamList.set(i, replaceItem);
//            searchParamList.set(replaceIndex, param);
//            advanceSearchsListView.getItems().set(i, replaceHBox);
//            advanceSearchsListView.getItems().set(replaceIndex, hbox);
//        }
//
//        saveParam();
//    }

    public void deleteItem(MyHBox<SearchParams> hbox) {
        boolean isSave = false;
        synchronized (LOCK) {
            var searchParamList = nameAndListParamsMap.get(currentAdvanceCfgName);
            if (searchParamList.size() == 1 && isDefault()) {
                JfoenixDialogUtils.alert(Locales.str("notification"), Locales.str("theLastOneCannotDelete"));
            } else if (searchParamList.size() == 1) {
                nameAndListParamsMap.remove(currentAdvanceCfgName);
                currentAdvanceCfgName = nameAndListParamsMap.keySet().iterator().next();
                currentAdvanceCfgIndex = 0;
                reConfigComboBoxAndDataUnlock();

                final String cur = currentAdvanceCfgName;
                ThreadUtils.globalHandler().post(()-> UIContext.sharedPref.edit().putString("advance_cfgs_name", cur).commit());
                isSave = true;
            } else {
                searchParamList.remove(hbox.getEx());
                advanceSearchsListView.getItems().remove(hbox);
                isSave = true;
            }
        }

        if (isSave) {
            saveParam();
        }
    }

    /**
     * @param after 异步 加锁。加载数据成功后，并帮你执行after。after里面自行加锁。
     */
    private void loadDataAsyncLocked(final Action0 after) {
        ThreadUtils.globalHandler().post(()->{
            //加载数据
            synchronized (LOCK) {
                try {
                    var fileStr = Files.readString(Path.of(CacheLocation.getAdvanceSearchesFile()));
                    var lines = fileStr.split("\n");
                    Gson gson = new Gson();

                    for (int i = 0, count = lines.length; i < count; i++) {
                        var line = lines[i];
                        if (TextUtils.isEmpty(line)) {
                            continue;
                        }
                        if (line.startsWith("#name#")) {
                            String key = line.substring(6);
                            if (i == 0) {
                                key = DEFAULT_CFG_NAME;
                            }
                            List<SearchParams> params = new ArrayList<>();
                            i++;
                            for (; i < count; i++) {
                                line = lines[i];
                                if (line.startsWith("#name#")) {
                                    i--; //退回来；让外层循环加到name行去
                                    break;
                                }
                                var pa = gson.fromJson(line, SearchParams.class);
                                params.add(pa);
                            }
                            nameAndListParamsMap.put(key, params);
                        }
                    }
                } catch (Exception e) {
                    Log.d("不做处理 " + e.getMessage());
                    //e.printStackTrace();
                    nameAndListParamsMap.clear();
                }

                if (nameAndListParamsMap.size() == 0) {
                    var list = new ArrayList<SearchParams>(4);
                    list.add(SearchParams.generate(""));
                    nameAndListParamsMap.put(DEFAULT_CFG_NAME, list);
                }

                currentAdvanceCfgName = UIContext.sharedPref.getString("advance_cfgs_name", DEFAULT_CFG_NAME);
                currentAdvanceCfgIndex = getIndexInMapUnlock();

                Platform.runLater(after::invoke);
            }
        });
    }

    private int getIndexInMapUnlock() {
        int i = 0;
        for (var key : nameAndListParamsMap.keySet()) {
            if (TextUtils.equals(key, currentAdvanceCfgName)) {
                break;
            }
            i++;
        }
        return i;
    }

    private final ChangeListener<Label> selectedChanged = (observable, oldValue, newValue) -> {
        synchronized (LOCK) {
            currentAdvanceCfgName = newValue.getText();
            currentAdvanceCfgIndex = getIndexInMapUnlock();
            reConfigComboBoxAndDataUnlock();
            final String cur = currentAdvanceCfgName;
            ThreadUtils.globalHandler().post(()-> UIContext.sharedPref.edit().putString("advance_cfgs_name", cur).commit());
        }
    };

    private boolean isSelected = false;

    public void removeCurrentNameCfg() {
        synchronized (LOCK) {
            nameAndListParamsMap.remove(currentAdvanceCfgName);
            currentAdvanceCfgName = nameAndListParamsMap.keySet().iterator().next();
            currentAdvanceCfgIndex = getIndexInMapUnlock();
            reConfigComboBoxAndDataUnlock();
            final String cur = currentAdvanceCfgName;
            ThreadUtils.globalHandler().post(()-> UIContext.sharedPref.edit().putString("advance_cfgs_name", cur).commit());
        }

        saveParam();
    }

    public void copyCurrentNameCfg(String newName) {
        synchronized (LOCK) {
            var list = nameAndListParamsMap.get(currentAdvanceCfgName);
            var reList = new ArrayList<SearchParams>(list.size());
            list.forEach(searchParams -> reList.add(searchParams.copy()));

            nameAndListParamsMap.put(newName, reList);
            currentAdvanceCfgName = newName;
            currentAdvanceCfgIndex = getIndexInMapUnlock();

            reConfigComboBoxAndDataUnlock();

            final String cur = currentAdvanceCfgName;
            ThreadUtils.globalHandler().post(()-> UIContext.sharedPref.edit().putString("advance_cfgs_name", cur).commit());
        }

        saveParam();
    }

    private void reConfigComboBoxAndDataUnlock() {
        int i = 0;
        int k = 0;
        var cur = currentAdvanceCfgName;
        var listParam = nameAndListParamsMap.get(cur);

        if (isSelected) {
            advanceSearchesCfgsCobox.getSelectionModel().selectedItemProperty().removeListener(selectedChanged);
        }

        advanceSearchesCfgsCobox.getItems().clear();
        for (var key : nameAndListParamsMap.keySet()) {
            advanceSearchesCfgsCobox.getItems().add(new Label(key));
            i++;
            if (key.equals(cur)) {
                k = i - 1;
            }
        }

        advanceSearchesCfgsCobox.getSelectionModel().select(k);
        advanceSearchesCfgsCobox.getSelectionModel().selectedItemProperty().addListener(selectedChanged);

        isSelected = true;
        advanceSearchsListView.getItems().clear();
        for (var item : listParam) {
            advanceSearchsListView.getItems().add(createItemFunc.invoke(item));
        }
    }

    public void initAdvanceCfgs() {
        //初始化数据
        //加载到cfg ComboBox中
        loadDataAsyncLocked(() -> {
            synchronized (LOCK) {
                reConfigComboBoxAndDataUnlock();
            }
        });
    }

    public void saveParam() {
        ThreadUtils.globalHandler().removeCallback(mSaveSearchParams);
        ThreadUtils.globalHandler().postDelayed(mSaveSearchParams, SAVE_DELAY_TS);
    }
}

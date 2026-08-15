package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.GlobalCfgStores;
import com.allan.atools.controller.NotepadFindController;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.uilibs.controls.MyHBox;
import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.bean.SearchParams;
import com.allan.baseparty.Action0;
import com.allan.baseparty.ActionR;
import com.google.gson.reflect.TypeToken;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

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

    private final LinkedHashMap<String, List<SearchParams>> nameAndListParamsMap = new LinkedHashMap<>();

    private final Object LOCK = new Object();

    /** 当前配置名在 advance.json 中的私有顶层 key */
    private static final String KEY_CURRENT_NAME = "_currentName";
    private static final TypeToken<List<SearchParams>> TYPE_LIST_PARAMS = new TypeToken<>() {};

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

    private final Runnable mSaveSearchParams = () -> {
        Log.d("save the new find combo box data!");
        synchronized (LOCK) {
            var advance = GlobalCfgStores.advance();
            for (var key : advance.keys()) {
                //_currentName 是私有 key，不在配置 map 中，删除清理时须跳过
                if (!KEY_CURRENT_NAME.equals(key) && !nameAndListParamsMap.containsKey(key)) {
                    advance.remove(key); //已被删除的配置名
                }
            }
            for (var entry : nameAndListParamsMap.entrySet()) {
                advance.set(entry.getKey(), entry.getValue());
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
                GlobalCfgStores.advance().setString(KEY_CURRENT_NAME, cur);
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
            //加载数据（GsonCfgStore 首次访问会同步读盘，保持在后台线程）
            synchronized (LOCK) {
                var advance = GlobalCfgStores.advance();
                for (var key : advance.keys()) {
                    if (KEY_CURRENT_NAME.equals(key)) {
                        continue;
                    }
                    nameAndListParamsMap.put(key, new ArrayList<>(advance.getObject(key, TYPE_LIST_PARAMS, List.of())));
                }

                if (nameAndListParamsMap.size() == 0) {
                    var list = new ArrayList<SearchParams>(4);
                    list.add(SearchParams.generate(""));
                    nameAndListParamsMap.put(DEFAULT_CFG_NAME, list);
                }

                currentAdvanceCfgName = advance.getString(KEY_CURRENT_NAME, DEFAULT_CFG_NAME);
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
            GlobalCfgStores.advance().setString(KEY_CURRENT_NAME, currentAdvanceCfgName);
        }
    };

    private boolean isSelected = false;

    public void removeCurrentNameCfg() {
        synchronized (LOCK) {
            nameAndListParamsMap.remove(currentAdvanceCfgName);
            currentAdvanceCfgName = nameAndListParamsMap.keySet().iterator().next();
            currentAdvanceCfgIndex = getIndexInMapUnlock();
            reConfigComboBoxAndDataUnlock();
            GlobalCfgStores.advance().setString(KEY_CURRENT_NAME, currentAdvanceCfgName);
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

            GlobalCfgStores.advance().setString(KEY_CURRENT_NAME, currentAdvanceCfgName);
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
        mSaveSearchParams.run();
    }
}

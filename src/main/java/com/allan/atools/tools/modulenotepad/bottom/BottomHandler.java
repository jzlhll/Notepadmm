package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.GlobalCfgStores;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.EditorAreaMgrCode;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.utils.Log;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.text.FinderFactory;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.baseparty.handler.*;
import com.google.gson.Gson;
import javafx.application.Platform;
import org.fxmisc.richtext.model.StyleSpans;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;

/**
 * BottomHandler是BottomSearchBtnsMgr的对象，即每一个Editor有一个Handler。
 * 但是所有的BottomHandler都共用一个Thread Looper。
 */
final class BottomHandler extends Handler {
    static final String SEARCH_PARAMS_KEY = "searchParams";

    private final BottomSearchBtnsMgr out;
    private final Styler styler;
    private volatile boolean destroyed;

    final Cache cache = new Cache();

    private static HandlerThread mThread;

    private static Looper getHandlerThreadLooper() {
        if (mThread == null) {
            mThread = new HandlerThread("bottom_handle_thread");
            mThread.start();
        }
        return mThread.getLooper();
    }

    public BottomHandler(BottomSearchBtnsMgr out) {
        super(getHandlerThreadLooper());
        this.out = out;
        styler = new Styler(out);
    }

    public void destroy() {
        destroyed = true;
        styler.destroy();
        removeAllCallbacksAndMessages();
    }

    private static final long DELAY_SAVE_PARAM_TS = 10 * 1000L;
    private static final long DELAY_TRIGGER_SEARCH_TS = 600L;
    private static final long DELAY_TRIGGER_SEARCH_TEMPORARY_TS = 300L;

    private static final int MSG_SAVE_PARAM = 1;
    private static final int MSG_TRIGGER_SEARCH = 2;
    private static final int MSG_TRIGGER_SEARCH_TEXT_CHANGE = 3;

    public enum ClickType {
        Search,
        Temp,
        None
    }

    public static ClickType toClickType(int i) {
        if (i == 1) {
            return ClickType.Temp;
        }
        return ClickType.Search;
    }

    public static int from(ClickType type) {
        if (type == ClickType.Temp) {
            return 1;
        }
        return 0;
    }

    public static ShowType toShowType(int i) {
        if (i == 1) {
            return ShowType.Temp;
        }
        if (i == 2) {
            return ShowType.BothSearchFrontTempBehind;
        }
        return ShowType.Search;
    }

    public static int from(ShowType type) {
        if (type == ShowType.Temp) {
            return 1;
        }
        if (type == ShowType.BothSearchFrontTempBehind) {
            return 2;
        }
        return 0;
    }

    @Override
    public void handleMessage(Message msg) {
        try {
            switch (msg.what) {
                case MSG_SAVE_PARAM -> saveParams();
                case MSG_TRIGGER_SEARCH, MSG_TRIGGER_SEARCH_TEXT_CHANGE ->
                        prepareSearch(toClickType(msg.arg1), (Long) msg.obj);
            }
        } catch (Exception e) {
            Log.e("BottomHandler Error", e);
        }
    }

    private void saveParams() {
        try {
            SearchParams template;
            template = out.mSearchParamAndIndicatorParam.searchParams.copy();
            template.words = "";
            String s = new Gson().toJson(template);

            savedTemplateParams = template;
            GlobalCfgStores.user().setString(SEARCH_PARAMS_KEY, Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            //e.printStackTrace();
        }
    }

    private static SearchParams savedTemplateParams;

    static SearchParams getTemplateParams() {
        if (savedTemplateParams == null) {
            var s = GlobalCfgStores.user().getString(SEARCH_PARAMS_KEY, null);
            try {
                var bs = Base64.getDecoder().decode(s);
                String json = new String(bs, StandardCharsets.UTF_8);
                savedTemplateParams = new Gson().fromJson(json, SearchParams.class);
                savedTemplateParams.words = ""; //去除上次搜索
            } catch (Exception e) {
                //e.printStackTrace();
                savedTemplateParams = new SearchParams();
            }
        }
        return savedTemplateParams;
    }

    void triggerSelectionTemporarySearch(final long flag) {
        //Log.d("trigger temporary searchxxxxxxxxxxx");
        if (Styler.DEBUG_STYLER) {
            Log.d("Styler: trigger when triggerSelection TemporarySearch flag " + flag);
        }
        removeMessages(MSG_TRIGGER_SEARCH);
        sendMessageDelayed(obtainMessage(MSG_TRIGGER_SEARCH, from(ClickType.Temp), 0, flag), DELAY_TRIGGER_SEARCH_TEMPORARY_TS);
    }

    void triggerSearchParamsChanged(final long flag) {
        //Log.d("trigger search!!!!");
        if (Styler.DEBUG_STYLER) {
            Log.d("Styler: trigger when SearchParams Changed flag " + flag);
        }
        removeMessages(MSG_SAVE_PARAM);
        sendMessageDelayed(obtainMessage(MSG_SAVE_PARAM), DELAY_SAVE_PARAM_TS);

        removeMessages(MSG_TRIGGER_SEARCH);
        sendMessageDelayed(obtainMessage(MSG_TRIGGER_SEARCH, from(ClickType.Search), 0, flag), DELAY_TRIGGER_SEARCH_TS);
    }

    void triggerSearchWhenTextChanged(final long flag) {
        if (Styler.DEBUG_STYLER) {
            Log.d("Styler: trigger When TextChanged flag " + flag);
        }
        removeMessages(MSG_TRIGGER_SEARCH_TEXT_CHANGE);
        long delay = out.editorArea.getEditor().isMarkdownStyler()
                ? DELAY_TRIGGER_SEARCH_TS : DELAY_TRIGGER_SEARCH_TS * 4;
        sendMessageDelayed(obtainMessage(MSG_TRIGGER_SEARCH_TEXT_CHANGE, from(ClickType.Search), 0, flag), delay);
    }

    private void prepareSearch(ClickType clickType, long flag) {
        var area = out.editorArea;
        if (!area.getEditor().isMarkdownStyler()) {
            searchInThread(clickType, flag, null, -1, null);
            return;
        }
        Platform.runLater(() -> {
            if (destroyed || area.getEditor().isDestroyed()
                    || flag != out.lastChangeSearchFlag.get()) {
                return;
            }
            if (area.getEditor() instanceof EditorAreaMgrCode codeEditor) {
                codeEditor.invalidateMarkdownStyleRequest();
            }
            long contentVersion = area.getEditor().getContentVersion();
            String text = area.getText();
            var currentSpans = area.getStyleSpans(0, text.length());
            post(() -> searchInThread(clickType, flag, text, contentVersion, currentSpans));
        });
    }

    private void searchInThread(ClickType clickType, final long flag, String textSnapshot,
                                long contentVersion, StyleSpans<Collection<String>> currentSpans) {
        var area = out.editorArea;// UIContext.currentAreaProp.get();
        if (area == null) {
            return;
        }
        boolean isMarkdown = area.getEditor().isMarkdownStyler();
        if (destroyed || isMarkdown && (flag != out.lastChangeSearchFlag.get()
                || contentVersion != area.getEditor().getContentVersion())) {
            return;
        }
        if(EditorArea.DEBUG_EDITOR) Log.v("search In Thread start....");
        SearchParams curParams, curTempParams;
        String tempWord;
        curParams = out.mSearchParamAndIndicatorParam.searchParams.copy();
        tempWord = out.getTemporaryWord();
        curTempParams = curParams.copy(tempWord, false, true);

        SearchParams[] searchParams;
        boolean isEmptyCur = TextUtils.isEmpty(curParams.words);
        boolean isEmptyTemp = TextUtils.isEmpty(curTempParams.words);

        ShowType showType = null;

        if (isEmptyCur && isEmptyTemp) {
            searchParams = null;
        } else if (isEmptyCur && !isEmptyTemp) {
            searchParams = new SearchParams[]{curTempParams};
            showType = ShowType.Temp;
        } else if (!isEmptyCur && isEmptyTemp) {
            searchParams = new SearchParams[]{curParams};
            showType = ShowType.Search;
        } else { //(!isEmptyCur && !isEmptyTemp)
            if (curParams.type == SearchParams.Type.Normal && curParams.words.equals(curTempParams.words)) {
                searchParams = new SearchParams[]{curParams};
                showType = ShowType.Search;
            } else {
                searchParams = new SearchParams[]{curParams, curTempParams};
                showType = ShowType.BothSearchFrontTempBehind;
            }
        }

        var t = isMarkdown ? textSnapshot : area.getText();
        OneFileSearchResults newCacheResult;
        if (t == null || t.length() == 0) {
            newCacheResult = new OneFileSearchResults();
        } else if (searchParams == null) {
            newCacheResult = new OneFileSearchResults().addTotalLen(t.length());
        } else {
            int[] totalLines = {0};
            //TimerCounter.start("bottom_search_in_thread");
            var lastResultItems = FinderFactory.find(t, false, searchParams, totalLines);
            //Log.d(BottomSearchBtnsMgr.TAG, "findFactory.find time: " + TimerCounter.end("bottom_search_in_thread"));
            newCacheResult = new OneFileSearchResults().addResults(lastResultItems).addTotalLen(t.length());
        }
        if (isMarkdown && (flag != out.lastChangeSearchFlag.get()
                || contentVersion != area.getEditor().getContentVersion())) {
            return;
        }
        cache.cacheResult = newCacheResult;
        if(EditorArea.DEBUG_EDITOR) Log.v("search In Thread end..temporary SearchEndCallback..");

        if (area.getEditor().isEditorCodeMode()) {
            if (isMarkdown) {
                styler.stylingMarkdown(clickType, curTempParams, curParams,
                        t, contentVersion, currentSpans);
            } else {
                styler.stylingCode(clickType, curTempParams, curParams);
            }
        } else {
            styler.stylingNormal(flag, cache.cacheResult, clickType, showType);
        }
    }
}

package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.Colors;
import com.allan.atools.SettingPreferences;
import com.allan.atools.UIContext;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.bean.SearchParamsIndicator;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.modulenotepad.Highlight;
import com.allan.atools.ui.IconfontCreator;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.baseparty.handler.TextUtils;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * BottomSearchBtnsMgr是每一个EditorArea独有的一个对象。
 */
public final class BottomSearchBtnsMgr {
    static final String TAG = "BottomSearchBtnsMgr";

    final EditorArea editorArea;

    final AtomicLong lastChangeSearchFlag = new AtomicLong(0);
    private final BottomHandler handler;

    SearchParamsIndicator mSearchParamAndIndicatorParam;
    private String temporaryWord;
    public String getTemporaryWord() {return temporaryWord;}

    public BottomSearchBtnsMgr(EditorArea editorImpl) {
        editorArea = editorImpl;
        handler = new BottomHandler(this);
    }

    public void destroy() {
        handler.destroy();
    }

    public void init() {
        editorArea.getEditor().textChanged.addAction(() -> {
            if(EditorArea.DEBUG_EDITOR) Log.d("code area text changed refresh search！");
            var flag = lastChangeSearchFlag.incrementAndGet();
            if(Styler.DEBUG_STYLER) Log.d("Styler: bottom text changed 11 flag=" + flag);
            handler.triggerSearchWhenTextChanged(flag);
        });

        editorArea.getEditor().selectionChanged.addAction(selectStr-> {
            if (isEnableSelectionListener) {
                if (!TextUtils.equalsAllowNullOrEmptyEqual(selectStr, temporaryWord)) {
                    var flag = lastChangeSearchFlag.incrementAndGet();
                    if(Styler.DEBUG_STYLER) Log.v("Styler: bottom selection changed 22 flag=" + flag);
                    temporaryWord = selectStr;
                    handler.triggerSelectionTemporarySearch(flag);
                } else {
                    if(Styler.DEBUG_STYLER) Log.v("Styler: bottom selection not changed ignore");
                }
            }
        });
        temporaryWord = null;

        var pair = new SearchParamsIndicator();
        pair.indicator = "";
        pair.searchParams = BottomHandler.getTemplateParams().copy();
        mSearchParamAndIndicatorParam = pair;
    }

    private boolean isEnableSelectionListener = true;

    public void disableSelectionListenerTemporary(long disableTs) {
        isEnableSelectionListener = false;
        ThreadUtils.globalHandler().postDelayed(()->{
            isEnableSelectionListener = true;
        }, disableTs);
    }

    void bottomSearchTextChanged(String newStr) {
        if (newStr.length() > 200) {
            SnackbarUtils.show(Locales.str("maybeTooLong"));
            UIContext.context().bottomSearchTextField.setText("");
            return;
        }
        mSearchParamAndIndicatorParam.searchParams.words = newStr;
        temporaryWord = null;
        var flag = lastChangeSearchFlag.incrementAndGet();
        if(Styler.DEBUG_STYLER) Log.d("Styler: bottomSearchTextChanged 33 flag=" + flag);
        handler.triggerSearchParamsChanged(flag);
    }

    void jumpToNext(EditorArea area, boolean back, boolean forceWrap) {
        Log.d("jump to next");
        var out = new Cache.Out();
        var cycleNext = SettingPreferences.getBoolean(SettingPreferences.cycleNextKey);
        if (forceWrap) {
            cycleNext = true;
        }
        var item = handler.cache.getNextCachedLineNum(editorArea, back, cycleNext, out);
        if (item == null) {
            updateIndicator(0, 0);
            return;
        }
        Highlight.JumpMode mode = back ? Highlight.JumpMode.GoUp : Highlight.JumpMode.GoDown;
        disableSelectionListenerTemporary(400);
        if (area.getEditor().isDestroyed()) {
            Log.e("jump to next but edior is destroyed!");
        }
        Highlight.jumpToLineAndSelectWord(area, mode, out.lineNum, item.range.start, item.range.end);

        updateIndicator(out.resultIndex, out.totalResultSize);
    }

    void updateIndicator(int resultIndex, int totalResultSize) {
        mSearchParamAndIndicatorParam.indicator = String.format("%d/%d", resultIndex, totalResultSize);
        UIContext.bottomSearchedIndicateProp.set(mSearchParamAndIndicatorParam.indicator);
    }

    static void changeSearchTextCaseBtn(boolean val) {
        var main = UIContext.context();
        if (val) {
            IconfontCreator.setText(main.bottomSearchTextCaseBtn, "ziti",
                    main.getMainBottomSize(18), Colors.ColorBottomBtnHighLight.invoke());
        } else {
            IconfontCreator.setText(main.bottomSearchTextCaseBtn, "ziti",
                    main.getMainBottomSize(16), Colors.ColorBottomBtnGray.invoke());
        }
    }

    static void changeWholeWordBtn(boolean val) {
        var main = UIContext.context();
        if (val) {
            IconfontCreator.setText(main.bottomSearchTextWholeWordsBtn, "centerjustified",
                    main.getMainBottomSize(18), Colors.ColorBottomBtnHighLight.invoke());
        } else {
            IconfontCreator.setText(main.bottomSearchTextWholeWordsBtn, "centerjustified",
                    main.getMainBottomSize(16), Colors.ColorBottomBtnGray.invoke());
        }
    }

    static void changeRuleBtn(SearchParams.Type type) {
        var main = UIContext.context();
        if (type == SearchParams.Type.Normal) {
            IconfontCreator.setText(main.bottomSearchTextRuleBtn, "zhengzeshi",
                    main.getMainBottomSize(16), Colors.ColorBottomBtnGray.invoke());
        } else {
            IconfontCreator.setText(main.bottomSearchTextRuleBtn, "zhengzeshi",
                    main.getMainBottomSize(18), Colors.ColorBottomBtnHighLight.invoke());
        }
    }

    public static void refreshSize() {
        var area = UIContext.currentAreaProp.get();
        if (area == null) {
            return;
        }
        var params = area.getBottomSearchBtnsMgr().mSearchParamAndIndicatorParam.searchParams;
        changeSearchTextCaseBtn(params.useCaseMatch);
        changeWholeWordBtn(params.useWholeWords);
        changeRuleBtn(params.type);
    }

    public Cache getBottomCache() {
        return handler.cache;
    }

    EventHandler<MouseEvent> caseBtnClick = e -> caseBtnReal();
    private void caseBtnReal() {
        boolean newValue;
        newValue = !mSearchParamAndIndicatorParam.searchParams.useCaseMatch;
        mSearchParamAndIndicatorParam.searchParams.useCaseMatch = newValue;
        changeSearchTextCaseBtn(newValue);
        Log.d("#1 changed case button " + newValue);
        var flag = lastChangeSearchFlag.incrementAndGet();
        if(Styler.DEBUG_STYLER) Log.d("Styler: caseBtnReal 44 flag=" + flag);
        handler.triggerSearchParamsChanged(flag);
    }

    EventHandler<MouseEvent> wholeWordClick = e -> wholeWordReal();
    private void wholeWordReal() {
        boolean newValue;
        newValue = !mSearchParamAndIndicatorParam.searchParams.useWholeWords;
        mSearchParamAndIndicatorParam.searchParams.useWholeWords = newValue;
        changeWholeWordBtn(newValue);
        Log.d("#1 changed whole word button " + newValue);
        var flag = lastChangeSearchFlag.incrementAndGet();
        if(Styler.DEBUG_STYLER) Log.d("Styler: wholeWordReal 55 flag=" + flag);
        handler.triggerSearchParamsChanged(flag);
    }

    EventHandler<MouseEvent> ruleClick = e -> ruleReal();
    private void ruleReal() {
        SearchParams.Type newValue;
        var isCurrent = mSearchParamAndIndicatorParam.searchParams.type == SearchParams.Type.Normal;
        newValue = isCurrent ? SearchParams.Type.Regex : SearchParams.Type.Normal;
        mSearchParamAndIndicatorParam.searchParams.type = newValue;
        changeRuleBtn(newValue);
        Log.d("#1 changed rule button " + newValue);
        var flag = lastChangeSearchFlag.incrementAndGet();
        if(Styler.DEBUG_STYLER) Log.d("Styler: ruleReal 66 flag=" + flag);
        handler.triggerSearchParamsChanged(flag);
    }
}

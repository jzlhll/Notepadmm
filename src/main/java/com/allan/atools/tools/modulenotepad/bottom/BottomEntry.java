package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.Colors;
import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.ui.IconfontCreator;
import com.allan.atools.utils.Log;
import javafx.event.WeakEventHandler;

public final class BottomEntry {
    private BottomEntry() {}

    private static boolean isBottomSearchTextListenerSendIt = true;

    public static void initAfterBottomCreated() {
        UIContext.context().bottomSearchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            var curArea = UIContext.currentAreaProp.get();
            if (isBottomSearchTextListenerSendIt && curArea != null) {
                curArea.getBottomSearchBtnsMgr().bottomSearchTextChanged(newValue);
            }
        });

        UIContext.context().bottomSearchTextUpperBtn.setOnMouseClicked(mouseEvent -> {
            var ar = UIContext.currentAreaProp.get();
            if (ar != null) {
                ar.getBottomSearchBtnsMgr().jumpToNext(ar, true, false);
            }
        });

        UIContext.context().bottomSearchTextDownBtn.setOnMouseClicked(mouseEvent -> {
            var ar = UIContext.currentAreaProp.get();
            if (ar != null) {
                ar.getBottomSearchBtnsMgr().jumpToNext(ar, false, false);
            }
        });

        IconfontCreator.setText(UIContext.context().bottomSearchTextUpperBtn, "arrowup", 17, Colors.ColorBottomBtnNormal.invoke());
        IconfontCreator.setText(UIContext.context().bottomSearchTextDownBtn, "falling", 20, Colors.ColorBottomBtnNormal.invoke());

        //tab发生变化。可能是切换，可能是删除。
        UIContext.currentAreaProp.addListener((observable, oldValue, newValue) -> {
            Log.d("change when current area; reset bottom visible");
            changeTo(newValue);
            boolean isNotExistArea = newValue == null;
            if (isNotExistArea) {
                UIContext.context().bottomSearchTextCaseBtn.setVisible(false);
                UIContext.context().bottomSearchTextWholeWordsBtn.setVisible(false);
                UIContext.context().bottomSearchTextRuleBtn.setVisible(false);
                UIContext.context().bottomSearchTextUpperBtn.setVisible(false);
                UIContext.context().bottomSearchTextDownBtn.setVisible(false);
                UIContext.context().bottomSearchTextField.setVisible(false);
            } else {
                UIContext.context().bottomSearchTextCaseBtn.setVisible(true);
                UIContext.context().bottomSearchTextWholeWordsBtn.setVisible(true);
                UIContext.context().bottomSearchTextRuleBtn.setVisible(true); //当初code模式下为何不可见 !newValue.getEditor().isEditorCodeMode()
                UIContext.context().bottomSearchTextUpperBtn.setVisible(true);
                UIContext.context().bottomSearchTextDownBtn.setVisible(true);
                UIContext.context().bottomSearchTextField.setVisible(true);
            }
        });
    }

    private static void changeTo(EditorArea area) {
        var bottom = area == null ? null : area.getBottomSearchBtnsMgr();
        if (area != null) {
            UIContext.context().bottomSearchTextCaseBtn.setOnMouseClicked(new WeakEventHandler<>(bottom.caseBtnClick));
            UIContext.context().bottomSearchTextWholeWordsBtn.setOnMouseClicked(new WeakEventHandler<>(bottom.wholeWordClick));
            UIContext.context().bottomSearchTextRuleBtn.setOnMouseClicked(new WeakEventHandler<>(bottom.ruleClick));

            BottomSearchBtnsMgr.changeSearchTextCaseBtn(bottom.mSearchParamAndIndicatorParam.searchParams.useCaseMatch);
            BottomSearchBtnsMgr.changeWholeWordBtn(bottom.mSearchParamAndIndicatorParam.searchParams.useWholeWords);
            BottomSearchBtnsMgr.changeRuleBtn(bottom.mSearchParamAndIndicatorParam.searchParams.type);
        }

        UIContext.bottomSearchedIndicateProp.set(area == null ? "" : bottom.mSearchParamAndIndicatorParam.indicator);
        //设置状态文字 切换tab的时候，要先禁用一小会儿；监听底部文字变化；重新设置到新tab的文字内容。
        isBottomSearchTextListenerSendIt = false;
        UIContext.context().bottomSearchTextField.setText(area == null ? "" : bottom.mSearchParamAndIndicatorParam.searchParams.words);
        //Log.d("BottomManager change tab setText...");
        ThreadUtils.globalHandler().postDelayed(() -> {
            isBottomSearchTextListenerSendIt = true;
            //Log.d("BottomManager change tab setText reset");
        }, 500);
    }
}

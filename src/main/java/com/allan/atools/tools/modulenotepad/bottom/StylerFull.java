package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.tools.modulenotepad.local.StyleCreator;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ManualGC;
import com.allan.atools.text.beans.OneFileSearchResults;
import javafx.application.Platform;

final class StylerFull extends Styler.IStylerAction {
    @Override
    void setVisibleParaChanged() {
    }

    @Override
    void removeVisibleParaChanged() {
    }

    StylerFull(Styler styler) {
        super(styler);
    }

    private boolean isLastEmpty = true;

    @Override
    void action(EditorArea area, long flag, OneFileSearchResults items, BottomHandler.ClickType clickType, ShowType showType) {
        if (flag != mStyler.out.lastChangeSearchFlag.get()) {
            if(Styler.DEBUG_STYLER) Log.v("StylerFlag changed55 flag=" + flag);
            return;
        }
        //此时还在异步线程
        var styleSpans = StyleCreator.createStyles(area, items, showType);
        if (styleSpans != null) {
            Platform.runLater(() -> {
                if (flag != mStyler.out.lastChangeSearchFlag.get()) {
                    if(Styler.DEBUG_STYLER) Log.v("StylerFlag changed66 flag=" + flag);
                    return;
                }
                if(Styler.DEBUG_STYLER) Log.w(">>>>>>set StyleSpans<<<<<< last is empty: " + isLastEmpty);
                area.setStyleSpans(0, styleSpans);
                isLastEmpty = false;
                if (flag != mStyler.out.lastChangeSearchFlag.get()) {
                    if(Styler.DEBUG_STYLER) Log.v("StylerFlag changed77 flag=" + flag);
                    return;
                }
                if (clickType == BottomHandler.ClickType.Search) {
                    if(Styler.DEBUG_STYLER) Log.w(">>>>>>jump To Next<<<<");
                    mStyler.out.jumpToNext(area, false, true);
                }
                ManualGC.decupleGC();
            });
        } else {
            //走到这里说明是empty
            if (isLastEmpty) {
                if (Styler.DEBUG_STYLER) Log.d("Styler: ignore last is empty!");
                return;
            }

            Platform.runLater(() -> {
                if (flag != mStyler.out.lastChangeSearchFlag.get()) {
                    if(Styler.DEBUG_STYLER) Log.v("StylerFlag changed88 flag=" + flag);
                    return;
                }
                UIContext.bottomSearchedIndicateProp.set("0/0"); //TODO 由于这个search End Callback是综合了搜索和双击temprory搜索直接设置有点问题
                var len = area.getLength();
                if (len > 0) {
                    if(Styler.DEBUG_STYLER) Log.w(">>>>>set style initialTextStyle " + len + "<<<<< last is empty: " + isLastEmpty);
                    area.setStyle(0, area.getLength(), area.getInitialTextStyle());
                    isLastEmpty = true;
                }
            });
        }
    }
}

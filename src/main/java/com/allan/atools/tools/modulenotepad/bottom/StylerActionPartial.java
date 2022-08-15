package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.modulenotepad.local.StyleCreator;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ManualGC;
import com.allan.atools.utils.TimerCounter;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.baseparty.Action0;
import javafx.application.Platform;

final class StylerActionPartial extends StylerAction {
    StylerActionPartial(BottomSearchBtnsMgr out) {
        super(out);
    }

    private record VisibleIndexes(int first, int last, int max, int startNum, int endNum/*, startPos*/) {
        @Override
        public String toString() {
            return "VisibleIndexes{" +
                    "first=" + first +
                    ", last=" + last +
                    ", max=" + max +
                    ", startNum=" + startNum +
                    ", endNum=" + endNum +
                    '}';
        }
    }

    private ShowType lastShowType;

    private void setBigParaStylePartRunFunction() {
        Log.d("style when scrolled======>>>");
        actionEndPart(out.editorArea, 0, out.getBottomCache().cacheResult,
                BottomHandler.ClickType.None, lastShowType);
    }
    private final Runnable setBigParaStylePartRunnable = this::setBigParaStylePartRunFunction;

    //        //getHandler().removeCallbacksAndMessages(setBigParaStylePartRunnable);
    //        //getHandler().postDelayed(setBigParaStylePartRunnable, 100);
    //之前是移除滑动过程中的动作。现在是不移除；滑动过程中时时刻刻排队清理改变style
    final Action0 visibleParaChanged = () -> getHandler().post(setBigParaStylePartRunnable);

    private boolean setVisibleParaChanged = false;

    void setVisibleParaChanged() {
        if (!setVisibleParaChanged) {
            setVisibleParaChanged = true;
            out.editorArea.getEditor().visibleParagraphChanged.addAction(visibleParaChanged);
        }
    }

    void removeVisibleParaChanged() {
        if (setVisibleParaChanged) {
            setVisibleParaChanged = false;
            out.editorArea.getEditor().visibleParagraphChanged.removeAction(visibleParaChanged);
        }
    }

    @Override
    void action(EditorArea area, long flag, OneFileSearchResults items, BottomHandler.ClickType clickType, ShowType showType) {
        actionEndPart(area, flag, items, clickType, showType);
    }

    private VisibleIndexes getIndexesFix(EditorArea area) {
        int first = area.firstVisibleParToAllParIndex();
        int last = area.lastVisibleParToAllParIndex();
        int max = area.getParagraphs().size() - 1;
        int startNum = Math.max(first - 3, 0);
        int endNum = Math.min(last + 3, max);
        VisibleIndexes mIndexes = new VisibleIndexes(first, last, max, startNum, endNum);
        //mIndexes.startPos = area.getAbsolutePosition(mIndexes.startNum, 0);
        Log.d("after scroll actionEnd Part: " + mIndexes);
        return mIndexes;
    }

    private void actionEndPart(EditorArea area, final long flag, OneFileSearchResults items, BottomHandler.ClickType clickType, ShowType showType) {
        if (flag != out.lastChangeSearchFlag.get()) {
            if(Styler.DEBUG_STYLER) Log.v("StylerFlag changed11 flag=" + flag);
            return;
        }

        if (clickType != BottomHandler.ClickType.None) {
            lastShowType = showType;
        }
        Log.d("part style when init======>>>");
        //此时还在异步线程
        Platform.runLater(() -> {
            if (flag != out.lastChangeSearchFlag.get()) {
                if(Styler.DEBUG_STYLER) Log.v("StylerFlag changed22 flag=" + flag);
                return;
            }

            var indexes = getIndexesFix(area);
            TimerCounter.start("init createStyle");
            var styleSpansEx = StyleCreator.createStylesRange(area, items, showType,
                    indexes.startNum, indexes.endNum);
            Log.d(TimerCounter.end("init createStyle"));
            ThreadUtils.execute(() -> {
                if (styleSpansEx != null) {
                    Platform.runLater(()-> {
                        if (flag != out.lastChangeSearchFlag.get()) {
                            if(Styler.DEBUG_STYLER) Log.v("StylerFlag changed33 flag=" + flag);
                            return;
                        }

                        Log.d("start pos " + styleSpansEx.areaStartPos() + ", " + styleSpansEx.styleSpans().length());
                        area.setStyleSpans(styleSpansEx.areaStartPos(), styleSpansEx.styleSpans());
                        if (clickType == BottomHandler.ClickType.Search) {
                            out.jumpToNext(area, false, true);
                        }

                        ManualGC.lessGC();
                    });
                }
                else
                {
                    Platform.runLater(()->{
                        if (flag != out.lastChangeSearchFlag.get()) {
                            if(Styler.DEBUG_STYLER) Log.v("StylerFlag changed44 flag=" + flag);
                            return;
                        }

                        int endPos = area.getAbsolutePosition(indexes.endNum, 0);
                        int startPos = area.getAbsolutePosition(indexes.startNum, 0);
                        //GlobalProfs.bottomSearchedIndicateProp.set("0/0"); //TODO 由于这个search End Callback是综合了搜索和双击temprory搜索直接设置有点问题
                        area.setStyle(startPos, endPos, area.getInitialTextStyle());
                        if (clickType == BottomHandler.ClickType.Search) {
                            out.jumpToNext(area, false, true);
                        }
                    });
                }
            });
        });
    }

    @Override
    void destroy() {
        getHandler().removeCallback(setBigParaStylePartRunnable);
    }
}

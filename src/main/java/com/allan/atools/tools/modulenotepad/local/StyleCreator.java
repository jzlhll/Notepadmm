package com.allan.atools.tools.modulenotepad.local;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class StyleCreator {
    /**
     * 通过二分法快速的定位到当前行，在items里面的index
     */
    private static int fastLocationIndex(List<ResultItemWrap> items, int lineNum, boolean needLow) {
        int low = 0, high = items.size() - 1;
        int mid, lastmid = -99; //随便放一个负数

        int n;
        while (true) {
            mid = (low + high) / 2;
            if (lastmid == mid) {
                break;
            }
            lastmid = mid;

            n =  items.get(mid).lineNum;
            if (lineNum > n) {
                low = mid;
            } else if (lineNum < n) {
                high = mid;
            } else {
                low = high = mid; //命中了当前行，则直接返回相同的index即可
                break;
            }
        }

        return needLow ? low : high; //由于我们是大致上找出lineNum的位置，用于setStyle的起始。所以取low即可。不用关注high =low+1还是=low
    }

    private static int[] fastLocationIndexes(List<ResultItemWrap> items, int startLineNum, int endLineNum) {
        //如果搜索结果中第一个的lineNum都大于显示区域。则不要
        if (items.get(0).lineNum > endLineNum) {
            return null;
        }
        //如果搜索结果中最后1个的lineNum都小于显示区域。则不要
        if (items.get(items.size() - 1).lineNum < startLineNum) {
            return null;
        }

        var start = fastLocationIndex(items, startLineNum, true);
        var end = fastLocationIndex(items, endLineNum, false);

        int withinStartIndex = -1, withinEndIndex = -1;
        for (int i = start; i <= end; i++) {
            var iLineNum = items.get(i).lineNum;
            if (iLineNum >= startLineNum && iLineNum <= endLineNum) {
                if (withinStartIndex == -1) { //开始的地方肯定只做一次
                    withinStartIndex = i;
                }
                withinEndIndex = i; //结束的地方一直往后直到最后一个
            }
        }

        if (withinStartIndex == -1 || withinEndIndex == -1) {
            //这种情况，说明fastLocationIndex得到的结果是not within区域的；这里做修正。
            //throw new RuntimeException("impossible of with in! for fastLocation Indexes ");
            return null;
        }
        return new int[] {withinStartIndex, withinEndIndex}; //由于我们是大致上找出lineNum的位置，用于setStyle的起始。所以取low即可。不用关注high =low+1还是=low
    }

    /**
     * 用于底部搜索；将结果 按照既定的3色上色。
     */
    public static StyleSpansEx createStylesRange(GenericStyledArea<Collection<String>, String, Collection<String>> area,
                                                 OneFileSearchResults oneResult,
                                                 ShowType mode,
                                                 int startParaNum,
                                                 int endParaNum) {
        if (oneResult == null || oneResult.results == null || oneResult.results.size() == 0) {
            return null;
        }

        var startEnd = fastLocationIndexes(oneResult.results, startParaNum, endParaNum);
        if (startEnd == null) {
            return null;
        }

        var initTextStyle = area.getInitialTextStyle();
        Set<String> style1;
        Set<String> style2 = null;

        String selectedStyleClass = "editor-selected-label";
        String tempStyleClass = "editor-selected-temp-label";

        if (mode == ShowType.Search) {
            style1 = Collections.singleton(selectedStyleClass);
        } else if (mode == ShowType.Temp) {
            style1 = Collections.singleton(tempStyleClass);
        } else {
            style1 = Collections.singleton(selectedStyleClass);
            style2 = Collections.singleton(tempStyleClass);
        }

        boolean isStyle2Null = style2 == null;

        var ssb = new StyleSpansBuilder<Collection<String>>();
        int len;
        int lastOffset = area.getAbsolutePosition(startParaNum, 0);
        int startPos = lastOffset;

        boolean isAdded = false;
        int totalSsb = 0;

        for (int i = startEnd[0]; i <= startEnd[1]; i++) {
            var itemWrap = oneResult.results.get(i);
            //Log.d("item wrap " + itemWrap.lineNum);
            if (itemWrap.lineMode == ResultItemWrap.LineMode.Real && itemWrap.items != null) {
                for (var item : itemWrap.items) {
                    isAdded = true;

                    len = item.range.totalOffset - lastOffset;
                    if (len > 0) {
                        ssb.add(initTextStyle, len);
                        totalSsb++;
                    }

                    len = item.range.end - item.range.start;
                    ssb.add(isStyle2Null || item.searchParams.major ? style1 : style2, len);
                    totalSsb++;

                    lastOffset = item.range.totalOffset + len;
                }
            }
        }

        int endPos = area.getAbsolutePosition(endParaNum, 0);
        if (endPos > lastOffset) {
            ssb.add(initTextStyle, endPos - lastOffset);
            totalSsb++;
        }
        //Log.d("setStyles total ssb size: " + totalSsb);
        return new StyleSpansEx(ssb.create(), startParaNum, endParaNum, startPos);
    }

    /**
     * 用于底部搜索；将结果 按照既定的3色上色。
     */
    public static StyleSpans<Collection<String>> createStyles(GenericStyledArea<Collection<String>, String, Collection<String>> area,
                                                              OneFileSearchResults oneResult,
                                                              ShowType mode) {
        if (oneResult == null || oneResult.results == null || oneResult.results.size() == 0) {
            return null;
        }

        var initTextStyle = area.getInitialTextStyle();
        Set<String> style1;
        Set<String> style2 = null;

        String selectedStyleClass = "editor-selected-label";
        String tempStyleClass = "editor-selected-temp-label";

        if (mode == ShowType.Search) {
            style1 = Collections.singleton(selectedStyleClass);
        } else if (mode == ShowType.Temp) {
            style1 = Collections.singleton(tempStyleClass);
        } else {
            style1 = Collections.singleton(selectedStyleClass);
            style2 = Collections.singleton(tempStyleClass);
        }

        boolean isStyle2Null = style2 == null;

        var ssb = new StyleSpansBuilder<Collection<String>>();
        int len;
        int lastOffset = 0;

        boolean isAdded = false;
        int totalSsb = 0;

        for (ResultItemWrap itemWrap : oneResult.results) {
            //Log.d("item wrap " + itemWrap.lineNum);
            if (itemWrap.lineMode == ResultItemWrap.LineMode.Real && itemWrap.items != null) {
                for (var item : itemWrap.items) {
                    isAdded = true;

                    len = item.range.totalOffset - lastOffset;
                    if (len > 0) {
                        ssb.add(initTextStyle, len);
                        totalSsb++;
                    }

                    len = item.range.end - item.range.start;

                    ssb.add(isStyle2Null || item.searchParams.major ? style1 : style2, len);
                    totalSsb++;

                    lastOffset = item.range.totalOffset + len;
                }
            }
        }

        if (isAdded) {
            if (oneResult.totalLen > lastOffset) {
                ssb.add(initTextStyle, oneResult.totalLen - lastOffset);
                totalSsb++;
            }
            //Log.d("setStyles total ssb size: " + totalSsb);
            return ssb.create();
        } else {
            return null;
        }
    }

}

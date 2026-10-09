package com.allan.atools.tools.modulenotepad.local;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public final class StyleCreator {
    /** 底部搜索的全文命中着色。 */
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

        for (ResultItemWrap itemWrap : oneResult.results) {
            if (itemWrap.lineMode == ResultItemWrap.LineMode.Real && itemWrap.items != null) {
                for (var item : itemWrap.items) {
                    isAdded = true;

                    len = item.range.totalOffset - lastOffset;
                    if (len > 0) {
                        ssb.add(initTextStyle, len);
                    }

                    len = item.range.end - item.range.start;

                    ssb.add(isStyle2Null || item.searchParams.major ? style1 : style2, len);

                    lastOffset = item.range.totalOffset + len;
                }
            }
        }

        if (isAdded) {
            if (oneResult.totalLen > lastOffset) {
                ssb.add(initTextStyle, oneResult.totalLen - lastOffset);
            }
            return ssb.create();
        } else {
            return null;
        }
    }

}

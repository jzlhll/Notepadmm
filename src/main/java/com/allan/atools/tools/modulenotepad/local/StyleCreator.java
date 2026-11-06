package com.allan.atools.tools.modulenotepad.local;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class StyleCreator {
    static final String SEARCHED_STYLE_CLASS = "editor-selected-label";
    static final String TEMP_STYLE_CLASS = "editor-selected-temp-label";

    /**
     * 命中片段必须是「基础文字样式 + 命中样式」的组合，不能只留命中样式。
     * 只留命中样式会丢掉 editor-default-label 携带的文字颜色，文字回退到 JavaFX
     * 默认的黑色，在深色主题里几乎不可读，浅色主题里也会失去主题一致性。
     */
    private static Set<String> withHighlight(Collection<String> baseStyle, String highlightClass) {
        var styles = new LinkedHashSet<String>(baseStyle == null ? 4 : baseStyle.size() + 1);
        if (baseStyle != null) {
            styles.addAll(baseStyle);
        }
        styles.add(highlightClass);
        return Collections.unmodifiableSet(styles);
    }

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

        String selectedStyleClass = SEARCHED_STYLE_CLASS;
        String tempStyleClass = TEMP_STYLE_CLASS;

        if (mode == ShowType.Search) {
            style1 = withHighlight(initTextStyle, selectedStyleClass);
        } else if (mode == ShowType.Temp) {
            style1 = withHighlight(initTextStyle, tempStyleClass);
        } else {
            style1 = withHighlight(initTextStyle, selectedStyleClass);
            style2 = withHighlight(initTextStyle, tempStyleClass);
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

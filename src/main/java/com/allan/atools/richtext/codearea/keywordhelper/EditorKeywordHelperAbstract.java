package com.allan.atools.richtext.codearea.keywordhelper;

import com.allan.baseparty.Action0;
import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.bean.SearchParams;
import javafx.application.Platform;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class EditorKeywordHelperAbstract {
    volatile Pattern mLastMatcher;
    final Object LOCK = new Object();

    boolean mIsTemporaryEnabled, mIsSearchEnabled;

    public abstract Pattern getPattern(SearchParams temporary, SearchParams search);

    public abstract Function<String, StyleSpans<Collection<String>>> getComputeHighlightFun();

    final String[] paramsToPatterns(SearchParams temporary, SearchParams search) {
        //todo temp的字段就采用\b全词匹配并匹配大小写
        String tempPattern = temporary == null ? null : (TextUtils.isEmpty(temporary.words) ? null : "\\b" + temporary.words + "\\b");
        String searchPattern;
        //todo 现在是不做正则修正。并且不管type是否为rule
        if (search == null || TextUtils.isEmpty(search.words)) {
            searchPattern = null;
        } else if (search.useWholeWords) {
            if (search.useCaseMatch) {
                searchPattern = "\\b" + search.words + "\\b";
            } else {
                searchPattern = "(?i)\\b" + search.words + "\\b";
            }
        } else {
            if (search.useCaseMatch) {
                searchPattern = search.words;
            } else {
                searchPattern = "(?i)" + search.words;
            }
        }

        mIsTemporaryEnabled = tempPattern != null;
        mIsSearchEnabled = searchPattern != null;
        return new String[]{tempPattern, searchPattern};
    }

    /**
     *
     */
    public final void triggerAllText(GenericStyledArea<Collection<String>, String, Collection<String>> area,
                                     SearchParams temporaryTextParam, SearchParams searchTextParam, Action0 endOfSetStyle) {
        mLastMatcher = getPattern(temporaryTextParam, searchTextParam);
        var spans = getComputeHighlightFun().apply(area.getText());
        Platform.runLater(()-> {
            area.setStyleSpans(0, spans);
            if(endOfSetStyle != null) endOfSetStyle.invoke();
        });
    }

//   todo 实现局部刷新。暂时代码文件以全刷为准
    //
//    public void triggerVisible(GenericStyledArea<Collection<String>, String, Collection<String>> area,
//                               SearchParams temporaryTextParam, SearchParams searchTextParam) {
//        mLastMatcher = getPattern(temporaryTextParam, searchTextParam);
//
//        int paragraph = Math.min(area.firstVisibleParToAllParIndex() + lm.getFrom(), area.getParagraphs().size() - 1);
//        String text = area.getText(paragraph, 0, paragraph, area.getParagraphLength(paragraph));
//
//        if (paragraph != prevParagraph || text.length() != prevTextLength) {
//            int startPos = area.getAbsolutePosition(paragraph, 0);
//            Platform.runLater(() -> area.setStyleSpans(startPos, computeStyles.apply(text)));
//            prevTextLength = text.length();
//            prevParagraph = paragraph;
//        }
//    }
}

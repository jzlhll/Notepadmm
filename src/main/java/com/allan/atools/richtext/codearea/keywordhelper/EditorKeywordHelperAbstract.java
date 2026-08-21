package com.allan.atools.richtext.codearea.keywordhelper;

import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.bean.SearchParams;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

public abstract class EditorKeywordHelperAbstract {
    public record StyleUpdate(int start, StyleSpans<Collection<String>> spans) {
    }

    volatile Pattern mLastMatcher;
    final Object LOCK = new Object();

    boolean mIsTemporaryEnabled, mIsSearchEnabled;

    public abstract Pattern getPattern(SearchParams temporary, SearchParams search);

    protected abstract StyleSpans<Collection<String>> computeHighlighting(
            String text, BooleanSupplier canContinue);

    final Pattern compilePattern(String source) {
        var lastMatcher = mLastMatcher;
        return lastMatcher != null && lastMatcher.pattern().equals(source)
                ? lastMatcher : Pattern.compile(source);
    }

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

    public final StyleUpdate computeStyleUpdate(String text, SearchParams temporary, SearchParams search,
                                                StyleSpans<Collection<String>> currentSpans,
                                                BooleanSupplier canContinue) {
        if (text.isEmpty()) {
            return canContinue.getAsBoolean() ? new StyleUpdate(0, null) : null;
        }
        mLastMatcher = getPattern(temporary, search);
        if (!canContinue.getAsBoolean()) {
            return null;
        }
        var newSpans = computeHighlighting(text, canContinue);
        return newSpans == null || !canContinue.getAsBoolean()
                ? null : createStyleUpdate(currentSpans, newSpans, text.length(), canContinue);
    }

    private StyleUpdate createStyleUpdate(StyleSpans<Collection<String>> currentSpans,
                                          StyleSpans<Collection<String>> newSpans, int textLength,
                                          BooleanSupplier canContinue) {
        if (newSpans.length() != textLength) {
            throw new IllegalStateException("Code style length does not match text length");
        }
        if (currentSpans == null || currentSpans.length() != textLength) {
            return new StyleUpdate(0, newSpans);
        }
        int changedStart = findChangedStart(currentSpans, newSpans, canContinue);
        if (changedStart < 0) {
            return null;
        }
        if (changedStart == textLength) {
            return new StyleUpdate(0, null);
        }
        int changedEnd = findChangedEnd(currentSpans, newSpans, changedStart, canContinue);
        if (changedEnd < 0) {
            return null;
        }
        return new StyleUpdate(changedStart, newSpans.subView(changedStart, changedEnd));
    }

    private int findChangedStart(StyleSpans<Collection<String>> oldSpans,
                                 StyleSpans<Collection<String>> newSpans,
                                 BooleanSupplier canContinue) {
        int oldIndex = 0;
        int newIndex = 0;
        int oldRemaining = oldSpans.getStyleSpan(0).getLength();
        int newRemaining = newSpans.getStyleSpan(0).getLength();
        int offset = 0;
        while (oldIndex < oldSpans.getSpanCount() && newIndex < newSpans.getSpanCount()) {
            if (((oldIndex + newIndex) & 1023) == 0 && !canContinue.getAsBoolean()) {
                return -1;
            }
            var oldSpan = oldSpans.getStyleSpan(oldIndex);
            var newSpan = newSpans.getStyleSpan(newIndex);
            if (!Objects.equals(oldSpan.getStyle(), newSpan.getStyle())) {
                return offset;
            }
            int consumed = oldRemaining < newRemaining ? oldRemaining : newRemaining;
            offset += consumed;
            oldRemaining -= consumed;
            newRemaining -= consumed;
            if (oldRemaining == 0 && ++oldIndex < oldSpans.getSpanCount()) {
                oldRemaining = oldSpans.getStyleSpan(oldIndex).getLength();
            }
            if (newRemaining == 0 && ++newIndex < newSpans.getSpanCount()) {
                newRemaining = newSpans.getStyleSpan(newIndex).getLength();
            }
        }
        return offset;
    }

    private int findChangedEnd(StyleSpans<Collection<String>> oldSpans,
                               StyleSpans<Collection<String>> newSpans, int changedStart,
                               BooleanSupplier canContinue) {
        int oldIndex = oldSpans.getSpanCount() - 1;
        int newIndex = newSpans.getSpanCount() - 1;
        int oldRemaining = oldSpans.getStyleSpan(oldIndex).getLength();
        int newRemaining = newSpans.getStyleSpan(newIndex).getLength();
        int suffixLength = 0;
        int maxSuffixLength = oldSpans.length() - changedStart;
        while (oldIndex >= 0 && newIndex >= 0 && suffixLength < maxSuffixLength) {
            if (((oldIndex + newIndex) & 1023) == 0 && !canContinue.getAsBoolean()) {
                return -1;
            }
            var oldSpan = oldSpans.getStyleSpan(oldIndex);
            var newSpan = newSpans.getStyleSpan(newIndex);
            if (!Objects.equals(oldSpan.getStyle(), newSpan.getStyle())) {
                break;
            }
            int consumed = oldRemaining < newRemaining ? oldRemaining : newRemaining;
            int remainingSuffix = maxSuffixLength - suffixLength;
            if (consumed > remainingSuffix) {
                consumed = remainingSuffix;
            }
            suffixLength += consumed;
            oldRemaining -= consumed;
            newRemaining -= consumed;
            if (oldRemaining == 0 && --oldIndex >= 0) {
                oldRemaining = oldSpans.getStyleSpan(oldIndex).getLength();
            }
            if (newRemaining == 0 && --newIndex >= 0) {
                newRemaining = newSpans.getStyleSpan(newIndex).getLength();
            }
        }
        return oldSpans.length() - suffixLength;
    }
}

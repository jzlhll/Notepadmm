package com.allan.atools.richtext.codearea.keywordhelper;

import com.allan.atools.bean.SearchParams;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Markdown 轻量语法高亮。
 */
public final class EditorKeywordHelperImplMarkdown extends EditorKeywordHelperAbstract {
    private static final String FENCED_CODE_PATTERN = "^[\\t ]*```[^\\r\\n]*"
            + "(?:\\R[\\s\\S]*?(?:^[\\t ]*```[\\t ]*$|\\z)|\\z)";
    private static final String INLINE_CODE_PATTERN = "`[^`\\r\\n]+`";
    private static final String TITLE_1_PATTERN = "^\\h{0,3}#\\h+[^\\r\\n]+$";
    private static final String TITLE_2_PATTERN = "^\\h{0,3}#{2}\\h+[^\\r\\n]+$";
    private static final String TITLE_3_PATTERN = "^\\h{0,3}#{3}\\h+[^\\r\\n]+$";
    private static final String TITLE_4_PATTERN = "^\\h{0,3}#{4}\\h+[^\\r\\n]+$";
    private static final String TITLE_5_PATTERN = "^\\h{0,3}#{5,}\\h+[^\\r\\n]+$";
    private static final String TABLE_PATTERN = "^\\h*\\|?.+\\|.+\\|?\\h*\\R"
            + "\\h*\\|?\\h*:?-{3,}:?\\h*(?:\\|\\h*:?-{3,}:?\\h*)+\\|?\\h*$"
            + "(?:\\R\\h*\\|?.+\\|.+\\|?\\h*)*";
    private static final String QUOTE_PATTERN = "^\\h{0,3}>\\h?";
    private static final String LIST_PATTERN = "^\\h{0,3}(?:[-+*]\\h+|\\d+[.)]\\h+)"
            + "(?:\\[[ xX]]\\h+)?";
    private static final String LINK_PATTERN = "!?\\[[^\\]\\r\\n]+\\]"
            + "\\([^\\s)]+(?:\\h+\"[^\"]*\")?\\)";
    private static final String BOLD_PATTERN = "(?<![\\\\*])\\*{2}(?!\\*)(?=\\S)"
            + "(?:(?!\\*{2})[^\\r\\n])+?(?<![\\\\\\s])\\*{2}(?!\\*)"
            + "|(?<![\\w\\\\_])_{2}(?!_)(?=\\S)"
            + "(?:(?!_{2})[^\\r\\n])+?(?<![\\\\\\s])_{2}(?![\\w_])";
    private static final String ITALIC_PATTERN = "(?<![\\\\*])\\*(?!\\*)(?=\\S)"
            + "[^*\\r\\n]+?(?<![\\\\\\s])\\*(?!\\*)"
            + "|(?<![\\w\\\\_])_(?!_)(?=\\S)"
            + "[^_\\r\\n]+?(?<![\\\\\\s])_(?![\\w_])";
    private static final String STRIKETHROUGH_PATTERN = "(?<![\\\\~])~{2}(?!~)(?=\\S)"
            + "(?:(?!~{2})[^\\r\\n])+?(?<![\\\\\\s])~{2}(?!~)"
            + "|(?<![\\\\~])~(?!~)(?=\\S)"
            + "[^~\\r\\n]+?(?<![\\\\\\s])~(?!~)";
    private static final Pattern TABLE_CONTENT_PATTERN = Pattern.compile(
            "(?<TABLEMARK>\\||:?-{3,}:?)"
                    + "|(?<INLINECODE>" + INLINE_CODE_PATTERN + ")"
                    + "|(?<LINK>" + LINK_PATTERN + ")"
                    + "|(?<BOLD>" + BOLD_PATTERN + ")"
                    + "|(?<ITALIC>" + ITALIC_PATTERN + ")"
                    + "|(?<STRIKETHROUGH>" + STRIKETHROUGH_PATTERN + ")");

    private final HashMap<String, Set<String>> styleClassAndSetMap = new HashMap<>();

    @Override
    public Pattern getPattern(SearchParams temporary, SearchParams search) {
        synchronized (LOCK) {
            var paramsPatterns = paramsToPatterns(temporary, search);
            var patternParts = new ArrayList<String>();
            if (paramsPatterns[0] != null) {
                patternParts.add("(?<TEMPORARY>" + paramsPatterns[0] + ")");
            }
            if (paramsPatterns[1] != null) {
                patternParts.add("(?<SEARCH>" + paramsPatterns[1] + ")");
            }
            patternParts.add("(?<FENCEDCODE>" + FENCED_CODE_PATTERN + ")");
            patternParts.add("(?<INLINECODE>" + INLINE_CODE_PATTERN + ")");
            patternParts.add("(?<TITLE1>" + TITLE_1_PATTERN + ")");
            patternParts.add("(?<TITLE2>" + TITLE_2_PATTERN + ")");
            patternParts.add("(?<TITLE3>" + TITLE_3_PATTERN + ")");
            patternParts.add("(?<TITLE4>" + TITLE_4_PATTERN + ")");
            patternParts.add("(?<TITLE5>" + TITLE_5_PATTERN + ")");
            patternParts.add("(?<TABLE>" + TABLE_PATTERN + ")");
            patternParts.add("(?<QUOTE>" + QUOTE_PATTERN + ")");
            patternParts.add("(?<LIST>" + LIST_PATTERN + ")");
            patternParts.add("(?<LINK>" + LINK_PATTERN + ")");
            patternParts.add("(?<BOLD>" + BOLD_PATTERN + ")");
            patternParts.add("(?<ITALIC>" + ITALIC_PATTERN + ")");
            patternParts.add("(?<STRIKETHROUGH>" + STRIKETHROUGH_PATTERN + ")");
            return Pattern.compile(String.join("|", patternParts), Pattern.MULTILINE);
        }
    }

    @Override
    public Function<String, StyleSpans<Collection<String>>> getComputeHighlightFun() {
        return this::computeHighlighting;
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (mLastMatcher == null) {
            synchronized (LOCK) {
                if (mLastMatcher == null) {
                    mLastMatcher = getPattern(null, null);
                }
            }
        }

        var matcher = mLastMatcher.matcher(text);
        var spansBuilder = new StyleSpansBuilder<Collection<String>>();
        int lastMatchEnd = 0;
        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastMatchEnd);
            if (matcher.group("TABLE") != null) {
                addTableHighlighting(spansBuilder, matcher.group());
                lastMatchEnd = matcher.end();
                continue;
            }
            String styleClass = mIsTemporaryEnabled && matcher.group("TEMPORARY") != null ? "temporary" :
                    mIsSearchEnabled && matcher.group("SEARCH") != null ? "search" :
                            matcher.group("FENCEDCODE") != null ? "markdown-code" :
                                    matcher.group("INLINECODE") != null ? "markdown-inline-code" :
                                            matcher.group("TITLE1") != null ? "markdown-title-1" :
                                                    matcher.group("TITLE2") != null ? "markdown-title-2" :
                                                            matcher.group("TITLE3") != null ? "markdown-title-3" :
                                                                    matcher.group("TITLE4") != null ? "markdown-title-4" :
                                                                            matcher.group("TITLE5") != null ? "markdown-title-5" :
                                                                                    matcher.group("QUOTE") != null ? "markdown-quote" :
                                                                                            matcher.group("LIST") != null ? "markdown-list" :
                                                                                                    matcher.group("LINK") != null ? "markdown-link" :
                                                                                                            matcher.group("BOLD") != null ? "markdown-bold" :
                                                                                                                    matcher.group("ITALIC") != null ? "markdown-italic" :
                                                                                                                            "markdown-strikethrough";
            var styleSet = styleClassAndSetMap.computeIfAbsent(styleClass, Collections::singleton);
            spansBuilder.add(styleSet, matcher.end() - matcher.start());
            lastMatchEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastMatchEnd);
        return spansBuilder.create();
    }

    private void addTableHighlighting(StyleSpansBuilder<Collection<String>> spansBuilder, String tableText) {
        var matcher = TABLE_CONTENT_PATTERN.matcher(tableText);
        int lastMatchEnd = 0;
        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastMatchEnd);
            String styleClass = matcher.group("TABLEMARK") != null ? "markdown-table-mark" :
                    matcher.group("INLINECODE") != null ? "markdown-inline-code" :
                            matcher.group("LINK") != null ? "markdown-link" :
                                    matcher.group("BOLD") != null ? "markdown-bold" :
                                            matcher.group("ITALIC") != null ? "markdown-italic" :
                                                    "markdown-strikethrough";
            var styleSet = styleClassAndSetMap.computeIfAbsent(styleClass, Collections::singleton);
            spansBuilder.add(styleSet, matcher.end() - matcher.start());
            lastMatchEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), tableText.length() - lastMatchEnd);
    }
}

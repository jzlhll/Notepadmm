package com.allan.atools.richtext.codearea.keywordhelper;

import com.allan.atools.bean.SearchParams;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorKeywordHelperImplXml extends EditorKeywordHelperAbstract {
    private static final Pattern ATTRIBUTES = Pattern.compile("([-.:\\w]+\\h*)(=)(\\h*(?:\"[^\"]*\"|'[^']*'))");
    private static final Collection<String> STYLE_TEMPORARY = Collections.singleton("temporary");
    private static final Collection<String> STYLE_SEARCH = Collections.singleton("search");
    private static final Collection<String> STYLE_COMMENT = Collections.singleton("comment");
    private static final Collection<String> STYLE_TAG_MARK = Collections.singleton("tagmark");
    private static final Collection<String> STYLE_TAG = Collections.singleton("anytag");
    private static final Collection<String> STYLE_ATTRIBUTE = Collections.singleton("attribute");
    private static final Collection<String> STYLE_ATTRIBUTE_VALUE = Collections.singleton("avalue");

    private static final int GROUP_OPEN_BRACKET = 2;
    private static final int GROUP_ELEMENT_NAME = 3;
    private static final int GROUP_ATTRIBUTES_SECTION = 4;
    private static final int GROUP_CLOSE_BRACKET = 5;
    private static final int GROUP_ATTRIBUTE_NAME = 1;
    private static final int GROUP_EQUAL_SYMBOL = 2;
    private static final int GROUP_ATTRIBUTE_VALUE = 3;

    @Override
    public Pattern getPattern(SearchParams temporary, SearchParams search) {
        var ans = paramsToPatterns(temporary, search);
        var tempPattern = ans[0];
        var searchPattern = ans[1];

        if (tempPattern == null && searchPattern == null) {
            return compilePattern(
                    "(?<ELEMENT>(</?\\h*)([-.:0-9a-zA-Z_]+)([^<>]*)(\\h*/?>))"
                            + "|(?<COMMENT><!--[\\s\\S]*?-->)");
        }

        if (tempPattern == null) { //tempPattern == null && searchPattern != null
            return compilePattern(
                    "(?<SEARCH>" + searchPattern + ")"
                            + "|(?<ELEMENT>(</?\\h*)([-.:0-9a-zA-Z_]+)([^<>]*)(\\h*/?>))"
                            + "|(?<COMMENT><!--[\\s\\S]*?-->)");
        }

        if (searchPattern == null) { //tempPattern != null && searchPattern == null
            return compilePattern(
                    "(?<TEMPORARY>" + tempPattern + ")"
                            + "|(?<ELEMENT>(</?\\h*)([-.:0-9a-zA-Z_]+)([^<>]*)(\\h*/?>))"
                            + "|(?<COMMENT><!--[\\s\\S]*?-->)");
        }

        //tempPattern != null && searchPattern != null
        return compilePattern(
                "(?<TEMPORARY>" + tempPattern + ")"
                        + "|(?<SEARCH>" + searchPattern + ")"
                        + "|(?<ELEMENT>(</?\\h*)([-.:0-9a-zA-Z_]+)([^<>]*)(\\h*/?>))"
                        + "|(?<COMMENT><!--[\\s\\S]*?-->)");
    }

    @Override
    protected StyleSpans<Collection<String>> computeHighlighting(
            String text, BooleanSupplier canContinue) {
        if (mLastMatcher == null) {
            synchronized (LOCK) {
                if (mLastMatcher == null) {
                    mLastMatcher = getPattern(null, null);
                }
            }
        }

        Matcher matcher = mLastMatcher.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int matchCount = 0;
        while (matcher.find()) {
            if ((matchCount++ & 255) == 0 && !canContinue.getAsBoolean()) {
                return null;
            }
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);

            if (mIsTemporaryEnabled && matcher.group("TEMPORARY") != null) {
                spansBuilder.add(STYLE_TEMPORARY, matcher.end() - matcher.start());
            } else if (mIsSearchEnabled && matcher.group("SEARCH") != null) {
                spansBuilder.add(STYLE_SEARCH, matcher.end() - matcher.start());
            } else if (matcher.group("COMMENT") != null) {
                spansBuilder.add(STYLE_COMMENT, matcher.end() - matcher.start());
            } else {
                if (matcher.group("ELEMENT") != null) {
                    String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);
                    spansBuilder.add(STYLE_TAG_MARK, matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));
                    spansBuilder.add(STYLE_TAG, matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));

                    if (!attributesText.isEmpty()) {

                        lastKwEnd = 0;

                        Matcher amatcher = ATTRIBUTES.matcher(attributesText);
                        int attributeCount = 0;
                        while (amatcher.find()) {
                            if ((attributeCount++ & 255) == 0 && !canContinue.getAsBoolean()) {
                                return null;
                            }
                            spansBuilder.add(Collections.emptyList(), amatcher.start() - lastKwEnd);
                            spansBuilder.add(STYLE_ATTRIBUTE, amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(STYLE_TAG_MARK, amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(STYLE_ATTRIBUTE_VALUE, amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));
                            lastKwEnd = amatcher.end();
                        }
                        if (attributesText.length() > lastKwEnd)
                            spansBuilder.add(Collections.emptyList(), attributesText.length() - lastKwEnd);
                    }

                    lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION);

                    spansBuilder.add(STYLE_TAG_MARK, matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd);
                }
            }
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}

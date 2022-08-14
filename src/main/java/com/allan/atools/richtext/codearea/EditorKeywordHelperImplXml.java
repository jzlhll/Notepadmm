package com.allan.atools.richtext.codearea;

import com.allan.atools.bean.SearchParams;
import com.allan.atools.utils.Log;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorKeywordHelperImplXml extends EditorKeywordHelperAbstract {
    private static final Pattern ATTRIBUTES = Pattern.compile("(\\w+\\h*)(=)(\\h*\"[^\"]+\")");

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
            return Pattern.compile(
                    "(?<ELEMENT>(</?\\h*)([.0-9a-zA-Z_]+)([^<>]*)(\\h*/?>))"
                            + "|(?<COMMENT><!--[^<>]+-->)");
        }

        if (tempPattern == null) { //tempPattern == null && searchPattern != null
            return Pattern.compile(
                    "(?<SEARCH>" + searchPattern + ")"
                            + "|(?<ELEMENT>(</?\\h*)([.0-9a-zA-Z_]+)([^<>]*)(\\h*/?>))"
                            + "|(?<COMMENT><!--[^<>]+-->)");
        }

        if (searchPattern == null) { //tempPattern != null && searchPattern == null
            return Pattern.compile(
                    "(?<TEMPORARY>" + tempPattern + ")"
                            + "|(?<ELEMENT>(</?\\h*)([.0-9a-zA-Z_]+)([^<>]*)(\\h*/?>))"
                            + "|(?<COMMENT><!--[^<>]+-->)");
        }

        //tempPattern != null && searchPattern != null
        return Pattern.compile(
                "(?<TEMPORARY>" + tempPattern + ")"
                        + "|(?<SEARCH>" + searchPattern + ")"
                        + "|(?<ELEMENT>(</?\\h*)([.0-9a-zA-Z_]+)([^<>]*)(\\h*/?>))"
                        + "|(?<COMMENT><!--[^<>]+-->)");
    }

    @Override
    public Function<String, StyleSpans<Collection<String>>> getComputeHighlightFun() {
        return this::computeHighlighting;
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Log.d("compute highlighting ");
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
        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);

            if (mIsTemporaryEnabled && matcher.group("TEMPORARY") != null) {
                spansBuilder.add(Collections.singleton("temporary"), matcher.end() - matcher.start());
            } else if (mIsSearchEnabled && matcher.group("SEARCH") != null) {
                spansBuilder.add(Collections.singleton("search"), matcher.end() - matcher.start());
            } else if (matcher.group("COMMENT") != null) {
                spansBuilder.add(Collections.singleton("comment"), matcher.end() - matcher.start());
            } else {
                if (matcher.group("ELEMENT") != null) {
                    String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);
                    spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));
                    spansBuilder.add(Collections.singleton("anytag"), matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));

                    if (!attributesText.isEmpty()) {

                        lastKwEnd = 0;

                        Matcher amatcher = ATTRIBUTES.matcher(attributesText);
                        while (amatcher.find()) {
                            spansBuilder.add(Collections.emptyList(), amatcher.start() - lastKwEnd);
                            spansBuilder.add(Collections.singleton("attribute"), amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(Collections.singleton("tagmark"), amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(Collections.singleton("avalue"), amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));
                            lastKwEnd = amatcher.end();
                        }
                        if (attributesText.length() > lastKwEnd)
                            spansBuilder.add(Collections.emptyList(), attributesText.length() - lastKwEnd);
                    }

                    lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION);

                    spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd);
                }
            }
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}

package com.allan.atools.richtext.codearea.keywordhelper;

import com.allan.atools.bean.SearchParams;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorKeywordHelperImplJava extends EditorKeywordHelperAbstract {

    private String keyWordPattern() {
        return "\\b(" + String.join("|", keyWords()) + ")\\b";
    }

    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/"   // for whole text processing (text blocks)
            + "|" + "/\\*[^\\v]*" + "|" + "^\\h*\\*([^\\v]*|/)";  // for visible paragraph processing (line by line)

    private final String[] keywords = new String[] {
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "record", "var" //追加
    };

    protected String[] keyWords() {
        return keywords;
    }

    public Pattern getPattern(SearchParams temporary, SearchParams search) {
        synchronized (LOCK) {
            var ans = paramsToPatterns(temporary, search);
            var tempPattern = ans[0];
            var searchPattern = ans[1];

            if (tempPattern == null && searchPattern == null) {
                return Pattern.compile(
                        "(?<KEYWORD>" + keyWordPattern() + ")"
                                + "|(?<PAREN>" + PAREN_PATTERN + ")"
                                + "|(?<BRACE>" + BRACE_PATTERN + ")"
                                + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                                + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                                + "|(?<STRING>" + STRING_PATTERN + ")"
                                + "|(?<COMMENT>" + COMMENT_PATTERN + ")");
            }

            if (tempPattern == null) { //tempPattern == null && searchPattern != null
                return Pattern.compile(
                        "(?<SEARCH>" + searchPattern + ")"
                                + "|(?<KEYWORD>" + keyWordPattern() + ")"
                                + "|(?<PAREN>" + PAREN_PATTERN + ")"
                                + "|(?<BRACE>" + BRACE_PATTERN + ")"
                                + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                                + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                                + "|(?<STRING>" + STRING_PATTERN + ")"
                                + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                );
            }

            if (searchPattern == null) { //tempPattern != null && searchPattern == null
                return Pattern.compile(
                        "(?<TEMPORARY>" + tempPattern + ")"
                                + "|(?<KEYWORD>" + keyWordPattern() + ")"
                                + "|(?<PAREN>" + PAREN_PATTERN + ")"
                                + "|(?<BRACE>" + BRACE_PATTERN + ")"
                                + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                                + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                                + "|(?<STRING>" + STRING_PATTERN + ")"
                                + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                );
            }

            //tempPattern != null && searchPattern != null
            return Pattern.compile(
                    "(?<TEMPORARY>" + tempPattern + ")"
                            + "|(?<SEARCH>" + searchPattern + ")"
                            + "|(?<KEYWORD>" + keyWordPattern() + ")"
                            + "|(?<PAREN>" + PAREN_PATTERN + ")"
                            + "|(?<BRACE>" + BRACE_PATTERN + ")"
                            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                            + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                            + "|(?<STRING>" + STRING_PATTERN + ")"
                            + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
            );
        }
    }

    @Override
    public Function<String, StyleSpans<Collection<String>>> getComputeHighlightFun() {
        return this::computeHighlighting;
    }

    private final HashMap<String, Set<String>> styleClassAndSetMap = new HashMap<>();

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (mLastMatcher == null) {
            synchronized (LOCK) {
                if (mLastMatcher == null) {
                    mLastMatcher = getPattern(null, null);
                }
            }
        }

        Matcher matcher = mLastMatcher.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder
                = new StyleSpansBuilder<>();
        while (matcher.find()) {
            //todo 其实可以更牛一点，采用temporary，search与关键字双重显示；不过比较复杂；需要多定义一些样式才行。
            String styleClass =
                    mIsTemporaryEnabled && matcher.group("TEMPORARY") != null ? "temporary" :
                            mIsSearchEnabled && matcher.group("SEARCH") != null ? "search" :
                                    matcher.group("KEYWORD") != null ? "keyword" :
                                            matcher.group("PAREN") != null ? "paren" :
                                                    matcher.group("BRACE") != null ? "brace" :
                                                            matcher.group("BRACKET") != null ? "bracket" :
                                                                    matcher.group("SEMICOLON") != null ? "semicolon" :
                                                                            matcher.group("STRING") != null ? "string" :
                                                                                    matcher.group("COMMENT") != null ? "comment" :
                                                                                            null; /* never happens */
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);

            if (styleClassAndSetMap.containsKey(styleClass)) {
                spansBuilder.add(styleClassAndSetMap.get(styleClass), matcher.end() - matcher.start());
            } else {
                var set = Collections.singleton(styleClass);
                styleClassAndSetMap.put(styleClass, set);
                spansBuilder.add(set, matcher.end() - matcher.start());
            }

            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        //Log.d(TAG, "compute Highlighting ");
        return spansBuilder.create();
    }

}

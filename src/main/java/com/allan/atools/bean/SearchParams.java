package com.allan.atools.bean;

import com.allan.atools.Colors;
import com.allan.baseparty.handler.TextUtils;
import org.jetbrains.annotations.NotNull;

/**
 * words;
 * filePaths
 * <p>
 * useCaseMatch;
 * useWholeWords;
 * type;
 * textColor;
 * bgColor;
 * enable;
 */
public class SearchParams {
    public enum Type {
        Normal,
        Regex,
        FakeRegex,
    }

    public String words;

    public boolean useCaseMatch = true;
    public boolean useWholeWords = true;
    public Type type = Type.Normal;

    public String textColor;
    public String bgColor;

    public boolean enable = true;

    public boolean highLight = false;

    public transient String[] filePaths;

    /**
     * 用于颜色显示。为false则会使用二级颜色
     */
    public transient boolean major = true;

    public int typeToIndex() {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case FakeRegex, Regex -> 1; //FakeRegex 2
            default -> 0;
        };
    }

    public void indexToType(int index) {
        switch (index) {
            case 0 -> type = Type.Normal;
            case 1, 2 -> type = Type.Regex;
            //2 -> fakeRegex
        }
    }

    public boolean isSameGeneric(SearchParams other) {
        if (other == null) {
            return false;
        }

        if (useCaseMatch != other.useCaseMatch) {
            return false;
        }

        if (useWholeWords != other.useWholeWords) {
            return false;
        }

        if (type != other.type) {
            return false;
        }

        if (!TextUtils.equalsAllowNullOrEmptyEqual(other.words, words)) {
            return false;
        }

        return true;
    }

    public SearchParams copy() {
        return getSearchParams(words, major, useWholeWords);
    }

    public SearchParams copy(String word) {
        return getSearchParams(word, major, useWholeWords);
    }

    public SearchParams copy(String word, boolean major, boolean useWholeWords) {
        return getSearchParams(word, major, useWholeWords);
    }

    @NotNull
    private SearchParams getSearchParams(String word, boolean major, boolean useWholeWords) {
        SearchParams copied = new SearchParams();

        copied.words = word;
        copied.major = major;
        copied.useWholeWords = useWholeWords;

        copied.useCaseMatch = useCaseMatch;
        copied.type = type;
        copied.textColor = textColor;
        copied.bgColor = bgColor;
        copied.enable = enable;
        copied.highLight = highLight;
        if (filePaths != null) {
            copied.filePaths = new String[filePaths.length];
            int i = 0;
            for (var filePath : filePaths) {
                copied.filePaths[i++] = filePath;
            }
        }
        return copied;
    }

    @Override
    public String toString() {
        return "SearchParams{" +
                words +
                ", caseMatch=" + useCaseMatch +
                ", wholeWords=" + useWholeWords +
                ", type=" + type +
                ", textColor='" + textColor + '\'' +
                ", bgColor='" + bgColor + '\'' +
                '}';
    }


    public static SearchParams generate(String words, String... filePaths) {
        return new Builder()
                .enable(true).textColor(Colors.SearchTextColor.invoke()).bgColor(Colors.SearchBgColor.invoke())
                .type(Type.Normal).useWholeWords(false).useCaseMatch(true)
                .words(words).filePaths(filePaths)
                .build();
    }

    public boolean isDefaultColors() {
        return Colors.SearchTextColor.invoke().equals(textColor) && Colors.SearchBgColor.invoke().equals(bgColor);
    }

    public static SearchParams generate(String words, boolean caseMatch, boolean wholeWords, Type type, String... filePaths) {
        return new Builder()
                .enable(true).textColor(Colors.SearchTextColor.invoke()).bgColor(Colors.SearchBgColor.invoke())
                .type(type).useWholeWords(wholeWords).useCaseMatch(caseMatch)
                .words(words).filePaths(filePaths)
                .build();
    }

    public static class Builder {
        SearchParams params;

        Builder() {
            init();
        }

        void init() {
            params = new SearchParams();
        }

        public Builder words(String words) {
            params.words = words;
            return this;
        }

        public Builder useCaseMatch(boolean caseMatch) {
            params.useCaseMatch = caseMatch;
            return this;
        }

        public Builder useWholeWords(boolean wholeWords) {
            params.useWholeWords = wholeWords;
            return this;
        }

        public Builder type(Type type) {
            params.type = type;
            return this;
        }

        public Builder textColor(String textColor) {
            params.textColor = textColor;
            return this;
        }

        public Builder bgColor(String bgColor) {
            params.bgColor = bgColor;
            return this;
        }

        public Builder enable(boolean enable) {
            params.enable = enable;
            return this;
        }

        public Builder highLight(boolean highlight) {
            params.highLight = highlight;
            return this;
        }

        public Builder filePaths(String... filePaths) {
            params.filePaths = filePaths;
            return this;
        }

        public SearchParams build() {
            return params;
        }
    }
}

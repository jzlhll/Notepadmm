package com.allan.atools.beans;

import com.allan.atools.Colors;
import com.allan.atools.bean.SearchParams;

public final class ReplaceParams extends SearchParams {
    public String replaceWords;

    public static ReplaceParams generate(String words, String replaceWords) {
        return new Builder()
                .enable(true).textColor(Colors.SearchTextColor.invoke()).bgColor(Colors.SearchBgColor.invoke())
                .type(Type.Normal).useWholeWords(false).useCaseMatch(true)
                .words(words).replaceWords(replaceWords)
                .build();
    }

    public static class Builder {
        ReplaceParams params;
        Builder() {
            init();
        }

        void init() {
            params = new ReplaceParams();
        }

        public Builder words(String words) {
            params.words = words;
            return this;
        }

        public Builder replaceWords(String replaceWords) {
            params.replaceWords = replaceWords;
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

        public ReplaceParams build() {
            return params;
        }
    }
}

package com.allan.atools.beans;

import com.allan.atools.bean.SearchParams;

public class ResultItem {
    public static class Range {
        public final int start;
        public final int end;
        public int totalOffset;

        public Range(int start, int end, int totalOffset) {
            this.start = start;
            this.end = end;
            this.totalOffset = totalOffset;
        }
    }

    public String matchWord;
    public Range range;

    public SearchParams searchParams;
}

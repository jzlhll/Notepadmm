package com.allan.atools.text;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.utils.Locales;
import com.allan.atools.bean.SearchParams;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractFinder implements IFinder{
    protected static class LineWrap {
        public String line;
        /**
         * 行在整体text的位置
         */
        public int offset;
    }

    protected final List<LineWrap> mLines;
    protected final String mFormat;
    protected final int mFormatLineNumOffset;
    protected final List<ResultItemWrap> mRetList;

    protected final boolean isSystemUseLineNum;

    private volatile boolean isStarted = true;
    protected boolean getIsStarted() {
        return !isStarted;
    }
    protected void setIsStarted(boolean s) {
        isStarted = s;
    }

    private static List<LineWrap> splits(String str) {
        int tmp = -1; //默认-1.后面有+1。相当于保证第一行不做分隔符拼接

        String[] lines = str.split("\n");
        List<LineWrap> res = new ArrayList<>(lines.length + 2);
        for (var line : lines) {
            var lineWrap = new LineWrap();
            lineWrap.offset = tmp + 1;
            lineWrap.line = line;
            tmp = lineWrap.offset + line.length();
            res.add(lineWrap);
        }
        return res;
    }

    public AbstractFinder(String text, boolean lineNum, SearchParams[] searchParams, int[] totalFileLineCount) {
        mLines = splits(text);

        totalFileLineCount[0] = mLines.size();

        int figures = ("" + mLines.size()).length() + 1;
        var line = Locales.str("line");
        line = "    " + line;
        mFormat = line + "%" + figures + "d: %s";
        mFormatLineNumOffset = line.length() + figures + 1 + 1;
        isSystemUseLineNum = lineNum;
        mRetList = new ArrayList<>();
    }

    public void cancel() {
        isStarted = false;
    }

}

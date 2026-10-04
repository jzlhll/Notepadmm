package com.allan.atools.text;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.utils.Locales;
import com.allan.atools.bean.SearchParams;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractFinder implements IFinder{
    protected static class LineWrap {
        public String line;
        public String caseInsensitiveLine;
        /**
         * 行在整体text的位置
         */
        public int offset;
    }

    protected final Iterable<LineWrap> mLines;
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
        var lines = splits(text);
        mLines = lines;

        totalFileLineCount[0] = lines.size();

        int figures = ("" + lines.size()).length() + 1;
        var line = Locales.str("line");
        line = "    " + line;
        mFormat = line + "%" + figures + "d: %s";
        mFormatLineNumOffset = line.length() + figures + 1 + 1;
        isSystemUseLineNum = lineNum;
        mRetList = new ArrayList<>();
    }

    /** 流式逐行读取，保留已有匹配与结果格式，避免拆分整篇正文。 */
    public AbstractFinder(java.io.Reader reader, boolean lineNum, SearchParams[] searchParams, int[] totalFileLineCount) {
        var input = new java.io.BufferedReader(reader, 32 * 1024);
        mLines = () -> new java.util.Iterator<>() {
            private String next;
            private boolean ready;
            private long offset;

            @Override
            public boolean hasNext() {
                if (Thread.currentThread().isInterrupted()) cancel();
                if (!isStarted) return false;
                if (!ready) {
                    try { next = input.readLine(); }
                    catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    ready = true;
                }
                return next != null;
            }

            @Override
            public LineWrap next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                if (offset > Integer.MAX_VALUE - next.length()) {
                    throw new IllegalStateException("搜索正文超过当前结果定位范围，请缩小文件后重试");
                }
                var line = new LineWrap();
                line.line = next;
                line.offset = (int) offset;
                offset += next.length() + 1L;
                totalFileLineCount[0]++;
                ready = false;
                next = null;
                return line;
            }
        };
        totalFileLineCount[0] = 0;
        String line = "    " + Locales.str("line");
        mFormat = line + "%6d: %s";
        mFormatLineNumOffset = line.length() + 8;
        isSystemUseLineNum = lineNum;
        mRetList = new ArrayList<>();
    }

    public void cancel() {
        isStarted = false;
    }

}

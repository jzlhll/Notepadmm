package com.allan.atools.text;

import com.allan.atools.utils.Locales;
import com.allan.atools.bean.SearchParams;

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
    protected final SearchResultList mRetList = new SearchResultList();

    protected final boolean isSystemUseLineNum;

    private volatile boolean isStarted = true;
    private java.util.function.BooleanSupplier cancelled = () -> false;

    public void setCancelled(java.util.function.BooleanSupplier cancelled) {
        this.cancelled = cancelled;
    }
    protected boolean getIsStarted() {
        return !isStarted || Thread.currentThread().isInterrupted() || cancelled.getAsBoolean();
    }
    protected void setIsStarted(boolean s) {
        isStarted = s;
    }

    public AbstractFinder(String text, boolean lineNum, SearchParams[] searchParams, int[] totalFileLineCount) {
        // 保留原先按 LF 拆行的坐标与尾部空行语义，只在遍历时创建当前行。
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\n') end--;
        final int last = end;
        int count = text.isEmpty() ? 1 : last == 0 ? 0 : 1;
        for (int i = 0; i < last; i++) if (text.charAt(i) == '\n') count++;
        totalFileLineCount[0] = count;
        mLines = () -> new java.util.Iterator<>() {
            private int offset;
            private boolean emptyPending = text.isEmpty();

            @Override
            public boolean hasNext() {
                return !getIsStarted() && !mRetList.getTruncated() && (emptyPending || offset < last);
            }

            @Override
            public LineWrap next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                int next = text.indexOf('\n', offset);
                if (next < 0 || next > last) next = last;
                var line = new LineWrap();
                line.offset = offset;
                if (next - offset > SearchResultList.MAX_LINE_CHARS) {
                    mRetList.truncate();
                    line.line = "";
                } else line.line = text.substring(offset, next);
                offset = next + 1;
                emptyPending = false;
                return line;
            }
        };
        int figures = Integer.toString(count).length() + 1;
        var line = "    " + Locales.str("line");
        mFormat = line + "%" + figures + "d: %s";
        mFormatLineNumOffset = line.length() + figures + 2;
        isSystemUseLineNum = lineNum;
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
                if (getIsStarted() || mRetList.getTruncated()) return false;
                if (!ready) {
                    try { next = SearchResultList.readLine(input, mRetList, () -> getIsStarted()); }
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
    }

    public void cancel() {
        isStarted = false;
    }

}

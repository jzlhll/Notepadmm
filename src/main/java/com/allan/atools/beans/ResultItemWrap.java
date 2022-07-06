package com.allan.atools.beans;

public final class ResultItemWrap {
    public enum LineMode {
        Real,
        FilePath
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getOrigLine() {
        return origLine;
    }

    public void setOrigLine(String origLine) {
        this.origLine = origLine;
    }

    private String line, origLine;

    public int lineNum;

    /**
     * 如果显示了line num；则会导致显示result的时候要偏移下
     */
    public int resultOffset;

    public LineMode lineMode = LineMode.Real;

    public ResultItem[] items;

    @Override
    public String toString() {
        return "Orig=" + origLine + ", line=" + line;
    }
}

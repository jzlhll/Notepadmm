package com.allan.atools.text;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.text.normal.FinderRegexImpl;
import com.allan.atools.bean.SearchParams;

import java.util.List;

public final class FinderFactory {
    private FinderFactory() {};

    private static volatile AbstractFinder mCurrentFindImpl;

    public static List<ResultItemWrap> find(String text, boolean lineNum, SearchParams[] searchParams, int[] totalFileLineCount) {
        return find(text, lineNum, searchParams, totalFileLineCount, () -> false);
    }

    public static List<ResultItemWrap> find(String text, boolean lineNum, SearchParams[] searchParams,
                                            int[] totalFileLineCount, java.util.function.BooleanSupplier cancelled) {
        AbstractFinder finder = new FinderRegexImpl(text, lineNum, searchParams, totalFileLineCount);
        finder.setCancelled(cancelled);
        mCurrentFindImpl = finder;
        try { return finder.find(); }
        finally { if (mCurrentFindImpl == finder) mCurrentFindImpl = null; }
    }

    public static synchronized void cancel() {
        var finder = mCurrentFindImpl;
        if (finder != null) {
            finder.cancel();
        }
    }

    public static List<ResultItemWrap> find(java.io.Reader reader, boolean lineNum, SearchParams[] searchParams, int[] totalFileLineCount) {
        AbstractFinder finder = new FinderRegexImpl(reader, lineNum, searchParams, totalFileLineCount);
        mCurrentFindImpl = finder;
        try { return finder.find(); }
        finally { if (mCurrentFindImpl == finder) mCurrentFindImpl = null; }
    }
}

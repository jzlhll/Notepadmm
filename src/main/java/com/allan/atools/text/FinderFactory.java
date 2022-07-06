package com.allan.atools.text;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.text.normal.FinderRegexImpl;
import com.allan.atools.bean.SearchParams;

import java.util.List;

public final class FinderFactory {
    private FinderFactory() {};

    private static AbstractFinder mCurrentFindImpl;

    public static List<ResultItemWrap> find(String text, boolean lineNum, SearchParams[] searchParams, int[] totalFileLineCount) {
        AbstractFinder f = new FinderRegexImpl(text, lineNum, searchParams, totalFileLineCount);
        mCurrentFindImpl = f;
        var ans = f.find();
        mCurrentFindImpl = null;
        return ans;
    }

    public static synchronized void cancel() {
        if (mCurrentFindImpl != null) {
            mCurrentFindImpl.cancel();
        }
    }
}

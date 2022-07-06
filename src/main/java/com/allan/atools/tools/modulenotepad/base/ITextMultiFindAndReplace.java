package com.allan.atools.tools.modulenotepad.base;

import com.allan.atools.text.beans.AllFilesSearchResults;
import com.allan.atools.beans.ReplaceParams;
import com.allan.atools.bean.SearchParams;
import com.allan.baseparty.Action;

public interface ITextMultiFindAndReplace {
    void multiFind(SearchParams params, Action<AllFilesSearchResults> action);
    StringBuilder multiReplace(ReplaceParams params);
}

package com.allan.atools.tools.modulenotepad.base;

import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.beans.ReplaceParams;
import com.allan.atools.bean.SearchParams;
import com.allan.baseparty.Action;

public interface ITextFindAndReplace {
    void find(SearchParams params, Action<OneFileSearchResults> action);
    void findAdvance(SearchParams[] params, Action<OneFileSearchResults> action);
    StringBuilder replace(ReplaceParams params);
}

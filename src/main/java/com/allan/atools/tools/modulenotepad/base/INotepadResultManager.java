package com.allan.atools.tools.modulenotepad.base;

import com.allan.atools.text.beans.AllFilesSearchResults;

public interface INotepadResultManager extends INotepadManager{
    void updateSearchLayout(AllFilesSearchResults r);
    void switchResultStyle(boolean splitOrNewWindow);
    void closeResult();
}

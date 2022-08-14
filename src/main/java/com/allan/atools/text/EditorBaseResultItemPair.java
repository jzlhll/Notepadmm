package com.allan.atools.text;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.richtext.codearea.EditorAreaMgr;

import java.lang.ref.WeakReference;

public class EditorBaseResultItemPair {
    public final WeakReference<EditorAreaMgr> areaEx;
    public final ResultItemWrap searchResultItem;
    public final int secondIndex;
    public EditorBaseResultItemPair(EditorAreaMgr area, ResultItemWrap item, int secondIndex) {
        areaEx = new WeakReference<>(area);
        searchResultItem = item;
        this.secondIndex = secondIndex;
    }

    public EditorBaseResultItemPair(WeakReference<EditorAreaMgr> areaRef, ResultItemWrap item, int secondIndex) {
        areaEx = areaRef;
        searchResultItem = item;
        this.secondIndex = secondIndex;
    }
}

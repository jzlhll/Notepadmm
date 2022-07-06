package com.allan.atools.text;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.richtext.codearea.EditorBase;

import java.lang.ref.WeakReference;

public class EditorBaseResultItemPair {
    public final WeakReference<EditorBase> areaEx;
    public final ResultItemWrap searchResultItem;
    public final int secondIndex;
    public EditorBaseResultItemPair(EditorBase area, ResultItemWrap item, int secondIndex) {
        areaEx = new WeakReference<>(area);
        searchResultItem = item;
        this.secondIndex = secondIndex;
    }

    public EditorBaseResultItemPair(WeakReference<EditorBase> areaRef, ResultItemWrap item, int secondIndex) {
        areaEx = areaRef;
        searchResultItem = item;
        this.secondIndex = secondIndex;
    }
}

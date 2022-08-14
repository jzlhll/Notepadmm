package com.allan.atools.text.beans;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.richtext.codearea.EditorAreaMgr;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * 泛型是Editor的泛型；不是result窗口的泛型
 */
public class OneFileSearchResults {
    public List<ResultItemWrap> results;
    public int totalLen;
    public File file;
    public WeakReference<EditorAreaMgr> area;

    public OneFileSearchResults addArea(EditorAreaMgr area) {
        this.area = new WeakReference<>(area);
        return this;
    }

    public OneFileSearchResults addResults(List<ResultItemWrap> results) {
        this.results = results;
        return this;
    }

    public OneFileSearchResults addFile(File file) {
        this.file = file;
        return this;
    }

    public OneFileSearchResults addTotalLen(int totalLen) {
        this.totalLen = totalLen;
        return this;
    }
}

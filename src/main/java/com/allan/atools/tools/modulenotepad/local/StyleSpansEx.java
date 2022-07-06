package com.allan.atools.tools.modulenotepad.local;

import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

public record StyleSpansEx(StyleSpans<Collection<String>> styleSpans, int startLineNum, int endLineNum, int areaStartPos) {
}

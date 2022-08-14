package com.allan.atools.richtext.codearea;

import javafx.scene.control.Tab;

import java.io.File;

public final class EditorBaseBuilder {
    public static EditorBase build(EditorAreaImpl area, File sourceFile, Tab tab, boolean isFake) {
        assert sourceFile != null;
        var shortcutType = EditorKeywordHelperFactory.sFilePathToExtension.invoke(sourceFile);
        if (shortcutType != null) {
            return new EditorBaseImplCode(area, sourceFile, tab, isFake);
        }

        return new EditorBase(area, sourceFile, tab, isFake);
    }
}

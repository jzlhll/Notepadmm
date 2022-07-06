package com.allan.atools.richtext.codearea;

public final class EditorKeywordHelperImplModuleInfo extends EditorKeywordHelperImplJava {
    private final String[] keywords = new String[] {
            "module", "requires", "exports", "opens", "to",
    };
    @Override
    protected String[] keyWords() {
        return keywords;
    }
}

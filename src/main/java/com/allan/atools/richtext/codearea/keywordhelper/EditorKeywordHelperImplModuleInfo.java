package com.allan.atools.richtext.codearea.keywordhelper;

public final class EditorKeywordHelperImplModuleInfo extends EditorKeywordHelperImplJava {
    private final String[] keywords = new String[] {
            "module", "open", "requires", "transitive", "static", "exports", "opens", "to",
            "uses", "provides", "with",
    };
    @Override
    protected String[] keyWords() {
        return keywords;
    }
}

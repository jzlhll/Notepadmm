package com.allan.atools.richtext.codearea.keywordhelper;

/**
 * Kotlin 代码关键字高亮。
 */
public final class EditorKeywordHelperImplKotlin extends EditorKeywordHelperImplJava {
    private final String[] keywords = new String[] {
            "as?", "as", "!in", "!is", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
            "try", "typealias", "typeof", "val", "var", "when", "while",
            "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import",
            "init", "param", "property", "receiver", "set", "setparam", "value", "where",
            "abstract", "actual", "annotation", "companion", "const", "crossinline", "data", "enum", "expect",
            "external", "final", "infix", "inline", "inner", "internal", "lateinit", "noinline", "open",
            "operator", "out", "override", "private", "protected", "public", "reified", "sealed", "suspend",
            "tailrec", "vararg", "it"
    };

    @Override
    protected String[] keyWords() {
        return keywords;
    }
}

package com.allan.atools.richtext.codearea;

public final class EditorKeywordHelperImplCC extends EditorKeywordHelperImplJava{
    private final String[] keywords = new String[] {
            "alignas",	"decltype",	"namespace",	"struct",
            "alignof",	"default",	"new",	"switch",
            "and",	"delete",	"noexcept",	"template",
            "and_eq",	"do",	"not",	"this",
            "asm",	"double",	"not_eq",	"thread_local",
            "auto",	"dynamic_cast",	"nullptr",	"throw",
            "bitand",	"else",	"operator",	"true",
            "bitor",	"enum",	"or",	"try",
            "bool",	"explicit",	"or_eq",	"typedef",
            "break",	"export",	"private",	"typeid",
            "case",	"extern",	"protected",	"typename",
            "catch",	"false",	"public",	"union",
            "char",	"float",	"register",	"unsigned",
            "char16_t",	"for",	"reinterpret_cast",	"using",
            "char32_t",	"friend",	"return",	"virtual",
            "class",	"goto",	"short",	"void",
            "compl",	"if",	"signed",	"volatile",
            "const",	"inline",	"sizeof",	"wchar_t",
            "constexpr",	"int",	"static",	"while",
            "const_cast",	"long",	"static_assert",	"xor",
            "continue",	"mutable",	"static_cast",	"xor_eq",
    };
    @Override
    protected String[] keyWords() {
        return keywords;
    }
}

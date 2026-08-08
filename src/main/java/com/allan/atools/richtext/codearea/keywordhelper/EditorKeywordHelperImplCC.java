package com.allan.atools.richtext.codearea.keywordhelper;

public final class EditorKeywordHelperImplCC extends EditorKeywordHelperImplJava{
    private final String[] keywords = new String[] {
            "#define",  "alignas",	"decltype",	"namespace",	"struct",
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
            "char8_t", "char16_t",	"for",	"reinterpret_cast",	"using",
            "char32_t",	"friend",	"return",	"virtual",
            "class",	"goto",	"short",	"void",
            "compl",	"if",	"signed",	"volatile",
            "const",	"inline",	"sizeof",	"wchar_t",
            "constexpr",	"int",	"static",	"while",
            "const_cast",	"long",	"static_assert",	"xor",
            "continue",	"mutable",	"static_cast",	"xor_eq",
            "concept", "consteval", "constinit", "co_await", "co_return", "co_yield", "requires",
            "restrict", "typeof", "typeof_unqual", "_Alignas", "_Alignof", "_Atomic", "_Bool", "_Complex",
            "_Generic", "_Imaginary", "_Noreturn", "_Static_assert", "_Thread_local",
    };
    @Override
    protected String[] keyWords() {
        return keywords;
    }
}

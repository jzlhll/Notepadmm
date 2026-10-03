package com.allan.atools.richtext.codearea.keywordhelper

import java.util.regex.Pattern

/** 常见围栏语言共用代码 token 类，字符串和注释优先保持完整。 */
object MarkdownCodeLanguages {
    @JvmStatic
    fun pattern(language: String): Pattern? {
        val words: String
        val comments: String
        val strings: String
        when (language) {
            "javascript", "typescript", "json" -> {
                words = if (language == "json") "true false null" else
                    "async await break case catch class const continue debugger default delete do else export extends false finally for from function if import in instanceof interface let new null of return static super switch this throw true try typeof undefined var void while with yield type implements private public protected readonly enum as"
                comments = if (language == "json") "(?!)" else "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"
                strings = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`"
            }
            "python" -> {
                words = "and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield"
                comments = "#[^\\r\\n]*"
                strings = "\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"
            }
            "shell" -> {
                words = "if then else elif fi for while do done case esac in function select until return export local echo printf read"
                comments = "#[^\\r\\n]*"
                strings = "\"(?:\\\\.|[^\"\\\\])*\"|'[^']*'"
            }
            "sql" -> {
                words = "select from where insert into update delete create alter drop table index view join inner left right outer on as and or not null is in exists values set group by order having limit offset distinct union all case when then else end with primary key foreign references begin commit rollback"
                comments = "--[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"
                strings = "'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\""
            }
            "yaml" -> {
                words = "true false null yes no on off"
                comments = "#[^\\r\\n]*"
                strings = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:''|[^'])*'"
            }
            else -> return null
        }
        val keywords = words.split(' ').joinToString("|") { Pattern.quote(it) }
        return Pattern.compile("(?<STRING>$strings)|(?<COMMENT>$comments)|(?<KEYWORD>\\b(?:$keywords)\\b)" +
            "|(?<PAREN>[()])|(?<BRACE>[{}])|(?<BRACKET>[\\[\\]])|(?<SEMICOLON>[;:,])",
            if (language == "sql" || language == "yaml") Pattern.CASE_INSENSITIVE else 0)
    }
}

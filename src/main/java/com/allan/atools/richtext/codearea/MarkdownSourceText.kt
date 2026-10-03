package com.allan.atools.richtext.codearea

/** 普通文字写入 Markdown 时统一保护标记和实体，表格另行保留边缘空格及制表符。 */
object MarkdownSourceText {
    fun escape(value: String, preserveEdgeSpaces: Boolean = false): String {
        val first = value.indexOfFirst { it != ' ' }
        val last = value.indexOfLast { it != ' ' }
        return buildString {
            value.forEachIndexed { index, character ->
                when {
                    character == '&' -> append("&amp;")
                    character == '\t' && preserveEdgeSpaces -> append("&#9;")
                    character == ' ' && preserveEdgeSpaces && (index < first || index > last) -> append("&#32;")
                    character in '!'..'/' || character in ':'..'@' || character in '['..'`' || character in '{'..'~' ->
                        append('\\').append(character)
                    else -> append(character)
                }
            }
        }
    }
}

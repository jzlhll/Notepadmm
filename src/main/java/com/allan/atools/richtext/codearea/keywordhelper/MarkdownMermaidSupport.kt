package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.FencedCodeBlock

/** 编辑区与完整预览共用 Mermaid 图表范围，具体语法由离线渲染库解析。 */
object MarkdownMermaidSupport {
    private val declaration = Regex("^(?:flowchart|graph|sequenceDiagram|gantt)(?:\\s|$)")

    fun isSupported(block: FencedCodeBlock): Boolean {
        if (!block.info.trim().substringBefore(' ').equals("mermaid", ignoreCase = true)) return false
        val lines = block.literal.removePrefix("\uFEFF").lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("%%") }.iterator()
        if (!lines.hasNext()) return false
        val opening = lines.next()
        // YAML 头部由 Mermaid 解析；这里只跳过已闭合的头部，继续识别图表声明。
        val firstLine = if (opening == "---") lines.asSequence().dropWhile { it != "---" }.drop(1).firstOrNull().orEmpty()
            else opening
        return declaration.containsMatchIn(firstLine)
    }
}

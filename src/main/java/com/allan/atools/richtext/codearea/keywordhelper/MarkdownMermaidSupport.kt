package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.FencedCodeBlock

/** 编辑区与完整预览共用 Mermaid 图表范围，具体语法由离线渲染库解析。 */
object MarkdownMermaidSupport {
    private val declaration = Regex("^(?:flowchart|graph|sequenceDiagram|gantt)(?:\\s|$)")

    fun isSupported(block: FencedCodeBlock): Boolean {
        if (!block.info.trim().substringBefore(' ').equals("mermaid", ignoreCase = true)) return false
        val firstLine = block.literal.removePrefix("\uFEFF").lineSequence().map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("%%") }.orEmpty()
        return declaration.containsMatchIn(firstLine)
    }
}

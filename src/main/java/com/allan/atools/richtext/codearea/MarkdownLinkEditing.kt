package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownAstCache
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownPlainText
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.utils.Locales
import javafx.scene.control.TextInputDialog
import org.commonmark.node.LinkReferenceDefinition

/** 正文和表格共用链接编辑与转义规则，引用式链接继承全文定义；调用方负责验证弹窗期间的文档状态。 */
object MarkdownLinkEditing {
    data class Edit(val start: Int, val end: Int, val text: String)

    @JvmStatic
    fun request(source: String, selectionStart: Int, selectionEnd: Int, caret: Int, document: MarkdownStructureSnapshot): Edit? {
        val state = if (document.text == source) document else {
            val definitions = document.elements.mapNotNull { it.node as? LinkReferenceDefinition }.joinToString("\n") { node ->
                node.sourceSpans.joinToString("\n") { document.text.substring(it.inputIndex, it.inputIndex + it.length) }
            }
            MarkdownAstCache().snapshot(source + "\n\n" + definitions)
        }
        if (state.intersectsLiteral(selectionStart, selectionEnd)) return null
        val existing = state.linkAt(caret)
        val dialog = TextInputDialog(existing?.destination ?: "https://").apply {
            title = Locales.str("markdown.editLink")
            headerText = Locales.str("markdown.linkAddress")
        }
        val destination = dialog.showAndWait().orElse(null) ?: return null
        val start = existing?.sourceSpans?.firstOrNull()?.inputIndex ?: selectionStart
        val end = existing?.sourceSpans?.lastOrNull()?.let { it.inputIndex + it.length } ?: selectionEnd
        val label = if (existing != null) {
            val first = existing.firstChild?.sourceSpans?.firstOrNull()
            val last = existing.lastChild?.sourceSpans?.lastOrNull()
            if (first != null && last != null) source.substring(first.inputIndex, last.inputIndex + last.length)
            else MarkdownPlainText.render(existing).trim()
        } else source.substring(selectionStart, selectionEnd).ifEmpty { Locales.str("markdown.linkLabel") }
            .replace("[", "\\[").replace("]", "\\]")
        val escaped = destination.trim().replace("<", "%3C").replace(">", "%3E").replace("\n", "").replace("\r", "")
        val title = existing?.title?.takeIf { it.isNotEmpty() }?.let { " \"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }.orEmpty()
        return Edit(start, end, if (escaped.isEmpty()) label else "[$label](<$escaped>$title)")
    }
}

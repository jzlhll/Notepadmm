package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorArea
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent

/** 从最新文档复制表格整行源码，保留 Markdown 标记、转义及原有间距。 */
fun copyMarkdownTableRow(area: EditorArea, tableId: String, rowIndex: Int) {
    val table = area.markdownTableDocumentState.tables.firstOrNull { it.id() == tableId && it.valid() }
        ?: return
    val row = table.rows().getOrNull(rowIndex) ?: return
    if (row.startOffset() < 0 || row.endOffset() < row.startOffset() || row.endOffset() > area.length) return

    val content = ClipboardContent()
    content.putString(area.getText(row.startOffset(), row.endOffset()))
    Clipboard.getSystemClipboard().setContent(content)
}

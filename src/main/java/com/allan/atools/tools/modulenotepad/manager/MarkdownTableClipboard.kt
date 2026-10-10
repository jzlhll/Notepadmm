package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.richtext.codearea.MarkdownTableParser
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState.Alignment
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DataFormat
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

enum class MarkdownTableCopyScope { ROW, COLUMN, TABLE }

private val tsvFormat = DataFormat.lookupMimeType("text/tab-separated-values") ?: DataFormat("text/tab-separated-values")
private val markdownCellsFormat = DataFormat.lookupMimeType("application/x-notepadmm-table-cells")
    ?: DataFormat("application/x-notepadmm-table-cells")

/** 从最新文档复制表格整行源码，保留 Markdown 标记、转义及原有间距。 */
fun copyMarkdownTableRow(area: EditorArea, tableId: String, rowIndex: Int) {
    copyMarkdownTableData(area, tableId, MarkdownTableCopyScope.ROW, rowIndex, 0, false)
}

/** Markdown 保留源码，TSV 使用格内可见文字；附加类型支持应用内多格往返。 */
fun copyMarkdownTableData(area: EditorArea, tableId: String, scope: MarkdownTableCopyScope,
                          rowIndex: Int, column: Int, tsv: Boolean) {
    com.allan.atools.richtext.codearea.MarkdownClipboard.cancelPendingCopy()
    val table = area.markdownTableDocumentState.tables.firstOrNull { it.id() == tableId && it.valid() }
        ?: return
    if (column !in table.alignments().indices || rowIndex !in table.rows().indices) return
    if (table.startOffset() < 0 || table.endOffset() > area.length) return
    val rows = if (scope == MarkdownTableCopyScope.ROW) listOf(table.rows()[rowIndex]) else table.rows()
    if (rows.any { it.startOffset() < 0 || it.endOffset() < it.startOffset() || it.endOffset() > area.length }) return
    val values = rows.map { row ->
        if (scope == MarkdownTableCopyScope.COLUMN) listOf(row.cells()[column].source())
        else row.cells().map { it.source() }
    }
    val content = ClipboardContent()
    if (tsv) {
        val rendered = HashMap<String, String>()
        val state = (area.editor as EditorAreaMgrCode).markdownSnapshot(area.text)
        MarkdownTableParser.parse(state.text, state.root) { source, node ->
            rendered[source] = if (source.isEmpty()) "" else MarkdownTableLayout.parse(node).joinToString("") { it.text() }
        } ?: return
        val text = serializeTsv(values.map { cells -> cells.map { rendered[it] ?: MarkdownTableCellText.decode(it) } })
        content.putString(text)
        content[tsvFormat] = text
    } else {
        val text = when (scope) {
            MarkdownTableCopyScope.ROW -> area.getText(rows[0].startOffset(), rows[0].endOffset())
            MarkdownTableCopyScope.TABLE -> area.getText(table.startOffset(), table.endOffset())
            MarkdownTableCopyScope.COLUMN -> buildList {
                add("| ${values[0][0]} |")
                val separator = when (table.alignments()[column]) {
                    Alignment.LEFT -> ":---"
                    Alignment.RIGHT -> "---:"
                    Alignment.CENTER -> ":---:"
                    else -> "---"
                }
                add("| $separator |")
                values.drop(1).forEach { add("| ${it[0]} |") }
            }.joinToString(table.lineEnding())
        }
        content.putString(text)
        val sourceCells = if (scope == MarkdownTableCopyScope.COLUMN) values
            else rows.map { row -> row.cells().map { it.source() } + row.extraCells() }
        content[markdownCellsFormat] = serializeTsv(sourceCells)
    }
    Clipboard.getSystemClipboard().setContent(content)
}

fun markdownClipboardCells(clipboard: Clipboard): List<List<String>>? {
    val value = clipboardText(clipboard, markdownCellsFormat) ?: return null
    return MarkdownTableEdits.parseTsv(value)
}

fun tableClipboardTsv(clipboard: Clipboard): String? = clipboardText(clipboard, tsvFormat)

fun hasTableCells(clipboard: Clipboard): Boolean =
    clipboard.hasContent(tsvFormat) || clipboard.hasContent(markdownCellsFormat)

// 外部应用可能只提供 MIME 数据，JavaFX 按平台返回字符串或 UTF-8 字节。
private fun clipboardText(clipboard: Clipboard, format: DataFormat): String? {
    val value = when (val content = clipboard.getContent(format)) {
        is String -> content
        is ByteBuffer -> StandardCharsets.UTF_8.decode(content.asReadOnlyBuffer()).toString()
        is ByteArray -> content.toString(StandardCharsets.UTF_8)
        else -> return null
    }
    return value.removePrefix("\uFEFF")
}

private fun serializeTsv(rows: List<List<String>>): String = rows.joinToString("\r\n") { cells ->
    cells.joinToString("\t") { value ->
        if (value.isEmpty() || value.any { it == '\t' || it == '\r' || it == '\n' || it == '"' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}

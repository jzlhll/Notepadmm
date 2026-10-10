package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.MarkdownTableDocumentState.Alignment
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState.Table

/** 表格结构变更先生成完整源码，再由主文档一次替换和撤销。 */
object MarkdownTableEdits {
    private const val MAX_ROWS = 1_000
    private const val MAX_COLUMNS = 100
    private const val MAX_CELLS = 10_000
    private const val MAX_SOURCE_LENGTH = 2 * 1024 * 1024
    data class Edit(val source: String, val row: Int, val column: Int)

    private data class Row(val prefix: String, val cells: MutableList<String>, val extra: List<String>)

    private class Content(val table: Table) {
        val alignments = table.alignments().toMutableList()
        val rows = table.rows().map { row ->
            Row(row.prefix(), row.cells().map { it.source() }.toMutableList(), row.extraCells())
        }.toMutableList()

        fun emptyRow() = Row(table.indent(), MutableList(alignments.size) { "" }, emptyList())

        fun result(row: Int, column: Int): Edit {
            val lines = ArrayList<String>()
            rows.forEachIndexed { index, value ->
                lines.add(value.prefix + (value.cells + value.extra).joinToString(" | ", "| ", " |"))
                if (index == 0) lines.add(table.indent() + alignments.joinToString(" | ", "| ", " |") {
                    when (it) {
                        Alignment.LEFT -> ":---"
                        Alignment.RIGHT -> "---:"
                        Alignment.CENTER -> ":---:"
                        else -> "---"
                    }
                })
            }
            return Edit(lines.joinToString(table.lineEnding()), row, column)
        }
    }

    @JvmStatic
    fun insertRow(table: Table, at: Int, column: Int): Edit? {
        if (at !in 1..table.rows().size) return null
        val content = Content(table)
        content.rows.add(at, content.emptyRow())
        return content.result(at, column)
    }

    @JvmStatic
    fun insertColumn(table: Table, at: Int, row: Int): Edit? {
        if (at !in 0..table.alignments().size) return null
        val content = Content(table)
        content.alignments.add(at, Alignment.DEFAULT)
        content.rows.forEach { it.cells.add(at, "") }
        return content.result(row, at)
    }

    @JvmStatic
    fun alignColumn(table: Table, row: Int, column: Int, alignment: Alignment): Edit? {
        if (column !in table.alignments().indices || table.alignments()[column] == alignment) return null
        val content = Content(table)
        content.alignments[column] = alignment
        return content.result(row, column)
    }

    @JvmStatic
    fun deleteRow(table: Table, row: Int, column: Int): Edit? {
        if (row !in 1 until table.rows().size) return null
        val content = Content(table)
        content.rows.removeAt(row)
        return content.result(row.coerceAtMost(content.rows.lastIndex), column)
    }

    @JvmStatic
    fun deleteColumn(table: Table, row: Int, column: Int): Edit? {
        if (table.alignments().size <= 1 || column !in table.alignments().indices) return null
        val content = Content(table)
        content.alignments.removeAt(column)
        content.rows.forEach { it.cells.removeAt(column) }
        return content.result(row, column.coerceAtMost(content.alignments.lastIndex))
    }

    @JvmStatic
    fun duplicateRow(table: Table, row: Int, column: Int): Edit? {
        if (row !in table.rows().indices) return null
        val content = Content(table)
        val original = content.rows[row]
        content.rows.add(row + 1, Row(table.indent(), original.cells.toMutableList(), original.extra))
        return content.result(row + 1, column)
    }

    @JvmStatic
    fun moveRow(table: Table, row: Int, column: Int, direction: Int): Edit? {
        val target = row + direction
        if (row !in 1 until table.rows().size || target !in 1 until table.rows().size) return null
        val content = Content(table)
        val moving = content.rows.removeAt(row)
        content.rows.add(target, moving)
        return content.result(target, column)
    }

    @JvmStatic
    fun moveColumn(table: Table, row: Int, column: Int, direction: Int): Edit? {
        val target = column + direction
        if (column !in table.alignments().indices || target !in table.alignments().indices) return null
        val content = Content(table)
        content.alignments.add(target, content.alignments.removeAt(column))
        content.rows.forEach { it.cells.add(target, it.cells.removeAt(column)) }
        return content.result(row, target)
    }

    @JvmStatic
    fun paste(table: Table, row: Int, column: Int, values: List<List<String>>): Edit? {
        return pasteValues(table, row, column, values, false)
    }

    @JvmStatic
    fun pasteMarkdown(table: Table, row: Int, column: Int, values: List<List<String>>): Edit? {
        return pasteValues(table, row, column, values, true)
    }

    private fun pasteValues(table: Table, row: Int, column: Int, values: List<List<String>>, markdown: Boolean): Edit? {
        if (row !in table.rows().indices || column !in table.alignments().indices || values.isEmpty()) return null
        val width = values.fold(0) { result, cells -> Math.max(result, cells.size) }
        val rows = Math.max(table.rows().size.toLong(), row.toLong() + values.size)
        val columns = Math.max(table.alignments().size.toLong(), column.toLong() + width)
        if (rows > MAX_ROWS || columns > MAX_COLUMNS || rows * columns > MAX_CELLS) return null
        // 在扩展矩阵前预算转义后的源码，分隔符、缩进和原有单元格也计入。
        var length = (rows + 1) * (columns * 7 + table.indent().length + table.lineEnding().length)
        table.rows().forEach { existing ->
            length += existing.prefix().length
            existing.cells().forEach { length += it.source().length }
            existing.extraCells().forEach { length += it.length + 3L }
        }
        if (length > MAX_SOURCE_LENGTH) return null
        val encoded = ArrayList<List<String>>(values.size)
        for (cells in values) {
            val next = ArrayList<String>(cells.size)
            for (value in cells) {
                if (value.length > MAX_SOURCE_LENGTH || length + value.length > MAX_SOURCE_LENGTH) return null
                val source = if (markdown) MarkdownTableCellText.encode(value, value) else encodePlainText(value)
                length += source.length
                if (length > MAX_SOURCE_LENGTH) return null
                next.add(source)
            }
            encoded.add(next)
        }
        val content = Content(table)
        while (content.alignments.size < column + width) {
            content.alignments.add(Alignment.DEFAULT)
            content.rows.forEach { it.cells.add("") }
        }
        while (content.rows.size < row + values.size) content.rows.add(content.emptyRow())
        encoded.forEachIndexed { y, cells ->
            cells.forEachIndexed { x, value ->
                content.rows[row + y].cells[column + x] = value
            }
        }
        return content.result(row, column)
    }

    // TSV 是显示文字，必须转义 Markdown，避免粘贴后变成样式、链接或 HTML。
    private fun encodePlainText(value: String): String =
        value.replace("\r\n", "\n").replace('\r', '\n').split('\n').joinToString("<br>") { line ->
            com.allan.atools.richtext.codearea.MarkdownSourceText.escape(line, preserveEdgeSpaces = true)
        }

    /** 支持电子表格中的引号、双引号转义和格内换行；行尾换行不额外生成空行。 */
    @JvmStatic
    fun parseTsv(text: String): List<List<String>>? {
        if (text.length > MAX_SOURCE_LENGTH) return null
        val rows = ArrayList<List<String>>()
        val cells = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var closedQuote = false
        var index = 0
        var rowEnded = false
        while (index < text.length) {
            val c = text[index]
            rowEnded = false
            if (quoted) {
                if (c == '"') {
                    if (text.getOrNull(index + 1) == '"') {
                        cell.append('"')
                        index++
                    } else {
                        quoted = false
                        closedQuote = true
                    }
                } else cell.append(c)
            } else when (c) {
                '\t' -> {
                    if (cells.size + 1 >= MAX_COLUMNS) return null
                    cells.add(cell.toString())
                    cell.setLength(0)
                    closedQuote = false
                }
                '\r', '\n' -> {
                    if (c == '\r' && text.getOrNull(index + 1) == '\n') index++
                    cells.add(cell.toString())
                    rows.add(cells.toList())
                    if (rows.size > MAX_ROWS) return null
                    cells.clear()
                    cell.setLength(0)
                    closedQuote = false
                    rowEnded = true
                }
                '"' -> {
                    if (cell.isEmpty() && !closedQuote) quoted = true
                    else if (closedQuote) return null
                    else cell.append(c)
                }
                else -> {
                    if (closedQuote) return null
                    cell.append(c)
                }
            }
            index++
        }
        if (quoted) return null
        if (!rowEnded) {
            cells.add(cell.toString())
            rows.add(cells.toList())
        }
        val width = rows.fold(0) { result, values -> Math.max(result, values.size) }
        if (rows.size > MAX_ROWS || rows.size.toLong() * width > MAX_CELLS) return null
        return rows
    }
}

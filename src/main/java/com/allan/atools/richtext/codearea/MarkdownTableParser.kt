package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.MarkdownTableDocumentState.*
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownAstCache
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.Node
import java.util.function.BiConsumer

/** AST 决定表格和行内语义；源码切片仅负责建立可逆的编辑范围。 */
object MarkdownTableParser {
    data class RowReplacement(val source: String, val row: Row)

    @JvmStatic
    fun materializeRow(original: Row, column: Int, value: String): RowReplacement {
        val text = StringBuilder(original.prefix())
        val cells = original.cells().mapIndexed { index, cell ->
            val source = if (index == column) value else cell.source()
            text.append("| ")
            val start = original.startOffset() + text.length
            text.append(source)
            val end = original.startOffset() + text.length
            text.append(' ')
            Cell(start, end, source, false)
        }
        original.extraCells().forEach { text.append("| ").append(it).append(' ') }
        text.append('|')
        return RowReplacement(text.toString(), Row(original.line(), original.startOffset(),
            original.startOffset() + text.length, original.header(), cells, original.prefix(), original.extraCells()))
    }

    @JvmStatic
    fun parse(text: String, inline: BiConsumer<String, Node>?): List<Table>? {
        val root = MarkdownAstCache().parse(text)
        val starts = ArrayList<Int>().apply {
            add(0)
            text.forEachIndexed { index, c -> if (c == '\n') add(index + 1) }
        }
        fun lineEnd(line: Int): Int {
            var end = if (line + 1 < starts.size) starts[line + 1] - 1 else text.length
            if (end > starts[line] && text[end - 1] == '\r') end--
            return end
        }
        val result = ArrayList<Table>()
        val pending = ArrayDeque<Node>().apply { add(root) }
        while (pending.isNotEmpty()) {
            if (Thread.currentThread().isInterrupted) return null
            val node = pending.removeLast()
            if (node !is TableBlock) {
                var child = node.lastChild
                while (child != null) {
                    pending.add(child)
                    child = child.previous
                }
                continue
            }
            val spans = node.sourceSpans
            if (spans.size < 2) continue
            val first = spans.first().lineIndex
            val last = spans.last().lineIndex
            // 非逐行源码映射保留原文，避免修改无法准确定位的容器。
            if (spans.size != last - first + 1) continue
            val rows = ArrayList<Row>()
            val alignments = ArrayList<Alignment>()
            var safe = true
            var section = node.firstChild
            while (section != null) {
                var rowNode = section.firstChild
                while (rowNode != null) {
                    if (rowNode is TableRow) {
                        val span = rowNode.sourceSpans.singleOrNull()
                        if (span == null) { safe = false; break }
                        val line = span.lineIndex
                        val lineStart = starts[line]
                        val end = lineEnd(line)
                        val contentStart = lineStart + span.columnIndex
                        val parts = splitCells(text, contentStart, end)
                        val cells = ArrayList<Cell>()
                        var cellNode = rowNode.firstChild
                        while (cellNode is TableCell) {
                            val part = parts.getOrNull(cells.size)
                            val cell = if (part == null) Cell(end, end, "", true)
                                else Cell(part.first, part.second, text.substring(part.first, part.second), false)
                            cells.add(cell)
                            inline?.accept(cell.source(), cellNode)
                            if (rows.isEmpty()) alignments.add(when (cellNode.alignment) {
                                TableCell.Alignment.LEFT -> Alignment.LEFT
                                TableCell.Alignment.CENTER -> Alignment.CENTER
                                TableCell.Alignment.RIGHT -> Alignment.RIGHT
                                else -> Alignment.DEFAULT
                            })
                            cellNode = cellNode.next
                        }
                        if (cells.isEmpty()) { safe = false; break }
                        rows.add(Row(line, lineStart, end, rows.isEmpty(), cells,
                            text.substring(lineStart, contentStart),
                            parts.drop(cells.size).map { text.substring(it.first, it.second) }))
                    }
                    rowNode = rowNode.next
                }
                section = section.next
            }
            if (!safe || rows.isEmpty()) continue
            val separator = spans[1]
            val prefix = text.substring(starts[separator.lineIndex], starts[separator.lineIndex] + separator.columnIndex)
            val ending = if (text.getOrNull(lineEnd(first)) == '\r') "\r\n" else "\n"
            result.add(Table(starts[first], lineEnd(last), first, last, rows, alignments, ending, prefix))
        }
        return result
    }

    private fun splitCells(text: String, begin: Int, end: Int): List<Pair<Int, Int>> {
        var start = begin
        while (start < end && (text[start] == ' ' || text[start] == '\t')) start++
        if (start < end && text[start] == '|') start++
        var stop = end
        while (stop > start && (text[stop - 1] == ' ' || text[stop - 1] == '\t')) stop--
        val parts = ArrayList<Pair<Int, Int>>()
        fun addCell(until: Int) {
            var left = start
            var right = until
            while (left < right && (text[left] == ' ' || text[left] == '\t')) left++
            while (right > left && (text[right - 1] == ' ' || text[right - 1] == '\t')) right--
            parts.add(left to right)
        }
        var index = start
        while (index < stop) {
            // 与 GFM 表格扩展一致：先消耗转义竖线，再处理行内代码。
            if (text[index] == '\\' && index + 1 < stop && text[index + 1] == '|') {
                index += 2
                continue
            }
            if (text[index] == '|') {
                addCell(index)
                start = index + 1
            }
            index++
        }
        if (start < stop || parts.isEmpty()) addCell(stop)
        return parts
    }
}

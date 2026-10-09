package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.MarkdownTableDocumentState

/** 格内撤销同步更新源码映射，结构变化仍交给完整解析。 */
object MarkdownTableUndoSupport {
    data class Target(val table: MarkdownTableDocumentState.Table, val row: Int, val column: Int)

    @JvmStatic
    fun findCell(state: MarkdownTableDocumentState, offset: Int): Target? {
        for (table in state.tables) {
            if (!table.valid() || table.mode() != MarkdownTableDocumentState.Mode.TABLE ||
                offset < table.startOffset() || offset > table.endOffset()) continue
            for ((rowIndex, row) in table.rows().withIndex()) {
                if (offset < row.startOffset() || offset > row.endOffset()) continue
                for ((column, cell) in row.cells().withIndex()) {
                    if (!cell.synthetic() && offset >= cell.startOffset() && offset <= cell.endOffset()) {
                        return Target(table, rowIndex, column)
                    }
                }
            }
        }
        return null
    }

    @JvmStatic
    fun applyCellChange(state: MarkdownTableDocumentState, position: Int, removed: String, inserted: String): Boolean {
        if ('\n' in removed || '\r' in removed || '\n' in inserted || '\r' in inserted) return false
        val target = findCell(state, position) ?: return false
        val cell = target.table.rows()[target.row].cells()[target.column]
        val offset = position - cell.startOffset()
        val source = cell.source()
        if (source.length != cell.endOffset() - cell.startOffset() || offset > source.length ||
            removed.length > source.length - offset || !source.regionMatches(offset, removed, 0, removed.length)) return false
        val replacement = source.replaceRange(offset, offset + removed.length, inserted)
        // 与表格分列规则一致；竖线或末尾反斜线可能改变列边界，不能沿用原映射。
        var index = 0
        while (index < replacement.length) {
            if (replacement[index] == '\\' && replacement.getOrNull(index + 1) == '|') index += 2
            else if (replacement[index++] == '|') return false
        }
        if (replacement.endsWith('\\')) return false
        state.applyCellChange(target.table.id(), target.row, target.column, position, removed, inserted)
        cell.setSource(replacement)
        return true
    }
}

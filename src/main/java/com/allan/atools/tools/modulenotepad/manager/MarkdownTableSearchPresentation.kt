package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState.Mode
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState.Table

/** 搜索命中的表格临时显示源码，不改变用户选择的表格模式或文档内容。 */
class MarkdownTableSearchPresentation {
    private var expanded = emptySet<String>()

    fun refresh(area: EditorArea, tables: List<Table>): Boolean {
        val next = tables.filter { table ->
            table.valid() && table.startOffset() >= 0 && table.startOffset() < table.endOffset() &&
                table.endOffset() <= area.length &&
                area.getStyleSpans(table.startOffset(), table.endOffset()).any { span ->
                    span.length > 0 && ("search" in span.style || "temporary" in span.style)
                }
        }.mapTo(HashSet()) { it.id() }
        if (expanded == next) return false
        expanded = next
        return true
    }

    fun isExpanded(table: Table): Boolean = table.id() in expanded

    fun mode(table: Table): Mode = if (isExpanded(table)) Mode.SOURCE else table.mode()

    fun clear() { expanded = emptySet() }
}

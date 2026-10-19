package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorArea

/** 协调 Markdown 表格的文档绑定、布局呈现与单元格编辑。 */
class MarkdownTablePreviewManager(area: EditorArea) {
    private val state = MarkdownTablePreviewState()

    init {
        state.session = MarkdownTablePreviewSession(state)
        state.rendering = MarkdownTableRendering(state)
        state.editing = MarkdownTableEditing(state)
        state.menus = MarkdownTableMenus(state)
        state.editing.configureCellEditor()
        state.session.refreshCurrentFile(area)
    }

    /** null 表示离开 Markdown 文档，仍须解绑并清理当前预览。 */
    fun refreshCurrentFile(area: EditorArea?) = state.session.refreshCurrentFile(area)
    fun destroy() = state.session.destroy()
}

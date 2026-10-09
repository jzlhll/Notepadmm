package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.UIContext
import com.allan.atools.controllerwindow.NotepadFindWindow
import com.allan.atools.richtext.codearea.MarkdownEditorSupport

/** Markdown 仅使用底部搜索，统一处理标签切换、文件类型变化及窗口快捷入口。 */
object MarkdownSearchSupport {
    @JvmStatic
    fun refresh() {
        val markdown = MarkdownEditorSupport.supportsMarkdown(UIContext.currentAreaProp.get())
        UIContext.context().notepadMainActionBarSearchBtn.isDisable = markdown
        if (markdown) NotepadFindWindow.getInstance().window?.hide()
    }

    @JvmStatic
    fun focusBottom(selectedText: String?) {
        val field = UIContext.context().bottomSearchTextField
        if (!selectedText.isNullOrEmpty()) field.text = selectedText
        field.requestFocus()
    }
}

package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.utils.Locales
import com.allan.atools.ui.SnackbarUtils
import javafx.scene.control.ChoiceDialog
import javafx.scene.control.TextInputDialog
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import org.commonmark.node.FencedCodeBlock

/** 围栏和表格入口操作保留源码位置，使用主文档撤销链。 */
class MarkdownCodeActions(private val area: EditorArea) {
    private fun state() = area.markdownCommands.snapshot()
    private fun replace(start: Int, end: Int, text: String, caret: Int) {
        if (!area.isEditable || area.markdownComposing) return
        area.undoManager.preventMerge()
        area.replaceText(start, end, text)
        area.moveTo(caret)
        area.undoManager.preventMerge()
        area.requestFollowCaret()
    }
    private fun block(): FencedCodeBlock? {
        if (area.editor.isRealtimeProcessingLimitReached) return null
        return state().elements.firstOrNull {
            it.node is FencedCodeBlock && it.ranges.any { range -> area.caretPosition in range.start..range.end }
        }?.node as? FencedCodeBlock
    }

    fun create() {
        if (area.markdownCommands.defer { create() }) return
        if (!area.markdownEditing.canFormat(true)) return
        val start = area.selection.start
        val end = area.selection.end
        val selected = area.selectedText
        val ticks = Math.max(3, (Regex("`+").findAll(selected).map { it.value.length }.maxOrNull() ?: 0) + 1)
        val fence = "`".repeat(ticks)
        val leading = if (start > 0 && area.getText(start - 1, start) != "\n") "\n" else ""
        val trailing = if (end < area.length && area.getText(end, end + 1) != "\n") "\n" else ""
        val value = "$leading$fence\n$selected\n$fence$trailing"
        replace(start, end, value, start + leading.length + fence.length + 1)
    }

    fun completeFence(state: MarkdownStructureSnapshot, line: MarkdownStructureSnapshot.Line): Boolean {
        if (area.selection.length > 0 || area.caretPosition != line.end) return false
        val node = state.elements.firstOrNull { it.node is FencedCodeBlock && it.node.sourceSpans.firstOrNull()?.lineIndex == state.lineAt(line.start) }
            ?.node as? FencedCodeBlock ?: return false
        if (node.closingFenceLength != null || node.literal.isNotBlank()) return false
        val source = area.getText(line.start, line.end)
        val marker = Regex("(?:`{3,}|~{3,})").find(source) ?: return false
        val prefix = continuationPrefix(source.substring(0, marker.range.first))
        val insertion = "\n$prefix\n$prefix${marker.value}"
        replace(line.end, line.end, insertion, line.end + prefix.length + 1)
        return true
    }

    fun chooseLanguage() {
        if (area.markdownCommands.defer { chooseLanguage() }) return
        val node = block() ?: return
        val before = area.text
        val options = listOf("text") + com.allan.atools.richtext.codearea.keywordhelper.MarkdownCodeLanguages.supportedLanguages() + "mermaid"
        val dialog = ChoiceDialog(node.info.trim().ifEmpty { "text" }, options)
        dialog.title = Locales.str("markdown.codeLanguage")
        dialog.headerText = Locales.str("markdown.codeLanguage")
        val language = dialog.showAndWait().orElse(null) ?: return
        if (before != area.text) return
        val span = node.sourceSpans.first()
        val raw = area.getText(span.inputIndex, span.inputIndex + span.length)
        val marker = Regex("(?:`{3,}|~{3,})").find(raw) ?: return
        val from = span.inputIndex + marker.range.last + 1
        replace(from, span.inputIndex + span.length, if (language == "text") "" else language, from)
    }

    fun copyCode() {
        if (area.markdownCommands.defer { copyCode() }) return
        val node = block() ?: return
        Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(node.literal) })
    }

    fun continuationPrefix(raw: String) = Regex("(?:^|(?<=[ \t>]))(?:[-+*]|[0-9]{1,9}[.)])([ \t]+)")
        .replace(raw) { match -> match.value.map { if (it == '\t') '\t' else ' ' }.joinToString("") }

    fun exitCode() {
        if (area.markdownCommands.defer { exitCode() }) return
        val node = block() ?: return
        val snapshot = state()
        val first = node.sourceSpans.first()
        val opening = area.getText(snapshot.lines[first.lineIndex].start, snapshot.lines[first.lineIndex].end)
        val fence = Regex("(?:`{3,}|~{3,})").find(opening) ?: return
        val prefix = continuationPrefix(opening.substring(0, fence.range.first))
        val end = node.sourceSpans.last().let { it.inputIndex + it.length }
        val value = if (node.closingFenceLength == null) "\n$prefix${node.fenceCharacter.repeat(node.openingFenceLength)}\n$prefix\n$prefix" else "\n$prefix\n$prefix"
        replace(end, end, value, end + value.length)
    }

    fun createTable() {
        if (area.markdownCommands.defer { createTable() }) return
        if (!area.markdownEditing.canFormat(true)) return
        val sourceBeforeDialog = area.text
        val dialog = TextInputDialog("3,3")
        dialog.title = Locales.str("markdown.createTable")
        dialog.headerText = Locales.str("markdown.tableDimensions")
        val value = dialog.showAndWait().orElse(null) ?: return
        if (area.text != sourceBeforeDialog) return
        val dimensions = value.split(',').map { it.trim().toIntOrNull() }
        if (dimensions.size != 2 || dimensions.any { it == null || it < 1 || it > 50 }) {
            SnackbarUtils.show(Locales.str("markdown.tableDimensionsInvalid")); return
        }
        val rows = dimensions[0]!!
        val columns = dimensions[1]!!
        val row = "|" + "   |".repeat(columns)
        val rule = "|" + " --- |".repeat(columns)
        val start = area.selection.start
        val end = area.selection.end
        val leading = if (start > 0) "\n\n" else ""
        val result = leading + (listOf(row, rule) + List(rows - 1) { row }).joinToString("\n") + "\n\n"
        replace(start, end, result, start + leading.length + 2)
    }
}

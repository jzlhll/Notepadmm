package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.utils.Locales
import com.allan.uilibs.richtexts.CodeArea
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent

/** Markdown 结构操作只修改主文档，每个用户操作形成独立的撤销边界。 */
class MarkdownEditingActions(private val area: EditorArea) {
    private val quote = Regex("^[ \\t]*(?:>[ \\t]?)+[ \\t]*")
    private val item = Regex("^([ \\t]*)([-+*]|[0-9]{1,9}[.)])([ \\t]+)(\\[[ xX]][ \\t]+)?")

    private fun snapshot(): MarkdownStructureSnapshot =
        (area.editor as EditorAreaMgrCode).markdownSnapshot(area.text)

    private fun change(start: Int, end: Int, replacement: String, selectStart: Int, selectEnd: Int = selectStart) {
        if (!area.isEditable || area.markdownComposing) return
        area.undoManager.preventMerge()
        area.replaceText(start, end, replacement)
        area.selectRange(selectStart, selectEnd)
        area.undoManager.preventMerge()
        area.requestFollowCaret()
    }

    fun canFormat(block: Boolean = false, inlineCode: Boolean = false): Boolean {
        if (!MarkdownEditorSupport.supportsMarkdown(area) || !area.isEditable || area.markdownComposing
            || area.editor.isRealtimeProcessingLimitReached) return false
        val state = snapshot()
        if (if (block) state.intersectsLiteralBlocks(area.selection.start, area.selection.end)
            else state.intersectsLiteral(area.selection.start, area.selection.end, inlineCode)) {
            com.allan.atools.ui.SnackbarUtils.show(Locales.str("markdown.literalFormat"))
            return false
        }
        return true
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!MarkdownEditorSupport.supportsMarkdown(area) || area.markdownComposing) return false
        val shortcut = MarkdownShortcuts.match(event)
        if (shortcut != null) {
            if (shortcut == MarkdownShortcuts.SOURCE) {
                area.toggleMarkdownPreview()
                return true
            }
            if (shortcut == MarkdownShortcuts.PASTE_PLAIN) {
                if (area.isEditable) area.markdownClipboard.paste(true)
                return true
            }
            // 降级和只读状态仍消费已识别的格式键，避免落入其他编辑器默认行为。
            if (!area.isEditable || area.editor.isRealtimeProcessingLimitReached) return true
            when (shortcut) {
                MarkdownShortcuts.BOLD -> wrap("**")
                MarkdownShortcuts.ITALIC -> wrap("*")
                MarkdownShortcuts.INLINE_CODE -> wrap("`")
                MarkdownShortcuts.LINK -> editLink()
                MarkdownShortcuts.STRIKE -> wrap("~~")
                MarkdownShortcuts.PASTE_PLAIN -> area.markdownClipboard.paste(true)
                MarkdownShortcuts.QUOTE -> prefixLines("> ")
                MarkdownShortcuts.LIST -> prefixLines("- ")
                else -> heading(shortcut.heading)
            }
            return true
        }
        if (!area.isEditable || area.editor.isRealtimeProcessingLimitReached) return false
        if (event.isAltDown || event.isControlDown || event.isMetaDown) return false
        if (event.code != KeyCode.ENTER && event.code != KeyCode.TAB && event.code != KeyCode.BACK_SPACE) return false
        if (event.code == KeyCode.TAB) {
            indent(event.isShiftDown)
            return true
        }
        // 普通退格不依赖全文结构，只有命中列表或引用前缀边界时才查询 AST。
        if (event.code == KeyCode.BACK_SPACE) {
            if (area.selection.length > 0) return false
            val paragraph = area.getParagraph(area.currentParagraph).text
            val quotes = quote.find(paragraph)?.value.orEmpty()
            val marker = item.find(paragraph.substring(quotes.length))
            val prefixEnd = quotes.length + (marker?.value?.length ?: 0)
            if (prefixEnd == 0 || area.caretPosition - area.getAbsolutePosition(area.currentParagraph, 0) != prefixEnd) return false
        }
        val state = snapshot()
        val line = state.lines[state.lineAt(area.caretPosition)]
        if ((line.frontMatter || state.isLiteral(area.caretPosition)) && !line.code) return false
        when (event.code) {
            KeyCode.ENTER -> {
                if (event.isShiftDown && !line.code) {
                    val prefix = quote.find(area.getText(line.start, line.end))?.value.orEmpty()
                    change(area.selection.start, area.selection.end, "  \n$prefix", area.selection.start + 3 + prefix.length)
                    return true
                }
                if (!event.isShiftDown) return enter(state, line)
            }
            KeyCode.BACK_SPACE -> if (area.selection.length == 0 && !line.code) {
                val content = area.getText(line.start, line.end)
                val quotes = quote.find(content)?.value.orEmpty()
                val marker = if (line.listDepth > 0) item.find(content.substring(quotes.length)) else null
                val end = quotes.length + (marker?.value?.length ?: 0)
                if (end > 0 && area.caretPosition == line.start + end) {
                    val from = if (marker != null) line.start + quotes.length else line.start + quotes.lastIndexOf('>')
                    change(from, line.start + end, "", from)
                    return true
                }
            }
            else -> Unit
        }
        return false
    }

    private fun enter(state: MarkdownStructureSnapshot, line: MarkdownStructureSnapshot.Line): Boolean {
        val content = area.getText(line.start, line.end)
        val quotes = if (line.quoteDepth > 0) quote.find(content)?.value.orEmpty() else ""
        if (line.code) {
            if (area.markdownCodeActions.completeFence(state, line)) return true
            val opening = state.elements.firstOrNull {
                it.node is org.commonmark.node.FencedCodeBlock && it.node.sourceSpans.firstOrNull()?.lineIndex == state.lineAt(line.start)
            }?.node?.sourceSpans?.firstOrNull()
            val prefix = if (opening != null) {
                val fence = Regex("(?:`{3,}|~{3,})").find(content, opening.inputIndex - line.start)
                area.markdownCodeActions.continuationPrefix(content.substring(0, fence?.range?.first ?: content.length))
            } else quotes + Regex("^[ \\t]*").find(content.substring(quotes.length))!!.value
            change(area.selection.start, area.selection.end, "\n$prefix", area.selection.start + prefix.length + 1)
            return true
        }
        if (area.markdownPreviewEnabled && line.heading > 0 && area.selection.length == 0 && area.caretPosition == line.end) {
            val heading = state.elements.firstOrNull { it.node is org.commonmark.node.Heading && it.ranges.any { range -> line.end in range.start..range.end } }
            val spans = heading?.node?.sourceSpans
            if (spans != null && spans.last().lineIndex > state.lineAt(line.end)) {
                val end = state.lines[spans.last().lineIndex].end
                val openingLine = state.lines[spans.first().lineIndex]
                val openingSource = area.getText(openingLine.start, openingLine.end)
                val container = quote.find(openingSource)?.value.orEmpty()
                val prefix = container + " ".repeat(Math.max(0, spans.first().inputIndex - openingLine.start - container.length))
                change(end, end, "\n$prefix", end + prefix.length + 1)
                return true
            }
        }
        val direct = if (line.listDepth > 0) item.find(content.substring(quotes.length)) else null
        val owner = if (line.listDepth > 0) state.elements.lastOrNull {
            it.node is org.commonmark.node.ListItem && it.ranges.any { range -> area.caretPosition in range.start..range.end }
        }?.node else null
        val opening = owner?.sourceSpans?.firstOrNull()?.lineIndex?.let { state.lines[it] }
            ?.let { area.getText(it.start, it.end) }
        val marker = direct ?: opening?.let { item.find(it.substring(quote.find(it)?.value.orEmpty().length)) }
        if (marker != null) {
            val start = line.start + quotes.length
            val bodyStart = start + (direct?.value?.length ?: Regex("^[ \t]*").find(content.substring(quotes.length))!!.value.length)
            if (content.substring(bodyStart - line.start).isBlank()) {
                var parent = owner?.parent
                while (parent != null && parent !is org.commonmark.node.ListItem) parent = parent.parent
                val parentLine = parent?.sourceSpans?.firstOrNull()?.lineIndex?.let { state.lines[it] }
                    ?.let { area.getText(it.start, it.end) }
                val parentContainer = parentLine?.let { quote.find(it)?.value }.orEmpty()
                val parentMarker = parentLine?.let { item.find(it.substring(parentContainer.length)) }
                if (parentMarker == null) change(start, line.end, "", start)
                else {
                    val token = parentMarker.groupValues[2]
                    val number = if (token[0].isDigit()) token.dropLast(1).toLong() + 1 else 0
                    val next = if (number in 1..999_999_999) "$number${token.last()}" else token
                    val prefix = parentContainer + parentMarker.groupValues[1] + next + parentMarker.groupValues[3] +
                        if (parentMarker.groupValues[4].isEmpty()) "" else "[ ] "
                    change(line.start, line.end, prefix, line.start + prefix.length)
                }
            } else {
                val token = marker.groupValues[2]
                val next = if (token[0].isDigit()) {
                    val number = token.dropLast(1).toLong() + 1
                    if (number <= 999_999_999) "$number${token.last()}" else token
                } else token
                val container = if (quotes.isNotEmpty() || line.quoteDepth == 0) quotes else opening?.let { quote.find(it)?.value }.orEmpty()
                val prefix = container + marker.groupValues[1] + next + marker.groupValues[3] +
                    if (marker.groupValues[4].isEmpty()) "" else "[ ] "
                if (area.selection.start < bodyStart) return false
                change(area.selection.start, area.selection.end, "\n$prefix", area.selection.start + 1 + prefix.length)
            }
            return true
        }
        if (line.quoteDepth > 0) {
            val prefix = if (quotes.isEmpty()) "> ".repeat(line.quoteDepth) else quotes
            if (content.substring(quotes.length).isBlank()) {
                val from = line.start + quotes.lastIndexOf('>')
                if (from >= line.start) change(from, line.end, "", from)
            } else change(area.selection.start, area.selection.end, "\n$prefix", area.selection.start + prefix.length + 1)
            return true
        }
        return false
    }

    fun toggleTask(event: MouseEvent): Boolean {
        if (!area.isEditable || area.markdownComposing || !area.markdownPreviewEnabled
            || !MarkdownEditorSupport.supportsMarkdown(area) || area.editor.isRealtimeProcessingLimitReached) return false
        val hit = area.hit(event.x, event.y).characterIndex
        if (!hit.isPresent) return false
        // 点击正文不获取全文快照；只有确实命中已绘制的任务标记才继续。
        val hitStyles = area.getStyleOfChar(hit.asInt)
        if (CodeArea.MARKDOWN_TASK_MARKER_CLASS !in hitStyles || "markdown-task-example" in hitStyles) return false
        val state = snapshot()
        val offset = state.lines[state.lineAt(hit.asInt)].taskOffset
        if (offset < 0 || hit.asInt !in offset - 1..offset + 1) return false
        val start = area.selection.start
        val end = area.selection.end
        val styles = area.getStyleOfChar(offset - 1)
        area.undoManager.preventMerge()
        area.replaceText(offset, offset + 1, if (area.getText(offset, offset + 1) == " ") "x" else " ")
        // 字符插入会使用默认样式，立即恢复整段标记，使复选框状态无需等待后台高亮。
        if (CodeArea.MARKDOWN_TASK_MARKER_CLASS in styles) area.setStyle(offset - 1, offset + 2, styles)
        area.selectRange(start, end)
        area.undoManager.preventMerge()
        return true
    }

    fun wrap(mark: String) {
        if (!canFormat(inlineCode = mark == "`")) return
        val start = area.selection.start
        val end = area.selection.end
        if (mark != "`") {
            wrapEmphasis(mark)
            return
        }
        val edit = MarkdownInlineCode.toggle(area.text, start, end)
        change(edit.start, edit.end, edit.text, edit.selectionStart, edit.selectionEnd)
    }

    private fun wrapEmphasis(mark: String) {
        val start = area.selection.start
        val end = area.selection.end
        if (start == end) {
            val length = mark.length
            if (start >= length && end + length <= area.length && area.getText(start - length, start) == mark && area.getText(end, end + length) == mark)
                change(start - length, end + length, "", start - length)
            else change(start, end, mark + mark, start + length)
            return
        }
        val state = snapshot()
        val wrappers = state.elements.filter {
            when (mark) {
                "**" -> it.node is org.commonmark.node.StrongEmphasis
                "*" -> it.node is org.commonmark.node.Emphasis
                "~~" -> it.node is org.commonmark.ext.gfm.strikethrough.Strikethrough
                else -> false
            }
        }.mapNotNull { entry ->
            val first = entry.ranges.firstOrNull() ?: return@mapNotNull null
            val last = entry.ranges.last()
            MarkdownStructureSnapshot.Range(first.start, last.end)
        }
        fun wrapper(from: Int, to: Int) = wrappers.firstOrNull {
            from in it.start..(it.start + mark.length) && to in (it.end - mark.length)..it.end
        }
        // 已有跨行强调只解除自身分隔符，保留容器前缀和原有换行。
        wrapper(start, end)?.let {
            val body = state.text.substring(it.start + mark.length, it.end - mark.length)
            change(it.start, it.end, body, it.start, it.start + body.length)
            return
        }
        val parts = ArrayList<Pair<MarkdownStructureSnapshot.Range, MarkdownStructureSnapshot.Range?>>()
        for (entry in state.elements) {
            if (entry.node !is org.commonmark.node.Paragraph && entry.node !is org.commonmark.node.Heading) continue
            val spans = ArrayList<org.commonmark.node.SourceSpan>()
            var child = entry.node.firstChild
            while (child != null) { spans.addAll(child.sourceSpans); child = child.next }
            if (spans.isEmpty()) continue
            val contentStart = spans.first().inputIndex
            val contentEnd = spans.last().let { it.inputIndex + it.length }
            for (span in entry.node.sourceSpans) {
                var from = Math.max(start, Math.max(contentStart, span.inputIndex))
                val line = state.lines[span.lineIndex]
                if (line.taskOffset >= 0) from = Math.max(from, line.taskOffset + 2)
                var to = Math.min(end, Math.min(contentEnd, span.inputIndex + span.length))
                while (from < to && state.text[from].isWhitespace()) from++
                while (to > from && state.text[to - 1].isWhitespace()) to--
                if (from < to) parts.add(MarkdownStructureSnapshot.Range(from, to) to wrapper(from, to))
            }
        }
        if (parts.isEmpty()) return
        val remove = parts.all { it.second != null }
        val edits = parts.mapNotNull { (range, existing) ->
            if (remove && existing != null) Triple(existing.start, existing.end,
                state.text.substring(existing.start + mark.length, existing.end - mark.length))
            else if (existing == null) Triple(range.start, range.end, mark + state.text.substring(range.start, range.end) + mark)
            else null
        }.sortedBy { it.first }
        if (edits.isEmpty()) return
        val from = Math.min(start, edits.first().first)
        val to = Math.max(end, edits.last().second)
        val result = StringBuilder(state.text.substring(from, to))
        edits.asReversed().forEach { (begin, finish, value) -> result.replace(begin - from, finish - from, value) }
        if (parts.size == 1) {
            val edit = edits.first()
            val selectedStart = edit.first + if (remove) 0 else mark.length
            val selectedEnd = edit.first + edit.third.length - if (remove) 0 else mark.length
            change(from, to, result.toString(), selectedStart, selectedEnd)
        } else change(from, to, result.toString(), from, from + result.length)
    }

    private fun lineRange(): Pair<Int, Int> {
        val source = area.text
        val start = area.selection.start
        val end = area.selection.end
        val effective = if (end > start && source[end - 1] == '\n') end - 1 else end
        val from = source.lastIndexOf('\n', start - 1) + 1
        val next = source.indexOf('\n', effective)
        return from to if (next < 0) source.length else next
    }

    fun prefixLines(prefix: String) {
        if (!canFormat(true)) return
        val (start, end) = lineRange()
        val state = snapshot()
        val original = area.getText(start, end).split('\n')
        if (prefix == "> ") {
            val remove = original.withIndex().all { (index, source) -> source.isBlank() || state.lines[state.lineAt(start) + index].quoteDepth > 0 && quote.find(source) != null }
            val result = original.joinToString("\n") { source ->
                if (remove) source.replaceFirst(Regex("^([ \\t]*)>[ \\t]?"), "$1") else prefix + source
            }
            change(start, end, result, start, start + result.length)
            return
        }
        val markers = original.mapIndexed { index, source ->
            val line = state.lines[state.lineAt(start) + index]
            val container = quote.find(source)?.value.orEmpty()
            if (line.listDepth > 0 && !line.code) item.find(source.substring(container.length)) else null
        }
        fun matches(marker: MatchResult?): Boolean = marker != null && when (prefix) {
            "1. " -> marker.groupValues[2][0].isDigit() && marker.groupValues[4].isEmpty()
            "- [ ] " -> marker.groupValues[4].isNotEmpty()
            else -> !marker.groupValues[2][0].isDigit() && marker.groupValues[4].isEmpty()
        }
        val remove = original.indices.all { original[it].isBlank() || matches(markers[it]) }
        val result = original.mapIndexed { index, source ->
            val container = quote.find(source)?.value.orEmpty()
            val rest = source.substring(container.length)
            val marker = markers[index]
            val indentation = marker?.groupValues?.get(1) ?: Regex("^[ \\t]*").find(rest)!!.value
            val body = if (marker != null) rest.substring(marker.value.length) else rest.substring(indentation.length)
            val token = when {
                remove -> ""
                prefix == "1. " && marker != null && marker.groupValues[2][0].isDigit() -> marker.groupValues[2] + " "
                prefix == "- [ ] " && marker != null && marker.groupValues[4].isNotEmpty() -> "- " + marker.groupValues[4]
                else -> prefix
            }
            container + indentation + token + body
        }.joinToString("\n")
        change(start, end, result, start, start + result.length)
    }

    fun heading(level: Int) {
        if (level !in 0..6 || !canFormat(true)) return
        var (start, end) = lineRange()
        val state = snapshot()
        val entries = state.elements.filter { it.node is org.commonmark.node.Heading }
        val setext = entries.filter { entry ->
            val spans = entry.node.sourceSpans
            spans.size > 1 && entry.ranges.any { it.start <= end && it.end >= start } &&
                area.getText(spans.last().inputIndex, spans.last().inputIndex + spans.last().length).trim().matches(Regex("[=-]+"))
        }
        for (entry in setext) {
            start = Math.min(start, state.lines[entry.node.sourceSpans.first().lineIndex].start)
            end = Math.max(end, state.lines[entry.node.sourceSpans.last().lineIndex].end)
        }
        val result = area.getText(start, end).split('\n').mapIndexedNotNull { index, content ->
            val line = state.lines[state.lineAt(start) + index]
            if (line.code || content.isBlank()) content else {
                val container = quote.find(content)?.value.orEmpty()
                val rest = content.substring(container.length)
                if (line.heading > 0 && rest.trim().matches(Regex("[=-]+"))) null else {
                    val body = rest.replace(Regex("^ {0,3}#{1,6}(?:[ \\t]+|$)"), "")
                    container + if (level == 0) body else "#".repeat(level) + " " + body
                }
            }
        }.joinToString("\n")
        change(start, end, result, start, start + result.length)
    }

    fun indent(outdent: Boolean) {
        val (start, end) = lineRange()
        val hadSelection = area.selection.length > 0
        val caret = area.caretPosition
        val original = area.getText(start, end)
        val transformed = original.split('\n').joinToString("\n") { content ->
            val prefix = quote.find(content)?.value.orEmpty()
            val rest = content.substring(prefix.length)
            prefix + if (outdent) rest.replace(Regex("^(?: {1,4}|\\t)"), "") else "    $rest"
        }
        val insertAtCaret = if (!hadSelection && !outdent) {
            val state = snapshot()
            state.lines[state.lineAt(caret)].listDepth == 0
        } else false
        if (insertAtCaret) {
            change(caret, caret, "    ", caret + 4)
        } else change(start, end, transformed, if (hadSelection) start else Math.max(start, caret + transformed.length - original.length),
            if (hadSelection) start + transformed.length else Math.max(start, caret + transformed.length - original.length))
    }

    fun editLink() {
        if (!canFormat()) return
        val source = area.text
        val edit = MarkdownLinkEditing.request(source, area.selection.start, area.selection.end, area.caretPosition, snapshot()) ?: return
        if (source != area.text || !area.isEditable || area.markdownComposing) return
        change(edit.start, edit.end, edit.text, edit.start, edit.start + edit.text.length)
    }

    fun installMenu() {
        val menu = Menu(Locales.str("markdown.format"))
        fun add(key: String, action: () -> Unit) {
            menu.items.add(MenuItem(Locales.str(key)).apply {
                MarkdownShortcuts.entries.firstOrNull { it.labelKey == key }?.let { shortcut ->
                    graphic = javafx.scene.control.Label(shortcut.display()).apply { styleClass.add("normal-desc-label") }
                }
                setOnAction { if (area.isEditable && !area.markdownComposing && !area.editor.isRealtimeProcessingLimitReached) action() }
            })
        }
        val headings = Menu(Locales.str("markdown.heading"))
        for (level in 1..6) headings.items.add(MenuItem("H$level").apply {
            graphic = javafx.scene.control.Label(MarkdownShortcuts.entries.first { it.heading == level }.display())
            setOnAction { heading(level) }
        })
        menu.items.add(headings)
        add("markdown.paragraph") { heading(0) }
        add("markdown.bold") { wrap("**") }
        add("markdown.italic") { wrap("*") }
        add("markdown.strike") { wrap("~~") }
        add("markdown.inlineCode") { wrap("`") }
        add("markdown.quote") { prefixLines("> ") }
        add("markdown.list") { prefixLines("- ") }
        add("markdown.orderedList") { prefixLines("1. ") }
        add("markdown.task") { prefixLines("- [ ] ") }
        add("markdown.editLink") { editLink() }
        menu.items.add(SeparatorMenuItem())
        add("markdown.createCode") { area.markdownCodeActions.create() }
        add("markdown.codeLanguage") { area.markdownCodeActions.chooseLanguage() }
        add("markdown.copyCode") { area.markdownCodeActions.copyCode() }
        add("markdown.exitCode") { area.markdownCodeActions.exitCode() }
        add("markdown.createTable") { area.markdownCodeActions.createTable() }
        add("markdown.insertImage") { area.markdownAttachments.chooseImage() }
        add("markdown.imageWidth") { area.markdownAttachments.resizeImage() }
        menu.items.add(SeparatorMenuItem())
        add("markdown.pastePlain") { area.markdownClipboard.paste(true) }
        val copyMenu = Menu(Locales.str("markdown.copyAs"))
        for ((key, mode) in listOf("markdown.copySource" to "markdown", "markdown.copyPlain" to "plain", "markdown.copyFormatted" to "formatted")) {
            copyMenu.items.add(MenuItem(Locales.str(key)).apply { setOnAction { area.markdownClipboard.copy(mode) } })
        }
        copyMenu.items.add(MenuItem(Locales.str("markdown.copyCode")).apply { setOnAction { area.markdownCodeActions.copyCode() } })
        val copyAddress = MenuItem(Locales.str("markdown.copyLinkAddress"))
        copyAddress.setOnAction {
            if (!area.editor.isRealtimeProcessingLimitReached) snapshot().linkAt(area.caretPosition)?.let { link ->
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(javafx.scene.input.ClipboardContent().apply { putString(link.destination) })
            }
        }
        copyMenu.items.add(copyAddress)
        val export = MenuItem(Locales.str("markdown.exportHtml"))
        export.setOnAction { area.markdownClipboard.exportHtml() }
        val stats = MenuItem(Locales.str("markdown.wordCount"))
        stats.setOnAction {
            if (area.editor.isRealtimeProcessingLimitReached) { com.allan.atools.ui.SnackbarUtils.show(Locales.str("markdown.previewLimit")); return@setOnAction }
            val document = snapshot()
            val selected = if (area.selection.length == 0) document else
                com.allan.atools.richtext.codearea.keywordhelper.MarkdownSelectionSnapshot.create(document, area.selection.start, area.selection.end)
            val plain = com.allan.atools.richtext.codearea.keywordhelper.MarkdownPlainText.render(selected.root)
            val characters = plain.codePoints().filter { !Character.isWhitespace(it) }.count()
            val words = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]|[\\p{L}\\p{N}_&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]]+").findAll(plain).count()
            val dialog = javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                String.format(Locales.str("markdown.wordCountValue"), characters, words))
            dialog.headerText = Locales.str("markdown.wordCount")
            dialog.showAndWait()
        }
        val preview = MenuItem(Locales.str("markdown.toggleSource"))
        preview.graphic = javafx.scene.control.Label(MarkdownShortcuts.SOURCE.display())
        preview.setOnAction { if (!area.markdownComposing) area.toggleMarkdownPreview() }
        val fullPreview = MenuItem(Locales.str("markdown.preview"))
        fullPreview.setOnAction { com.allan.atools.tools.modulenotepad.manager.MarkdownPreviewWindow.show(area) }
        area.contextMenu.items.addAll(SeparatorMenuItem(), menu, copyMenu, export, stats, preview, fullPreview)
        area.contextMenu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING) {
            val enabled = MarkdownEditorSupport.supportsMarkdown(area)
            menu.isVisible = enabled
            menu.isDisable = !area.isEditable || area.editor.isRealtimeProcessingLimitReached
            copyMenu.isVisible = enabled
            export.isVisible = enabled
            stats.isVisible = enabled
            preview.isVisible = enabled
            fullPreview.isVisible = enabled
        }
    }
}

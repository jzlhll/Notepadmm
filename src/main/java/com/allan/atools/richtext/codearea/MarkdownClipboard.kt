package com.allan.atools.richtext.codearea

import com.allan.atools.Colors
import com.allan.atools.MarkdownThemes
import com.allan.atools.UIContext
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.manager.MarkdownHtmlRenderer
import com.allan.atools.tools.modulenotepad.manager.MarkdownPreviewWindow
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DataFormat
import javafx.stage.FileChooser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** 剪贴板保留 Markdown 类型，跨应用同时提供纯文本与有限 HTML。 */
class MarkdownClipboard(private val area: EditorArea) {
    companion object {
        private val markdownFormat = DataFormat.lookupMimeType("text/markdown") ?: DataFormat("text/markdown")

        @JvmStatic
        fun fromHtml(html: String): String {
            val document = Jsoup.parseBodyFragment(html)
            document.select("script,style,iframe,object").remove()
            return htmlToMarkdown(document.body()).trim()
        }

        @JvmStatic
        fun htmlTableCells(html: String): List<List<String>>? {
            val document = Jsoup.parseBodyFragment(html)
            document.select("script,style,iframe,object").remove()
            val tables = document.select("table")
            if (tables.size != 1) return null
            val table = tables.first() ?: return null
            // 只有独立表格可覆盖目标矩阵；表外文字、图片及其他可见内容整段保留。
            val remainder = document.body().clone()
            remainder.select("table").remove()
            if (htmlToMarkdown(remainder).isNotBlank() || table.select("caption").isNotEmpty()) return null
            return tableCells(table)
        }

        private fun tableCells(table: Element): List<List<String>>? {
            val rows = table.select("tr").filter { it.closest("table") === table }
            if (rows.isEmpty() || rows.size > 1000) return null
            val matrix = MutableList(rows.size) { ArrayList<String?>() }
            var occupied = 0
            rows.forEachIndexed { rowIndex, row ->
                var column = 0
                var groupEnd = rowIndex + 1
                while (groupEnd < rows.size && rows[groupEnd].parent() === row.parent()) groupEnd++
                for (cell in row.children().filter { it.tagName() == "td" || it.tagName() == "th" }) {
                    while (column < matrix[rowIndex].size && matrix[rowIndex][column] != null) column++
                    val width = cell.attr("colspan").ifEmpty { "1" }.toIntOrNull() ?: return null
                    val declaredHeight = cell.attr("rowspan").ifEmpty { "1" }.toIntOrNull() ?: return null
                    if (width !in 1..100 || column + width > 100 || declaredHeight !in 0..1000) return null
                    val height = if (declaredHeight == 0) groupEnd - rowIndex else Math.min(declaredHeight, groupEnd - rowIndex)
                    occupied += width * height
                    if (occupied > 10000) return null
                    for (y in rowIndex until rowIndex + height) {
                        while (matrix[y].size < column + width) matrix[y].add(null)
                        for (x in column until column + width) {
                            if (matrix[y][x] != null) return null
                            matrix[y][x] = ""
                        }
                    }
                    matrix[rowIndex][column] = com.allan.atools.tools.modulenotepad.manager.MarkdownTableCellText.encode(htmlToMarkdown(cell).trim(), "")
                    column += width
                }
            }
            val columns = matrix.map { it.size }.maxOrNull() ?: return null
            if (columns == 0 || rows.size * columns > 10000) return null
            return matrix.map { row -> List(columns) { row.getOrNull(it).orEmpty() } }
        }

        private fun absoluteHtml(html: String, base: String?): String {
            if (base == null) return html
            val document = Jsoup.parseBodyFragment(html, base)
            document.select("img[src],a[href]").forEach { element ->
                val key = if (element.tagName() == "img") "src" else "href"
                if (!element.attr(key).startsWith('#')) element.absUrl(key).takeIf { it.isNotEmpty() }?.let { element.attr(key, it) }
            }
            document.outputSettings().prettyPrint(false)
            return document.body().html()
        }

        private fun htmlToMarkdown(node: Node): String {
            if (node is TextNode) return MarkdownSourceText.escape(node.wholeText)
            if (node !is Element) return ""
            if (node.hasClass("md-task-icon")) {
                val source = node.selectFirst(".md-task-source") ?: return ""
                return MarkdownInlineCode.encode(source.wholeText())
            }
            fun contents() = node.childNodes().joinToString("") { htmlToMarkdown(it) }
            val content = when (node.tagName()) {
                "script", "style", "iframe", "object" -> ""
                "br" -> "  \n"
                "b", "strong" -> "**${contents()}**"
                "i", "em" -> "*${contents()}*"
                "s", "del" -> "~~${contents()}~~"
                "u", "sub", "sup", "mark" -> "<${node.tagName()}>${contents()}</${node.tagName()}>"
                "h1", "h2", "h3", "h4", "h5", "h6" -> "\n\n${"#".repeat(node.tagName().last().digitToInt())} ${contents()}\n\n"
                "p", "div", "section" -> "\n\n${contents()}\n\n"
                "blockquote" -> "\n\n" + contents().trim().split('\n').joinToString("\n") { "> $it" } + "\n\n"
                "pre" -> {
                    val source = node.wholeText().trimEnd('\n')
                    val fence = "`".repeat(Math.max(3, (Regex("`+").findAll(source).map { it.value.length }.maxOrNull() ?: 0) + 1))
                    "\n\n$fence\n$source\n$fence\n\n"
                }
                "code" -> MarkdownInlineCode.encode(node.wholeText())
                "a" -> {
                    val address = node.attr("href").replace("<", "%3C").replace(">", "%3E").replace("\n", "")
                    if (address.isEmpty() || address.startsWith("javascript:", true)) contents() else "[${contents()}](<$address>)"
                }
                "img" -> {
                    val address = node.attr("src").replace("<", "%3C").replace(">", "%3E").replace("\n", "")
                    "![${node.attr("alt").replace("[", "\\[").replace("]", "\\]")}](<$address>)"
                }
                "ul", "ol" -> {
                    val number = node.attr("start").toIntOrNull() ?: 1
                    "\n\n" + node.children().filter { it.tagName() == "li" }.mapIndexed { index, child ->
                        val marker = if (node.tagName() == "ol") "${number + index}. " else "- "
                        val content = htmlToMarkdown(child).trim()
                        val checkbox = child.selectFirst("input[type=checkbox]")
                        marker + (if (checkbox == null) "" else if (checkbox.hasAttr("checked")) "[x] " else "[ ] ") +
                            content.split('\n').joinToString("\n${" ".repeat(marker.length)}")
                    }.joinToString("\n") + "\n\n"
                }
                "li" -> contents()
                "hr" -> "\n\n---\n\n"
                "input" -> ""
                "table" -> {
                    val caption = node.children().filter { it.tagName() == "caption" }.joinToString("\n\n") { htmlToMarkdown(it) }
                    val prefix = "\n\n" + if (caption.isBlank()) "" else "$caption\n\n"
                    val rows = tableCells(node) ?: return prefix + node.select("tr").filter { it.closest("table") === node }.joinToString("\n\n") { row ->
                        row.children().filter { it.tagName() == "td" || it.tagName() == "th" }.joinToString(" | ") { htmlToMarkdown(it).trim() }
                    } + "\n\n"
                    val columns = rows.map { it.size }.maxOrNull() ?: 0
                    if (columns == 0) "" else {
                        fun row(values: List<String>) = "| " + (values + List(columns - values.size) { "" }).joinToString(" | ") + " |"
                        prefix +
                            (listOf(row(rows.first()), row(List(columns) { "---" })) + rows.drop(1).map(::row)).joinToString("\n") + "\n\n"
                    }
                }
                else -> contents()
            }
            return content
        }
    }

    fun paste(plain: Boolean = false): Boolean {
        if (!area.isEditable || area.markdownComposing) return false
        val clipboard = Clipboard.getSystemClipboard()
        if (!plain) {
            val markdown = clipboard.getContent(markdownFormat) as? String
            if (markdown != null) { insert(markdown); return true }
            if (clipboard.hasImage()) { area.markdownAttachments.importClipboardImage(clipboard.image); return true }
            if (clipboard.hasFiles() && clipboard.files.all(MarkdownAttachments::isImage)) {
                area.markdownAttachments.importFiles(clipboard.files); return true
            }
            if (clipboard.hasHtml() && !area.editor.isRealtimeProcessingLimitReached) {
                val html = clipboard.html
                if (html.length <= 2 * 1024 * 1024) { insert(fromHtml(html)); return true }
            }
        }
        if (clipboard.hasString()) { insert(clipboard.string); return true }
        return false
    }

    private fun insert(value: String) {
        area.undoManager.preventMerge()
        area.replaceSelection(value.replace("\r\n", "\n").replace('\r', '\n'))
        area.undoManager.preventMerge()
    }

    fun copy(mode: String) {
        val source = if (area.selection.length > 0) area.selectedText else area.text
        if (mode == "markdown") {
            Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(source); this[markdownFormat] = source })
            return
        }
        if (area.editor.isRealtimeProcessingLimitReached) { SnackbarUtils.show(Locales.str("markdown.previewLimit")); return }
        val base = area.editor.sourceFile?.parentFile?.toURI()?.toASCIIString()
        val document = (area.editor as EditorAreaMgrCode).markdownSnapshot(area.text)
        val state = if (area.selection.length == 0) document else
            com.allan.atools.richtext.codearea.keywordhelper.MarkdownSelectionSnapshot.create(document, area.selection.start, area.selection.end)
        val plain = com.allan.atools.richtext.codearea.keywordhelper.MarkdownPlainText.render(state.root)
        val content = ClipboardContent()
        when (mode) {
            "plain" -> content.putString(plain)
            "formatted" -> { content.putString(plain); content.putHtml(absoluteHtml(MarkdownHtmlRenderer.body(state, base), base)); content[markdownFormat] = source }
            else -> { content.putString(source); content[markdownFormat] = source }
        }
        Clipboard.getSystemClipboard().setContent(content)
    }

    fun exportHtml() {
        if (area.editor.isRealtimeProcessingLimitReached) { SnackbarUtils.show(Locales.str("markdown.previewLimit")); return }
        val chooser = FileChooser()
        chooser.title = Locales.str("markdown.exportHtml")
        chooser.initialFileName = area.editor.documentState.displayName.substringBeforeLast('.') + ".html"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("HTML", "*.html"))
        val destination = chooser.showSaveDialog(UIContext.mainWindow) ?: return
        val source = area.text
        val base = area.editor.sourceFile?.parentFile?.toURI()?.toASCIIString()
        val dark = Colors.isDark()
        val themeCss = MarkdownThemes.previewCss(dark)
        ThreadUtils.execute {
            try {
                val state = (area.editor as EditorAreaMgrCode).markdownSnapshot(source)
                val html = MarkdownPreviewWindow.renderDocument(state, base, dark, themeCss)
                Files.writeString(destination.toPath(), html, StandardCharsets.UTF_8)
                Platform.runLater { SnackbarUtils.show(Locales.str("markdown.exportDone")) }
            } catch (error: Exception) {
                Log.e("Export markdown HTML failed", error)
                Platform.runLater { SnackbarUtils.show(Locales.str("markdown.exportFailed")) }
            }
        }
    }
}

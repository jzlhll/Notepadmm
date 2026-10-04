package com.allan.atools.richtext.codearea

import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownDetailsBlocks
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.utils.Locales
import com.allan.uilibs.richtexts.CodeArea
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** HTML 详情块使用原生控件预览，折叠状态独立于文档内容和撤销历史。 */
class MarkdownDetailsPresentation(private val area: EditorArea) {
    companion object { const val GRAPHIC_CLASS = "markdown-details-graphic" }

    private class State {
        val open = HashMap<Int, Boolean>()
    }
    private class Entry(val block: MarkdownDetailsBlocks.Block, val state: State) {
        var height = 0.0
        var preview = true
    }
    private var entries = emptyList<Entry>()
    private val saved = HashMap<Pair<String, Int>, State>()
    private var installed = false
    private var pending = false
    private var disposed = false
    private val layoutChanged = InvalidationListener { requestRefresh(true) }
    private val selectionChanged = InvalidationListener { requestRefresh(false) }

    fun apply(snapshot: MarkdownStructureSnapshot) {
        if (snapshot.details.isEmpty()) {
            clear()
            saved.clear()
            return
        }
        if (!installed) {
            installed = true
            area.addParagraphGraphicDecorator(this, ::graphic)
            area.widthProperty().addListener(layoutChanged)
            area.paddingProperty().addListener(layoutChanged)
            UIContext.getFontSizeProperty().addListener(layoutChanged)
            area.caretPositionProperty().addListener(selectionChanged)
            area.selectionProperty().addListener(selectionChanged)
            area.focusedProperty().addListener(selectionChanged)
        }
        val occurrences = HashMap<String, Int>()
        val nextSaved = HashMap<Pair<String, Int>, State>()
        val next = snapshot.details.map { block ->
            val occurrence = occurrences.getOrDefault(block.source, 0)
            occurrences[block.source] = occurrence + 1
            val key = block.source to occurrence
            val state = saved[key] ?: State()
            nextSaved[key] = state
            Entry(block, state)
        }
        clear()
        entries = next
        saved.clear()
        saved.putAll(nextSaved)
        refresh()
    }

    private fun requestRefresh(resize: Boolean) {
        if (disposed || entries.isEmpty()) return
        if (resize) entries.forEach { it.height = 0.0 }
        if (pending) return
        pending = true
        Platform.runLater {
            area.markdownSyntax.runAfterPointer(Runnable {
                area.runAfterMarkdownComposition(Runnable {
                    pending = false
                    if (!disposed) refresh()
                })
            })
        }
    }

    private fun width(): Double {
        val numberWidth = area.lookup(".lineno")?.prefWidth(-1.0) ?: 0.0
        return Math.max(1.0, area.width - area.insets.left - area.insets.right
            - numberWidth - MarkdownEditorSupport.textLeftPadding(area))
    }

    private fun refresh() {
        if (!area.markdownPreviewEnabled || area.editor.isRealtimeProcessingLimitReached) { clear(); return }
        area.suspendVisibleParsWhileInvoke {
            for (entry in entries) {
                val block = entry.block
                val selection = area.selection
                val editing = (area.isFocused || selection.length > 0) && (if (selection.length == 0) area.caretPosition in block.start..block.end
                    else selection.start < block.end && selection.end > block.start)
                val changed = entry.preview == editing || entry.height == 0.0
                entry.preview = !editing
                if (entry.preview && entry.height == 0.0) entry.height = Math.ceil(content(entry).prefHeight(width())) + 12.0
                for (line in block.firstLine..block.lastLine) {
                    if (line !in area.paragraphs.indices) continue
                    val old = area.getParagraph(line).paragraphStyle
                    val styles = old.filterNot { it.startsWith(CodeArea.DETAILS_PREVIEW_HEIGHT_PREFIX) }.toMutableList()
                    if (entry.preview) styles.add(CodeArea.DETAILS_PREVIEW_HEIGHT_PREFIX +
                        if (line == block.firstLine) entry.height else 0.0)
                    if (old != styles) area.setParagraphStyle(line, styles)
                    if (changed) area.recreateParagraphGraphic(line)
                }
            }
        }
        area.requestLayout()
    }

    private fun graphic(line: Int, base: Node?): Node? {
        val entry = entries.firstOrNull { it.preview && line in it.block.firstLine..it.block.lastLine } ?: return base
        if (line != entry.block.firstLine) return Pane().apply { setMinSize(0.0, 0.0); setPrefSize(0.0, 0.0); setMaxSize(0.0, 0.0) }
        val body = content(entry).apply { isManaged = false }
        return object : Pane() {
            init {
                styleClass.add(GRAPHIC_CLASS)
                if (base != null) { base.opacity = 0.0; children.add(base) }
                children.add(body)
                val clip = Rectangle()
                body.clip = clip
                clip.widthProperty().bind(body.widthProperty())
                clip.heightProperty().bind(body.heightProperty())
                addEventHandler(MouseEvent.MOUSE_PRESSED) { it.consume() }
                addEventHandler(MouseEvent.MOUSE_DRAGGED) { it.consume() }
                addEventHandler(MouseEvent.MOUSE_RELEASED) { it.consume() }
                addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
                    if (event.button == MouseButton.PRIMARY && event.clickCount == 2) edit(entry)
                    event.consume()
                }
                setOnContextMenuRequested { event ->
                    val source = MenuItem(Locales.str("markdownTableShowSource")).apply { setOnAction { edit(entry) } }
                    ContextMenu(source).show(this, event.screenX, event.screenY)
                    event.consume()
                }
            }
            override fun computePrefWidth(height: Double): Double = base?.prefWidth(height) ?: 0.0
            override fun layoutChildren() {
                base?.resizeRelocate(0.0, 0.0, this.width, height)
                body.resizeRelocate(this.width + MarkdownEditorSupport.textLeftPadding(area), 6.0,
                    this@MarkdownDetailsPresentation.width(), Math.max(0.0, entry.height - 12.0))
            }
        }
    }

    private fun edit(entry: Entry) {
        if (area.markdownComposing || entry !in entries) return
        area.moveTo(entry.block.start)
        area.requestFocus()
        requestRefresh(false)
    }

    private fun content(entry: Entry): VBox {
        val root = Jsoup.parseBodyFragment(entry.block.source).body().children().firstOrNull { it.normalName() == "details" }
            ?: return VBox()
        val size = UIContext.getFontSizeProperty().get().toDouble()
        val indices = root.getAllElements().filter { it.normalName() == "details" }
            .withIndex().associate { it.value to it.index }
        fun text(source: String, classes: List<String> = emptyList()): Text = Text(source).apply {
            font = Font.font("JetBrains Mono",
                if ("markdown-details-bold" in classes) FontWeight.BOLD else FontWeight.NORMAL,
                if ("markdown-details-italic" in classes) FontPosture.ITALIC else FontPosture.REGULAR, size)
            styleClass.add("markdown-details-text")
            styleClass.addAll(classes)
        }
        fun inline(node: org.jsoup.nodes.Node, flow: TextFlow, classes: List<String> = emptyList()) {
            if (node is TextNode) { flow.children.add(text(node.wholeText, classes)); return }
            if (node !is Element) return
            val tag = node.normalName()
            if (tag in setOf("script", "style")) return
            if (tag == "br") { flow.children.add(text("\n", classes)); return }
            val style = when (tag) {
                "b", "strong" -> "markdown-details-bold"
                "i", "em" -> "markdown-details-italic"
                "code", "pre" -> "markdown-inline-code"
                "u" -> "markdown-details-underline"
                "s", "del" -> "markdown-strikethrough"
                "mark" -> "markdown-highlight"
                else -> null
            }
            node.childNodes().forEach { inline(it, flow, if (style == null) classes else classes + style) }
        }
        fun trim(flow: TextFlow) {
            (flow.children.firstOrNull() as? Text)?.let { it.text = it.text.trimStart() }
            (flow.children.lastOrNull() as? Text)?.let { it.text = it.text.trimEnd() }
            flow.lineSpacing = 3.0
        }
        fun details(element: Element): VBox {
            val id = indices.getValue(element)
            val opened = entry.state.open.getOrPut(id) { element.hasAttr("open") }
            val summary = element.children().firstOrNull { it.normalName() == "summary" }
            val title = TextFlow()
            if (summary == null) title.children.add(text(Locales.str("markdownDetailsSummary"))) else inline(summary, title)
            trim(title)
            val arrow = text(if (opened) "▼" else "▶")
            val row = object : Pane() {
                init { children.addAll(arrow, title); cursor = Cursor.HAND }
                override fun getContentBias(): Orientation = Orientation.HORIZONTAL
                override fun computePrefHeight(width: Double): Double = Math.max(size * 1.5, title.prefHeight(Math.max(1.0, width - size * 1.25)))
                override fun layoutChildren() {
                    arrow.relocate(0.0, Math.max(0.0, (size * 1.5 - arrow.layoutBounds.height) / 2.0))
                    title.resizeRelocate(size * 1.25, 0.0, Math.max(1.0, width - size * 1.25), height)
                }
            }
            val result = VBox(size * 0.2, row)
            if (id > 0) result.padding = Insets(0.0, 0.0, 0.0, size * 1.25)
            if (opened) {
                val body = VBox(size * 0.2)
                var flow = TextFlow()
                fun flush() {
                    trim(flow)
                    if (flow.children.filterIsInstance<Text>().any { it.text.isNotBlank() }) body.children.add(flow)
                    flow = TextFlow()
                }
                fun append(node: org.jsoup.nodes.Node) {
                    if (node === summary) return
                    if (node is Element && node.normalName() == "details") {
                        flush()
                        body.children.add(details(node))
                    } else if (node is Element && node.isBlock && node.select("details").isNotEmpty()) {
                        flush()
                        node.childNodes().forEach { append(it) }
                        flush()
                    } else if (node is Element && node.isBlock) {
                        flush()
                        inline(node, flow)
                        flush()
                    } else inline(node, flow)
                }
                element.childNodes().forEach { append(it) }
                flush()
                result.children.add(body)
            }
            row.setOnMouseClicked { event ->
                if (event.button == MouseButton.PRIMARY && event.clickCount == 1 && event.isStillSincePress
                    && !area.markdownComposing && entry in entries) {
                    entry.state.open[id] = !entry.state.open.getValue(id)
                    requestRefresh(true)
                }
                event.consume()
            }
            return result
        }
        return details(root)
    }

    private fun clearStyles() {
        area.suspendVisibleParsWhileInvoke {
            for (line in area.paragraphs.indices) {
                val old = area.getParagraph(line).paragraphStyle
                val styles = old.filterNot { it.startsWith(CodeArea.DETAILS_PREVIEW_HEIGHT_PREFIX) }
                if (old != styles) { area.setParagraphStyle(line, styles); area.recreateParagraphGraphic(line) }
            }
        }
    }

    fun clear() {
        if (entries.isEmpty()) return
        entries = emptyList()
        // 样式随编辑后的段落移动，清理时按当前段落识别，避免删除换行后留下不可见正文。
        clearStyles()
    }

    fun destroy() {
        disposed = true
        clear()
        saved.clear()
        if (!installed) return
        area.removeParagraphGraphicDecorator(this)
        area.widthProperty().removeListener(layoutChanged)
        area.paddingProperty().removeListener(layoutChanged)
        UIContext.getFontSizeProperty().removeListener(layoutChanged)
        area.caretPositionProperty().removeListener(selectionChanged)
        area.selectionProperty().removeListener(selectionChanged)
        area.focusedProperty().removeListener(selectionChanged)
    }
}

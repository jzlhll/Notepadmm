package com.allan.atools.richtext.codearea

import com.allan.atools.Colors
import com.allan.atools.FontTheme
import com.allan.atools.MarkdownThemes
import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownEmbeddedBlocks
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.manager.*
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.uilibs.richtexts.CodeArea
import com.google.gson.Gson
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.concurrent.Worker
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Node
import javafx.scene.layout.Pane
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.WeakHashMap
import java.util.concurrent.Future
import javax.imageio.ImageIO

/** 复杂块共用完整排版，保留全文源码坐标；离屏释放 WebView，选择、搜索及输入时回到源码。 */
class MarkdownEmbeddedPresentation(private val area: EditorArea) {
    private data class CachedFragment(val html: String, val comparable: String, val start: Int, val line: Int)
    private class Entry(var block: MarkdownEmbeddedBlocks.Block) {
        var html: String? = null
        var comparableHtml: String? = null
        var source: String? = null
        var renderedStart = block.start
        var renderedLine = block.firstLine
        var height = 0.0
        var preview = false
        var styledHeight: Double? = null
        var searchHit = false
        var readingState: String? = null
        val taskStates = HashMap<Int, Boolean>()
    }
    private val views = WeakHashMap<BlockView, Entry>()
    private var entries = emptyList<Entry>()
    private var snapshot: MarkdownStructureSnapshot? = null
    private var taskPositionsCurrent = false
    private var version = -1L
    private var file: File? = null
    private var generation = 0L
    private var renderTask: Future<*>? = null
    private var anchors = emptyMap<String, Int>()
    private var renderedDependencies: List<MarkdownStructureSnapshot.HtmlDependency>? = null
    private var renderedBlockCount = 0
    private var renderingRequired = false
    private var forceReload = false
    private var installed = false
    private var disposed = false
    private var pending = false
    private val selectionChanged = InvalidationListener { requestRefresh() }
    private val layoutChanged = InvalidationListener {
        // 保留上次测量高度，等页面回报新高度后再调整，避免滚动文档先收缩再展开。
        views.keys.toList().forEach { it.resizeContent() }
        requestRefresh()
    }
    private val themeChanged = InvalidationListener { snapshot?.let { render(it, true) } }

    fun apply(state: MarkdownStructureSnapshot) {
        if (disposed) return
        if (!installed) {
            installed = true
            area.addParagraphGraphicDecorator(this, ::graphic)
            area.caretPositionProperty().addListener(selectionChanged)
            area.selectionProperty().addListener(selectionChanged)
            area.focusedProperty().addListener(selectionChanged)
            area.editableProperty().addListener(selectionChanged)
            area.widthProperty().addListener(layoutChanged)
            UIContext.getFontSizeProperty().addListener(themeChanged)
            UIContext.getFontThemeProperty().addListener(themeChanged)
            MarkdownThemes.revisionProperty().addListener(themeChanged)
        }
        val previous = entries.associateBy { it.block.start }
        val next = state.embeddedBlocks.map { block ->
            (previous[block.start]?.also { entry ->
                if (entry.block.firstLine != block.firstLine || entry.block.lastLine != block.lastLine) {
                    styles(entry, false)
                    entry.styledHeight = null
                }
                entry.block = block
            } ?: Entry(block)).also { entry ->
                entry.searchHit = area.getStyleSpans(block.start, block.end).any {
                    "search" in it.style || "temporary" in it.style
                }
            }
        }
        val retained = next.toHashSet()
        val removed = entries.filterNot { it in retained }
        removed.forEach { styles(it, false) }
        views.keys.toList().filter { views[it] in removed }.forEach { it.release() }
        entries = next
        val changedFile = file != area.editor.sourceFile
        val changed = snapshot !== state || changedFile
        snapshot = state
        taskPositionsCurrent = true
        version = area.editor.contentVersion
        file = area.editor.sourceFile
        if (changed && (renderingRequired || changedFile || renderedDependencies != state.htmlDependencies || renderedBlockCount != entries.size ||
            entries.any { entry ->
                val source = entry.source
                source == null || source.length != entry.block.end - entry.block.start ||
                    !state.text.regionMatches(entry.block.start, source, 0, source.length)
            })) render(state, changedFile)
        anchors = anchors + state.headings.associate { it.anchor to state.lines[it.line].start }
        refresh()
    }

    private fun render(state: MarkdownStructureSnapshot, force: Boolean = false) {
        renderingRequired = true
        forceReload = forceReload || force
        val reload = forceReload
        val token = ++generation
        renderTask?.cancel(true)
        if (entries.isEmpty()) {
            renderingRequired = false
            forceReload = false
            renderedDependencies = state.htmlDependencies
            renderedBlockCount = 0
            anchors = emptyMap()
            return
        }
        val base = file?.parentFile?.toURI()?.toASCIIString()
        val reusable = if (!reload && renderedDependencies == state.htmlDependencies) entries.filter { entry ->
            val source = entry.source
            source != null && entry.html != null && entry.comparableHtml != null &&
                source.length == entry.block.end - entry.block.start && state.text.regionMatches(entry.block.start, source, 0, source.length)
        }.associate { entry -> entry.block.start to CachedFragment(entry.html!!, entry.comparableHtml!!, entry.renderedStart, entry.renderedLine) }
        else emptyMap()
        renderTask = ThreadUtils.submit {
            try {
                val blocks = state.embeddedBlocks
                val blocksByStart = blocks.associateBy { it.start }
                val pendingBlocks = blocks.filter { it.start !in reusable }
                val nodes = ArrayList<org.commonmark.node.Node>()
                var partial = state.htmlDependencies.none { it.kind == "note-reference" || it.kind == "note-definition" }
                var node = state.root.firstChild
                var blockIndex = 0
                while (node != null) {
                    val first = node.sourceSpans.firstOrNull()?.inputIndex
                    val end = node.sourceSpans.lastOrNull()?.let { it.inputIndex + it.length }
                    if (first != null && end != null) {
                        while (blockIndex < pendingBlocks.size && pendingBlocks[blockIndex].end <= first) blockIndex++
                        val block = pendingBlocks.getOrNull(blockIndex)
                        if (block != null && first < block.end && end > block.start) {
                            nodes.add(node)
                            if (first < block.start || end > block.end) partial = false
                        }
                    }
                    node = node.next
                }
                // 全文 AST 保留引用解析和目录上下文；脚注编号及跨块 HTML 继续使用完整输出。
                val document = Jsoup.parseBodyFragment(if (pendingBlocks.isEmpty()) "" else
                    MarkdownHtmlRenderer.body(state, base, true, if (partial) nodes else null))
                // 脚注库把第一条定义的坐标写到整组容器上；各定义必须按自己的源码范围拆开。
                document.select("section.footnotes").removeAttr("data-source-start").removeAttr("data-source-line")
                val sourceNodes = document.select("[data-source-start]")
                val buffers = pendingBlocks.associate { it.start to StringBuilder() }
                for (element in sourceNodes) {
                    val offset = element.attr("data-source-start").toIntOrNull() ?: continue
                    var low = 0
                    var high = blocks.lastIndex
                    while (low <= high) {
                        val middle = (low + high) / 2
                        if (blocks[middle].start <= offset) low = middle + 1 else high = middle - 1
                    }
                    val block = blocks.getOrNull(high) ?: continue
                    if (block.start !in buffers || offset >= block.end || element.parents().any { parent ->
                        parent.attr("data-source-start").toIntOrNull()?.let { it >= block.start && it < block.end } == true
                    }) continue
                    val html = if (element.normalName() == "li" && element.parent()?.parent()?.hasClass("footnotes") == true)
                        "<section class=\"footnotes\"><ol start=\"${element.siblingIndex() + 1}\">${element.outerHtml()}</ol></section>"
                    else element.outerHtml()
                    buffers.getValue(block.start).append(html)
                }
                // HTML 归一化也留在后台，界面线程只比较结果并更新实际变化的页面。
                val fragments = reusable.mapValues { (start, cached) ->
                    val line = blocksByStart.getValue(start).firstLine
                    val html = SOURCE_COORDINATES.replace(cached.html) { match ->
                        val delta = if (match.groupValues[1] == "start") start - cached.start else line - cached.line
                        "data-source-${match.groupValues[1]}=\"${match.groupValues[2].toInt() + delta}\""
                    }
                    html to cached.comparable
                } + buffers.mapValues { (_, buffer) ->
                    val html = buffer.toString()
                    html to stableHtml(html)
                }
                val targets = state.headings.associate { it.anchor to state.lines[it.line].start }.toMutableMap()
                for (block in blocks) {
                    val fragment = fragments[block.start]?.first ?: continue
                    Jsoup.parseBodyFragment(fragment).select("[id]").forEach { element ->
                        val source = element.parents().firstOrNull { it.hasAttr("data-source-start") }
                        val position = element.attr("data-source-start").toIntOrNull()
                            ?: source?.attr("data-source-start")?.toIntOrNull()
                        if (position != null) targets[element.id()] = position
                    }
                }
                if (Thread.currentThread().isInterrupted) return@submit
                Platform.runLater {
                    if (disposed || token != generation || snapshot !== state ||
                        version != area.editor.contentVersion || file != area.editor.sourceFile) return@runLater
                    renderTask = null
                    renderingRequired = false
                    forceReload = false
                    anchors = targets
                    renderedDependencies = state.htmlDependencies
                    renderedBlockCount = entries.size
                    for (entry in entries) {
                        entry.source = state.text.substring(entry.block.start, entry.block.end)
                        // HTML 注释没有可显示节点，保留可点击的简短占位，编辑时恢复全文。
                        val fragment = fragments[entry.block.start]
                        val html = fragment?.first.orEmpty().ifEmpty {
                            if (state.text.substring(entry.block.start, entry.block.end).trimStart().startsWith("<!--"))
                                "<span class=\"source-placeholder\">${MarkdownHtmlRenderer.escape(Locales.str("markdown.hiddenComment"))}</span>"
                            else ""
                        }
                        if (html.isEmpty()) { entry.html = null; continue }
                        val comparable = if (fragment?.first?.isNotEmpty() == true) fragment.second else stableHtml(html)
                        if (!reload && entry.html != null && entry.comparableHtml == comparable) continue
                        entry.html = html
                        entry.comparableHtml = comparable
                        entry.taskStates.clear()
                        entry.renderedStart = entry.block.start
                        entry.renderedLine = entry.block.firstLine
                        views.keys.toList().filter { views[it] === entry }.forEach { it.load() }
                    }
                    refresh()
                }
            } catch (error: Exception) {
                if (!Thread.currentThread().isInterrupted) Log.e("Render embedded markdown failed", error)
                Platform.runLater {
                    if (token == generation) { entries.forEach { it.html = null }; refresh() }
                }
            }
        }
    }

    private fun stableHtml(html: String): String {
        return INPUT_TAG.replace(html.replace(SOURCE_COORDINATES, "")) { tag ->
            tag.value.replace(CHECKED_ATTRIBUTE, "")
        }
    }

    private fun requestRefresh() {
        if (pending || disposed) return
        pending = true
        Platform.runLater {
            area.markdownSyntax.runAfterPointer(Runnable {
                area.runAfterMarkdownComposition(Runnable { pending = false; if (!disposed) refresh() })
            })
        }
    }

    private fun refresh() {
        if (disposed || area.markdownComposing) return
        if (snapshot == null || version != area.editor.contentVersion || entries.isEmpty()) return
        var changed = false
        area.suspendVisibleParsWhileInvoke {
            for (entry in entries) {
                val block = entry.block
                val selection = area.selection
                val editing = (area.isFocused || selection.length > 0) &&
                    if (selection.length == 0) area.caretPosition in block.start..block.end
                    else selection.start < block.end && selection.end > block.start
                val preview = area.markdownPreviewEnabled && !area.editor.isRealtimeProcessingLimitReached && entry.html != null && !editing && !entry.searchHit
                if (styles(entry, preview)) changed = true
            }
        }
        views.keys.toList().forEach { it.syncReadonly() }
        if (changed) area.requestLayout()
    }

    private fun styles(entry: Entry, preview: Boolean): Boolean {
        val height = if (preview) Math.max(32.0, entry.height) else -1.0
        if (entry.preview == preview && entry.styledHeight == height) return false
        val changed = entry.preview != preview || entry.styledHeight == null
        entry.preview = preview
        entry.styledHeight = height
        // 单纯测高只影响首行，余下源码行继续保持收起状态。
        val last = if (changed) entry.block.lastLine else entry.block.firstLine
        for (line in entry.block.firstLine..last) {
            if (line !in area.paragraphs.indices) continue
            val old = area.getParagraph(line).paragraphStyle
            val next = old.filterNot { it.startsWith(CodeArea.EMBEDDED_PREVIEW_HEIGHT_PREFIX) }.toMutableList()
            if (preview) next.add(CodeArea.EMBEDDED_PREVIEW_HEIGHT_PREFIX + if (line == entry.block.firstLine)
                height else 0.0)
            if (old != next) area.setParagraphStyle(line, next)
            if (changed) area.recreateParagraphGraphic(line)
        }
        return true
    }

    private fun width(base: Node?): Double = Math.max(40.0, area.width - area.insets.left - area.insets.right -
        (base?.prefWidth(-1.0) ?: 0.0) - MarkdownEditorSupport.textLeftPadding(area) - 12.0)

    private fun graphic(line: Int, base: Node?): Node? {
        var low = 0
        var high = entries.lastIndex
        while (low <= high) {
            val middle = (low + high) / 2
            if (entries[middle].block.firstLine <= line) low = middle + 1 else high = middle - 1
        }
        val entry = entries.getOrNull(high)?.takeIf { it.preview && line <= it.block.lastLine } ?: return base
        if (line != entry.block.firstLine) return Pane().apply { setMinSize(0.0, 0.0); setPrefSize(0.0, 0.0); setMaxSize(0.0, 0.0) }
        if (views.values.toSet().size >= 8 && entry !in views.values) {
            Platform.runLater { if (entry in entries) { entry.html = null; styles(entry, false) } }
            return base
        }
        return views.keys.firstOrNull { views[it] === entry }?.also { it.updateBase(base) } ?: BlockView(entry, base)
    }

    private fun edit(position: Int) {
        if (disposed || area.markdownComposing || version != area.editor.contentVersion || file != area.editor.sourceFile || snapshot == null) return
        area.selectRange(Math.max(0, Math.min(position, area.length)), Math.max(0, Math.min(position, area.length)))
        area.requestFocus()
        requestRefresh()
        area.requestFollowCaret()
    }

    private inner class BlockView(private val entry: Entry, private var base: Node?) : Pane() {
        private val web = WebView()
        private val imageTasks = ArrayList<Future<*>>()
        private var imageUrls = emptyList<String>()
        private var revision = 0L
        private var live = false
        private var ready = false
        private var readonly: Boolean? = null
        private var pageTask: Future<*>? = null
        private val bridge = Bridge()

        init {
            views[this] = entry
            styleClass.add("markdown-embedded-graphic")
            web.isManaged = false
            web.isContextMenuEnabled = false
            web.engine.setCreatePopupHandler { null }
            web.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL) { event ->
                if (event.deltaX == 0.0 && !event.isShiftDown && !event.isControlDown && !event.isMetaDown) {
                    area.scrollYBy(-event.deltaY)
                    event.consume()
                }
            }
            web.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED) { event ->
                if (MarkdownShortcuts.match(event) == MarkdownShortcuts.SOURCE && !area.markdownComposing) {
                    area.markdownEditing.handleKey(event)
                    event.consume()
                } else if (event.isShortcutDown && !event.isAltDown && !event.isShiftDown &&
                    event.code == javafx.scene.input.KeyCode.A) {
                    area.selectAll()
                    area.requestFocus()
                    requestRefresh()
                    event.consume()
                }
            }
            base?.let { it.opacity = 0.0; children.add(it) }
            children.add(web)
            web.engine.loadWorker.stateProperty().addListener { _, _, state ->
                if (live && state == Worker.State.SUCCEEDED &&
                    web.engine.executeScript("typeof embeddedReady === 'function' && window.embeddedRevision === $revision") == true) {
                    ready = true
                    // Kotlin 编译使用 JDK 25 的兼容接口，运行时由 JavaFX 提供未弃用的实现。
                    @Suppress("DEPRECATION")
                    val window = web.engine.executeScript("window") as JSObject
                    window.setMember("embeddedBridge", bridge)
                    web.engine.executeScript("embeddedReady()")
                    syncTasks()
                    syncReadonly()
                    entry.readingState?.let { web.engine.executeScript("restorePreviewReadingState(${Gson().toJson(it)})") }
                    imageUrls.indices.forEach { loadImage(it) }
                } else if (live && state == Worker.State.FAILED) {
                    entry.html = null
                    requestRefresh()
                }
            }
            sceneProperty().addListener { _, _, scene ->
                // 样式或文档段落替换可能在同一帧内重新挂载，不能因此清空已显示的页面。
                if (scene == null) Platform.runLater { if (this.scene == null) release() } else if (!live) {
                    live = true
                    views[this] = entry
                    load()
                }
            }
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED) { it.consume() }
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED) { it.consume() }
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED) { it.consume() }
        }

        fun updateBase(next: Node?) {
            if (base === next) return
            base?.let { children.remove(it) }
            base = next
            next?.let { it.opacity = 0.0; children.add(0, it) }
            requestLayout()
        }

        fun updateTask(line: Int, checked: Boolean) {
            val expected = revision
            Platform.runLater {
                if (live && ready && expected == revision) web.engine.executeScript("embeddedTask($line,$checked)")
            }
        }

        private fun syncTasks() {
            val html = entry.html ?: return
            val states = Jsoup.parseBodyFragment(html).select("li.task-list-item[data-source-line]").mapNotNull { item ->
                val line = item.attr("data-source-line").toIntOrNull() ?: return@mapNotNull null
                val input = item.selectFirst("input[type=checkbox]") ?: return@mapNotNull null
                listOf(line, entry.taskStates[line] ?: input.hasAttr("checked"))
            }
            web.engine.executeScript("embeddedTasks(${Gson().toJson(states)})")
        }

        fun load() {
            if (!live || disposed || entry.html == null) return
            saveReadingState()
            ready = false
            readonly = null
            revision++
            imageTasks.forEach(MarkdownImageTasks::cancel)
            imageTasks.clear()
            pageTask?.cancel(true)
            val expected = revision
            val source = entry.html!!
            val baseUrl = file?.parentFile?.toURI()?.toASCIIString()
            val dark = Colors.isDark()
            val themeCss = MarkdownThemes.previewCss(dark)
            val css = "body{overflow:hidden;font-size:${UIContext.getFontSizeProperty().get()}px;font-family:${Gson().toJson(FontTheme.fontFamily())}}" +
                "article{max-width:none;margin:0;padding:6px 4px;box-sizing:border-box}article>:first-child{margin-top:0}article>:last-child{margin-bottom:0}" +
                "img[data-image-loading]{min-width:32px;min-height:24px}.source-placeholder{color:var(--muted)}"
            pageTask = ThreadUtils.submit {
                try {
                    val body = Jsoup.parseBodyFragment(source)
                    val images = body.select("img").mapIndexed { index, image ->
                        val url = image.attr("src")
                        image.removeAttr("src")
                        image.attr("data-image-id", index.toString())
                        image.attr("data-image-loading", "true")
                        url
                    }
                    if (images.size > 8) {
                        Platform.runLater { if (live && expected == revision) { entry.html = null; requestRefresh() } }
                        return@submit
                    }
                    val script = MarkdownEmbeddedPresentation::class.java.getResource("/markdown/embedded.js")!!.readText()
                    val html = MarkdownPreviewWindow.renderFragment(body.body().html(), baseUrl, dark, themeCss,
                        body.select(".md-math").isNotEmpty(), body.select(".mermaid-source").isNotEmpty(), css, script + "\nwindow.embeddedRevision=$expected;")
                    Platform.runLater {
                        if (live && !disposed && expected == revision && entry in entries) {
                            pageTask = null
                            imageUrls = images
                            web.engine.loadContent(html)
                            resizeContent()
                        }
                    }
                } catch (error: Exception) {
                    if (!Thread.currentThread().isInterrupted) Log.e("Prepare embedded markdown page failed", error)
                    Platform.runLater {
                        if (live && expected == revision) { entry.html = null; requestRefresh() }
                    }
                }
            }
        }

        fun loadImage(index: Int) {
            if (!live || !ready || disposed || index !in imageUrls.indices) return
            val expected = revision
            val currentFile = file
            val url = imageUrls[index]
            val diagnostic = MarkdownImageLoadLog("embedded", url, currentFile, entry.block.firstLine + 1)
            imageTasks.add(MarkdownImageTasks.submit {
                try {
                    val resolved = MarkdownImageLocation.resolve(currentFile, url) ?: error("Unsupported image location")
                    val decoded = when (resolved.scheme.lowercase()) {
                        "http", "https" -> MarkdownRemoteImageLoader.loadDecoded(resolved.toASCIIString(), diagnostic, 0.0, 0.0)
                        "file" -> {
                            val source = MarkdownImageLocation.file(resolved)
                            require(source.length() <= 20L * 1024 * 1024) { "Image exceeds byte limit" }
                            MarkdownImageDecoder.decode(source, 0.0, 0.0)
                        }
                        "data" -> {
                            require(url.length <= 28 * 1024 * 1024 && url.substringBefore(',').endsWith(";base64")) { "Unsupported image data" }
                            MarkdownImageDecoder.decode(Base64.getDecoder().decode(url.substringAfter(',')), 0.0, 0.0)
                        }
                        else -> error("Unsupported image protocol")
                    }
                    diagnostic.complete(decoded.image)
                    require(!decoded.image.isError) { "Image decode failed" }
                    val output = ByteArrayOutputStream()
                    ImageIO.write(SwingFXUtils.fromFXImage(decoded.image, null), "png", output)
                    val data = "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray())
                    Platform.runLater {
                        if (live && expected == revision && entry.preview && currentFile == file && currentFile == area.editor.sourceFile)
                            web.engine.executeScript("embeddedImage($index,${Gson().toJson(data)},${decoded.width},${decoded.height})")
                    }
                } catch (error: Exception) {
                    diagnostic.fail("embedded image load failed", error)
                    Platform.runLater {
                        if (live && expected == revision) web.engine.executeScript("embeddedImage($index,null,0,0)")
                    }
                }
            })
        }

        fun syncReadonly() {
            val next = !area.isEditable
            if (live && ready && web.engine.loadWorker.state == Worker.State.SUCCEEDED && readonly != next) {
                web.engine.executeScript("embeddedReadonly($next)")
                readonly = next
            }
        }

        fun resizeContent() {
            if (!live) return
            web.resize(width(base), Math.max(32.0, entry.height))
            requestLayout()
        }

        private fun saveReadingState() {
            if (live && web.engine.loadWorker.state == Worker.State.SUCCEEDED) {
                try { entry.readingState = web.engine.executeScript("previewReadingState()") as? String }
                catch (_: RuntimeException) { /* 页面正在释放时保留已保存的折叠状态。 */ }
            }
        }

        fun release() {
            saveReadingState()
            live = false
            ready = false
            revision++
            pageTask?.cancel(true)
            pageTask = null
            imageTasks.forEach(MarkdownImageTasks::cancel)
            imageTasks.clear()
            views.remove(this)
            // JS 回调可能触发源码展开；等本次回调返回后再释放页面。
            Platform.runLater { if (!live) web.engine.load(null) }
        }

        /** 固定本地脚本的桥接，不向文档中的脚本或外部导航暴露编辑器。 */
        inner class Bridge {
            fun height(value: Double) {
                if (!live || !ready || disposed || !value.isFinite() || value < 1 || entry !in entries) return
                if (value > 30_000) { entry.html = null; requestRefresh(); return }
                val next = Math.ceil(value)
                if (kotlin.math.abs(entry.height - next) < 1) return
                entry.height = next
                Platform.runLater { if (entry in entries && entry.preview) { styles(entry, true); area.requestLayout() } }
            }
            fun edit(position: Int) {
                if (live && ready && entry in entries) this@MarkdownEmbeddedPresentation.edit(
                    if (position < 0) entry.block.start else position + entry.block.start - entry.renderedStart)
            }
            fun open(destination: String) {
                if (!live || !ready || entry !in entries || version != area.editor.contentVersion || file != area.editor.sourceFile) return
                if (destination.startsWith('#')) {
                    val id = try { java.net.URLDecoder.decode(destination.substring(1).replace("+", "%2B"), "UTF-8") } catch (_: Exception) { destination.substring(1) }
                    anchors[id]?.let { this@MarkdownEmbeddedPresentation.edit(it); return }
                }
                (area.editor as EditorAreaMgrCode).openMarkdownDestination(destination)
            }
            fun copy(value: String) {
                if (!live || !ready || entry !in entries) return
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(javafx.scene.input.ClipboardContent().apply { putString(value) })
            }
            fun task(line: Int, checked: Boolean) {
                if (!live || !ready || entry !in entries || !area.isEditable || area.markdownComposing || version != area.editor.contentVersion || file != area.editor.sourceFile) return
                val offset = snapshot?.lines?.getOrNull(line + entry.block.firstLine - entry.renderedLine)?.taskOffset ?: return
                area.markdownEditing.setTaskChecked(offset, checked)
            }
            fun retry(index: Int) { loadImage(index) }
        }

        override fun computePrefWidth(height: Double): Double = base?.prefWidth(height) ?: 0.0
        override fun layoutChildren() {
            base?.resizeRelocate(0.0, 0.0, width, height)
            web.resizeRelocate(width + MarkdownEditorSupport.textLeftPadding(area), 0.0,
                this@MarkdownEmbeddedPresentation.width(base), Math.max(32.0, entry.height))
        }
    }


    fun onTextChanged(position: Int, removed: String, inserted: String): Boolean {
        generation++
        renderTask?.cancel(true)
        val state = snapshot
        if (taskPositionsCurrent && state != null && removed.length == 1 && inserted.length == 1 &&
            removed[0] in " xX" && inserted[0] in " xX") {
            val line = state.lineAt(position)
            if (state.lines[line].taskOffset == position) {
                // 任务状态不改变块结构、坐标或高度，保留页面并只更新 checkbox。
                val checked = inserted != " "
                for (entry in entries) {
                    if (position !in entry.block.start until entry.block.end) continue
                    val renderedLine = line - entry.block.firstLine + entry.renderedLine
                    entry.taskStates[renderedLine] = checked
                    entry.source = entry.source?.replaceRange(position - entry.block.start,
                        position - entry.block.start + 1, inserted)
                    views.keys.toList().filter { views[it] === entry }.forEach { it.updateTask(renderedLine, checked) }
                }
                version = area.editor.contentVersion
                return true
            }
        }
        taskPositionsCurrent = false
        val delta = inserted.length - removed.length
        val lines = inserted.count { it == '\n' } - removed.count { it == '\n' }
        val end = position + removed.length
        anchors = anchors.mapNotNull { (id, offset) ->
            when {
                offset < position -> id to offset
                offset >= end -> id to offset + delta
                else -> null
            }
        }.toMap()
        val retained = ArrayList<Entry>()
        for (entry in entries) {
            val block = entry.block
            if (end < block.start || end == block.start && inserted.endsWith('\n')) {
                entry.block = block.copy(start = block.start + delta, end = block.end + delta,
                    firstLine = block.firstLine + lines, lastLine = block.lastLine + lines)
                retained.add(entry)
            } else if (position > block.end) retained.add(entry)
            else {
                entry.block = block.copy(lastLine = Math.max(block.firstLine, block.lastLine + lines))
                styles(entry, false)
                views.keys.toList().filter { views[it] === entry }.forEach { it.release() }
            }
        }
        entries = retained
        return false
    }

    fun clear() {
        generation++
        renderTask?.cancel(true)
        renderTask = null
        entries.forEach { styles(it, false) }
        entries = emptyList()
        snapshot = null
        renderedDependencies = null
        renderedBlockCount = 0
        renderingRequired = false
        forceReload = false
        anchors = emptyMap()
        taskPositionsCurrent = false
        views.keys.toList().forEach { it.release() }
    }

    fun destroy() {
        disposed = true
        clear()
        if (!installed) return
        area.removeParagraphGraphicDecorator(this)
        area.caretPositionProperty().removeListener(selectionChanged)
        area.selectionProperty().removeListener(selectionChanged)
        area.focusedProperty().removeListener(selectionChanged)
        area.editableProperty().removeListener(selectionChanged)
        area.widthProperty().removeListener(layoutChanged)
        UIContext.getFontSizeProperty().removeListener(themeChanged)
        UIContext.getFontThemeProperty().removeListener(themeChanged)
        MarkdownThemes.revisionProperty().removeListener(themeChanged)
    }

    companion object {
        private val SOURCE_COORDINATES = Regex("data-source-(start|line)=\"([0-9]+)\"")
        private val CHECKED_ATTRIBUTE = Regex("\\schecked(?:=\"[^\"]*\")?")
        private val INPUT_TAG = Regex("<input\\b[^>]*>")
    }
}

package com.allan.atools.richtext.codearea

import com.allan.atools.Colors
import com.allan.atools.FontTheme
import com.allan.atools.AppTheme
import com.allan.atools.SettingPreferences
import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownEmbeddedBlocks
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot.Range
import com.allan.atools.richtext.codearea.keywordhelper.normalizeHtmlImageDestination
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.manager.*
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.uilibs.richtexts.CodeArea
import com.google.gson.Gson
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.concurrent.Worker
import javafx.scene.Node
import javafx.scene.layout.Pane
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.Future

/** 技术片段独立排版，同行正文保留原生编辑；离屏释放 WebView，选择、搜索及输入时回到源码。 */
class MarkdownEmbeddedPresentation(private val area: EditorArea) {
    private data class CachedFragment(val ranges: List<Range>, val anchors: Map<String, Int>)
    private data class RenderedFragment(val html: String, val comparable: String, val anchors: Map<String, Int>, val source: String)
    private class Entry(var block: MarkdownEmbeddedBlocks.Block) {
        // 空字符串表示注释没有可见内容，null 表示尚未渲染或需要回退源码。
        var html: String? = null
        var comparableHtml: String? = null
        var source: String? = null
        var renderedRanges = block.sourceRanges
        var renderedLine = block.firstLine
        var renderedAnchors = emptyMap<String, Int>()
        var height = 0.0
        var sourceHeight = 32.0
        var leftInset: Double? = null
        var rightInset = 10.0
        var preview = false
        var styledHeight: Double? = null
        var searchHit = false
        var readingState: String? = null
        val taskStates = HashMap<Int, Boolean>()

        fun matchesSource(text: String): Boolean {
            val current = source ?: return false
            return current.length == block.end - block.start && text.regionMatches(block.start, current, 0, current.length)
        }
    }
    // 可见块在段落样式重建、临时脱离场景时仍保留同一个页面，离屏或展开源码后才释放。
    private val views = LinkedHashMap<BlockView, Entry>()
    private var entries = emptyList<Entry>()
    private var snapshot: MarkdownStructureSnapshot? = null
    private var taskPositionsCurrent = false
    private var version = -1L
    private var file: File? = null
    private var generation = 0L
    private var renderTask: Future<*>? = null
    private var anchors = emptyMap<String, Int>()
    private var headingAnchors = emptyMap<String, Int>()
    private var renderedDependencies: List<MarkdownStructureSnapshot.HtmlDependency>? = null
    private var renderedBlockCount = 0
    private var renderingRequired = false
    private var forceReload = false
    private var installed = false
    private var disposed = false
    private var pending = false
    private val selectionChanged = InvalidationListener { requestRefresh() }
    private val viewportChanged = InvalidationListener { requestRefresh() }
    private val sceneChanged = InvalidationListener { requestRefresh() }
    private val zoomChanged: (Set<String>) -> Unit = { keys -> views.keys.toList().forEach { it.syncImages(keys) } }
    private val layoutChanged = InvalidationListener {
        // 保留上次测量高度，等页面回报新高度后再调整，避免滚动文档先收缩再展开。
        views.keys.toList().forEach { it.resizeContent() }
        requestRefresh()
    }
    private val themeChanged = InvalidationListener {
        val state = snapshot ?: return@InvalidationListener
        if (taskPositionsCurrent && version == area.editor.contentVersion && file == area.editor.sourceFile &&
            state === (area.editor as EditorAreaMgrCode).currentMarkdownSnapshot()) {
            render(state, true)
        } else {
            renderingRequired = true
            forceReload = true
        }
    }

    fun apply(state: MarkdownStructureSnapshot) {
        if (disposed) return
        if (!installed) {
            installed = true
            area.markdownImageActions.addZoomListener(zoomChanged)
            area.addParagraphGraphicDecorator(this, ::graphic)
            area.caretPositionProperty().addListener(selectionChanged)
            area.selectionProperty().addListener(selectionChanged)
            area.focusedProperty().addListener(selectionChanged)
            area.editableProperty().addListener(selectionChanged)
            area.visibleParagraphs.addListener(viewportChanged)
            area.sceneProperty().addListener(sceneChanged)
            area.widthProperty().addListener(layoutChanged)
            area.paddingProperty().addListener(layoutChanged)
            UIContext.getFontSizeProperty().addListener(themeChanged)
            UIContext.getFontThemeProperty().addListener(themeChanged)
            SettingPreferences.getStringProp(SettingPreferences.appThemeKey).addListener(themeChanged)
        }
        val previous = entries.associateBy { it.block.start }
        val next = state.embeddedBlocks.map { block ->
            (previous[block.start]?.takeIf { it.block.sourceRanges == block.sourceRanges }?.also { entry ->
                if (entry.block.firstLine != block.firstLine || entry.block.lastLine != block.lastLine ||
                    entry.block.replacesSource != block.replacesSource) {
                    styles(entry, false)
                    entry.styledHeight = null
                    entry.leftInset = null
                }
                entry.block = block
            } ?: Entry(block)).also { entry ->
                entry.searchHit = block.sourceRanges.any { range ->
                    area.getStyleSpans(range.start, range.end).any { "search" in it.style || "temporary" in it.style }
                }
            }
        }
        val retained = next.toHashSet()
        val removed = entries.filterNot { it in retained }
        removed.forEach { styles(it, false) }
        views.keys.toList().filter { views[it] in removed }.forEach { it.release() }
        entries = next
        val currentFile = area.editor.sourceFile
        val currentVersion = area.editor.contentVersion
        val changedFile = file != currentFile
        val changed = snapshot !== state || changedFile || version != currentVersion || !taskPositionsCurrent
        snapshot = state
        taskPositionsCurrent = true
        version = currentVersion
        file = currentFile
        if (changed) headingAnchors = state.headings.associate { it.anchor to state.lines[it.line].start }
        val needsRender = if (renderingRequired) {
            changed || renderTask == null
        } else {
            changed && (changedFile || renderedDependencies != state.htmlDependencies || renderedBlockCount != entries.size ||
                entries.any { !it.matchesSource(state.text) })
        }
        if (needsRender) render(state, changedFile)
        refresh()
    }

    private fun render(state: MarkdownStructureSnapshot, force: Boolean = false) {
        renderingRequired = true
        forceReload = forceReload || force
        val reload = forceReload
        val token = ++generation
        renderTask?.cancel(true)
        renderTask = null
        if (entries.isEmpty()) {
            renderingRequired = false
            forceReload = false
            renderedDependencies = state.htmlDependencies
            renderedBlockCount = 0
            anchors = emptyMap()
            return
        }
        val requestedFile = file
        val requestedVersion = version
        val base = requestedFile?.parentFile?.toURI()?.toASCIIString()
        val reusable: Map<Int, CachedFragment> = if (reload || renderedDependencies != state.htmlDependencies) emptyMap() else buildMap {
            for (entry in this@MarkdownEmbeddedPresentation.entries) {
                if (entry.html != null && entry.matchesSource(state.text)) {
                    put(entry.block.start, CachedFragment(entry.renderedRanges, entry.renderedAnchors))
                }
            }
        }
        renderTask = ThreadUtils.submit {
            try {
                val blocks = state.embeddedBlocks
                val pendingBlocks = blocks.filter { it.start !in reusable }
                val buffers = pendingBlocks.associate { it.start to StringBuilder() }
                if (pendingBlocks.isNotEmpty()) {
                    val nodeIds = HashMap<org.commonmark.node.Node, String>(state.elements.size)
                    state.elements.forEachIndexed { index, element -> nodeIds[element.node] = index.toString() }
                    val detached = LinkedHashSet<org.commonmark.node.Node>()
                    val contextBlocks = ArrayList<MarkdownEmbeddedBlocks.Block>()
                    for (block in pendingBlocks) {
                        var context = block.detailStarts.isNotEmpty()
                        for (source in block.nodes) {
                            if (source in nodeIds) context = true else detached.add(source)
                        }
                        if (context) contextBlocks.add(block)
                    }
                    detached.forEachIndexed { index, source -> nodeIds[source] = "local-$index" }
                    val nodes = ArrayList<org.commonmark.node.Node>()
                    if (contextBlocks.isNotEmpty() && state.htmlDependencies.any { it.kind == "note-reference" || it.kind == "note-definition" }) {
                        nodes.add(state.root)
                    } else {
                        var node = state.root.firstChild
                        var blockIndex = 0
                        while (node != null) {
                            val first = node.sourceSpans.firstOrNull()?.inputIndex
                            val end = node.sourceSpans.lastOrNull()?.let { it.inputIndex + it.length }
                            if (first != null && end != null) {
                                while (blockIndex < contextBlocks.size && contextBlocks[blockIndex].end <= first) blockIndex++
                                val block = contextBlocks.getOrNull(blockIndex)
                                if (block != null && first < block.end && end > block.start) nodes.add(node)
                            }
                            node = node.next
                        }
                    }
                    // 仅脚注需要全文编号；其余只渲染相交根节点，新建 HTML 片段共用同一索引及清理过程。
                    nodes.addAll(detached)
                    val document = MarkdownHtmlRenderer.document(state, base, true, nodes, nodeIds)
                    // 原生 HTML 图片与源码采集共用地址规则，保留 Markdown 图片解析所得的地址。
                    for (image in document.select("img[src]")) {
                        val index = image.attr("data-source-node").toIntOrNull()
                        if (index != null && state.elements.getOrNull(index)?.node is org.commonmark.node.Image) continue
                        val url = image.attr("src")
                        val normalized = normalizeHtmlImageDestination(url)
                        if (url != normalized) image.attr("src", normalized)
                    }
                    // 脚注库把第一条定义的坐标写到整组容器上；各定义必须按自己的源码范围拆开。
                    document.select("section.footnotes").removeAttr("data-source-start").removeAttr("data-source-line").removeAttr("data-source-node")
                    val htmlByNode = document.select("[data-source-node]").groupBy { it.attr("data-source-node") }
                    val details = document.select("details[data-source-start]").associateBy { it.attr("data-source-start").toIntOrNull() }
                    for (block in pendingBlocks) {
                        val contents = ArrayList<Pair<Int, String>>()
                        for (source in block.nodes) {
                            val elements = htmlByNode[nodeIds.getValue(source)]
                            val element = if (source is org.commonmark.ext.footnotes.FootnoteDefinition)
                                elements?.firstOrNull { it.normalName() == "li" } else elements?.firstOrNull()
                            val fragment = element?.clone()
                            fragment?.select("[data-source-node]")?.removeAttr("data-source-node")
                            val html = if (fragment != null && element != null && fragment.normalName() == "li" && source is org.commonmark.ext.footnotes.FootnoteDefinition)
                                "<section class=\"footnotes\"><ol start=\"${element.siblingIndex() + 1}\">${fragment.outerHtml()}</ol></section>"
                            else fragment?.outerHtml().orEmpty()
                            if (html.isNotEmpty()) contents.add(source.sourceSpans.first().inputIndex to html)
                        }
                        for (start in block.detailStarts) {
                            // details 只取显式闭合的元素，不能按坐标范围顺带收进后面的段落。
                            details[start]?.clone()?.let { fragment ->
                                fragment.select("[data-source-node]").removeAttr("data-source-node")
                                contents.add(start to fragment.outerHtml())
                            }
                        }
                        contents.sortedBy { it.first }.forEach { (_, html) ->
                            buffers.getValue(block.start).append("<div class=\"md-local-preview\">$html</div>")
                        }
                    }
                }
                val fragments = HashMap<Int, RenderedFragment>(buffers.size)
                val targets = HashMap<String, Int>()
                // 按源码顺序合并锚点，重名时后面的块覆盖前面的块。
                for (block in blocks) {
                    if (Thread.currentThread().isInterrupted) return@submit
                    val buffer = buffers[block.start]
                    if (buffer != null) {
                        val html = buffer.toString()
                        val fragment = RenderedFragment(html, stableHtml(html, block), htmlAnchors(html),
                            state.text.substring(block.start, block.end))
                        fragments[block.start] = fragment
                        targets.putAll(fragment.anchors)
                        continue
                    }
                    // 页面复用时，锚点继续以实际挂载的 HTML 坐标为基准。
                    val cached = reusable.getValue(block.start)
                    cached.anchors.forEach { (id, position) ->
                        targets[id] = sourcePosition(position, cached.ranges, block.sourceRanges)
                    }
                }
                if (Thread.currentThread().isInterrupted) return@submit
                Platform.runLater {
                    if (!finishRender(token, state, requestedVersion, requestedFile)) return@runLater
                    renderingRequired = false
                    forceReload = false
                    anchors = targets
                    renderedDependencies = state.htmlDependencies
                    renderedBlockCount = entries.size
                    for (entry in entries) {
                        val fragment = fragments[entry.block.start] ?: continue
                        val html = fragment.html
                        entry.source = fragment.source
                        if (html.isEmpty() && !fragment.source.trimStart().startsWith("<!--")) { entry.html = null; continue }
                        if (!reload && entry.html != null && entry.comparableHtml == fragment.comparable) continue
                        entry.html = html
                        entry.comparableHtml = fragment.comparable
                        entry.taskStates.clear()
                        entry.renderedRanges = entry.block.sourceRanges
                        entry.renderedLine = entry.block.firstLine
                        entry.renderedAnchors = fragment.anchors
                        views.keys.toList().filter { views[it] === entry }.forEach {
                            if (html.isEmpty()) it.release() else it.load()
                        }
                    }
                    refresh()
                }
            } catch (error: Exception) {
                if (Thread.currentThread().isInterrupted) return@submit
                Log.e("Render embedded markdown failed", error)
                Platform.runLater {
                    if (!finishRender(token, state, requestedVersion, requestedFile)) return@runLater
                    entries.forEach { it.html = null }
                    refresh()
                }
            }
        }
    }

    private fun finishRender(token: Long, state: MarkdownStructureSnapshot, requestedVersion: Long, requestedFile: File?): Boolean {
        if (disposed || token != generation) return false
        renderTask = null
        return taskPositionsCurrent && snapshot === state && version == requestedVersion &&
            version == area.editor.contentVersion && file == requestedFile && file == area.editor.sourceFile
    }

    private fun htmlAnchors(html: String): Map<String, Int> {
        if (html.isEmpty()) return emptyMap()
        return buildMap {
            Jsoup.parseBodyFragment(html).select("[id]").forEach { element ->
                val position = element.attr("data-source-start").toIntOrNull()
                    ?: element.parents().firstOrNull { it.hasAttr("data-source-start") }
                        ?.attr("data-source-start")?.toIntOrNull()
                if (position != null) put(element.id(), position)
            }
        }
    }

    private fun sourceRangeIndex(ranges: List<Range>, position: Int): Int {
        val index = ranges.binarySearchBy(position) { it.start }
        return if (index >= 0) index else Math.max(0, -index - 2)
    }

    private fun sourcePosition(position: Int, from: List<Range>, to: List<Range>): Int {
        val index = sourceRangeIndex(from, position)
        return position + to[index].start - from[index].start
    }

    private fun stableHtml(html: String, block: MarkdownEmbeddedBlocks.Block): String {
        // 修改片段间的正文只平移后续片段，片段内部的坐标变化仍需要更新页面。
        val normalized = SOURCE_COORDINATES.replace(html) { match ->
            val value = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            if (match.groupValues[1] == "start") {
                val index = sourceRangeIndex(block.sourceRanges, value)
                "data-source-start=\"$index:${value - block.sourceRanges[index].start}\""
            } else "data-source-line=\"${value - block.firstLine}\""
        }
        return INPUT_TAG.replace(normalized) { tag ->
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
        if (area.scene == null) {
            views.keys.toList().forEach { it.release() }
            return
        }
        if (snapshot == null || version != area.editor.contentVersion || entries.isEmpty()) return
        val visible = area.visibleParagraphs.isNotEmpty()
        val first = if (visible) area.firstVisibleParToAllParIndex() else -1
        val last = if (visible) area.lastVisibleParToAllParIndex() else -1
        if (visible) views.keys.toList().filter {
            val block = views.getValue(it).block
            block.graphicLine > last || block.lastLine < first
        }.forEach { it.release() }
        val active = views.values.toSet()
        var available = Math.max(0, MAX_VIEWS - active.size)
        var changed = false
        area.suspendVisibleParsWhileInvoke {
            for (entry in entries) {
                val block = entry.block
                val selection = area.selection
                val editing = (area.isFocused || selection.length > 0) && block.sourceRanges.any { range ->
                    if (selection.length == 0) area.caretPosition in range.start..range.end
                    else selection.start < range.end && selection.end > range.start
                }
                val eligible = area.markdownPreviewEnabled && !area.editor.isRealtimeProcessingLimitReached && entry.html != null &&
                    (entry.html != "" || block.replacesSource) && !editing && !entry.searchHit
                val inViewport = visible && block.graphicLine <= last && block.lastLine >= first
                val hiddenComment = entry.html == ""
                val preview = eligible && (hiddenComment || !inViewport || entry in active || available > 0)
                if (preview && !hiddenComment && inViewport && entry !in active) available--
                if (styles(entry, preview)) changed = true
            }
        }
        views.keys.toList().filter { views[it]?.preview != true }.forEach { it.release() }
        views.keys.toList().forEach { it.syncReadonly() }
        if (changed) area.requestLayout()
    }

    private fun styles(entry: Entry, preview: Boolean): Boolean {
        val height = if (preview) {
            if (entry.html == "") 0.0 else Math.max(32.0, entry.height) +
                if (entry.block.replacesSource) 0.0 else entry.sourceHeight + CONTENT_GAP * 2
        } else -1.0
        if (entry.preview == preview && entry.styledHeight == height) return false
        val changed = entry.preview != preview || entry.styledHeight == null || (entry.styledHeight == 0.0) != (height == 0.0)
        entry.preview = preview
        entry.styledHeight = height
        // 独立块只收起自己的源码；同行片段仅在文字下方增加占高，保留原生鼠标命中。
        val anchor = entry.block.graphicLine
        val last = if (changed && entry.block.replacesSource) entry.block.lastLine else anchor
        for (line in anchor..last) {
            if (line !in area.paragraphs.indices) continue
            val old = area.getParagraph(line).paragraphStyle
            val next = old.filterNot { it.startsWith(CodeArea.EMBEDDED_PREVIEW_HEIGHT_PREFIX) ||
                it.startsWith(CodeArea.EMBEDDED_CONTENT_HEIGHT_PREFIX) }.toMutableList()
            if (preview) next.add(if (entry.block.replacesSource)
                CodeArea.EMBEDDED_PREVIEW_HEIGHT_PREFIX + if (line == anchor) height else 0.0
                else CodeArea.EMBEDDED_CONTENT_HEIGHT_PREFIX + height)
            if (old != next) area.setParagraphStyle(line, next)
            if (changed) area.recreateParagraphGraphic(line)
        }
        return true
    }

    private fun contentWidth(entry: Entry): Double = Math.max(1.0, area.width - area.insets.left - area.insets.right -
        area.documentLayout.lineNumberWidth() - (entry.leftInset ?: MarkdownEditorSupport.textLeftPadding(area)) - entry.rightInset)

    private fun graphic(line: Int, base: Node?): Node? {
        var low = 0
        var high = entries.lastIndex
        while (low <= high) {
            val middle = (low + high) / 2
            if (entries[middle].block.graphicLine <= line) low = middle + 1 else high = middle - 1
        }
        val entry = entries.getOrNull(high)?.takeIf { it.preview && line <= it.block.lastLine } ?: return base
        if (line != entry.block.graphicLine || entry.html == "")
            return Pane().apply { setMinSize(0.0, 0.0); setPrefSize(0.0, 0.0); setMaxSize(0.0, 0.0) }
        if (views.values.toSet().size >= MAX_VIEWS && entry !in views.values) {
            // 临时缺少控件时只展开源码，保留 HTML，等离屏控件释放后重新挂载。
            Platform.runLater {
                if (!disposed && entry in entries && entry.preview && entry !in views.values) {
                    if (views.values.toSet().size < MAX_VIEWS) area.recreateParagraphGraphic(entry.block.graphicLine)
                    else styles(entry, false)
                }
            }
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
        private val webClip = Rectangle()
        private val imageTasks = HashMap<Int, Future<*>>()
        private val loadedImages = HashSet<Int>()
        private var imageUrls = emptyList<String>()
        private var revision = 0L
        private var live = false
        private var ready = false
        @Suppress("DEPRECATION")
        private var scriptWindow: JSObject? = null
        private var readonly: Boolean? = null
        private var pageTask: Future<*>? = null
        private val bridge = Bridge()
        private var source: TextFlow? = null
        private val sourceChanged = InvalidationListener { if (source?.isNeedsLayout == true) requestLayout() }

        init {
            views[this] = entry
            isPickOnBounds = false
            styleClass.add("markdown-embedded-graphic")
            web.isManaged = false
            web.clip = webClip
            web.setMinSize(0.0, 0.0)
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
            base?.let { children.add(it) }
            children.add(web)
            web.engine.loadWorker.stateProperty().addListener { _, _, state ->
                if (live && !ready && state == Worker.State.SUCCEEDED &&
                    web.engine.executeScript("typeof embeddedReady === 'function' && window.embeddedRevision === $revision") == true) {
                    ready = true
                    // Kotlin 编译使用 JDK 25 的兼容接口，运行时由 JavaFX 提供未弃用的实现。
                    @Suppress("DEPRECATION")
                    val window = web.engine.executeScript("window") as JSObject
                    scriptWindow = window
                    window.setMember("embeddedBridge", bridge)
                    web.engine.executeScript("embeddedReady()")
                    syncTasks()
                    syncReadonly()
                    syncImages()
                    entry.readingState?.let { web.engine.executeScript("restorePreviewReadingState(${gson.toJson(it)})") }
                    imageUrls.indices.forEach { loadImage(it) }
                } else if (live && state == Worker.State.FAILED) {
                    entry.html = null
                    requestRefresh()
                }
            }
            sceneProperty().addListener { _, _, scene ->
                // 样式或文档段落替换可能在同一帧内重新挂载，不能因此清空已显示的页面。
                if (scene == null) requestRefresh() else if (!live && entry.preview && entry in entries) {
                    live = true
                    views[this] = entry
                    load()
                }
            }
            parentProperty().addListener { _, _, _ ->
                source?.needsLayoutProperty()?.removeListener(sourceChanged)
                source = null
                requestLayout()
            }
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED) { it.consume() }
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED) { it.consume() }
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED) { it.consume() }
        }

        fun updateBase(next: Node?) {
            if (base === next) return
            base?.let { children.remove(it) }
            base = next
            next?.let { children.add(0, it) }
            requestLayout()
        }

        fun updateTask(line: Int, checked: Boolean) {
            val expected = revision
            Platform.runLater {
                if (live && ready && expected == revision) web.engine.executeScript("embeddedTask($line,$checked)")
            }
        }

        private fun syncTasks() {
            if (entry.taskStates.isEmpty()) return
            val states = entry.taskStates.map { (line, checked) -> listOf(line, checked) }
            web.engine.executeScript("embeddedTasks(${gson.toJson(states)})")
        }

        fun load() {
            if (!live || disposed || entry.html == null) return
            saveReadingState()
            ready = false
            scriptWindow = null
            readonly = null
            revision++
            imageTasks.values.forEach(MarkdownImageTasks::cancel)
            imageTasks.clear()
            loadedImages.clear()
            pageTask?.cancel(true)
            val expected = revision
            val source = entry.html!!
            val baseUrl = file?.parentFile?.toURI()?.toASCIIString()
            val dark = Colors.isDark()
            val themeCss = AppTheme.previewCss(dark)
            val css = "body{overflow:hidden;font-size:${UIContext.getFontSizeProperty().get()}px;font-family:${gson.toJson(FontTheme.fontFamily())},monospace}" +
                "article{max-width:none;margin:0;padding:6px 0;box-sizing:border-box}article>:first-child{margin-top:0}article>:last-child{margin-bottom:0}" +
                ".md-local-preview+.md-local-preview{margin-top:12px}" +
                "img[data-image-loading]{min-width:32px;min-height:24px}"
            val labels = gson.toJson(listOf("markdown.imageBefore", "markdown.imageAfter", "markdown.imageZoom", "markdown.imageEdit").map(Locales::str))
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
                    val html = MarkdownPreviewWindow.renderFragment(body.body().html(), baseUrl, dark, themeCss,
                        body.select(".md-math").isNotEmpty(), body.select(".mermaid-source").isNotEmpty(), css,
                        embeddedScript + "\nwindow.embeddedRevision=$expected;window.embeddedImageLabels=$labels;")
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
            if (!live || !ready || disposed || index !in imageUrls.indices || index in imageTasks || index in loadedImages) return
            val expected = revision
            val currentFile = file
            val url = imageUrls[index]
            val urlStart = url.indexOfFirst { !it.isWhitespace() }
            val loggedUrl = if (url.regionMatches(urlStart, "data:", 0, 5, ignoreCase = true)) "data:(${url.length} chars)" else url
            val diagnostic = MarkdownImageLoadLog("embedded", loggedUrl, currentFile, entry.block.firstLine + 1)
            lateinit var task: Future<*>
            task = MarkdownImageTasks.submit {
                val image = try {
                    val resolved = MarkdownImageLocation.resolve(currentFile, url) ?: error("Unsupported image location")
                    when (resolved.scheme.lowercase()) {
                        "http", "https" -> MarkdownRemoteImageLoader.loadEmbedded(resolved.toASCIIString(), diagnostic)
                        "file" -> MarkdownEmbeddedImageCache.load(MarkdownImageLocation.file(resolved), diagnostic)
                        "data" -> MarkdownEmbeddedImageCache.loadData(url, diagnostic)
                        else -> error("Unsupported image protocol")
                    }
                } catch (error: Exception) {
                    if (Thread.currentThread().isInterrupted) return@submit
                    diagnostic.fail("embedded image load failed", error)
                    null
                }
                if (Thread.currentThread().isInterrupted) return@submit
                Platform.runLater {
                    val window = scriptWindow
                    if (window != null && live && ready && !disposed && expected == revision && imageTasks[index] === task &&
                        entry.preview && currentFile == file && currentFile == area.editor.sourceFile) {
                        imageTasks.remove(index)
                        if (image != null) loadedImages.add(index)
                        window.call("embeddedImage", index, image?.dataUrl, image?.width ?: 0.0, image?.height ?: 0.0)
                    }
                }
            }
            imageTasks[index] = task
        }

        fun syncImages(changedKeys: Set<String>? = null) {
            if (!live || !ready || changedKeys?.isEmpty() == true) return
            val actions = area.markdownImageActions
            val config = imageUrls.mapIndexedNotNull { index, url ->
                val key = actions.imageKey(url)
                if (changedKeys != null && key !in changedKeys) return@mapIndexedNotNull null
                val local = if (changedKeys == null) actions.localFile(url) != null else null
                mapOf("index" to index, "percent" to actions.percentForKey(key), "local" to local)
            }
            if (config.isNotEmpty()) web.engine.executeScript("embeddedImageControls(${gson.toJson(config)})")
        }

        private fun imageTarget(index: Int): MarkdownImageActions.Target? {
            if (!live || !ready || !entry.preview || index !in imageUrls.indices) return null
            val url = imageUrls[index]
            val key = MarkdownImageLocation.resolve(file, url)
            val occurrence = imageUrls.take(index).count { MarkdownImageLocation.resolve(file, it) == key }
            return area.markdownImageActions.targets(entry.block.start, entry.block.end)
                .filter { MarkdownImageLocation.resolve(file, it.destination) == key }.getOrNull(occurrence)
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
            web.resize(contentWidth(entry), Math.max(32.0, entry.height))
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
            source?.needsLayoutProperty()?.removeListener(sourceChanged)
            source = null
            live = false
            ready = false
            scriptWindow = null
            revision++
            pageTask?.cancel(true)
            pageTask = null
            imageTasks.values.forEach(MarkdownImageTasks::cancel)
            imageTasks.clear()
            loadedImages.clear()
            views.remove(this)
            requestRefresh()
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
                    if (position < 0) entry.block.start else sourcePosition(position, entry.renderedRanges, entry.block.sourceRanges))
            }
            fun open(destination: String) {
                if (!live || !ready || entry !in entries || version != area.editor.contentVersion || file != area.editor.sourceFile) return
                if (destination.startsWith('#')) {
                    val id = try { java.net.URLDecoder.decode(destination.substring(1).replace("+", "%2B"), "UTF-8") } catch (_: Exception) { destination.substring(1) }
                    (anchors[id] ?: headingAnchors[id])?.let { this@MarkdownEmbeddedPresentation.edit(it); return }
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
            fun imageJump(index: Int, after: Boolean) {
                imageTarget(index)?.let { target -> Platform.runLater { area.markdownImageActions.jump(target, after) } }
            }
            fun imageZoom(index: Int, x: Double, y: Double) {
                val target = imageTarget(index) ?: return
                val point = web.localToScreen(x, y) ?: return
                Platform.runLater { area.markdownImageActions.showZoom(web, target, point.x, point.y) }
            }
            fun imageEdit(index: Int) {
                imageTarget(index)?.let { target -> Platform.runLater { area.markdownImageActions.editImage(target) } }
            }
        }

        override fun computePrefWidth(height: Double): Double = base?.prefWidth(height) ?: 0.0
        override fun layoutChildren() {
            base?.resizeRelocate(0.0, 0.0, width, height)
            var top = 0.0
            if (!entry.block.replacesSource) {
                val flow = parent?.childrenUnmodifiable?.filterIsInstance<TextFlow>()?.firstOrNull()
                if (source !== flow) {
                    source?.needsLayoutProperty()?.removeListener(sourceChanged)
                    source = flow
                    source?.needsLayoutProperty()?.addListener(sourceChanged)
                }
                flow?.layout()
                val bottom = flow?.children?.filterIsInstance<Text>()?.map { it.boundsInParent.maxY }?.maxOrNull()
                if (flow != null && bottom != null) {
                    val measured = flow.layoutY + bottom + flow.insets.bottom + flow.lineSpacing / 2
                    val changed = kotlin.math.abs(entry.sourceHeight - measured) >= 0.5
                    entry.sourceHeight = measured
                    entry.leftInset = flow.insets.left
                    entry.rightInset = flow.insets.right
                    if (changed) Platform.runLater {
                        if (!disposed && entry in entries && entry.preview) styles(entry, true)
                    }
                }
                top = entry.sourceHeight + CONTENT_GAP
            }
            web.resizeRelocate(width + (entry.leftInset ?: MarkdownEditorSupport.textLeftPadding(area)) - area.estimatedScrollXProperty().value, top,
                contentWidth(entry), Math.max(32.0, entry.height))
            // 页面测高回填前只允许绘制在当前段落已分配的区域，不能遮住下一行正文。
            webClip.width = web.width
            webClip.height = Math.max(0.0, Math.min(web.height, height - top -
                if (entry.block.replacesSource) 0.0 else CONTENT_GAP))
        }
    }


    fun onTextChanged(position: Int, removed: String, inserted: String): Boolean {
        generation++
        renderTask?.cancel(true)
        renderTask = null
        val state = snapshot
        if (taskPositionsCurrent && state != null && removed.length == 1 && inserted.length == 1 &&
            removed[0] in " xX" && inserted[0] in " xX") {
            val line = state.lineAt(position)
            if (state.lines[line].taskOffset == position) {
                // 任务状态不改变块结构、坐标或高度，保留页面并只更新 checkbox。
                val checked = inserted != " "
                for (entry in entries) {
                    if (entry.block.sourceRanges.none { position in it.start until it.end }) continue
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
        val changesLines = '\n' in inserted || '\n' in removed || '\r' in inserted || '\r' in removed
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
                entry.block = block.copy(sourceRanges = block.sourceRanges.map { Range(it.start + delta, it.end + delta) },
                    firstLine = block.firstLine + lines, lastLine = block.lastLine + lines)
                retained.add(entry)
            } else if (position > block.end) {
                retained.add(entry)
            } else if (!changesLines && block.sourceRanges.size > 1 && position > block.start && end < block.end &&
                block.sourceRanges.none { range ->
                    if (removed.isEmpty()) position in range.start..range.end
                    else position < range.end && end > range.start
                }) {
                // 只移动间隙之后的片段，保持现有页面和图片加载状态，等新快照核对结构。
                entry.block = block.copy(sourceRanges = block.sourceRanges.map {
                    if (it.start >= end) Range(it.start + delta, it.end + delta) else it
                })
                retained.add(entry)
            } else {
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
        headingAnchors = emptyMap()
        taskPositionsCurrent = false
        views.keys.toList().forEach { it.release() }
    }

    fun destroy() {
        disposed = true
        clear()
        if (!installed) return
        area.markdownImageActions.removeZoomListener(zoomChanged)
        area.removeParagraphGraphicDecorator(this)
        area.caretPositionProperty().removeListener(selectionChanged)
        area.selectionProperty().removeListener(selectionChanged)
        area.focusedProperty().removeListener(selectionChanged)
        area.editableProperty().removeListener(selectionChanged)
        area.visibleParagraphs.removeListener(viewportChanged)
        area.sceneProperty().removeListener(sceneChanged)
        area.widthProperty().removeListener(layoutChanged)
        area.paddingProperty().removeListener(layoutChanged)
        UIContext.getFontSizeProperty().removeListener(themeChanged)
        UIContext.getFontThemeProperty().removeListener(themeChanged)
        SettingPreferences.getStringProp(SettingPreferences.appThemeKey).removeListener(themeChanged)
    }

    companion object {
        private const val MAX_VIEWS = 8
        private const val CONTENT_GAP = 12.0
        private val SOURCE_COORDINATES = Regex("data-source-(start|line)=\"([0-9]+)\"")
        private val CHECKED_ATTRIBUTE = Regex("\\schecked(?:=\"[^\"]*\")?")
        private val INPUT_TAG = Regex("<input\\b[^>]*>")
        private val gson = Gson()
        private val embeddedScript by lazy {
            MarkdownEmbeddedPresentation::class.java.getResource("/markdown/embedded.js")!!.readText()
        }
    }
}

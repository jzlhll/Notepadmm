package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.MarkdownEditorSupport.supportsMarkdown
import com.allan.atools.richtext.codearea.MarkdownEditorSupport.textLeftPadding
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.uilibs.richtexts.CodeArea
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.InvalidationListener
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.text.Font
import javafx.scene.text.Text
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Paragraph
import org.reactfx.Subscription
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.Future
import java.util.regex.Pattern

/**
 * Markdown 行内图片显示管理器。
 * 独立成段的图片或 HTML img 标签通过段落样式撑高行高，图片在零宽 graphic 容器中溢出绘制。
 * 相对路径基于 Markdown 文件目录解析，图片支持 Typora 的 zoom 百分比，并按视口宽度等比收缩。
 * 段落样式变更不进入仅订阅文本变更的撤销历史。
 */
class MarkdownImageManager(area: EditorArea) {
    companion object {
        private const val REFRESH_DELAY_MS = 220L
        private const val MAX_REFRESH_WAIT_MS = 450L
        /** 原图尺寸未知时的占位显示高度。 */
        private const val PLACEHOLDER_IMAGE_HEIGHT = 180.0
        private const val GAP_TOP = 4.0
        private const val GAP_BOTTOM = 8.0
        /** 与 editor_markdown.css 中的图片边框宽度保持一致。 */
        private const val FRAME_BORDER = 1.0
        private const val PLACEHOLDER_WIDTH = 320.0
        private const val PLACEHOLDER_HEIGHT = 48.0
        private const val IMAGE_CACHE_LIMIT = 48
        /** 使用固定补偿避开行号，不动态测量行号宽度。 */
        private const val LINE_NO_COMPENSATE = 100.0
        private const val IMAGE_PARA_CLASS = "markdown-image-para"
        private const val PREF_HEIGHT_PREFIX = CodeArea.PARAGRAPH_PREF_HEIGHT_PREFIX
        private val HTML_IMG_TAG = Pattern.compile("(?i)<img\\b((?:\"[^\"]*\"|'[^']*'|[^'\">])*?)/?>")
        private val HTML_IMG_ATTR = Pattern.compile("(?i)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))")
        private val STYLE_ZOOM_PATTERN = Pattern.compile("(?i)zoom\\s*:\\s*(\\d+(?:\\.\\d+)?)\\s*%")

        private fun isOverLimit(area: EditorArea?): Boolean =
            area == null || !area.markdownPreviewEnabled || area.editor.isRealtimeProcessingLimitReached

        private fun parseImages(area: EditorArea, text: String, mdFile: File?): List<MarkdownImage> {
            val collector = ImageCollector()
            val state = (area.editor as EditorAreaMgrCode).markdownSnapshot(text)
            state.root.accept(collector)
            if (collector.found.isEmpty()) return emptyList()
            val images = ArrayList<MarkdownImage>()
            for (found in collector.found) {
                if (state.isEmbeddedLine(found.lineIndex)) continue
                val resolved = resolve(mdFile, found.destination) ?: continue
                images.add(MarkdownImage(found.lineIndex, found.alt, resolved, found.styleZoom, found.width))
            }
            return images.toList()
        }

        /** 解析 img 标签属性，缺少 src 时返回 null。 */
        private fun parseImgTag(attributes: String): ImgAttr? {
            var src: String? = null
            var alt: String? = null
            var zoom = 1.0
            var width = 0.0
            val matcher = HTML_IMG_ATTR.matcher(attributes)
            while (matcher.find()) {
                val name = matcher.group(1).lowercase(Locale.ROOT)
                val value = matcher.group(3) ?: matcher.group(4) ?: matcher.group(5) ?: continue
                when (name) {
                    "src" -> src = value
                    "alt" -> alt = value
                    "width" -> try {
                        width = Math.max(0.0, Math.min(10000.0, value.toDouble()))
                    } catch (_: NumberFormatException) { }
                    "style" -> {
                        val zoomMatcher = STYLE_ZOOM_PATTERN.matcher(value)
                        if (zoomMatcher.find()) zoom = zoomMatcher.group(1).toDouble() / 100.0
                    }
                }
            }
            if (src.isNullOrBlank()) return null
            return ImgAttr(src.trim(), alt, zoom, width)
        }

        private fun resolve(mdFile: File?, destination: String): Resolved? {
            val uri = MarkdownImageLocation.resolve(mdFile, destination) ?: return null
            val scheme = uri.scheme.lowercase(Locale.ROOT)
            if (scheme == "http" || scheme == "https") return Resolved(uri.toASCIIString(), null, true)
            if (scheme != "file") return null
            return try {
                Resolved(uri.toASCIIString(), MarkdownImageLocation.file(uri), false)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    private var textChanges: Subscription? = null
    private data class CachedImage(val image: WeakReference<Image>, val width: Double, val height: Double)
    // 图片节点和共享缓存负责强引用，正文索引只保留尺寸和弱引用。
    private val imageCache = object : LinkedHashMap<String, CachedImage?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedImage?>?): Boolean = size > IMAGE_CACHE_LIMIT
    }
    private var currentArea: EditorArea? = null
    private val refreshScheduler = LatestRefreshScheduler(REFRESH_DELAY_MS, MAX_REFRESH_WAIT_MS, ::startRefresh)
    private var imageByLine: Map<Int, MarkdownImage> = emptyMap()
    private var runtimeActive = false
    private var destroyed = false
    private var parseTask: Future<*>? = null
    private var cachedLineHeight = -1.0
    private var layoutPending = false
    private val layoutChanged = InvalidationListener {
        if (!layoutPending) {
            layoutPending = true
            Platform.runLater {
                layoutPending = false
                val area = currentArea
                if (destroyed || !runtimeActive || area == null) return@runLater
                cachedLineHeight = -1.0
                area.suspendVisibleParsWhileInvoke {
                    for (info in imageByLine.values) applyImageParagraphStyle(area, info.lineIndex, info)
                }
            }
        }
    }

    init { bindEditor(area) }

    fun destroy() {
        destroyed = true
        unbindEditor()
        refreshScheduler.dispose()
    }

    fun refreshCurrentFile(area: EditorArea) = bindEditor(area)

    private fun bindEditor(area: EditorArea) {
        unbindEditor()
        currentArea = area
        if (!supportsMarkdown(area)) return
        textChanges = area.plainTextChanges().subscribe { onTextChanged(it.position, it.removed, it.inserted) }
        if (isOverLimit(area)) return
        activateRuntime()
        refreshScheduler.startNow()
    }

    private fun unbindEditor() {
        val area = currentArea ?: return
        textChanges?.unsubscribe()
        textChanges = null
        deactivateRuntime()
        clearAllImageStyles(area)
        currentArea = null
    }

    private fun onTextChanged(position: Int, removed: String, inserted: String) {
        if (isOverLimit(currentArea)) {
            deactivateRuntime()
            clearAllImageStyles(currentArea)
            return
        }
        activateRuntime()
        val area = currentArea!!
        val first = area.offsetToPosition(Math.min(position, area.length), org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).major
        val removedLines = removed.count { it == '\n' }
        val insertedLines = inserted.count { it == '\n' }
        val delta = insertedLines - removedLines
        val next = LinkedHashMap<Int, MarkdownImage>()
        val moved = HashSet<Int>()
        var touched = false
        for (info in imageByLine.values) {
            if (info.lineIndex >= first && info.lineIndex <= first + removedLines) {
                MarkdownImageTasks.cancel(info.loadTask)
                touched = true
                continue
            }
            if (info.lineIndex > first + removedLines && delta != 0) {
                info.lineIndex += delta
                moved.add(info.lineIndex)
            }
            next[info.lineIndex] = info
        }
        imageByLine = next
        // 只展开正在改动的图片行，其余图片保留节点、加载状态和缓存。
        if (touched) for (line in first..first + insertedLines) {
            clearImageParagraphStyle(area, line)
            area.recreateParagraphGraphic(line)
        }
        for (line in moved) area.recreateParagraphGraphic(line)
        refreshScheduler.request()
    }

    private fun startRefresh(requestId: Long) {
        val area = currentArea
        if (destroyed || !runtimeActive || !supportsMarkdown(area) || isOverLimit(area)) {
            refreshScheduler.complete(requestId, null)
            return
        }
        area!!
        val contentVersion = area.editor.contentVersion
        val text = area.text
        val mdFile = area.editor.sourceFile
        parseTask = ThreadUtils.submit {
            var images: List<MarkdownImage>? = null
            try {
                images = parseImages(area, text, mdFile)
            } catch (e: RuntimeException) {
                Log.e("parse markdown images failed", e)
            }
            val result = images
            Platform.runLater {
                area.runAfterMarkdownComposition { finishRefresh(area, contentVersion, requestId, result) }
            }
        }
    }

    private fun finishRefresh(area: EditorArea, contentVersion: Long, parsedRequestId: Long, parsed: List<MarkdownImage>?) {
        refreshScheduler.complete(parsedRequestId) {
            parseTask = null
            if (parsed != null && !destroyed && area === currentArea
                && area.editor.contentVersion == contentVersion && !isOverLimit(area)
            ) applyImages(area, contentVersion, parsed)
        }
    }

    private fun applyImages(area: EditorArea, contentVersion: Long, parsed: List<MarkdownImage>) {
        if (destroyed || area !== currentArea || area.editor.contentVersion != contentVersion || isOverLimit(area)) return
        cachedLineHeight = -1.0
        val newByLine = LinkedHashMap<Int, MarkdownImage>()
        for (info in parsed) {
            val old = imageByLine[info.lineIndex]
            val same = old != null && old.key == info.key && old.alt == info.alt && old.width == info.width && old.styleZoom == info.styleZoom
            newByLine.putIfAbsent(info.lineIndex, if (same) old else info)
        }
        val changed = HashSet<Int>()
        for ((line, old) in imageByLine) {
            if (newByLine[line] !== old) MarkdownImageTasks.cancel(old.loadTask)
            if (line !in newByLine) {
                clearImageParagraphStyle(area, line)
                changed.add(line)
            }
        }
        for ((line, info) in newByLine) if (imageByLine[line] !== info) changed.add(line)
        imageByLine = newByLine
        for ((line, info) in newByLine) applyImageParagraphStyle(area, line, info)
        for (line in changed) area.recreateParagraphGraphic(line)
    }

    private fun activateRuntime() {
        val area = currentArea ?: return
        if (runtimeActive) return
        runtimeActive = true
        area.widthProperty().addListener(layoutChanged)
        area.paddingProperty().addListener(layoutChanged)
        area.addParagraphGraphicDecorator(this, ::createGraphic)
    }

    private fun deactivateRuntime() {
        currentArea?.let {
            it.removeParagraphGraphicDecorator(this)
            it.widthProperty().removeListener(layoutChanged)
            it.paddingProperty().removeListener(layoutChanged)
        }
        for (info in imageByLine.values) MarkdownImageTasks.cancel(info.loadTask)
        runtimeActive = false
        invalidateRefresh()
    }

    /** 图片通过零宽容器溢出绘制在标签行下方，行号节点保持在上层。 */
    private fun createGraphic(index: Int, base: Node?): Node? {
        val area = currentArea ?: return base
        val info = imageByLine[index] ?: return base
        val imageNode = createImageNode(area, info)
        val box = MarkdownImageGraphic(base, imageNode,
            if (info.sourceHeight > 0) info.sourceHeight else lineHeight(area), GAP_TOP, GAP_BOTTOM
        ) { height ->
            if (Math.abs(info.sourceHeight - height) >= 0.5) {
                info.sourceHeight = height
                // 下一次脉冲再回填样式，避免在段落布局过程中修改文档样式。
                Platform.runLater {
                    if (!destroyed && area === currentArea && !isOverLimit(area) && imageByLine[info.lineIndex] === info) {
                        applyImageParagraphStyle(area, info.lineIndex, info)
                    }
                }
            }
        }
        // 行号固定在左侧；图片反向减去 scrollX，以便随文本水平滚动。
        imageNode.layoutXProperty().bind(Bindings.createDoubleBinding(
            { textLeftPadding(area) + LINE_NO_COMPENSATE - area.estimatedScrollXProperty().value },
            area.paddingProperty(), area.estimatedScrollXProperty()))
        return box
    }

    private fun createImageNode(area: EditorArea, info: MarkdownImage): Node {
        val image = cachedImage(info.key)
        if (image == null) {
            if (isKnownFailure(info.key)) return createPlaceholder(area, info, Locales.str("markdownImageLoadFailed"))
            beginLoad(area, info)
            return createPlaceholder(area, info, Locales.str("markdownImageLoading"))
        }
        if (image.isError) return createPlaceholder(area, info, Locales.str("markdownImageLoadFailed"))
        val view = ImageView(image)
        view.isPreserveRatio = true
        view.fitHeightProperty().bind(Bindings.createDoubleBinding({ displaySize(area, info)[0] }, area.widthProperty(), area.paddingProperty()))
        view.fitWidthProperty().bind(Bindings.createDoubleBinding({ displaySize(area, info)[1] }, area.widthProperty(), area.paddingProperty()))
        return StackPane(view).apply {
            styleClass.add("markdown-image-frame")
            setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE)
            prefWidthProperty().bind(view.fitWidthProperty().add(FRAME_BORDER * 2))
            prefHeightProperty().bind(view.fitHeightProperty().add(FRAME_BORDER * 2))
        }
    }

    private fun createPlaceholder(owner: EditorArea, info: MarkdownImage, message: String): Node {
        val text = if (info.alt.isNullOrBlank()) message else info.alt + " - " + message
        val label = Label(text)
        label.styleClass.add("markdown-image-placeholder")
        val frame = StackPane(label)
        frame.styleClass.add("markdown-image-frame")
        frame.prefHeight = PLACEHOLDER_HEIGHT
        frame.prefWidthProperty().bind(Bindings.createDoubleBinding(
            { Math.min(PLACEHOLDER_WIDTH, availableImageWidth(owner)) }, owner.widthProperty(), owner.paddingProperty()))
        frame.minWidth = Region.USE_PREF_SIZE
        label.isWrapText = true
        frame.setOnMouseClicked {
            val area = currentArea ?: return@setOnMouseClicked
            if (destroyed || imageByLine[info.lineIndex] !== info || info.loading) return@setOnMouseClicked
            info.loadLog?.event("retry requested")
            removeCachedImage(info.key)
            info.loading = false
            beginLoad(area, info)
            area.recreateParagraphGraphic(info.lineIndex)
        }
        return frame
    }

    /** 返回显示高度和宽度；按原图尺寸缩放，原图未知时使用占位高度，宽度超限时等比收缩。 */
    private fun displaySize(area: EditorArea, info: MarkdownImage): DoubleArray {
        synchronized(this) {
            imageCache[info.key]?.let {
                info.imageWidth = it.width
                info.imageHeight = it.height
            }
        }
        val aspect = if (info.imageWidth > 0 && info.imageHeight > 0) info.imageWidth / info.imageHeight else 4.0 / 3.0
        var height = if (info.imageHeight > 0) info.imageHeight * info.styleZoom else PLACEHOLDER_IMAGE_HEIGHT
        if (info.width > 0) height = info.width / aspect
        val maxWidth = availableImageWidth(area)
        if (height * aspect > maxWidth) height = maxWidth / aspect
        return doubleArrayOf(height, height * aspect)
    }

    private fun availableImageWidth(area: EditorArea): Double =
        Math.max(1.0, area.width - area.insets.left - area.insets.right - textLeftPadding(area) - LINE_NO_COMPENSATE - FRAME_BORDER * 2)

    private fun totalParagraphHeight(area: EditorArea, info: MarkdownImage): Int {
        val nodeHeight = if (isFailedImage(info)) PLACEHOLDER_HEIGHT else displaySize(area, info)[0] + FRAME_BORDER * 2
        val sourceHeight = if (info.sourceHeight > 0) info.sourceHeight else lineHeight(area)
        return Math.ceil(sourceHeight + GAP_TOP + nodeHeight + GAP_BOTTOM).toInt()
    }

    private fun applyImageParagraphStyle(area: EditorArea, index: Int, info: MarkdownImage) {
        if (index < 0 || index >= area.paragraphs.size) return
        val existing = ArrayList(area.getParagraph(index).paragraphStyle)
        val merged = existing.filterTo(ArrayList()) { it != IMAGE_PARA_CLASS && !it.startsWith(PREF_HEIGHT_PREFIX) }
        merged.add(IMAGE_PARA_CLASS)
        merged.add(PREF_HEIGHT_PREFIX + totalParagraphHeight(area, info))
        if (merged != existing) area.setParagraphStyle(index, merged)
    }

    private fun clearImageParagraphStyle(area: EditorArea, index: Int) {
        if (index < 0 || index >= area.paragraphs.size) return
        val existing = ArrayList(area.getParagraph(index).paragraphStyle)
        val merged = existing.filterTo(ArrayList()) { it != IMAGE_PARA_CLASS && !it.startsWith(PREF_HEIGHT_PREFIX) }
        if (merged.size != existing.size) area.setParagraphStyle(index, merged)
    }

    private fun clearAllImageStyles(area: EditorArea?) {
        if (area == null) return
        for (line in imageByLine.keys) {
            clearImageParagraphStyle(area, line)
            area.recreateParagraphGraphic(line)
        }
        imageByLine = emptyMap()
    }

    private fun beginLoad(area: EditorArea, info: MarkdownImage) {
        if (info.loading) return
        info.loading = true
        val diagnostic = MarkdownImageLoadLog("body", info.key, area.editor.sourceFile, info.lineIndex + 1)
        info.loadLog = diagnostic
        info.loadTask = MarkdownImageTasks.submit {
            val decoded = try {
                loadImage(info, diagnostic)
            } catch (e: Exception) {
                diagnostic.fail("load threw exception", e)
                null
            }
            if (Thread.currentThread().isInterrupted) return@submit
            val image = decoded?.image
            diagnostic.complete(image)
            cacheImage(info.key, decoded)
            if (!ThreadUtils.sBeClosing && !Thread.currentThread().isInterrupted) {
                Platform.runLater { onImageLoaded(area, info, image, diagnostic) }
            } else diagnostic.event("result skipped application closing or worker interrupted")
        }
    }

    private fun onImageLoaded(area: EditorArea, info: MarkdownImage, image: Image?, diagnostic: MarkdownImageLoadLog?) {
        diagnostic?.complete(image)
        if (area.markdownComposing) {
            area.runAfterMarkdownComposition { onImageLoaded(area, info, image, diagnostic) }
            return
        }
        info.loading = false
        info.loadTask = null
        val index = info.lineIndex
        if (destroyed || area !== currentArea || isOverLimit(area) || imageByLine[index] !== info) {
            diagnostic?.event("result skipped stale image or inactive document")
            return
        }
        applyImageParagraphStyle(area, index, info)
        area.recreateParagraphGraphic(index)
    }

    private fun loadImage(info: MarkdownImage, diagnostic: MarkdownImageLoadLog): MarkdownImageDecoder.Result? {
        val resolved = info.resolved
        if (resolved.remote) return MarkdownRemoteImageLoader.loadDecoded(resolved.url, diagnostic, 0.0, 0.0)
        val file = resolved.file
        if (file == null || !file.isFile) throw IOException("Local image file missing")
        return MarkdownImageDecoder.decode(file, 0.0, 0.0)
    }

    @Synchronized private fun cachedImage(key: String): Image? = imageCache[key]?.image?.get()
    @Synchronized private fun cacheImage(key: String, decoded: MarkdownImageDecoder.Result?) {
        imageCache[key] = decoded?.let { CachedImage(WeakReference(it.image), it.width, it.height) }
    }
    @Synchronized private fun removeCachedImage(key: String) { imageCache.remove(key) }
    @Synchronized private fun isKnownFailure(key: String): Boolean = imageCache.containsKey(key) && imageCache[key] == null

    private fun isFailedImage(info: MarkdownImage): Boolean {
        val image = cachedImage(info.key)
        return if (image == null) isKnownFailure(info.key) else image.isError
    }

    private fun lineHeight(area: EditorArea): Double {
        if (cachedLineHeight > 0) return cachedLineHeight
        var font: Font? = null
        for (node in area.lookupAll(".text")) {
            if (node is Text && node.text.isNotBlank()) {
                font = node.font
                break
            }
        }
        val probe = Text("Ag")
        probe.font = font ?: Font.font("monospace", 14.0)
        cachedLineHeight = probe.layoutBounds.height
        return cachedLineHeight
    }

    private fun invalidateRefresh() {
        refreshScheduler.invalidate()
        val task = parseTask
        parseTask = null
        task?.cancel(true)
    }

    /** 收集整段只有一张图片的段落，以及独立的 HTML img 标签。 */
    private class ImageCollector : AbstractVisitor() {
        val found = ArrayList<FoundImage>()

        override fun visit(paragraph: Paragraph) {
            val first = paragraph.firstChild
            if (first is org.commonmark.node.Image && first.next == null) {
                val spans = first.sourceSpans
                if (spans.isNotEmpty()) found.add(FoundImage(spans[0].lineIndex, first.destination, altOf(first), 1.0, 0.0))
            } else if (first is HtmlInline && first.next == null) {
                addHtmlImgs(first.literal, firstLine(first), found)
            }
            visitChildren(paragraph)
        }

        override fun visit(block: HtmlBlock) = addHtmlImgs(block.literal, firstLine(block), found)

        private fun firstLine(node: org.commonmark.node.Node): Int = node.sourceSpans.firstOrNull()?.lineIndex ?: 0

        /** 图片行号按标签之前的换行数叠加。 */
        private fun addHtmlImgs(literal: String?, startLine: Int, found: MutableList<FoundImage>) {
            if (literal == null) return
            val matcher = HTML_IMG_TAG.matcher(literal)
            while (matcher.find()) {
                val parsed = parseImgTag(matcher.group(1)) ?: continue
                var count = 0
                for (index in 0 until matcher.start()) if (literal[index] == '\n') count++
                found.add(FoundImage(startLine + count, parsed.src, parsed.alt, parsed.zoom, parsed.width))
            }
        }

        private fun altOf(image: org.commonmark.node.Image): String {
            val builder = StringBuilder()
            var child = image.firstChild
            while (child != null) {
                if (child is org.commonmark.node.Text) builder.append(child.literal)
                child = child.next
            }
            return builder.toString()
        }
    }

    private data class ImgAttr(val src: String, val alt: String?, val zoom: Double, val width: Double)
    private data class FoundImage(val lineIndex: Int, val destination: String, val alt: String?, val styleZoom: Double, val width: Double)
    private data class Resolved(val url: String, val file: File?, val remote: Boolean)
    private class MarkdownImage(var lineIndex: Int, val alt: String?, val resolved: Resolved, val styleZoom: Double, val width: Double) {
        val key = resolved.url
        var imageWidth = -1.0
        var imageHeight = -1.0
        var sourceHeight = -1.0
        var loading = false
        var loadTask: Future<*>? = null
        var loadLog: MarkdownImageLoadLog? = null
    }
}

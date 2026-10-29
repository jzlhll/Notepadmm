package com.allan.atools.richtext.codearea

import com.allan.atools.UIContext
import com.allan.atools.bean.SearchParams
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperAbstract
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperImplMarkdown
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownAstCache
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.threads.ClosedDroppedHandler
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.allan.baseparty.Action0
import com.allan.baseparty.handler.HandlerThread
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.scene.control.Tab
import javafx.util.Duration
import org.reactfx.Subscription
import java.io.File
import java.awt.Desktop
import java.net.MalformedURLException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

/** 协调代码高亮、Markdown 解析及样式回写。 */
class EditorAreaMgrCode(
    area: EditorArea, sourceFile: File?, tab: Tab?, documentState: EditorDocumentState?
) : EditorAreaMgr(area, sourceFile, tab, documentState) {
    companion object {
        private const val STYLE_INTERVAL_MS = 120L
        @JvmField var sJavaKeywordCssFileLoad = false
        @Volatile private var sStylerHandler: ClosedDroppedHandler? = null

        private fun stylerHandler(): ClosedDroppedHandler {
            sStylerHandler?.let { return it }
            return synchronized(EditorAreaMgrCode::class.java) {
                sStylerHandler ?: run {
                    val thread = HandlerThread("code-styler")
                    thread.start()
                    ClosedDroppedHandler(thread.looper).also { sStylerHandler = it }
                }
            }
        }

        private fun ensureKeywordStylesheet(helper: EditorKeywordHelperAbstract?) {
            if (sJavaKeywordCssFileLoad || helper == null) return
            sJavaKeywordCssFileLoad = true
            try {
                val url = ResLocation.getURLByRealPath(ResLocation.getRealPath("css", "editor_keywords.css"))
                UIContext.mainController!!.stage.scene.stylesheets.add(url.toExternalForm())
            } catch (e: MalformedURLException) {
                Log.e("load editor keyword stylesheet failed", e)
            }
        }

        private fun sameSearch(first: SearchParams?, second: SearchParams?): Boolean {
            val firstEmpty = first?.words.isNullOrEmpty()
            val secondEmpty = second?.words.isNullOrEmpty()
            if (firstEmpty || secondEmpty) return firstEmpty == secondEmpty
            return first.isSameGeneric(second) && first.textColor == second.textColor
                && first.bgColor == second.bgColor && first.highLight == second.highLight && first.enable == second.enable
        }
    }

    private var mKeywordHelper: EditorKeywordHelperAbstract? = null
    private val markdownAstCache = MarkdownAstCache()
    private val styleRequestId = AtomicLong()
    private val styleDelay = PauseTransition()
    private var styleTextSubscription: Subscription? = null
    @Volatile private var pendingStyleTask: Runnable? = null
    private var latestTemporaryText: SearchParams? = null
    private var latestSearchText: SearchParams? = null
    private var latestEndCallback: Action0? = null
    private var styleDirty = false
    private var styleRunning = false
    private var runningStyleRequestId = 0L
    @Volatile private var styleOptionsVersion = 0L
    private var lastStyleStartedAt = 0L
    private var currentMarkdownSnapshot: MarkdownStructureSnapshot? = null
    private var markdownSnapshotVersion = -1L

    init {
        styleDelay.setOnFinished { startLatestStyle() }
        mKeywordHelper = createKeywordHelper(sourceFile)
        area.styleClass.remove("markdown-editor")
        if (mKeywordHelper is EditorKeywordHelperImplMarkdown) area.styleClass.add("markdown-editor")
        area.documentLayout.apply(this.documentState)
        ensureKeywordStylesheet(mKeywordHelper)
        if (mKeywordHelper != null) {
            bindStyleTextChanges()
            requestStyle(null, null, null, true)
        }
    }

    override fun isEditorCodeMode(): Boolean = mKeywordHelper != null

    fun bindKeywordHelper(sourceFile: File?) {
        resetStyleScheduler()
        (getArea() as? EditorArea)?.markdownPresentation?.clear()
        mKeywordHelper = createKeywordHelper(sourceFile)
        val area = getArea()!!
        area.styleClass.remove("markdown-editor")
        if (mKeywordHelper is EditorKeywordHelperImplMarkdown) area.styleClass.add("markdown-editor")
        (area as EditorArea).documentLayout.apply(documentState)
        ensureKeywordStylesheet(mKeywordHelper)
        bindStyleTextChanges()
        if (mKeywordHelper == null) {
            if (area.length > 0) area.setStyle(0, area.length, area.initialTextStyle)
            return
        }
        requestStyle(null, null, null, true)
    }

    private fun bindStyleTextChanges() {
        styleTextSubscription?.unsubscribe()
        styleTextSubscription = null
        val area = getArea()
        if (mKeywordHelper != null && area != null) {
            styleTextSubscription = area.plainTextChanges().subscribe { change ->
                if (mKeywordHelper is EditorKeywordHelperImplMarkdown && area is EditorArea) {
                    area.markdownPresentation.onTextChanged(change.position, change.removed, change.inserted)
                }
                styleDirty = true
                scheduleLatestStyle(false)
            }
        }
    }

    private fun createKeywordHelper(sourceFile: File?): EditorKeywordHelperAbstract? {
        val helper = EditorKeywordHelperFactory.create(sourceFile ?: File(documentState.displayName))
        if (helper is EditorKeywordHelperImplMarkdown) helper.setAstCache(markdownAstCache)
        return helper
    }

    fun markdownSnapshot(text: String): MarkdownStructureSnapshot = markdownAstCache.snapshot(text)

    fun currentMarkdownSnapshot(): MarkdownStructureSnapshot? =
        if (markdownSnapshotVersion == contentVersion) currentMarkdownSnapshot else null

    fun openMarkdownLinkAt(position: Int) {
        val helper = mKeywordHelper as? EditorKeywordHelperImplMarkdown ?: return
        if (isDestroyed()) return
        val text = getArea()!!.text
        ThreadUtils.execute {
            helper.findLinkDestination(text, position)?.let { openMarkdownDestination(it) }
        }
    }

    fun openMarkdownDestination(destination: String) {
        if (isDestroyed()) return
        ThreadUtils.execute {
            try {
                val bytes = destination.toByteArray(StandardCharsets.UTF_8)
                val encoded = StringBuilder(bytes.size)
                // 保留 URL 分隔符与已有百分号编码，其余字符按 UTF-8 编码。
                var index = 0
                while (index < bytes.size) {
                    val value = bytes[index].toInt() and 0xff
                    if (value == '%'.code && index + 2 < bytes.size
                        && Character.digit(bytes[index + 1].toInt().toChar(), 16) >= 0
                        && Character.digit(bytes[index + 2].toInt().toChar(), 16) >= 0
                    ) {
                        encoded.append('%').append(bytes[++index].toInt().toChar()).append(bytes[++index].toInt().toChar())
                    } else if (value in 'a'.code..'z'.code || value in 'A'.code..'Z'.code
                        || value in '0'.code..'9'.code || "-._~:/?#[]@!$&'()*+,;=".indexOf(value.toChar()) >= 0
                    ) {
                        encoded.append(value.toChar())
                    } else {
                        encoded.append('%').append(Character.forDigit(value ushr 4, 16))
                            .append(Character.forDigit(value and 0xf, 16))
                    }
                    index++
                }
                val uri = URI(encoded.toString())
                if (uri.scheme == null || "file".equals(uri.scheme, true)
                    || ResLocation.isWindow && uri.scheme.length == 1
                ) {
                    Platform.runLater {
                        val area = getArea() as? EditorArea
                        if (!isDestroyed() && area != null) MarkdownNavigation.open(area, uri)
                    }
                    return@execute
                }
                if ("mailto".equals(uri.scheme, true)) {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                        Desktop.getDesktop().mail(uri)
                    }
                    return@execute
                }
                if ((!"http".equals(uri.scheme, true) && !"https".equals(uri.scheme, true)) || uri.rawAuthority == null) {
                    return@execute
                }
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Log.e("open markdown link: system browser is unavailable")
                    return@execute
                }
                Desktop.getDesktop().browse(uri)
            } catch (e: Exception) {
                Log.e("open markdown link failed", e)
            }
        }
    }

    override fun trigger(temporaryText: SearchParams?, searchText: SearchParams?, end: Action0?) {
        requestStyle(temporaryText, searchText, end, true)
    }

    fun refreshMarkdownPresentation() = requestStyle(latestTemporaryText, latestSearchText, null, true)

    /** 正文订阅已负责版本刷新，底部搜索只在条件改变时重新计算样式。 */
    fun triggerSearch(temporaryText: SearchParams?, searchText: SearchParams?, endCallback: Action0?) {
        if (sameSearch(latestTemporaryText, temporaryText) && sameSearch(latestSearchText, searchText)) {
            endCallback?.invoke()
            return
        }
        trigger(temporaryText, searchText, endCallback)
    }

    private fun requestStyle(temporaryText: SearchParams?, searchText: SearchParams?, endSetStyleCallback: Action0?, immediate: Boolean) {
        val temporaryCopy = temporaryText?.copy()
        val searchCopy = searchText?.copy()
        val action = Runnable {
            if (mKeywordHelper == null || isDestroyed()) return@Runnable
            latestTemporaryText = temporaryCopy
            latestSearchText = searchCopy
            latestEndCallback = endSetStyleCallback
            styleOptionsVersion++
            styleDirty = true
            scheduleLatestStyle(immediate)
        }
        if (Platform.isFxApplicationThread()) action.run() else Platform.runLater(action)
    }

    private fun scheduleLatestStyle(immediate: Boolean) {
        if (!styleDirty || styleRunning || mKeywordHelper == null || isDestroyed()) return
        val elapsed = (System.nanoTime() - lastStyleStartedAt) / 1_000_000
        if (immediate || lastStyleStartedAt == 0L || elapsed >= STYLE_INTERVAL_MS) {
            startLatestStyle()
            return
        }
        styleDelay.duration = Duration.millis((STYLE_INTERVAL_MS - elapsed).toDouble())
        styleDelay.playFromStart()
    }

    private fun startLatestStyle() {
        if (!styleDirty || styleRunning || mKeywordHelper == null || isDestroyed()) return
        if (disableStylerIfNeeded(latestEndCallback)) {
            styleDirty = false
            latestEndCallback = null
            return
        }
        val area = getArea() as? EditorArea ?: return
        styleDelay.stop()
        styleDirty = false
        styleRunning = true
        lastStyleStartedAt = System.nanoTime()
        val requestId = styleRequestId.incrementAndGet()
        runningStyleRequestId = requestId
        val optionsVersion = styleOptionsVersion
        val contentVersion = contentVersion
        val text = area.text
        val styledDocument = area.content.snapshot()
        val temporaryText = latestTemporaryText
        val searchText = latestSearchText
        val endCallback = latestEndCallback
        latestEndCallback = null
        val helper = mKeywordHelper!!
        val renderMarkdown = helper is EditorKeywordHelperImplMarkdown && area.markdownPreviewEnabled
        if (helper is EditorKeywordHelperImplMarkdown) helper.setPreviewEnabled(renderMarkdown)

        val task = Runnable {
            var update: EditorKeywordHelperAbstract.StyleUpdate? = null
            var structure: MarkdownStructureSnapshot? = null
            try {
                if (canComputeStyle(requestId, optionsVersion, contentVersion, helper)) {
                    val currentSpans = styledDocument.getStyleSpans(0, text.length)
                    val syntaxSpans = if (helper is EditorKeywordHelperImplMarkdown)
                        MarkdownSyntaxPresentation.highlightingStyles(currentSpans) else currentSpans
                    update = helper.computeStyleUpdate(text, temporaryText, searchText, syntaxSpans) {
                        canComputeStyle(requestId, optionsVersion, contentVersion, helper)
                    }
                    if (update != null && renderMarkdown && canComputeStyle(requestId, optionsVersion, contentVersion, helper)) {
                        structure = markdownAstCache.snapshot(text)
                    }
                }
            } catch (e: RuntimeException) {
                Log.e("Code styler failed", e)
            }
            val result = update
            val resultStructure = structure
            Platform.runLater {
                area.runAfterMarkdownComposition {
                    area.markdownSyntax.runAfterPointer {
                        finishStyle(requestId, optionsVersion, contentVersion, helper, area, result, resultStructure, endCallback)
                    }
                }
            }
        }
        pendingStyleTask = task
        stylerHandler().post(task)
    }

    private fun finishStyle(requestId: Long, optionsVersion: Long, contentVersion: Long,
                            helper: EditorKeywordHelperAbstract, area: EditorArea,
                            update: EditorKeywordHelperAbstract.StyleUpdate?,
                            structure: MarkdownStructureSnapshot?, endCallback: Action0?) {
        if (requestId != runningStyleRequestId) return
        pendingStyleTask = null
        styleRunning = false
        runningStyleRequestId = 0L
        val alive = isStyleTaskAlive(requestId, optionsVersion, helper)
        val contentCurrent = contentVersion == this.contentVersion
        if (alive && update != null && contentCurrent) {
            if (structure != null) {
                currentMarkdownSnapshot = structure
                markdownSnapshotVersion = contentVersion
            }
            area.suspendVisibleParsWhileInvoke {
                if (update.spans() != null) area.setStyleSpans(update.start(), update.spans())
                if (structure != null) area.markdownPresentation.apply(structure)
            }
        }
        if (alive && contentCurrent) endCallback?.invoke()
        if (styleDirty) scheduleLatestStyle(false)
    }

    private fun isStyleTaskAlive(requestId: Long, optionsVersion: Long, helper: EditorKeywordHelperAbstract): Boolean =
        requestId == styleRequestId.get() && optionsVersion == styleOptionsVersion
            && helper === mKeywordHelper && !isRealtimeProcessingLimitReached && !isDestroyed()

    private fun canComputeStyle(requestId: Long, optionsVersion: Long, contentVersion: Long, helper: EditorKeywordHelperAbstract): Boolean =
        isStyleTaskAlive(requestId, optionsVersion, helper) && contentVersion == this.contentVersion

    private fun resetStyleScheduler() {
        currentMarkdownSnapshot = null
        markdownSnapshotVersion = -1L
        styleDelay.stop()
        styleRequestId.incrementAndGet()
        val task = pendingStyleTask
        val handler = sStylerHandler
        if (task != null && handler != null) handler.removeCallback(task)
        pendingStyleTask = null
        latestTemporaryText = null
        latestSearchText = null
        latestEndCallback = null
        styleDirty = false
        styleRunning = false
        runningStyleRequestId = 0L
        styleOptionsVersion++
        lastStyleStartedAt = 0L
    }

    override fun destroy() {
        styleTextSubscription?.unsubscribe()
        styleTextSubscription = null
        resetStyleScheduler()
        styleDelay.setOnFinished(null)
        super.destroy()
    }
}

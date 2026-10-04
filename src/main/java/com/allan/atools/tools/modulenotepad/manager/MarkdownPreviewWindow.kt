package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.Colors
import com.allan.atools.MarkdownThemes
import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.richtext.codearea.MarkdownEditorSupport
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.web.WebView
import javafx.stage.Stage
import netscape.javascript.JSObject
import org.reactfx.Subscription
import java.util.concurrent.Future

/** 完整排版预览保留源码定位；正文编辑、保存和撤销仍由原编辑器负责。 */
class MarkdownPreviewWindow private constructor() {
    private val stage = Stage()
    private val view = WebView()
    private val hint = Label(Locales.str("markdown.previewHint"))
    private val bridge = PreviewBridge()
    private var area: EditorArea? = null
    private var boundFile: java.io.File? = null
    private var boundMarkdown = false
    private var changes: Subscription? = null
    private var task: Future<*>? = null
    private var renderedVersion = -1L
    private var renderedSnapshot: MarkdownStructureSnapshot? = null
    private var generation = 0L
    private val scheduler = LatestRefreshScheduler(200, 450, ::render)
    private val currentChanged = ChangeListener<EditorArea> { _, _, value -> bind(value) }
    private val themeChanged = javafx.beans.InvalidationListener { generation++; scheduler.request() }
    private val fontChanged = javafx.beans.InvalidationListener { scheduler.request() }
    private val editableChanged = ChangeListener<Boolean> { _, _, _ -> scheduler.request() }
    private val caretChanged = ChangeListener<Number> { _, _, value ->
        if (stage.isShowing && renderedVersion == area?.editor?.contentVersion) {
            try { view.engine.executeScript("followSource(${value.toInt()})") }
            catch (_: RuntimeException) { /* 页面尚未加载时等待本轮渲染。 */ }
        }
    }

    init {
        stage.initOwner(UIContext.mainWindow)
        stage.title = Locales.str("markdown.preview")
        val source = Button(Locales.str("markdown.backToEditor"))
        source.setOnAction { area?.requestFocus(); UIContext.mainWindow?.requestFocus() }
        val bar = HBox(12.0, source, hint).apply { style = "-fx-padding: 8;" }
        stage.scene = Scene(BorderPane(view, bar, null, null, null), 820.0, 680.0)
        view.isContextMenuEnabled = true
        view.engine.setCreatePopupHandler { null }
        view.engine.loadWorker.stateProperty().addListener { _, _, value ->
            if (value == Worker.State.SUCCEEDED && stage.isShowing && renderedVersion == area?.editor?.contentVersion) {
                (view.engine.executeScript("window") as JSObject).setMember("editorBridge", bridge)
                view.engine.executeScript("installPreviewBridge()")
            }
        }
        stage.setOnHidden {
            UIContext.currentAreaProp.removeListener(currentChanged)
            MarkdownThemes.revisionProperty().removeListener(themeChanged)
            UIContext.getFontSizeProperty().removeListener(fontChanged)
            UIContext.getFontThemeProperty().removeListener(fontChanged)
            bind(null)
            view.engine.load(null)
        }
    }

    private fun bind(value: EditorArea?) {
        generation++
        task?.cancel(true)
        task = null
        scheduler.invalidate()
        changes?.unsubscribe()
        changes = null
        area?.caretPositionProperty()?.removeListener(caretChanged)
        area?.editableProperty()?.removeListener(editableChanged)
        area = value
        boundFile = value?.editor?.sourceFile
        boundMarkdown = MarkdownEditorSupport.supportsMarkdown(value)
        renderedSnapshot = null
        renderedVersion = -1
        // 解绑后立刻移除旧页面及其桥接，路径相同的版本号也不能复用旧页面。
        view.engine.loadContent("<html><body></body></html>")
        stage.title = value?.editor?.documentState?.displayName?.let { "$it · ${Locales.str("markdown.preview")}" }
            ?: Locales.str("markdown.preview")
        if (!boundMarkdown) return
        value ?: return
        changes = value.plainTextChanges().subscribe { scheduler.request() }
        value.caretPositionProperty().addListener(caretChanged)
        value.editableProperty().addListener(editableChanged)
        scheduler.startNow()
    }

    private fun render(requestId: Long) {
        val current = area
        if (current == null || !stage.isShowing || current.editor.isRealtimeProcessingLimitReached) {
            scheduler.complete(requestId, null)
            if (current?.editor?.isRealtimeProcessingLimitReached == true) {
                renderedVersion = -1
                renderedSnapshot = null
                hint.text = Locales.str("markdown.previewLimit")
                view.engine.loadContent("<html><body>${escape(hint.text)}</body></html>")
            }
            return
        }
        val version = current.editor.contentVersion
        val source = current.text
        val revision = generation
        val dark = Colors.isDark()
        val themeCss = MarkdownThemes.previewCss(dark)
        val fontSize = UIContext.getFontSizeProperty().get().toDouble()
        val fontFamily = com.allan.atools.FontTheme.fontFamily().replace("\"", "")
        val readonly = !current.isEditable
        val file = current.editor.sourceFile
        val base = file?.parentFile?.toURI()?.toASCIIString()
        task = ThreadUtils.submit {
            try {
                val state = (current.editor as EditorAreaMgrCode).markdownSnapshot(source)
                val html = renderDocument(state, base, dark, themeCss)
                    .replace("<body ", "<body data-readonly=\"$readonly\" style=\"font-size:${fontSize}px;font-family:${escape(fontFamily)}\" ")
                Platform.runLater {
                    scheduler.complete(requestId) {
                        task = null
                        if (revision == generation && area === current && version == current.editor.contentVersion
                            && file == current.editor.sourceFile && stage.isShowing) {
                            renderedSnapshot = state
                            renderedVersion = version
                            hint.text = Locales.str("markdown.previewHint")
                            view.engine.loadContent(html)
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e("Render markdown preview failed", error)
                Platform.runLater { scheduler.complete(requestId) { hint.text = Locales.str("markdown.previewFailed") } }
            }
        }
    }

    /** 仅由固定预览脚本调用，外部页面不能导航进预览视图。 */
    inner class PreviewBridge {
        fun edit(position: Int) {
            val current = area ?: return
            if (current.editor.contentVersion != renderedVersion || position < 0 || position > current.length) return
            current.moveTo(position)
            current.requestFollowCaret()
            UIContext.mainWindow?.requestFocus()
            current.requestFocus()
        }
        fun copy(value: String) {
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(javafx.scene.input.ClipboardContent().apply { putString(value) })
        }
        fun open(destination: String) {
            val current = area ?: return
            if (current.editor.contentVersion != renderedVersion) return
            (current.editor as EditorAreaMgrCode).openMarkdownDestination(destination)
        }
        fun task(position: Int, checked: Boolean) {
            val current = area ?: return
            if (!current.isEditable || current.markdownComposing || current.editor.contentVersion != renderedVersion) return
            val offset = renderedSnapshot?.lines?.getOrNull(position)?.taskOffset ?: return
            if (offset < 0) return
            val start = current.selection.start
            val end = current.selection.end
            current.undoManager.preventMerge()
            current.replaceText(offset, offset + 1, if (checked) "x" else " ")
            current.selectRange(start, end)
            current.undoManager.preventMerge()
        }
    }

    companion object {
        private var instance: MarkdownPreviewWindow? = null

        @JvmStatic
        fun refreshCurrentFile(area: EditorArea?) {
            val window = instance ?: return
            if (window.stage.isShowing && window.area === area &&
                (window.boundFile != area?.editor?.sourceFile || window.boundMarkdown != MarkdownEditorSupport.supportsMarkdown(area))) {
                window.bind(area)
            }
        }

        @JvmStatic
        fun show(area: EditorArea) {
            val window = instance ?: MarkdownPreviewWindow().also { instance = it }
            if (!window.stage.isShowing) {
                UIContext.currentAreaProp.addListener(window.currentChanged)
                MarkdownThemes.revisionProperty().addListener(window.themeChanged)
                UIContext.getFontSizeProperty().addListener(window.fontChanged)
                UIContext.getFontThemeProperty().addListener(window.fontChanged)
                window.stage.show()
            }
            window.bind(area)
            window.stage.toFront()
        }

        @JvmStatic
        fun close() { instance?.stage?.close(); instance = null }

        fun renderBody(state: MarkdownStructureSnapshot): String {
            return MarkdownHtmlRenderer.body(state)
        }

        private val katexLibrary by lazy { MarkdownPreviewWindow::class.java.getResource("/markdown/katex/katex.min.js")!!.readText() }
        private val katexCss by lazy {
            val source = MarkdownPreviewWindow::class.java.getResource("/markdown/katex/katex.min.css")!!.readText()
            Regex("url\\((?:\"|')?(fonts/[^)\"']+)(?:\"|')?\\)").replace(source) { match ->
                val name = match.groupValues[1]
                val bytes = MarkdownPreviewWindow::class.java.getResourceAsStream("/markdown/katex/$name")!!.use { it.readAllBytes() }
                val type = when { name.endsWith(".woff2") -> "font/woff2"; name.endsWith(".woff") -> "font/woff"; else -> "font/ttf" }
                "url(data:$type;base64,${java.util.Base64.getEncoder().encodeToString(bytes)})"
            }
        }
        private val mermaidLibrary by lazy { MarkdownPreviewWindow::class.java.getResource("/mermaid/mermaid.min.js")!!.readText() }

        fun renderDocument(state: MarkdownStructureSnapshot, base: String?, dark: Boolean, themeCss: String = MarkdownThemes.previewCss(dark)): String {
            val style = MarkdownPreviewWindow::class.java.getResource("/markdown/preview.css")!!.readText()
            val script = MarkdownPreviewWindow::class.java.getResource("/markdown/preview.js")!!.readText()
            val baseTag = if (base == null) "" else "<base href=\"${escape(base)}\">"
            val formula = state.elements.any { it.node is com.allan.atools.richtext.codearea.keywordhelper.MarkdownMath || it.node is com.allan.atools.richtext.codearea.keywordhelper.MarkdownMathBlock }
            val diagram = state.elements.any { it.node is org.commonmark.node.FencedCodeBlock && it.node.info.trim() == "mermaid" }
            val libraries = (if (formula) "<style>$katexCss</style><script>$katexLibrary</script>" else "") +
                (if (diagram) "<script>$mermaidLibrary</script>" else "")
            return "<!doctype html><html><head><meta charset=\"utf-8\">$baseTag<style>$style$themeCss</style>$libraries</head>" +
                "<body data-copy-label=\"${escape(Locales.str("markdown.copyCode"))}\" class=\"${if (dark) "dark" else "light"}\"><article>${MarkdownHtmlRenderer.body(state, base)}</article><script>$script</script></body></html>"
        }

        fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}

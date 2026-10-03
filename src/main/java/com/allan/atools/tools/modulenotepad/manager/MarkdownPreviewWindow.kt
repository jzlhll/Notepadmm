package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.Colors
import com.allan.atools.SettingPreferences
import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.richtext.codearea.MarkdownEditorSupport
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownExtensions
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
import org.commonmark.renderer.html.HtmlRenderer
import org.reactfx.Subscription
import java.util.concurrent.Future

/** 完整排版预览保留源码定位；正文编辑、保存和撤销仍由原编辑器负责。 */
class MarkdownPreviewWindow private constructor() {
    private val stage = Stage()
    private val view = WebView()
    private val hint = Label(Locales.str("markdown.previewHint"))
    private val bridge = PreviewBridge()
    private var area: EditorArea? = null
    private var changes: Subscription? = null
    private var task: Future<*>? = null
    private var renderedVersion = -1L
    private var renderedSnapshot: MarkdownStructureSnapshot? = null
    private var generation = 0L
    private val scheduler = LatestRefreshScheduler(200, 450, ::render)
    private val currentChanged = ChangeListener<EditorArea> { _, _, value -> bind(value) }
    private val themeChanged = ChangeListener<Boolean> { _, _, _ -> scheduler.request() }
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
            SettingPreferences.getBoolProp(SettingPreferences.appVisionKey).removeListener(themeChanged)
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
        renderedSnapshot = null
        renderedVersion = -1
        if (!MarkdownEditorSupport.supportsMarkdown(value)) {
            view.engine.loadContent("<html><body></body></html>")
            return
        }
        value ?: return
        changes = value.plainTextChanges().subscribe { scheduler.request() }
        value.caretPositionProperty().addListener(caretChanged)
        value.editableProperty().addListener(editableChanged)
        stage.title = value.editor.documentState.displayName + " · " + Locales.str("markdown.preview")
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
        task = ThreadUtils.submit {
            try {
                val state = (current.editor as EditorAreaMgrCode).markdownSnapshot(source)
                val html = renderDocument(state, current.editor.sourceFile?.parentFile?.toURI()?.toASCIIString(), dark)
                    .replace("<body class=", "<body data-readonly=\"${!current.isEditable}\" class=")
                Platform.runLater {
                    scheduler.complete(requestId) {
                        task = null
                        if (revision == generation && area === current && version == current.editor.contentVersion && stage.isShowing) {
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
        fun open(destination: String) {
            val current = area ?: return
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
        fun show(area: EditorArea) {
            val window = instance ?: MarkdownPreviewWindow().also { instance = it }
            if (!window.stage.isShowing) {
                UIContext.currentAreaProp.addListener(window.currentChanged)
                SettingPreferences.getBoolProp(SettingPreferences.appVisionKey).addListener(window.themeChanged)
                window.stage.show()
            }
            window.bind(area)
            window.stage.toFront()
        }

        @JvmStatic
        fun close() { instance?.stage?.close(); instance = null }

        fun renderBody(state: MarkdownStructureSnapshot): String {
            val renderer = HtmlRenderer.builder().extensions(MarkdownExtensions.all())
                .escapeHtml(true).sanitizeUrls(true).softbreak("<br>\n")
                .attributeProviderFactory {
                    org.commonmark.renderer.html.AttributeProvider { node, tag, attributes ->
                        node.sourceSpans.firstOrNull()?.let { span ->
                            attributes["data-source-start"] = span.inputIndex.toString()
                            attributes["data-source-line"] = span.lineIndex.toString()
                        }
                        if (node is org.commonmark.node.Heading) {
                            state.headings.firstOrNull { it.line == node.sourceSpans.firstOrNull()?.lineIndex }
                                ?.let { attributes["id"] = it.anchor }
                        }
                    }
                }.build()
            return renderer.render(state.root)
        }

        fun renderDocument(state: MarkdownStructureSnapshot, base: String?, dark: Boolean): String {
            val style = MarkdownPreviewWindow::class.java.getResource("/markdown/preview.css")!!.readText()
            val script = MarkdownPreviewWindow::class.java.getResource("/markdown/preview.js")!!.readText()
            val baseTag = if (base == null) "" else "<base href=\"${escape(base)}\">"
            return "<!doctype html><html><head><meta charset=\"utf-8\">$baseTag<style>$style</style></head>" +
                "<body class=\"${if (dark) "dark" else "light"}\"><article>${renderBody(state)}</article><script>$script</script></body></html>"
        }

        fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}

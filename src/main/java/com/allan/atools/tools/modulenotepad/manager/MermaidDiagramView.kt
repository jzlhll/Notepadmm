package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.utils.Locales
import com.google.gson.Gson
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.control.Label
import javafx.scene.input.KeyEvent
import javafx.scene.layout.StackPane
import javafx.scene.web.WebView
import javafx.util.Duration

/** 按可见节点加载离线 Mermaid，离开虚拟视口后释放 WebView，复用已生成的 SVG。 */
class MermaidDiagramView(
    private val source: String,
    private val dark: Boolean,
    cached: Rendered?,
    private val onRendered: (Rendered) -> Unit,
    private val onFailure: (String) -> Unit
) : StackPane() {
    data class Rendered(val svg: String, val width: Double, val height: Double)

    private var webView: WebView? = null
    private var disposed = false
    private var rendered = cached
    private val timeout = PauseTransition(Duration.seconds(15.0))

    init {
        styleClass.add("markdown-mermaid-frame")
        children.add(Label(Locales.str("markdownMermaidLoading")))
        sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                load()
            } else {
                Platform.runLater { if (this.scene == null) release() }
            }
        }
        timeout.setOnFinished { fail("Mermaid rendering timed out") }
        // WebView 先处理选区与复制，未处理的按键也不应编辑底层 Markdown 源码。
        addEventHandler(KeyEvent.ANY) { it.consume() }
    }

    private fun load() {
        if (disposed || webView != null) return
        val view = WebView()
        webView = view
        view.isContextMenuEnabled = false
        view.isFocusTraversable = true
        view.setMinSize(0.0, 0.0)
        view.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)
        val engine = view.engine
        engine.setCreatePopupHandler { null }
        val previous = rendered
        if (previous != null) {
            engine.isJavaScriptEnabled = false
            engine.loadContent(html(previous.svg, dark))
            children.setAll(view)
            return
        }
        engine.titleProperty().addListener { _, _, title ->
            if (disposed || webView !== view) return@addListener
            when (title) {
                "diagram-ready" -> {
                    timeout.stop()
                    try {
                        val result = Rendered(
                            engine.executeScript("window.diagramResult.svg") as String,
                            (engine.executeScript("window.diagramResult.width") as Number).toDouble(),
                            (engine.executeScript("window.diagramResult.height") as Number).toDouble()
                        )
                        rendered = result
                        children.setAll(view)
                        // 延后段落高度变更，避免在 WebKit 回调栈内重建或销毁节点。
                        Platform.runLater { if (!disposed) onRendered(result) }
                    } catch (error: RuntimeException) {
                        fail(error.message ?: "Unable to read Mermaid output")
                    }
                }
                "diagram-failed" -> fail(engine.executeScript("window.diagramError").toString())
            }
        }
        engine.loadWorker.stateProperty().addListener { _, _, state ->
            if (disposed || webView !== view) return@addListener
            when (state) {
                Worker.State.SUCCEEDED -> try {
                    engine.executeScript(library)
                    engine.executeScript(renderer)
                    engine.executeScript("window.renderNotepadDiagram(${gson.toJson(source)}, $dark)")
                } catch (error: Exception) {
                    fail(error.message ?: "Unable to initialize Mermaid")
                }
                Worker.State.FAILED -> fail("Unable to load Mermaid renderer")
                else -> Unit
            }
        }
        timeout.playFromStart()
        engine.loadContent(html("", dark))
        // Mermaid 的字体和 SVG 测量需要节点进入 Scene。
        children.setAll(view)
    }

    private fun fail(message: String) {
        timeout.stop()
        Platform.runLater {
            if (!disposed && webView != null) onFailure(message)
        }
    }

    private fun release() {
        timeout.stop()
        val view = webView ?: return
        webView = null
        view.engine.loadWorker.cancel()
        view.engine.load(null)
        children.clear()
    }

    fun dispose() {
        disposed = true
        release()
    }

    companion object {
        private val gson = Gson()
        private val library by lazy {
            requireNotNull(MermaidDiagramView::class.java.getResourceAsStream("/mermaid/mermaid.min.js"))
                .bufferedReader().use { it.readText() }
        }
        private val renderer by lazy {
            requireNotNull(MermaidDiagramView::class.java.getResourceAsStream("/mermaid/renderer.js"))
                .bufferedReader().use { it.readText() }
        }

        private fun html(svg: String, dark: Boolean): String = """
            <!doctype html><html><head><meta charset="UTF-8">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src data:;">
            <style>html,body{margin:0;padding:0;overflow:hidden;background:${if (dark) "#292a2f" else "#ffffff"};}
            #diagram{width:100%;}svg{display:block;width:100%;height:auto;}
            #diagram svg,#diagram svg text,#diagram svg tspan{-webkit-user-select:text!important;user-select:text!important;}
            #diagram svg text,#diagram svg tspan{cursor:text!important;pointer-events:all!important;}</style>
            </head><body><div id="diagram">$svg</div></body></html>
        """.trimIndent()
    }
}

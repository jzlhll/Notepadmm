package com.allan.atools.tools.modulenotepad.manager

import com.google.gson.Gson
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.image.Image
import javafx.scene.web.WebView
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** SVG 在隔离页面转成受限像素的 PNG，图片工作线程等待结果，界面线程不阻塞。 */
object MarkdownSvgDecoder {
    private data class Raster(val png: String, val width: Double, val height: Double)
    fun decode(bytes: ByteArray, requestedWidth: Double, requestedHeight: Double): MarkdownImageDecoder.Result {
        check(!Platform.isFxApplicationThread()) { "SVG decoding requires an image worker" }
        val svg = Jsoup.parse(bytes.toString(Charsets.UTF_8), "", Parser.xmlParser()).selectFirst("svg")
            ?: throw IOException("Unsupported image format")
        if (!svg.hasAttr("xmlns")) svg.attr("xmlns", "http://www.w3.org/2000/svg")
        // SVG 作为图片加载，保留 foreignObject 中的静态文字和布局，避免图表标签丢失。
        svg.select("script,iframe,object,embed").remove()
        (listOf(svg) + svg.getAllElements()).forEach { element ->
            element.attributes().asList().filter { attribute ->
                attribute.key.startsWith("on", true) || attribute.key in setOf("href", "xlink:href") &&
                    !attribute.value.startsWith('#') && !attribute.value.startsWith("data:image/", true)
            }.forEach { element.removeAttr(it.key) }
        }
        val payload = Base64.getEncoder().encodeToString(svg.outerHtml().toByteArray(Charsets.UTF_8))
        val result = CompletableFuture<Raster>()
        val viewRef = AtomicReference<WebView?>()
        Platform.runLater {
            if (result.isDone) return@runLater
            try {
                val view = WebView()
                viewRef.set(view)
                val engine = view.engine
                engine.setCreatePopupHandler { null }
                engine.titleProperty().addListener { _, _, title ->
                    if (result.isDone) return@addListener
                    try {
                        when (title) {
                            "svg-ready" -> {
                                val encoded = engine.executeScript("window.svgRaster.png") as String
                                val width = (engine.executeScript("window.svgRaster.width") as Number).toDouble()
                                val height = (engine.executeScript("window.svgRaster.height") as Number).toDouble()
                                result.complete(Raster(encoded, width, height))
                            }
                            "svg-failed" -> result.completeExceptionally(IOException("SVG rasterization failed"))
                        }
                    } catch (error: Exception) { result.completeExceptionally(error) }
                }
                engine.loadWorker.stateProperty().addListener { _, _, state ->
                    if (result.isDone) return@addListener
                    if (state == Worker.State.FAILED) result.completeExceptionally(IOException("SVG page load failed"))
                    if (state == Worker.State.SUCCEEDED) try {
                        engine.executeScript(script)
                        engine.executeScript("rasterSvg(${Gson().toJson(payload)},${safeSize(requestedWidth)},${safeSize(requestedHeight)})")
                    } catch (error: Exception) { result.completeExceptionally(error) }
                }
                engine.loadContent("""<!doctype html><html><head><meta charset="UTF-8">
                    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; script-src 'unsafe-inline'; style-src 'unsafe-inline';">
                    </head><body></body></html>""")
            } catch (error: Exception) { result.completeExceptionally(error) }
        }
        try {
            val raster = result.get(15, TimeUnit.SECONDS)
            val image = Base64.getDecoder().decode(raster.png.substringAfter(',')).inputStream().use { Image(it) }
            if (image.isError) throw IOException("SVG raster decode failed", image.exception)
            return MarkdownImageDecoder.Result(image, raster.width, raster.height)
        } finally {
            result.cancel(false)
            Platform.runLater {
                viewRef.getAndSet(null)?.engine?.let { it.loadWorker.cancel(); it.load(null) }
            }
        }
    }

    private fun safeSize(value: Double) = if (value.isFinite() && value > 0) value else 0.0

    private val script = """
        function rasterSvg(payload, requestedWidth, requestedHeight) {
            var image = new Image();
            image.onerror = function() { document.title = 'svg-failed'; };
            image.onload = function() {
                try {
                    var width = image.naturalWidth, height = image.naturalHeight;
                    if (!(width > 0 && height > 0)) throw new Error('Invalid SVG dimensions');
                    var factor = Math.min(1, Math.sqrt(4194304 / (width * height)));
                    if (requestedWidth > 0) factor = Math.min(factor, requestedWidth / width);
                    if (requestedHeight > 0) factor = Math.min(factor, requestedHeight / height);
                    var canvas = document.createElement('canvas');
                    canvas.width = Math.max(1, Math.floor(width * factor));
                    canvas.height = Math.max(1, Math.floor(height * factor));
                    canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
                    window.svgRaster = {png:canvas.toDataURL('image/png'),width:width,height:height};
                    document.title = 'svg-ready';
                } catch (error) { document.title = 'svg-failed'; }
            };
            image.src = 'data:image/svg+xml;base64,' + payload;
        }
    """.trimIndent()
}

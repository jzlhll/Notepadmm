package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.threads.ThreadUtils
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import java.io.File
import java.lang.ref.WeakReference
import java.net.URI
import java.util.concurrent.Future

/** 表格缩略图固定尺寸，最多四个后台请求与六十四份缩略图，退出文档即释放。 */
class MarkdownTableImageCache {
    private class Entry(val url: URI) {
        var image: Image? = null
        var failed = false
        var task: Future<*>? = null
        val views = ArrayList<Pair<WeakReference<StackPane>, String>>()
    }
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val queue = ArrayDeque<Entry>()
    private val running = HashSet<Future<*>>()
    private var active = 0
    private var generation = 0L

    fun node(destination: String, alt: String, document: File?): Node {
        val box = StackPane().apply { setMinSize(120.0, 80.0); setPrefSize(120.0, 80.0); setMaxSize(120.0, 80.0); accessibleText = alt }
        Tooltip.install(box, Tooltip(alt.ifEmpty { destination }))
        val uri = try {
            val value = URI(destination.replace(" ", "%20"))
            if (value.scheme == null) document?.parentFile?.toURI()?.resolve(value) else value
        } catch (_: Exception) { null }
        if (uri == null || uri.scheme.lowercase(java.util.Locale.ROOT) !in setOf("http", "https", "file")) {
            box.children.setAll(Label(alt.ifEmpty { "Image" })); return box
        }
        val entry = entries.getOrPut(uri.toString()) { Entry(uri).also { queue.add(it) } }
        entry.views.removeAll { it.first.get() == null }
        entry.views.add(WeakReference(box) to alt)
        show(entry, box, alt)
        while (entries.size > 64) {
            val oldest = entries.entries.iterator().next()
            entries.remove(oldest.key)
            queue.remove(oldest.value)
        }
        pump()
        return box
    }

    private fun show(entry: Entry, box: StackPane, alt: String) {
        val image = entry.image
        if (image != null) {
            box.children.setAll(ImageView(image).apply { fitWidth = 120.0; fitHeight = 80.0; isPreserveRatio = true; accessibleText = alt })
        } else {
            box.children.setAll(Label(alt.ifEmpty { "Image" }).apply { isWrapText = true })
            box.setOnMousePressed { event ->
                if (entry.failed) {
                    event.consume()
                    entry.failed = false
                    queue.add(entry)
                    pump()
                }
            }
        }
    }

    private fun pump() {
        while (active < 4 && queue.isNotEmpty()) {
            val entry = queue.removeFirst()
            if (entry.task != null) continue
            active++
            val revision = generation
            entry.task = ThreadUtils.submit {
                val image = try {
                    val connection = entry.url.toURL().openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                    if (connection.contentLengthLong > 20L * 1024 * 1024) throw java.io.IOException("Table image exceeds byte limit")
                    connection.getInputStream().use { input ->
                        val bytes = input.readNBytes(20 * 1024 * 1024 + 1)
                        if (bytes.size > 20 * 1024 * 1024) throw java.io.IOException("Table image exceeds byte limit")
                        Image(bytes.inputStream(), 120.0, 80.0, true, true).takeIf { !it.isError && it.width > 0 }
                    }
                } catch (_: Exception) { null }
                Platform.runLater {
                    if (revision == generation) {
                        active--
                        entry.task?.let { running.remove(it) }
                        entry.task = null
                        if (entries[entry.url.toString()] === entry) {
                            entry.image = image
                            entry.failed = image == null
                            entry.views.forEach { (reference, alt) -> reference.get()?.let { show(entry, it, alt) } }
                        }
                        pump()
                    }
                }
            }
            entry.task?.let { running.add(it) }
        }
    }

    fun clear() {
        generation++
        running.forEach { it.cancel(true) }
        running.clear()
        entries.clear()
        queue.clear()
        active = 0
    }
}

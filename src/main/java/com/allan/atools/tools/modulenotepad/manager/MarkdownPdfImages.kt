package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.tools.modulenotepad.log.LogMemoryBudget
import com.allan.atools.utils.Log
import javafx.embed.swing.SwingFXUtils
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.util.Base64
import java.util.Locale
import javax.imageio.ImageIO

/** 输出图片先受限读取并固定到本次临时目录，栅格图按纸张分辨率解码，共用远程缓存。 */
class MarkdownPdfImages(private val sourceFile: File?, private val width: Double, private val height: Double) : AutoCloseable {
    private val directory = Files.createTempDirectory("atools-pdf-images-")
    private val leases = ArrayList<LogMemoryBudget.Lease>()

    fun prepare(html: String): String {
        val document = Jsoup.parse(html)
        val base = sourceFile?.parentFile?.toURI() ?: File(".").toURI()
        val images = HashMap<URI, String>()
        for (image in document.select("img[src]")) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            val uri = MarkdownImageLocation.resolve(sourceFile, image.attr("src")) ?: error("Unsupported image location")
            val destination = images.getOrPut(uri) {
                val diagnostic = MarkdownImageLoadLog("pdf", uri.toString(), sourceFile,
                    image.attr("data-source-line").toIntOrNull()?.plus(1) ?: 0)
                try {
                    val bytes = when (uri.scheme.lowercase(Locale.ROOT)) {
                        "http", "https" -> MarkdownRemoteImageLoader.loadBytes(uri.toString(), diagnostic)
                        "file" -> MarkdownImageLocation.file(uri).inputStream().use { it.readNBytes(20 * 1024 * 1024 + 1) }
                        "data" -> {
                            val value = uri.toString()
                            require(value.length <= 28 * 1024 * 1024 && ',' in value) { "Image data exceeds byte limit" }
                            val payload = value.substringAfter(',')
                            if (value.substringBefore(',').endsWith(";base64", true)) Base64.getDecoder().decode(payload)
                            else URLDecoder.decode(payload.replace("+", "%2B"), Charsets.UTF_8).toByteArray(Charsets.UTF_8)
                        }
                        else -> error("Unsupported image protocol")
                    }
                    require(bytes.size <= 20 * 1024 * 1024) { "Image exceeds byte limit" }
                    val svg = uri.path?.lowercase(Locale.ROOT)?.endsWith(".svg") == true ||
                        bytes.copyOfRange(0, Math.min(bytes.size, 1024)).toString(Charsets.UTF_8).contains("<svg")
                    val path = directory.resolve("${images.size}.${if (svg) "svg" else "png"}")
                    if (svg) {
                        leases.add(LogMemoryBudget.reserve(LogMemoryBudget.TASK_RESERVATION))
                        val vector = Jsoup.parse(bytes.toString(Charsets.UTF_8), "", Parser.xmlParser())
                        vector.selectFirst("svg")?.attr("xml:base", if (uri.scheme == "data") base.toString() else uri.toString())
                        Files.writeString(path, vector.outerHtml())
                        diagnostic.event("prepared SVG bytes=${bytes.size}")
                    } else {
                        val decoded = MarkdownImageDecoder.decode(bytes, width * 2, height * 2)
                        require(!decoded.image.isError) { "Image decode failed" }
                        leases.add(LogMemoryBudget.reserve((decoded.image.width * decoded.image.height * 4).toLong()))
                        check(ImageIO.write(SwingFXUtils.fromFXImage(decoded.image, null), "png", path.toFile())) { "PNG encoder unavailable" }
                        diagnostic.complete(decoded.image)
                    }
                    path.toUri().toASCIIString() + if (svg && uri.fragment != null) "#${uri.rawFragment}" else ""
                } catch (error: Exception) { diagnostic.fail("prepare PDF image failed", error); throw error }
            }
            image.attr("src", destination)
        }
        document.outputSettings().prettyPrint(false)
        return document.outerHtml()
    }

    override fun close() {
        try {
            Files.list(directory).use { files -> files.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(directory)
        } catch (error: Exception) { Log.e("Clean PDF image files failed", error) }
        finally { leases.forEach { it.close() }; leases.clear() }
    }
}

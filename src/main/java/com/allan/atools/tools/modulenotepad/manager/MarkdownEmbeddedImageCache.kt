package com.allan.atools.tools.modulenotepad.manager

import javafx.embed.swing.SwingFXUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import javax.imageio.ImageIO

/** 同行和局部 WebView 跨重建复用转码结果，缓存只保留 data URL 和原始尺寸。 */
object MarkdownEmbeddedImageCache {
    private const val MAX_ENTRIES = 64
    private const val MAX_BYTES = 48L * 1024 * 1024
    private const val MAX_DATA_LENGTH = 28 * 1024 * 1024
    private val pngDataUrlPrefix = "data:image/png;base64,".toByteArray(Charsets.US_ASCII)

    data class Result(val dataUrl: String, val width: Double, val height: Double)

    private data class FileVersion(val path: Path, val modified: FileTime, val created: FileTime, val size: Long, val fileKey: Any?)
    private data class Entry(val image: Result, val bytes: Long)

    private val lock = Any()
    private val entries = LinkedHashMap<Any, Entry>(16, 0.75f, true)
    private val loading = HashMap<Any, CompletableFuture<Result?>>()
    private var retainedBytes = 0L

    fun load(file: File, diagnostic: MarkdownImageLoadLog): Result {
        val path = file.toPath().toRealPath()
        val version = fileVersion(path)
        return getOrEncode(version, diagnostic) {
            val decoded = MarkdownImageDecoder.decode(path.toFile(), 0.0, 0.0)
            if (fileVersion(path) != version) throw IOException("Local image file changed during decode")
            decoded
        }
    }

    private fun fileVersion(path: Path): FileVersion {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        if (!attributes.isRegularFile) throw IOException("Local image file missing")
        return FileVersion(path, attributes.lastModifiedTime(), attributes.creationTime(), attributes.size(), attributes.fileKey())
    }

    fun loadData(url: String, diagnostic: MarkdownImageLoadLog): Result {
        val comma = url.indexOf(',')
        require(url.length <= MAX_DATA_LENGTH && comma >= 7 && url.regionMatches(comma - 7, ";base64", 0, 7)) {
            "Unsupported image data"
        }
        val key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.toByteArray()))
        return getOrEncode(key, diagnostic) {
            MarkdownImageDecoder.decode(Base64.getDecoder().decode(url.substring(comma + 1)), 0.0, 0.0)
        }
    }

    fun getOrEncode(key: Any, diagnostic: MarkdownImageLoadLog, decode: () -> MarkdownImageDecoder.Result): Result {
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image request interrupted")
            val request = CompletableFuture<Result?>()
            val cached: Result?
            val pending: CompletableFuture<Result?>?
            synchronized(lock) {
                cached = entries[key]?.image
                pending = if (cached == null) loading.putIfAbsent(key, request) else null
            }
            if (cached != null) {
                diagnostic.event("encoded cache hit width=${cached.width} height=${cached.height}")
                return cached
            }
            if (pending != null) {
                val shared = try {
                    pending.get()
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw error
                } catch (error: ExecutionException) {
                    throw error.cause ?: error
                }
                if (shared != null) {
                    diagnostic.event("encoded cache shared width=${shared.width} height=${shared.height}")
                    return shared
                }
                // 当前请求仍然有效时，可以接替已经取消的转码任务。
                synchronized(lock) { loading.remove(key, pending) }
                continue
            }

            // 首个请求在当前线程转码，等待方不会依赖同一线程池中尚未开始的任务。
            try {
                val decoded = decode()
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image request interrupted")
                if (decoded.image.isError) throw IOException("Image decode failed", decoded.image.exception)
                val pixels = SwingFXUtils.fromFXImage(decoded.image, null) ?: throw IOException("Image has no readable pixels")
                val output = ByteArrayOutputStream()
                output.write(pngDataUrlPrefix)
                Base64.getEncoder().wrap(output).use { encoded ->
                    if (!ImageIO.write(pixels, "png", encoded)) throw IOException("PNG writer unavailable")
                }
                val dataUrl = output.toString(Charsets.US_ASCII)
                val image = Result(dataUrl, decoded.width, decoded.height)
                retain(key, image)
                request.complete(image)
                diagnostic.complete(decoded.image)
                return image
            } catch (error: Throwable) {
                if (error is InterruptedException) Thread.currentThread().interrupt()
                if (Thread.currentThread().isInterrupted || error is InterruptedIOException) {
                    request.complete(null)
                } else {
                    request.completeExceptionally(error)
                }
                throw error
            } finally {
                synchronized(lock) { loading.remove(key, request) }
            }
        }
    }

    private fun retain(key: Any, image: Result) {
        // 按双字节字符计入 data URL 和本地路径，并预留条目元数据的空间。
        val pathLength = (key as? FileVersion)?.path?.toString()?.length ?: 0
        val bytes = 2L * (image.dataUrl.length + pathLength) + 256
        if (bytes > MAX_BYTES) return
        synchronized(lock) {
            retainedBytes -= entries.put(key, Entry(image, bytes))?.bytes ?: 0L
            retainedBytes += bytes
            val oldest = entries.values.iterator()
            while (entries.size > MAX_ENTRIES || retainedBytes > MAX_BYTES) {
                retainedBytes -= oldest.next().bytes
                oldest.remove()
            }
        }
    }
}

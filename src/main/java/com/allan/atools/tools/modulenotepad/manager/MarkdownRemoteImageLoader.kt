package com.allan.atools.tools.modulenotepad.manager

import javafx.scene.image.Image
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.concurrent.CompletableFuture

/** 正文与表格共用远程图片读取及内存缓存，同一链接合并下载并复用原图和缩略图。 */
object MarkdownRemoteImageLoader {
    private const val MAX_REDIRECTS = 5
    private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    private const val TIMEOUT_MS = 8000
    private const val MAX_CACHE_LINKS = 128
    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024

    private data class ImageSize(val width: Double, val height: Double)

    private class CachedImage(val bytes: ByteArray) {
        val decoded = HashMap<ImageSize, Image>()
        @Volatile var memoryBytes = bytes.size.toLong()
    }

    private val cacheLock = Any()
    private val cache = LinkedHashMap<URI, CachedImage>(16, 0.75f, true)
    private val loading = HashMap<URI, CompletableFuture<CachedImage>>()

    @JvmStatic
    fun load(destination: String, diagnostic: MarkdownImageLoadLog, width: Double, height: Double): Image {
        val uri = imageUri(destination)
        val data = imageData(uri, diagnostic)
        try {
            val image = synchronized(data) {
                val size = ImageSize(width, height)
                val known = data.decoded[size]
                if (known != null) {
                    diagnostic.event("cache decoded hit requestedWidth=$width requestedHeight=$height")
                    known
                } else {
                    Image(data.bytes.inputStream(), width, height, true, true).also { result ->
                        if (!result.isError && result.width > 0 && result.height > 0) {
                            data.decoded[size] = result
                            data.memoryBytes += (result.width * result.height * 4).toLong()
                        }
                    }
                }
            }
            synchronized(cacheLock) {
                if (image.isError || image.width <= 0 || image.height <= 0) {
                    cache.entries.removeIf { it.value === data }
                } else {
                    trimCache()
                }
            }
            return image
        } catch (error: Exception) {
            synchronized(cacheLock) { cache.entries.removeIf { it.value === data } }
            diagnostic.fail("remote decode threw exception", error)
            throw error
        }
    }

    // 查询参数参与缓存键，片段不参与；保留路径大小写和已编码的字符。
    private fun imageUri(destination: String): URI =
        URI(URI(destination.replace(" ", "%20")).normalize().toASCIIString().substringBefore('#'))

    private fun imageData(uri: URI, diagnostic: MarkdownImageLoadLog): CachedImage {
        val request: CompletableFuture<CachedImage>
        val owner: Boolean
        synchronized(cacheLock) {
            val known = cache[uri]
            if (known != null) {
                diagnostic.event("cache hit bytes=${known.bytes.size}")
                return known
            }
            val pending = loading[uri]
            owner = pending == null
            request = pending ?: CompletableFuture<CachedImage>().also { loading[uri] = it }
        }
        if (!owner) {
            diagnostic.event("cache wait shared download")
            return request.get()
        }
        try {
            val data = download(uri, diagnostic)
            request.complete(data)
            return data
        } catch (error: Exception) {
            request.completeExceptionally(error)
            throw error
        } finally {
            synchronized(cacheLock) { loading.remove(uri) }
        }
    }

    // 调用方持有 cacheLock；重定向别名共享同一份数据，不重复计入缓存字节预算。
    private fun trimCache() {
        var memory = cache.values.toSet().sumOf { it.memoryBytes }
        while (cache.size > MAX_CACHE_LINKS || memory > MAX_CACHE_BYTES) {
            val oldest = cache.entries.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
            memory = cache.values.toSet().sumOf { it.memoryBytes }
        }
    }

    private fun download(destination: URI, diagnostic: MarkdownImageLoadLog): CachedImage {
        var stage = "resolve"
        try {
            var uri = destination
            val visited = HashSet<URI>()
            for (redirects in 0..MAX_REDIRECTS) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image request interrupted")
                if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) {
                    throw IOException("Unsupported image redirect protocol: ${uri.scheme}")
                }
                if (!visited.add(uri)) throw IOException("Image redirect loop: $uri")
                synchronized(cacheLock) {
                    val known = cache[uri]
                    if (known != null) {
                        visited.forEach { cache[it] = known }
                        trimCache()
                        diagnostic.event("cache hit redirectUrl=$uri bytes=${known.bytes.size}")
                        return known
                    }
                }
                stage = "connect"
                val connection = uri.toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                try {
                    val status = connection.responseCode
                    val length = connection.contentLengthLong
                    diagnostic.event("response status=$status contentType=${connection.contentType}" +
                        " contentLength=$length finalUrl=$uri redirects=$redirects")
                    if (status in setOf(301, 302, 303, 307, 308)) {
                        val location = connection.getHeaderField("Location")
                        if (location.isNullOrBlank()) throw IOException("Image redirect missing Location: HTTP $status")
                        if (redirects == MAX_REDIRECTS) throw IOException("Image exceeded $MAX_REDIRECTS redirects")
                        val next = imageUri(uri.resolve(location.trim().replace(" ", "%20")).toString())
                        diagnostic.event("redirect status=$status from=$uri to=$next")
                        uri = next
                        continue
                    }
                    if (status !in 200..299) throw IOException("Image request returned HTTP $status at $uri")
                    if (length > MAX_IMAGE_BYTES) throw IOException("Image exceeds $MAX_IMAGE_BYTES byte limit")
                    stage = "read"
                    val bytes = connection.inputStream.use { it.readNBytes(MAX_IMAGE_BYTES + 1) }
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image request interrupted")
                    diagnostic.event("read bytes=${bytes.size}")
                    if (bytes.size > MAX_IMAGE_BYTES) throw IOException("Image exceeds $MAX_IMAGE_BYTES byte limit")
                    if (bytes.isEmpty()) throw IOException("Empty image response at $uri")
                    val data = CachedImage(bytes)
                    synchronized(cacheLock) {
                        visited.forEach { cache[it] = data }
                        trimCache()
                    }
                    diagnostic.event("cache stored bytes=${bytes.size} links=${visited.size}")
                    return data
                } finally {
                    connection.disconnect()
                }
            }
            throw IOException("Image exceeded $MAX_REDIRECTS redirects")
        } catch (error: Exception) {
            diagnostic.fail("remote $stage threw exception", error)
            throw error
        }
    }
}

package com.allan.atools.tools.modulenotepad.manager

import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageInputStream
import javax.imageio.stream.ImageInputStream
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.ceil
import kotlin.math.sqrt

/** 图片先读取原始尺寸，再限制解码像素；排版继续使用原始尺寸。 */
object MarkdownImageDecoder {
    data class Result(val image: Image, val width: Double, val height: Double)

    @JvmStatic
    fun decode(bytes: ByteArray, requestedWidth: Double, requestedHeight: Double): Result =
        MemoryCacheImageInputStream(bytes.inputStream()).use { input ->
            decode(input, { bytes.inputStream() }, requestedWidth, requestedHeight)
        }

    @JvmStatic
    fun decode(file: File, requestedWidth: Double, requestedHeight: Double): Result =
        FileImageInputStream(file).use { input ->
            decode(input, { file.inputStream() }, requestedWidth, requestedHeight)
        }

    private fun decode(input: ImageInputStream, open: () -> InputStream, requestedWidth: Double, requestedHeight: Double): Result {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image request interrupted")
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) {
            val bytes = open().use { it.readNBytes(20 * 1024 * 1024 + 1) }
            if (bytes.size > 20 * 1024 * 1024) throw IOException("Image exceeds byte limit")
            return MarkdownSvgDecoder.decode(bytes, requestedWidth, requestedHeight)
        }
        val reader = readers.next()
        try {
            reader.input = input
            val width = reader.getWidth(0).toDouble()
            val height = reader.getHeight(0).toDouble()
            if (width <= 0 || height <= 0) throw IOException("Invalid image dimensions")
            var factor = Math.min(1.0, sqrt(4_194_304.0 / (width * height)))
            if (requestedWidth > 0) factor = Math.min(factor, requestedWidth / width)
            if (requestedHeight > 0) factor = Math.min(factor, requestedHeight / height)
            val decodedWidth = Math.max(1.0, width * factor)
            val decodedHeight = Math.max(1.0, height * factor)
            var image = open().use { Image(it, decodedWidth, decodedHeight, true, true) }
            if (image.isError) {
                val options = reader.defaultReadParam
                val sample = Math.max(1, ceil(1.0 / factor).toInt())
                options.setSourceSubsampling(sample, sample, 0, 0)
                image = SwingFXUtils.toFXImage(reader.read(0, options), null)
            }
            return Result(image, width, height)
        } finally {
            reader.dispose()
        }
    }
}

/** 正文和表格共用四个图片工作线程，取消后及时移除尚未开始的任务。 */
object MarkdownImageTasks {
    private val workers = ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()) { task ->
        Thread(task, "markdown-image-loader").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    @JvmStatic fun submit(action: Runnable): Future<*> = workers.submit(action)
    @JvmStatic fun cancel(task: Future<*>?) {
        task?.cancel(true)
        workers.purge()
    }
}

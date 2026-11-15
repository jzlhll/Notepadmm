package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.image.PasteHubImageCodec
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.roundToInt

/** 图片编辑保留原图快照，由 PasteHubImage 缩放和编码，保存直接发布已预览的 WebP 字节。 */
class MarkdownImageCompression private constructor(
    val file: File,
    val info: ImageInfo,
    private val sourceBytes: ByteArray,
    private val rgba: ByteArray,
    val originalImage: Image
) {
    data class Options(val quality: Int = 85, val scale: Double = 1.0) {
        init {
            require(quality in 0..100) { "Image quality must be between 0 and 100" }
            require(scale.isFinite() && scale > 0.0 && scale <= 1.0) { "Image scale must be greater than 0 and at most 1" }
        }
    }

    data class ImageInfo(val width: Int, val height: Int, val format: String, val byteSize: Long)
    data class Size(val width: Int, val height: Int)
    data class EncodedImage(val bytes: ByteArray, val source: ImageInfo, val output: ImageInfo, val options: Options, val image: Image)

    companion object {
        private const val MAX_INPUT_BYTES = 20 * 1024 * 1024
        private const val MAX_PIXELS = 40_000_000L
        private val QUALITY_SUFFIX = Regex("_compress([0-9]{1,3})$", RegexOption.IGNORE_CASE)

        fun compressedQuality(file: File): Int? {
            if (file.extension.lowercase(Locale.ROOT) !in listOf("webp", "jpg", "jpeg")) return null
            return QUALITY_SUFFIX.find(file.nameWithoutExtension)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
        }

        fun read(file: File): MarkdownImageCompression {
            val source = file.canonicalFile
            val bytes = readBytes(source)
            MemoryCacheImageInputStream(bytes.inputStream()).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) throw IOException("Image reader unavailable")
                val reader = readers.next()
                try {
                    reader.input = input
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) { "Image exceeds pixel limit" }
                    val format = reader.formatName.lowercase(Locale.ROOT)
                    require(reader.getNumImages(true) == 1 && !hasAnimationChunk(bytes, format)) { "Input must be a static image" }
                    val image = reader.read(0) ?: throw IOException("Image decode failed")
                    try {
                        val rgba = ByteArray(width * height * 4)
                        val row = IntArray(width)
                        for (y in 0 until height) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image editing interrupted")
                            image.getRGB(0, y, width, 1, row, 0, width)
                            for (x in 0 until width) {
                                val pixel = row[x]
                                val offset = (y * width + x) * 4
                                rgba[offset] = (pixel ushr 16).toByte()
                                rgba[offset + 1] = (pixel ushr 8).toByte()
                                rgba[offset + 2] = pixel.toByte()
                                rgba[offset + 3] = (pixel ushr 24).toByte()
                            }
                        }
                        return MarkdownImageCompression(source, ImageInfo(width, height, format, bytes.size.toLong()),
                            bytes, rgba, SwingFXUtils.toFXImage(image, null))
                    } finally { image.flush() }
                } finally { reader.dispose() }
            }
        }

        private fun hasAnimationChunk(bytes: ByteArray, format: String): Boolean {
            if (format != "png" && format != "webp") return false
            val littleEndian = format == "webp"
            var offset = if (littleEndian) 12 else 8
            val overhead = if (littleEndian) 8 else 12
            while (offset <= bytes.size - overhead) {
                val lengthOffset = if (littleEndian) offset + 4 else offset
                var length = 0L
                for (index in 0..3) {
                    val shift = if (littleEndian) index * 8 else (3 - index) * 8
                    length = length or ((bytes[lengthOffset + index].toLong() and 255) shl shift)
                }
                val nameOffset = if (littleEndian) offset else offset + 4
                val name = String(bytes, nameOffset, 4, Charsets.US_ASCII)
                if (name == "acTL" && format == "png" || format == "webp" && (name == "ANIM" || name == "ANMF")) return true
                if (format == "webp" && name == "VP8X" && length > 0 && offset + 8 < bytes.size &&
                    (bytes[offset + 8].toInt() and 2) != 0) return true
                val next = offset.toLong() + length + overhead + if (littleEndian) length % 2 else 0L
                if (next > bytes.size) return false
                offset = next.toInt()
            }
            return false
        }

        private fun readBytes(file: File): ByteArray {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image editing interrupted")
            val bytes = file.inputStream().use { it.readNBytes(MAX_INPUT_BYTES + 1) }
            require(bytes.size <= MAX_INPUT_BYTES) { "Image exceeds byte limit" }
            return bytes
        }
    }

    fun scaledSize(options: Options): Size {
        if (options.scale == 1.0) return Size(info.width, info.height)
        val longSide = if (info.width >= info.height) info.width else info.height
        val rounded = (longSide * options.scale / 10).roundToInt().toLong() * 10
        val targetLong = Math.max(1L, Math.min(longSide.toLong(), rounded))
        val ratio = targetLong.toDouble() / longSide
        return Size(Math.max(1, (info.width * ratio).roundToInt()), Math.max(1, (info.height * ratio).roundToInt()))
    }

    fun preview(options: Options): EncodedImage {
        val size = scaledSize(options)
        val bytes = PasteHubImageCodec().encodeWebp(rgba, info.width, info.height, size.width, size.height, options.quality)
        val image = MemoryCacheImageInputStream(bytes.inputStream()).use { input ->
            // ImageIO.read(ImageInputStream) 会自行关闭流，此处由外层 use 统一关闭。
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) throw IOException("WebP preview reader unavailable")
            val reader = readers.next()
            try {
                reader.input = input
                val decoded = reader.read(0) ?: throw IOException("WebP preview decode failed")
                try { SwingFXUtils.toFXImage(decoded, null) } finally { decoded.flush() }
            } finally { reader.dispose() }
        }
        return EncodedImage(bytes, info, ImageInfo(size.width, size.height, "webp", bytes.size.toLong()), options, image)
    }

    fun save(encoded: EncodedImage, options: Options): File {
        require(encoded.output.format == "webp" && encoded.options == options) { "Image editor output must match WebP preview options" }
        val size = scaledSize(options)
        require(encoded.source == info && encoded.output.width == size.width && encoded.output.height == size.height) {
            "Image preview does not match the source or target dimensions"
        }
        require(readBytes(file).contentEquals(sourceBytes)) { "Source image changed after preview" }
        val resized = size.width != info.width || size.height != info.height
        val stem = if (compressedQuality(file) == null) file.nameWithoutExtension else QUALITY_SUFFIX.replace(file.nameWithoutExtension, "")
        var output = if (resized) File(file.parentFile, "${UUID.randomUUID()}.webp")
            else File(file.parentFile, "${stem}_compress${options.quality}.webp")
        val temporary = Files.createTempFile(file.toPath().parent, ".atools-image-edit-", ".webp")
        try {
            Files.write(temporary, encoded.bytes)
            if (Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java) != null) {
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(file.toPath()))
            }
            require(readBytes(file).contentEquals(sourceBytes)) { "Source image changed while saving" }
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image editing interrupted")
                try {
                    Files.move(temporary, output.toPath())
                    return output
                } catch (_: FileAlreadyExistsException) {
                    output = if (resized) File(file.parentFile, "${UUID.randomUUID()}.webp")
                        else File(file.parentFile, "${stem}_${UUID.randomUUID()}_compress${options.quality}.webp")
                }
            }
        } finally { Files.deleteIfExists(temporary) }
    }
}

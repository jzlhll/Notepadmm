package com.allan.uilibs.image

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFileAttributeView
import java.util.Locale
import javax.imageio.IIOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.roundToInt

/**
 * 静态图片的格式转换、等比缩小和编码质量设置，不依赖 JavaFX 或应用状态。
 * 文件及字节数组共用 ImageIO 编解码器，WebP 等扩展格式取决于运行时注册的插件。
 * 所有操作同步执行，由调用方安排后台线程、确认覆盖及界面回写；输出不保留原有元数据。
 *
 * ```kotlin
 * val helper = ImageCompressionHelper()
 * helper.compress(File("input.png"), File("output.jpg"))
 * helper.compress(File("input.webp"), File("small.jpg"), ImageCompressionHelper.Options(quality = 85, scale = 0.6))
 * ```
 */
class ImageCompressionHelper @JvmOverloads constructor(
    private val maxInputBytes: Int = 20 * 1024 * 1024,
    private val maxPixels: Long = 40_000_000
) {
    enum class Format(val imageIoName: String, val extension: String) {
        JPEG("jpeg", "jpg"), PNG("png", "png"), WEBP("webp", "webp")
    }

    /** quality 为 JPEG／有损 WebP 的 0～100 编码参数；PNG 始终使用无损编码。 */
    data class Options @JvmOverloads constructor(
        val format: Format = Format.JPEG,
        val quality: Int = 85,
        val scale: Double = 1.0,
        val backgroundRgb: Int = 0xFFFFFF,
        val roundLongEdgeTo: Int = 1
    ) {
        init {
            require(quality in 0..100) { "Image quality must be between 0 and 100" }
            require(scale.isFinite() && scale > 0.0 && scale <= 1.0) { "Image scale must be greater than 0 and at most 1" }
            require(backgroundRgb in 0..0xFFFFFF) { "Image background must be a 24-bit RGB color" }
            require(roundLongEdgeTo > 0) { "Image dimension rounding step must be positive" }
        }
    }

    data class ImageInfo(val width: Int, val height: Int, val format: String, val byteSize: Long, val animated: Boolean)
    data class Size(val width: Int, val height: Int)
    data class Result(val source: ImageInfo, val output: ImageInfo)
    data class EncodedImage(val bytes: ByteArray, val result: Result)

    init {
        require(maxInputBytes in 1 until Int.MAX_VALUE) { "Invalid image byte limit" }
        require(maxPixels > 0) { "Invalid image pixel limit" }
    }

    /** 只检查是否注册了对应编码器，不安装或加载额外的原生库。 */
    fun canWrite(format: Format): Boolean = ImageIO.getImageWritersByFormatName(format.imageIoName).hasNext()

    @Throws(IOException::class)
    fun readInfo(source: File): ImageInfo = withReader(readBytes(source)) { _, info -> info }

    @Throws(IOException::class)
    fun readInfo(source: ByteArray): ImageInfo = withReader(source) { _, info -> info }

    /** 按长边计算等比目标尺寸；取整步长为 1 时按像素取整，比例为 1 时保留原尺寸。 */
    @JvmOverloads
    fun scaledSize(width: Int, height: Int, scale: Double, roundLongEdgeTo: Int = 1): Size {
        require(width > 0 && height > 0 && width.toLong() * height <= maxPixels) { "Invalid source image dimensions" }
        require(scale.isFinite() && scale > 0.0 && scale <= 1.0) { "Image scale must be greater than 0 and at most 1" }
        require(roundLongEdgeTo > 0) { "Image dimension rounding step must be positive" }
        if (scale == 1.0) return Size(width, height)
        val longSide = if (width >= height) width else height
        val rounded = (longSide * scale / roundLongEdgeTo).roundToInt().toLong() * roundLongEdgeTo
        val targetLong = Math.max(1L, Math.min(longSide.toLong(), rounded))
        val ratio = targetLong.toDouble() / longSide
        return Size(Math.max(1, (width * ratio).roundToInt()), Math.max(1, (height * ratio).roundToInt()))
    }

    /** 只调整真实像素，不编码文件；调用方负责释放返回的 BufferedImage，源图不修改。 */
    @Throws(IOException::class)
    fun resize(source: BufferedImage, width: Int, height: Int): BufferedImage {
        require(width > 0 && height > 0 && width.toLong() * height <= maxPixels) { "Invalid target image dimensions" }
        checkInterrupted()
        return draw(source, width, height, source.colorModel.hasAlpha(), 0xFFFFFF)
    }

    /** 完整解码原始像素后转换；拒绝动图，避免只输出第一帧。 */
    @JvmOverloads
    @Throws(IOException::class)
    fun compress(source: ByteArray, options: Options = Options()): EncodedImage {
        if (!canWrite(options.format)) throw IIOException("Image writer unavailable: ${options.format.imageIoName}")
        return withReader(source) { reader, info ->
            if (info.animated) throw IIOException("Animated images cannot be compressed as static images")
            checkInterrupted()
            val original = reader.read(0) ?: throw IIOException("Image decode failed")
            try {
                checkInterrupted()
                val size = scaledSize(info.width, info.height, options.scale, options.roundLongEdgeTo)
                // 必须先完成真实像素缩放，再转换颜色模型并执行一次最终质量编码。
                val resized = if (size.width != info.width || size.height != info.height) resize(original, size.width, size.height) else original
                try {
                    val convert = when (options.format) {
                        Format.JPEG -> resized.type != BufferedImage.TYPE_INT_RGB
                        Format.WEBP -> resized.type != BufferedImage.TYPE_INT_RGB && resized.type != BufferedImage.TYPE_INT_ARGB
                        Format.PNG -> false
                    }
                    val image = if (convert) draw(resized, size.width, size.height,
                        options.format != Format.JPEG && resized.colorModel.hasAlpha(), options.backgroundRgb) else resized
                    try {
                        val bytes = encode(image, options)
                        EncodedImage(bytes, Result(info, ImageInfo(size.width, size.height, options.format.imageIoName, bytes.size.toLong(), false)))
                    } finally { if (image !== resized) image.flush() }
                } finally { if (resized !== original) resized.flush() }
            } finally { original.flush() }
        }
    }

    /**
     * 编码完成后才写入目标路径，默认拒绝覆盖；覆盖采用原子替换，不支持时保留原文件并报错。
     * 目标扩展名须与 format 对应，格式转换应写入新的文件名。
     * 覆盖源文件时再次比较原始字节，拒绝覆盖编码期间已经变化的内容。
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun compress(source: File, target: File, options: Options = Options(), overwrite: Boolean = false): Result {
        val input = source.toPath().toRealPath()
        val output = target.toPath().toAbsolutePath().normalize()
        val extension = target.extension.lowercase(Locale.ROOT)
        require(extension == options.format.extension || options.format == Format.JPEG && extension == "jpeg") {
            "Image file extension does not match output format: ${options.format.imageIoName}"
        }
        if (!Files.isDirectory(output.parent)) throw IOException("Image output directory is missing")
        if (Files.isSymbolicLink(output)) throw IOException("Image output cannot be a symbolic link")
        if (!overwrite && Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw java.nio.file.FileAlreadyExistsException(output.toString())
        val replacingSource = Files.exists(output) && Files.isSameFile(input, output)
        val bytes = readBytes(input.toFile())
        val encoded = compress(bytes, options)
        checkInterrupted()
        val temporary = Files.createTempFile(output.parent, ".image-compression-", ".${options.format.extension}")
        try {
            Files.write(temporary, encoded.bytes)
            checkInterrupted()
            if (replacingSource && !readBytes(input.toFile()).contentEquals(bytes)) {
                throw IOException("Source image changed while compressing")
            }
            if (Files.exists(output)) {
                if (!overwrite) throw java.nio.file.FileAlreadyExistsException(output.toString())
                if (Files.isSymbolicLink(output)) throw IOException("Image output cannot be a symbolic link")
                if (Files.getFileAttributeView(output, PosixFileAttributeView::class.java) != null) {
                    Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(output))
                }
            }
            if (overwrite) Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            else Files.move(temporary, output)
            return encoded.result
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun readBytes(source: File): ByteArray {
        checkInterrupted()
        val bytes = source.inputStream().use { it.readNBytes(maxInputBytes + 1) }
        if (bytes.size > maxInputBytes) throw IIOException("Image exceeds byte limit: $maxInputBytes")
        return bytes
    }

    private fun <T> withReader(bytes: ByteArray, action: (ImageReader, ImageInfo) -> T): T {
        checkInterrupted()
        if (bytes.isEmpty() || bytes.size > maxInputBytes) throw IIOException("Invalid image byte size: ${bytes.size}")
        MemoryCacheImageInputStream(bytes.inputStream()).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) throw IIOException("Image reader unavailable")
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0 || width.toLong() * height > maxPixels) {
                    throw IIOException("Image exceeds pixel limit: ${width}x$height")
                }
                val format = reader.formatName.lowercase(Locale.ROOT)
                val frames = reader.getNumImages(true)
                val animated = frames > 1 || hasAnimationChunk(bytes, format)
                if (frames <= 0 && !animated) throw IIOException("Image frame count is unknown")
                return action(reader, ImageInfo(width, height, format, bytes.size.toLong(), animated))
            } finally { reader.dispose() }
        }
    }

    private fun draw(source: BufferedImage, width: Int, height: Int, alpha: Boolean, backgroundRgb: Int): BufferedImage {
        val image = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            if (alpha) graphics.composite = AlphaComposite.Src else {
                graphics.color = Color(backgroundRgb)
                graphics.fillRect(0, 0, width, height)
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(source, 0, 0, width, height, null)
            return image
        } catch (error: Exception) {
            image.flush()
            throw error
        } finally { graphics.dispose() }
    }

    private fun encode(image: BufferedImage, options: Options): ByteArray {
        checkInterrupted()
        val writers = ImageIO.getImageWritersByFormatName(options.format.imageIoName)
        if (!writers.hasNext()) throw IIOException("Image writer unavailable: ${options.format.imageIoName}")
        val writer = writers.next()
        try {
            val params = writer.defaultWriteParam
            if (options.format != Format.PNG) {
                if (!params.canWriteCompressed()) throw IIOException("Image writer does not support quality settings")
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionTypes?.let { types ->
                    val type = if (options.format == Format.WEBP) types.firstOrNull { it.equals("Lossy", true) }
                        else types.firstOrNull()
                    params.compressionType = type ?: throw IIOException("Image writer has no supported lossy compression mode")
                }
                params.compressionQuality = options.quality / 100f
            }
            val bytes = ByteArrayOutputStream()
            MemoryCacheImageOutputStream(bytes).use { output ->
                writer.output = output
                writer.write(null, IIOImage(image, null, null), params)
            }
            checkInterrupted()
            return bytes.toByteArray()
        } finally { writer.dispose() }
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

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image compression interrupted")
    }
}

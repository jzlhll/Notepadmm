package com.allan.atools.tools.modulenotepad.manager

import com.allan.uilibs.image.ImageCompressionHelper
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.util.UUID
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

/** 图片编辑使用固定的原图快照，预览只写内存，保存直接发布用户确认的同一份 JPEG 字节。 */
class MarkdownImageCompression private constructor(
    val file: File,
    val info: ImageCompressionHelper.ImageInfo,
    private val sourceBytes: ByteArray
) {
    companion object {
        private const val MAX_INPUT_BYTES = 20 * 1024 * 1024
        private val QUALITY_SUFFIX = Regex("_compress([0-9]{1,3})$", RegexOption.IGNORE_CASE)

        fun compressedQuality(file: File): Int? {
            if (!file.extension.equals("jpg", true) && !file.extension.equals("jpeg", true)) return null
            return QUALITY_SUFFIX.find(file.nameWithoutExtension)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
        }

        fun read(file: File): MarkdownImageCompression {
            val source = file.canonicalFile
            val bytes = readBytes(source)
            val info = ImageCompressionHelper().readInfo(bytes)
            require(!info.animated) { "Animated images cannot be edited as static images" }
            return MarkdownImageCompression(source, info, bytes)
        }

        private fun readBytes(file: File): ByteArray {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image editing interrupted")
            val bytes = file.inputStream().use { it.readNBytes(MAX_INPUT_BYTES + 1) }
            require(bytes.size <= MAX_INPUT_BYTES) { "Image exceeds byte limit" }
            return bytes
        }
    }

    fun originalImage(): Image = MemoryCacheImageInputStream(sourceBytes.inputStream()).use { input ->
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) throw IOException("Image reader unavailable")
        val reader = readers.next()
        try {
            reader.input = input
            val image = reader.read(0) ?: throw IOException("Image decode failed")
            try { SwingFXUtils.toFXImage(image, null) } finally { image.flush() }
        } finally { reader.dispose() }
    }

    fun preview(options: ImageCompressionHelper.Options): ImageCompressionHelper.EncodedImage =
        ImageCompressionHelper().compress(sourceBytes, options)

    fun save(encoded: ImageCompressionHelper.EncodedImage, options: ImageCompressionHelper.Options): File {
        require(options.format == ImageCompressionHelper.Format.JPEG && encoded.result.output.format == "jpeg") { "Image editor output must be JPEG" }
        val size = ImageCompressionHelper().scaledSize(info.width, info.height, options.scale, options.roundLongEdgeTo)
        require(encoded.result.source == info && encoded.result.output.width == size.width && encoded.result.output.height == size.height) {
            "Image preview does not match the source or target dimensions"
        }
        require(readBytes(file).contentEquals(sourceBytes)) { "Source image changed after preview" }
        val resized = size.width != info.width || size.height != info.height
        val stem = if (compressedQuality(file) == null) file.nameWithoutExtension else QUALITY_SUFFIX.replace(file.nameWithoutExtension, "")
        var output = if (resized) File(file.parentFile, "${UUID.randomUUID()}.jpg")
            else File(file.parentFile, "${stem}_compress${options.quality}.jpg")
        val temporary = Files.createTempFile(file.toPath().parent, ".atools-image-edit-", ".jpg")
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
                    output = if (resized) File(file.parentFile, "${UUID.randomUUID()}.jpg")
                        else File(file.parentFile, "${stem}_${UUID.randomUUID()}_compress${options.quality}.jpg")
                }
            }
        } finally { Files.deleteIfExists(temporary) }
    }
}

package com.allan.atools.image

import com.allan.atools.utils.ResLocation
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

/** 通过 PasteHubImage 的 C ABI 缩放 RGBA 并编码有损 WebP，原生输出始终由库释放。 */
class PasteHubImageCodec {
    @Structure.FieldOrder("structSize", "format", "width", "height", "scalePercent", "quality", "webpLossless")
    class NativeOptions : Structure() {
        @JvmField var structSize = 0
        @JvmField var format = 0
        @JvmField var width = 0
        @JvmField var height = 0
        @JvmField var scalePercent = 100.0
        @JvmField var quality = 85
        @JvmField var webpLossless = 0
    }

    @Structure.FieldOrder("data", "byteSize", "width", "height", "format")
    class NativeBuffer : Structure() {
        @JvmField var data: Pointer? = null
        @JvmField var byteSize = 0L
        @JvmField var width = 0
        @JvmField var height = 0
        @JvmField var format = 0
    }

    interface Api : Library {
        fun ph_image_options_init(options: NativeOptions)
        fun ph_image_encode_rgba(
            rgba: ByteArray, size: Long, width: Int, height: Int, stride: Long,
            options: NativeOptions, output: NativeBuffer, error: ByteArray, errorCapacity: Long
        ): Int
        fun ph_image_buffer_free(buffer: NativeBuffer)
    }

    companion object {
        private const val WEBP = 3
        private val api: Api by lazy {
            if (Native.SIZE_T_SIZE != 8) throw IOException("PasteHubImage requires a 64-bit runtime")
            val platform = when {
                ResLocation.isOsx -> "macos"
                ResLocation.isWindow -> when (System.getProperty("os.arch").lowercase()) {
                    "aarch64", "arm64" -> "windows-arm64"
                    "amd64", "x86_64" -> "windows-x64"
                    else -> throw IOException("Unsupported PasteHubImage architecture")
                }
                else -> throw IOException("Unsupported PasteHubImage platform")
            }
            val name = if (ResLocation.isOsx) "libPasteHubImage.dylib" else "PasteHubImage.dll"
            val library = File(ResLocation.getRealPath("native", platform, name))
            if (!library.isFile) throw IOException("PasteHubImage library is missing: $library")
            try {
                Native.load(library.absolutePath, Api::class.java)
            } catch (error: LinkageError) {
                throw IOException("Load PasteHubImage failed: $library", error)
            }
        }
    }

    fun encodeWebp(rgba: ByteArray, width: Int, height: Int, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray {
        require(width > 0 && height > 0 && width.toLong() * height * 4 == rgba.size.toLong()) { "Invalid RGBA dimensions" }
        require(targetWidth in 1..16383 && targetHeight in 1..16383) { "WebP dimensions must not exceed 16383 pixels per side" }
        require(quality in 0..100) { "Image quality must be between 0 and 100" }
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image compression interrupted")
        val library = api
        val options = NativeOptions()
        library.ph_image_options_init(options)
        options.format = WEBP
        options.width = targetWidth
        options.height = targetHeight
        options.quality = quality
        options.webpLossless = 0
        val output = NativeBuffer()
        val error = ByteArray(512)
        try {
            val status = library.ph_image_encode_rgba(rgba, rgba.size.toLong(), width, height, width.toLong() * 4,
                options, output, error, error.size.toLong())
            if (status != 0) throw IOException("PasteHubImage encode failed ($status): ${Native.toString(error, "UTF-8")}")
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Image compression interrupted")
            val data = output.data ?: throw IOException("PasteHubImage returned no data")
            if (output.byteSize !in 1..Int.MAX_VALUE.toLong() || output.format != WEBP ||
                output.width != targetWidth || output.height != targetHeight) {
                throw IOException("PasteHubImage returned an invalid output buffer")
            }
            return data.getByteArray(0, output.byteSize.toInt())
        } finally {
            library.ph_image_buffer_free(output)
        }
    }
}

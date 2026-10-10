package com.allan.atools.richtext.codearea

import javafx.scene.image.Image
import javafx.scene.image.PixelFormat
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** 比较各格式的实际内容，避免同类型的新剪贴板内容被后台复制覆盖。 */
class MarkdownClipboardSnapshot private constructor(private val contents: Map<DataFormat, Any>) {
    private data class ImageContent(val width: Int, val height: Int, val digest: ByteBuffer)

    fun matches(clipboard: Clipboard): Boolean = capture(clipboard)?.contents == contents

    companion object {
        fun capture(clipboard: Clipboard): MarkdownClipboardSnapshot? {
            return try {
                val types = clipboard.contentTypes.toSet()
                val contents = LinkedHashMap<DataFormat, Any>()
                for (type in types) {
                    contents[type] = freeze(clipboard.getContent(type)) ?: return null
                }
                // 读取不同格式时也可能发生外部复制；类型变化时不能接受这份快照。
                if (clipboard.contentTypes != types) null else MarkdownClipboardSnapshot(contents)
            } catch (_: Exception) {
                null
            }
        }

        private fun freeze(value: Any?): Any? {
            return when (value) {
                is String, is Boolean, is Char, is Byte, is Short, is Int, is Long, is Float, is Double, is File -> value
                is ByteArray -> ByteBuffer.wrap(value.copyOf()).asReadOnlyBuffer()
                is ByteBuffer -> {
                    val buffer = value.asReadOnlyBuffer()
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    ByteBuffer.wrap(bytes).asReadOnlyBuffer()
                }
                is List<*> -> {
                    val items = ArrayList<Any>(value.size)
                    for (item in value) items.add(freeze(item) ?: return null)
                    items
                }
                is Image -> {
                    val reader = value.pixelReader ?: return null
                    val width = value.width.toInt()
                    val height = value.height.toInt()
                    if (width <= 0 || height <= 0) return null
                    val digest = MessageDigest.getInstance("SHA-256")
                    // 分块读取全部像素，摘要内存不随图片尺寸增长。
                    val pixels = ByteArray(4096 * 4)
                    val format = PixelFormat.getByteBgraInstance()
                    for (y in 0 until height) {
                        var x = 0
                        while (x < width) {
                            val count = Math.min(4096, width - x)
                            reader.getPixels(x, y, count, 1, format, pixels, 0, count * 4)
                            digest.update(pixels, 0, count * 4)
                            x += count
                        }
                    }
                    ImageContent(width, height, ByteBuffer.wrap(digest.digest()).asReadOnlyBuffer())
                }
                // 不能可靠比较的自定义内容不作为允许后台覆盖的依据。
                else -> null
            }
        }
    }
}

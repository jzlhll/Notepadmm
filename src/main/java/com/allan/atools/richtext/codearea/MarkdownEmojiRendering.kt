package com.allan.atools.richtext.codearea

import com.allan.atools.utils.Log
import com.allan.uilibs.richtexts.CodeArea
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.scene.transform.Scale
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.floor

/** 以高分辨率系统字形覆盖表情，原 Text 保留排版、光标与源码坐标。 */
class MarkdownEmojiRendering(private val area: CodeArea) : Pane() {
    private class Entry(val view: ImageView, val opacity: Double) {
        var rasterKey: Key? = null
        var raster: Raster? = null
        var measurement: Measurement? = null
        var ink: Bounds? = null
    }
    private data class Measurement(val key: Key, val font: Font, val bounds: Bounds, val baseline: Double)
    private val entries = IdentityHashMap<Text, Entry>()

    init {
        isManaged = false
        isMouseTransparent = true
    }

    fun refresh() {
        if (scene == null) {
            clear()
            return
        }
        var ancestor: Node? = area
        while (ancestor != null) {
            if (!ancestor.isVisible || ancestor.opacity == 0.0) {
                clear()
                return
            }
            ancestor = ancestor.parent
        }
        val visible = area.lookupAll(".${CodeArea.MARKDOWN_EMOJI_RENDERED_CLASS}")
            .filterIsInstance<Text>().filter { it.isVisible && it.scene === scene }.toSet()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (text, entry) = iterator.next()
            if (text !in visible) {
                text.opacity = entry.opacity
                children.remove(entry.view)
                iterator.remove()
            }
        }
        for (text in visible) {
            val entry = entries[text]
            val glyph = text.text.replace("\u2060", "")
            val original = text.layoutBounds
            val flow = text.parent as? TextFlow
            var start = 0
            if (flow != null) for (child in flow.childrenUnmodifiable) {
                if (!child.isManaged) continue
                if (child === text) break
                start += if (child is Text) child.text.length else 1
            }
            val singleLine = flow != null && flow.rangeShape(start, start + text.text.length)
                .count { it is MoveTo } == 1
            val key = Key(text.font.name, glyph, text.fill, text.isUnderline, text.isStrikethrough)
            // 多行合并的 Text 继续使用原生绘制，避免把换行字形压成一行。
            val raster = when {
                glyph.isEmpty() || glyph.length > 32 || original.width <= 0.0 || !singleLine -> null
                entry != null && entry.rasterKey == key -> entry.raster
                else -> raster(key)
            }
            if (raster == null) {
                if (entry != null) {
                    text.opacity = entry.opacity
                    children.remove(entry.view)
                    entries.remove(text)
                }
                continue
            }
            val current = entry ?: Entry(ImageView().apply {
                isManaged = false
                isSmooth = true
                clip = Rectangle()
            }, text.opacity).also {
                entries[text] = it
                children.add(it.view)
            }
            current.rasterKey = key
            current.raster = raster
            // TextFlow 的行高由一整行的字体决定，不能用行高拉伸单个表情。
            // 测量原节点实际着墨位置，保留混排和换行后的真实基线与大小。
            val measurement = Measurement(key, text.font, text.boundsInLocal, flow!!.baselineOffset)
            if (current.measurement != measurement) {
                current.ink = measureInk(text, current.opacity)
                current.measurement = measurement
            }
            val ink = current.ink
            if (ink == null) {
                text.opacity = current.opacity
                current.view.isVisible = false
                continue
            }
            val ratioX = ink.width / raster.ink.width
            val ratioY = ink.height / raster.ink.height
            val local = BoundingBox(
                ink.minX - raster.ink.minX * ratioX,
                ink.minY - raster.ink.minY * ratioY,
                raster.image.width * ratioX, raster.image.height * ratioY
            )
            val target = sceneToLocal(text.localToScene(local))
            val view = current.view
            view.image = raster.image
            view.fitWidth = target.width
            view.fitHeight = target.height
            view.relocate(target.minX, target.minY)
            var opacity = current.opacity
            var clipBounds: Bounds = target
            ancestor = text
            var shown = true
            while (ancestor != null && ancestor !== parent) {
                val node = ancestor
                if (!node.isVisible) shown = false
                if (node !== text) opacity *= node.opacity
                node.clip?.let { clip ->
                    val bounds = sceneToLocal(node.localToScene(clip.boundsInParent))
                    val left = Math.max(clipBounds.minX, bounds.minX)
                    val top = Math.max(clipBounds.minY, bounds.minY)
                    val right = Math.min(clipBounds.maxX, bounds.maxX)
                    val bottom = Math.min(clipBounds.maxY, bounds.maxY)
                    clipBounds = BoundingBox(left, top, Math.max(0.0, right - left), Math.max(0.0, bottom - top))
                }
                ancestor = node.parent
            }
            (view.clip as Rectangle).apply {
                x = clipBounds.minX - target.minX
                y = clipBounds.minY - target.minY
                width = clipBounds.width
                height = clipBounds.height
            }
            view.isVisible = shown
            view.opacity = opacity
            // 只有高清图生成成功后才隐藏原字形；Text 仍参与 TextFlow 排版与选区计算。
            text.opacity = 0.0
        }
    }

    fun clear() {
        entries.forEach { (text, entry) -> text.opacity = entry.opacity }
        entries.clear()
        children.clear()
    }

    private fun measureInk(text: Text, opacity: Double): Bounds? {
        return try {
            text.opacity = 1.0
            val bounds = text.boundsInParent
            val left = floor(bounds.minX * MEASUREMENT_SCALE) - 1.0
            val top = floor(bounds.minY * MEASUREMENT_SCALE) - 1.0
            val viewport = Rectangle2D(left, top,
                ceil(bounds.maxX * MEASUREMENT_SCALE) + 1.0 - left,
                ceil(bounds.maxY * MEASUREMENT_SCALE) + 1.0 - top)
            if (viewport.width * viewport.height > MAX_CACHE_PIXELS / 4.0) return null
            val parameters = SnapshotParameters().apply {
                fill = Color.TRANSPARENT
                transform = Scale(MEASUREMENT_SCALE, MEASUREMENT_SCALE)
                this.viewport = viewport
            }
            val ink = inkBounds(text.snapshot(parameters, null)) ?: return null
            text.parentToLocal(BoundingBox((left + ink.minX) / MEASUREMENT_SCALE,
                (top + ink.minY) / MEASUREMENT_SCALE, ink.width / MEASUREMENT_SCALE, ink.height / MEASUREMENT_SCALE))
        } catch (error: RuntimeException) {
            Log.e("Measure markdown emoji ink failed", error)
            null
        } finally {
            text.opacity = opacity
        }
    }

    private data class Key(val fontName: String, val glyph: String, val fill: Paint?, val underline: Boolean,
                           val strikethrough: Boolean)
    private class Raster(val image: Image, val ink: Bounds)

    companion object {
        private const val RASTER_FONT_SIZE = 128.0
        private const val MEASUREMENT_SCALE = 4.0
        private const val MAX_CACHE_PIXELS = 4_194_304.0
        private val cache = LinkedHashMap<Key, Raster?>(128, 0.75f, true)
        private var cachePixels = 0.0

        // 在 FX 线程生成并跨标签共享缓存；使用大字号重新栅格化，不能仅放大小字号快照。
        private fun raster(key: Key): Raster? {
            if (cache.containsKey(key)) return cache[key]
            val result = runCatching {
                val probe = Text(key.glyph).apply {
                    font = Font(key.fontName, RASTER_FONT_SIZE)
                    fill = key.fill ?: Color.BLACK
                    isUnderline = key.underline
                    isStrikethrough = key.strikethrough
                }
                val logical = probe.layoutBounds
                val bounds = probe.boundsInLocal
                val left = floor(bounds.minX) - 1.0
                val top = floor(bounds.minY) - 1.0
                val viewport = Rectangle2D(left, top, ceil(bounds.maxX) + 1.0 - left, ceil(bounds.maxY) + 1.0 - top)
                if (logical.width <= 0.0 || logical.height <= 0.0
                    || viewport.width * viewport.height > MAX_CACHE_PIXELS / 4.0
                ) return@runCatching null
                val parameters = SnapshotParameters().apply {
                    fill = Color.TRANSPARENT
                    this.viewport = viewport
                }
                val image = probe.snapshot(parameters, null)
                val ink = inkBounds(image) ?: return@runCatching null
                Raster(image, ink)
            }.getOrElse {
                Log.e("Render high resolution markdown emoji failed", it)
                null
            }
            cache[key] = result
            if (result != null) cachePixels += result.image.width * result.image.height
            val iterator = cache.entries.iterator()
            while ((cache.size > 128 || cachePixels > MAX_CACHE_PIXELS) && iterator.hasNext()) {
                val raster = iterator.next().value
                if (raster != null) cachePixels -= raster.image.width * raster.image.height
                iterator.remove()
            }
            return result
        }

        private fun inkBounds(image: Image): Bounds? {
            val pixels = image.pixelReader ?: return null
            var left = image.width.toInt()
            var top = image.height.toInt()
            var right = -1
            var bottom = -1
            for (y in 0 until image.height.toInt()) for (x in 0 until image.width.toInt()) {
                if ((pixels.getArgb(x, y) ushr 24) < 8) continue
                if (x < left) left = x
                if (y < top) top = y
                if (x > right) right = x
                if (y > bottom) bottom = y
            }
            return if (right < left || bottom < top) null
            else BoundingBox(left.toDouble(), top.toDouble(), (right - left + 1).toDouble(), (bottom - top + 1).toDouble())
        }
    }
}

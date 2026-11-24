package com.allan.atools.richtext.codearea

import com.allan.atools.GlobalCfgStores
import com.allan.atools.pop.impl.StringChooseCreatorImpl
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownHtmlImage
import com.allan.atools.richtext.codearea.keywordhelper.collectMarkdownHtmlImages
import com.allan.atools.tools.modulenotepad.manager.MarkdownImageEditWindow
import com.allan.atools.tools.modulenotepad.manager.MarkdownImageLocation
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import com.google.gson.reflect.TypeToken
import javafx.geometry.Side
import javafx.scene.Node
import org.commonmark.node.Image
import java.io.File
import java.net.URI

/** 图片导航和显示比例独立于源码；压缩生成新文件并按原版本更新当前图片引用。 */
class MarkdownImageActions(private val area: EditorArea) {
    data class Target(
        val start: Int, val end: Int, val destination: String, val source: String, val version: Long, val document: File?,
        val htmlSourceValue: MarkdownHtmlImage.SourceValue? = null
    )

    companion object {
        private const val ZOOM_KEY = "markdownImageZooms"
        private val ZOOM_CHOICES = listOf(20, 40, 60, 80, 100)
        private val zoomType = object : TypeToken<Map<String, Map<String, Int>>>() {}
    }

    private val zoomListeners = LinkedHashSet<(Set<String>) -> Unit>()
    private val untitledZooms = HashMap<String, Int>()
    private var zoomDocument: File? = null
    private var savedZooms: MutableMap<String, Int>? = null
    private var imageEditWindow: MarkdownImageEditWindow? = null
    private var disposed = false

    fun addZoomListener(listener: (Set<String>) -> Unit) { zoomListeners.add(listener) }
    fun removeZoomListener(listener: (Set<String>) -> Unit) { zoomListeners.remove(listener) }

    fun targets(start: Int, end: Int): List<Target> {
        val state = (area.editor as EditorAreaMgrCode).currentMarkdownSnapshot() ?: return emptyList()
        val version = area.editor.contentVersion
        val document = area.editor.sourceFile
        val targets = ArrayList<Target>()
        for (element in state.elements) {
            val image = element.node as? Image ?: continue
            val first = element.ranges.firstOrNull()?.start ?: continue
            val last = element.ranges.last().end
            if (first < start || last > end) continue
            targets.add(Target(first, last, image.destination, state.text.substring(first, last), version, document))
        }
        for (image in collectMarkdownHtmlImages(state, start, end)) {
            val (first, last) = image.range
            targets.add(Target(first, last, image.destination, state.text.substring(first, last), version, document, image.sourceValue))
        }
        return targets.sortedBy { it.start }
    }

    fun valid(target: Target): Boolean = !disposed && !area.editor.isDestroyed() && !area.markdownComposing &&
        area.editor.contentVersion == target.version && area.editor.sourceFile == target.document &&
        target.start >= 0 && target.end <= area.length && area.getText(target.start, target.end) == target.source

    fun jump(target: Target, after: Boolean) {
        if (!valid(target)) return
        var position = if (after) target.end else target.start
        if (after && area.isEditable) {
            val tail = area.getText(target.end, area.length)
            // 图片之后只剩空白时，退出到独立正文行并保留空白分隔行。
            if (tail.isBlank()) {
                val firstBreak = tail.indexOf('\n')
                val secondBreak = if (firstBreak >= 0) tail.indexOf('\n', firstBreak + 1) else -1
                if (secondBreak >= 0) position = target.end + secondBreak + 1 else {
                    area.undoManager.preventMerge()
                    try { area.insertText(area.length, if (firstBreak >= 0) "\n" else "\n\n") }
                    finally { area.undoManager.preventMerge() }
                    position = area.length
                }
            }
        }
        area.moveTo(position)
        area.requestFocus()
        area.requestFollowCaret()
    }

    fun percentForKey(key: String): Int? = zoomValues()[key]?.takeIf { it in ZOOM_CHOICES }

    private fun zoomValues(): MutableMap<String, Int> {
        val file = area.editor.sourceFile ?: return untitledZooms
        if (zoomDocument != file || savedZooms == null) {
            zoomDocument = file
            savedZooms = LinkedHashMap(GlobalCfgStores.recent().getObject(ZOOM_KEY, zoomType, emptyMap())[file.absolutePath] ?: emptyMap())
        }
        return savedZooms!!
    }

    fun imageKey(destination: String): String =
        MarkdownImageLocation.resolve(area.editor.sourceFile, destination)?.toASCIIString() ?: destination

    fun showZoom(anchor: Node, target: Target, screenX: Double? = null, screenY: Double? = null) {
        if (!valid(target) || anchor.scene == null) return
        val percent = percentForKey(imageKey(target.destination)) ?: 100
        val menu = StringChooseCreatorImpl(ZOOM_CHOICES.map { "$it%" }, ZOOM_CHOICES.indexOf(percent), 120.0)
            .createMenu { index ->
                if (valid(target)) rememberZoom(target.destination, ZOOM_CHOICES[index])
            }
        if (screenX != null && screenY != null) menu.show(anchor, screenX, screenY)
        else menu.show(anchor, Side.BOTTOM, 0.0, 0.0)
    }

    private fun rememberZoom(destination: String, value: Int) {
        val values = zoomValues()
        val key = imageKey(destination)
        if (values[key] == value) return
        values[key] = value
        val changedKeys = linkedSetOf(key)
        val file = area.editor.sourceFile
        if (file != null) {
            val saved = LinkedHashMap(GlobalCfgStores.recent().getObject(ZOOM_KEY, zoomType, emptyMap()))
            while (values.size > 256) {
                val removedKey = values.keys.first()
                values.remove(removedKey)
                changedKeys.add(removedKey)
            }
            saved.remove(file.absolutePath)
            saved[file.absolutePath] = values
            while (saved.size > 128) saved.remove(saved.keys.first())
            GlobalCfgStores.recent().set(ZOOM_KEY, saved)
        }
        zoomListeners.toList().forEach { it(changedKeys) }
    }

    fun editImage(target: Target) {
        if (!valid(target) || !area.isEditable) return
        if (target.htmlSourceValue == null && !target.source.startsWith("![")) return
        val file = localFile(target.destination) ?: return
        imageEditWindow?.close()
        val window = MarkdownImageEditWindow(file,
            { valid(target) && area.isEditable },
            { output -> applyCompressedImage(target, output) },
            { imageEditWindow = null })
        imageEditWindow = window
        window.show()
    }

    private fun applyCompressedImage(target: Target, output: File) {
        if (!valid(target) || !area.isEditable) {
            SnackbarUtils.show(Locales.str("markdown.imageCompressedSourceChanged").replace("{path}", output.absolutePath))
            return
        }
        val base = target.document?.parentFile?.toPath()?.toAbsolutePath()?.normalize()
        val destination = if (base == null) output.toURI().toASCIIString() else {
            try { URI(null, null, base.relativize(output.toPath()).toString().replace('\\', '/'), null).toASCIIString() }
            catch (_: IllegalArgumentException) { output.toURI().toASCIIString() }
        }
        val edits = target.htmlSourceValue?.let { MarkdownAttachments.htmlDestinationEdits(it, destination) }
            ?: compressedSource(target, destination)?.let { listOf(Triple(target.start, target.end, it)) }
        if (edits == null) {
            SnackbarUtils.show(Locales.str("markdown.imageCompressedSourceChanged").replace("{path}", output.absolutePath))
            return
        }
        percentForKey(imageKey(target.destination))?.let { rememberZoom(destination, it) }
        MarkdownAttachments.applySourceEdits(area, edits)
        SnackbarUtils.show(Locales.str("markdown.imageCompressDone"))
    }

    private fun compressedSource(target: Target, destination: String): String? {
        if (!target.source.startsWith("![")) return null
        val escaped = destination.replace("&", "&amp;")
        val range = MarkdownAttachments.destinationRange(target.source, false)
        if (range != null) {
            val address = if (target.source.getOrNull(range.first - 1) == '<') escaped
                else escaped.replace("(", "\\(").replace(")", "\\)")
            return target.source.replaceRange(range, address)
        }
        // 引用式图片只改当前实例，不修改共享定义或其他图片。
        val state = (area.editor as EditorAreaMgrCode).currentMarkdownSnapshot() ?: return null
        val image = state.elements.firstOrNull { it.node is Image && it.ranges.firstOrNull()?.start == target.start &&
            it.ranges.lastOrNull()?.end == target.end }?.node as? Image ?: return null
        val first = image.firstChild?.sourceSpans?.firstOrNull()?.inputIndex
        val last = image.lastChild?.sourceSpans?.lastOrNull()?.let { it.inputIndex + it.length }
        val label = if (first != null && last != null) area.getText(first, last) else ""
        val title = image.title?.takeIf { it.isNotEmpty() }?.let { " \"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }.orEmpty()
        return "![$label](<$escaped>$title)"
    }

    fun localFile(destination: String): File? {
        val uri = MarkdownImageLocation.resolve(area.editor.sourceFile, destination) ?: return null
        return if (uri.scheme.equals("file", true)) runCatching { MarkdownImageLocation.file(uri) }.getOrNull() else null
    }

    fun destroy() {
        disposed = true
        imageEditWindow?.close()
        imageEditWindow = null
        zoomListeners.clear()
    }
}

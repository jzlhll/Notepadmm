package com.allan.atools.richtext.codearea

import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownHtmlImage
import com.allan.atools.richtext.codearea.keywordhelper.collectMarkdownHtmlImages
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.manager.MarkdownHtmlRenderer
import com.allan.atools.tools.modulenotepad.manager.MarkdownImageDecoder
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import javafx.stage.FileChooser
import org.commonmark.node.Image as MarkdownImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO

/** 图片附件后台写入，完成后仅向原版本选区插入路径，避免覆盖后来输入。 */
class MarkdownAttachments(private val area: EditorArea) {
    private class AttachmentSizeException(val fileName: String, val limitBytes: Long) :
        IOException("Image exceeds $limitBytes byte attachment limit")

    companion object {
        private const val MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024

        @JvmStatic
        fun isImage(file: File) = file.isFile && file.extension.lowercase(java.util.Locale.ROOT) in
            setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

        private data class RebasePlan(val text: String, val edits: List<Triple<Int, Int, String>>)

        @JvmStatic
        fun rebaseForSaveAs(area: EditorArea, previous: File?, target: File): String = rebasePlan(area, previous, target).text

        @JvmStatic
        fun applyRebaseForSaveAs(area: EditorArea, previous: File?, target: File) {
            applySourceEdits(area, rebasePlan(area, previous, target).edits)
        }

        fun applySourceEdits(area: EditorArea, edits: List<Triple<Int, Int, String>>) {
            if (edits.isEmpty()) return
            val ordered = edits.sortedBy { it.first }
            fun mapped(position: Int): Int {
                var shift = 0
                for ((start, end, value) in ordered) {
                    if (position < start) break
                    if (position < end) return start + shift + Math.min(position - start, value.length)
                    shift += value.length - (end - start)
                }
                return position + shift
            }
            val anchor = mapped(area.anchor)
            val caret = mapped(area.caretPosition)
            area.undoManager.preventMerge()
            try {
                val changes = area.createMultiChange(ordered.size)
                ordered.asReversed().forEach { (start, end, value) -> changes.replaceTextAbsolutely(start, end, value) }
                changes.commit()
                area.selectRange(anchor, caret)
            } finally {
                area.undoManager.preventMerge()
            }
        }

        private fun rebasePlan(area: EditorArea, previous: File?, target: File): RebasePlan {
            val source = area.text
            if (previous == null || previous.parentFile == target.parentFile || !MarkdownEditorSupport.isMarkdownFile(previous)
                || area.editor.isRealtimeProcessingLimitReached) return RebasePlan(source, emptyList())
            val state = (area.editor as EditorAreaMgrCode).markdownSnapshot(source)
            val replacements = ArrayList<Triple<Int, Int, String>>()
            fun rebased(destination: String): String? {
                if (destination.isBlank() || destination.startsWith('#') || destination.startsWith("//")
                    || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(destination)) return null
                val uri = try { java.net.URI(destination.replace(" ", "%20")) } catch (_: Exception) { return null }
                val decoded = uri.path ?: return null
                if (File(decoded).isAbsolute) return null
                val path = previous.parentFile.toPath().toAbsolutePath().resolve(decoded.replace('\\', '/')).normalize()
                val relative = try { target.parentFile.toPath().toAbsolutePath().relativize(path).toString().replace('\\', '/') }
                    catch (_: IllegalArgumentException) { path.toUri().toASCIIString() }
                val encoded = if (relative.startsWith("file:")) relative else java.net.URI(null, null, relative, null).toASCIIString()
                return encoded + (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
            }
            for (image in collectMarkdownHtmlImages(state, 0, source.length)) {
                val sourceValue = image.sourceValue ?: continue
                val encoded = rebased(image.destination) ?: continue
                replacements.addAll(htmlDestinationEdits(sourceValue, encoded))
            }
            for (entry in state.elements) {
                val node = entry.node
                val destination = when (node) {
                    is MarkdownImage -> node.destination
                    is org.commonmark.node.Link -> node.destination
                    is org.commonmark.node.LinkReferenceDefinition -> node.destination
                    else -> continue
                }
                val encoded = rebased(destination) ?: continue
                val first = node.sourceSpans.firstOrNull() ?: continue
                val last = node.sourceSpans.last()
                val raw = state.text.substring(first.inputIndex, last.inputIndex + last.length)
                val reference = node is org.commonmark.node.LinkReferenceDefinition
                val range = destinationRange(raw, reference) ?: continue
                val original = raw.substring(range.first, range.last + 1)
                val unescaped = org.jsoup.parser.Parser.unescapeEntities(
                    Regex("\\\\([!\"#$%&'()*+,\\-./:;<=>?@\\[\\]\\^_`{|}~])").replace(original) { it.groupValues[1] }, false)
                if (unescaped != destination) continue
                // URI 中合法的括号、实体前缀仍可能改变 Markdown 目的地址的边界。
                val markdownDestination = encoded.replace("&", "&amp;").replace("\\", "\\\\")
                    .let { if (raw.getOrNull(range.first - 1) == '<') it else it.replace("(", "\\(").replace(")", "\\)") }
                replacements.add(Triple(first.inputIndex + range.first, first.inputIndex + range.last + 1, markdownDestination))
            }
            if (replacements.isEmpty()) return RebasePlan(source, emptyList())
            val edits = replacements.distinctBy { it.first }
            val value = StringBuilder(state.text)
            edits.sortedByDescending { it.first }.forEach { (start, end, replacement) -> value.replace(start, end, replacement) }
            return RebasePlan(value.toString(), edits)
        }

        fun htmlDestinationEdits(source: MarkdownHtmlImage.SourceValue, destination: String): List<Triple<Int, Int, String>> {
            val escaped = MarkdownHtmlRenderer.escape(destination)
            val value = when (source.quote) {
                '"' -> escaped
                '\'' -> escaped.replace("'", "&#39;")
                else -> escaped.replace("'", "&#39;").replace("=", "&#61;")
            }
            return source.segments.mapIndexed { index, (range, fillsLine) ->
                val replacement = when {
                    index == 0 -> value
                    // Tab 会被 URL 解析忽略，同时让 CommonMark 保持 HTML 标签跨行连续。
                    fillsLine -> "&#9;"
                    else -> ""
                }
                Triple(range.start, range.end, replacement)
            }
        }

        /** 定位原始图片／链接中的地址，供迁移与图片转换保留其余源码格式。 */
        fun destinationRange(raw: String, definition: Boolean): IntRange? {
            var index = 0
            while (raw.getOrNull(index)?.isWhitespace() == true) index++
            if (raw.getOrNull(index) == '!') index++
            if (raw.getOrNull(index) != '[') return null
            var brackets = 0
            while (index < raw.length) {
                val character = raw[index++]
                if (character == '\\') { index++; continue }
                if (character == '[') brackets++
                if (character == ']' && --brackets == 0) break
            }
            val separator = if (definition) ':' else '('
            if (raw.getOrNull(index++) != separator) return null
            while (raw.getOrNull(index)?.isWhitespace() == true) index++
            if (raw.getOrNull(index) == '<') {
                val start = ++index
                while (index < raw.length) {
                    if (raw[index] == '\\') { index += 2; continue }
                    if (raw[index] == '>') return if (index > start) start until index else null
                    index++
                }
                return null
            }
            val start = index
            var parentheses = 0
            while (index < raw.length) {
                val character = raw[index]
                if (character == '\\') { index += 2; continue }
                if (character.isWhitespace() || (character == ')' && parentheses == 0)) break
                if (character == '(') parentheses++
                if (character == ')') parentheses--
                index++
            }
            return if (index > start) start until index else null
        }
    }

    fun chooseImage() {
        val chooser = FileChooser()
        chooser.title = Locales.str("markdown.insertImage")
        chooser.extensionFilters.add(FileChooser.ExtensionFilter(Locales.str("markdown.imageFiles"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp", "*.svg"))
        chooser.showOpenMultipleDialog(UIContext.mainWindow)?.let { importFiles(it) }
    }

    @JvmOverloads
    fun importFiles(files: List<File>, insertion: java.util.function.Consumer<String>? = null) = import(files.filter(::isImage), null, insertion)
    @JvmOverloads
    fun importClipboardImage(image: Image, insertion: java.util.function.Consumer<String>? = null) = import(emptyList(), image, insertion)

    private fun import(files: List<File>, image: Image?, insertion: java.util.function.Consumer<String>?) {
        if (!area.isEditable || area.markdownComposing || (files.isEmpty() && image == null)) return
        val document = area.editor.sourceFile
        if (document == null) { SnackbarUtils.show(Locales.str("markdown.saveBeforeImage")); return }
        val version = area.editor.contentVersion
        val start = area.selection.start
        val end = area.selection.end
        val folder = File(document.parentFile, document.nameWithoutExtension + ".assets")
        ThreadUtils.execute {
            val created = ArrayList<File>()
            try {
                Files.createDirectories(folder.toPath())
                val labels = ArrayList<Pair<String, String>>()
                for (file in files) {
                    val source = file.toPath()
                    val size = Files.size(source)
                    if (size > MAX_ATTACHMENT_BYTES) throw AttachmentSizeException(file.name, MAX_ATTACHMENT_BYTES)
                    val svgLimit = MarkdownImageDecoder.MAX_SVG_BYTES.toLong()
                    if (size > svgLimit && !MarkdownImageDecoder.hasRasterReader(file)) {
                        throw AttachmentSizeException(file.name, svgLimit)
                    }
                    val target = File(folder, UUID.randomUUID().toString() + "." + file.extension.lowercase(java.util.Locale.ROOT))
                    Files.copy(source, target.toPath())
                    created.add(target)
                    labels.add(file.nameWithoutExtension to target.name)
                }
                if (image != null) {
                    if (image.width * image.height > 40_000_000) throw java.io.IOException("Clipboard image exceeds pixel limit")
                    val target = File(folder, UUID.randomUUID().toString() + ".png")
                    if (!ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", target)) throw java.io.IOException("No PNG writer")
                    created.add(target)
                    labels.add("image" to target.name)
                }
                val markdown = labels.joinToString("\n") { (label, name) ->
                    val path = java.net.URI(null, null, folder.name + "/" + name, null).toASCIIString()
                    "![${label.replace("[", "\\[").replace("]", "\\]")}](<$path>)"
                }
                Platform.runLater {
                    if (!area.editor.isDestroyed() && area.isEditable && !area.markdownComposing
                        && area.editor.contentVersion == version && area.editor.sourceFile == document) {
                        area.undoManager.preventMerge()
                        if (insertion != null) insertion.accept(markdown)
                        else {
                            area.replaceText(start, end, markdown)
                            area.moveTo(start + markdown.length)
                        }
                        area.undoManager.preventMerge()
                        area.requestFollowCaret()
                    } else {
                        // 版本已变化时不猜测插入点，附件路径仍可从目录中取用。
                        SnackbarUtils.show(Locales.str("markdown.imageInsertChanged"))
                    }
                }
            } catch (error: Exception) {
                created.forEach { try { Files.deleteIfExists(it.toPath()) } catch (_: Exception) { } }
                Log.e("Import markdown image failed", error)
                Platform.runLater {
                    val message = if (error is AttachmentSizeException) {
                        Locales.str("markdown.imageInsertTooLarge")
                            .replace("{limit}", (error.limitBytes / (1024 * 1024)).toString())
                            .replace("{name}", error.fileName)
                    } else Locales.str("markdown.imageInsertFailed")
                    SnackbarUtils.show(message)
                }
            }
        }
    }

}

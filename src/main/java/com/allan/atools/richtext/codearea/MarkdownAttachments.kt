package com.allan.atools.richtext.codearea

import com.allan.atools.UIContext
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.manager.MarkdownHtmlRenderer
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import javafx.stage.FileChooser
import org.commonmark.node.Image as MarkdownImage
import java.io.File
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO

/** 图片附件后台写入，完成后仅向原版本选区插入路径，避免覆盖后来输入。 */
class MarkdownAttachments(private val area: EditorArea) {
    companion object {
        @JvmStatic
        fun isImage(file: File) = file.isFile && file.extension.lowercase(java.util.Locale.ROOT) in
            setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

        private data class RebasePlan(val text: String, val edits: List<Triple<Int, Int, String>>)

        @JvmStatic
        fun rebaseForSaveAs(area: EditorArea, previous: File?, target: File): String = rebasePlan(area, previous, target).text

        @JvmStatic
        fun applyRebaseForSaveAs(area: EditorArea, previous: File?, target: File) {
            val plan = rebasePlan(area, previous, target)
            if (plan.edits.isEmpty()) return
            fun mapped(position: Int): Int {
                var shift = 0
                for ((start, end, value) in plan.edits.sortedBy { it.first }) {
                    if (position < start) break
                    if (position <= end) return start + shift + Math.min(position - start, value.length)
                    shift += value.length - (end - start)
                }
                return position + shift
            }
            val anchor = mapped(area.anchor)
            val caret = mapped(area.caretPosition)
            area.undoManager.preventMerge()
            val changes = area.createMultiChange(plan.edits.size)
            plan.edits.sortedByDescending { it.first }.forEach { (start, end, value) -> changes.replaceTextAbsolutely(start, end, value) }
            changes.commit()
            area.selectRange(anchor, caret)
            area.undoManager.preventMerge()
        }

        private fun rebasePlan(area: EditorArea, previous: File?, target: File): RebasePlan {
            val source = area.text
            if (previous == null || previous.parentFile == target.parentFile || !MarkdownEditorSupport.isMarkdownFile(previous)
                || area.editor.isRealtimeProcessingLimitReached) return RebasePlan(source, emptyList())
            val state = (area.editor as EditorAreaMgrCode).markdownSnapshot(area.text)
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
            for (entry in state.elements) {
                val node = entry.node
                val first = node.sourceSpans.firstOrNull() ?: continue
                val last = node.sourceSpans.last()
                val raw = state.text.substring(first.inputIndex, last.inputIndex + last.length)
                if (node is org.commonmark.node.HtmlInline || node is org.commonmark.node.HtmlBlock) {
                    // 只迁移 img 的 src；其他 HTML 内容与属性保持原样。
                    val tags = Regex("(?is)<!--.*?-->|<img\\b[^>]*>")
                    val attributes = Regex("(?i)\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
                    for (tag in tags.findAll(raw)) {
                        if (tag.value.startsWith("<!--")) continue
                        val match = attributes.find(tag.value) ?: continue
                        val group = (1..3).mapNotNull { match.groups[it] }.firstOrNull() ?: continue
                        val destination = org.jsoup.parser.Parser.unescapeEntities(group.value, true)
                        val encoded = rebased(destination) ?: continue
                        val start = first.inputIndex + tag.range.first + group.range.first
                        replacements.add(Triple(start, start + group.value.length, MarkdownHtmlRenderer.escape(encoded)))
                    }
                    continue
                }
                val destination = when (node) {
                    is MarkdownImage -> node.destination
                    is org.commonmark.node.Link -> node.destination
                    is org.commonmark.node.LinkReferenceDefinition -> node.destination
                    else -> continue
                }
                val encoded = rebased(destination) ?: continue
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
            val original = state.text
            val value = StringBuilder(original)
            replacements.distinctBy { it.first }.sortedByDescending { it.first }.forEach { value.replace(it.first, it.second, it.third) }
            return RebasePlan(value.toString(), replacements.distinctBy { it.first })
        }
        private fun destinationRange(raw: String, definition: Boolean): IntRange? {
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
                    if (file.length() > 50L * 1024 * 1024) throw java.io.IOException("Image exceeds attachment limit")
                    val target = File(folder, UUID.randomUUID().toString() + "." + file.extension.lowercase(java.util.Locale.ROOT))
                    Files.copy(file.toPath(), target.toPath())
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
                    if (!area.editor.isDestroyed && area.isEditable && !area.markdownComposing
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
                Platform.runLater { SnackbarUtils.show(Locales.str("markdown.imageInsertFailed")) }
            }
        }
    }

    fun resizeImage() {
        if (!area.isEditable || area.markdownComposing || area.editor.isRealtimeProcessingLimitReached) return
        val state = (area.editor as EditorAreaMgrCode).markdownSnapshot(area.text)
        val entry = state.elements.firstOrNull { it.node is MarkdownImage && it.ranges.any { range -> area.caretPosition in range.start until range.end } }
        var from: Int
        var to: Int
        var destination: String
        var alt: String
        var title: String
        var initialWidth = "640"
        val image = entry?.node as? MarkdownImage
        if (image != null) {
            from = image.sourceSpans.first().inputIndex
            to = image.sourceSpans.last().let { it.inputIndex + it.length }
            destination = image.destination
            alt = com.allan.atools.richtext.codearea.keywordhelper.MarkdownPlainText.render(image)
            title = image.title.orEmpty()
        } else {
            val html = state.elements.firstOrNull { (it.node is org.commonmark.node.HtmlInline || it.node is org.commonmark.node.HtmlBlock)
                && it.ranges.any { range -> area.caretPosition in range.start until range.end } } ?: return
            val start = html.node.sourceSpans.first().inputIndex
            val end = html.node.sourceSpans.last().let { it.inputIndex + it.length }
            val tag = Regex("(?is)<!--.*?-->|<img\\b[^>]*>").findAll(state.text.substring(start, end))
                .firstOrNull { !it.value.startsWith("<!--") && area.caretPosition in (start + it.range.first)..(start + it.range.last) } ?: return
            val parsed = org.jsoup.Jsoup.parseBodyFragment(tag.value).selectFirst("img[src]") ?: return
            from = start + tag.range.first
            to = start + tag.range.last + 1
            destination = parsed.attr("src")
            alt = parsed.attr("alt")
            title = parsed.attr("title")
            initialWidth = parsed.attr("width").ifEmpty { "640" }
        }
        val dialog = javafx.scene.control.TextInputDialog(initialWidth)
        dialog.title = Locales.str("markdown.imageWidth")
        dialog.headerText = Locales.str("markdown.imageWidth")
        val width = dialog.showAndWait().orElse(null)?.toIntOrNull() ?: return
        if (width !in 1..10000 || state.text != area.text || !area.isEditable || area.markdownComposing) return
        val titleAttribute = if (title.isEmpty()) "" else " title=\"${MarkdownHtmlRenderer.escape(title)}\""
        val result = "<img src=\"${MarkdownHtmlRenderer.escape(destination)}\" alt=\"${MarkdownHtmlRenderer.escape(alt)}\" width=\"$width\"$titleAttribute>"
        area.undoManager.preventMerge()
        area.replaceText(from, to, result)
        area.moveTo(from + result.length)
        area.undoManager.preventMerge()
    }

}

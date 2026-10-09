package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.MarkdownThemes
import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.log.LogMemoryBudget
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.google.gson.Gson
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.SwingFXUtils
import javafx.scene.SnapshotParameters
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ChoiceDialog
import javafx.scene.control.ScrollPane
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.scene.transform.Scale
import javafx.scene.web.WebView
import javafx.stage.FileChooser
import netscape.javascript.JSObject
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** 独立页面复用预览排版，以高分辨率图像分页输出；临时文件完成后才替换目标。 */
object MarkdownPdfExport {
    private const val MARGIN = 42f
    private const val CSS_TO_POINT = 0.75
    private const val SCALE = 2.0
    private val gson = Gson()
    private var current: ExportTask? = null
    private val style by lazy { MarkdownPdfExport::class.java.getResource("/markdown/pdf.css")!!.readText() }
    private val script by lazy { MarkdownPdfExport::class.java.getResource("/markdown/pdf.js")!!.readText() }

    @JvmStatic
    fun export(area: EditorArea) {
        current?.let { it.progress.toFront(); return }
        if (area.editor.isRealtimeProcessingLimitReached) {
            SnackbarUtils.show(Locales.str("markdown.previewLimit")); return
        }
        val portrait = Locales.str("markdown.pdfPortrait")
        val landscape = Locales.str("markdown.pdfLandscape")
        val dialog = ChoiceDialog(portrait, listOf(portrait, landscape)).apply {
            initOwner(UIContext.mainWindow)
            title = Locales.str("markdown.exportPdf")
            headerText = Locales.str("markdown.pdfPageLayout") + "\n" + Locales.str("markdown.pdfImageHint")
            contentText = Locales.str("markdown.pdfOrientation")
        }
        val orientation = dialog.showAndWait().orElse(null) ?: return
        val chooser = FileChooser().apply {
            title = Locales.str("markdown.exportPdf")
            initialFileName = area.editor.documentState.displayName.substringBeforeLast('.') + ".pdf"
            extensionFilters.add(FileChooser.ExtensionFilter("PDF", "*.pdf"))
        }
        val chosen = chooser.showSaveDialog(UIContext.mainWindow) ?: return
        val destination = if (chosen.name.endsWith(".pdf", true)) chosen else File(chosen.path + ".pdf")
        if (destination != chosen && destination.exists()) {
            val replace = Alert(Alert.AlertType.CONFIRMATION, Locales.str("document.replaceOutput"), ButtonType.OK, ButtonType.CANCEL)
            replace.initOwner(UIContext.mainWindow)
            replace.headerText = destination.name
            if (replace.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return
        }
        // 文件对话框运行嵌套事件循环，快照在用户确认输出路径后获取。
        if (area.editor.isDestroyed || area.editor.isRealtimeProcessingLimitReached) {
            SnackbarUtils.show(Locales.str("markdown.previewLimit")); return
        }
        val paper = if (orientation == landscape) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
        val source = area.text
        val name = area.editor.documentState.displayName
        val file = area.editor.sourceFile
        val base = file?.parentFile?.toURI()?.toASCIIString()
        val theme = MarkdownThemes.previewCss(false)
        val task = ExportTask(destination.toPath(), paper, name, file)
        current = task
        task.start {
            val state = (area.editor as EditorAreaMgrCode).markdownSnapshot(source)
            MarkdownPreviewWindow.renderDocument(state, base, false, theme, style, script)
        }
    }

    private class ExportTask(private val destination: Path, private val paper: PDRectangle, private val documentName: String,
                             private val sourceFile: File?) {
        private val width = Math.floor((paper.width - MARGIN * 2) / CSS_TO_POINT)
        private val height = Math.floor((paper.height - MARGIN * 2) / CSS_TO_POINT)
        private val web = WebView().apply {
            minWidth = width; prefWidth = width; maxWidth = width
            minHeight = height; prefHeight = height; maxHeight = height
            isContextMenuEnabled = false
            isMouseTransparent = true
            engine.setCreatePopupHandler { null }
        }
        private val pagesReady = CompletableFuture<DoubleArray>()
        private val bridge = Bridge()
        @Volatile private var cancelled = false
        private var finished = false
        private var prepared = false
        @Volatile private var worker: Thread? = null
        val progress = DocumentOutputProgress(Locales.str("markdown.exportPdf"), ::cancel, ScrollPane(web))

        init {
            web.engine.loadWorker.stateProperty().addListener { _, _, state ->
                if (cancelled || finished) return@addListener
                try {
                    if (state == Worker.State.SUCCEEDED && !prepared) {
                        prepared = true
                        @Suppress("DEPRECATION")
                        val window = web.engine.executeScript("window") as JSObject
                        window.setMember("pdfBridge", bridge)
                        web.engine.executeScript("preparePdf($width,$height)")
                    } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                        pagesReady.completeExceptionally(IllegalStateException("PDF page load failed"))
                    }
                } catch (error: Exception) { pagesReady.completeExceptionally(error) }
            }
        }

        private fun cancel() {
            if (cancelled) return
            cancelled = true
            worker?.interrupt()
            pagesReady.cancel(true)
        }

        fun start(render: () -> String) {
            ThreadUtils.execute {
                worker = Thread.currentThread()
                var temporary: Path? = null
                var images: MarkdownPdfImages? = null
                try {
                    checkCancelled()
                    LogMemoryBudget.reserve(96 * LogMemoryBudget.MIB).use {
                        val assets = MarkdownPdfImages(sourceFile, width, height)
                        images = assets
                        val html = assets.prepare(render())
                        checkCancelled()
                        Platform.runLater { if (!cancelled) web.engine.loadContent(html) }
                        val cuts = pagesReady.get(120, TimeUnit.SECONDS)
                        val output = Files.createTempFile(destination.toAbsolutePath().parent, ".atools-pdf-", ".tmp")
                        temporary = output
                        PDDocument(IOUtils.createTempFileOnlyStreamCache()).use { document ->
                            document.documentInformation.title = documentName
                            document.documentInformation.creator = "ATools"
                            val numberFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
                            for (page in 0 until cuts.size - 1) {
                                checkCancelled()
                                val image = capture(page, cuts.size - 1, cuts[page]).get(30, TimeUnit.SECONDS)
                                checkCancelled()
                                val pixels = SwingFXUtils.fromFXImage(image, null)
                                val rows = Math.min(pixels.height, Math.ceil((cuts[page + 1] - cuts[page]) * SCALE).toInt())
                                val clipped = pixels.getSubimage(0, 0, pixels.width, Math.max(1, rows))
                                val picture = LosslessFactory.createFromImage(document, clipped)
                                val sheet = PDPage(paper)
                                document.addPage(sheet)
                                PDPageContentStream(document, sheet).use { content ->
                                    val pageHeight = (clipped.height / SCALE * CSS_TO_POINT).toFloat()
                                    content.drawImage(picture, MARGIN, paper.height - MARGIN - pageHeight,
                                        (width * CSS_TO_POINT).toFloat(), pageHeight)
                                    val label = "${page + 1} / ${cuts.size - 1}"
                                    content.beginText()
                                    content.setFont(numberFont, 9f)
                                    content.newLineAtOffset((paper.width - numberFont.getStringWidth(label) / 1000 * 9) / 2, 20f)
                                    content.showText(label)
                                    content.endText()
                                }
                            }
                            checkCancelled()
                            document.save(output.toFile())
                        }
                        checkCancelled()
                        try {
                            Files.move(output, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        } catch (_: AtomicMoveNotSupportedException) {
                            Files.move(output, destination, StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                    Platform.runLater { if (!ThreadUtils.sBeClosing) SnackbarUtils.show(Locales.str("markdown.exportDone")) }
                } catch (_: CancellationException) {
                    // 用户取消时仅清理本次输出，不改变目标文件。
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    if (!cancelled) Log.e("Export markdown PDF failed", error)
                    Platform.runLater { if (!cancelled && !ThreadUtils.sBeClosing) SnackbarUtils.show(Locales.str("markdown.pdfExportFailed")) }
                } finally {
                    worker = null
                    images?.close()
                    try { temporary?.let { Files.deleteIfExists(it) } }
                    catch (error: Exception) { Log.e("Clean PDF temporary file failed", error) }
                    Platform.runLater {
                        finished = true
                        web.engine.load(null)
                        progress.close()
                        if (current === this) current = null
                    }
                }
            }
        }

        private fun checkCancelled() {
            if (cancelled || ThreadUtils.sBeClosing || Thread.currentThread().isInterrupted) throw CancellationException()
        }

        private fun capture(page: Int, total: Int, top: Double): CompletableFuture<Image> {
            val result = CompletableFuture<Image>()
            Platform.runLater {
                try {
                    checkCancelled()
                    progress.showPage(page + 1, total)
                    web.engine.executeScript("showPdfPage($top)")
                    web.resize(width, height)
                    web.applyCss()
                    web.layout()
                    val parameters = SnapshotParameters().apply { fill = Color.WHITE; transform = Scale(SCALE, SCALE) }
                    web.snapshot({ snapshot ->
                        if (cancelled) result.completeExceptionally(CancellationException()) else result.complete(snapshot.image)
                        null
                    }, parameters, null)
                } catch (error: Exception) { result.completeExceptionally(error) }
            }
            return result
        }

        /** 固定分页脚本回传页边界，不向文档提供编辑器或文件操作。 */
        inner class Bridge {
            fun ready(value: String) {
                try {
                    val cuts = gson.fromJson(value, DoubleArray::class.java)
                    require(cuts.size in 2..1001 && cuts[0] == 0.0)
                    require(cuts.all { it.isFinite() } && cuts.asList().zipWithNext().all { (start, end) -> end > start && end - start <= height })
                    pagesReady.complete(cuts)
                } catch (error: Exception) { pagesReady.completeExceptionally(error) }
            }
            fun failed(message: String) { pagesReady.completeExceptionally(IllegalStateException(message)) }
        }
    }
}

package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import javafx.application.Platform
import javafx.print.PageLayout
import javafx.print.PrinterJob
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import java.io.Reader
import java.io.StringReader
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** 普通文本按打印机纸张分页，分块文件以已编辑正文和磁盘尾部的固定快照打印。 */
object TextPrintSupport {
    private var current: PrintTask? = null

    @JvmStatic
    fun print(area: EditorArea) {
        current?.let { it.progress.toFront(); return }
        var job: PrinterJob? = null
        var task: PrintTask? = null
        try {
            val printer = PrinterJob.createPrinterJob()
            if (printer == null) { SnackbarUtils.show(Locales.str("document.noPrinter")); return }
            job = printer
            printer.jobSettings.jobName = area.editor.documentState.displayName
            if (!printer.showPrintDialog(UIContext.mainWindow)) { printer.cancelJob(); return }
            if (area.editor.isDestroyed || ThreadUtils.sBeClosing) { printer.cancelJob(); return }
            val prefix = area.text
            val snapshot = area.largeLog?.snapshot(prefix)
            val family = area.lookupAll(".text").filterIsInstance<Text>().firstOrNull()?.font?.family ?: "Monospaced"
            val printing = PrintTask(printer, Font.font(family, 10.0))
            task = printing
            current = printing
            printing.start { if (snapshot == null) StringReader(prefix) else snapshot.openReader { printing.cancelled } }
        } catch (error: Exception) {
            job?.cancelJob()
            task?.progress?.close()
            if (current === task) current = null
            Log.e("Prepare text printing failed", error)
            if (!ThreadUtils.sBeClosing) SnackbarUtils.show(Locales.str("document.printFailed"))
        }
    }

    private class PrintTask(private val job: PrinterJob, private val font: Font) {
        @Volatile var cancelled = false
            private set
        @Volatile private var worker: Thread? = null
        val progress = DocumentOutputProgress(Locales.str("document.print"), ::cancel)

        private fun cancel() {
            if (cancelled) return
            cancelled = true
            worker?.interrupt()
            ThreadUtils.execute { job.cancelJob() }
        }

        fun start(open: () -> Reader) {
            val layout = job.jobSettings.pageLayout
            val ranges = job.jobSettings.pageRanges
            val lastPage = ranges?.fold(0) { last, range -> Math.max(last, range.endPage) } ?: 0
            // 页码由本任务筛选，清除驱动端范围，避免把已筛选页面再次按序号过滤。
            job.jobSettings.setPageRanges()
            ThreadUtils.execute {
                worker = Thread.currentThread()
                try {
                    if (cancelled) throw CancellationException()
                    open().use { reader ->
                        val pending = StringBuilder()
                        val buffer = CharArray(4096)
                        var ended = false
                        var page = 1
                        var printed = false
                        while (!ended || pending.isNotEmpty() || !printed) {
                            if (cancelled || Thread.currentThread().isInterrupted) throw CancellationException()
                            while (!ended && pending.length < 16_384) {
                                val count = reader.read(buffer)
                                if (count < 0) ended = true
                                else pending.append(buffer, 0, count)
                            }
                            val value = pending.toString()
                            val ready = CompletableFuture<Pair<TextFlow, Int>>()
                            Platform.runLater {
                                try {
                                    if (cancelled) throw CancellationException()
                                    progress.showPage(page)
                                    ready.complete(paginate(value, layout))
                                } catch (error: Exception) { ready.completeExceptionally(error) }
                            }
                            val (node, consumed) = ready.get(30, TimeUnit.SECONDS)
                            if (ranges == null || ranges.isEmpty() || ranges.any { page in it.startPage..it.endPage }) {
                                if (!job.printPage(layout, node)) {
                                    if (cancelled || job.jobStatus == PrinterJob.JobStatus.CANCELED) throw CancellationException()
                                    error("Printer rejected page")
                                }
                                printed = true
                            }
                            pending.delete(0, consumed)
                            if (ended && pending.isEmpty()) break
                            if (lastPage > 0 && page >= lastPage) break
                            page++
                        }
                        if (!printed) error("No pages in selected range")
                    }
                    if (cancelled || job.jobStatus == PrinterJob.JobStatus.CANCELED) throw CancellationException()
                    check(job.endJob()) { "Could not spool print job" }
                    Platform.runLater { if (!ThreadUtils.sBeClosing) SnackbarUtils.show(Locales.str("document.printSubmitted")) }
                } catch (_: CancellationException) {
                    job.cancelJob()
                } catch (_: InterruptedException) {
                    job.cancelJob()
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    job.cancelJob()
                    if (!cancelled) Log.e("Print text failed", error)
                    Platform.runLater { if (!cancelled && !ThreadUtils.sBeClosing) SnackbarUtils.show(Locales.str("document.printFailed")) }
                } finally {
                    worker = null
                    Platform.runLater { progress.close(); if (current === this) current = null }
                }
            }
        }

        private fun paginate(source: String, layout: PageLayout): Pair<TextFlow, Int> {
            val text = Text().apply { font = this@PrintTask.font; fill = Color.BLACK }
            val flow = TextFlow(text).apply { lineSpacing = 2.0; prefWidth = layout.printableWidth }
            fun height(count: Int): Double {
                text.text = source.substring(0, count).replace("\t", "    ")
                return flow.prefHeight(layout.printableWidth)
            }
            var low = 0
            var high = source.length
            while (low < high) {
                val middle = (low + high + 1) / 2
                if (height(middle) <= layout.printableHeight) low = middle else high = middle - 1
            }
            if (source.isNotEmpty() && low == 0) error("Printable area is too small")
            if (low < source.length && low > 0 && Character.isHighSurrogate(source[low - 1])) low--
            if (low < source.length && low > 0 && source[low - 1] == '\r' && source[low] == '\n') low--
            if (source.isNotEmpty() && low == 0) error("Printable area is too small")
            val measured = height(low)
            flow.resize(layout.printableWidth, measured)
            flow.layout()
            return flow to low
        }
    }
}

package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.UIContext
import com.allan.atools.ui.SegmentedControl
import com.allan.atools.ui.createSecondaryScene
import com.allan.atools.ui.withAppTheme
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.allan.uilibs.image.ImageCompressionHelper
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.binding.DoubleBinding
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Screen
import javafx.stage.Stage
import java.io.File
import java.util.Locale
import java.util.concurrent.Future
import kotlin.math.roundToInt

/** 大窗口对比原图与内存预览，串行合并参数刷新，只在保存时发布用户确认的结果。 */
class MarkdownImageEditWindow(
    private val file: File,
    private val canSave: () -> Boolean,
    private val saved: (File) -> Unit,
    private val closed: () -> Unit
) {
    private data class Preview(val options: ImageCompressionHelper.Options, val encoded: ImageCompressionHelper.EncodedImage)
    private val stage = Stage()
    private val qualities = listOf(60, 65, 70, 75, 80, 85, 90)
    private val quality = SegmentedControl(qualities.map(Int::toString))
    private val scale = Slider(20.0, 100.0, 100.0)
    private val dimensions = Label()
    private val status = Label(Locales.str("markdown.imageEditLoading"))
    private val previousQuality = Label()
    private val save = Button(Locales.str("save"))
    private val cancel = Button(Locales.str("close"))
    private val original = ImagePane(Locales.str("markdown.imageEditOriginal"))
    private val processed = ImagePane(Locales.str("markdown.imageEditProcessed"))
    private val scheduler = LatestRefreshScheduler(120, 350, ::startPreview)
    private var source: MarkdownImageCompression? = null
    private var preview: Preview? = null
    private var loadTask: Future<*>? = null
    private var renderTask: Future<*>? = null
    private var saveTask: Future<*>? = null
    private var saving = false
    private var disposed = false

    init {
        val owner = UIContext.mainWindow
        stage.initOwner(owner)
        stage.initModality(Modality.WINDOW_MODAL)
        stage.title = Locales.str("markdown.imageEditTitle")
        quality.select(qualities.indexOf(85))
        quality.isDisable = true
        quality.selectedIndexProperty().addListener { _, _, _ -> requestPreview() }
        scale.apply {
            majorTickUnit = 20.0
            minorTickCount = 19
            blockIncrement = 1.0
            isShowTickLabels = false
            isShowTickMarks = false
            isSnapToTicks = true
            prefWidth = 300.0
            minWidth = 160.0
            isDisable = true
            valueProperty().addListener { _, _, _ -> requestPreview() }
        }
        save.isDisable = true
        save.styleClass.addAll("markdown-image-edit-action", "markdown-image-edit-save")
        save.setOnAction { savePreview() }
        cancel.styleClass.addAll("markdown-image-edit-action", "markdown-image-edit-close")
        cancel.setOnAction { close() }
        val settings = GridPane().apply {
            hgap = 12.0
            vgap = 12.0
            alignment = Pos.CENTER_LEFT
            add(Label(Locales.str("markdown.imageEditQuality")), 0, 0)
            add(quality, 1, 0)
            add(Label(Locales.str("markdown.imageEditScale")), 0, 1)
            add(HBox(12.0, scale, dimensions).apply { alignment = Pos.CENTER_LEFT }, 1, 1)
        }
        val path = Label(file.absolutePath).apply { isWrapText = true; styleClass.add("markdown-image-edit-path") }
        previousQuality.isManaged = false
        previousQuality.isVisible = false
        val controls = VBox(12.0, path, settings, previousQuality).apply { padding = Insets(16.0) }
        val comparisonScale = Bindings.createDoubleBinding({
            val width = Math.max(original.view.image?.width ?: 1.0, processed.view.image?.width ?: 1.0)
            val height = Math.max(original.view.image?.height ?: 1.0, processed.view.image?.height ?: 1.0)
            val availableWidth = Math.max(1.0, Math.min(original.scroll.viewportBounds.width, processed.scroll.viewportBounds.width) - 20.0)
            val availableHeight = Math.max(1.0, Math.min(original.scroll.viewportBounds.height, processed.scroll.viewportBounds.height) - 20.0)
            Math.min(1.0, Math.min(availableWidth / width, availableHeight / height))
        }, original.view.imageProperty(), processed.view.imageProperty(),
            original.scroll.viewportBoundsProperty(), processed.scroll.viewportBoundsProperty())
        original.bindScale(comparisonScale)
        processed.bindScale(comparisonScale)
        original.scroll.hvalueProperty().bindBidirectional(processed.scroll.hvalueProperty())
        original.scroll.vvalueProperty().bindBidirectional(processed.scroll.vvalueProperty())
        val images = HBox(16.0, original, processed).apply {
            padding = Insets(0.0, 16.0, 0.0, 16.0)
            HBox.setHgrow(original, Priority.ALWAYS)
            HBox.setHgrow(processed, Priority.ALWAYS)
        }
        status.isWrapText = true
        status.maxWidth = Double.MAX_VALUE
        val footer = HBox(12.0, status, cancel, save).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(12.0, 16.0, 16.0, 16.0)
            styleClass.add("markdown-image-edit-footer")
            HBox.setHgrow(status, Priority.ALWAYS)
        }
        val content = BorderPane(images, controls, null, footer, null).apply { styleClass.add("markdown-image-edit") }
        val screen = if (owner == null) Screen.getPrimary() else Screen.getScreensForRectangle(owner.x, owner.y, owner.width, owner.height)
            .firstOrNull() ?: Screen.getPrimary()
        val bounds = screen.visualBounds
        val width = Math.min(1440.0, bounds.width * 0.94)
        val height = Math.min(980.0, bounds.height * 0.92)
        stage.scene = createSecondaryScene(stage, content, width - 4.0, height - 40.0, stage.title).apply {
            stylesheets.add(ResLocation.getURL("css", "markdown_image_edit.css").toExternalForm())
            addEventFilter(KeyEvent.KEY_PRESSED) {
                if (it.code == KeyCode.ESCAPE && !saving) { close(); it.consume() }
            }
        }
        stage.minWidth = Math.min(900.0, width)
        stage.minHeight = Math.min(600.0, height)
        stage.x = bounds.minX + (bounds.width - width) / 2
        stage.y = bounds.minY + (bounds.height - height) / 2
        stage.setOnCloseRequest { if (saving) it.consume() }
        stage.setOnHidden { dispose() }
    }

    fun show() {
        if (disposed) return
        if (stage.isShowing) { stage.toFront(); return }
        stage.show()
        stage.toFront()
        loadTask = MarkdownImageTasks.submit {
            try {
                val snapshot = MarkdownImageCompression.read(file)
                val image = snapshot.originalImage()
                if (Thread.currentThread().isInterrupted) return@submit
                Platform.runLater {
                    if (disposed) return@runLater
                    loadTask = null
                    source = snapshot
                    original.view.image = image
                    original.info.text = imageInfo(snapshot.info)
                    quality.isDisable = false
                    scale.isDisable = false
                    MarkdownImageCompression.compressedQuality(file)?.let { value ->
                        previousQuality.text = Locales.str("markdown.imageEditPreviousQuality").replace("{quality}", value.toString())
                        previousQuality.isVisible = true
                        previousQuality.isManaged = true
                    }
                    requestPreview(true)
                }
            } catch (error: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e("Load markdown image editor source failed", error)
                    Platform.runLater { if (!disposed) status.text = Locales.str("markdown.imageQualityFailed") }
                }
            }
        }
    }

    private fun currentOptions(): ImageCompressionHelper.Options = ImageCompressionHelper.Options(
        quality = qualities[quality.selectedIndex()], scale = scale.value.roundToInt() / 100.0, roundLongEdgeTo = 10
    )

    private fun requestPreview(immediate: Boolean = false) {
        val snapshot = source ?: return
        if (disposed || saving) return
        val options = currentOptions()
        val size = ImageCompressionHelper().scaledSize(snapshot.info.width, snapshot.info.height, options.scale, options.roundLongEdgeTo)
        dimensions.text = "${scale.value.roundToInt()}%   ${size.width} × ${size.height} px"
        save.isDisable = true
        status.text = Locales.str("markdown.imageEditRendering")
        if (immediate) scheduler.startNow() else scheduler.request()
    }

    private fun startPreview(requestId: Long) {
        val snapshot = source
        if (snapshot == null || disposed || saving) { scheduler.complete(requestId, null); return }
        val options = currentOptions()
        val cached = preview
        if (cached != null && cached.options == options) {
            scheduler.complete(requestId) {
                save.isDisable = !canSave()
                status.text = ""
            }
            return
        }
        renderTask = MarkdownImageTasks.submit {
            try {
                val encoded = snapshot.preview(options)
                val image = Image(encoded.bytes.inputStream())
                require(!image.isError && image.width > 0) { "JPEG preview decode failed" }
                if (Thread.currentThread().isInterrupted) return@submit
                Platform.runLater {
                    scheduler.complete(requestId) {
                        renderTask = null
                        if (!disposed && source === snapshot && options == currentOptions()) {
                            preview = Preview(options, encoded)
                            processed.view.image = image
                            processed.info.text = imageInfo(encoded.result.output)
                            save.isDisable = !canSave()
                            status.text = ""
                        }
                    }
                }
            } catch (error: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e("Render markdown image editing preview failed", error)
                    Platform.runLater {
                        scheduler.complete(requestId) {
                            renderTask = null
                            if (!disposed && options == currentOptions()) {
                                save.isDisable = true
                                status.text = Locales.str("markdown.imageEditPreviewFailed")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun savePreview() {
        val snapshot = source ?: return
        val result = preview ?: return
        if (disposed || saving || result.options != currentOptions() || save.isDisable) return
        if (!canSave()) { status.text = Locales.str("markdown.imageEditSourceChanged"); return }
        val previous = MarkdownImageCompression.compressedQuality(file)
        if (previous != null) {
            val confirm = ButtonType(Locales.str("markdown.imageQualityContinue"), ButtonBar.ButtonData.OK_DONE)
            val warning = Alert(Alert.AlertType.CONFIRMATION).apply { initOwner(stage) }.withAppTheme()
            warning.title = Locales.str("markdown.imageEditTitle")
            warning.headerText = Locales.str("markdown.imageQualityRepeat").replace("{quality}", previous.toString())
            warning.contentText = Locales.str("markdown.imageQualityRepeatHint")
            warning.buttonTypes.setAll(ButtonType.CANCEL, confirm)
            (warning.dialogPane.lookupButton(confirm) as Button).isDefaultButton = false
            (warning.dialogPane.lookupButton(ButtonType.CANCEL) as Button).isDefaultButton = true
            if (warning.showAndWait().orElse(null) != confirm) return
        }
        if (!canSave()) { status.text = Locales.str("markdown.imageEditSourceChanged"); return }
        saving = true
        save.isDisable = true
        cancel.isDisable = true
        quality.isDisable = true
        scale.isDisable = true
        status.text = Locales.str("markdown.imageEditSaving")
        saveTask = MarkdownImageTasks.submit {
            try {
                val output = snapshot.save(result.encoded, result.options)
                Platform.runLater {
                    if (!disposed) {
                        saveTask = null
                        saving = false
                        stage.close()
                        saved(output)
                    }
                }
            } catch (error: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e("Save markdown image editing result failed", error)
                    Platform.runLater {
                        if (!disposed) {
                            saveTask = null
                            saving = false
                            cancel.isDisable = false
                            quality.isDisable = false
                            scale.isDisable = false
                            save.isDisable = !canSave()
                            status.text = Locales.str("markdown.imageCompressFailed")
                        }
                    }
                }
            }
        }
    }

    fun close() {
        stage.close()
        dispose()
    }

    private fun dispose() {
        if (disposed) return
        disposed = true
        scheduler.dispose()
        MarkdownImageTasks.cancel(loadTask)
        MarkdownImageTasks.cancel(renderTask)
        MarkdownImageTasks.cancel(saveTask)
        source = null
        preview = null
        original.view.image = null
        processed.view.image = null
        closed()
    }

    private fun imageInfo(info: ImageCompressionHelper.ImageInfo): String {
        val bytes = info.byteSize
        val size = when {
            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f MiB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KiB", bytes / 1024.0)
            else -> "$bytes B"
        }
        return "${info.width} × ${info.height} px   ${info.format.uppercase(Locale.ROOT)}   $size"
    }

    private class ImagePane(title: String) : VBox(8.0) {
        val view = ImageView().apply { isPreserveRatio = true; isSmooth = true }
        private val canvas = StackPane(view).apply { padding = Insets(10.0); styleClass.add("markdown-image-edit-canvas") }
        val scroll = ScrollPane(canvas).apply {
            minWidth = 0.0
            minHeight = 0.0
            isPannable = true
            styleClass.add("markdown-image-edit-scroll")
        }
        val info = Label().apply { isWrapText = true }

        init {
            minWidth = 0.0
            prefWidth = 0.0
            styleClass.add("markdown-image-edit-pane")
            children.setAll(Label(title), scroll, info)
            VBox.setVgrow(scroll, Priority.ALWAYS)
            scroll.isFitToWidth = true
            scroll.isFitToHeight = true
            canvas.prefWidthProperty().bind(Bindings.createDoubleBinding({ scroll.viewportBounds.width }, scroll.viewportBoundsProperty()))
            canvas.prefHeightProperty().bind(Bindings.createDoubleBinding({ scroll.viewportBounds.height }, scroll.viewportBoundsProperty()))
        }

        fun bindScale(scale: DoubleBinding) {
            view.fitWidthProperty().bind(Bindings.createDoubleBinding(
                { Math.max(1.0, (view.image?.width ?: 1.0) * scale.value) }, view.imageProperty(), scale))
            view.fitHeightProperty().bind(Bindings.createDoubleBinding(
                { Math.max(1.0, (view.image?.height ?: 1.0) * scale.value) }, view.imageProperty(), scale))
        }
    }
}

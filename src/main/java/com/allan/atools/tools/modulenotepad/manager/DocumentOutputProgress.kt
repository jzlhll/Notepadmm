package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.UIContext
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Locales
import javafx.beans.value.ChangeListener
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.stage.Stage

/** 输出任务独立展示进度；关闭窗口或退出应用时取消所属任务。 */
class DocumentOutputProgress(title: String, cancel: () -> Unit, preview: Node? = null) {
    private val stage = Stage()
    private val status = Label(Locales.str("document.outputPreparing")).apply { isWrapText = true }
    private var finished = false
    private val closing = ChangeListener<String> { _, _, _ -> if (!finished) cancel() }

    init {
        stage.initOwner(UIContext.mainWindow)
        stage.title = title
        val button = Button(Locales.str("cancel")).apply { setOnAction { cancel() } }
        val indicator = ProgressIndicator().apply { setPrefSize(24.0, 24.0) }
        val bar = HBox(12.0, indicator, status, button).apply { style = "-fx-padding: 12; -fx-alignment: center-left;" }
        stage.scene = Scene(BorderPane(preview, bar, null, null, null),
            if (preview == null) 440.0 else 760.0, if (preview == null) 90.0 else 720.0)
        stage.setOnCloseRequest { if (!finished) cancel() }
        ThreadUtils.sClosingProper.addListener(closing)
        stage.show()
    }

    fun showPage(page: Int, total: Int? = null) {
        status.text = if (total == null) String.format(Locales.str("document.outputPage"), page)
            else String.format(Locales.str("document.outputPages"), page, total)
    }

    fun toFront() { stage.toFront() }

    fun close() {
        finished = true
        ThreadUtils.sClosingProper.removeListener(closing)
        stage.close()
    }
}

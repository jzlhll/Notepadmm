@file:JvmName("SecondaryWindows")

package com.allan.atools.ui

import com.allan.atools.tools.AllStagesManager
import com.allan.atools.utils.Locales
import com.allan.atools.utils.ResLocation
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.WindowEvent

/** 二级窗口共用无标题外框，导航与关闭操作合并到顶部，配色随应用主题切换。 */
fun createSecondaryScene(stage: Stage, content: Parent, width: Double, height: Double): Scene {
    stage.initStyle(StageStyle.TRANSPARENT)
    val close = Button("×").apply {
        styleClass.addAll("secondary-window-close", "window-header-action")
        accessibleText = Locales.str("close")
        tooltip = Tooltip(accessibleText)
        setOnAction { stage.fireEvent(WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)) }
    }
    val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
    val header = HBox(spacer, close).apply {
        alignment = Pos.CENTER_RIGHT
        styleClass.add("secondary-window-header")
        prefHeight = 36.0
    }
    // FXML 中标记的导航移入操作区，避免搜索页签上方再占一行。
    val navigation = content.lookup(".secondary-window-navigation")
    if (navigation != null && navigation.parent is Pane) {
        (navigation.parent as Pane).children.remove(navigation)
        navigation.styleClass.add("window-header-action")
        header.children.add(0, navigation)
        header.styleClass.add("secondary-window-header-with-navigation")
        header.prefHeight = 50.0
    }
    val frame = BorderPane(content, header, null, null, null).apply {
        styleClass.add("secondary-window")
    }
    frame.clip = Rectangle().apply {
        widthProperty().bind(frame.widthProperty())
        heightProperty().bind(frame.heightProperty())
        arcWidth = 20.0
        arcHeight = 20.0
    }
    val scene = if (width > 0 && height > 0) Scene(frame, width + 4, height + header.prefHeight + 4) else Scene(frame)
    scene.fill = Color.TRANSPARENT
    val manager = AllStagesManager.getInstance()
    scene.stylesheets.setAll(manager.mainStage.scene.stylesheets)
    scene.stylesheets.add(ResLocation.getURL("css", "secondary_windows.css").toExternalForm())
    manager.registerScene(scene)
    MainWindowChrome(stage, frame, header)
    return scene
}

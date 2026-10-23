@file:JvmName("DialogTheme")

package com.allan.atools.ui

import com.allan.atools.tools.AllStagesManager
import com.allan.atools.utils.ResLocation
import javafx.scene.control.Dialog

/** 为独立弹窗场景加载应用样式，并纳入已有主题切换机制。 */
fun <T : Dialog<*>> T.withAppTheme(): T {
    val manager = AllStagesManager.getInstance()
    if (owner == null) initOwner(manager.mainStage)
    val scene = dialogPane.scene
    scene.stylesheets.setAll(owner.scene.stylesheets)
    scene.stylesheets.add(ResLocation.getURL("css", "dialogs.css").toExternalForm())
    dialogPane.styleClass.add("app-dialog-pane")
    manager.registerScene(scene)
    return this
}

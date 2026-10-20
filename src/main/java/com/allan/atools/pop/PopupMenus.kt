@file:JvmName("PopupMenus")

package com.allan.atools.pop

import javafx.event.Event
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.ContextMenu
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.Tooltip
import javafx.scene.layout.Region
import javafx.stage.Window
import javafx.stage.PopupWindow
import javafx.stage.WindowEvent

private const val MENU_LAYER_KEY = "atools.menu.overlay"

/** 沿用菜单项模型，将菜单直接绘制在所属窗口内，不创建原生弹出窗口。 */
class AppContextMenu(vararg items: MenuItem) : ContextMenu(*items) {
    private var updateBeforeShow: Runnable? = null
    private var layer: MenuOverlay? = null

    fun setBeforeShow(action: Runnable) {
        updateBeforeShow = action
    }

    fun isMenuShowing(): Boolean = layer != null

    override fun show(anchor: Node, side: Side, dx: Double, dy: Double) {
        open(anchor) { it.showMenu(this, anchor, side, dx, dy) }
    }

    override fun show(anchor: Node, screenX: Double, screenY: Double) {
        open(anchor) { it.showMenu(this, anchor, screenX, screenY) }
    }

    private fun open(anchor: Node, show: (MenuOverlay) -> Unit) {
        val scene = anchor.scene ?: return
        if (scene.window?.isShowing != true) return
        hide()
        updateBeforeShow?.run()
        Event.fireEvent(this, Event(Menu.ON_SHOWING))
        if (items.none { it.isVisible }) return
        Event.fireEvent(this, WindowEvent(this, WindowEvent.WINDOW_SHOWING))
        val target = menuLayer(scene)
        layer = target
        show(target)
        Event.fireEvent(this, Event(Menu.ON_SHOWN))
        Event.fireEvent(this, WindowEvent(this, WindowEvent.WINDOW_SHOWN))
    }

    override fun hide() {
        layer?.close()
    }

    fun overlayClosed() {
        if (layer == null) return
        Event.fireEvent(this, Event(Menu.ON_HIDING))
        Event.fireEvent(this, WindowEvent(this, WindowEvent.WINDOW_HIDING))
        layer = null
        Event.fireEvent(this, Event(Menu.ON_HIDDEN))
        Event.fireEvent(this, WindowEvent(this, WindowEvent.WINDOW_HIDDEN))
    }
}

fun isMenuShowing(menu: ContextMenu?): Boolean = when (menu) {
    is AppContextMenu -> menu.isMenuShowing()
    else -> menu?.isShowing == true
}

fun hasShowingPopup(owner: Window): Boolean {
    if ((owner.scene?.properties?.get(MENU_LAYER_KEY) as? MenuOverlay)?.isOpen == true) return true
    return Window.getWindows().any { window ->
        if (window !is PopupWindow || window is Tooltip || !window.isShowing) return@any false
        var parent = window.ownerWindow
        while (parent != null) {
            if (parent === owner) return@any true
            parent = (parent as? PopupWindow)?.ownerWindow
        }
        false
    }
}

@JvmOverloads
fun showPopupContent(anchor: Node, content: Region, side: Side = Side.BOTTOM,
                     dx: Double = 0.0, dy: Double = 0.0, onHidden: Runnable? = null): MenuOverlay {
    val scene = requireNotNull(anchor.scene) { "Menu anchor must be attached to a scene" }
    val layer = menuLayer(scene)
    layer.showContent(anchor, content, side, dx, dy, onHidden)
    return layer
}

private fun menuLayer(scene: Scene): MenuOverlay {
    return scene.properties.getOrPut(MENU_LAYER_KEY) { MenuOverlay(scene) } as MenuOverlay
}

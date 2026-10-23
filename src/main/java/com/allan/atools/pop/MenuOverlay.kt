package com.allan.atools.pop

import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.css.PseudoClass
import javafx.event.EventHandler
import javafx.geometry.Bounds
import javafx.geometry.Pos
import javafx.geometry.Side
import javafx.scene.AccessibleRole
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.CustomMenuItem
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

/** 管理同一场景内的菜单与子菜单，使用固定尺寸直接显示，关闭时释放输入监听。 */
class MenuOverlay(private val scene: Scene) {
    private val surface = Pane().apply {
        isManaged = false
        isPickOnBounds = false
        addEventHandler(KeyEvent.ANY) { it.consume() }
    }
    private val host = object : StackPane() {
        override fun layoutChildren() {
            super.layoutChildren()
            surface.resizeRelocate(0.0, 0.0, width, height)
        }
    }
    private val panels = mutableListOf<MenuPanel>()
    private var menu: AppContextMenu? = null
    private var onHidden: Runnable? = null
    private var previousFocus: Node? = null
    private val active = PseudoClass.getPseudoClass("active")
    private val outsidePress = EventHandler<MouseEvent> { event ->
        if (panels.none { contains(it.node, event.target as? Node) }) {
            val consume = menu?.consumeAutoHidingEvents ?: true
            if (menu?.isAutoHide != false) {
                close()
                if (consume) {
                    event.consume()
                    consumeClosingClick()
                }
            }
        }
    }
    private val keys = EventHandler<KeyEvent>(::handleKey)
    private val windowFocus = ChangeListener<Boolean> { _, _, focused -> if (!focused) close() }
    private val geometryChanged = ChangeListener<Number> { _, _, _ -> close() }

    val isOpen: Boolean get() = panels.isNotEmpty()

    fun showMenu(value: AppContextMenu, anchor: Node, side: Side, dx: Double, dy: Double) {
        begin(value, null)
        val panel = createMenu(value.items, null, null)
        val bounds = anchor.localToScene(anchor.boundsInLocal)
        place(panel, bounds, side, dx, dy)
        panel.node.requestFocus()
        select(panel, panel.rows.indexOfFirst { !it.isDisabled })
    }

    fun showMenu(value: AppContextMenu, anchor: Node, screenX: Double, screenY: Double) {
        begin(value, null)
        val point = surface.screenToLocal(screenX, screenY)
        val panel = createMenu(value.items, null, null)
        placeAt(panel, point.x, point.y)
        panel.node.requestFocus()
        select(panel, panel.rows.indexOfFirst { !it.isDisabled })
    }

    fun showContent(anchor: Node, content: Region, side: Side, dx: Double, dy: Double, hidden: Runnable?) {
        begin(null, hidden)
        val panel = createPanel(content, emptyList(), null, null)
        place(panel, anchor.localToScene(anchor.boundsInLocal), side, dx, dy)
        (focusTarget(content) ?: panel.node).requestFocus()
    }

    private fun begin(value: AppContextMenu?, hidden: Runnable?) {
        close()
        previousFocus = scene.focusOwner
        menu = value
        onHidden = hidden
        if (surface.scene !== scene) {
            val original = scene.root
            if (original is Pane) {
                detach(surface)
                original.children.add(surface)
            } else {
                scene.root = host
                host.children.setAll(original, surface)
                host.applyCss()
                host.layout()
            }
        }
        surface.toFront()
        surface.resizeRelocate(0.0, 0.0, scene.width, scene.height)
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsidePress)
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keys)
        scene.window.focusedProperty().addListener(windowFocus)
        scene.window.xProperty().addListener(geometryChanged)
        scene.window.yProperty().addListener(geometryChanged)
        scene.widthProperty().addListener(geometryChanged)
        scene.heightProperty().addListener(geometryChanged)
    }

    fun close() {
        if (!isOpen) return
        val oldMenu = menu
        val hidden = onHidden
        val focus = previousFocus
        menu = null
        onHidden = null
        previousFocus = null
        closeAfter(-1)
        scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsidePress)
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keys)
        scene.window.focusedProperty().removeListener(windowFocus)
        scene.window.xProperty().removeListener(geometryChanged)
        scene.window.yProperty().removeListener(geometryChanged)
        scene.widthProperty().removeListener(geometryChanged)
        scene.heightProperty().removeListener(geometryChanged)
        if (scene.window.isFocused && focus != null && focus.scene === scene && focus.isVisible && !focus.isDisabled) focus.requestFocus()
        oldMenu?.overlayClosed()
        hidden?.run()
    }

    private fun createMenu(items: List<MenuItem>, parentRow: HBox?, owner: Menu?): MenuPanel {
        val rows = mutableListOf<HBox>()
        val content = VBox().apply { styleClass.add("app-menu-list") }
        for (item in items.filter { it.isVisible }) {
            if (item is SeparatorMenuItem) {
                content.children.add(Region().apply { styleClass.add("app-menu-separator") })
                continue
            }
            val row = HBox(8.0).apply {
                styleClass.add("app-menu-item")
                style = item.style
                alignment = Pos.CENTER_LEFT
                isDisable = item.isDisable
                isFocusTraversable = !item.isDisable
                accessibleRole = AccessibleRole.MENU_ITEM
                accessibleText = item.text
                userData = item
            }
            if (item is CustomMenuItem) {
                item.content?.let { node -> detach(node); row.children.add(node) }
            } else {
                val mark = when (item) {
                    is RadioMenuItem -> if (item.isSelected) "●" else ""
                    is CheckMenuItem -> if (item.isSelected) "✓" else ""
                    else -> ""
                }
                if (items.any { it is CheckMenuItem || it is RadioMenuItem }) {
                    row.children.add(Label(mark).apply { styleClass.add("app-menu-mark") })
                }
                item.graphic?.let { graphic -> detach(graphic); row.children.add(graphic) }
                row.children.add(Label(item.text).apply {
                    isMnemonicParsing = item.isMnemonicParsing
                    minWidth = 0.0
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                })
                item.accelerator?.let { key -> row.children.add(Label(key.displayText).apply { styleClass.add("app-menu-shortcut") }) }
                if (item is Menu) row.children.add(Label("›").apply { styleClass.add("app-menu-submenu-arrow") })
            }
            rows.add(row)
            content.children.add(row)
        }
        val panel = createPanel(content, rows, parentRow, owner)
        for ((index, row) in rows.withIndex()) {
            row.setOnMouseEntered {
                if (!row.isDisabled) {
                    select(panel, index)
                    val item = row.userData as MenuItem
                    if (item is Menu) openSubmenu(panel, row, item) else closeAfter(panels.indexOf(panel))
                }
            }
            row.setOnMouseClicked { event ->
                if (event.button == MouseButton.PRIMARY && !row.isDisabled) {
                    val item = row.userData as MenuItem
                    if (item is Menu) openSubmenu(panel, row, item) else activate(item)
                    event.consume()
                }
            }
        }
        return panel
    }

    private fun createPanel(content: Region, rows: List<HBox>, parent: HBox?, owner: Menu?): MenuPanel {
        detach(content)
        val scroll = ScrollPane(content).apply {
            styleClass.add("app-menu-scroll")
            isFitToWidth = true
            isFitToHeight = rows.isEmpty()
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            isFocusTraversable = false
        }
        val node = VBox(scroll).apply {
            styleClass.add("app-menu-panel")
            isManaged = false
            isVisible = false
            isFocusTraversable = true
            VBox.setVgrow(scroll, Priority.ALWAYS)
        }
        surface.children.add(node)
        node.applyCss()
        val availableWidth = scene.width - 16.0
        val preferred = content.prefWidth(-1.0) + node.insets.left + node.insets.right
        val minimumWidth = if (rows.isEmpty()) 32.0 else 160.0
        var width = when {
            preferred > availableWidth -> availableWidth
            preferred < minimumWidth -> if (availableWidth < minimumWidth) availableWidth else minimumWidth
            else -> preferred
        }
        val preferredHeight = content.prefHeight(width - node.insets.left - node.insets.right) + node.insets.top + node.insets.bottom
        val availableHeight = scene.height - 16.0
        val height = if (preferredHeight > availableHeight) availableHeight else preferredHeight
        if (preferredHeight > availableHeight) {
            // 内容自身的滚动条已包含在首选宽度中，仅为外层实际溢出预留空间。
            val scrollbarWidth = (scroll.lookup(".scroll-bar:vertical") as? Region)?.prefWidth(-1.0) ?: 0.0
            val expandedWidth = width + scrollbarWidth
            width = if (expandedWidth > availableWidth) availableWidth else expandedWidth
        }
        node.setMinSize(width, height)
        node.setPrefSize(width, height)
        node.setMaxSize(width, height)
        node.resize(width, height)
        node.layout()
        val panel = MenuPanel(node, rows, parent, owner)
        panels.add(panel)
        return panel
    }

    private fun place(panel: MenuPanel, bounds: Bounds, side: Side, dx: Double, dy: Double) {
        var x = bounds.minX + dx
        var y = bounds.maxY + dy
        when (side) {
            Side.TOP -> y = bounds.minY - panel.node.height + dy
            Side.LEFT -> { x = bounds.minX - panel.node.width + dx; y = bounds.minY + dy }
            Side.RIGHT -> { x = bounds.maxX + dx; y = bounds.minY + dy }
            else -> if (y + panel.node.height > scene.height - 8.0) y = bounds.minY - panel.node.height + dy
        }
        placeAt(panel, x, y)
    }

    private fun placeAt(panel: MenuPanel, targetX: Double, targetY: Double) {
        val right = scene.width - panel.node.width - 8.0
        val bottom = scene.height - panel.node.height - 8.0
        val x = when { targetX < 8.0 -> 8.0; targetX > right -> right; else -> targetX }
        val y = when { targetY < 8.0 -> 8.0; targetY > bottom -> bottom; else -> targetY }
        val scaleX = scene.window.outputScaleX
        val scaleY = scene.window.outputScaleY
        panel.node.relocate(Math.round(x * scaleX) / scaleX, Math.round(y * scaleY) / scaleY)
        panel.node.isVisible = true
    }

    private fun openSubmenu(parent: MenuPanel, row: HBox, item: Menu) {
        val index = panels.indexOf(parent)
        if (panels.getOrNull(index + 1)?.owner === item) return
        closeAfter(index)
        item.show()
        if (item.items.none { it.isVisible }) {
            item.hide()
            return
        }
        val panel = createMenu(item.items, row, item)
        val bounds = row.localToScene(row.boundsInLocal)
        val x = if (bounds.maxX + panel.node.width + 4.0 > scene.width - 8.0) bounds.minX - panel.node.width - 4.0 else bounds.maxX + 4.0
        placeAt(panel, x, bounds.minY - 4.0)
    }

    private fun closeAfter(index: Int) {
        while (panels.lastIndex > index) {
            val panel = panels.removeAt(panels.lastIndex)
            panel.owner?.hide()
            for (row in panel.rows) row.children.clear()
            surface.children.remove(panel.node)
        }
    }

    private fun select(panel: MenuPanel, index: Int) {
        if (index !in panel.rows.indices) return
        panel.selected = index
        for ((position, row) in panel.rows.withIndex()) row.pseudoClassStateChanged(active, position == index)
        val row = panel.rows[index]
        row.requestFocus()
        val scroll = panel.node.children.first() as ScrollPane
        val viewport = scroll.viewportBounds.height
        val range = scroll.content.layoutBounds.height - viewport
        if (range > 0.0) {
            val bounds = row.boundsInParent
            val top = scroll.vvalue * range
            if (bounds.minY < top) scroll.vvalue = bounds.minY / range
            else if (bounds.maxY > top + viewport) scroll.vvalue = (bounds.maxY - viewport) / range
        }
    }

    private fun activate(item: MenuItem) {
        if (item.isDisable) return
        when (item) {
            is CheckMenuItem -> item.isSelected = !item.isSelected
            is RadioMenuItem -> item.isSelected = true
        }
        if (item !is CustomMenuItem || item.isHideOnClick) close()
        item.fire()
    }

    private fun handleKey(event: KeyEvent) {
        if (event.code == KeyCode.ESCAPE || event.code == KeyCode.TAB) {
            if (menu?.isHideOnEscape != false) close()
            event.consume()
            return
        }
        val panel = panels.lastOrNull { it.rows.any { row -> contains(row, scene.focusOwner) } }
            ?: panels.lastOrNull() ?: return
        if (panel.rows.isEmpty()) return
        val enabled = panel.rows.indices.filter { !panel.rows[it].isDisabled }
        if (enabled.isEmpty()) return
        when (event.code) {
            KeyCode.DOWN, KeyCode.UP -> {
                val current = enabled.indexOf(panel.selected)
                val next = if (event.code == KeyCode.DOWN) (current + 1) % enabled.size else if (current <= 0) enabled.lastIndex else current - 1
                select(panel, enabled[next])
            }
            KeyCode.HOME -> select(panel, enabled.first())
            KeyCode.END -> select(panel, enabled.last())
            KeyCode.LEFT -> {
                val index = panels.indexOf(panel)
                if (index > 0) {
                    val parentRow = panel.parentRow
                    closeAfter(index - 1)
                    parentRow?.requestFocus()
                } else closeAfter(0)
            }
            KeyCode.RIGHT, KeyCode.ENTER, KeyCode.SPACE -> {
                val row = panel.rows.getOrNull(panel.selected) ?: return
                val item = row.userData as MenuItem
                if (item is Menu) {
                    openSubmenu(panel, row, item)
                    panels.getOrNull(panels.indexOf(panel) + 1)?.let {
                        select(it, it.rows.indexOfFirst { row -> !row.isDisabled })
                    }
                } else if (event.code != KeyCode.RIGHT) activate(item)
            }
            else -> {
                val item = panels.asSequence().flatMap { it.rows.asSequence() }
                    .map { it.userData as MenuItem }.firstOrNull { !it.isDisable && it.accelerator?.match(event) == true }
                if (item == null) return
                activate(item)
            }
        }
        event.consume()
    }

    private fun consumeClosingClick() {
        val handler = object : EventHandler<MouseEvent> {
            override fun handle(event: MouseEvent) {
                if (event.eventType == MouseEvent.MOUSE_CLICKED) {
                    event.consume()
                    remove()
                } else {
                    // release 后仍会合成 clicked；本轮输入结束后再移除拦截。
                    Platform.runLater { remove() }
                }
            }

            private fun remove() {
                scene.removeEventFilter(MouseEvent.MOUSE_CLICKED, this)
                scene.removeEventFilter(MouseEvent.MOUSE_RELEASED, this)
            }
        }
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, handler)
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, handler)
    }

    private fun contains(parent: Node, target: Node?): Boolean {
        var node = target
        while (node != null) {
            if (node === parent) return true
            node = node.parent
        }
        return false
    }

    private fun focusTarget(node: Node): Node? {
        if (node.isFocusTraversable && node.isVisible && !node.isDisabled) return node
        if (node is Parent) {
            for (child in node.childrenUnmodifiable) focusTarget(child)?.let { return it }
        }
        return null
    }

    private fun detach(node: Node) {
        (node.parent as? Pane)?.children?.remove(node)
    }

    private class MenuPanel(val node: VBox, val rows: List<HBox>, val parentRow: HBox?, val owner: Menu?) {
        var selected = -1
    }
}

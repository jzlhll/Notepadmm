package com.allan.atools.ui.controls

import com.allan.atools.pop.AppContextMenu
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.css.PseudoClass
import javafx.scene.AccessibleAction
import javafx.scene.AccessibleAttribute
import javafx.scene.AccessibleRole
import javafx.scene.control.Button
import javafx.scene.control.ContentDisplay
import javafx.scene.control.MenuItem
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane

/** 以普通按钮承载窗口内菜单，保留按压反馈、键盘操作和展开状态。 */
class AppMenuButton : Button() {
    val items: ObservableList<MenuItem> = FXCollections.observableArrayList()
    private val menu = AppContextMenu()
    private val showing = ReadOnlyBooleanWrapper(false)
    private var updateBeforeShow: Runnable? = null
    private val arrow = StackPane(StackPane().apply { styleClass.add("arrow") }).apply {
        styleClass.add("arrow-button")
        maxWidth = Region.USE_PREF_SIZE
    }

    var showArrow: Boolean = true
        set(value) {
            field = value
            graphic = if (value) arrow else null
        }

    init {
        accessibleRole = AccessibleRole.MENU_BUTTON
        contentDisplay = ContentDisplay.RIGHT
        graphicTextGap = 6.0
        graphic = arrow
        setOnAction { if (showing.get()) hide() else show() }
        addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.DOWN || event.code == KeyCode.UP) {
                show()
                event.consume()
            }
        }
        menu.setOnHidden { showing.set(false) }
        showing.addListener { _, _, value -> pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), value) }
    }

    fun setBeforeShow(action: Runnable) {
        updateBeforeShow = action
    }

    fun isShowing(): Boolean = showing.get()

    fun showingProperty(): ReadOnlyBooleanProperty = showing.readOnlyProperty

    fun show() {
        if (isDisabled || showing.get()) return
        updateBeforeShow?.run()
        menu.items.setAll(items)
        menu.show(this, javafx.geometry.Side.BOTTOM, 0.0, 0.0)
        showing.set(menu.isMenuShowing())
    }

    fun hide() {
        menu.hide()
    }

    override fun queryAccessibleAttribute(attribute: AccessibleAttribute, vararg parameters: Any?): Any? {
        if (attribute == AccessibleAttribute.EXPANDED) return showing.get()
        return super.queryAccessibleAttribute(attribute, *parameters)
    }

    override fun executeAccessibleAction(action: AccessibleAction, vararg parameters: Any?) {
        when (action) {
            AccessibleAction.EXPAND -> show()
            AccessibleAction.COLLAPSE -> hide()
            else -> super.executeAccessibleAction(action, *parameters)
        }
    }
}

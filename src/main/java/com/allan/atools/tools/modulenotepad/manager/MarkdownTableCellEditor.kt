package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.ui.controls.AccessibleTextArea
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.ScrollPane
import javafx.scene.control.Skin
import javafx.scene.control.skin.TextAreaSkin
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment

/** 在文本变化通知期间保留实际替换范围，避免重复文字的差量位置产生歧义。 */
open class MarkdownTableCellEditor : AccessibleTextArea() {
    data class Replacement(val start: Int, val end: Int, val original: String)

    var activeReplacement: Replacement? = null
        private set

    private val alignment = SimpleObjectProperty(TextAlignment.LEFT)
    var cellTextAlignment: TextAlignment
        get() = alignment.get()
        set(value) { alignment.set(value) }

    override fun createDefaultSkin(): Skin<*> = object : TextAreaSkin(this@MarkdownTableCellEditor) {
        init {
            for (pane in children.filterIsInstance<ScrollPane>()) {
                // 宽高设为 0 会被 ScrollPaneSkin 回退到默认尺寸，须关闭显隐策略以取消占位。
                pane.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                pane.vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                (pane.content.lookup(".text") as? Text)?.textAlignmentProperty()?.bind(alignment)
            }
        }
    }

    override fun copy() {
        com.allan.atools.richtext.codearea.MarkdownClipboard.cancelPendingCopy()
        super.copy()
    }

    override fun replaceText(start: Int, end: Int, text: String) {
        val previous = activeReplacement
        activeReplacement = Replacement(start, end, getText())
        try {
            super.replaceText(start, end, text)
        } finally {
            activeReplacement = previous
        }
    }
}

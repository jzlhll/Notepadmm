package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.ui.controls.AccessibleTextArea

/** 在文本变化通知期间保留实际替换范围，避免重复文字的差量位置产生歧义。 */
open class MarkdownTableCellEditor : AccessibleTextArea() {
    data class Replacement(val start: Int, val end: Int, val original: String)

    var activeReplacement: Replacement? = null
        private set

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

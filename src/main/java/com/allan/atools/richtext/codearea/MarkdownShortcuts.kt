package com.allan.atools.richtext.codearea

import com.allan.atools.utils.ResLocation
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent

/** 正文、表格与快捷键帮助共用现有键位，格式命令的适用范围由各编辑入口判断。 */
enum class MarkdownShortcuts(val labelKey: String, val key: KeyCode, val shift: Boolean = false, val heading: Int = -1) {
    BOLD("markdown.bold", KeyCode.B),
    ITALIC("markdown.italic", KeyCode.I),
    INLINE_CODE("markdown.inlineCode", KeyCode.BACK_QUOTE),
    LINK("markdown.editLink", KeyCode.K),
    STRIKE("markdown.strike", KeyCode.X, true),
    H1("H1", KeyCode.DIGIT1, heading = 1),
    H2("H2", KeyCode.DIGIT2, heading = 2),
    H3("H3", KeyCode.DIGIT3, heading = 3),
    H4("H4", KeyCode.DIGIT4, heading = 4),
    H5("H5", KeyCode.DIGIT5, heading = 5),
    H6("H6", KeyCode.DIGIT6, heading = 6),
    PARAGRAPH("markdown.paragraph", KeyCode.DIGIT0, heading = 0),
    QUOTE("markdown.quote", KeyCode.Q, true),
    LIST("markdown.list", KeyCode.L, true),
    SOURCE("markdown.toggleSource", KeyCode.M, true),
    PASTE_PLAIN("markdown.pastePlain", KeyCode.V, true);

    fun display(): String = modifier() + (if (shift) "Shift + " else "") +
        if (key == KeyCode.BACK_QUOTE) "`" else key.getName()

    companion object {
        @JvmStatic fun modifier(): String = if (ResLocation.isWindow) "Ctrl + " else "⌘ + "

        @JvmStatic fun match(event: KeyEvent): MarkdownShortcuts? {
            if (!event.isShortcutDown || event.isAltDown || event.isControlDown && event.isMetaDown) return null
            return entries.firstOrNull {
                it.shift == event.isShiftDown && (it.key == event.code || it.heading >= 0 &&
                    event.code.name == "NUMPAD${it.heading}")
            }
        }
    }
}

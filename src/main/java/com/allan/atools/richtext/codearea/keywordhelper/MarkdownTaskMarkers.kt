package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.Code

/** 单独的状态短码可作为图标展示；源码与普通行内代码的保存语义保持一致。 */
object MarkdownTaskMarkers {
    @JvmStatic fun isStatus(value: String) = value == "[ ]" || value == "[x]" || value == "[X]"

    @JvmStatic fun inlineOffset(node: Code, source: String): Int {
        if (!isStatus(node.literal) || node.sourceSpans.size != 1) return -1
        val span = node.sourceSpans.first()
        val raw = source.substring(span.inputIndex, span.inputIndex + span.length)
        val offset = raw.indexOf('[')
        return if (offset >= 0 && raw.substring(offset, offset + 3) == node.literal) span.inputIndex + offset else -1
    }
}

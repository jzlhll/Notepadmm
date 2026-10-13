package com.allan.atools.richtext.codearea

/** 清除选中文字产生的临时背景，保留搜索着色和语法样式。 */
object EditorTemporaryHighlight {
    @JvmStatic
    fun clear(area: EditorArea) {
        if (area.length == 0) return
        val spans = area.getStyleSpans(0, area.length)
        var start = -1
        var end = 0
        var offset = 0
        for (span in spans) {
            if ("temporary" in span.style || "editor-selected-temp-label" in span.style) {
                if (start < 0) start = offset
                end = offset + span.length
            }
            offset += span.length
        }
        if (start < 0 || end <= start) return
        val cleared = spans.subView(start, end).mapStyles { styles ->
            if ("temporary" in styles || "editor-selected-temp-label" in styles) {
                val remaining = styles.filterNot { it == "temporary" || it == "editor-selected-temp-label" }.toSet()
                if (remaining.isEmpty()) area.initialTextStyle else remaining
            } else styles
        }
        val clearedStart = start
        area.suspendVisibleParsWhileInvoke { area.setStyleSpans(clearedStart, cleared) }
    }
}

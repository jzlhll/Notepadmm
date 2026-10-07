package com.allan.atools.richtext.codearea

import com.allan.uilibs.richtexts.CodeArea
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.geometry.Insets
import javafx.scene.layout.Region
import javafx.scene.text.TextFlow
import java.util.IdentityHashMap

/** 可见段落按类型换行，长源码行提供横向范围，正文仍使用视口宽度。 */
class MarkdownParagraphWrapSupport(private val area: CodeArea) {
    companion object {
        const val TABLE_SOURCE_CLASS = "markdown-table-source-line"

        // RichTextFX 0.11.7 的 ParagraphBox 是包内类，换行属性尚未暴露到编辑器 API。
        private val wrapProperty = Class.forName("org.fxmisc.richtext.ParagraphBox")
            .getDeclaredMethod("wrapTextProperty").apply { isAccessible = true }
    }

    private class Entry(val wrap: BooleanProperty, val padding: Insets, var forced: Boolean = false)
    private val entries = IdentityHashMap<Region, Entry>()
    private var visible = emptyList<Region>()
    private var layoutPending = false

    fun refresh(enabled: Boolean, collect: Boolean = true) {
        if (!enabled) {
            clear()
            return
        }
        var changed = false
        val flow = MarkdownVisibleParagraphs.flow(area)
        if (collect) visible = MarkdownVisibleParagraphs.boxes(flow)
        val visibleSet = visible.toHashSet()
        val extraWidth = if (area.isWrapText && flow != null)
            Math.max(0.0, flow.totalWidthEstimateProperty().value - flow.width) else 0.0
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (box, entry) = iterator.next()
            if (box !in visibleSet) {
                restore(box, entry)
                iterator.remove()
            }
        }
        for (box in visible) {
            val text = box.childrenUnmodifiable.firstOrNull { it is TextFlow } as? TextFlow ?: continue
            val entry = entries.getOrPut(box) { Entry(wrapProperty.invoke(box) as BooleanProperty, box.padding) }
            val source = text.styleClass.any { it == TABLE_SOURCE_CLASS || it.startsWith("md-code-block-") }
            // 隐藏源码的表格、Mermaid 预览不参与横向内容宽度估算。
            val force = source && text.isVisible && !text.isMouseTransparent
            if (entry.forced != force) {
                entry.wrap.unbind()
                if (force) entry.wrap.set(false) else entry.wrap.bind(area.wrapTextProperty())
                entry.forced = force
                changed = true
            }
            // Flowless 给所有段落使用相同宽度；扣掉长行多出的部分，避免正文也被撑宽。
            val extra = if (!force) extraWidth else 0.0
            val original = entry.padding
            val padding = Insets(original.top, original.right + extra, original.bottom, original.left)
            if (box.padding != padding) {
                box.padding = padding
                changed = true
            }
        }
        // 新进入视口的段落在本次布局后才能识别；合并到下一次脉冲刷新虚拟行高。
        if (changed && !layoutPending) {
            layoutPending = true
            Platform.runLater {
                layoutPending = false
                area.requestLayout()
            }
        }
    }

    private fun restore(box: Region, entry: Entry) {
        if (entry.forced) {
            entry.wrap.unbind()
            if (box.scene != null) entry.wrap.bind(area.wrapTextProperty()) else entry.wrap.set(area.isWrapText)
        }
        if (box.padding != entry.padding) box.padding = entry.padding
    }

    fun clear() {
        entries.forEach { (box, entry) -> restore(box, entry) }
        entries.clear()
        visible = emptyList()
    }
}

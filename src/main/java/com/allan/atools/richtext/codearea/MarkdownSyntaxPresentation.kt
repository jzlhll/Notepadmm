package com.allan.atools.richtext.codearea

import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot
import com.allan.uilibs.richtexts.CodeArea
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.Control
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.web.WebView
import org.fxmisc.richtext.model.StyleSpansBuilder
import org.fxmisc.richtext.model.StyleSpans

/** 保留源码坐标，只展开光标或选区所在结构的语法标记；拖选与输入法期间不改变排版。 */
class MarkdownSyntaxPresentation(private val area: EditorArea) {
    private var snapshot: MarkdownStructureSnapshot? = null
    private var version = -1L
    private var groupsByLine = emptyArray<MutableList<Int>?>()
    private var expanded = emptySet<Int>()
    private var pointerDown = false
    private var pointerSequence = 0L
    private var textPointer = false
    private var pending = false
    private var disposed = false
    private val afterPointer = ArrayDeque<Runnable>()
    private val changed = InvalidationListener { requestRefresh() }
    private val pressed = EventHandler<MouseEvent> {
        if (it.button == MouseButton.PRIMARY) {
            pointerSequence++
            pointerDown = true
            var node = it.target as? Node
            textPointer = true
            while (node != null && node !== area) {
                if (node is Control || node is WebView || CodeArea.MARKDOWN_TASK_RENDERED_CLASS in node.styleClass
                    || MarkdownDetailsPresentation.GRAPHIC_CLASS in node.styleClass) {
                    textPointer = false
                    break
                }
                node = node.parent
            }
        }
    }
    private val released = EventHandler<MouseEvent> {
        if (it.button == MouseButton.PRIMARY) {
            val sequence = pointerSequence
            val resolveSelection = textPointer
            textPointer = false
            // 等本次点击完成命中、链接跳转或任务切换后，再展开产生宽度变化的标记。
            Platform.runLater {
                if (!disposed && sequence == pointerSequence) {
                    if (resolveSelection && !area.markdownComposing) {
                        val anchor = visibleOffset(area.anchor)
                        val caret = visibleOffset(area.caretPosition)
                        if (anchor != area.anchor || caret != area.caretPosition) area.selectRange(anchor, caret)
                    }
                    pointerDown = false
                    while (afterPointer.isNotEmpty()) area.runAfterMarkdownComposition(afterPointer.removeFirst())
                    requestRefresh()
                }
            }
        }
    }

    fun install() {
        area.caretPositionProperty().addListener(changed)
        area.selectionProperty().addListener(changed)
        area.focusedProperty().addListener(changed)
        area.addEventFilter(MouseEvent.MOUSE_PRESSED, pressed)
        area.addEventFilter(MouseEvent.MOUSE_RELEASED, released)
    }

    fun runAfterPointer(action: Runnable) {
        if (disposed) return
        if (pointerDown) afterPointer.add(action) else action.run()
    }

    fun apply(value: MarkdownStructureSnapshot) {
        if (disposed) return
        if (snapshot !== value) {
            groupsByLine = arrayOfNulls(value.lines.size)
            value.syntaxGroups.forEachIndexed { index, group ->
                for (line in value.lineAt(group.start)..value.lineAt(group.end)) {
                    val entries = groupsByLine[line] ?: ArrayList<Int>().also { groupsByLine[line] = it }
                    entries.add(index)
                }
            }
        }
        snapshot = value
        version = area.editor.contentVersion
        val next = if (enabled()) selectedGroups(value) else emptySet()
        val desired = if (enabled()) next.flatMap { value.syntaxGroups[it].markers } else styledRanges(COLLAPSIBLE)
        // 直接对齐最终状态，不能先全量收起再展开，否则每次高亮都会重建同一批文本节点。
        val actual = styledRanges(EXPANDED)
        area.suspendVisibleParsWhileInvoke {
            changeExpanded(subtract(actual, desired), false)
            changeExpanded(subtract(desired, actual), true)
        }
        expanded = next
        area.markdownPresentation.refreshFrontMatterDelimiters()
    }

    fun requestRefresh() {
        if (pending || disposed) return
        pending = true
        Platform.runLater {
            if (!disposed) area.runAfterMarkdownComposition {
                pending = false
                refresh()
            }
        }
    }

    private fun enabled() = area.markdownPreviewEnabled && MarkdownEditorSupport.supportsMarkdown(area)
        && !area.editor.isRealtimeProcessingLimitReached

    private fun refresh() {
        val state = snapshot ?: return
        if (disposed || pointerDown || area.markdownComposing || !enabled() || version != area.editor.contentVersion) return
        val next = selectedGroups(state)
        if (next == expanded) return
        val previous = expanded.flatMap { state.syntaxGroups[it].markers }
        val desired = next.flatMap { state.syntaxGroups[it].markers }
        area.suspendVisibleParsWhileInvoke {
            changeExpanded(subtract(previous, desired), false)
            changeExpanded(subtract(desired, previous), true)
        }
        expanded = next
        area.markdownPresentation.refreshFrontMatterDelimiters()
        area.requestLayout()
        if (area.isFocused) area.requestFollowCaret()
    }

    private fun selectedGroups(state: MarkdownStructureSnapshot): Set<Int> {
        val start = area.selection.start
        val end = area.selection.end
        val next = HashSet<Int>()
        if (area.isFocused || start != end) {
            for (line in state.lineAt(start)..state.lineAt(end)) {
                groupsByLine[line]?.forEach { index ->
                    val group = state.syntaxGroups[index]
                    if (if (start == end) start in group.start..group.end else start < group.end && end > group.start) next.add(index)
                }
            }
        }
        return next
    }

    private fun visibleOffset(position: Int): Int {
        val state = snapshot ?: return position
        if (!enabled() || version != area.editor.contentVersion) return position
        var result = position
        groupsByLine[state.lineAt(position)]?.forEach { index ->
            val group = state.syntaxGroups[index]
            for (marker in group.markers) {
                if (result !in marker.start..marker.end) continue
                val styles = area.getStyleOfChar(marker.start)
                if (COLLAPSIBLE !in styles || EXPANDED in styles || "search" in styles || "temporary" in styles) continue
                if ("markdown-emoji" in styles) {
                    // 表情本身可见：左边界保留短码起点，右侧命中覆盖完整短码，拖选才能复制整颗表情。
                    if (styles.any { it.startsWith(CodeArea.MARKDOWN_EMOJI_GLYPH_PREFIX) }) {
                        result = if (result == marker.start) marker.start else marker.end
                    }
                    continue
                }
                // 零宽边界可能命中标记内部：前缀映射到正文起点，后缀映射到正文终点。
                result = if (marker.start != group.start && marker.end == group.end) marker.start else marker.end
            }
        }
        return result
    }

    private fun styledRanges(style: String): List<MarkdownStructureSnapshot.Range> {
        if (area.length == 0) return emptyList()
        var offset = 0
        val ranges = ArrayList<MarkdownStructureSnapshot.Range>()
        for (span in area.getStyleSpans(0, area.length)) {
            if (style in span.style && span.length > 0) ranges.add(MarkdownStructureSnapshot.Range(offset, offset + span.length))
            offset += span.length
        }
        return ranges
    }

    private fun changeExpanded(ranges: List<MarkdownStructureSnapshot.Range>, reveal: Boolean) {
        if (ranges.isEmpty()) return
        for (range in mergeRanges(ranges)) {
            val builder = StyleSpansBuilder<Collection<String>>()
            var changed = false
            for (span in area.getStyleSpans(range.start, range.end)) {
                val old = span.style
                val styles = if (reveal && COLLAPSIBLE in old) {
                    if (EXPANDED in old) old else (old + EXPANDED).toSet()
                } else if (EXPANDED in old) old.filterNot { it == EXPANDED }.toSet() else old
                if (old != styles) changed = true
                builder.add(styles, span.length)
            }
            if (changed) area.setStyleSpans(range.start, builder.create())
        }
    }

    private fun mergeRanges(ranges: List<MarkdownStructureSnapshot.Range>): List<MarkdownStructureSnapshot.Range> {
        val merged = ArrayList<MarkdownStructureSnapshot.Range>()
        for (range in ranges.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && range.start <= last.end) {
                merged[merged.lastIndex] = MarkdownStructureSnapshot.Range(last.start, Math.max(last.end, range.end))
            } else merged.add(range)
        }
        return merged
    }

    private fun subtract(
        ranges: List<MarkdownStructureSnapshot.Range>, excluded: List<MarkdownStructureSnapshot.Range>
    ): List<MarkdownStructureSnapshot.Range> {
        val result = ArrayList<MarkdownStructureSnapshot.Range>()
        val masks = mergeRanges(excluded)
        var index = 0
        for (range in mergeRanges(ranges)) {
            var start = range.start
            while (index < masks.size && masks[index].end <= start) index++
            var cursor = index
            while (cursor < masks.size && masks[cursor].start < range.end) {
                val mask = masks[cursor++]
                if (mask.start > start) result.add(MarkdownStructureSnapshot.Range(start, mask.start))
                start = Math.max(start, mask.end)
            }
            if (start < range.end) result.add(MarkdownStructureSnapshot.Range(start, range.end))
        }
        return result
    }

    fun clear() {
        if (disposed) return
        changeExpanded(styledRanges(COLLAPSIBLE), true)
        snapshot = null
        groupsByLine = emptyArray()
        expanded = emptySet()
        version = -1
    }

    fun destroy() {
        disposed = true
        afterPointer.clear()
        area.caretPositionProperty().removeListener(changed)
        area.selectionProperty().removeListener(changed)
        area.focusedProperty().removeListener(changed)
        area.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressed)
        area.removeEventFilter(MouseEvent.MOUSE_RELEASED, released)
        snapshot = null
        groupsByLine = emptyArray()
    }

    companion object {
        const val COLLAPSIBLE = "markdown-syntax-collapsible"
        const val EXPANDED = "markdown-syntax-expanded"

        /** 高亮只比较语法样式，光标产生的展开状态由界面独立维护。可在后台处理不可变快照。 */
        @JvmStatic
        fun highlightingStyles(spans: StyleSpans<Collection<String>>): StyleSpans<Collection<String>> =
            spans.mapStyles { styles -> if (EXPANDED in styles) styles.filterNot { it == EXPANDED }.toSet() else styles }
    }
}

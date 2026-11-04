package com.allan.atools.richtext.codearea

import com.allan.uilibs.richtexts.CodeArea
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.layout.Region
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.fxmisc.flowless.VirtualFlow

/** 只读取虚拟列表已挂载的段落节点，段落排版和图标查询不触发布局。 */
object MarkdownVisibleParagraphs {
    fun flow(area: CodeArea): VirtualFlow<*, *>? =
        area.childrenUnmodifiable.firstOrNull { it is VirtualFlow<*, *> } as? VirtualFlow<*, *>

    fun boxes(flow: VirtualFlow<*, *>?): List<Region> {
        // Flowless 0.7.4 的 visibleCells() 会先调用 layout()；Navigator 的直接子节点就是已挂载的段落。
        val navigator = flow?.childrenUnmodifiable?.firstOrNull { it is Parent } as? Parent ?: return emptyList()
        return navigator.childrenUnmodifiable.filterIsInstance<Region>()
            .filter { "paragraph-box" in it.styleClass }
    }

    fun texts(area: CodeArea): List<Text> = buildList {
        for (box in boxes(flow(area))) {
            val paragraph = box.childrenUnmodifiable.firstOrNull { it is TextFlow } as? TextFlow
            if (paragraph == null) {
                // 表格预览行用自定义段落图形替换源码段落，收集其中单元格 TextFlow 的文本节点。
                // 表格模式下隐藏的源码段落带 gutter 样式类，跳过后不会重复处理源码表情。
                box.childrenUnmodifiable.forEach { collectTableTexts(it, this) }
                continue
            }
            paragraph.childrenUnmodifiable.forEach { if (it is Text) add(it) }
        }
    }

    private fun collectTableTexts(node: Node, into: MutableList<Text>) {
        if ("markdown-table-line-gutter" in node.styleClass) return
        if (node is TextFlow) {
            node.childrenUnmodifiable.forEach { if (it is Text) into.add(it) }
            return
        }
        if (node is Parent) node.childrenUnmodifiable.forEach { collectTableTexts(it, into) }
    }
}

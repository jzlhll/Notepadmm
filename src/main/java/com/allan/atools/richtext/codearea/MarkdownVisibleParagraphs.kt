package com.allan.atools.richtext.codearea

import com.allan.uilibs.richtexts.CodeArea
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.layout.Region
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.scene.web.WebView
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
            box.childrenUnmodifiable.forEach { collectTexts(it, this) }
        }
    }

    private fun collectTexts(node: Node, into: MutableList<Text>) {
        // 源码 TextFlow 仍在段落内，按实际可见性跳过；控件内部文本由自身皮肤管理。
        if (!node.isVisible || node.opacity == 0.0 || node is Control || node is WebView) return
        if (node is TextFlow) {
            // 高清覆盖会隐藏原 Text，原节点仍需参与后续刷新，不能按其 opacity 过滤。
            node.childrenUnmodifiable.forEach { if (it is Text) into.add(it) }
            return
        }
        if (node is Parent) node.childrenUnmodifiable.forEach { collectTexts(it, into) }
    }
}

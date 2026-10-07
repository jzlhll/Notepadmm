package com.allan.atools.richtext.codearea

import com.allan.uilibs.richtexts.CodeArea
import javafx.scene.layout.Region
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.fxmisc.flowless.VirtualFlow

/** 直接读取虚拟列表的可见单元格，段落排版和图标共用入口，不递归搜索编辑器节点树。 */
object MarkdownVisibleParagraphs {
    fun flow(area: CodeArea): VirtualFlow<*, *>? =
        area.childrenUnmodifiable.firstOrNull { it is VirtualFlow<*, *> } as? VirtualFlow<*, *>

    fun boxes(flow: VirtualFlow<*, *>?): List<Region> =
        flow?.visibleCells()?.mapNotNull { it.node as? Region }.orEmpty()

    fun texts(area: CodeArea): List<Text> = buildList {
        for (box in boxes(flow(area))) {
            val paragraph = box.childrenUnmodifiable.firstOrNull { it is TextFlow } as? TextFlow ?: continue
            paragraph.childrenUnmodifiable.forEach { if (it is Text) add(it) }
        }
    }
}

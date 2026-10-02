package com.allan.atools.tools.modulenotepad.bottom

import com.allan.atools.Colors
import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorScrollPane
import com.allan.atools.richtext.codearea.MarkdownEditorSupport
import com.allan.atools.ui.IconfontCreator
import com.allan.atools.utils.Locales
import javafx.beans.value.ChangeListener
import javafx.scene.control.Tooltip
import kotlin.math.roundToInt

/** 底部 Markdown 缩放按钮及当前标签页的比例提示。 */
object BottomMarkdownZoom {
    private var observedPane: EditorScrollPane? = null
    private val zoomChanged = ChangeListener<Number> { _, _, _ -> refresh() }

    @JvmStatic
    fun init() {
        val main = UIContext.context()
        main.markdownZoomOutBtn.tooltip = Tooltip()
        main.markdownZoomInBtn.tooltip = Tooltip()
        main.markdownZoomBox.managedProperty().bind(main.markdownZoomBox.visibleProperty())
        main.markdownZoomOutBtn.setOnAction {
            (UIContext.currentTabProp.get()?.content as? EditorScrollPane)?.changeZoom(-10)
        }
        main.markdownZoomInBtn.setOnAction {
            (UIContext.currentTabProp.get()?.content as? EditorScrollPane)?.changeZoom(10)
        }
        UIContext.currentAreaProp.addListener { _, _, _ -> refresh() }
        refresh()
    }

    @JvmStatic
    fun refreshSize() {
        val main = UIContext.context()
        IconfontCreator.setText(main.markdownZoomOutIcon, "suoxiao",
            main.getMainBottomSize(20), Colors.ColorBottomBtnNormal)
        IconfontCreator.setText(main.markdownZoomInIcon, "fangda",
            main.getMainBottomSize(20), Colors.ColorBottomBtnNormal)
    }

    @JvmStatic
    fun refresh() {
        val main = UIContext.context()
        val pane = UIContext.currentTabProp.get()?.content as? EditorScrollPane
        if (observedPane !== pane) {
            observedPane?.zoomPercentProperty()?.removeListener(zoomChanged)
            observedPane = pane
            pane?.zoomPercentProperty()?.addListener(zoomChanged)
        }
        val available = pane != null && MarkdownEditorSupport.supportsMarkdown(pane.editorArea)
        main.markdownZoomBox.isVisible = available
        if (!available) {
            pane?.resetZoom()
            return
        }
        val percent = pane!!.zoomPercent
        main.markdownZoomOutBtn.isDisable = percent <= EditorScrollPane.MIN_ZOOM_PERCENT
        main.markdownZoomInBtn.isDisable = percent >= EditorScrollPane.MAX_ZOOM_PERCENT
        val displayedPercent = percent.roundToInt()
        main.markdownZoomOutBtn.tooltip?.text = "${Locales.str("zoomSmall")} ($displayedPercent%)"
        main.markdownZoomInBtn.tooltip?.text = "${Locales.str("zoomBig")} ($displayedPercent%)"
        main.markdownZoomOutBtn.accessibleText = Locales.str("zoomSmall")
        main.markdownZoomInBtn.accessibleText = Locales.str("zoomBig")
    }
}

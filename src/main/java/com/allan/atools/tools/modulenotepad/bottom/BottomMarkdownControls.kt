package com.allan.atools.tools.modulenotepad.bottom

import com.allan.atools.UIContext
import com.allan.atools.pop.MenuOverlay
import com.allan.atools.pop.impl.MarkdownZoomChooseCreatorImpl
import com.allan.atools.pop.showPopupContent
import com.allan.atools.richtext.codearea.EditorScrollPane
import com.allan.atools.richtext.codearea.MarkdownEditorSupport
import com.allan.atools.utils.Locales
import javafx.beans.value.ChangeListener
import javafx.geometry.Side
import javafx.scene.control.Tooltip
import kotlin.math.roundToInt

/** 底部 Markdown 显示模式、缩放浮层及当前标签页的状态同步。 */
object BottomMarkdownControls {
    private var observedPane: EditorScrollPane? = null
    private var zoomOverlay: MenuOverlay? = null
    private val zoomChanged = ChangeListener<Number> { _, _, _ -> refresh() }

    @JvmStatic
    fun init() {
        val main = UIContext.context()
        main.markdownZoomBtn.tooltip = Tooltip(Locales.str("zoom"))
        main.markdownControlsBox.managedProperty().bind(main.markdownControlsBox.visibleProperty())
        main.markdownSourceCheckBtn.tooltip = Tooltip(Locales.str("markdown.toggleSource"))
        main.markdownSourceCheckBtn.setOnMouseClicked {
            val area = UIContext.currentAreaProp.get() ?: return@setOnMouseClicked
            if (!MarkdownEditorSupport.supportsMarkdown(area)) return@setOnMouseClicked
            area.runAfterMarkdownComposition {
                if (UIContext.currentAreaProp.get() === area && !area.editor.isDestroyed()) {
                    area.toggleMarkdownPreview()
                    area.requestFocus()
                }
            }
        }
        main.markdownZoomBtn.setOnMouseClicked {
            if (zoomOverlay?.isOpen == true) {
                zoomOverlay?.close()
                return@setOnMouseClicked
            }
            val pane = observedPane ?: return@setOnMouseClicked
            if (!MarkdownEditorSupport.supportsMarkdown(pane.editorArea)) return@setOnMouseClicked
            val content = MarkdownZoomChooseCreatorImpl(pane.zoomPercent).createPop { percent ->
                if (UIContext.currentAreaProp.get() === pane.editorArea && !pane.editorArea.editor.isDestroyed()
                    && MarkdownEditorSupport.supportsMarkdown(pane.editorArea)) {
                    pane.setZoomPercent(percent.toDouble())
                }
            }
            zoomOverlay = showPopupContent(main.markdownZoomBtn, content, Side.TOP,
                onHidden = Runnable { zoomOverlay = null })
        }
        UIContext.currentAreaProp.addListener { _, _, _ -> refresh() }
        refresh()
    }

    @JvmStatic
    fun refresh() {
        val main = UIContext.context()
        val pane = UIContext.currentTabProp.get()?.content as? EditorScrollPane
        if (observedPane !== pane) {
            zoomOverlay?.close()
            observedPane?.zoomPercentProperty()?.removeListener(zoomChanged)
            observedPane = pane
            pane?.zoomPercentProperty()?.addListener(zoomChanged)
        }
        val available = pane != null && MarkdownEditorSupport.supportsMarkdown(pane.editorArea)
        main.markdownControlsBox.isVisible = available
        if (!available) {
            zoomOverlay?.close()
            pane?.resetZoom()
            return
        }
        val currentPane = pane!!
        main.changeBottomTextBtnCheckStyle(main.markdownSourceCheckBtn, !currentPane.editorArea.markdownPreviewEnabled)
        val displayedPercent = currentPane.zoomPercent.roundToInt()
        main.markdownZoomBtn.tooltip?.text = "${Locales.str("zoom")} ($displayedPercent%)"
        main.markdownZoomBtn.accessibleText = "${Locales.str("zoom")} ($displayedPercent%)"
    }
}

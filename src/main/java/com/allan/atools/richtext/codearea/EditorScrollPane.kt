package com.allan.atools.richtext.codearea

import com.allan.uilibs.richtexts.MyVirtualScrollPane
import com.allan.atools.utils.ResLocation
import javafx.beans.InvalidationListener
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.ReadOnlyDoubleWrapper
import javafx.beans.property.SimpleDoubleProperty
import javafx.scene.input.ScrollEvent
import javafx.scene.input.ZoomEvent
import javafx.scene.layout.Region
import javafx.scene.shape.Rectangle
import javafx.scene.transform.Scale
import org.fxmisc.flowless.Virtualized
import org.reactfx.Subscription
import org.reactfx.value.Val
import org.reactfx.value.Var
import kotlin.math.abs
import kotlin.math.exp

/** 编辑器缩放容器，滚动条保持原尺寸，Markdown 内容整体缩放。 */
class EditorScrollPane(val editorArea: EditorArea) :
    MyVirtualScrollPane<EditorZoomViewport>(EditorZoomViewport(editorArea)) {

    override fun removeContent(): EditorZoomViewport {
        content.clearEmojiRendering()
        return super.removeContent()
    }

    private val zoomPercentState = ReadOnlyDoubleWrapper(this, "zoomPercent", 100.0)
    val zoomPercent: Double
        get() = zoomPercentState.get()

    fun zoomPercentProperty(): ReadOnlyDoubleProperty = zoomPercentState.readOnlyProperty

    init {
        addEventFilter(ZoomEvent.ZOOM) { event ->
            if (!MarkdownEditorSupport.supportsMarkdown(editorArea)) return@addEventFilter
            if (event.zoomFactor.isFinite() && event.zoomFactor > 0.0) {
                setZoomPercent(zoomPercent * event.zoomFactor)
            }
            event.consume()
        }
        addEventFilter(ScrollEvent.SCROLL) { event ->
            // 兼容 Windows 将触控板捏合转换为 Ctrl + 滚轮的输入方式。
            if (!ResLocation.isWindow || !event.isControlDown || event.isAltDown || event.isMetaDown
                || event.isShiftDown || event.deltaY == 0.0
                || !MarkdownEditorSupport.supportsMarkdown(editorArea)
            ) return@addEventFilter
            setZoomPercent(zoomPercent * exp(event.deltaY * 0.0025))
            event.consume()
        }
    }

    companion object {
        const val MIN_ZOOM_PERCENT = 100
        const val MAX_ZOOM_PERCENT = 250
    }

    fun changeZoom(delta: Int) {
        if (!MarkdownEditorSupport.supportsMarkdown(editorArea)) return
        setZoomPercent(zoomPercent + delta)
    }

    private fun setZoomPercent(target: Double) {
        if (!target.isFinite()) return
        val percent = when {
            target < MIN_ZOOM_PERCENT -> MIN_ZOOM_PERCENT.toDouble()
            target > MAX_ZOOM_PERCENT -> MAX_ZOOM_PERCENT.toDouble()
            else -> target
        }
        if (percent == zoomPercent) return
        content.setZoom(percent / 100.0)
        zoomPercentState.set(percent)
    }

    fun resetZoom() {
        setZoomPercent(MIN_ZOOM_PERCENT.toDouble())
    }
}

/** 将缩放后的虚拟文档尺寸和滚动位置映射到编辑器坐标。 */
class EditorZoomViewport(private val area: EditorArea) : Region(), Virtualized {
    private val zoom = SimpleDoubleProperty(1.0)
    // 留白使用滚动条占位前的尺寸，滚动条只改变正文视口，不参与留白计算。
    val documentLayoutWidth: Double
        get() = (parent as? EditorScrollPane)?.width ?: width

    val documentLayoutHeight: Double
        get() = ((parent as? EditorScrollPane)?.height ?: height) / zoom.get()

    private val panX = Var.newSimpleVar(0.0)
    private val scale = Scale(1.0, 1.0, 0.0, 0.0)
    private val clipRect = Rectangle().apply {
        widthProperty().bind(this@EditorZoomViewport.widthProperty())
        heightProperty().bind(this@EditorZoomViewport.heightProperty())
    }
    private val emojiRendering = MarkdownEmojiRendering(area)
    private val taskRendering = MarkdownTaskRendering(area)
    private var decorationsDirty = true
    private var decorationChanges: Subscription? = null
    private val decorationsChanged = InvalidationListener { decorationsDirty = true }
    private val emojiPulse = Runnable {
        if (decorationsDirty) {
            decorationsDirty = false
            if (MarkdownEditorSupport.supportsMarkdown(area)) {
                area.refreshMarkdownParagraphWrapping()
                val texts = MarkdownVisibleParagraphs.texts(area)
                emojiRendering.refresh(texts)
                taskRendering.refresh(texts)
            } else {
                emojiRendering.clear()
                taskRendering.clear()
            }
        }
    }
    private val totalWidth: Val<Double> = Val.combine(
        area.totalWidthEstimateProperty(), area.widthProperty(), area.insetsProperty(), zoom
    ) { textWidth, areaWidth, insets, factor ->
        // 自动换行时原生内容宽度可以小于视口，不能用编辑区宽度补齐：
        // 纵向滚动条出现前的旧宽度会误触发横向滚动条，反复改变换行和行高。
        val contentWidth = textWidth + insets.left + insets.right
        val width = if (factor.toDouble() > 1.0) Math.max(areaWidth.toDouble(), contentWidth) else contentWidth
        width * factor.toDouble()
    }
    // RichTextFX 的文档高度不含编辑器内边距，滚动范围需补齐上下留白。
    private val totalHeight: Val<Double> = Val.combine(
        area.totalHeightEstimateProperty(), area.insetsProperty(), zoom
    ) { height, insets, factor ->
        (height + insets.top + insets.bottom) * factor.toDouble()
    }
    private val scrollX: Var<Double> = Val.combine(area.estimatedScrollXProperty(), panX, zoom) {
            offset, pan, factor -> offset * factor.toDouble() + pan
    }.asVar { scrollXToPixel(it) }
    private val scrollY: Var<Double> = Val.combine(area.estimatedScrollYProperty(), zoom) {
            offset, factor -> offset * factor.toDouble()
    }.asVar { scrollYToPixel(it) }

    init {
        styleClass.add("editor-zoom-viewport")
        children.addAll(area, emojiRendering, taskRendering)
        area.needsLayoutProperty().addListener(decorationsChanged)
        area.visibleParagraphs.addListener(decorationsChanged)
        area.estimatedScrollXProperty().addListener(decorationsChanged)
        area.estimatedScrollYProperty().addListener(decorationsChanged)
        area.editableProperty().addListener(decorationsChanged)
        area.visibleProperty().addListener(decorationsChanged)
        area.opacityProperty().addListener(decorationsChanged)
        // 布局完成后再取字形位置，避免在 TextFlow 排版中途生成快照。
        sceneProperty().addListener { _, previous, current ->
            previous?.removePostLayoutPulseListener(emojiPulse)
            decorationChanges?.unsubscribe()
            decorationChanges = null
            decorationsDirty = true
            current?.addPostLayoutPulseListener(emojiPulse)
            if (current != null) decorationChanges = area.richChanges().subscribe { decorationsDirty = true }
            if (current == null) {
                emojiRendering.clear()
                taskRendering.clear()
            }
        }
        backgroundProperty().bind(area.backgroundProperty())
        area.isManaged = false
        clip = clipRect
        applyZoomRendering(zoom.get())
        panX.addListener { _, _, value -> area.layoutX = -value }
        addEventFilter(ScrollEvent.SCROLL) { event ->
            if (zoom.get() <= 1.0) return@addEventFilter
            val horizontal = if (event.isShiftDown && event.deltaX == 0.0) event.deltaY else event.deltaX
            if (horizontal != 0.0 && (event.isShiftDown || abs(horizontal) >= abs(event.deltaY))) {
                scrollXBy(-horizontal)
                event.consume()
            }
        }
    }

    fun setZoom(factor: Double) {
        if (zoom.get() == factor) return
        val offset = scrollX.value / zoom.get() * factor
        zoom.set(factor)
        applyZoomRendering(factor)
        requestLayout()
        scrollXToPixel(offset)
    }

    /** 仅在放大时挂载缩放变换，100% 直接透传原生渲染路径。 */
    private fun applyZoomRendering(factor: Double) {
        if (factor > 1.0) {
            scale.x = factor
            scale.y = factor
            if (!area.transforms.contains(scale)) area.transforms.add(scale)
        } else {
            area.transforms.remove(scale)
            panX.value = 0.0
        }
    }

    override fun layoutChildren() {
        val factor = zoom.get()
        // 放大时保留原排版宽度，超出视口的部分交给外层横向滚动。
        area.resize(width, height / factor)
        if (factor > 1.0) scrollXToPixel(scrollX.value)
    }

    fun clearEmojiRendering() {
        emojiRendering.clear()
        taskRendering.clear()
    }

    fun followCaret() {
        if (zoom.get() <= 1.0 || width <= 0.0 || scene == null) return
        val caret = area.caretSelectionBind.underlyingCaret
        if (caret.scene !== scene || caret.parent == null) return
        // 直接转换光标节点坐标，包含外层平移和缩放，避免使用旧的屏幕边界缓存。
        val bounds = sceneToLocal(caret.localToScene(caret.boundsInLocal)) ?: return
        val margin = if (width > 16.0) 8.0 else 0.0
        val delta = when {
            bounds.minX < margin -> bounds.minX - margin
            bounds.maxX > width - margin -> bounds.maxX - width + margin
            else -> return
        }
        scrollXBy(delta)
    }

    override fun totalWidthEstimateProperty(): Val<Double> = totalWidth
    override fun totalHeightEstimateProperty(): Val<Double> = totalHeight
    override fun estimatedScrollXProperty(): Var<Double> = scrollX
    override fun estimatedScrollYProperty(): Var<Double> = scrollY

    override fun scrollXBy(deltaX: Double) = scrollXToPixel(scrollX.value + deltaX)
    override fun scrollYBy(deltaY: Double) = area.scrollYBy(deltaY / zoom.get())

    override fun scrollXToPixel(pixel: Double) {
        val factor = zoom.get()
        val maxOffset = Math.max(0.0, totalWidth.value - width)
        val offset = Math.max(0.0, Math.min(pixel, maxOffset))
        val pan = Math.min(offset, Math.max(0.0, area.width * factor - width))
        panX.value = pan
        area.estimatedScrollXProperty().value = (offset - pan) / factor
    }

    override fun scrollYToPixel(pixel: Double) {
        area.estimatedScrollYProperty().value = pixel / zoom.get()
    }
}

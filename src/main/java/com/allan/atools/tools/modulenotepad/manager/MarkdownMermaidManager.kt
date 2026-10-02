package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.Colors
import com.allan.atools.SettingPreferences
import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorScrollPane
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.richtext.codearea.MarkdownEditorSupport
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.uilibs.richtexts.CodeArea
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.HBox
import javafx.stage.Popup
import javafx.util.Duration
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.FencedCodeBlock
import org.fxmisc.richtext.model.TwoDimensional.Bias.Forward
import org.reactfx.Subscription
import java.util.WeakHashMap
import java.util.concurrent.Future
import kotlin.math.ceil

/** Mermaid 围栏代码块的离线预览、源码切换和虚拟段落生命周期管理。 */
class MarkdownMermaidManager(area: EditorArea?) {
    private class Diagram(
        var firstLine: Int,
        var lastLine: Int,
        val source: String,
        val closed: Boolean,
        var showSource: Boolean = false,
        var error: String? = null,
        var rendered: MermaidDiagramView.Rendered? = null
    )

    private var currentArea: EditorArea? = null
    private var diagrams = listOf<Diagram>()
    private val saved = WeakHashMap<EditorArea, List<Diagram>>()
    private val views = mutableSetOf<MermaidDiagramView>()
    private var textChanges: Subscription? = null
    private var viewportChanges: Subscription? = null
    private var parseTask: Future<*>? = null
    private var destroyed = false
    private var updating = false
    private var presentationActive = false
    private var layoutPending = false
    private var parsedVersion = -1L
    private var hoverDiagram: Diagram? = null
    private var activeDiagram: Diagram? = null
    private var toolbarDiagram: Diagram? = null
    private val modeButton = Button()
    private val errorLabel = Label(Locales.str("markdownMermaidFailed"))
    private val toolbar = HBox(4.0, modeButton, errorLabel)
    private val toolbarPopup = Popup()
    private val hideDelay = PauseTransition(Duration.millis(180.0))
    private val mouseMoved = EventHandler<MouseEvent> { event ->
        val area = currentArea
        if (area != null && presentationActive) {
            val line = area.hit(event.x, event.y).insertionIndex.let {
                area.offsetToPosition(it, Forward).major
            }
            hoverDiagram = diagrams.firstOrNull { line in it.firstLine..it.lastLine }
            updateToolbar()
        }
    }
    private val mouseExited = EventHandler<MouseEvent> {
        hoverDiagram = null
        updateToolbar()
    }
    private val mousePressed = EventHandler<MouseEvent> { event ->
        activeDiagram = null
        mouseMoved.handle(event)
    }
    private val scheduler = LatestRefreshScheduler(250, 600, ::parse)
    private val layoutChanged = InvalidationListener { requestLayout() }
    private val viewportTransformChanged = InvalidationListener {
        hoverDiagram = null
        hideToolbar()
    }
    private val themeChanged = InvalidationListener {
        saved.values.forEach { list -> list.forEach { it.rendered = null } }
        diagrams.forEach { it.rendered = null }
        refreshGraphics()
    }
    private val caretChanged = InvalidationListener {
        val editor = currentArea
        if (!updating && editor != null) {
            activeDiagram = null
            updateToolbar()
        }
    }

    init {
        toolbar.styleClass.add("markdown-table-toolbar")
        toolbar.padding = Insets(4.0)
        modeButton.styleClass.add("markdown-table-toolbar-button")
        modeButton.setOnAction {
            val diagram = toolbarDiagram
            val editor = currentArea
            if (diagram != null && editor != null && diagrams.contains(diagram)) {
                if (diagram.showSource && (!diagram.closed || parsedVersion != editor.editor.contentVersion)) {
                    return@setOnAction
                }
                diagram.showSource = !diagram.showSource
                diagram.error = null
                hoverDiagram = diagram
                activeDiagram = diagram
                refreshGraphics(diagram)
                if (diagram.showSource) {
                    editor.moveTo(Math.min(diagram.firstLine + 1, editor.paragraphs.size - 1), 0)
                    editor.requestFocus()
                }
                updateToolbar()
            }
        }
        toolbar.setOnMouseEntered { hideDelay.stop() }
        toolbar.setOnMouseExited {
            hoverDiagram = null
            updateToolbar()
        }
        hideDelay.setOnFinished { if (!toolbar.isHover) hideToolbar() }
        toolbarPopup.isAutoFix = true
        toolbarPopup.isAutoHide = false
        toolbarPopup.content.add(toolbar)
        SettingPreferences.getBoolProp(SettingPreferences.appVisionKey).addListener(themeChanged)
        UIContext.getFontSizeProperty().addListener(layoutChanged)
        refreshCurrentFile(area)
    }

    fun refreshCurrentFile(area: EditorArea?) {
        if (area === currentArea && MarkdownEditorSupport.supportsMarkdown(area)) return
        unbind()
        if (destroyed || !MarkdownEditorSupport.supportsMarkdown(area)) return
        currentArea = area
        area ?: return
        diagrams = saved[area].orEmpty()
        area.addParagraphGraphicDecorator(this, ::createGraphic)
        area.widthProperty().addListener(layoutChanged)
        area.localToSceneTransformProperty().addListener(viewportTransformChanged)
        area.paddingProperty().addListener(layoutChanged)
        area.caretPositionProperty().addListener(caretChanged)
        area.focusedProperty().addListener(caretChanged)
        area.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMoved)
        area.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExited)
        area.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed)
        viewportChanges = area.viewportDirtyEvents().subscribe {
            hoverDiagram = null
            hideToolbar()
        }
        textChanges = area.plainTextChanges().subscribe { change ->
            parsedVersion = -1
            val first = area.offsetToPosition(change.position, Forward).major
            val removed = change.removed.count { it == '\n' }
            val inserted = change.inserted.count { it == '\n' }
            val difference = inserted - removed
            // 段落样式随正文移动；仅展开被修改的图表，其他图表继续使用已有预览。
            diagrams.forEach { diagram ->
                if (diagram.firstLine > first + removed) {
                    diagram.firstLine += difference
                    diagram.lastLine += difference
                } else if (diagram.lastLine >= first) {
                    diagram.firstLine = Math.min(diagram.firstLine, first)
                    diagram.lastLine = Math.max(first + inserted, diagram.lastLine + difference)
                    diagram.showSource = true
                    diagram.error = null
                    diagram.rendered = null
                }
            }
            if (area.editor.isRealtimeProcessingLimitReached) clearPresentation() else refreshGraphics()
            scheduler.request()
        }
        scheduler.startNow()
    }

    private fun unbind() {
        scheduler.invalidate()
        parseTask?.cancel(true)
        parseTask = null
        textChanges?.unsubscribe()
        textChanges = null
        viewportChanges?.unsubscribe()
        viewportChanges = null
        hoverDiagram = null
        activeDiagram = null
        parsedVersion = -1
        hideToolbar()
        val area = currentArea ?: return
        saved[area] = diagrams
        clearPresentation()
        area.removeParagraphGraphicDecorator(this)
        area.widthProperty().removeListener(layoutChanged)
        area.localToSceneTransformProperty().removeListener(viewportTransformChanged)
        area.paddingProperty().removeListener(layoutChanged)
        area.caretPositionProperty().removeListener(caretChanged)
        area.focusedProperty().removeListener(caretChanged)
        area.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMoved)
        area.removeEventHandler(MouseEvent.MOUSE_EXITED, mouseExited)
        area.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed)
        currentArea = null
        diagrams = emptyList()
    }

    fun destroy() {
        destroyed = true
        unbind()
        scheduler.dispose()
        saved.clear()
        toolbarPopup.content.clear()
        SettingPreferences.getBoolProp(SettingPreferences.appVisionKey).removeListener(themeChanged)
        UIContext.getFontSizeProperty().removeListener(layoutChanged)
    }

    private fun parse(requestId: Long) {
        val area = currentArea
        if (destroyed || area == null || area.editor.isRealtimeProcessingLimitReached) {
            clearPresentation()
            scheduler.complete(requestId, null)
            return
        }
        val version = area.editor.contentVersion
        val text = area.text
        parseTask = ThreadUtils.submit {
            val parsed = mutableListOf<Diagram>()
            try {
                (area.editor as EditorAreaMgrCode).parseMarkdown(text).accept(object : AbstractVisitor() {
                    override fun visit(block: FencedCodeBlock) {
                        if (!block.info.trim().equals("mermaid", ignoreCase = true)) return
                        val spans = block.sourceSpans
                        if (spans.isEmpty()) return
                        // 仅接管流程图、时序图；其他 Mermaid 类型仍按普通源码呈现。
                        val declaration = block.literal.lineSequence().map { it.trim() }
                            .firstOrNull { it.isNotEmpty() && !it.startsWith("%%") }.orEmpty()
                        if (!Regex("^(flowchart|graph)(?:\\s|$)|^sequenceDiagram(?:\\s|$)")
                                .containsMatchIn(declaration)) return
                        val closed = block.closingFenceLength != null
                        parsed.add(Diagram(spans.first().lineIndex, spans.last().lineIndex,
                            block.literal, closed, showSource = !closed))
                    }
                })
            } catch (error: RuntimeException) {
                Log.e("Parse markdown Mermaid failed", error)
            }
            Platform.runLater {
                scheduler.complete(requestId) {
                    parseTask = null
                    if (!destroyed && currentArea === area && area.editor.contentVersion == version) {
                        val previous = diagrams.associateBy { it.firstLine }
                        val hoveredLine = hoverDiagram?.firstLine
                        val activeLine = activeDiagram?.firstLine
                        clearPresentation()
                        diagrams = parsed.map { diagram ->
                            previous[diagram.firstLine]?.let { old ->
                                diagram.showSource = old.showSource || !diagram.closed
                                if (old.source == diagram.source) {
                                    diagram.rendered = old.rendered
                                    diagram.error = old.error
                                }
                            }
                            diagram
                        }
                        parsedVersion = version
                        hoverDiagram = diagrams.firstOrNull { it.firstLine == hoveredLine }
                        activeDiagram = diagrams.firstOrNull { it.firstLine == activeLine }
                        refreshGraphics()
                    }
                }
            }
        }
    }

    private fun requestLayout() {
        if (layoutPending || destroyed) return
        layoutPending = true
        Platform.runLater {
            layoutPending = false
            if (!destroyed) refreshGraphics()
        }
    }

    private fun refreshGraphics(target: Diagram? = null) {
        val area = currentArea ?: return
        if (area.editor.isRealtimeProcessingLimitReached) return
        presentationActive = true
        updating = true
        try {
            area.suspendVisibleParsWhileInvoke {
                (if (target == null) diagrams else listOf(target)).forEach { diagram ->
                    for (line in diagram.firstLine..diagram.lastLine) {
                        val style = if (!diagram.showSource) {
                            CodeArea.MERMAID_PREVIEW_HEIGHT_PREFIX +
                                if (line == diagram.firstLine) previewHeight(diagram) + 16.0 else 0.0
                        } else null
                        setStyle(line, style)
                        if (line in 0 until area.paragraphs.size) area.recreateParagraphGraphic(line)
                    }
                }
            }
        } finally {
            updating = false
        }
        updateToolbar()
    }

    private fun setStyle(line: Int, height: String?) {
        val area = currentArea ?: return
        if (line !in 0 until area.paragraphs.size) return
        val previous = area.getParagraph(line).paragraphStyle
        val styles = previous.filterNot {
            it.startsWith(CodeArea.MERMAID_PREVIEW_HEIGHT_PREFIX)
        }.toMutableList()
        if (height != null) styles.add(height)
        if (styles != previous.toList()) area.setParagraphStyle(line, styles)
    }

    private fun clearPresentation() {
        val area = currentArea ?: return
        presentationActive = false
        hideToolbar()
        updating = true
        try {
            views.toList().forEach { it.dispose() }
            views.clear()
            area.suspendVisibleParsWhileInvoke {
                diagrams.forEach { diagram ->
                    for (line in diagram.firstLine..diagram.lastLine) {
                        setStyle(line, null)
                        if (line in 0 until area.paragraphs.size) area.recreateParagraphGraphic(line)
                    }
                }
            }
        } finally {
            updating = false
        }
    }

    private fun availableWidth(): Double {
        val area = currentArea ?: return 400.0
        val graphic = area.lookup(".lineno")
        return Math.max(120.0, area.width - area.insets.left - area.insets.right -
            (graphic?.prefWidth(-1.0) ?: 0.0) - MarkdownEditorSupport.textLeftPadding(area))
    }

    private fun previewHeight(diagram: Diagram): Double {
        val rendered = diagram.rendered ?: return 180.0
        val width = Math.min(availableWidth(), rendered.width)
        return ceil(width * rendered.height / rendered.width)
    }

    private fun createGraphic(line: Int, base: Node?): Node? {
        val area = currentArea ?: return base
        if (!presentationActive) return base
        val diagram = diagrams.firstOrNull { line in it.firstLine..it.lastLine } ?: return base
        if (!diagram.closed || diagram.showSource) return base
        if (line != diagram.firstLine) {
            return Pane().apply { setMinSize(0.0, 0.0); setPrefSize(0.0, 0.0); setMaxSize(0.0, 0.0) }
        }
        val width = base?.prefWidth(-1.0) ?: 0.0
        val box = Pane()
        box.setMinWidth(width)
        box.setPrefWidth(width)
        box.setMaxWidth(width)
        val view = MermaidDiagramView(diagram.source, Colors.isDark(), diagram.rendered,
            onRendered = { rendered ->
                if (currentArea === area && diagrams.contains(diagram) && !diagram.showSource) {
                    diagram.rendered = rendered
                    refreshGraphics(diagram)
                }
            },
            onFailure = { message ->
                if (currentArea === area && diagrams.contains(diagram) && !diagram.showSource) {
                    diagram.error = message
                    diagram.showSource = true
                    hoverDiagram = diagram
                    refreshGraphics(diagram)
                }
            })
        views.add(view)
        view.sceneProperty().addListener { _, _, scene ->
            if (scene == null) Platform.runLater {
                if (view.scene == null) views.remove(view)
            } else views.add(view)
        }
        view.setOnMouseEntered {
            hoverDiagram = diagram
            updateToolbar()
        }
        view.setOnMouseMoved { event ->
            hoverDiagram = diagram
            updateToolbar()
            event.consume()
        }
        view.addEventFilter(MouseEvent.MOUSE_PRESSED, EventHandler { event ->
            if (event.button == MouseButton.PRIMARY) {
                activeDiagram = diagram
                hoverDiagram = diagram
                updateToolbar()
            }
        })
        // 先交给 WebView 选择图中文字，再阻止鼠标操作改变底层源码选区。
        view.addEventHandler(MouseEvent.MOUSE_PRESSED, EventHandler { it.consume() })
        view.addEventHandler(MouseEvent.MOUSE_DRAGGED, EventHandler { it.consume() })
        view.addEventHandler(MouseEvent.MOUSE_RELEASED, EventHandler { it.consume() })
        view.addEventHandler(MouseEvent.MOUSE_CLICKED, EventHandler { it.consume() })
        view.addEventFilter(ScrollEvent.SCROLL, EventHandler { event ->
            if (event.deltaY != 0.0) area.scrollYBy(-event.deltaY)
            if (event.deltaX != 0.0) area.scrollXBy(-event.deltaX)
            event.consume()
        })
        val content = view
        content.relocate(0.0, 8.0)
        content.setPrefSize(Math.min(availableWidth(), diagram.rendered?.width ?: availableWidth()),
            previewHeight(diagram))
        content.isManaged = false
        content.resize(content.prefWidth(-1.0), content.prefHeight(-1.0))
        content.layoutXProperty().bind(Bindings.createDoubleBinding(
            {
                width + MarkdownEditorSupport.textLeftPadding(area) +
                    Math.max(0.0, (availableWidth() - content.width) / 2.0)
            }, area.widthProperty(), area.paddingProperty(), content.widthProperty()))
        box.children.add(content)
        if (base != null) {
            base.opacity = 0.0
            box.children.add(base)
        }
        return box
    }

    private fun updateToolbar() {
        if (updating) return
        val area = currentArea
        if (area == null || !presentationActive || area.scene?.window?.isShowing != true ||
            area.visibleParagraphs.isEmpty()) {
            hideToolbar()
            return
        }
        val diagram = hoverDiagram ?: if (area.isFocused) {
            activeDiagram ?: diagrams.firstOrNull { area.currentParagraph in it.firstLine..it.lastLine }
        } else null
        if (diagram == null || !diagrams.contains(diagram)) {
            if (!toolbar.isHover) hideDelay.playFromStart()
            return
        }
        val first = area.firstVisibleParToAllParIndex()
        val last = area.lastVisibleParToAllParIndex()
        if (diagram.lastLine < first || diagram.firstLine > last) {
            hideToolbar()
            return
        }
        val areaBounds = EditorScrollPane.viewportBoundsOnScreen(area)
        val origin = area.localToScreen(area.insets.left +
            (area.lookup(".lineno")?.prefWidth(-1.0) ?: 0.0) + MarkdownEditorSupport.textLeftPadding(area), 0.0)
        if (areaBounds == null || origin == null) {
            hideToolbar()
            return
        }
        val header = area.getParagraphBoundsOnScreen(diagram.firstLine).orElse(null)
        if (header == null && diagram.firstLine >= first) {
            hideToolbar()
            return
        }
        hideDelay.stop()
        toolbarDiagram = diagram
        modeButton.text = Locales.str(if (diagram.showSource) "markdownMermaidShowDiagram" else "markdownTableShowSource")
        modeButton.isDisable = diagram.showSource && (!diagram.closed || parsedVersion != area.editor.contentVersion)
        errorLabel.isVisible = diagram.error != null
        errorLabel.isManaged = errorLabel.isVisible
        errorLabel.tooltip = diagram.error?.let { Tooltip(it) }
        toolbar.stylesheets.setAll(area.scene.stylesheets)
        toolbar.applyCss()
        val x = Math.max(areaBounds.minX, Math.min(origin.x, areaBounds.maxX - toolbar.prefWidth(-1.0)))
        val y = if (header == null) areaBounds.minY else
            Math.max(areaBounds.minY, header.minY - toolbar.prefHeight(-1.0) - 6.0)
        if (toolbarPopup.isShowing) {
            toolbarPopup.x = x
            toolbarPopup.y = y
        } else {
            toolbarPopup.show(area, x, y)
        }
    }

    private fun hideToolbar() {
        hideDelay.stop()
        toolbarPopup.hide()
        toolbarDiagram = null
    }
}

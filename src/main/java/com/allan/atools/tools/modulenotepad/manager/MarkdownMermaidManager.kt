package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.Colors
import com.allan.atools.SettingPreferences
import com.allan.atools.UIContext
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.richtext.codearea.EditorAreaMgrCode
import com.allan.atools.richtext.codearea.MarkdownEditorSupport
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownMermaidSupport
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.uilibs.richtexts.CodeArea
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
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
    private val views = WeakHashMap<MermaidDiagramView, Diagram>()
    private val modeButtons = WeakHashMap<Button, Diagram>()
    private var textChanges: Subscription? = null
    private var parseTask: Future<*>? = null
    private var destroyed = false
    private var presentationActive = false
    private var layoutPending = false
    private var parsedVersion = -1L
    private var themeVersion = 0L
    private val scheduler = LatestRefreshScheduler(250, 600, ::parse)
    private val layoutChanged = InvalidationListener { requestLayout() }
    private val themeChanged = InvalidationListener {
        themeVersion++
        saved.values.forEach { list -> list.forEach { it.rendered = null } }
        diagrams.forEach { it.rendered = null }
        refreshGraphics()
    }

    init {
        SettingPreferences.getBoolProp(SettingPreferences.appVisionKey).addListener(themeChanged)
        UIContext.getFontSizeProperty().addListener(layoutChanged)
        refreshCurrentFile(area)
    }

    fun refreshCurrentFile(area: EditorArea?) {
        if (area === currentArea && MarkdownEditorSupport.supportsMarkdown(area) && area?.markdownPreviewEnabled == true) return
        unbind()
        if (destroyed || !MarkdownEditorSupport.supportsMarkdown(area) || area?.markdownPreviewEnabled != true) return
        currentArea = area
        area ?: return
        diagrams = saved[area].orEmpty()
        area.addParagraphGraphicDecorator(this, ::createGraphic)
        area.widthProperty().addListener(layoutChanged)
        area.paddingProperty().addListener(layoutChanged)
        textChanges = area.plainTextChanges().subscribe { change ->
            parsedVersion = -1
            val first = area.offsetToPosition(change.position, Forward).major
            val removed = change.removed.count { it == '\n' }
            val inserted = change.inserted.count { it == '\n' }
            val difference = inserted - removed
            val changed = ArrayList<Diagram>()
            // 段落样式随正文移动；仅展开被修改的图表，其他图表继续使用已有预览。
            diagrams.forEach { diagram ->
                if (diagram.firstLine > first + removed) {
                    diagram.firstLine += difference
                    diagram.lastLine += difference
                    if (difference != 0) changed.add(diagram)
                } else if (diagram.lastLine >= first) {
                    if (!diagram.showSource || difference != 0 || diagram.error != null) changed.add(diagram)
                    diagram.firstLine = Math.min(diagram.firstLine, first)
                    diagram.lastLine = Math.max(first + inserted, diagram.lastLine + difference)
                    diagram.showSource = true
                    diagram.error = null
                    diagram.rendered = null
                }
            }
            if (area.editor.isRealtimeProcessingLimitReached) clearPresentation() else {
                changed.forEach { refreshGraphics(it) }
                updateModeButtons()
            }
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
        parsedVersion = -1
        val area = currentArea ?: return
        saved[area] = diagrams
        clearPresentation()
        area.removeParagraphGraphicDecorator(this)
        area.widthProperty().removeListener(layoutChanged)
        area.paddingProperty().removeListener(layoutChanged)
        currentArea = null
        diagrams = emptyList()
    }

    fun destroy() {
        destroyed = true
        unbind()
        scheduler.dispose()
        saved.clear()
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
                        if (!MarkdownMermaidSupport.isSupported(block)) return
                        val spans = block.sourceSpans
                        if (spans.isEmpty()) return
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
                        val oldDiagrams = diagrams
                        val next = parsed.map { diagram ->
                            previous[diagram.firstLine]?.let { old ->
                                if (old.lastLine == diagram.lastLine && old.source == diagram.source
                                    && old.closed == diagram.closed) return@map old
                                diagram.showSource = old.showSource || !diagram.closed
                                if (old.source == diagram.source) {
                                    diagram.rendered = old.rendered
                                    diagram.error = old.error
                                }
                            }
                            diagram
                        }
                        diagrams = next
                        parsedVersion = version
                        val removed = oldDiagrams.filter { it !in next }
                        val changed = next.filter { diagram -> diagram !in oldDiagrams || !presentationActive ||
                            removed.any { it.firstLine <= diagram.lastLine && it.lastLine >= diagram.firstLine } }
                        area.suspendVisibleParsWhileInvoke {
                            // 正文修改不销毁未改变的 WebView；只清理消失的图表、刷新新增或变化的图表。
                            removed.forEach { old ->
                                for (line in old.firstLine..old.lastLine) {
                                    setStyle(line, null)
                                    if (line in 0 until area.paragraphs.size) area.recreateParagraphGraphic(line)
                                }
                            }
                            changed.forEach { refreshGraphics(it) }
                        }
                        updateModeButtons()
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
            if (!destroyed) refreshGraphics(recreate = false)
        }
    }

    private fun refreshGraphics(target: Diagram? = null, recreate: Boolean = true) {
        val area = currentArea ?: return
        if (area.editor.isRealtimeProcessingLimitReached) return
        presentationActive = true
        area.suspendVisibleParsWhileInvoke {
            (if (target == null) diagrams else listOf(target)).forEach { diagram ->
                for (line in diagram.firstLine..diagram.lastLine) {
                    val style = if (!diagram.showSource) {
                        CodeArea.MERMAID_PREVIEW_HEIGHT_PREFIX + if (line == diagram.firstLine)
                            previewHeight(diagram) + MarkdownMermaidGraphic.HEADER_HEIGHT + 16.0 else 0.0
                    } else if (line == diagram.firstLine) {
                        CodeArea.MERMAID_SOURCE_HEADER_HEIGHT_PREFIX + MarkdownMermaidGraphic.HEADER_HEIGHT
                    } else null
                    val changed = setStyle(line, style)
                    if (recreate && (line == diagram.firstLine || changed) && line in 0 until area.paragraphs.size) {
                        area.recreateParagraphGraphic(line)
                    }
                }
                if (!recreate) views.filterValues { it === diagram }.keys.forEach { view ->
                    view.setPrefSize(Math.min(availableWidth(), diagram.rendered?.width ?: availableWidth()),
                        previewHeight(diagram))
                }
            }
        }
    }

    private fun setStyle(line: Int, height: String?): Boolean {
        val area = currentArea ?: return false
        if (line !in 0 until area.paragraphs.size) return false
        val previous = area.getParagraph(line).paragraphStyle
        val styles = previous.filterNot {
            it.startsWith(CodeArea.MERMAID_PREVIEW_HEIGHT_PREFIX) ||
                it.startsWith(CodeArea.MERMAID_SOURCE_HEADER_HEIGHT_PREFIX)
        }.toMutableList()
        if (height != null) styles.add(height)
        if (styles == previous.toList()) return false
        area.setParagraphStyle(line, styles)
        return true
    }

    private fun clearPresentation() {
        val area = currentArea ?: return
        presentationActive = false
        views.keys.toList().forEach { it.dispose() }
        views.clear()
        modeButtons.clear()
        area.suspendVisibleParsWhileInvoke {
            diagrams.forEach { diagram ->
                for (line in diagram.firstLine..diagram.lastLine) {
                    setStyle(line, null)
                    if (line in 0 until area.paragraphs.size) area.recreateParagraphGraphic(line)
                }
            }
        }
    }

    private fun availableWidth(): Double {
        val area = currentArea ?: return 400.0
        val graphic = area.lookup(".lineno")
        return Math.max(0.0, area.width - area.insets.left - area.insets.right -
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
        if (line != diagram.firstLine) {
            if (diagram.showSource) return base
            return Pane().apply { setMinSize(0.0, 0.0); setPrefSize(0.0, 0.0); setMaxSize(0.0, 0.0) }
        }
        val header = createToolbar(diagram, area)
        if (diagram.showSource) return MarkdownMermaidGraphic(area, base, header, null, ::availableWidth)
        val renderedTheme = themeVersion
        val view = MermaidDiagramView(diagram.source, Colors.isDark(), diagram.rendered,
            onRendered = { rendered ->
                if (renderedTheme == themeVersion && currentArea === area && diagrams.contains(diagram) && !diagram.showSource) {
                    diagram.rendered = rendered
                    refreshGraphics(diagram, recreate = false)
                }
            },
            onFailure = { message ->
                if (renderedTheme == themeVersion && currentArea === area && diagrams.contains(diagram) && !diagram.showSource) {
                    diagram.error = message
                    diagram.showSource = true
                    refreshGraphics(diagram)
                }
            })
        views[view] = diagram
        view.sceneProperty().addListener { _, _, scene ->
            if (scene == null) Platform.runLater {
                if (view.scene == null) views.remove(view)
            } else views[view] = diagram
        }
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
        view.setPrefSize(Math.min(availableWidth(), diagram.rendered?.width ?: availableWidth()),
            previewHeight(diagram))
        return MarkdownMermaidGraphic(area, base, header, view, ::availableWidth)
    }

    private fun createToolbar(diagram: Diagram, area: EditorArea): HBox {
        val modeButton = Button(Locales.str(if (diagram.showSource) "markdownMermaidShowDiagram" else "markdownTableShowSource"))
        modeButton.styleClass.add("markdown-table-toolbar-button")
        modeButton.isFocusTraversable = false
        modeButton.minWidth = Region.USE_PREF_SIZE
        modeButton.isDisable = diagram.showSource && (!diagram.closed || parsedVersion != area.editor.contentVersion)
        modeButtons[modeButton] = diagram
        modeButton.sceneProperty().addListener { _, _, scene ->
            if (scene == null) Platform.runLater {
                if (modeButton.scene == null) modeButtons.remove(modeButton)
            } else modeButtons[modeButton] = diagram
        }
        modeButton.setOnAction {
            if (currentArea !== area || !presentationActive || !diagrams.contains(diagram) || area.markdownComposing) return@setOnAction
            if (diagram.showSource && (!diagram.closed || parsedVersion != area.editor.contentVersion)) return@setOnAction
            diagram.showSource = !diagram.showSource
            diagram.error = null
            refreshGraphics(diagram)
            if (diagram.showSource) {
                area.moveTo(Math.min(diagram.firstLine + 1, area.paragraphs.size - 1), 0)
                area.requestFollowCaret()
                area.requestFocus()
            }
        }
        val errorLabel = Label(Locales.str("markdownMermaidFailed"))
        errorLabel.styleClass.add("markdown-mermaid-error")
        errorLabel.isVisible = diagram.error != null
        errorLabel.isManaged = errorLabel.isVisible
        errorLabel.tooltip = diagram.error?.let { Tooltip(it) }
        return HBox(4.0, modeButton, errorLabel).apply {
            styleClass.add("markdown-table-toolbar")
            alignment = Pos.CENTER_LEFT
            minWidth = 0.0
            // 按钮先响应，避免点击操作栏时改变底层源码的选区。
            addEventHandler(MouseEvent.MOUSE_PRESSED) { it.consume() }
            addEventHandler(MouseEvent.MOUSE_DRAGGED) { it.consume() }
            addEventHandler(MouseEvent.MOUSE_RELEASED) { it.consume() }
            addEventHandler(MouseEvent.MOUSE_CLICKED) { it.consume() }
        }
    }

    private fun updateModeButtons() {
        val area = currentArea ?: return
        modeButtons.forEach { (button, diagram) ->
            button.isDisable = diagram.showSource && (!diagram.closed || parsedVersion != area.editor.contentVersion)
        }
    }
}

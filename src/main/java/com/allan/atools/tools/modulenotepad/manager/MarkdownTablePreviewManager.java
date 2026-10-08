package com.allan.atools.tools.modulenotepad.manager;

import static com.allan.atools.richtext.codearea.MarkdownEditorSupport.supportsMarkdown;
import static com.allan.atools.richtext.codearea.MarkdownEditorSupport.textLeftPadding;
import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState;
import com.allan.atools.richtext.codearea.MarkdownTableParser;
import com.allan.atools.richtext.codearea.MarkdownParagraphWrapSupport;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.uilibs.richtexts.CodeArea;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.reactfx.Subscription;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

/** Markdown 表格直接编辑、形态切换、结构操作与逐行呈现。 */
public final class MarkdownTablePreviewManager {
    private static final String PREVIEW_CLASS = "markdown-table-preview-para";
    private static final String HEIGHT_PREFIX = CodeArea.PARAGRAPH_PREVIEW_HEIGHT_PREFIX;
    private static final String SOURCE_HEADER_CLASS = "markdown-table-source-header";
    private final LatestRefreshScheduler refreshScheduler =
            new LatestRefreshScheduler(180, 420, this::startRefresh);
    private final InvalidationListener layoutChanged = observable -> requestLayoutRefresh();
    private final InvalidationListener selectionChanged = observable -> {
        hideCellMenu();
        updateSelection();
    };
    private final InvalidationListener editableChanged = observable -> {
        this.cellEditor.setEditable(this.currentArea != null && this.currentArea.isEditable() && !this.structurePending);
        updateToolbar();
    };
    private final EventHandler<MouseEvent> areaMouseDragged = this::onAreaMouseDragged;
    private final TextArea cellEditor = new TextArea() {
        @Override
        public void paste() {
            pasteClipboard(false);
        }
    };
    private final Map<Integer, MarkdownTableDocumentState.Table> tableByLine = new HashMap<>();
    private final Set<Integer> presentationLines = new HashSet<>();
    private final Map<Integer, Integer> rowIndexByLine = new HashMap<>();
    private Map<String, List<MarkdownTableLayout.Run>> inlineContents = Map.of();
    private final TextFlow measureFlow = new TextFlow();
    private final MarkdownTableImageCache tableImages = new MarkdownTableImageCache();
    private final Text editorMeasureText = new Text();
    private final ArrayDeque<LayoutJob> layoutJobs = new ArrayDeque<>();
    private final ArrayDeque<PresentationJob> presentationJobs = new ArrayDeque<>();
    private final AnimationTimer layoutTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (composingText || handlingInputMethod) {
                stop();
                return;
            }
            if (layoutPending) {
                layoutPending = false;
                refreshLayout();
            }
            long deadline = System.nanoTime() + 4_000_000;
            var area = currentArea;
            if (area != null && !presentationJobs.isEmpty()) {
                boolean visible = hasVisibleParagraph(area);
                int first = visible ? area.firstVisibleParToAllParIndex() : 0;
                int last = visible ? area.lastVisibleParToAllParIndex() : -1;
                PresentationJob priority = null;
                for (var job : presentationJobs) {
                    job.updateViewport(first, last);
                    if (priority == null && job.hasVisibleWork()) priority = job;
                }
                if (priority != null && presentationJobs.peek() != priority) {
                    presentationJobs.remove(priority);
                    presentationJobs.addFirst(priority);
                }
            }
            if (area != null) area.suspendVisibleParsWhileInvoke(() -> {
                while ((!presentationJobs.isEmpty() || !layoutJobs.isEmpty()) && System.nanoTime() < deadline) {
                    if (!presentationJobs.isEmpty() && (layoutJobs.isEmpty() || presentationJobs.peek().hasVisibleWork())) {
                        var job = presentationJobs.peek();
                        if (!job.isCurrent() || job.step()) presentationJobs.remove(job);
                    } else {
                        var job = layoutJobs.peek();
                        if (!job.isCurrent()) {
                            layoutJobs.remove();
                        } else if (job.step()) {
                            layoutJobs.remove();
                            job.apply();
                        }
                    }
                }
            });
            if (layoutJobs.isEmpty() && presentationJobs.isEmpty() && !layoutPending) {
                stop();
            }
        }
    };
    private long layoutGeneration;
    private boolean movingCellEditor;
    private boolean handlingInputMethod;
    private boolean composingText;
    private long inputMethodRevision;
    private Runnable afterComposition;
    private boolean structurePending;
    private final ArrayDeque<Boolean> pendingUndo = new ArrayDeque<>();
    private long cellActivationRevision;

    private EditorArea currentArea;
    private Subscription textChanges;
    private Subscription viewportChanges;
    private Future<?> parseTask;
    private List<MarkdownTableDocumentState.Table> tables = List.of();
    private ContextMenu cellMenu;
    private final Set<MarkdownTableToolbar> toolbars = Collections.newSetFromMap(new WeakHashMap<>());
    private MarkdownTableDocumentState.Table activeTable;
    private int activeRow = -1;
    private int activeColumn = -1;
    private String pendingTableId;
    private int pendingRow = -1;
    private int pendingColumn = -1;
    private int pendingSourceOffset = -1;
    private boolean destroyed;
    private boolean updatingCellEditor;
    private boolean writingCell;
    private MarkdownTableDocumentState.Row writingRow;
    private boolean writingStructure;
    private boolean layoutPending;
    private double graphicWidth;
    private double textPadding;
    private double layoutWidth = -1;
    private Font font;
    private long parsedVersion = -1;
    private java.io.File boundFile;
    private final Map<EditorArea, SavedLayouts> savedLayouts = new WeakHashMap<>();

    private record SavedLayouts(long version, java.io.File file, long savedAt, int cellCount,
                                List<MarkdownTableDocumentState.Table> tables,
                                Map<String, List<MarkdownTableLayout.Run>> contents,
                                Map<String, TableLayout> layouts, double width,
                                double graphicWidth, double textPadding, Font font) {}

    public MarkdownTablePreviewManager(EditorArea area) {
        configureCellEditor();
        refreshCurrentFile(area);
    }

    public void refreshCurrentFile(EditorArea area) {
        if (area == currentArea && textChanges != null && area.getMarkdownPreviewEnabled()
                && java.util.Objects.equals(boundFile, area.getEditor().getSourceFile())) return;
        unbindEditor();
        currentArea = area;
        if (!supportsMarkdown(area) || !area.getMarkdownPreviewEnabled()) {
            return;
        }
        boundFile = area.getEditor().getSourceFile();
        textChanges = area.plainTextChanges().subscribe(change ->
                onTextChanged(change.getPosition(), change.getRemoved(), change.getInserted()));
        viewportChanges = area.viewportDirtyEvents().subscribe(event -> requestLayoutRefresh());
        area.caretPositionProperty().addListener(selectionChanged);
        area.selectionProperty().addListener(selectionChanged);
        area.editableProperty().addListener(editableChanged);
        area.widthProperty().addListener(layoutChanged);
        area.sceneProperty().addListener(layoutChanged);
        area.paddingProperty().addListener(layoutChanged);
        area.wrapTextProperty().addListener(layoutChanged);
        area.paragraphGraphicFactoryProperty().addListener(layoutChanged);
        UIContext.getFontSizeProperty().addListener(layoutChanged);
        UIContext.getFontThemeProperty().addListener(layoutChanged);
        area.addEventFilter(MouseEvent.MOUSE_DRAGGED, areaMouseDragged);
        area.addParagraphGraphicDecorator(this, this::createGraphic);
        var saved = savedLayouts.remove(area);
        if (saved != null && saved.version() == area.getEditor().getContentVersion()
                && java.util.Objects.equals(saved.file(), boundFile) && !area.getEditor().isRealtimeProcessingLimitReached()) {
            tables = saved.tables();
            inlineContents = saved.contents();
            layouts.putAll(saved.layouts());
            layoutWidth = saved.width();
            graphicWidth = saved.graphicWidth();
            textPadding = saved.textPadding();
            font = saved.font();
            cellEditor.setFont(font);
            parsedVersion = saved.version();
            rebuildLineIndex();
            updatePresentation();
            requestLayoutRefresh();
        } else refreshScheduler.startNow();
    }

    public void destroy() {
        destroyed = true;
        unbindEditor();
        savedLayouts.clear();
        refreshScheduler.dispose();
    }

    private void configureCellEditor() {
        cellEditor.getStyleClass().add("markdown-table-cell-editor");
        cellEditor.setWrapText(true);
        cellEditor.setPrefRowCount(1);
        cellEditor.setMinSize(0, 0);
        cellEditor.textProperty().addListener((observable, oldValue, newValue) -> writeActiveCell(newValue));
        cellEditor.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, event -> {
            var area = currentArea;
            long revision = ++inputMethodRevision;
            handlingInputMethod = true;
            composingText = !event.getComposed().isEmpty();
            Platform.runLater(() -> {
                if (destroyed || currentArea != area || revision != inputMethodRevision) {
                    return;
                }
                handlingInputMethod = false;
                if (composingText) {
                    return;
                }
                writeActiveCell(cellEditor.getText());
                if (layoutPending || !layoutJobs.isEmpty()) {
                    layoutTimer.start();
                }
                if (afterComposition != null) {
                    var action = afterComposition;
                    afterComposition = null;
                    action.run();
                }
            });
        });
        cellEditor.caretPositionProperty().addListener(observable -> hideCellMenu());
        cellEditor.selectionProperty().addListener(observable -> hideCellMenu());
        cellEditor.addEventFilter(KeyEvent.KEY_PRESSED, this::onCellKeyPressed);
        cellEditor.addEventFilter(KeyEvent.KEY_TYPED, this::onCellKeyTyped);
        cellEditor.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (currentArea != null && event.getDeltaX() == 0 && !event.isShiftDown()) {
                currentArea.scrollYBy(-event.getDeltaY());
                event.consume();
            }
        });
        cellEditor.setContextMenu(null);
        cellEditor.setOnContextMenuRequested(event -> {
            if (activeTable != null && activeRow >= 0 && activeColumn >= 0) {
                showCellMenu(activeTable, activeRow, activeColumn,
                        event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });
        cellEditor.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused && !movingCellEditor) {
                Platform.runLater(() -> {
                    if (currentArea != null && currentArea.isFocused() && !cellEditor.isFocused() && !structurePending) {
                        endCellEditing(false);
                    }
                    updateToolbar();
                });
            }
        });
    }

    private void unbindEditor() {
        saveLayouts();
        parsedVersion = -1;
        boundFile = null;
        inputMethodRevision++;
        composingText = false;
        handlingInputMethod = false;
        structurePending = false;
        pendingUndo.clear();
        writingRow = null;
        cellActivationRevision++;
        afterComposition = null;
        layoutTimer.stop();
        layoutJobs.clear();
        presentationJobs.clear();
        layoutGeneration++;
        layoutPending = false;
        refreshScheduler.invalidate();
        if (parseTask != null) {
            parseTask.cancel(true);
            parseTask = null;
        }
        if (textChanges != null) {
            textChanges.unsubscribe();
            textChanges = null;
        }
        if (viewportChanges != null) {
            viewportChanges.unsubscribe();
            viewportChanges = null;
        }
        var area = currentArea;
        if (area != null) {
            area.caretPositionProperty().removeListener(selectionChanged);
            area.selectionProperty().removeListener(selectionChanged);
            area.editableProperty().removeListener(editableChanged);
            area.widthProperty().removeListener(layoutChanged);
            area.sceneProperty().removeListener(layoutChanged);
            area.paddingProperty().removeListener(layoutChanged);
            area.wrapTextProperty().removeListener(layoutChanged);
            area.paragraphGraphicFactoryProperty().removeListener(layoutChanged);
            UIContext.getFontSizeProperty().removeListener(layoutChanged);
            UIContext.getFontThemeProperty().removeListener(layoutChanged);
            area.removeEventFilter(MouseEvent.MOUSE_DRAGGED, areaMouseDragged);
            clearPresentation();
            area.removeParagraphGraphicDecorator(this);
        }
        currentArea = null;
        endCellEditing(false);
        toolbars.clear();
        currentArea = null;
        layoutTimer.stop();
        layoutJobs.clear();
        presentationJobs.clear();
        tables = List.of();
        tableByLine.clear();
        rowIndexByLine.clear();
        inlineContents = Map.of();
        tableImages.clear();
        measureFlow.getChildren().clear();
        editorMeasureText.setText("");
        cellEditor.clear();
        layouts.clear();
        layoutWidth = -1;
    }

    /** 标签切换只缓存测量数据，不持有离屏节点；大表和未完成的布局不进入缓存。 */
    private void saveLayouts() {
        savedLayouts.keySet().removeIf(area -> area.getEditor().isDestroyed());
        var area = currentArea;
        if (destroyed || area == null || area.getEditor().isDestroyed() || font == null
                || parsedVersion != area.getEditor().getContentVersion() || !layoutJobs.isEmpty() || !presentationJobs.isEmpty()) return;
        int count = 0;
        var copies = new HashMap<String, TableLayout>();
        for (var entry : layouts.entrySet()) {
            var layout = entry.getValue();
            if (layout.cells == null || layout.frozenWidths) return;
            for (var row : layout.cells) count += row.length;
            if (count > 20_000) return;
            var copy = new TableLayout();
            copy.table = layout.table;
            copy.cells = layout.cells;
            copy.widths = layout.widths;
            copy.totalWidth = layout.totalWidth;
            copy.viewportWidth = layout.viewportWidth;
            copy.font = layout.font;
            copy.alignments = layout.alignments;
            copy.rowHeights.putAll(layout.rowHeights);
            copy.measuredVersion = layout.measuredVersion;
            copies.put(entry.getKey(), copy);
        }
        savedLayouts.put(area, new SavedLayouts(parsedVersion, boundFile, System.nanoTime(), count,
                tables, inlineContents, copies, layoutWidth, graphicWidth, textPadding, font));
        while (savedLayouts.size() > 4 || savedLayouts.values().stream().mapToInt(SavedLayouts::cellCount).sum() > 20_000) {
            EditorArea oldest = null;
            long time = Long.MAX_VALUE;
            for (var entry : savedLayouts.entrySet()) {
                if (entry.getValue().savedAt() < time) { oldest = entry.getKey(); time = entry.getValue().savedAt(); }
            }
            savedLayouts.remove(oldest);
        }
    }

    private void onTextChanged(int position, String removed, String inserted) {
        var area = currentArea;
        if (area == null) {
            return;
        }
        if (area.getEditor().isRealtimeProcessingLimitReached()) {
            layoutJobs.clear();
            presentationJobs.clear();
            structurePending = false;
            pendingUndo.clear();
            inlineContents = Map.of();
            area.getMarkdownTableDocumentState().reset();
            tables = List.of();
            rebuildLineIndex();
            updatePresentation();
            endCellEditing(false);
            toolbars.clear();
            return;
        }
        if (area.getMarkdownTableDocumentState().getTables().isEmpty() && !tables.isEmpty()) {
            tables = List.of();
            rebuildLineIndex();
        }
        long lineDelta = inserted.chars().filter(character -> character == '\n').count()
                - removed.chars().filter(character -> character == '\n').count();
        var shiftedTables = lineDelta == 0 ? List.<MarkdownTableDocumentState.Table>of()
                : tables.stream().filter(table -> position + removed.length() <= table.startOffset()).toList();
        if ((writingCell || writingStructure || writingRow != null) && activeTable != null) {
            if (writingRow != null) {
                area.getMarkdownTableDocumentState().applyRowChange(activeTable.id(), activeRow, writingRow);
            } else if (writingCell) {
                area.getMarkdownTableDocumentState().applyCellChange(
                        activeTable.id(), activeRow, activeColumn, position, removed, inserted);
            } else {
                area.getMarkdownTableDocumentState().applyKnownTableChange(
                        activeTable.id(), position, removed, inserted);
                rebuildLineIndex();
            }
        } else {
            area.getMarkdownTableDocumentState().applyTextChange(
                    position, removed, inserted);
            rebuildLineIndex();
            if (removed.indexOf('\n') >= 0 || inserted.indexOf('\n') >= 0
                    || tables.stream().anyMatch(table -> !table.valid())) {
                updatePresentation();
            }
        }
        // 前方换行改变虚拟段落索引，只重新绑定发生位移的表格行；单元格布局继续复用。
        for (var table : shiftedTables) {
            if (table.valid() && table.mode() == MarkdownTableDocumentState.Mode.TABLE && hasTableLayout(table)) {
                recreateTableGraphics(table);
            }
        }
        if (!writingCell && !writingStructure && writingRow == null) {
            syncEditorFromDocument();
        }
        refreshScheduler.request();
        updateToolbar();
    }

    private void startRefresh(long requestId) {
        var area = currentArea;
        if (destroyed || area == null || area.getEditor().isRealtimeProcessingLimitReached()) {
            refreshScheduler.complete(requestId, null);
            return;
        }
        long version = area.getEditor().getContentVersion();
        String text = area.getText();
        parseTask = ThreadUtils.submit(() -> {
            List<MarkdownTableDocumentState.Table> parsed = null;
            var contents = new HashMap<String, List<MarkdownTableLayout.Run>>();
            try {
                var state = ((com.allan.atools.richtext.codearea.EditorAreaMgrCode) area.getEditor()).markdownSnapshot(text);
                parsed = MarkdownTableParser.parse(text, state.getRoot(), (source, node) ->
                        contents.put(source, MarkdownTableLayout.parse(node))).stream()
                        .filter(table -> !state.isEmbeddedLine(table.firstLine())).toList();
            } catch (RuntimeException exception) {
                Log.e("parse markdown tables failed", exception);
            }
            var result = parsed;
            Platform.runLater(() -> refreshScheduler.complete(requestId, () -> {
                parseTask = null;
                if (destroyed || area != currentArea
                        || version != area.getEditor().getContentVersion()
                        || area.getEditor().isRealtimeProcessingLimitReached()) {
                    return;
                }
                if (result == null) {
                    structurePending = false;
                    pendingUndo.clear();
                    endCellEditingAtFallback();
                    return;
                }
                if (pendingTableId == null) structurePending = false;
                cellEditor.setEditable(area.isEditable() && !structurePending);
                area.getMarkdownTableDocumentState().reconcile(result);
                parsedVersion = version;
                tables = area.getMarkdownTableDocumentState().getTables();
                inlineContents = Map.copyOf(contents);
                rebuildLineIndex();
                if (pendingTableId != null && tables.stream().noneMatch(table -> table.id().equals(pendingTableId) && table.valid())) {
                    structurePending = false;
                    endCellEditingAtFallback();
                }
                if (pendingTableId == null) {
                    syncActiveCellAfterParse();
                }
                applyParsedLayout();
                if (!structurePending) resumePendingUndo();
            }));
        });
    }

    private MarkdownTableDocumentState.Table latestTable(MarkdownTableDocumentState.Table previous) {
        if (previous == null) return null;
        for (var table : tables) if (table.id().equals(previous.id())) return table;
        return null;
    }

    private void rebuildLineIndex() {
        tableByLine.clear();
        rowIndexByLine.clear();
        var ids = new HashSet<String>();
        for (var table : tables) {
            ids.add(table.id());
            if (!table.valid()) {
                continue;
            }
            for (int line = table.firstLine(); line <= table.lastLine(); line++) {
                tableByLine.put(line, table);
            }
            for (int row = 0; row < table.rows().size(); row++) {
                rowIndexByLine.put(table.rows().get(row).line(), row);
            }
            var layout = layouts.get(table.id());
            if (layout != null && layout.cells != null && layout.cells.length == table.rows().size()
                    && layout.widths.length == table.alignments().size()) {
                layout.table = table;
            }
        }
        layouts.keySet().removeIf(id -> !ids.contains(id));
    }

    private void requestLayoutRefresh() {
        if (currentArea != null && !destroyed) {
            layoutPending = true;
            layoutTimer.start();
        }
    }

    private void refreshLayout() {
        var area = currentArea;
        if (area == null || tables.isEmpty() || area.getWidth() <= 0 || area.getScene() == null) {
            return;
        }
        Node lineNumber = area.lookup(".lineno");
        double nextGraphicWidth = lineNumber == null ? 0 : lineNumber.prefWidth(-1);
        double nextTextPadding = textLeftPadding(area);
        var nextFont = Font.font("JetBrains Mono", UIContext.getFontSizeProperty().get());
        double available = Math.floor(Math.max(1, area.getWidth() - area.getInsets().getLeft()
                - area.getInsets().getRight() - nextGraphicWidth - nextTextPadding));
        if (layoutWidth == available && graphicWidth == nextGraphicWidth
                && textPadding == nextTextPadding && nextFont.equals(font)) {
            return;
        }
        layoutWidth = available;
        graphicWidth = nextGraphicWidth;
        textPadding = nextTextPadding;
        font = nextFont;
        cellEditor.setFont(font);
        queueLayouts(true);
    }

    private void applyParsedLayout() {
        if (layoutWidth <= 0 || font == null) {
            refreshLayout();
        } else {
            queueLayouts(false);
        }
    }

    private void queueLayouts(boolean resize) {
        layoutGeneration++;
        layoutJobs.clear();
        presentationJobs.clear();
        updatePresentation();
        for (var table : tables) {
            if (table.valid()) {
                var layout = tableLayout(table);
                if (!resize && !table.id().equals(pendingTableId) && canReuseLayout(table, layout)) {
                    layout.table = table;
                    for (var graphic : List.copyOf(layout.graphics)) graphic.updateSelectionStyle();
                    continue;
                }
                boolean editing = activeTable != null && activeTable.id().equals(table.id());
                layoutJobs.add(new LayoutJob(table, layout, editing && !resize));
            }
        }
        updateToolbar();
        layoutTimer.start();
    }

    /** 内容、样式及可用宽度都未变化时，仅更新源码映射，不重新测量和重建单元格。 */
    private boolean canReuseLayout(MarkdownTableDocumentState.Table table, TableLayout layout) {
        if (layout.cells == null || layout.cells.length != table.rows().size()
                || layout.viewportWidth != layoutWidth || !font.equals(layout.font)
                || !table.alignments().equals(layout.alignments)) return false;
        if (layout.frozenWidths && (activeTable == null || !activeTable.id().equals(table.id()))) return false;
        for (int row = 0; row < layout.cells.length; row++) {
            var sourceRow = table.rows().get(row);
            if (layout.cells[row].length != sourceRow.cells().size()) return false;
            for (int column = 0; column < layout.cells[row].length; column++) {
                var cell = layout.cells[row][column];
                String source = sourceRow.cells().get(column).source();
                if (!cell.source.equals(source) || cell.header != sourceRow.header()
                        || !cell.runs.equals(inlineContents.get(source))) return false;
            }
        }
        return true;
    }

    /** 分帧测量完整表格；测量完成前继续使用上一份布局。 */
    private final class LayoutJob {
        private final MarkdownTableDocumentState.Table table;
        private final TableLayout layout;
        private final long generation = layoutGeneration;
        private final long documentVersion = currentArea.getEditor().getContentVersion();
        private final Font measuredFont = font;
        private final double available = layoutWidth;
        private final MarkdownTableLayout.Cell[][] cells;
        private final double[] natural;
        private final boolean freezeWidths;
        private double[] widths;
        private int row;
        private int column;
        private boolean measuringHeights;
        private final Map<Integer, Double> measuredRowHeights = new HashMap<>();
        private double rowHeight;

        LayoutJob(MarkdownTableDocumentState.Table table, TableLayout layout, boolean freezeWidths) {
            this.table = table;
            this.layout = layout;
            int columns = table.alignments().size();
            cells = new MarkdownTableLayout.Cell[table.rows().size()][];
            natural = new double[columns];
            this.freezeWidths = freezeWidths && layout.widths != null && layout.widths.length == columns;
        }

        boolean isCurrent() {
            return currentArea != null && !destroyed && generation == layoutGeneration
                    && documentVersion == currentArea.getEditor().getContentVersion();
        }

        boolean step() {
            if (row == cells.length) {
                if (measuringHeights) {
                    return true;
                }
                widths = freezeWidths ? layout.widths.clone()
                        : MarkdownTableLayout.distribute(natural,
                                MarkdownTableLayout.characterWidth(measuredFont), available);
                measuringHeights = true;
                row = 0;
                rowHeight = measuredFont.getSize() + MarkdownTableLayout.VERTICAL_INSETS;
            }
            if (!measuringHeights) {
                if (column == 0) cells[row] = new MarkdownTableLayout.Cell[natural.length];
                var sourceRow = table.rows().get(row);
                String source = sourceRow.cells().get(column).source();
                MarkdownTableLayout.Cell previous = layout.cells != null && row < layout.cells.length
                        && column < layout.cells[row].length ? layout.cells[row][column] : null;
                var runs = inlineContents.get(source);
                if (runs == null) {
                    // 已知输入的预览结果尚未回填时保留文字，下一次后台解析替换行内样式。
                    runs = List.of(new MarkdownTableLayout.Run(decodeCell(source), false, false, false, false, false));
                }
                var cell = previous != null && previous.source.equals(source)
                        && previous.font.equals(measuredFont) && previous.header == sourceRow.header()
                        && previous.runs.equals(runs) ? previous.copy()
                        : new MarkdownTableLayout.Cell(source, runs, measuredFont, sourceRow.header());
                cell.measureNatural(measureFlow);
                cells[row][column] = cell;
                natural[column] = Math.max(natural[column], cell.natural);
            } else {
                cells[row][column].measureHeight(measureFlow, widths[column]);
                rowHeight = Math.max(rowHeight, measuredCellHeight(table, row, column,
                        cells[row][column], widths[column], measuredFont));
            }
            if (++column == natural.length) {
                if (measuringHeights) {
                    measuredRowHeights.put(row, Math.ceil(rowHeight));
                    rowHeight = measuredFont.getSize() + MarkdownTableLayout.VERTICAL_INSETS;
                }
                column = 0;
                row++;
            }
            return false;
        }

        void apply() {
            boolean hadLayout = layout.cells != null;
            boolean structureChanged = hadLayout && (layout.cells.length != cells.length
                    || layout.widths.length != widths.length);
            layout.table = table;
            layout.cells = cells;
            layout.widths = widths;
            layout.totalWidth = Arrays.stream(widths).sum();
            layout.viewportWidth = available;
            layout.font = measuredFont;
            layout.alignments = List.copyOf(table.alignments());
            layout.frozenWidths = freezeWidths;
            layout.rowHeights = measuredRowHeights;
            layout.revision++;
            layout.measuredVersion = documentVersion;
            if (structureChanged) layout.structureRevision++;
            double maxOffset = Math.max(0, layout.totalWidth - available);
            table.setHorizontalOffset(Math.min(table.horizontalOffset(), maxOffset));
            // 测量结果一次性替换，行高、可见节点和源码呈现继续按帧提交。
            presentationJobs.addFirst(new PresentationJob(table));
        }
    }

    private final Map<String, TableLayout> layouts = new HashMap<>();

    private TableLayout tableLayout(MarkdownTableDocumentState.Table table) {
        return layouts.computeIfAbsent(table.id(), ignored -> new TableLayout());
    }

    private boolean hasTableLayout(MarkdownTableDocumentState.Table table) {
        var layout = layouts.get(table.id());
        return layout != null && layout.table == table && layout.cells != null
                && layout.cells.length == table.rows().size()
                && layout.widths.length == table.alignments().size();
    }

    private void updateSelection() {
        if (structurePending || writingStructure) return;
        if (!cellEditor.isFocused()) {
            var selectedCell = selectedCell();
            if (selectedCell != null && currentArea.isFocused()) {
                var selection = currentArea.getSelection();
                var cell = selectedCell.table().rows().get(selectedCell.row()).cells().get(selectedCell.column());
                int start = sourceOffsetToEditor(cell.source(), selection.getStart() - cell.startOffset());
                int end = sourceOffsetToEditor(cell.source(), selection.getEnd() - cell.startOffset());
                activateCell(selectedCell.table(), selectedCell.row(), selectedCell.column(), 12);
                Platform.runLater(() -> cellEditor.selectRange(start, end));
                return;
            }
            for (var layout : layouts.values()) {
                for (var graphic : List.copyOf(layout.graphics)) {
                    if (graphic.getScene() != null) {
                        graphic.updateSelectionStyle();
                    }
                }
            }
            updateToolbar();
        }
    }

    private void updatePresentation() {
        if (currentArea == null) return;
        presentationJobs.clear();
        if (tables.isEmpty() && presentationLines.isEmpty()) return;
        presentationJobs.add(new PresentationJob(null));
        layoutTimer.start();
    }

    /** 可见行优先；清理旧范围和回填离屏段落都拆到逐行步骤，不能在测量完成时一次性提交整表。 */
    private final class PresentationJob {
        private final long generation = layoutGeneration;
        private final long version = currentArea.getEditor().getContentVersion();
        private final List<MarkdownTableDocumentState.Table> targets;
        private final MarkdownTableDocumentState.Table target;
        private final List<Integer> oldLines;
        private int visibleFirst = -1;
        private int visibleLast = -1;
        private int visibleLine;
        private int oldIndex;
        private int tableIndex;
        private int sourceLine = -1;

        PresentationJob(MarkdownTableDocumentState.Table table) {
            target = table;
            targets = table == null ? List.copyOf(tables) : List.of(table);
            oldLines = table == null ? List.copyOf(presentationLines) : List.of();
            boolean visible = hasVisibleParagraph(currentArea);
            updateViewport(visible ? currentArea.firstVisibleParToAllParIndex() : 0,
                    visible ? currentArea.lastVisibleParToAllParIndex() : -1);
        }

        boolean isCurrent() {
            return currentArea != null && !destroyed && generation == layoutGeneration
                    && version == currentArea.getEditor().getContentVersion();
        }

        boolean hasVisibleWork() {
            return visibleLine <= visibleLast;
        }

        void updateViewport(int first, int last) {
            if (target != null) {
                first = Math.max(first, target.firstLine());
                last = Math.min(last, target.lastLine());
            }
            if (visibleFirst == first && visibleLast == last) return;
            visibleFirst = first;
            visibleLast = last;
            visibleLine = first;
        }

        boolean step() {
            if (visibleLine <= visibleLast) {
                int line = visibleLine++;
                var table = tableByLine.get(line);
                if ((target == null || table == target) && (table != null || presentationLines.contains(line)))
                    present(table, line, true);
                return false;
            }
            if (oldIndex < oldLines.size()) {
                int line = oldLines.get(oldIndex++);
                if (line < visibleFirst || line > visibleLast) {
                    var table = tableByLine.get(line);
                    if (!presented(table)) setPreviewStyle(line, null, false);
                }
                return false;
            }
            while (tableIndex < targets.size()) {
                var table = targets.get(tableIndex);
                if (sourceLine < 0) sourceLine = table.firstLine();
                if (sourceLine > table.lastLine()) {
                    tableIndex++;
                    sourceLine = -1;
                    continue;
                }
                int line = sourceLine++;
                if (line < visibleFirst || line > visibleLast) present(table, line, false);
                return false;
            }
            var pendingLayout = pendingTableId == null ? null : layouts.get(pendingTableId);
            if (pendingLayout != null && pendingLayout.measuredVersion == version && parsedVersion == version)
                restorePendingCell();
            updateToolbar();
            return true;
        }

        private boolean presented(MarkdownTableDocumentState.Table table) {
            return table != null && table.valid() && (table.mode() == MarkdownTableDocumentState.Mode.SOURCE
                    || table.mode() == MarkdownTableDocumentState.Mode.TABLE && hasTableLayout(table));
        }

        private void present(MarkdownTableDocumentState.Table table, int line, boolean visible) {
            boolean preview = table != null && table.valid() && table.mode() == MarkdownTableDocumentState.Mode.TABLE
                    && hasTableLayout(table);
            boolean source = table != null && table.valid() && table.mode() == MarkdownTableDocumentState.Mode.SOURCE;
            setPreviewStyle(line, preview ? paragraphHeight(table, line) : null, source);
            if (!visible || !preview) return;
            var layout = layouts.get(table.id());
            for (var graphic : List.copyOf(layout.graphics)) {
                if (graphic.sourceLine != line || graphic.getScene() == null) continue;
                if (graphic.structureRevision != layout.structureRevision) {
                    recreateParagraphGraphic(line);
                } else if (graphic.layoutRevision != layout.revision) {
                    graphic.refresh();
                }
                break;
            }
        }
    }

    private void clearPresentation() {
        for (int line : List.copyOf(presentationLines)) {
            setPreviewStyle(line, null, false);
        }
        presentationLines.clear();
    }

    private boolean setPreviewStyle(int line, Double height, boolean sourceLine) {
        var area = currentArea;
        if (area == null) {
            return false;
        }
        if (line < 0 || line >= area.getParagraphs().size()) {
            presentationLines.remove(line);
            return false;
        }
        var table = tableByLine.get(line);
        boolean sourceHeader = sourceLine && table != null && line == table.firstLine();
        boolean preview = height != null;
        boolean wasPresented = presentationLines.contains(line);
        var existing = area.getParagraph(line).getParagraphStyle();
        var styles = new ArrayList<>(existing);
        styles.removeIf(style -> style.equals(PREVIEW_CLASS) || style.equals(SOURCE_HEADER_CLASS)
                || style.equals(MarkdownParagraphWrapSupport.TABLE_SOURCE_CLASS) || style.startsWith(HEIGHT_PREFIX)
                || style.startsWith(CodeArea.TABLE_SOURCE_HEADER_HEIGHT_PREFIX));
        if (preview) {
            styles.add(PREVIEW_CLASS);
            styles.add(HEIGHT_PREFIX + height);
        }
        if (sourceHeader) {
            styles.add(SOURCE_HEADER_CLASS);
            styles.add(CodeArea.TABLE_SOURCE_HEADER_HEIGHT_PREFIX + MarkdownTableToolbar.HEIGHT);
        }
        if (sourceLine) styles.add(MarkdownParagraphWrapSupport.TABLE_SOURCE_CLASS);
        boolean presented = preview || sourceLine;
        if (presented) presentationLines.add(line);
        else presentationLines.remove(line);
        boolean modeChanged = existing.contains(PREVIEW_CLASS) != preview
                || existing.contains(SOURCE_HEADER_CLASS) != sourceHeader;
        boolean presentationChanged = wasPresented != presented;
        boolean stylesChanged = !styles.equals(existing);
        if (stylesChanged) {
            area.setParagraphStyle(line, styles);
        }
        if (modeChanged || presentationChanged) {
            area.recreateParagraphGraphic(line);
        }
        return stylesChanged;
    }

    private Node createGraphic(int line, Node base) {
        var table = tableByLine.get(line);
        var layout = table == null ? null : layouts.get(table.id());
        if (table != null && table.valid() && table.mode() == MarkdownTableDocumentState.Mode.SOURCE
                && line == table.firstLine()) {
            return new MarkdownTableSourceHeader(currentArea, base, createToolbar(table), () -> layoutWidth);
        }
        if (table == null || !table.valid() || table.mode() != MarkdownTableDocumentState.Mode.TABLE
                || layout == null || !hasTableLayout(table)) {
            return base;
        }
        int row = rowIndexByLine.getOrDefault(line, -1);
        return new RowGraphic(layout, row, base);
    }

    /** 只为虚拟列表中的行持有节点；高度、列宽、选择变化均直接更新现有节点。 */
    private final class RowGraphic extends Pane {
        private final TableLayout layout;
        private final int row;
        private final int sourceLine;
        private final long structureRevision;
        private long layoutRevision;
        private final Node base;
        private final HBox content = new HBox();
        private final Pane viewport = new Pane(content);
        private final Rectangle clip = new Rectangle();
        private ScrollBar scrollBar;
        private boolean syncingScrollBar;
        private final MarkdownTableToolbar header;

        RowGraphic(TableLayout layout, int row, Node base) {
            this.layout = layout;
            this.row = row;
            this.sourceLine = row < 0 ? layout.table.firstLine() + 1 : layout.table.rows().get(row).line();
            this.structureRevision = layout.structureRevision;
            this.base = base;
            content.setManaged(false);
            viewport.setManaged(false);
            viewport.setClip(clip);
            viewport.layoutXProperty().bind(Bindings.createDoubleBinding(
                    () -> getWidth() + textLeftPadding(currentArea)
                            - currentArea.estimatedScrollXProperty().getValue(),
                    widthProperty(), currentArea.paddingProperty(), currentArea.estimatedScrollXProperty()));
            getChildren().add(viewport);
            header = row == 0 ? createToolbar(layout.table) : null;
            if (header != null) {
                header.setManaged(false);
                header.layoutXProperty().bind(viewport.layoutXProperty());
                getChildren().add(header);
            }
            layout.contents.add(content);
            layout.graphics.add(this);
            if (row >= 0) {
                content.getStyleClass().add("markdown-table-preview-row");
                if (row == 0) {
                    content.getStyleClass().add("markdown-table-preview-header");
                } else if (row % 2 == 0) {
                    content.getStyleClass().add("markdown-table-preview-alternate");
                }
                for (int column = 0; column < layout.widths.length; column++) {
                    content.getChildren().add(new CellGraphic(this, column));
                }
            }
            addEventFilter(ScrollEvent.SCROLL, event -> scrollTable(layout.table, event));
            if (base != null) {
                base.setOpacity(0);
                getChildren().add(base);
            }
            refresh();
        }

        int line() {
            return ready() ? (row < 0 ? layout.table.firstLine() + 1 : layout.table.rows().get(row).line()) : sourceLine;
        }

        private boolean ready() {
            return layout.table.valid() && layout.table.mode() == MarkdownTableDocumentState.Mode.TABLE
                    && structureRevision == layout.structureRevision && row < layout.cells.length
                    && layout.cells.length == layout.table.rows().size()
                    && layout.widths.length == layout.table.alignments().size();
        }

        void refresh() {
            if (!ready()) return;
            content.setTranslateX(-layout.table.horizontalOffset());
            if (header != null) refreshToolbar(header, layout.table);
            for (var node : content.getChildren()) {
                ((CellGraphic) node).refresh();
            }
            boolean showBar = line() == layout.table.lastLine()
                    && layout.totalWidth > layout.viewportWidth;
            if (showBar && scrollBar == null) {
                scrollBar = new ScrollBar();
                scrollBar.setManaged(false);
                scrollBar.setOrientation(Orientation.HORIZONTAL);
                scrollBar.getStyleClass().add("markdown-table-scroll-bar");
                scrollBar.layoutXProperty().bind(viewport.layoutXProperty());
                scrollBar.valueProperty().addListener((observable, oldValue, value) -> {
                    if (!syncingScrollBar) {
                        layout.table.setHorizontalOffset(value.doubleValue());
                        layout.applyOffset(value.doubleValue());
                    }
                });
                getChildren().add(scrollBar);
            }
            if (scrollBar != null) {
                syncingScrollBar = true;
                double maxOffset = Math.max(0, layout.totalWidth - layout.viewportWidth);
                scrollBar.setVisible(showBar);
                scrollBar.setMax(maxOffset);
                scrollBar.setVisibleAmount(maxOffset == 0 ? 0
                        : maxOffset * layout.viewportWidth / layout.totalWidth);
                scrollBar.setValue(layout.table.horizontalOffset());
                syncingScrollBar = false;
            }
            updateSelectionStyle();
            layoutRevision = layout.revision;
            requestLayout();
        }

        void updateSelectionStyle() {
            if (!ready()) return;
            var selection = currentArea.getSelection();
            var table = layout.table;
            setStyleClass(content, "markdown-table-selection-hit", selection.getLength() > 0
                    && selection.getStart() < table.endOffset() && selection.getEnd() > table.startOffset()
                    && !selectionHitsCell(table));
            for (var node : content.getChildren()) {
                var cell = (CellGraphic) node;
                var source = table.rows().get(row).cells().get(cell.column);
                setStyleClass(cell, "markdown-table-selected-cell", selection.getLength() > 0
                        && selection.getStart() < source.endOffset() && selection.getEnd() > source.startOffset());
            }
        }

        @Override
        protected double computePrefWidth(double height) {
            return base == null ? 0 : base.prefWidth(height);
        }

        @Override
        protected double computePrefHeight(double width) {
            return ready() ? paragraphHeight(layout.table, line()) : getHeight();
        }

        @Override
        protected void layoutChildren() {
            // 分帧提交结构变化时，旧行保留当前画面，直到自己的步骤重建，不能读取新结构的越界坐标。
            if (!ready()) return;
            double height = layout.rowHeights.getOrDefault(row, 2.0);
            if (base != null) {
                base.resizeRelocate(0, 0, getWidth(), getHeight());
            }
            clip.setWidth(Math.min(layout.viewportWidth, layout.totalWidth));
            clip.setHeight(height);
            double top = header == null ? 0 : MarkdownTableToolbar.HEIGHT;
            viewport.setLayoutY(top);
            viewport.resize(clip.getWidth(), height);
            content.resize(layout.totalWidth, height);
            if (header != null) header.resize(layout.viewportWidth, top);
            if (scrollBar != null) {
                scrollBar.resize(layout.viewportWidth, MarkdownTableLayout.SCROLL_HEIGHT);
                scrollBar.setLayoutY(top + height);
            }
        }
    }

    /** 预览文字和活动编辑框共享一个单元格，普通输入不重新挂载编辑框。 */
    private final class CellGraphic extends StackPane {
        private final RowGraphic graphic;
        private final int column;
        private final TextFlow text = new TextFlow();
        private MarkdownTableLayout.Cell displayed;

        CellGraphic(RowGraphic graphic, int column) {
            this.graphic = graphic;
            this.column = column;
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            setAlignment(Pos.TOP_LEFT);
            // 每格内容区统一扣除左右各 13px、上下各 10px，边框只画一次。
            setPadding(new Insets(graphic.row == 0 ? 9 : 10, 12, 9, column == 0 ? 12 : 13));
            getStyleClass().add("markdown-table-preview-cell");
            if (column == 0) {
                getStyleClass().add("markdown-table-preview-first-cell");
            }
            // 在父节点的冒泡阶段截断，先让 TextArea 皮肤处理，避免外层编辑器重复写入组合文字。
            addEventHandler(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, event -> event.consume());
            setOnMousePressed(event -> {
                if (!graphic.ready()) { event.consume(); return; }
                var table = graphic.layout.table;
                if (event.getButton() == MouseButton.PRIMARY) {
                    if (event.isShortcutDown() && event.getTarget() instanceof Node target) {
                        for (var value = target; value != null && value != this; value = value.getParent()) {
                            if (value.getUserData() instanceof String destination) {
                                event.consume();
                                ((com.allan.atools.richtext.codearea.EditorAreaMgrCode) currentArea.getEditor()).openMarkdownDestination(destination);
                                return;
                            }
                        }
                    }
                    if (isDescendant(event.getTarget(), cellEditor)) {
                        return;
                    }
                    event.consume();
                    activateCell(table, graphic.row, column, 12);
                    double screenX = event.getScreenX();
                    double screenY = event.getScreenY();
                    Platform.runLater(() -> {
                        if (activeTable != null && activeTable.id().equals(table.id())
                                && activeRow == graphic.row && activeColumn == column) {
                            cellEditor.applyCss();
                            cellEditor.layout();
                            var point = cellEditor.screenToLocal(screenX, screenY);
                            if (point != null && cellEditor.getSkin() instanceof TextAreaSkin skin) {
                                skin.positionCaret(skin.getIndex(point.getX(), point.getY()), false);
                            }
                        }
                    });
                } else if (event.getButton() == MouseButton.SECONDARY) {
                    event.consume();
                    if (activeTable != table || activeRow != graphic.row || activeColumn != column) {
                        activateCell(table, graphic.row, column, 12);
                    }
                    showCellMenu(table, graphic.row, column, event.getScreenX(), event.getScreenY());
                }
            });
        }

        void refresh() {
            if (!graphic.ready()) return;
            var layout = graphic.layout;
            var cell = layout.cells[graphic.row][column];
            if (displayed == null || !displayed.runs.equals(cell.runs)
                    || !displayed.font.equals(cell.font) || displayed.header != cell.header) {
                MarkdownTableLayout.fill(text, cell.runs, cell.font, cell.header, run -> tableImages.node(run.image(), run.text(), currentArea.getEditor().getSourceFile()));
            }
            displayed = cell;
            text.setTextAlignment(switch (layout.table.alignments().get(column)) {
                case CENTER -> TextAlignment.CENTER;
                case RIGHT -> TextAlignment.RIGHT;
                case DEFAULT, LEFT -> TextAlignment.LEFT;
            });
            double width = layout.widths[column];
            setMinWidth(width);
            setPrefWidth(width);
            setMaxWidth(width);
            if (activeTable != null && activeTable.id().equals(layout.table.id())
                    && activeRow == graphic.row && activeColumn == column) {
                reparentCellEditor(this);
            } else if (getChildren().size() != 1 || getChildren().get(0) != text) {
                getChildren().setAll(text);
            }
        }
    }

    private static void setStyleClass(Node node, String style, boolean enabled) {
        boolean existing = node.getStyleClass().contains(style);
        if (enabled && !existing) {
            node.getStyleClass().add(style);
        } else if (!enabled && existing) {
            node.getStyleClass().remove(style);
        }
    }

    private void scrollTable(MarkdownTableDocumentState.Table table, ScrollEvent event) {
        double delta = event.getDeltaX();
        if (delta == 0 && event.isShiftDown()) {
            delta = event.getDeltaY();
        }
        var layout = tableLayout(table);
        if (delta == 0 || layout.totalWidth <= layout.viewportWidth) {
            return;
        }
        double value = Math.max(0, Math.min(table.horizontalOffset() - delta,
                layout.totalWidth - layout.viewportWidth));
        table.setHorizontalOffset(value);
        layout.applyOffset(value);
        event.consume();
    }

    private void activateCell(MarkdownTableDocumentState.Table table, int row, int column, double clickX) {
        if (structurePending || deferWhileComposing(() -> activateCell(table, row, column, clickX))) {
            return;
        }
        var latest = latestTable(table);
        if (latest != table) {
            if (latest != null) activateCell(latest, row, column, clickX);
            return;
        }
        var layout = layouts.get(table.id());
        if (!table.valid() || layout == null || layout.cells == null
                || row < 0 || row >= layout.cells.length || column >= layout.widths.length) {
            return;
        }
        hideCellMenu();
        var previousTable = activeTable;
        int previousRow = activeRow;
        boolean sameCell = previousTable == table
                && activeRow == row && activeColumn == column;
        if (!sameCell) {
            currentArea.getUndoManager().preventMerge();
            activeTable = table;
            activeRow = row;
            activeColumn = column;
            updatingCellEditor = true;
            cellEditor.setText(decodeCell(table.rows().get(row).cells().get(column).source()));
            updatingCellEditor = false;
            cellEditor.positionCaret(clickX == Double.MAX_VALUE ? cellEditor.getLength() : 0);
        }
        cellEditor.setEditable(currentArea.isEditable() && !structurePending);
        if (previousTable != null && (previousTable != table || previousRow != row)) {
            updateRowHeight(previousTable, previousRow, true);
        }
        updateActiveRowHeight();
        refreshRowGraphics(table, row);
        if (previousTable != null && !previousTable.id().equals(table.id())) {
            queueLayouts(false);
        }
        currentArea.showParagraphInViewport(table.rows().get(row).line());
        double left = 0;
        for (int index = 0; index < column; index++) left += layout.widths[index];
        double right = left + layout.widths[column];
        double offset = table.horizontalOffset();
        if (left < offset) offset = left;
        else if (right > offset + layout.viewportWidth) offset = right - layout.viewportWidth;
        offset = Math.max(0, Math.min(offset, Math.max(0, layout.totalWidth - layout.viewportWidth)));
        table.setHorizontalOffset(offset);
        layout.applyOffset(offset);
        long activation = ++cellActivationRevision;
        Platform.runLater(() -> {
            if (currentArea != null && activation == cellActivationRevision && activeTable != null
                    && activeTable.id().equals(table.id()) && activeRow == row && activeColumn == column) {
                currentArea.applyCss();
                currentArea.layout();
                refreshRowGraphics(activeTable, row);
                cellEditor.requestFocus();
            }
        });
        updateToolbar();
    }

    private void reparentCellEditor(StackPane target) {
        if (cellEditor.getParent() == target) {
            return;
        }
        movingCellEditor = true;
        try {
            Parent parent = cellEditor.getParent();
            if (parent instanceof Pane pane) {
                pane.getChildren().remove(cellEditor);
            }
            target.getChildren().setAll(cellEditor);
            cellEditor.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            StackPane.setMargin(cellEditor, new Insets(-10, -13, -10, -13));
            cellEditor.applyCss();
            // ScrollPane 默认缓存视口；表格滚动到小数坐标时，缓存插值会使编辑文字发虚。
            Node viewport = cellEditor.lookup(".scroll-pane > .viewport");
            if (viewport != null) {
                viewport.setCache(false);
            }
        } finally {
            movingCellEditor = false;
        }
    }

    private void endCellEditing(boolean focusDocument) {
        if (currentArea != null && deferWhileComposing(() -> endCellEditing(focusDocument))) {
            return;
        }
        hideCellMenu();
        cellActivationRevision++;
        var previousTable = activeTable;
        int previousRow = activeRow;
        activeTable = null;
        activeRow = -1;
        activeColumn = -1;
        movingCellEditor = true;
        Parent parent = cellEditor.getParent();
        if (parent instanceof Pane pane) {
            pane.getChildren().remove(cellEditor);
        }
        movingCellEditor = false;
        pendingTableId = null;
        pendingRow = -1;
        pendingColumn = -1;
        pendingSourceOffset = -1;
        if (previousTable != null && currentArea != null && !destroyed) {
            updateRowHeight(previousTable, previousRow, true);
            queueLayouts(false);
        }
        if (focusDocument && currentArea != null) {
            currentArea.requestFocus();
        }
    }

    private void writeActiveCell(String value) {
        if (updatingCellEditor || handlingInputMethod || composingText || structurePending
                || activeTable == null || currentArea == null || !currentArea.isEditable()
                || !activeTable.valid() || activeRow < 0 || activeRow >= activeTable.rows().size()) {
            return;
        }
        var row = activeTable.rows().get(activeRow);
        if (activeColumn < 0 || activeColumn >= row.cells().size()) {
            return;
        }
        var cell = row.cells().get(activeColumn);
        String encoded = MarkdownTableCellText.encode(value, cell.source());
        if (cell.synthetic()) {
            var replacement = MarkdownTableParser.materializeRow(row, activeColumn, encoded);
            writingRow = replacement.getRow();
            try {
                currentArea.replaceText(row.startOffset(), row.endOffset(), replacement.getSource());
            } finally {
                writingRow = null;
            }
            updateActiveRowHeight();
            return;
        }
        // 紧邻分隔符时保留空白，末尾反斜线不能转义列分隔符。
        if (encoded.endsWith("\\") && cell.endOffset() < currentArea.getLength()
                && currentArea.getText(cell.endOffset(), cell.endOffset() + 1).equals("|")) encoded += " ";
        if (cell.startOffset() > currentArea.getLength() || cell.endOffset() > currentArea.getLength()) {
            endCellEditing(true);
            return;
        }
        String current = currentArea.getText(cell.startOffset(), cell.endOffset());
        if (current.equals(encoded)) {
            return;
        }
        writingCell = true;
        try {
            currentArea.replaceText(cell.startOffset(), cell.endOffset(), encoded);
            cell.setSource(encoded);
        } finally {
            writingCell = false;
        }
        updateActiveRowHeight();
    }

    private void updateActiveRowHeight() {
        if (composingText || handlingInputMethod || activeTable == null || activeRow < 0 || font == null) {
            return;
        }
        var layout = layouts.get(activeTable.id());
        if (layout == null || layout.cells == null || activeRow >= layout.cells.length
                || activeColumn < 0 || activeColumn >= layout.widths.length) {
            return;
        }
        String source = activeTable.rows().get(activeRow).cells().get(activeColumn).source();
        var previous = layout.cells[activeRow][activeColumn];
        if (!previous.source.equals(source)) {
            var runs = inlineContents.get(source);
            if (runs == null) {
                runs = List.of(new MarkdownTableLayout.Run(cellEditor.getText(), false, false, false, false, false));
            }
            var cell = new MarkdownTableLayout.Cell(source, runs, layout.font, activeRow == 0);
            cell.measureHeight(measureFlow, layout.widths[activeColumn]);
            layout.cells[activeRow][activeColumn] = cell;
        }
        updateRowHeight(activeTable, activeRow, true);
    }

    private void updateRowHeight(MarkdownTableDocumentState.Table table, int row, boolean present) {
        var layout = layouts.get(table.id());
        if (layout == null || layout.cells == null || row < 0 || row >= layout.cells.length
                || row >= table.rows().size()) {
            return;
        }
        double height = layout.font.getSize() + MarkdownTableLayout.VERTICAL_INSETS;
        for (int column = 0; column < layout.cells[row].length; column++) {
            height = Math.max(height, measuredCellHeight(table, row, column,
                    layout.cells[row][column], layout.widths[column], layout.font));
        }
        int line = table.rows().get(row).line();
        layout.rowHeights.put(row, Math.ceil(height));
        if (present && currentArea != null && table.mode() == MarkdownTableDocumentState.Mode.TABLE) {
            setPreviewStyle(line, paragraphHeight(table, line), false);
            refreshRowGraphics(table, row);
        }
    }

    private double measuredCellHeight(MarkdownTableDocumentState.Table table, int row, int column,
                                      MarkdownTableLayout.Cell cell, double width, Font measuredFont) {
        if (activeTable == null || !activeTable.id().equals(table.id()) || activeRow != row || activeColumn != column)
            return cell.height;
        editorMeasureText.setFont(measuredFont);
        editorMeasureText.setText(cellEditor.getText().isEmpty() ? " " : cellEditor.getText() + "\u200b");
        editorMeasureText.setWrappingWidth(Math.max(1, width - MarkdownTableLayout.HORIZONTAL_INSETS));
        return editorMeasureText.getLayoutBounds().getHeight() + MarkdownTableLayout.VERTICAL_INSETS;
    }

    private void refreshRowGraphics(MarkdownTableDocumentState.Table table, int row) {
        var layout = layouts.get(table.id());
        if (layout != null) {
            for (var graphic : List.copyOf(layout.graphics)) {
                if (graphic.row == row && graphic.getScene() != null) {
                    graphic.refresh();
                }
            }
        }
    }

    private double paragraphHeight(MarkdownTableDocumentState.Table table, int line) {
        var layout = tableLayout(table);
        double height = layout.rowHeights.getOrDefault(rowIndexByLine.getOrDefault(line, -1), 2.0);
        if (line == table.firstLine()) height += MarkdownTableToolbar.HEIGHT;
        if (line == table.lastLine() && layout.totalWidth > layout.viewportWidth) {
            height += MarkdownTableLayout.SCROLL_HEIGHT;
        }
        return height;
    }

    private void syncEditorFromDocument() {
        if (composingText || handlingInputMethod || structurePending || activeTable == null || !activeTable.valid()
                || activeRow < 0 || activeRow >= activeTable.rows().size()) {
            return;
        }
        var row = activeTable.rows().get(activeRow);
        if (activeColumn < 0 || activeColumn >= row.cells().size()) {
            endCellEditing(false);
            return;
        }
        var cell = row.cells().get(activeColumn);
        if (cell.startOffset() < 0 || cell.endOffset() > currentArea.getLength()
                || cell.startOffset() > cell.endOffset()) {
            endCellEditing(false);
            return;
        }
        String value = decodeCell(currentArea.getText(cell.startOffset(), cell.endOffset()));
        if (!cellEditor.getText().equals(value)) {
            int caret = cellEditor.getCaretPosition();
            updatingCellEditor = true;
            cellEditor.setText(value);
            cellEditor.positionCaret(Math.min(caret, value.length()));
            updatingCellEditor = false;
            updateActiveRowHeight();
        }
    }

    private void onCellKeyPressed(KeyEvent event) {
        if (composingText || handlingInputMethod) return;
        if (event.isAltDown() || event.isControlDown() && event.isMetaDown()) return;
        if (event.isShortcutDown() && (event.getCode() == KeyCode.Z || event.getCode() == KeyCode.Y && !event.isShiftDown())) {
            event.consume();
            performUndoRedo(event.isShiftDown() || event.getCode() == KeyCode.Y);
            return;
        }
        if (structurePending) {
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE) {
            event.consume();
            exitTable(activeTable, 1);
            return;
        }
        if (event.isShortcutDown() && event.getCode() == KeyCode.V && event.isShiftDown()) {
            event.consume();
            pastePlainText();
            return;
        }
        if (event.isShortcutDown() && !event.isShiftDown() && event.getCode() == KeyCode.ENTER) {
            event.consume();
            int row = activeRow;
            int column = activeColumn;
            applyTableEdit(activeTable, table -> MarkdownTableEdits.insertRow(table, row + 1, column));
            return;
        }
        if (!event.isShortcutDown() && event.getCode() == KeyCode.TAB) {
            event.consume();
            moveCell(event.isShiftDown() ? -1 : 1);
            return;
        }
        var shortcut = com.allan.atools.richtext.codearea.MarkdownShortcuts.match(event);
        if (shortcut == null) return;
        switch (shortcut) {
            case BOLD -> { event.consume(); wrapCellSelection("**"); }
            case ITALIC -> { event.consume(); wrapCellSelection("*"); }
            case STRIKE -> { event.consume(); wrapCellSelection("~~"); }
            case INLINE_CODE -> { event.consume(); wrapCellSelection("`"); }
            default -> { }
        }
    }

    private void editCellLink() {
        var area = currentArea;
        var table = activeTable;
        if (!canModify(table) || composingText || handlingInputMethod) return;
        long activation = cellActivationRevision;
        long version = area.getEditor().getContentVersion();
        String source = cellEditor.getText();
        var selection = cellEditor.getSelection();
        var document = ((com.allan.atools.richtext.codearea.EditorAreaMgrCode) area.getEditor()).markdownSnapshot(area.getText());
        var edit = com.allan.atools.richtext.codearea.MarkdownLinkEditing.request(source,
                selection.getStart(), selection.getEnd(), cellEditor.getCaretPosition(), document);
        if (edit == null || destroyed || currentArea != area || activation != cellActivationRevision
                || version != area.getEditor().getContentVersion() || !source.equals(cellEditor.getText())
                || !canModify(activeTable) || composingText || handlingInputMethod) return;
        area.getUndoManager().preventMerge();
        cellEditor.replaceText(edit.getStart(), edit.getEnd(), edit.getText());
        cellEditor.selectRange(edit.getStart(), edit.getStart() + edit.getText().length());
        area.getUndoManager().preventMerge();
    }

    private void onCellKeyTyped(KeyEvent event) {
        if (structurePending) {
            event.consume();
            return;
        }
        if (currentArea == null || !currentArea.isEditable()
                || !currentArea.getEditor().getState().isChinesePunctuation()
                || event.getCharacter().length() != 1) {
            return;
        }
        Character value = switch (event.getCharacter().charAt(0)) {
            case ',' -> '，';
            case '.' -> '。';
            case '?' -> '？';
            case '!' -> '！';
            case ':' -> '：';
            case ';' -> '；';
            case '(' -> '（';
            case ')' -> '）';
            case '[' -> '【';
            case ']' -> '】';
            default -> null;
        };
        if (value != null) {
            event.consume();
            cellEditor.replaceSelection(value.toString());
        }
    }

    private void wrapCellSelection(String mark) {
        if (currentArea == null || !currentArea.isEditable() || structurePending || composingText || handlingInputMethod) return;
        int start = cellEditor.getSelection().getStart();
        int end = cellEditor.getSelection().getEnd();
        String selected = cellEditor.getSelectedText();
        var source = cellEditor.getText();
        var state = new com.allan.atools.richtext.codearea.keywordhelper.MarkdownAstCache().snapshot(source);
        if (state.intersectsLiteral(start, end, mark.equals("`"))) return;
        currentArea.getUndoManager().preventMerge();
        if (mark.equals("`")) {
            var edit = com.allan.atools.richtext.codearea.MarkdownInlineCode.toggle(source, start, end);
            cellEditor.replaceText(edit.getStart(), edit.getEnd(), edit.getText());
            cellEditor.selectRange(edit.getSelectionStart(), edit.getSelectionEnd());
            currentArea.getUndoManager().preventMerge();
            return;
        }
        if (selected.startsWith(mark) && selected.endsWith(mark) && selected.length() >= mark.length() * 2) {
            var value = selected.substring(mark.length(), selected.length() - mark.length());
            cellEditor.replaceText(start, end, value);
            cellEditor.selectRange(start, start + value.length());
        } else if (start >= mark.length() && end + mark.length() <= source.length()
                && source.substring(start - mark.length(), start).equals(mark)
                && source.substring(end, end + mark.length()).equals(mark)) {
            cellEditor.replaceText(start - mark.length(), end + mark.length(), selected);
            cellEditor.selectRange(start - mark.length(), end - mark.length());
        } else {
            cellEditor.replaceText(start, end, mark + selected + mark);
            cellEditor.selectRange(start + mark.length(), end + mark.length());
        }
        currentArea.getUndoManager().preventMerge();
    }

    private void moveCell(int direction) {
        var table = activeTable;
        if (table == null) {
            return;
        }
        int columns = table.alignments().size();
        int index = activeRow * columns + activeColumn + direction;
        currentArea.getUndoManager().preventMerge();
        if (index < 0 || index >= table.rows().size() * columns) {
            leaveTable(table, direction);
            return;
        }
        activateCell(table, index / columns, index % columns, direction < 0 ? Double.MAX_VALUE : 12);
    }

    private void exitTable(MarkdownTableDocumentState.Table table, int direction) {
        if (deferWhileComposing(() -> exitTable(table, direction)) || structurePending) return;
        var target = latestTable(table);
        if (target != null && target.valid() && currentArea != null) leaveTable(target, direction);
    }

    private void leaveTable(MarkdownTableDocumentState.Table table, int direction) {
        var area = currentArea;
        int target;
        int adjacent = direction > 0 ? table.lastLine() + 1 : table.firstLine() - 1;
        int paragraph = adjacent + direction;
        boolean reusable = adjacent >= 0 && adjacent < area.getParagraphs().size()
                && paragraph >= 0 && paragraph < area.getParagraphs().size()
                && area.getParagraph(adjacent).length() == 0 && !tableByLine.containsKey(paragraph);
        if (reusable) {
            target = area.getAbsolutePosition(paragraph, direction > 0 ? 0 : area.getParagraph(paragraph).length());
        } else if (area.isEditable()) {
            int position = direction > 0 ? table.endOffset() : table.startOffset();
            // 留下永久空白分隔行；在紧邻表格的空行打字仍会被 GFM 当作表体或表头段落。
            target = direction > 0 ? position + table.lineEnding().length() * 2 : position;
            endCellEditing(false);
            area.getUndoManager().preventMerge();
            area.insertText(position, table.lineEnding() + table.lineEnding());
            area.getUndoManager().preventMerge();
        } else {
            target = direction > 0 ? Math.min(area.getLength(), table.endOffset() + table.lineEnding().length())
                    : Math.max(0, table.startOffset() - 1);
        }
        endCellEditing(false);
        area.moveTo(target);
        area.requestFollowCaret();
        area.requestFocus();
    }

    private void showCellMenu(MarkdownTableDocumentState.Table table, int row, int column,
                              double screenX, double screenY) {
        if (deferWhileComposing(() -> showCellMenu(table, row, column, screenX, screenY))) {
            return;
        }
        var area = currentArea;
        if (!hasShowingWindow(area)) {
            return;
        }
        hideCellMenu();
        var undo = new MenuItem(Locales.str("markdownTableUndo"));
        var redo = new MenuItem(Locales.str("markdownTableRedo"));
        var cut = new MenuItem(Locales.str("markdownTableCut"));
        var copy = new MenuItem(Locales.str("markdownTableCopy"));
        var copyRow = new MenuItem(Locales.str("markdownTableCopyRow"));
        var paste = new MenuItem(Locales.str("markdownTablePaste"));
        var selectAll = new MenuItem(Locales.str("markdownTableSelectAll"));
        var pasteCells = new MenuItem(Locales.str("markdownTablePasteCells"));
        var pasteText = new MenuItem(Locales.str("markdownTablePasteText"));
        var insertAbove = new MenuItem(Locales.str("markdownTableInsertAbove"));
        var insertBelow = new MenuItem(Locales.str("markdownTableInsertBelow"));
        var insertLeft = new MenuItem(Locales.str("markdownTableInsertLeft"));
        var insertRight = new MenuItem(Locales.str("markdownTableInsertRight"));
        var duplicateRow = new MenuItem(Locales.str("markdownTableDuplicateRow"));
        var moveUp = new MenuItem(Locales.str("markdownTableMoveUp"));
        var moveDown = new MenuItem(Locales.str("markdownTableMoveDown"));
        var moveLeft = new MenuItem(Locales.str("markdownTableMoveLeft"));
        var moveRight = new MenuItem(Locales.str("markdownTableMoveRight"));
        var copyAs = new Menu(Locales.str("markdownTableCopyData"));
        copyAs.getItems().addAll(copyRow,
                tableCopyItem("markdownTableCopyRowTsv", table, MarkdownTableCopyScope.ROW, row, column, true),
                tableCopyItem("markdownTableCopyColumnMarkdown", table, MarkdownTableCopyScope.COLUMN, row, column, false),
                tableCopyItem("markdownTableCopyColumnTsv", table, MarkdownTableCopyScope.COLUMN, row, column, true),
                tableCopyItem("markdownTableCopyTableMarkdown", table, MarkdownTableCopyScope.TABLE, row, column, false),
                tableCopyItem("markdownTableCopyTableTsv", table, MarkdownTableCopyScope.TABLE, row, column, true));
        var exitBefore = new MenuItem(Locales.str("markdownTableExitBefore"));
        var exitAfter = new MenuItem(Locales.str("markdownTableExitAfter"));
        var alignment = new Menu(Locales.str("markdownTableAlignment"));
        var alignmentGroup = new ToggleGroup();
        for (var value : new MarkdownTableDocumentState.Alignment[]{MarkdownTableDocumentState.Alignment.LEFT,
                MarkdownTableDocumentState.Alignment.CENTER, MarkdownTableDocumentState.Alignment.RIGHT}) {
            String key = switch (value) {
                case LEFT -> "markdownTableAlignLeft";
                case CENTER -> "markdownTableAlignCenter";
                default -> "markdownTableAlignRight";
            };
            var item = new RadioMenuItem(Locales.str(key));
            item.setToggleGroup(alignmentGroup);
            var currentAlignment = table.alignments().get(column);
            item.setSelected(currentAlignment == value || value == MarkdownTableDocumentState.Alignment.LEFT
                    && currentAlignment == MarkdownTableDocumentState.Alignment.DEFAULT);
            item.setOnAction(event -> applyTableEdit(table,
                    target -> MarkdownTableEdits.alignColumn(target, row, column, value)));
            alignment.getItems().add(item);
        }
        var deleteRow = new MenuItem(Locales.str("markdownTableDeleteRow"));
        var deleteColumn = new MenuItem(Locales.str("markdownTableDeleteColumn"));
        var rowActions = new Menu(Locales.str("markdownTableRowActions"));
        rowActions.getItems().addAll(insertAbove, insertBelow, duplicateRow, moveUp, moveDown, new SeparatorMenuItem(), deleteRow);
        var columnActions = new Menu(Locales.str("markdownTableColumnActions"));
        columnActions.getItems().addAll(insertLeft, insertRight, moveLeft, moveRight, alignment, new SeparatorMenuItem(), deleteColumn);
        var formats = new Menu(Locales.str("markdown.format"));
        var names = new String[]{"markdown.bold", "markdown.italic", "markdown.strike", "markdown.inlineCode"};
        var marks = new String[]{"**", "*", "~~", "`"};
        for (int index = 0; index < marks.length; index++) {
            var item = new MenuItem(Locales.str(names[index]));
            var mark = marks[index];
            for (var shortcut : com.allan.atools.richtext.codearea.MarkdownShortcuts.values()) {
                if (shortcut.getLabelKey().equals(names[index])) item.setGraphic(new Label(shortcut.display()));
            }
            item.setOnAction(event -> wrapCellSelection(mark));
            formats.getItems().add(item);
        }
        var editLink = new MenuItem(Locales.str("markdown.editLink"));
        editLink.setOnAction(event -> editCellLink());
        formats.getItems().add(editLink);
        formats.setDisable(!area.isEditable() || structurePending);
        var menu = new ContextMenu(undo, redo, new SeparatorMenuItem(), cut, copy, copyAs,
                paste, pasteCells, pasteText, selectAll, new SeparatorMenuItem(), formats, rowActions, columnActions,
                new SeparatorMenuItem(), exitBefore, exitAfter);
        cellMenu = menu;
        undo.setOnAction(event -> runUndo(false));
        redo.setOnAction(event -> runUndo(true));
        cut.setOnAction(event -> cellEditor.cut());
        copy.setOnAction(event -> cellEditor.copy());
        copyRow.setOnAction(event -> MarkdownTableClipboardKt.copyMarkdownTableRow(area, table.id(), row));
        paste.setOnAction(event -> cellEditor.paste());
        pasteCells.setOnAction(event -> pasteClipboard(true));
        pasteText.setOnAction(event -> pastePlainText());
        insertAbove.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.insertRow(target, row, column)));
        insertBelow.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.insertRow(target, row + 1, column)));
        insertLeft.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.insertColumn(target, column, row)));
        insertRight.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.insertColumn(target, column + 1, row)));
        duplicateRow.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.duplicateRow(target, row, column)));
        moveUp.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.moveRow(target, row, column, -1)));
        moveDown.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.moveRow(target, row, column, 1)));
        moveLeft.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.moveColumn(target, row, column, -1)));
        moveRight.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.moveColumn(target, row, column, 1)));
        exitBefore.setOnAction(event -> exitTable(table, -1));
        exitAfter.setOnAction(event -> exitTable(table, 1));
        selectAll.setOnAction(event -> cellEditor.selectAll());
        deleteRow.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.deleteRow(target, row, column)));
        deleteColumn.setOnAction(event -> applyTableEdit(table, target -> MarkdownTableEdits.deleteColumn(target, row, column)));
        menu.setOnShowing(event -> {
            boolean editable = area.isEditable() && !structurePending;
            undo.setDisable(!editable || !area.getUndoManager().isUndoAvailable());
            redo.setDisable(!editable || !area.getUndoManager().isRedoAvailable());
            cut.setDisable(!editable || cellEditor.getSelectedText().isEmpty());
            copy.setDisable(cellEditor.getSelectedText().isEmpty());
            var clipboard = Clipboard.getSystemClipboard();
            paste.setDisable(!editable || (!clipboard.hasString() && !clipboard.hasHtml() && !clipboard.hasImage() && !clipboard.hasFiles() && !MarkdownTableClipboardKt.hasTableCells(clipboard)));
            pasteCells.setDisable(paste.isDisable());
            pasteText.setDisable(!editable || !clipboard.hasString());
            insertAbove.setDisable(!editable || row == 0);
            insertBelow.setDisable(!editable);
            insertLeft.setDisable(!editable);
            insertRight.setDisable(!editable);
            alignment.setDisable(!editable);
            copyAs.setDisable(structurePending);
            duplicateRow.setDisable(!editable);
            moveUp.setDisable(!editable || row <= 1);
            moveDown.setDisable(!editable || row == 0 || row == table.rows().size() - 1);
            moveLeft.setDisable(!editable || column == 0);
            moveRight.setDisable(!editable || column == table.alignments().size() - 1);
            exitBefore.setDisable(structurePending);
            exitAfter.setDisable(structurePending);
            deleteRow.setDisable(!editable || row == 0);
            deleteColumn.setDisable(!editable || table.alignments().size() == 1);
        });
        menu.setOnHidden(event -> {
            if (cellMenu == menu) {
                cellMenu = null;
            }
        });
        menu.show(area, screenX, screenY);
    }

    private MenuItem tableCopyItem(String key, MarkdownTableDocumentState.Table table,
                                   MarkdownTableCopyScope scope, int row, int column, boolean tsv) {
        var item = new MenuItem(Locales.str(key));
        item.setOnAction(event -> {
            if (currentArea != null) {
                MarkdownTableClipboardKt.copyMarkdownTableData(currentArea, table.id(), scope, row, column, tsv);
            }
        });
        return item;
    }

    private void hideCellMenu() {
        var menu = cellMenu;
        cellMenu = null;
        if (menu != null) {
            menu.hide();
        }
    }

    private void runUndo(boolean redo) {
        performUndoRedo(redo);
    }

    private void resumePendingUndo() {
        if (structurePending || pendingUndo.isEmpty() || currentArea == null) return;
        var area = currentArea;
        Platform.runLater(() -> {
            if (currentArea == area && !structurePending && !pendingUndo.isEmpty()) {
                performUndoRedo(pendingUndo.removeFirst());
                if (!structurePending) resumePendingUndo();
            }
        });
    }

    private void performUndoRedo(boolean redo) {
        if (currentArea == null || !currentArea.isEditable()) return;
        if (structurePending) {
            pendingUndo.addLast(redo);
            return;
        }
        if (!(redo ? currentArea.getUndoManager().isRedoAvailable() : currentArea.getUndoManager().isUndoAvailable())) return;
        if (deferWhileComposing(() -> performUndoRedo(redo))) {
            return;
        }
        currentArea.getUndoManager().preventMerge();
        if (activeTable != null) {
            pendingTableId = activeTable.id();
            pendingRow = activeRow;
            pendingColumn = activeColumn;
        }
        structurePending = true;
        cellEditor.setEditable(false);
        writingStructure = true;
        try {
            if (redo) {
                currentArea.getUndoManager().redo();
            } else {
                currentArea.getUndoManager().undo();
            }
        } finally {
            writingStructure = false;
        }
        currentArea.getUndoManager().preventMerge();
        refreshScheduler.startNow();
    }

    private MarkdownTableToolbar createToolbar(MarkdownTableDocumentState.Table table) {
        var bar = new MarkdownTableToolbar(table.id(), () -> optimize(table),
                () -> toggleMode(table), () -> exitTable(table, 1));
        toolbars.add(bar);
        refreshToolbar(bar, table);
        return bar;
    }

    private void refreshToolbar(MarkdownTableToolbar bar, MarkdownTableDocumentState.Table table) {
        bar.refresh(currentArea != null && currentArea.isEditable(), table != null && table.valid(),
                structurePending, table != null && table.mode() == MarkdownTableDocumentState.Mode.SOURCE);
    }

    private void updateToolbar() {
        for (var bar : List.copyOf(toolbars)) {
            if (bar.getScene() == null) continue;
            var table = tables.stream().filter(value -> value.id().equals(bar.getTableId())).findFirst().orElse(null);
            refreshToolbar(bar, table);
        }
    }

    private static boolean hasShowingWindow(Node node) {
        return node != null && node.getScene() != null && node.getScene().getWindow() != null
                && node.getScene().getWindow().isShowing();
    }

    private static boolean hasVisibleParagraph(EditorArea area) {
        return hasShowingWindow(area) && !area.getVisibleParagraphs().isEmpty();
    }

    private void onAreaMouseDragged(MouseEvent event) {
        if (isDescendant(event.getTarget(), cellEditor) || isDescendantOfType(event.getTarget(), ScrollBar.class)
                || isDescendantOfType(event.getTarget(), MarkdownTableToolbar.class)) {
            return;
        }
        var area = currentArea;
        if (!hasVisibleParagraph(area)) {
            return;
        }
        Point2D point = area.screenToLocal(event.getScreenX(), event.getScreenY());
        var hit = area.hit(point.getX(), point.getY()).getCharacterIndex();
        if (hit.isEmpty()) {
            return;
        }
        var table = tableAtOffset(hit.getAsInt());
        if (table == null || table.mode() != MarkdownTableDocumentState.Mode.TABLE) {
            return;
        }
        int anchor = currentArea.getAnchor();
        if (anchor < table.startOffset()) {
            currentArea.selectRange(anchor, table.startOffset());
            event.consume();
        } else if (anchor > table.endOffset()) {
            currentArea.selectRange(anchor, table.endOffset());
            event.consume();
        }
    }

    private static boolean isDescendant(Object target, Node ancestor) {
        Node node = target instanceof Node targetNode ? targetNode : null;
        while (node != null) {
            if (node == ancestor) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private static boolean isDescendantOfType(Object target, Class<? extends Node> type) {
        Node node = target instanceof Node targetNode ? targetNode : null;
        while (node != null) {
            if (type.isInstance(node)) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private MarkdownTableDocumentState.Table tableAtOffset(int offset) {
        for (var table : tables) {
            if (offset >= table.startOffset() && offset <= table.endOffset()) {
                return table;
            }
        }
        return null;
    }

    private CellTarget selectedCell() {
        var selection = currentArea.getSelection();
        if (selection.getLength() == 0) {
            return null;
        }
        int line = currentArea.offsetToPosition(selection.getStart(),
                org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
        var table = tableByLine.get(line);
        int row = rowIndexByLine.getOrDefault(line, -1);
        if (table == null || !table.valid() || table.mode() != MarkdownTableDocumentState.Mode.TABLE || row < 0) {
            return null;
        }
        var cells = table.rows().get(row).cells();
        for (int column = 0; column < cells.size(); column++) {
            var cell = cells.get(column);
            if (selection.getStart() >= cell.startOffset() && selection.getEnd() <= cell.endOffset()) {
                return new CellTarget(table, row, column);
            }
        }
        return null;
    }

    private boolean selectionHitsCell(MarkdownTableDocumentState.Table table) {
        var selection = currentArea.getSelection();
        int low = 0;
        int high = table.rows().size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            var cells = table.rows().get(middle).cells();
            if (cells.get(cells.size() - 1).endOffset() <= selection.getStart()) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        for (int row = low; row < table.rows().size(); row++) {
            for (var cell : table.rows().get(row).cells()) {
                if (cell.startOffset() >= selection.getEnd()) {
                    return false;
                }
                if (selection.getStart() < cell.endOffset()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int sourceOffsetToEditor(String source, int sourceOffset) {
        return MarkdownTableCellText.sourceOffset(source, sourceOffset);
    }

    private void toggleMode(MarkdownTableDocumentState.Table table) {
        if (deferWhileComposing(() -> toggleMode(table))) {
            return;
        }
        if (table == null || structurePending) {
            return;
        }
        var latest = latestTable(table);
        if (latest != table) {
            if (latest != null) toggleMode(latest);
            return;
        }
        if (table.mode() == MarkdownTableDocumentState.Mode.SOURCE && !table.valid()) {
            return;
        }
        table.setMode(table.mode() == MarkdownTableDocumentState.Mode.TABLE
                ? MarkdownTableDocumentState.Mode.SOURCE : MarkdownTableDocumentState.Mode.TABLE);
        if (table.mode() == MarkdownTableDocumentState.Mode.SOURCE) {
            endCellEditing(false);
            currentArea.moveTo(Math.min(table.startOffset(), currentArea.getLength()));
            currentArea.requestFocus();
        }
        updatePresentation();
    }

    private void applyTableEdit(MarkdownTableDocumentState.Table table,
                                java.util.function.Function<MarkdownTableDocumentState.Table, MarkdownTableEdits.Edit> operation) {
        if (deferWhileComposing(() -> applyTableEdit(table, operation))) return;
        var target = latestTable(table);
        if (!canModify(target)) return;
        var edit = operation.apply(target);
        if (edit != null && !edit.getSource().equals(safeTableSource(target))) {
            replaceTable(target, edit.getSource(), edit.getRow(), edit.getColumn());
        }
    }

    private void pastePlainText() {
        if (deferWhileComposing(this::pastePlainText)) return;
        if (canModify(activeTable) && Clipboard.getSystemClipboard().hasString()) {
            cellEditor.replaceSelection(Clipboard.getSystemClipboard().getString());
        }
    }

    private void importCellImages(java.util.function.Consumer<java.util.function.Consumer<String>> importer) {
        var area = currentArea;
        var table = activeTable;
        int row = activeRow;
        int column = activeColumn;
        long activation = cellActivationRevision;
        var selection = cellEditor.getSelection();
        importer.accept(markdown -> {
            if (currentArea == area && activeTable != null && activeTable.id().equals(table.id())
                    && activeRow == row && activeColumn == column && cellActivationRevision == activation
                    && canModify(activeTable) && !composingText && !handlingInputMethod) {
                cellEditor.replaceText(selection.getStart(), selection.getEnd(), markdown);
                cellEditor.positionCaret(selection.getStart() + markdown.length());
            } else com.allan.atools.ui.SnackbarUtils.show(Locales.str("markdown.imageInsertChanged"));
        });
    }

    private void pasteClipboard(boolean forceCells) {
        if (deferWhileComposing(() -> pasteClipboard(forceCells))) return;
        if (!canModify(activeTable)) return;
        var clipboard = Clipboard.getSystemClipboard();
        var markdownCells = MarkdownTableClipboardKt.markdownClipboardCells(clipboard);
        if (markdownCells != null) {
            int row = activeRow;
            int column = activeColumn;
            applyTableEdit(activeTable, table -> MarkdownTableEdits.pasteMarkdown(table, row, column, markdownCells));
            return;
        }
        String tsv = MarkdownTableClipboardKt.tableClipboardTsv(clipboard);
        String value = tsv != null ? tsv : clipboard.getString();
        if (value != null && (forceCells || tsv != null || value.indexOf('\t') >= 0)) {
            var matrix = MarkdownTableEdits.parseTsv(value);
            if (matrix != null) {
                int row = activeRow;
                int column = activeColumn;
                applyTableEdit(activeTable, table -> MarkdownTableEdits.paste(table, row, column, matrix));
            } else cellEditor.replaceSelection(value);
            return;
        }
        if (!forceCells) {
            if (clipboard.hasImage()) {
                importCellImages(insert -> currentArea.getMarkdownAttachments().importClipboardImage(clipboard.getImage(), insert));
                return;
            }
            if (clipboard.hasFiles() && clipboard.getFiles().stream().allMatch(com.allan.atools.richtext.codearea.MarkdownAttachments::isImage)) {
                importCellImages(insert -> currentArea.getMarkdownAttachments().importFiles(clipboard.getFiles(), insert));
                return;
            }
            if (clipboard.hasHtml() && clipboard.getHtml().length() <= 2 * 1024 * 1024) {
                var cells = com.allan.atools.richtext.codearea.MarkdownClipboard.htmlTableCells(clipboard.getHtml());
                if (cells != null) {
                    int row = activeRow;
                    int column = activeColumn;
                    applyTableEdit(activeTable, table -> MarkdownTableEdits.pasteMarkdown(table, row, column, cells));
                } else cellEditor.replaceSelection(com.allan.atools.richtext.codearea.MarkdownClipboard.fromHtml(clipboard.getHtml()));
                return;
            }
        }
        if (value == null) return;
        cellEditor.replaceSelection(value);
    }

    private void optimize(MarkdownTableDocumentState.Table table) {
        if (deferWhileComposing(() -> optimize(table))) {
            return;
        }
        var latest = latestTable(table);
        if (latest != table) {
            if (latest != null) optimize(latest);
            return;
        }
        if (!canModify(table)) {
            return;
        }
        String source = safeTableSource(table);
        String formatted = source == null ? null : MarkdownTableOptimizeManager.format(currentArea, source);
        if (formatted != null && !formatted.equals(source)) {
            replaceTable(table, formatted, activeRow, activeColumn);
        }
    }

    private boolean canModify(MarkdownTableDocumentState.Table table) {
        return table != null && table.valid() && currentArea != null && currentArea.isEditable() && !structurePending;
    }

    private boolean deferWhileComposing(Runnable action) {
        if (!composingText && !handlingInputMethod) {
            return false;
        }
        afterComposition = action;
        return true;
    }

    private void replaceTable(MarkdownTableDocumentState.Table table, String replacement, int row, int column) {
        if (replacement == null || !canModify(table)) {
            return;
        }
        structurePending = true;
        cellEditor.setEditable(false);
        pendingTableId = table.id();
        pendingRow = row;
        pendingColumn = column;
        pendingSourceOffset = table.mode() == MarkdownTableDocumentState.Mode.SOURCE
                ? currentArea.getCaretPosition() - table.startOffset() : -1;
        currentArea.getUndoManager().preventMerge();
        activeTable = table;
        writingStructure = true;
        try {
            currentArea.replaceText(table.startOffset(), table.endOffset(), replacement);
        } finally {
            writingStructure = false;
        }
        currentArea.getUndoManager().preventMerge();
        refreshScheduler.startNow();
    }

    private void restorePendingCell() {
        if (pendingTableId == null) {
            syncActiveCellAfterParse();
            return;
        }
        for (var table : tables) {
            if (!table.id().equals(pendingTableId) || !table.valid()) {
                continue;
            }
            var layout = layouts.get(table.id());
            if (table.mode() == MarkdownTableDocumentState.Mode.TABLE
                    && (layout == null || layout.table != table || layout.cells == null)) {
                return;
            }
            structurePending = false;
            cellEditor.setEditable(currentArea.isEditable());
            resumePendingUndo();
            int row = pendingRow;
            int column = pendingColumn;
            pendingTableId = null;
            pendingRow = -1;
            pendingColumn = -1;
            int sourceOffset = pendingSourceOffset;
            pendingSourceOffset = -1;
            if (row >= 0 && !table.rows().isEmpty()) {
                if (row >= table.rows().size()) {
                    row = table.rows().size() - 1;
                }
                if (column < 0) {
                    column = 0;
                } else if (column >= table.alignments().size()) {
                    column = table.alignments().size() - 1;
                }
                if (table.mode() == MarkdownTableDocumentState.Mode.TABLE) {
                    activateCell(table, row, column, 12);
                } else {
                    endCellEditing(false);
                    currentArea.moveTo(table.rows().get(row).cells().get(column).startOffset());
                    currentArea.requestFocus();
                }
            } else {
                int target = sourceOffset < 0 ? table.startOffset()
                        : table.startOffset() + Math.min(sourceOffset, table.endOffset() - table.startOffset());
                endCellEditing(false);
                currentArea.moveTo(Math.min(target, currentArea.getLength()));
                currentArea.requestFocus();
            }
            return;
        }
        pendingTableId = null;
        pendingRow = -1;
        pendingColumn = -1;
        pendingSourceOffset = -1;
        structurePending = false;
        endCellEditingAtFallback();
        resumePendingUndo();
    }

    private void syncActiveCellAfterParse() {
        if (activeTable == null) {
            return;
        }
        String id = activeTable.id();
        for (var table : tables) {
            if (table.id().equals(id) && table.valid() && activeRow < table.rows().size()
                    && activeColumn < table.alignments().size()) {
                activeTable = table;
                syncEditorFromDocument();
                return;
            }
        }
        endCellEditingAtFallback();
    }

    private void endCellEditingAtFallback() {
        int target = currentArea == null ? 0 : currentArea.getCaretPosition();
        if (activeTable != null && activeRow >= 0 && activeRow < activeTable.rows().size()
                && activeColumn >= 0 && activeColumn < activeTable.alignments().size()) {
            target = activeTable.rows().get(activeRow).cells().get(activeColumn).startOffset();
        }
        endCellEditing(false);
        if (currentArea != null) {
            if (target < 0) {
                target = 0;
            } else if (target > currentArea.getLength()) {
                target = currentArea.getLength();
            }
            currentArea.moveTo(target);
            currentArea.requestFocus();
        }
    }

    private void recreateTableGraphics(MarkdownTableDocumentState.Table table) {
        if (!hasVisibleParagraph(currentArea)) {
            return;
        }
        int first = Math.max(table.firstLine(), currentArea.firstVisibleParToAllParIndex());
        int last = Math.min(table.lastLine(), currentArea.lastVisibleParToAllParIndex());
        for (int line = first; line <= last; line++) {
            recreateParagraphGraphic(line);
        }
    }

    private void recreateParagraphGraphic(int line) {
        if (currentArea != null && line >= 0 && line < currentArea.getParagraphs().size()) {
            currentArea.recreateParagraphGraphic(line);
        }
    }

    private String safeTableSource(MarkdownTableDocumentState.Table table) {
        if (table == null || table.startOffset() < 0 || table.endOffset() > currentArea.getLength()
                || table.startOffset() > table.endOffset()) {
            return null;
        }
        return currentArea.getText(table.startOffset(), table.endOffset());
    }

    private static String decodeCell(String source) {
        return MarkdownTableCellText.decode(source);
    }

    /** 当前布局数值和可见行的弱引用，不持有离屏单元格节点。 */
    private final class TableLayout {
        private MarkdownTableDocumentState.Table table;
        private MarkdownTableLayout.Cell[][] cells;
        private double[] widths;
        private double totalWidth;
        private double viewportWidth;
        private Font font;
        private List<MarkdownTableDocumentState.Alignment> alignments = List.of();
        private boolean frozenWidths;
        private Map<Integer, Double> rowHeights = new HashMap<>();
        private long revision;
        private long structureRevision;
        private long measuredVersion = -1;
        private final Set<HBox> contents = Collections.newSetFromMap(new WeakHashMap<>());
        private final Set<RowGraphic> graphics = Collections.newSetFromMap(new WeakHashMap<>());

        private void applyOffset(double value) {
            for (var graphic : List.copyOf(graphics)) {
                graphic.content.setTranslateX(-value);
                if (graphic.scrollBar != null) {
                    graphic.syncingScrollBar = true;
                    graphic.scrollBar.setValue(value);
                    graphic.syncingScrollBar = false;
                }
            }
        }
    }

    private record CellTarget(MarkdownTableDocumentState.Table table, int row, int column) {}
}

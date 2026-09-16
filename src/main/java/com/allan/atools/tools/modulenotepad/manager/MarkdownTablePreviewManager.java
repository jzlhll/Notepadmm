package com.allan.atools.tools.modulenotepad.manager;

import static com.allan.atools.richtext.codearea.MarkdownEditorSupport.supportsMarkdown;
import static com.allan.atools.richtext.codearea.MarkdownEditorSupport.textLeftPadding;
import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState;
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownAstCache;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.uilibs.richtexts.CodeArea;
import javafx.animation.PauseTransition;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
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
import javafx.stage.Popup;
import javafx.util.Duration;
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
    private static final long TOOLBAR_VISIBILITY_WINDOW_NANOS = 3_000_000_000L;
    private final LatestRefreshScheduler refreshScheduler =
            new LatestRefreshScheduler(180, 420, this::startRefresh);
    private final PauseTransition toolbarVisibilityDelay = new PauseTransition();
    private final InvalidationListener layoutChanged = observable -> requestLayoutRefresh();
    private final InvalidationListener selectionChanged = observable -> {
        hideCellMenu();
        updateSelection();
    };
    private final InvalidationListener editableChanged = observable -> updateToolbar();
    private final EventHandler<MouseEvent> areaMouseMoved = this::onAreaMouseMoved;
    private final EventHandler<MouseEvent> areaMouseExited = this::onAreaMouseExited;
    private final EventHandler<MouseEvent> areaMouseDragged = this::onAreaMouseDragged;
    private final TextArea cellEditor = new TextArea();
    private final Map<Integer, MarkdownTableDocumentState.Table> tableByLine = new HashMap<>();
    private final Set<Integer> previewLines = new HashSet<>();
    private final Map<Integer, Integer> rowIndexByLine = new HashMap<>();
    private Map<String, List<MarkdownTableLayout.Run>> inlineContents = Map.of();
    private final TextFlow measureFlow = new TextFlow();
    private final Text editorMeasureText = new Text();
    private final ArrayDeque<LayoutJob> layoutJobs = new ArrayDeque<>();
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
            while (!layoutJobs.isEmpty() && System.nanoTime() < deadline) {
                var job = layoutJobs.peek();
                if (!job.isCurrent()) {
                    layoutJobs.remove();
                } else if (job.step()) {
                    layoutJobs.remove();
                    job.apply();
                }
            }
            if (layoutJobs.isEmpty() && !layoutPending) {
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
    private long toolbarContentVersion = -1;
    private String toolbarContentTableId;
    private boolean toolbarOptimized;

    private EditorArea currentArea;
    private Subscription textChanges;
    private Subscription viewportChanges;
    private Future<?> parseTask;
    private List<MarkdownTableDocumentState.Table> tables = List.of();
    private ContextMenu cellMenu;
    private Popup toolbarPopup;
    private HBox toolbar;
    private Button addRowButton;
    private Button addColumnButton;
    private Button optimizeButton;
    private Button modeButton;
    private MarkdownTableDocumentState.Table toolbarTable;
    private MarkdownTableDocumentState.Table pendingToolbarTable;
    private MarkdownTableDocumentState.Table hoverTable;
    private MarkdownTableDocumentState.Table activeTable;
    private int activeRow = -1;
    private int activeColumn = -1;
    private String pendingTableId;
    private int pendingRow = -1;
    private int pendingColumn = -1;
    private int pendingSourceOffset = -1;
    private boolean destroyed;
    private boolean updatingPresentation;
    private boolean updatingCellEditor;
    private boolean writingCell;
    private boolean writingStructure;
    private boolean layoutPending;
    private boolean toolbarVisibilityPending;
    private boolean toolbarVisibilityChanged;
    private long lastToolbarVisibilityChangeNanos;
    private double graphicWidth;
    private double textPadding;
    private double layoutWidth = -1;
    private Font font;

    public MarkdownTablePreviewManager(EditorArea area) {
        toolbarVisibilityDelay.setOnFinished(event -> applyPendingToolbarVisibility());
        configureCellEditor();
        refreshCurrentFile(area);
    }

    public void refreshCurrentFile(EditorArea area) {
        unbindEditor();
        currentArea = area;
        if (!supportsMarkdown(area)) {
            return;
        }
        textChanges = area.plainTextChanges().subscribe(change ->
                onTextChanged(change.getPosition(), change.getRemoved(), change.getInserted()));
        viewportChanges = area.viewportDirtyEvents().subscribe(event -> {
            requestLayoutRefresh();
            // viewportDirty 仅由 scale 与滚动偏移变化触发：编辑器滚动时立即隐藏悬浮表头。
            // 不调用 updateToolbar，避免过滤窗口已过期时被同帧重新显示；hideToolbar 不重置过滤时间
            hideToolbar();
        });
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
        area.addEventHandler(MouseEvent.MOUSE_MOVED, areaMouseMoved);
        area.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, areaMouseExited);
        area.addEventFilter(MouseEvent.MOUSE_DRAGGED, areaMouseDragged);
        area.addParagraphGraphicDecorator(this, this::createGraphic);
        refreshScheduler.startNow();
    }

    public void destroy() {
        destroyed = true;
        unbindEditor();
        refreshScheduler.dispose();
        disposeToolbar();
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
                    if (currentArea != null && currentArea.isFocused() && !cellEditor.isFocused()) {
                        endCellEditing(false);
                    }
                    updateToolbar();
                });
            }
        });
    }

    private void unbindEditor() {
        inputMethodRevision++;
        composingText = false;
        handlingInputMethod = false;
        afterComposition = null;
        layoutTimer.stop();
        layoutJobs.clear();
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
            area.removeEventHandler(MouseEvent.MOUSE_MOVED, areaMouseMoved);
            area.removeEventHandler(MouseEvent.MOUSE_EXITED_TARGET, areaMouseExited);
            area.removeEventFilter(MouseEvent.MOUSE_DRAGGED, areaMouseDragged);
            clearPresentation();
            area.removeParagraphGraphicDecorator(this);
        }
        currentArea = null;
        endCellEditing(false);
        hideToolbar();
        toolbarVisibilityChanged = false;
        currentArea = null;
        layoutTimer.stop();
        layoutJobs.clear();
        tables = List.of();
        tableByLine.clear();
        rowIndexByLine.clear();
        inlineContents = Map.of();
        measureFlow.getChildren().clear();
        editorMeasureText.setText("");
        cellEditor.clear();
        layouts.clear();
        layoutWidth = -1;
    }

    private void onTextChanged(int position, String removed, String inserted) {
        var area = currentArea;
        if (area == null) {
            return;
        }
        if (area.getEditor().isRealtimeProcessingLimitReached()) {
            layoutJobs.clear();
            inlineContents = Map.of();
            area.getMarkdownTableDocumentState().reset();
            tables = List.of();
            rebuildLineIndex();
            updatePresentation();
            endCellEditing(false);
            hideToolbar();
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
        if ((writingCell || writingStructure) && activeTable != null) {
            area.getMarkdownTableDocumentState().applyKnownTableChange(
                    activeTable.id(), position, removed, inserted);
            if (writingStructure) {
                rebuildLineIndex();
                updatePresentation();
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
        // 怀疑表格前方增删换行后，部分复用的行节点未与新行号同步，导致预览看似断成两块。
        // 暂在行映射和样式更新后重建受影响表格的可见节点；根因尚未复现确认，后续可据此调整。
        for (var table : shiftedTables) {
            if (table.valid() && table.mode() == MarkdownTableDocumentState.Mode.TABLE && hasTableLayout(table)) {
                recreateTableGraphics(table);
            }
        }
        if (!writingCell && !writingStructure) {
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
        var previousContents = inlineContents;
        parseTask = ThreadUtils.submit(() -> {
            List<MarkdownTableDocumentState.Table> parsed = null;
            var contents = new HashMap<String, List<MarkdownTableLayout.Run>>();
            try {
                parsed = MarkdownTableDocumentState.parse(text);
                if (parsed != null) {
                    var parser = new MarkdownAstCache();
                    for (var table : parsed) {
                        for (var row : table.rows()) {
                            for (var cell : row.cells()) {
                                if (Thread.currentThread().isInterrupted()) {
                                    return;
                                }
                                String source = cell.source();
                                if (!contents.containsKey(source)) {
                                    var runs = previousContents.get(source);
                                    contents.put(source, runs == null
                                            ? MarkdownTableLayout.parse(parser.parse(source)) : runs);
                                }
                            }
                        }
                    }
                }
            } catch (RuntimeException exception) {
                Log.e("parse markdown tables failed", exception);
            }
            var result = parsed;
            Platform.runLater(() -> refreshScheduler.complete(requestId, () -> {
                parseTask = null;
                if (destroyed || area != currentArea || result == null
                        || version != area.getEditor().getContentVersion()
                        || area.getEditor().isRealtimeProcessingLimitReached()) {
                    return;
                }
                area.getMarkdownTableDocumentState().reconcile(result);
                tables = area.getMarkdownTableDocumentState().getTables();
                inlineContents = Map.copyOf(contents);
                rebuildLineIndex();
                if (pendingTableId == null) {
                    syncActiveCellAfterParse();
                }
                applyParsedLayout();
            }));
        });
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
            toolbarContentVersion = -1;
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
        updatePresentation();
        for (var table : tables) {
            if (table.valid()) {
                var layout = tableLayout(table);
                boolean editing = activeTable != null && activeTable.id().equals(table.id());
                layoutJobs.add(new LayoutJob(table, layout, editing && !resize));
            }
        }
        layoutTimer.start();
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

        LayoutJob(MarkdownTableDocumentState.Table table, TableLayout layout, boolean freezeWidths) {
            this.table = table;
            this.layout = layout;
            int columns = table.alignments().size();
            cells = new MarkdownTableLayout.Cell[table.rows().size()][columns];
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
            }
            if (!measuringHeights) {
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
            }
            if (++column == natural.length) {
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
            layout.rowHeights.clear();
            for (int index = 0; index < cells.length; index++) {
                updateRowHeight(table, index, false);
            }
            double maxOffset = Math.max(0, layout.totalWidth - available);
            table.setHorizontalOffset(Math.min(table.horizontalOffset(), maxOffset));
            layout.applyOffset(table.horizontalOffset());
            if (structureChanged) {
                recreateTableGraphics(table);
            } else {
                for (var graphic : List.copyOf(layout.graphics)) {
                    if (graphic.getScene() != null) {
                        graphic.refresh();
                    }
                }
            }
            updateTablePresentation(table);
            if (pendingTableId != null && pendingTableId.equals(table.id())) {
                restorePendingCell();
            }
            updateToolbar();
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
        var area = currentArea;
        if (updatingPresentation || area == null) {
            return;
        }
        updatingPresentation = true;
        try {
            area.suspendVisibleParsWhileInvoke(() -> {
                var wantedLines = new HashSet<Integer>();
                for (var table : tables) {
                    if (table.valid() && table.mode() == MarkdownTableDocumentState.Mode.TABLE
                            && hasTableLayout(table)) {
                        for (int line = table.firstLine(); line <= table.lastLine(); line++) {
                            wantedLines.add(line);
                        }
                    }
                }
                for (int line : List.copyOf(previewLines)) {
                    if (!wantedLines.contains(line)) {
                        setPreviewStyle(line, null);
                    }
                }
                for (var table : tables) {
                    boolean preview = table.valid() && table.mode() == MarkdownTableDocumentState.Mode.TABLE
                            && hasTableLayout(table);
                    for (int line = table.firstLine(); line <= table.lastLine(); line++) {
                        Double height = null;
                        if (preview) {
                            height = paragraphHeight(table, line);
                        }
                        setPreviewStyle(line, height);
                    }
                }
            });
        } finally {
            updatingPresentation = false;
        }
        updateToolbar();
    }

    private void updateTablePresentation(MarkdownTableDocumentState.Table table) {
        if (currentArea == null || updatingPresentation) {
            return;
        }
        updatingPresentation = true;
        try {
            currentArea.suspendVisibleParsWhileInvoke(() -> {
                boolean preview = table.valid() && table.mode() == MarkdownTableDocumentState.Mode.TABLE;
                for (int line = table.firstLine(); line <= table.lastLine(); line++) {
                    setPreviewStyle(line, preview ? paragraphHeight(table, line) : null);
                }
            });
        } finally {
            updatingPresentation = false;
        }
    }

    private void clearPresentation() {
        for (int line : List.copyOf(previewLines)) {
            setPreviewStyle(line, null);
        }
        previewLines.clear();
    }

    private boolean setPreviewStyle(int line, Double height) {
        var area = currentArea;
        if (area == null) {
            return false;
        }
        if (line < 0 || line >= area.getParagraphs().size()) {
            previewLines.remove(line);
            return false;
        }
        boolean preview = height != null;
        boolean wasPreview = previewLines.contains(line);
        var existing = area.getParagraph(line).getParagraphStyle();
        var styles = new ArrayList<>(existing);
        styles.removeIf(style -> style.equals(PREVIEW_CLASS) || style.startsWith(HEIGHT_PREFIX));
        if (preview) {
            styles.add(PREVIEW_CLASS);
            styles.add(HEIGHT_PREFIX + height);
            previewLines.add(line);
        } else {
            previewLines.remove(line);
        }
        boolean modeChanged = existing.contains(PREVIEW_CLASS) != preview;
        boolean presentationChanged = wasPreview != preview;
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
        if (table == null || !table.valid() || table.mode() != MarkdownTableDocumentState.Mode.TABLE
                || layout == null || layout.widths == null || layout.table != table) {
            return base;
        }
        int row = rowIndexByLine.getOrDefault(line, -1);
        return new RowGraphic(layout, row, base);
    }

    /** 只为虚拟列表中的行持有节点；高度、列宽、选择变化均直接更新现有节点。 */
    private final class RowGraphic extends Pane {
        private final TableLayout layout;
        private final int row;
        private final Node base;
        private final HBox content = new HBox();
        private final Pane viewport = new Pane(content);
        private final Rectangle clip = new Rectangle();
        private ScrollBar scrollBar;
        private boolean syncingScrollBar;

        RowGraphic(TableLayout layout, int row, Node base) {
            this.layout = layout;
            this.row = row;
            this.base = base;
            content.setManaged(false);
            viewport.setManaged(false);
            viewport.setClip(clip);
            viewport.layoutXProperty().bind(Bindings.createDoubleBinding(
                    () -> getWidth() + textLeftPadding(currentArea)
                            - currentArea.estimatedScrollXProperty().getValue(),
                    widthProperty(), currentArea.paddingProperty(), currentArea.estimatedScrollXProperty()));
            getChildren().add(viewport);
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
            setOnMouseEntered(event -> {
                hoverTable = layout.table;
                updateToolbar();
            });
            setOnMouseExited(event -> clearHoverLater(layout.table));
            addEventFilter(ScrollEvent.SCROLL, event -> scrollTable(layout.table, event));
            if (base != null) {
                base.setOpacity(0);
                getChildren().add(base);
            }
            refresh();
        }

        int line() {
            return row < 0 ? layout.table.firstLine() + 1 : layout.table.rows().get(row).line();
        }

        void refresh() {
            if (row >= layout.table.rows().size() || row >= layout.cells.length) {
                return;
            }
            content.setTranslateX(-layout.table.horizontalOffset());
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
            requestLayout();
        }

        void updateSelectionStyle() {
            if (row >= layout.table.rows().size()) {
                return;
            }
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
            return paragraphHeight(layout.table, line());
        }

        @Override
        protected void layoutChildren() {
            double height = layout.rowHeights.getOrDefault(row, 2.0);
            if (base != null) {
                base.resizeRelocate(0, 0, getWidth(), getHeight());
            }
            clip.setWidth(Math.min(layout.viewportWidth, layout.totalWidth));
            clip.setHeight(height);
            viewport.resize(clip.getWidth(), height);
            content.resize(layout.totalWidth, height);
            if (scrollBar != null) {
                scrollBar.resize(layout.viewportWidth, MarkdownTableLayout.SCROLL_HEIGHT);
                scrollBar.setLayoutY(height);
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
                var table = graphic.layout.table;
                if (event.getButton() == MouseButton.PRIMARY) {
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
            var layout = graphic.layout;
            var cell = layout.cells[graphic.row][column];
            if (displayed == null || !displayed.runs.equals(cell.runs)
                    || !displayed.font.equals(cell.font) || displayed.header != cell.header) {
                MarkdownTableLayout.fill(text, cell.runs, cell.font, cell.header);
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
        if (deferWhileComposing(() -> activateCell(table, row, column, clickX))) {
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
        boolean sameCell = previousTable != null && previousTable.id().equals(table.id())
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
        cellEditor.setEditable(currentArea.isEditable());
        if (previousTable != null && (previousTable != table || previousRow != row)) {
            updateRowHeight(previousTable, previousRow, true);
        }
        updateActiveRowHeight();
        refreshRowGraphics(table, row);
        if (previousTable != null && !previousTable.id().equals(table.id())) {
            queueLayouts(false);
        }
        Platform.runLater(() -> {
            if (currentArea != null && activeTable == table && activeRow == row && activeColumn == column) {
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
        if (updatingCellEditor || handlingInputMethod || composingText
                || activeTable == null || currentArea == null || !currentArea.isEditable()
                || !activeTable.valid() || activeRow < 0 || activeRow >= activeTable.rows().size()) {
            return;
        }
        var row = activeTable.rows().get(activeRow);
        if (activeColumn < 0 || activeColumn >= row.cells().size()) {
            return;
        }
        var cell = row.cells().get(activeColumn);
        String encoded = encodeCell(value);
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
            double cellHeight = layout.cells[row][column].height;
            if (activeTable != null && activeTable.id().equals(table.id())
                    && activeRow == row && activeColumn == column) {
                editorMeasureText.setFont(layout.font);
                editorMeasureText.setText(cellEditor.getText().isEmpty() ? " " : cellEditor.getText() + "\u200b");
                editorMeasureText.setWrappingWidth(Math.max(1,
                        layout.widths[column] - MarkdownTableLayout.HORIZONTAL_INSETS));
                cellHeight = editorMeasureText.getLayoutBounds().getHeight() + MarkdownTableLayout.VERTICAL_INSETS;
            }
            height = Math.max(height, cellHeight);
        }
        int line = table.rows().get(row).line();
        layout.rowHeights.put(row, Math.ceil(height));
        if (present && currentArea != null && table.mode() == MarkdownTableDocumentState.Mode.TABLE) {
            setPreviewStyle(line, paragraphHeight(table, line));
            refreshRowGraphics(table, row);
        }
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
        if (line == table.lastLine() && layout.totalWidth > layout.viewportWidth) {
            height += MarkdownTableLayout.SCROLL_HEIGHT;
        }
        return height;
    }

    private void syncEditorFromDocument() {
        if (composingText || handlingInputMethod || activeTable == null
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
        if (event.getCode() == KeyCode.TAB) {
            event.consume();
            moveCell(event.isShiftDown() ? -1 : 1);
            return;
        }
        if (event.isShortcutDown() && (event.getCode() == KeyCode.Z || event.getCode() == KeyCode.Y)) {
            event.consume();
            performUndoRedo(event.isShiftDown() || event.getCode() == KeyCode.Y);
            return;
        }
        if (event.isShortcutDown() && !event.isShiftDown() && event.getCode() == KeyCode.B) {
            event.consume();
            wrapCellSelection("**");
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.BACK_QUOTE) {
            event.consume();
            wrapCellSelection("`");
        }
    }

    private void onCellKeyTyped(KeyEvent event) {
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
        int start = cellEditor.getSelection().getStart();
        int end = cellEditor.getSelection().getEnd();
        String selected = cellEditor.getSelectedText();
        cellEditor.replaceText(start, end, mark + selected + mark);
        cellEditor.selectRange(start + mark.length(), end + mark.length());
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
            int target = direction < 0 ? table.startOffset() : table.endOffset();
            endCellEditing(false);
            currentArea.moveTo(target);
            currentArea.requestFocus();
            return;
        }
        activateCell(table, index / columns, index % columns, direction < 0 ? Double.MAX_VALUE : 12);
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
        var paste = new MenuItem(Locales.str("markdownTablePaste"));
        var selectAll = new MenuItem(Locales.str("markdownTableSelectAll"));
        var deleteRow = new MenuItem(Locales.str("markdownTableDeleteRow"));
        var deleteColumn = new MenuItem(Locales.str("markdownTableDeleteColumn"));
        var menu = new ContextMenu(undo, redo, cut, copy, paste, selectAll, deleteRow, deleteColumn);
        cellMenu = menu;
        undo.setOnAction(event -> runUndo(false));
        redo.setOnAction(event -> runUndo(true));
        cut.setOnAction(event -> cellEditor.cut());
        copy.setOnAction(event -> cellEditor.copy());
        paste.setOnAction(event -> cellEditor.paste());
        selectAll.setOnAction(event -> cellEditor.selectAll());
        deleteRow.setOnAction(event -> deleteRow(table, row));
        deleteColumn.setOnAction(event -> deleteColumn(table, column));
        menu.setOnShowing(event -> {
            boolean editable = area.isEditable();
            undo.setDisable(!editable || !area.getUndoManager().isUndoAvailable());
            redo.setDisable(!editable || !area.getUndoManager().isRedoAvailable());
            cut.setDisable(!editable || cellEditor.getSelectedText().isEmpty());
            copy.setDisable(cellEditor.getSelectedText().isEmpty());
            paste.setDisable(!editable || !Clipboard.getSystemClipboard().hasString());
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

    private void performUndoRedo(boolean redo) {
        if (deferWhileComposing(() -> performUndoRedo(redo))) {
            return;
        }
        currentArea.getUndoManager().preventMerge();
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

    private void ensureToolbar() {
        if (toolbarPopup != null) {
            return;
        }
        addRowButton = toolbarButton("markdownTableAddRow", event -> addRow(toolbarTable));
        addColumnButton = toolbarButton("markdownTableAddColumn", event -> addColumn(toolbarTable));
        optimizeButton = toolbarButton("markdownTableOptimize", event -> optimize(toolbarTable));
        modeButton = toolbarButton("markdownTableShowSource", event -> toggleMode(toolbarTable));
        toolbar = new HBox(4, addRowButton, addColumnButton, optimizeButton, modeButton);
        toolbar.getStyleClass().add("markdown-table-toolbar");
        toolbar.setPadding(new Insets(4));
        toolbar.setOnMouseEntered(event -> updateToolbar());
        toolbar.setOnMouseExited(event -> {
            hoverTable = null;
            updateToolbar();
        });
        toolbarPopup = new Popup();
        toolbarPopup.setAutoFix(true);
        toolbarPopup.setAutoHide(false);
        toolbarPopup.getContent().add(toolbar);
        if (currentArea != null && currentArea.getScene() != null) {
            toolbar.getStylesheets().setAll(currentArea.getScene().getStylesheets());
        }
    }

    private Button toolbarButton(String key, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        var button = new Button(Locales.str(key));
        button.setOnAction(action);
        button.getStyleClass().add("markdown-table-toolbar-button");
        return button;
    }

    private void updateToolbar() {
        var area = currentArea;
        if (!hasShowingWindow(area)) {
            hideToolbar();
            return;
        }
        var table = hoverTable;
        if (table == null && cellEditor.isFocused()) {
            table = activeTable;
        }
        if (table == null && area.isFocused()) {
            table = tableAtOffset(area.getCaretPosition());
        }
        if (table == null || !isTableVisible(table)) {
            if (toolbar != null && toolbar.isHover()) {
                cancelPendingToolbarVisibility();
            } else {
                requestToolbarVisibility(null);
            }
            return;
        }
        requestToolbarVisibility(table);
    }

    private void requestToolbarVisibility(MarkdownTableDocumentState.Table table) {
        boolean shouldShow = table != null;
        boolean showing = toolbarPopup != null && toolbarPopup.isShowing();
        if (shouldShow == showing) {
            cancelPendingToolbarVisibility();
            if (shouldShow) {
                showToolbar(table);
            }
            return;
        }

        long now = System.nanoTime();
        long remaining = TOOLBAR_VISIBILITY_WINDOW_NANOS - (now - lastToolbarVisibilityChangeNanos);
        if (!toolbarVisibilityChanged || remaining <= 0) {
            cancelPendingToolbarVisibility();
            applyToolbarVisibility(table);
            return;
        }
        pendingToolbarTable = table;
        toolbarVisibilityPending = true;
        toolbarVisibilityDelay.setDuration(Duration.millis(remaining / 1_000_000.0));
        toolbarVisibilityDelay.playFromStart();
    }

    private void applyPendingToolbarVisibility() {
        if (!toolbarVisibilityPending) {
            return;
        }
        var table = pendingToolbarTable;
        toolbarVisibilityPending = false;
        pendingToolbarTable = null;
        applyToolbarVisibility(table);
    }

    private void applyToolbarVisibility(MarkdownTableDocumentState.Table table) {
        toolbarVisibilityChanged = true;
        lastToolbarVisibilityChangeNanos = System.nanoTime();
        if (table == null) {
            hideToolbar();
        } else if (hasShowingWindow(currentArea) && isTableVisible(table)) {
            showToolbar(table);
        } else {
            hideToolbar();
        }
    }

    private void showToolbar(MarkdownTableDocumentState.Table table) {
        var area = currentArea;
        ensureToolbar();
        if (!toolbar.getStylesheets().equals(area.getScene().getStylesheets())) {
            toolbar.getStylesheets().setAll(area.getScene().getStylesheets());
        }
        toolbarTable = table;
        boolean editable = area.isEditable() && table.valid();
        addRowButton.setDisable(!editable);
        addColumnButton.setDisable(!editable);
        long version = area.getEditor().getContentVersion();
        if (toolbarContentVersion != version || !table.id().equals(toolbarContentTableId)) {
            String source = safeTableSource(table);
            String formatted = source == null ? null : MarkdownTableOptimizeManager.format(area, source);
            toolbarOptimized = formatted == null || formatted.equals(source);
            toolbarContentVersion = version;
            toolbarContentTableId = table.id();
        }
        optimizeButton.setDisable(!editable || toolbarOptimized);
        modeButton.setDisable(table.mode() == MarkdownTableDocumentState.Mode.SOURCE && !table.valid());
        modeButton.setText(Locales.str(table.mode() == MarkdownTableDocumentState.Mode.TABLE
                ? "markdownTableShowSource" : "markdownTableShowTable"));
        String reason = !area.isEditable() ? Locales.str("markdownTableReadonly")
                : !table.valid() ? Locales.str("markdownTableInvalid")
                : toolbarOptimized ? Locales.str("markdownTableOptimized") : null;
        optimizeButton.setTooltip(reason == null ? null : new Tooltip(reason));
        String modifyReason = !area.isEditable() ? Locales.str("markdownTableReadonly")
                : !table.valid() ? Locales.str("markdownTableInvalid") : null;
        addRowButton.setTooltip(modifyReason == null ? null : new Tooltip(modifyReason));
        addColumnButton.setTooltip(modifyReason == null ? null : new Tooltip(modifyReason));
        modeButton.setTooltip(table.valid() ? null : new Tooltip(Locales.str("markdownTableInvalid")));
        positionToolbar(table);
    }

    private void positionToolbar(MarkdownTableDocumentState.Table table) {
        var area = currentArea;
        Bounds areaBounds = area.localToScreen(area.getBoundsInLocal());
        Point2D textOrigin = area.localToScreen(
                area.getInsets().getLeft() + graphicWidth + textLeftPadding(area), 0);
        if (areaBounds == null || textOrigin == null) {
            hideToolbar();
            return;
        }
        Bounds headerBounds = visibleHeaderBounds(table);
        double x = headerBounds == null ? textOrigin.getX() : headerBounds.getMinX();
        double y = areaBounds.getMinY();
        if (headerBounds != null) {
            y = Math.max(areaBounds.getMinY(), headerBounds.getMinY()
                    - 42 - addRowButton.prefHeight(-1) / 2);
        }
        x = Math.min(x, Math.max(areaBounds.getMinX(),
                areaBounds.getMaxX() - toolbar.prefWidth(-1)));
        if (toolbarPopup.isShowing()) {
            toolbarPopup.setX(x);
            toolbarPopup.setY(y);
        } else {
            toolbarPopup.show(area, x, y);
        }
    }

    private Bounds visibleHeaderBounds(MarkdownTableDocumentState.Table table) {
        var area = currentArea;
        var layout = layouts.get(table.id());
        if (area == null || layout == null || layout.table != table) {
            return null;
        }
        for (var graphic : List.copyOf(layout.graphics)) {
            if (graphic.row != 0 || !graphic.isVisible() || graphic.getScene() != area.getScene()) {
                continue;
            }
            Bounds bounds = graphic.viewport.localToScreen(graphic.viewport.getBoundsInLocal());
            if (bounds != null) {
                return bounds;
            }
        }
        return null;
    }

    private boolean isTableVisible(MarkdownTableDocumentState.Table table) {
        var area = currentArea;
        if (!hasVisibleParagraph(area) || !tables.contains(table)) {
            return false;
        }

        var areaBounds = area.localToScreen(area.getBoundsInLocal());
        var layout = layouts.get(table.id());
        if (areaBounds != null && layout != null && layout.table == table) {
            for (var content : List.copyOf(layout.contents)) {
                if (!content.isVisible() || content.getScene() != area.getScene()) {
                    continue;
                }
                var contentBounds = content.localToScreen(content.getBoundsInLocal());
                if (contentBounds != null
                        && contentBounds.getMaxX() >= areaBounds.getMinX()
                        && contentBounds.getMinX() <= areaBounds.getMaxX()
                        && contentBounds.getMaxY() >= areaBounds.getMinY()
                        && contentBounds.getMinY() <= areaBounds.getMaxY()) {
                    return true;
                }
            }
        }

        int first = area.firstVisibleParToAllParIndex();
        int last = area.lastVisibleParToAllParIndex();
        if (first >= 0 && last >= first && table.firstLine() <= last && table.lastLine() >= first) {
            return true;
        }
        return false;
    }

    private static boolean hasShowingWindow(Node node) {
        return node != null && node.getScene() != null && node.getScene().getWindow() != null
                && node.getScene().getWindow().isShowing();
    }

    private static boolean hasVisibleParagraph(EditorArea area) {
        return hasShowingWindow(area) && !area.getVisibleParagraphs().isEmpty();
    }

    private void hideToolbar() {
        cancelPendingToolbarVisibility();
        if (toolbarPopup != null) {
            toolbarPopup.hide();
        }
        toolbarTable = null;
    }

    private void cancelPendingToolbarVisibility() {
        toolbarVisibilityDelay.stop();
        toolbarVisibilityPending = false;
        pendingToolbarTable = null;
    }

    private void disposeToolbar() {
        hideToolbar();
        if (toolbarPopup != null) {
            toolbarPopup.getContent().clear();
        }
        toolbarPopup = null;
        toolbar = null;
    }

    private void onAreaMouseMoved(MouseEvent event) {
        var area = currentArea;
        if (!hasVisibleParagraph(area)) {
            hoverTable = null;
            updateToolbar();
            return;
        }
        var hit = area.hit(event.getX(), event.getY()).getCharacterIndex();
        hoverTable = hit.isPresent() ? tableAtOffset(hit.getAsInt()) : null;
        updateToolbar();
    }

    private void onAreaMouseExited(MouseEvent event) {
        clearHoverLater(hoverTable);
    }

    private void clearHoverLater(MarkdownTableDocumentState.Table expected) {
        Platform.runLater(() -> {
            if (hoverTable == expected && (toolbar == null || !toolbar.isHover())) {
                hoverTable = null;
                updateToolbar();
            }
        });
    }

    private void onAreaMouseDragged(MouseEvent event) {
        if (isDescendant(event.getTarget(), cellEditor) || isDescendantOfType(event.getTarget(), ScrollBar.class)) {
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
        int sourceIndex = 0;
        int editorIndex = 0;
        int limit = Math.min(source.length(), Math.max(0, sourceOffset));
        while (sourceIndex < limit) {
            int breakLength = htmlBreakLength(source, sourceIndex);
            if (breakLength > 0 && sourceIndex + breakLength <= limit) {
                sourceIndex += breakLength;
            } else {
                sourceIndex++;
            }
            editorIndex++;
        }
        return editorIndex;
    }

    private static int htmlBreakLength(String source, int index) {
        for (String value : new String[]{"<br>", "<br/>", "<br />"}) {
            if (index + value.length() <= source.length()
                    && source.regionMatches(true, index, value, 0, value.length())) {
                return value.length();
            }
        }
        return 0;
    }

    private void toggleMode(MarkdownTableDocumentState.Table table) {
        if (deferWhileComposing(() -> toggleMode(table))) {
            return;
        }
        if (table == null) {
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

    private void addRow(MarkdownTableDocumentState.Table table) {
        if (deferWhileComposing(() -> addRow(table))) {
            return;
        }
        if (!canModify(table)) {
            return;
        }
        String source = safeTableSource(table);
        if (source == null) {
            return;
        }
        String row = emptyRow(table.alignments().size(), table.indent());
        replaceTable(table, source + table.lineEnding() + row,
                table.rows().size(), 0);
    }

    private void addColumn(MarkdownTableDocumentState.Table table) {
        if (deferWhileComposing(() -> addColumn(table))) {
            return;
        }
        if (!canModify(table)) {
            return;
        }
        int columns = table.alignments().size() + 1;
        replaceTable(table, serializeTable(table, table.rows(), columns, -1, -1), 0, columns - 1);
    }

    private void deleteRow(MarkdownTableDocumentState.Table table, int row) {
        if (deferWhileComposing(() -> deleteRow(table, row))) {
            return;
        }
        if (!canModify(table) || row <= 0 || row >= table.rows().size()) {
            return;
        }
        replaceTable(table, serializeTable(table, table.rows(), table.alignments().size(), row, -1),
                Math.min(row, table.rows().size() - 2), activeColumn);
    }

    private void deleteColumn(MarkdownTableDocumentState.Table table, int column) {
        if (deferWhileComposing(() -> deleteColumn(table, column))) {
            return;
        }
        if (!canModify(table) || table.alignments().size() <= 1) {
            return;
        }
        replaceTable(table, serializeTable(table, table.rows(), table.alignments().size() - 1, -1, column),
                activeRow, Math.min(column, table.alignments().size() - 2));
    }

    private void optimize(MarkdownTableDocumentState.Table table) {
        if (deferWhileComposing(() -> optimize(table))) {
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
        return table != null && table.valid() && currentArea != null && currentArea.isEditable();
    }

    private boolean deferWhileComposing(Runnable action) {
        if (!composingText && !handlingInputMethod) {
            return false;
        }
        afterComposition = action;
        return true;
    }

    private void replaceTable(MarkdownTableDocumentState.Table table, String replacement, int row, int column) {
        if (replacement == null) {
            return;
        }
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

    private String serializeTable(MarkdownTableDocumentState.Table table,
                                  List<MarkdownTableDocumentState.Row> rows, int columns,
                                  int skippedRow, int skippedColumn) {
        var out = new StringBuilder();
        int sourceColumns = table.alignments().size();
        int writtenRows = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            if (rowIndex == skippedRow) {
                continue;
            }
            if (writtenRows > 0) {
                out.append(table.lineEnding());
            }
            appendSerializedRow(out, table.indent(), rows.get(rowIndex), sourceColumns, columns, skippedColumn);
            writtenRows++;
            if (rowIndex == 0) {
                out.append(table.lineEnding());
                appendSerializedSeparator(out, table, sourceColumns, columns, skippedColumn);
            }
        }
        return out.toString();
    }

    private void appendSerializedRow(StringBuilder out, String indent, MarkdownTableDocumentState.Row row,
                                     int sourceColumns, int columns, int skippedColumn) {
        out.append(indent);
        int written = 0;
        for (int source = 0; source < sourceColumns && written < columns; source++) {
            if (source == skippedColumn) {
                continue;
            }
            out.append("| ").append(row.cells().get(source).source().strip()).append(' ');
            written++;
        }
        while (written++ < columns) {
            out.append("|   ");
        }
        out.append('|');
    }

    private void appendSerializedSeparator(StringBuilder out, MarkdownTableDocumentState.Table table,
                                           int sourceColumns, int columns, int skippedColumn) {
        out.append(table.indent());
        int written = 0;
        for (int source = 0; source < sourceColumns && written < columns; source++) {
            if (source == skippedColumn) {
                continue;
            }
            out.append("| ").append(separator(table.alignments().get(source))).append(' ');
            written++;
        }
        while (written++ < columns) {
            out.append("| --- ");
        }
        out.append('|');
    }

    private static String separator(MarkdownTableDocumentState.Alignment alignment) {
        return switch (alignment) {
            case DEFAULT -> "---";
            case LEFT -> ":---";
            case RIGHT -> "---:";
            case CENTER -> ":---:";
        };
    }

    private static String emptyRow(int columns, String indent) {
        return indent + "|   ".repeat(columns) + '|';
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
        endCellEditingAtFallback();
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
        return source.replaceAll("(?i)<br\\s*/?>", "\n");
    }

    private static String encodeCell(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
        var result = new StringBuilder(normalized.length());
        int slashes = 0;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '|') {
                if (slashes % 2 == 0) {
                    result.append('\\');
                }
                result.append(character);
                slashes = 0;
            } else {
                result.append(character);
                slashes = character == '\\' ? slashes + 1 : 0;
            }
        }
        return result.toString();
    }

    /** 当前布局数值和可见行的弱引用，不持有离屏单元格节点。 */
    private final class TableLayout {
        private MarkdownTableDocumentState.Table table;
        private MarkdownTableLayout.Cell[][] cells;
        private double[] widths;
        private double totalWidth;
        private double viewportWidth;
        private Font font;
        private final Map<Integer, Double> rowHeights = new HashMap<>();
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

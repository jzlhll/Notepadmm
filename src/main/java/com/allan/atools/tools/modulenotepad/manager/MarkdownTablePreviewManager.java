package com.allan.atools.tools.modulenotepad.manager;

import static com.allan.atools.richtext.codearea.MarkdownEditorSupport.supportsMarkdown;
import static com.allan.atools.richtext.codearea.MarkdownEditorSupport.textLeftPadding;

import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.EditorAreaMgrCode;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import com.allan.uilibs.richtexts.CodeArea;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Link;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.reactfx.Subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Forward;

/** Markdown 表格逐行预览；单击进入源码，光标或选区位于表格内时保持源码。 */
public final class MarkdownTablePreviewManager {
    private static final String PREVIEW_CLASS = "markdown-table-preview-para";
    private static final String HEIGHT_PREFIX = CodeArea.PARAGRAPH_PREVIEW_HEIGHT_PREFIX;
    private final LatestRefreshScheduler refreshScheduler =
            new LatestRefreshScheduler(220, 450, this::startRefresh);
    private final InvalidationListener selectionChanged = observable -> updatePresentation();
    private final InvalidationListener layoutChanged = observable -> requestLayoutRefresh();

    private EditorArea currentArea;
    private Subscription textChanges;
    private Subscription viewportChanges;
    private Future<?> parseTask;
    private List<Table> tables = List.of();
    private final Map<Integer, Table> tableByLine = new HashMap<>();
    private final Set<Integer> previewLines = new HashSet<>();
    private boolean destroyed;
    private boolean updating;
    private boolean layoutPending;
    private double graphicWidth;
    private double textPadding;
    private double layoutWidth = -1;
    private Font font;

    public MarkdownTablePreviewManager(EditorArea area) {
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
        viewportChanges = area.viewportDirtyEvents().subscribe(event -> requestLayoutRefresh());
        area.caretPositionProperty().addListener(selectionChanged);
        area.selectionProperty().addListener(selectionChanged);
        area.widthProperty().addListener(layoutChanged);
        area.sceneProperty().addListener(layoutChanged);
        area.paddingProperty().addListener(layoutChanged);
        area.wrapTextProperty().addListener(layoutChanged);
        area.paragraphGraphicFactoryProperty().addListener(layoutChanged);
        UIContext.getFontSizeProperty().addListener(layoutChanged);
        UIContext.getFontThemeProperty().addListener(layoutChanged);
        area.addParagraphGraphicDecorator(this, this::createGraphic);
        refreshScheduler.startNow();
    }

    public void destroy() {
        destroyed = true;
        unbindEditor();
        refreshScheduler.dispose();
    }

    private void unbindEditor() {
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
        if (area == null) {
            return;
        }
        area.caretPositionProperty().removeListener(selectionChanged);
        area.selectionProperty().removeListener(selectionChanged);
        area.widthProperty().removeListener(layoutChanged);
        area.sceneProperty().removeListener(layoutChanged);
        area.paddingProperty().removeListener(layoutChanged);
        area.wrapTextProperty().removeListener(layoutChanged);
        area.paragraphGraphicFactoryProperty().removeListener(layoutChanged);
        UIContext.getFontSizeProperty().removeListener(layoutChanged);
        UIContext.getFontThemeProperty().removeListener(layoutChanged);
        clearTables();
        area.removeParagraphGraphicDecorator(this);
        currentArea = null;
    }

    private void onTextChanged(int position, String removed, String inserted) {
        var area = currentArea;
        int firstLine = area.offsetToPosition(Math.min(position, area.getLength()), Forward).getMajor();
        int removedLines = (int) removed.chars().filter(ch -> ch == '\n').count();
        int insertedLines = (int) inserted.chars().filter(ch -> ch == '\n').count();
        var shifted = new HashSet<Integer>();
        for (int line : previewLines) {
            if (line < firstLine) {
                shifted.add(line);
            } else if (line > firstLine + removedLines) {
                shifted.add(line + insertedLines - removedLines);
            }
        }
        // 新段落可能继承插入位置的预览样式，连同受影响的原段落一起清理。
        for (int line = firstLine; line <= firstLine + insertedLines; line++) {
            shifted.add(line);
        }
        previewLines.clear();
        previewLines.addAll(shifted);
        clearTables();
        refreshScheduler.request();
    }

    private void clearTables() {
        tables = List.of();
        tableByLine.clear();
        layoutWidth = -1;
        for (int line : List.copyOf(previewLines)) {
            setPreviewStyle(line, null);
        }
        previewLines.clear();
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
            List<Table> parsed = null;
            try {
                parsed = parseTables(area, text);
            } catch (RuntimeException exception) {
                Log.e("parse markdown table previews failed", exception);
            }
            var result = parsed;
            Platform.runLater(() -> refreshScheduler.complete(requestId, () -> {
                parseTask = null;
                if (destroyed || area != currentArea || result == null
                        || version != area.getEditor().getContentVersion()
                        || area.getEditor().isRealtimeProcessingLimitReached()) {
                    return;
                }
                clearTables();
                tables = result;
                for (var table : tables) {
                    for (int line = table.firstLine; line <= table.lastLine; line++) {
                        tableByLine.put(line, table);
                    }
                }
                refreshLayout();
            }));
        });
    }

    private void requestLayoutRefresh() {
        if (layoutPending || currentArea == null || destroyed) {
            return;
        }
        layoutPending = true;
        Platform.runLater(() -> {
            layoutPending = false;
            if (!destroyed && currentArea != null) {
                refreshLayout();
            }
        });
    }

    private void refreshLayout() {
        var area = currentArea;
        if (tables.isEmpty() || area.getWidth() <= 0 || area.getScene() == null) {
            return;
        }
        area.applyCss();
        Node lineNumber = area.lookup(".lineno");
        double nextGraphicWidth = lineNumber == null ? 0 : lineNumber.prefWidth(-1);
        double nextTextPadding = textLeftPadding(area);
        var nextFont = Font.font("JetBrains Mono", UIContext.getFontSizeProperty().get());
        double available = Math.max(24, area.getWidth() - area.getInsets().getLeft()
                - area.getInsets().getRight() - nextGraphicWidth - nextTextPadding);
        // 首次挂载、行号完成布局或 CSS 更新后重新测量；普通滚动沿用已有单元格布局。
        if (layoutWidth == available && graphicWidth == nextGraphicWidth
                && textPadding == nextTextPadding && nextFont.equals(font)) {
            return;
        }
        layoutWidth = available;
        graphicWidth = nextGraphicWidth;
        textPadding = nextTextPadding;
        font = nextFont;
        for (var table : tables) {
            measureTable(table, available);
        }
        updatePresentation();
    }

    private void measureTable(Table table, double available) {
        table.layoutDirty = true;
        int columns = table.rows.get(0).cells.size();
        var ideal = new double[columns];
        double minimum = Math.min(font.getSize() * 3 + 24, available / columns);
        for (var row : table.rows) {
            for (int col = 0; col < columns; col++) {
                var flow = createCellText(row.cells.get(col), row.line == table.firstLine);
                ideal[col] = Math.max(ideal[col], Math.max(minimum, flow.prefWidth(-1) + 24));
            }
        }
        table.widths = new double[columns];
        double remaining = available;
        int flexible = columns;
        while (flexible > 0) {
            double share = remaining / flexible;
            boolean fixed = false;
            for (int col = 0; col < columns; col++) {
                if (table.widths[col] == 0 && ideal[col] <= share) {
                    table.widths[col] = ideal[col];
                    remaining -= ideal[col];
                    flexible--;
                    fixed = true;
                }
            }
            if (!fixed) {
                for (int col = 0; col < columns; col++) {
                    if (table.widths[col] == 0) {
                        table.widths[col] = share;
                    }
                }
                break;
            }
        }
        for (var row : table.rows) {
            double height = font.getSize() + 20;
            for (int col = 0; col < columns; col++) {
                var flow = createCellText(row.cells.get(col), row.line == table.firstLine);
                height = Math.max(height, flow.prefHeight(Math.max(1, table.widths[col] - 24)) + 20);
            }
            row.height = Math.ceil(height);
        }
    }

    private void updatePresentation() {
        var area = currentArea;
        if (updating || area == null || tables.isEmpty()) {
            return;
        }
        updating = true;
        try {
            int caretLine = area.getCurrentParagraph();
            var selection = area.getSelection();
            int firstSelected = area.offsetToPosition(selection.getStart(), Forward).getMajor();
            int lastSelected = area.offsetToPosition(
                    selection.getLength() == 0 ? selection.getEnd() : selection.getEnd() - 1, Forward).getMajor();
            area.suspendVisibleParsWhileInvoke(() -> {
                for (var table : tables) {
                    boolean source = (caretLine >= table.firstLine && caretLine <= table.lastLine)
                            || (selection.getLength() > 0 && firstSelected <= table.lastLine
                            && lastSelected >= table.firstLine);
                    boolean preview = !source && table.widths != null;
                    if (table.preview == preview && !table.layoutDirty) {
                        continue;
                    }
                    table.preview = preview;
                    boolean layoutDirty = table.layoutDirty;
                    table.layoutDirty = false;
                    for (int line = table.firstLine; line <= table.lastLine; line++) {
                        Row row = table.rowByLine.get(line);
                        Double height = null;
                        if (preview) {
                            if (row != null) {
                                height = row.height + 1;
                            } else if (line == table.firstLine + 1) {
                                height = 2.0;
                            }
                        }
                        if (!setPreviewStyle(line, height) && height != null && layoutDirty) {
                            area.recreateParagraphGraphic(line);
                        }
                    }
                }
            });
        } finally {
            updating = false;
        }
    }

    private boolean setPreviewStyle(int line, Double height) {
        var area = currentArea;
        if (line < 0 || line >= area.getParagraphs().size()) {
            return false;
        }
        var existing = area.getParagraph(line).getParagraphStyle();
        var styles = new ArrayList<>(existing);
        styles.removeIf(style -> style.equals(PREVIEW_CLASS) || style.startsWith(HEIGHT_PREFIX));
        if (height != null) {
            styles.add(PREVIEW_CLASS);
            styles.add(HEIGHT_PREFIX + height);
            previewLines.add(line);
        } else {
            previewLines.remove(line);
        }
        if (!styles.equals(existing)) {
            area.setParagraphStyle(line, styles);
            return true;
        }
        return false;
    }

    private Node createGraphic(int line, Node base) {
        var table = tableByLine.get(line);
        if (table == null || !table.preview || table.widths == null) {
            return base;
        }
        Row row = table.rowByLine.get(line);
        if (row == null && line != table.firstLine + 1) {
            return base;
        }
        var area = currentArea;
        var cells = new HBox();
        cells.setManaged(false);
        if (row != null) {
            cells.setOnMousePressed(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    event.consume();
                    area.moveTo(line, 0);
                    area.requestFocus();
                    area.requestFollowCaret();
                }
            });
            cells.getStyleClass().add("markdown-table-preview-row");
            if (line == table.firstLine) {
                cells.getStyleClass().add("markdown-table-preview-header");
            } else if (table.rows.indexOf(row) % 2 == 0) {
                cells.getStyleClass().add("markdown-table-preview-alternate");
            }
            for (int col = 0; col < row.cells.size(); col++) {
                var text = createCellText(row.cells.get(col), line == table.firstLine);
                var cell = new StackPane(text);
                cell.setAlignment(Pos.TOP_LEFT);
                cell.setPadding(new Insets(9, 12, 9, 12));
                cell.getStyleClass().add("markdown-table-preview-cell");
                if (col == 0) {
                    cell.getStyleClass().add("markdown-table-preview-first-cell");
                }
                cell.setMinWidth(table.widths[col]);
                cell.setPrefWidth(table.widths[col]);
                cell.setMaxWidth(table.widths[col]);
                cells.getChildren().add(cell);
            }
        }
        var box = new Pane(cells) {
            private boolean heightRefreshPending;

            @Override
            protected double computePrefWidth(double height) {
                return base == null ? 0 : base.prefWidth(height);
            }

            @Override
            protected double computeMinWidth(double height) {
                return computePrefWidth(height);
            }

            @Override
            protected double computeMaxWidth(double height) {
                return computePrefWidth(height);
            }

            @Override
            protected void layoutChildren() {
                if (base != null) {
                    base.resizeRelocate(0, 0, getWidth(), getHeight());
                }
                if (row != null) {
                    double width = 0;
                    for (double column : table.widths) {
                        width += column;
                    }
                    double height = Math.ceil(Math.max(font.getSize() + 20, cells.prefHeight(width)));
                    cells.resize(width, height);
                    // 以应用 CSS 后的真实单元格布局为准，包含边框、留白及文字换行高度。
                    if (height != row.height + 1 && !heightRefreshPending) {
                        heightRefreshPending = true;
                        Platform.runLater(() -> {
                            heightRefreshPending = false;
                            if (destroyed || area != currentArea || !table.preview
                                    || tableByLine.get(line) != table || getScene() == null
                                    || getScene() != area.getScene()) {
                                return;
                            }
                            double actualHeight = Math.ceil(Math.max(font.getSize() + 20,
                                    cells.prefHeight(cells.getWidth())));
                            row.height = actualHeight - 1;
                            setPreviewStyle(line, actualHeight);
                        });
                    }
                }
            }
        };
        cells.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> box.getWidth() + textLeftPadding(area) - area.estimatedScrollXProperty().getValue(),
                box.widthProperty(), area.paddingProperty(), area.estimatedScrollXProperty()));
        box.setPickOnBounds(false);
        if (base != null) {
            if (row == null) {
                base.setOpacity(0);
            }
            box.getChildren().add(base);
        }
        return box;
    }

    private TextFlow createCellText(TableCell cell, boolean header) {
        var flow = new TextFlow();
        flow.setMinWidth(0);
        flow.setMaxWidth(Double.MAX_VALUE);
        flow.setTextAlignment(cell.getAlignment() == TableCell.Alignment.CENTER ? TextAlignment.CENTER
                : cell.getAlignment() == TableCell.Alignment.RIGHT ? TextAlignment.RIGHT : TextAlignment.LEFT);
        appendInline(flow, cell, header, false, false, false);
        if (flow.getChildren().isEmpty()) {
            var text = new Text(" ");
            text.setFont(font);
            flow.getChildren().add(text);
        }
        return flow;
    }

    private void appendInline(TextFlow flow, org.commonmark.node.Node node,
                              boolean bold, boolean italic, boolean strike, boolean link) {
        bold |= node instanceof StrongEmphasis;
        italic |= node instanceof Emphasis;
        strike |= node instanceof Strikethrough;
        link |= node instanceof Link;
        String literal = null;
        if (node instanceof org.commonmark.node.Text text) {
            literal = text.getLiteral();
        } else if (node instanceof Code code) {
            literal = code.getLiteral();
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            literal = "\n";
        } else if (node instanceof HtmlInline html) {
            literal = html.getLiteral().matches("(?i)<br\\s*/?>") ? "\n" : html.getLiteral();
        }
        if (literal != null) {
            var text = new Text(literal);
            text.setFont(font);
            text.getStyleClass().add("markdown-table-preview-content");
            if (bold) {
                text.setStrokeWidth(font.getSize() * 0.035);
                text.strokeProperty().bind(text.fillProperty());
            }
            if (italic) {
                text.getTransforms().add(new javafx.scene.transform.Shear(-0.18, 0));
            }
            text.setStrikethrough(strike);
            if (link) {
                text.getStyleClass().add("markdown-link");
            } else if (node instanceof Code) {
                text.getStyleClass().add("markdown-inline-code");
            }
            flow.getChildren().add(text);
        }
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            appendInline(flow, child, bold, italic, strike, link);
        }
    }

    private static List<Table> parseTables(EditorArea area, String text) {
        var result = new ArrayList<Table>();
        ((EditorAreaMgrCode) area.getEditor()).parseMarkdown(text).accept(new AbstractVisitor() {
            @Override
            public void visit(CustomBlock block) {
                if (!(block instanceof TableBlock) || block.getSourceSpans().isEmpty()) {
                    visitChildren(block);
                    return;
                }
                var spans = block.getSourceSpans();
                var table = new Table(spans.get(0).getLineIndex(),
                        spans.get(spans.size() - 1).getLineIndex());
                for (var section = block.getFirstChild(); section != null; section = section.getNext()) {
                    for (var child = section.getFirstChild(); child != null; child = child.getNext()) {
                        if (!(child instanceof TableRow) || child.getSourceSpans().isEmpty()) {
                            continue;
                        }
                        var cells = new ArrayList<TableCell>();
                        for (var cell = child.getFirstChild(); cell != null; cell = cell.getNext()) {
                            if (cell instanceof TableCell tableCell) {
                                cells.add(tableCell);
                            }
                        }
                        var row = new Row(child.getSourceSpans().get(0).getLineIndex(), cells);
                        table.rows.add(row);
                        table.rowByLine.put(row.line, row);
                    }
                }
                if (!table.rows.isEmpty() && !table.rows.get(0).cells.isEmpty()) {
                    result.add(table);
                }
            }
        });
        return result;
    }

    /** 表格源码范围与各行共用的列宽。 */
    private static final class Table {
        final int firstLine;
        final int lastLine;
        final List<Row> rows = new ArrayList<>();
        final Map<Integer, Row> rowByLine = new HashMap<>();
        double[] widths;
        boolean preview;
        boolean layoutDirty;

        Table(int firstLine, int lastLine) {
            this.firstLine = firstLine;
            this.lastLine = lastLine;
        }
    }

    /** 单行单元格内容与换行后的高度。 */
    private static final class Row {
        final int line;
        final List<TableCell> cells;
        double height;

        Row(int line, List<TableCell> cells) {
            this.line = line;
            this.cells = cells;
        }
    }
}

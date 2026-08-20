package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action0;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Forward;

/** 管理 Markdown 表格优化提示与原文对齐。 */
public final class MarkdownTableOptimizeManager {
    private static final long REFRESH_DELAY_MS = 600;
    private static final Pattern SEPARATOR_CELL_PATTERN = Pattern.compile(":?-+:?");

    private final Action0 textChangedAction = this::onTextChanged;
    private final ChangeListener<Number> caretChanged =
            (observable, oldValue, newValue) -> updatePopupForCaret();
    private final ChangeListener<Number> scrollChanged =
            (observable, oldValue, newValue) -> updatePopupForCaret();

    private EditorArea currentArea;
    private PauseTransition refreshDelay;
    private Popup optimizePopup;
    private Label optimizeLabel;
    private List<MarkdownTable> optimizableTables = List.of();
    private MarkdownTable activeTable;
    private boolean runtimeActive;
    private boolean tablesDirty;
    private boolean destroyed;
    private long requestId;
    private long shownContentVersion = -1;
    private Future<?> parseTask;

    public MarkdownTableOptimizeManager(EditorArea area) {
        bindEditor(area);
    }

    public void destroy() {
        destroyed = true;
        unbindEditor();
    }

    public void refreshCurrentFile(EditorArea area) {
        bindEditor(area);
    }

    private void ensurePopup() {
        if (optimizePopup != null) {
            return;
        }
        optimizePopup = new Popup();
        optimizeLabel = new Label(Locales.str("markdownTableOptimize"));
        optimizeLabel.setCursor(Cursor.HAND);
        optimizeLabel.setPadding(new Insets(5, 11, 5, 11));
        optimizeLabel.setStyle("-fx-background-color: #ffe0b2;"
                + "-fx-background-radius: 12;"
                + "-fx-text-fill: black;"
                + "-fx-font-size: 12px;");
        optimizeLabel.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.25)));
        optimizeLabel.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                optimizeActiveTable();
                event.consume();
            }
        });
        optimizePopup.setAutoFix(true);
        optimizePopup.setAutoHide(false);
        optimizePopup.setHideOnEscape(true);
        optimizePopup.getContent().add(optimizeLabel);
    }

    private void bindEditor(EditorArea area) {
        unbindEditor();
        currentArea = area;
        clearTables();
        if (!supports(area)) {
            tablesDirty = false;
            return;
        }

        area.getEditor().textChanged.addAction(textChangedAction);
        if (isOverLimit(area)) {
            tablesDirty = false;
            return;
        }
        activateRuntime();
        tablesDirty = true;
        startRefresh();
    }

    private void unbindEditor() {
        var area = currentArea;
        if (area == null) {
            return;
        }
        area.getEditor().textChanged.removeAction(textChangedAction);
        deactivateRuntime();
        currentArea = null;
    }

    private void onTextChanged() {
        invalidateRefresh();
        tablesDirty = true;
        if (isOverLimit(currentArea)) {
            deactivateRuntime();
            clearTables();
            tablesDirty = false;
            return;
        }
        activateRuntime();
        refreshDelay.playFromStart();
    }

    private void startRefresh() {
        invalidateRefresh();
        var area = currentArea;
        if (destroyed || !runtimeActive || !tablesDirty || !supports(area) || isOverLimit(area)) {
            return;
        }

        long contentVersion = area.getEditor().getContentVersion();
        String text = area.getText();
        long currentRequestId = requestId;
        parseTask = ThreadUtils.submit(() -> {
            try {
                var tables = parseTables(text);
                if (tables != null && !ThreadUtils.sBeClosing && !Thread.currentThread().isInterrupted()) {
                    Platform.runLater(() -> applyTables(area, contentVersion, currentRequestId, tables));
                }
            } catch (RuntimeException e) {
                Log.e("parse markdown tables failed", e);
            }
        });
    }

    private void applyTables(EditorArea area, long contentVersion, long parsedRequestId,
                             List<MarkdownTable> tables) {
        if (destroyed || area != currentArea || parsedRequestId != requestId
                || area.getEditor().getContentVersion() != contentVersion || isOverLimit(area)) {
            return;
        }
        parseTask = null;
        optimizableTables = tables;
        shownContentVersion = contentVersion;
        tablesDirty = false;
        updatePopupForCaret();
    }

    private void activateRuntime() {
        var area = currentArea;
        if (runtimeActive || area == null) {
            return;
        }
        runtimeActive = true;
        refreshDelay = new PauseTransition(Duration.millis(REFRESH_DELAY_MS));
        refreshDelay.setOnFinished(event -> startRefresh());
        area.caretPositionProperty().addListener(caretChanged);
        area.estimatedScrollXProperty().addListener(scrollChanged);
        area.estimatedScrollYProperty().addListener(scrollChanged);
    }

    private void deactivateRuntime() {
        var area = currentArea;
        if (runtimeActive && area != null) {
            area.caretPositionProperty().removeListener(caretChanged);
            area.estimatedScrollXProperty().removeListener(scrollChanged);
            area.estimatedScrollYProperty().removeListener(scrollChanged);
        }
        runtimeActive = false;
        invalidateRefresh();
        if (refreshDelay != null) {
            refreshDelay.setOnFinished(null);
            refreshDelay = null;
        }
        disposePopup();
    }

    private void updatePopupForCaret() {
        var area = currentArea;
        if (area == null || shownContentVersion != area.getEditor().getContentVersion()
                || optimizableTables.isEmpty()) {
            hidePopup();
            return;
        }

        int caretPosition = area.getCaretPosition();
        if (caretPosition < 0 || caretPosition > area.getLength()) {
            hidePopup();
            return;
        }
        int lineIndex = area.offsetToPosition(caretPosition, Forward).getMajor();
        var table = findTable(lineIndex);
        if (table == null) {
            hidePopup();
            return;
        }
        if (sameTableRange(table, activeTable) && optimizePopup != null && optimizePopup.isShowing()) {
            positionPopupAtTableTop(area, table);
            return;
        }

        String formattedText = formatForCurrentFont(area, table.originalText());
        if (formattedText == null || formattedText.equals(table.originalText())) {
            hidePopup();
            return;
        }
        activeTable = new MarkdownTable(table.startOffset(), table.endOffset(), table.startLine(),
                table.endLine(), table.originalText(), formattedText);
        positionPopupAtTableTop(area, activeTable);
    }

    private void positionPopupAtTableTop(EditorArea area, MarkdownTable table) {
        var tableStartBounds = area.getCharacterBoundsOnScreen(
                table.startOffset(), table.startOffset() + 1);
        if (tableStartBounds.isEmpty()) {
            hidePopup();
            return;
        }
        ensurePopup();
        double popupX = tableStartBounds.get().getMinX();
        double popupY = tableStartBounds.get().getMinY() - 30;
        if (optimizePopup.isShowing()) {
            optimizePopup.setX(popupX);
            optimizePopup.setY(popupY);
        } else {
            optimizePopup.show(area, popupX, popupY);
        }
    }

    private static boolean sameTableRange(MarkdownTable first, MarkdownTable second) {
        return first != null && second != null
                && first.startOffset() == second.startOffset() && first.endOffset() == second.endOffset();
    }

    private static String formatForCurrentFont(EditorArea area, String tableText) {
        Font font = findEditorFont(area);
        var measureNode = new Text();
        measureNode.setFont(font);
        TextWidthProvider widthProvider = text -> {
            measureNode.setText(text);
            return measureNode.getLayoutBounds().getWidth();
        };
        var tables = parseTables(tableText, widthProvider);
        return tables == null || tables.isEmpty() ? null : tables.get(0).formattedText();
    }

    private static Font findEditorFont(EditorArea area) {
        Font fallback = null;
        for (Node node : area.lookupAll(".text")) {
            if (!(node instanceof Text text) || text.getText().isBlank()) {
                continue;
            }
            if (fallback == null) {
                fallback = text.getFont();
            }
            if (text.getStyleClass().stream().noneMatch(style -> style.startsWith("markdown-"))) {
                return text.getFont();
            }
        }
        return fallback == null ? Font.getDefault() : fallback;
    }

    private MarkdownTable findTable(int lineIndex) {
        int start = 0;
        int end = optimizableTables.size() - 1;
        while (start <= end) {
            int middle = (start + end) >>> 1;
            var table = optimizableTables.get(middle);
            if (lineIndex < table.startLine()) {
                end = middle - 1;
            } else if (lineIndex > table.endLine()) {
                start = middle + 1;
            } else {
                return table;
            }
        }
        return null;
    }

    private void optimizeActiveTable() {
        var area = currentArea;
        var table = activeTable;
        if (area == null || table == null || shownContentVersion != area.getEditor().getContentVersion()) {
            hidePopup();
            return;
        }
        String currentText = area.getText(table.startOffset(), table.endOffset());
        if (!currentText.equals(table.originalText())) {
            hidePopup();
            return;
        }
        String formattedText = formatForCurrentFont(area, currentText);
        if (formattedText == null || formattedText.equals(currentText)) {
            hidePopup();
            return;
        }
        table = new MarkdownTable(table.startOffset(), table.endOffset(), table.startLine(),
                table.endLine(), currentText, formattedText);

        int caretPosition = area.getCaretPosition();
        hidePopup();
        area.replaceText(table.startOffset(), table.endOffset(), table.formattedText());
        int newCaretPosition;
        if (caretPosition <= table.startOffset()) {
            newCaretPosition = caretPosition;
        } else if (caretPosition >= table.endOffset()) {
            newCaretPosition = caretPosition + table.formattedText().length() - table.originalText().length();
        } else {
            int relativePosition = caretPosition - table.startOffset();
            newCaretPosition = table.startOffset() + Math.min(relativePosition, table.formattedText().length());
        }
        area.moveTo(newCaretPosition);
        area.requestFocus();
    }

    private void hidePopup() {
        if (optimizePopup != null) {
            optimizePopup.hide();
        }
        activeTable = null;
    }

    private void disposePopup() {
        hidePopup();
        if (optimizeLabel != null) {
            optimizeLabel.setOnMouseClicked(null);
        }
        if (optimizePopup != null) {
            optimizePopup.getContent().clear();
            optimizePopup = null;
        }
        optimizeLabel = null;
    }

    private void invalidateRefresh() {
        if (refreshDelay != null) {
            refreshDelay.stop();
        }
        hidePopup();
        requestId++;
        var task = parseTask;
        parseTask = null;
        if (task != null) {
            task.cancel(true);
        }
    }

    private void clearTables() {
        optimizableTables = List.of();
        shownContentVersion = -1;
        hidePopup();
    }

    private static boolean isOverLimit(EditorArea area) {
        return area == null || area.getEditor().isRealtimeProcessingLimitReached();
    }

    public static boolean supports(EditorArea area) {
        if (area == null || area.getEditor().getSourceFile() == null) {
            return false;
        }
        String name = area.getEditor().getSourceFile().getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    private static List<MarkdownTable> parseTables(String text) {
        return parseTables(text, null);
    }

    private static List<MarkdownTable> parseTables(String text, TextWidthProvider widthProvider) {
        var lines = parseLines(text);
        if (lines == null) {
            return null;
        }
        var tables = new ArrayList<MarkdownTable>();
        int lineIndex = 0;
        while (lineIndex + 1 < lines.size()) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            var headerLine = lines.get(lineIndex);
            var separatorLine = lines.get(lineIndex + 1);
            if (headerLine.inFence() || separatorLine.inFence()) {
                lineIndex++;
                continue;
            }

            var header = parseRow(headerLine.content());
            var separator = parseRow(separatorLine.content());
            var alignments = parseAlignments(separator);
            if (header == null || alignments == null || header.cells().size() != alignments.size()) {
                lineIndex++;
                continue;
            }

            int tableEndLine = lineIndex + 2;
            boolean invalidBody = false;
            var bodyRows = new ArrayList<MarkdownRow>();
            while (tableEndLine < lines.size() && !lines.get(tableEndLine).inFence()) {
                var body = parseRow(lines.get(tableEndLine).content());
                if (body == null) {
                    break;
                }
                if (body.cells().size() != header.cells().size()) {
                    invalidBody = true;
                    while (tableEndLine < lines.size()
                            && !lines.get(tableEndLine).inFence()
                            && parseRow(lines.get(tableEndLine).content()) != null) {
                        tableEndLine++;
                    }
                    break;
                }
                bodyRows.add(body);
                tableEndLine++;
            }
            if (invalidBody) {
                lineIndex = tableEndLine;
                continue;
            }

            int startOffset = headerLine.startOffset();
            int endOffset = lines.get(tableEndLine - 1).contentEndOffset();
            String originalText = text.substring(startOffset, endOffset);
            String formattedText = widthProvider == null ? originalText
                    : formatTable(lines, lineIndex, header, bodyRows, alignments, widthProvider);
            tables.add(new MarkdownTable(startOffset, endOffset, lineIndex, tableEndLine - 1,
                    originalText, formattedText));
            lineIndex = tableEndLine;
        }
        return List.copyOf(tables);
    }

    private static List<MarkdownLine> parseLines(String text) {
        var lines = new ArrayList<MarkdownLine>();
        boolean inFence = false;
        char fenceCharacter = 0;
        int fenceLength = 0;
        int lineStart = 0;
        while (lineStart < text.length()) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            int nextLineStart = text.indexOf('\n', lineStart);
            int contentEnd = nextLineStart >= 0 ? nextLineStart : text.length();
            int nextStart = nextLineStart >= 0 ? nextLineStart + 1 : text.length();
            if (contentEnd > lineStart && text.charAt(contentEnd - 1) == '\r') {
                contentEnd--;
            }

            String content = text.substring(lineStart, contentEnd);
            boolean lineInFence = inFence;
            int contentStart = skipLeadingSpaces(content);
            int currentFenceLength = countFenceCharacters(content, contentStart);
            if (currentFenceLength >= 3) {
                char currentFenceCharacter = content.charAt(contentStart);
                lineInFence = true;
                if (!inFence) {
                    inFence = true;
                    fenceCharacter = currentFenceCharacter;
                    fenceLength = currentFenceLength;
                } else if (currentFenceCharacter == fenceCharacter && currentFenceLength >= fenceLength
                        && content.substring(contentStart + currentFenceLength).isBlank()) {
                    inFence = false;
                }
            }

            lines.add(new MarkdownLine(lineStart, contentEnd, text.substring(contentEnd, nextStart),
                    content, lineInFence));
            lineStart = nextStart;
        }
        return lines;
    }

    private static int skipLeadingSpaces(String text) {
        int index = 0;
        while (index < text.length() && index < 3 && text.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private static int countFenceCharacters(String text, int start) {
        if (start >= text.length()) {
            return 0;
        }
        char character = text.charAt(start);
        if (character != '`' && character != '~') {
            return 0;
        }
        int index = start;
        while (index < text.length() && text.charAt(index) == character) {
            index++;
        }
        return index - start;
    }

    private static MarkdownRow parseRow(String line) {
        int start = 0;
        while (start < line.length() && line.charAt(start) == ' ') {
            start++;
        }
        if (start > 3 || start < line.length() && line.charAt(start) == '\t') {
            return null;
        }
        int end = line.length();
        while (end > start && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        if (start >= end) {
            return null;
        }

        var delimiters = findTableDelimiters(line, start, end);
        if (delimiters.isEmpty()) {
            return null;
        }
        boolean leadingPipe = delimiters.get(0) == start;
        boolean trailingPipe = delimiters.get(delimiters.size() - 1) == end - 1;
        int cellStart = leadingPipe ? delimiters.get(0) + 1 : start;
        int delimiterStart = leadingPipe ? 1 : 0;
        int delimiterEnd = trailingPipe ? delimiters.size() - 1 : delimiters.size();
        var cells = new ArrayList<String>();
        for (int index = delimiterStart; index < delimiterEnd; index++) {
            int delimiter = delimiters.get(index);
            cells.add(line.substring(cellStart, delimiter).strip());
            cellStart = delimiter + 1;
        }
        int finalCellEnd = trailingPipe ? delimiters.get(delimiters.size() - 1) : end;
        cells.add(line.substring(cellStart, finalCellEnd).strip());
        if (cells.size() < 2) {
            return null;
        }
        return new MarkdownRow(start, List.copyOf(cells));
    }

    private static List<Integer> findTableDelimiters(String line, int start, int end) {
        var delimiters = new ArrayList<Integer>();
        int codeTicks = 0;
        int index = start;
        while (index < end) {
            char character = line.charAt(index);
            if (character == '`' && !isEscaped(line, index)) {
                int runLength = countCharacters(line, index, end, '`');
                if (codeTicks == 0) {
                    if (hasClosingBackticks(line, index + runLength, end, runLength)) {
                        codeTicks = runLength;
                    }
                } else if (runLength == codeTicks) {
                    codeTicks = 0;
                }
                index += runLength;
                continue;
            }
            if (character == '|' && codeTicks == 0 && !isEscaped(line, index)) {
                delimiters.add(index);
            }
            index++;
        }
        return delimiters;
    }

    private static boolean hasClosingBackticks(String line, int start, int end, int runLength) {
        int index = start;
        while (index < end) {
            if (line.charAt(index) != '`' || isEscaped(line, index)) {
                index++;
                continue;
            }
            int currentRunLength = countCharacters(line, index, end, '`');
            if (currentRunLength == runLength) {
                return true;
            }
            index += currentRunLength;
        }
        return false;
    }

    private static int countCharacters(String text, int start, int end, char character) {
        int index = start;
        while (index < end && text.charAt(index) == character) {
            index++;
        }
        return index - start;
    }

    private static boolean isEscaped(String text, int index) {
        int slashCount = 0;
        for (int current = index - 1; current >= 0 && text.charAt(current) == '\\'; current--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private static List<ColumnAlignment> parseAlignments(MarkdownRow separator) {
        if (separator == null) {
            return null;
        }
        var alignments = new ArrayList<ColumnAlignment>();
        for (String cell : separator.cells()) {
            if (!SEPARATOR_CELL_PATTERN.matcher(cell).matches()) {
                return null;
            }
            boolean leftColon = cell.startsWith(":");
            boolean rightColon = cell.endsWith(":");
            ColumnAlignment alignment = leftColon && rightColon ? ColumnAlignment.CENTER
                    : leftColon ? ColumnAlignment.LEFT
                    : rightColon ? ColumnAlignment.RIGHT : ColumnAlignment.DEFAULT;
            alignments.add(alignment);
        }
        return List.copyOf(alignments);
    }

    private static String formatTable(List<MarkdownLine> lines, int startLine,
                                      MarkdownRow header, List<MarkdownRow> bodyRows,
                                      List<ColumnAlignment> alignments,
                                      TextWidthProvider widthProvider) {
        int columnCount = header.cells().size();
        double[] widths = new double[columnCount];
        String[] separators = new String[columnCount];
        double spaceWidth = widthProvider.width(" ");
        if (spaceWidth <= 0) {
            spaceWidth = 1;
        }
        for (int column = 0; column < columnCount; column++) {
            widths[column] = widthProvider.width(header.cells().get(column));
            for (var row : bodyRows) {
                double cellWidth = widthProvider.width(row.cells().get(column));
                if (cellWidth > widths[column]) {
                    widths[column] = cellWidth;
                }
            }
            separators[column] = createSeparator(
                    alignments.get(column), widths[column] + spaceWidth, widthProvider);
            widths[column] = widthProvider.width(separators[column]);
        }

        String indent = " ".repeat(header.indent());
        var formatted = new StringBuilder();
        appendContentRow(formatted, indent, header.cells(), alignments, widths, spaceWidth, widthProvider);
        formatted.append(lines.get(startLine).lineEnding());
        appendSeparatorRow(formatted, indent, separators);
        for (int rowIndex = 0; rowIndex < bodyRows.size(); rowIndex++) {
            formatted.append(lines.get(startLine + rowIndex + 1).lineEnding());
            appendContentRow(formatted, indent, bodyRows.get(rowIndex).cells(), alignments,
                    widths, spaceWidth, widthProvider);
        }
        return formatted.toString();
    }

    private static void appendContentRow(StringBuilder out, String indent, List<String> cells,
                                         List<ColumnAlignment> alignments, double[] widths,
                                         double spaceWidth, TextWidthProvider widthProvider) {
        out.append(indent);
        double widthError = 0;
        for (int column = 0; column < cells.size(); column++) {
            String cell = cells.get(column);
            int padding = (int) Math.round(
                    (widths[column] - widthProvider.width(cell) - widthError) / spaceWidth);
            if (padding < 0) {
                padding = 0;
            }
            int bestPadding = padding;
            double bestError = Double.MAX_VALUE;
            int firstCandidate = padding > 0 ? padding - 1 : 0;
            for (int candidate = firstCandidate; candidate <= padding + 1; candidate++) {
                int candidateLeftPadding = calculateLeftPadding(alignments.get(column), candidate);
                int candidateRightPadding = candidate - candidateLeftPadding;
                String candidateText = " ".repeat(candidateLeftPadding)
                        + cell + " ".repeat(candidateRightPadding);
                double candidateError = Math.abs(
                        widthError + widthProvider.width(candidateText) - widths[column]);
                if (candidateError < bestError) {
                    bestError = candidateError;
                    bestPadding = candidate;
                }
            }
            int leftPadding = calculateLeftPadding(alignments.get(column), bestPadding);
            int rightPadding = bestPadding - leftPadding;
            String leftSpaces = " ".repeat(leftPadding);
            String rightSpaces = " ".repeat(rightPadding);
            out.append("| ")
                    .append(leftSpaces)
                    .append(cell)
                    .append(rightSpaces)
                    .append(' ');
            widthError += widthProvider.width(leftSpaces + cell + rightSpaces) - widths[column];
        }
        out.append('|');
    }

    private static int calculateLeftPadding(ColumnAlignment alignment, int padding) {
        return switch (alignment) {
            case RIGHT -> padding;
            case CENTER -> padding / 2;
            case DEFAULT, LEFT -> 0;
        };
    }

    private static void appendSeparatorRow(StringBuilder out, String indent, String[] separators) {
        out.append(indent);
        for (String separator : separators) {
            out.append("| ").append(separator).append(' ');
        }
        out.append('|');
    }

    private static String createSeparator(ColumnAlignment alignment, double targetWidth,
                                          TextWidthProvider widthProvider) {
        int dashCount = 3;
        String separator = separatorText(alignment, dashCount);
        double currentWidth = widthProvider.width(separator);
        double dashWidth = widthProvider.width("-");
        if (currentWidth < targetWidth && dashWidth > 0) {
            dashCount += (int) Math.ceil((targetWidth - currentWidth) / dashWidth);
            separator = separatorText(alignment, dashCount);
            while (widthProvider.width(separator) < targetWidth) {
                dashCount++;
                separator = separatorText(alignment, dashCount);
            }
        }
        return separator;
    }

    private static String separatorText(ColumnAlignment alignment, int dashCount) {
        return switch (alignment) {
            case DEFAULT -> "-".repeat(dashCount);
            case LEFT -> ':' + "-".repeat(dashCount);
            case RIGHT -> "-".repeat(dashCount) + ':';
            case CENTER -> ':' + "-".repeat(dashCount) + ':';
        };
    }

    private interface TextWidthProvider {
        double width(String text);
    }

    private enum ColumnAlignment {
        DEFAULT,
        LEFT,
        CENTER,
        RIGHT
    }

    private record MarkdownLine(int startOffset, int contentEndOffset, String lineEnding,
                                String content, boolean inFence) {
    }

    private record MarkdownRow(int indent, List<String> cells) {
    }

    private record MarkdownTable(int startOffset, int endOffset, int startLine, int endLine,
                                 String originalText, String formattedText) {
    }
}

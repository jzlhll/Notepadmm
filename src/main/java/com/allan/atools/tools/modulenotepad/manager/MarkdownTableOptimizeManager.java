package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.MarkdownTableDocumentState;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/** 按编辑器字体的实际宽度对齐 Markdown 表格原文。 */
public final class MarkdownTableOptimizeManager {
    private MarkdownTableOptimizeManager() {
    }

    public static String format(EditorArea area, String source) {
        var tables = MarkdownTableDocumentState.parse(source);
        if (tables == null || tables.size() != 1) {
            return null;
        }
        var table = tables.get(0);
        if (!table.valid() || table.startOffset() != 0 || table.endOffset() != source.length()) {
            return null;
        }
        var measure = new Text();
        measure.setFont(findEditorFont(area));
        double spaceWidth = width(measure, " ");
        double dashWidth = width(measure, "-");
        if (spaceWidth <= 0 || dashWidth <= 0) {
            return null;
        }
        int columns = table.alignments().size();
        var widths = new double[columns];
        var separators = new String[columns];
        for (int column = 0; column < columns; column++) {
            for (var row : table.rows()) {
                double width = width(measure, row.cells().get(column).source().strip());
                if (width > widths[column]) {
                    widths[column] = width;
                }
            }
            var alignment = table.alignments().get(column);
            double targetWidth = widths[column] + spaceWidth;
            int dashes = 3;
            String separator = separator(alignment, dashes);
            double separatorWidth = width(measure, separator);
            if (separatorWidth < targetWidth) {
                dashes += (int) Math.ceil((targetWidth - separatorWidth) / dashWidth);
                separator = separator(alignment, dashes);
                while (width(measure, separator) < targetWidth) {
                    separator = separator(alignment, ++dashes);
                }
            }
            separators[column] = separator;
            widths[column] = width(measure, separator);
        }

        var result = new StringBuilder();
        appendRow(result, table, table.rows().get(0), widths, spaceWidth, measure);
        result.append(table.lineEnding());
        result.append(table.indent());
        for (String separator : separators) {
            result.append("| ").append(separator).append(' ');
        }
        result.append('|');
        for (int row = 1; row < table.rows().size(); row++) {
            result.append(table.lineEnding());
            appendRow(result, table, table.rows().get(row), widths, spaceWidth, measure);
        }
        return result.toString();
    }

    private static void appendRow(StringBuilder out, MarkdownTableDocumentState.Table table,
                                  MarkdownTableDocumentState.Row row, double[] widths,
                                  double spaceWidth, Text measure) {
        out.append(table.indent());
        double widthError = 0;
        for (int column = 0; column < widths.length; column++) {
            String value = row.cells().get(column).source().strip();
            int padding = (int) Math.round((widths[column] - width(measure, value) - widthError) / spaceWidth);
            if (padding < 0) {
                padding = 0;
            }
            String padded = value;
            double bestError = Double.MAX_VALUE;
            // 实测相邻空格数量，并补偿上一列误差，避免分隔线逐列偏移。
            for (int candidate = padding > 0 ? padding - 1 : 0; candidate <= padding + 1; candidate++) {
                int left = switch (table.alignments().get(column)) {
                    case RIGHT -> candidate;
                    case CENTER -> candidate / 2;
                    case DEFAULT, LEFT -> 0;
                };
                String text = " ".repeat(left) + value + " ".repeat(candidate - left);
                double error = Math.abs(widthError + width(measure, text) - widths[column]);
                if (error < bestError) {
                    bestError = error;
                    padded = text;
                }
            }
            out.append("| ").append(padded).append(' ');
            widthError += width(measure, padded) - widths[column];
        }
        out.append('|');
    }

    private static String separator(MarkdownTableDocumentState.Alignment alignment, int dashes) {
        return switch (alignment) {
            case DEFAULT -> "-".repeat(dashes);
            case LEFT -> ':' + "-".repeat(dashes);
            case RIGHT -> "-".repeat(dashes) + ':';
            case CENTER -> ':' + "-".repeat(dashes) + ':';
        };
    }

    private static double width(Text measure, String text) {
        measure.setText(text);
        return measure.getLayoutBounds().getWidth();
    }

    private static Font findEditorFont(EditorArea area) {
        Font fallback = null;
        for (var node : area.lookupAll(".text")) {
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
        return fallback == null ? Font.font("JetBrains Mono", UIContext.getFontSizeProperty().get()) : fallback;
    }
}

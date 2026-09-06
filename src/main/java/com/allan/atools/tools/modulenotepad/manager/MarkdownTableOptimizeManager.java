package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.richtext.codearea.MarkdownTableDocumentState;

/** Markdown 表格纯字符串格式化。 */
public final class MarkdownTableOptimizeManager {
    private MarkdownTableOptimizeManager() {
    }

    public static String format(String source) {
        var tables = MarkdownTableDocumentState.parse(source);
        if (tables == null || tables.size() != 1) {
            return null;
        }
        var table = tables.get(0);
        if (!table.valid() || table.startOffset() != 0 || table.endOffset() != source.length()) {
            return null;
        }
        int columns = table.alignments().size();
        var widths = new int[columns];
        for (int column = 0; column < columns; column++) {
            int colonCount = table.alignments().get(column) == MarkdownTableDocumentState.Alignment.CENTER ? 2
                    : table.alignments().get(column) == MarkdownTableDocumentState.Alignment.DEFAULT ? 0 : 1;
            widths[column] = 3 + colonCount;
            for (var row : table.rows()) {
                int width = displayWidth(row.cells().get(column).source());
                if (width > widths[column]) {
                    widths[column] = width;
                }
            }
        }

        var result = new StringBuilder();
        appendRow(result, table, table.rows().get(0), widths);
        result.append(table.lineEnding());
        appendSeparator(result, table, widths);
        for (int row = 1; row < table.rows().size(); row++) {
            result.append(table.lineEnding());
            appendRow(result, table, table.rows().get(row), widths);
        }
        return result.toString();
    }

    private static void appendRow(StringBuilder out, MarkdownTableDocumentState.Table table,
                                  MarkdownTableDocumentState.Row row, int[] widths) {
        out.append(table.indent());
        for (int column = 0; column < widths.length; column++) {
            String value = row.cells().get(column).source().strip();
            int padding = widths[column] - displayWidth(value);
            int left = switch (table.alignments().get(column)) {
                case RIGHT -> padding;
                case CENTER -> padding / 2;
                case DEFAULT, LEFT -> 0;
            };
            out.append("| ").append(" ".repeat(left)).append(value)
                    .append(" ".repeat(padding - left)).append(' ');
        }
        out.append('|');
    }

    private static void appendSeparator(StringBuilder out, MarkdownTableDocumentState.Table table,
                                        int[] widths) {
        out.append(table.indent());
        for (int column = 0; column < widths.length; column++) {
            var alignment = table.alignments().get(column);
            int colons = alignment == MarkdownTableDocumentState.Alignment.CENTER ? 2
                    : alignment == MarkdownTableDocumentState.Alignment.DEFAULT ? 0 : 1;
            String value = switch (alignment) {
                case DEFAULT -> "-".repeat(widths[column]);
                case LEFT -> ':' + "-".repeat(widths[column] - colons);
                case RIGHT -> "-".repeat(widths[column] - colons) + ':';
                case CENTER -> ':' + "-".repeat(widths[column] - colons) + ':';
            };
            out.append("| ").append(value).append(' ');
        }
        out.append('|');
    }

    /** 固定 Unicode 显示列宽，不依赖字体与 JavaFX。 */
    public static int displayWidth(String text) {
        int width = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                    || codePoint == 0x200D || codePoint >= 0xFE00 && codePoint <= 0xFE0F
                    || codePoint >= 0xE0100 && codePoint <= 0xE01EF) {
                continue;
            }
            width += isWide(codePoint) ? 2 : 1;
        }
        return width;
    }

    private static boolean isWide(int codePoint) {
        return codePoint >= 0x1100 && (codePoint <= 0x115F
                || codePoint == 0x2329 || codePoint == 0x232A
                || codePoint >= 0x2E80 && codePoint <= 0xA4CF && codePoint != 0x303F
                || codePoint >= 0xAC00 && codePoint <= 0xD7A3
                || codePoint >= 0xF900 && codePoint <= 0xFAFF
                || codePoint >= 0xFE10 && codePoint <= 0xFE19
                || codePoint >= 0xFE30 && codePoint <= 0xFE6F
                || codePoint >= 0xFF00 && codePoint <= 0xFF60
                || codePoint >= 0xFFE0 && codePoint <= 0xFFE6
                || codePoint >= 0x1F300 && codePoint <= 0x1FAFF
                || codePoint >= 0x20000 && codePoint <= 0x3FFFD);
    }
}

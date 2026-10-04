package com.allan.atools.richtext.codearea;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 单个编辑器内 Markdown 表格的稳定标识、源码映射与显示状态。 */
public final class MarkdownTableDocumentState {
    public enum Mode {
        TABLE,
        SOURCE
    }

    private List<Table> tables = List.of();

    public List<Table> getTables() {
        return tables;
    }

    public void reset() {
        tables = List.of();
    }

    public static List<Table> parse(String text) {
        return MarkdownTableParser.parse(text, null);
    }

    /** 已知格内替换按目标身份更新，空格插入不能按前方文本平移。 */
    public void applyCellChange(String tableId, int rowIndex, int column, int position,
                                String removed, String inserted) {
        int delta = inserted.length() - removed.length();
        for (var table : tables) {
            if (!table.id.equals(tableId)) {
                table.shift(position, position + removed.length(), delta, 0);
                continue;
            }
            table.endOffset += delta;
            var target = table.rows.get(rowIndex);
            target.endOffset += delta;
            target.cells.get(column).endOffset += delta;
            for (int index = column + 1; index < target.cells.size(); index++) {
                target.cells.get(index).shift(delta);
            }
            for (int index = rowIndex + 1; index < table.rows.size(); index++) {
                table.rows.get(index).shift(delta, 0);
            }
        }
    }

    /** 在后台解析回填前先按主文档差量修正现有范围。 */
    public void applyTextChange(int position, String removed, String inserted) {
        int removedLength = removed.length();
        int insertedLength = inserted.length();
        int removedEnd = position + removedLength;
        int delta = insertedLength - removedLength;
        int lineDelta = lineCount(inserted) - lineCount(removed);
        for (var table : tables) {
            table.shift(position, removedEnd, delta, lineDelta);
        }
    }

    public void applyKnownTableChange(String tableId, int position, String removed, String inserted) {
        int removedEnd = position + removed.length();
        int delta = inserted.length() - removed.length();
        int lineDelta = lineCount(inserted) - lineCount(removed);
        for (var table : tables) {
            if (table.id.equals(tableId) && position == table.startOffset && removedEnd == table.endOffset) {
                // 整表替换后只有表格锚点可沿用，旧行列范围不能继续表示新结构。
                table.endOffset = position + inserted.length();
                table.lastLine += lineDelta;
                table.valid = false;
            } else {
                table.shift(position, removedEnd, delta, lineDelta);
            }
        }
    }

    /** 补齐缺失格只替换一行，同步提供新范围，输入不等待后台解析。 */
    public void applyRowChange(String tableId, int rowIndex, Row replacement) {
        var target = tables.stream().filter(table -> table.id.equals(tableId)).findFirst().orElseThrow();
        var previous = target.rows.get(rowIndex);
        int delta = replacement.endOffset - previous.endOffset;
        for (var table : tables) {
            if (table != target) table.shift(previous.startOffset, previous.endOffset, delta, 0);
        }
        target.endOffset += delta;
        var rows = new ArrayList<>(target.rows);
        rows.set(rowIndex, replacement);
        target.rows = List.copyOf(rows);
        for (int index = rowIndex + 1; index < rows.size(); index++) rows.get(index).shift(delta, 0);
    }

    private static int lineCount(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    /** 以已修正范围做一对一匹配，保留表格标识、形态和横向位置。 */
    public void reconcile(List<Table> parsed) {
        // 新表格按源码位置排列且互不重叠；二分定位旧表覆盖的区间，避免逐表两两扫描。
        var counts = new int[parsed.size() + 1];
        var firstMatches = new int[tables.size()];
        var matchCounts = new int[tables.size()];
        for (int index = 0; index < tables.size(); index++) {
            var old = tables.get(index);
            int first = boundary(parsed, old.startOffset, true);
            int end = boundary(parsed, old.endOffset, false);
            int count = Math.max(0, end - first);
            firstMatches[index] = first;
            matchCounts[index] = count;
            if (count > 0) {
                counts[first]++;
                counts[end]--;
            }
        }
        for (int index = 1; index < parsed.size(); index++) counts[index] += counts[index - 1];
        var result = new ArrayList<>(parsed);
        for (int index = 0; index < tables.size(); index++) {
            var old = tables.get(index);
            int first = firstMatches[index];
            int count = matchCounts[index];
            if (count == 1 && counts[first] == 1) parsed.get(first).copyViewState(old);
            else if (count == 0 && !old.valid && old.endOffset > old.startOffset) result.add(old);
        }
        result.sort((first, second) -> Integer.compare(first.startOffset, second.startOffset));
        tables = List.copyOf(result);
    }

    private static int boundary(List<Table> tables, int position, boolean byEnd) {
        int low = 0;
        int high = tables.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            var table = tables.get(middle);
            boolean before = byEnd ? table.endOffset <= position : table.startOffset < position;
            if (before) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    public static final class Table {
        private String id = UUID.randomUUID().toString();
        private Mode mode = Mode.TABLE;
        private int startOffset;
        private int endOffset;
        private int firstLine;
        private int lastLine;
        private List<Row> rows;
        private List<Alignment> alignments;
        private String lineEnding;
        private String indent;
        private boolean valid = true;
        private double horizontalOffset;

        public Table(int startOffset, int endOffset, int firstLine, int lastLine,
                     List<Row> rows, List<Alignment> alignments, String lineEnding, String indent) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.firstLine = firstLine;
            this.lastLine = lastLine;
            this.rows = List.copyOf(rows);
            this.alignments = List.copyOf(alignments);
            this.lineEnding = lineEnding;
            this.indent = indent;
        }

        private void copyViewState(Table old) {
            id = old.id;
            mode = old.mode;
            horizontalOffset = old.horizontalOffset;
        }

        private void shift(int position, int removedEnd, int delta, int lineDelta) {
            if (removedEnd <= startOffset) {
                startOffset += delta;
                endOffset += delta;
                firstLine += lineDelta;
                lastLine += lineDelta;
                for (var row : rows) {
                    row.shift(delta, lineDelta);
                }
                return;
            }
            if (position >= endOffset) {
                return;
            }
            valid = false;
            endOffset += delta;
            lastLine += lineDelta;
            if (position < startOffset) {
                int shift = position + Math.max(0, delta) - startOffset;
                startOffset += shift;
            }
            if (endOffset < startOffset) {
                endOffset = startOffset;
            }
            for (var row : rows) {
                row.shiftChange(position, removedEnd, delta, lineDelta);
            }
        }

        public String id() { return id; }
        public Mode mode() { return mode; }
        public void setMode(Mode mode) { this.mode = mode; }
        public int startOffset() { return startOffset; }
        public int endOffset() { return endOffset; }
        public int firstLine() { return firstLine; }
        public int lastLine() { return lastLine; }
        public List<Row> rows() { return rows; }
        public List<Alignment> alignments() { return alignments; }
        public String lineEnding() { return lineEnding; }
        public String indent() { return indent; }
        public boolean valid() { return valid; }
        public double horizontalOffset() { return horizontalOffset; }
        public void setHorizontalOffset(double value) { horizontalOffset = value; }
    }

    public static final class Row {
        private int line;
        private int startOffset;
        private int endOffset;
        private final boolean header;
        private final List<Cell> cells;
        private final String prefix;
        private final List<String> extraCells;

        public Row(int line, int startOffset, int endOffset, boolean header, List<Cell> cells,
                   String prefix, List<String> extraCells) {
            this.line = line;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.header = header;
            this.cells = List.copyOf(cells);
            this.prefix = prefix;
            this.extraCells = List.copyOf(extraCells);
        }

        private void shift(int delta, int lineDelta) {
            startOffset += delta;
            endOffset += delta;
            line += lineDelta;
            for (var cell : cells) {
                cell.shift(delta);
            }
        }

        private void shiftChange(int position, int removedEnd, int delta, int lineDelta) {
            if (removedEnd <= startOffset) {
                shift(delta, lineDelta);
                return;
            }
            if (position >= endOffset) {
                return;
            }
            endOffset += delta;
            for (var cell : cells) {
                cell.shiftChange(position, removedEnd, delta);
            }
        }

        public int line() { return line; }
        public int startOffset() { return startOffset; }
        public int endOffset() { return endOffset; }
        public boolean header() { return header; }
        public List<Cell> cells() { return cells; }
        public String prefix() { return prefix; }
        public List<String> extraCells() { return extraCells; }
    }

    public static final class Cell {
        private int startOffset;
        private int endOffset;
        private String source;
        private final boolean synthetic;

        public Cell(int startOffset, int endOffset, String source, boolean synthetic) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.source = source;
            this.synthetic = synthetic;
        }

        private void shift(int delta) {
            startOffset += delta;
            endOffset += delta;
        }

        private void shiftChange(int position, int removedEnd, int delta) {
            if (removedEnd <= startOffset) {
                shift(delta);
            } else if (position < endOffset) {
                endOffset += delta;
            }
        }

        public int startOffset() { return startOffset; }
        public int endOffset() { return endOffset; }
        public String source() { return source; }
        public boolean synthetic() { return synthetic; }
        public void setSource(String source) { this.source = source; }
    }

    public enum Alignment {
        DEFAULT,
        LEFT,
        CENTER,
        RIGHT
    }
}

package com.allan.atools.richtext.codearea;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 单个编辑器内 Markdown 表格的稳定标识、源码映射与显示状态。 */
public final class MarkdownTableDocumentState {
    private static final Pattern SEPARATOR = Pattern.compile(":?-{3,}:?");
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
        var lines = lines(text);
        var result = new ArrayList<Table>();
        int line = 0;
        while (line + 1 < lines.size()) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            if (lines.get(line).inFence() || lines.get(line + 1).inFence()) {
                line++;
                continue;
            }
            var header = parseRow(lines.get(line), true);
            var separator = parseRow(lines.get(line + 1), false);
            var alignments = parseAlignments(separator);
            if (header == null || alignments == null || header.cells().size() != alignments.size()) {
                line++;
                continue;
            }
            var rows = new ArrayList<Row>();
            rows.add(new Row(line, lines.get(line).start(), lines.get(line).end(), true, header.cells()));
            int endLine = line + 2;
            while (endLine < lines.size() && !lines.get(endLine).inFence()) {
                var body = parseRow(lines.get(endLine), true);
                if (body == null || body.cells().size() != header.cells().size()) {
                    break;
                }
                rows.add(new Row(endLine, lines.get(endLine).start(), lines.get(endLine).end(), false,
                        body.cells()));
                endLine++;
            }
            String ending = lines.get(line).ending().isEmpty() ? "\n" : lines.get(line).ending();
            result.add(new Table(lines.get(line).start(), lines.get(endLine - 1).end(), line, endLine - 1,
                    rows, alignments, ending, header.indent()));
            line = endLine;
        }
        return List.copyOf(result);
    }

    private static List<Line> lines(String text) {
        var result = new ArrayList<Line>();
        int start = 0;
        boolean inFence = false;
        char fenceCharacter = 0;
        int fenceLength = 0;
        while (start <= text.length()) {
            int newline = text.indexOf('\n', start);
            int rawEnd = newline < 0 ? text.length() : newline;
            int end = rawEnd > start && text.charAt(rawEnd - 1) == '\r' ? rawEnd - 1 : rawEnd;
            String ending = newline < 0 ? "" : end < rawEnd ? "\r\n" : "\n";
            String content = text.substring(start, end);
            boolean lineInFence = inFence;
            int contentStart = contentStart(content);
            if (contentStart >= 0 && contentStart < content.length()
                    && (content.charAt(contentStart) == '`' || content.charAt(contentStart) == '~')) {
                char character = content.charAt(contentStart);
                int count = count(content, contentStart, content.length(), character);
                if (count >= 3) {
                    lineInFence = true;
                    if (!inFence) {
                        inFence = true;
                        fenceCharacter = character;
                        fenceLength = count;
                    } else if (character == fenceCharacter && count >= fenceLength
                            && content.substring(contentStart + count).isBlank()) {
                        inFence = false;
                    }
                }
            }
            result.add(new Line(start, end, ending, content, lineInFence));
            if (newline < 0) {
                break;
            }
            start = newline + 1;
        }
        return result;
    }

    private static ParsedRow parseRow(Line line, boolean contentRow) {
        String text = line.content();
        int start = contentStart(text);
        if (start < 0 || start < text.length() && text.charAt(start) == '\t') {
            return null;
        }
        int end = text.length();
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start >= end) {
            return null;
        }
        var pipes = delimiters(text, start, end);
        if (pipes.isEmpty()) {
            return null;
        }
        boolean leading = pipes.get(0) == start;
        boolean trailing = pipes.get(pipes.size() - 1) == end - 1;
        int segmentStart = leading ? start + 1 : start;
        int firstPipe = leading ? 1 : 0;
        int lastPipe = trailing ? pipes.size() - 1 : pipes.size();
        var cells = new ArrayList<Cell>();
        for (int index = firstPipe; index < lastPipe; index++) {
            int pipe = pipes.get(index);
            cells.add(cell(line.start(), text, segmentStart, pipe));
            segmentStart = pipe + 1;
        }
        cells.add(cell(line.start(), text, segmentStart, trailing ? end - 1 : end));
        if (contentRow && cells.isEmpty()) {
            return null;
        }
        return new ParsedRow(text.substring(0, start), List.copyOf(cells));
    }

    private static int contentStart(String text) {
        int index = 0;
        while (true) {
            int spaces = 0;
            while (index < text.length() && text.charAt(index) == ' ') {
                index++;
                spaces++;
            }
            if (spaces > 3) {
                return -1;
            }
            if (index >= text.length() || text.charAt(index) != '>') {
                return index;
            }
            index++;
            if (index < text.length() && text.charAt(index) == ' ') {
                index++;
            }
        }
    }

    private static Cell cell(int lineStart, String text, int start, int end) {
        while (start < end && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) {
            start++;
        }
        while (end > start && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
            end--;
        }
        return new Cell(lineStart + start, lineStart + end, text.substring(start, end));
    }

    private static List<Integer> delimiters(String text, int start, int end) {
        var result = new ArrayList<Integer>();
        int codeTicks = 0;
        for (int index = start; index < end;) {
            char character = text.charAt(index);
            if (character == '`' && !escaped(text, index)) {
                int count = count(text, index, end, '`');
                if (codeTicks == 0) {
                    codeTicks = hasClosingTicks(text, index + count, end, count) ? count : 0;
                } else if (codeTicks == count) {
                    codeTicks = 0;
                }
                index += count;
                continue;
            }
            if (character == '|' && codeTicks == 0 && !escaped(text, index)) {
                result.add(index);
            }
            index++;
        }
        return result;
    }

    private static boolean hasClosingTicks(String text, int start, int end, int expected) {
        for (int index = start; index < end;) {
            if (text.charAt(index) != '`' || escaped(text, index)) {
                index++;
                continue;
            }
            int count = count(text, index, end, '`');
            if (count == expected) {
                return true;
            }
            index += count;
        }
        return false;
    }

    private static int count(String text, int start, int end, char character) {
        int index = start;
        while (index < end && text.charAt(index) == character) {
            index++;
        }
        return index - start;
    }

    private static boolean escaped(String text, int index) {
        int count = 0;
        while (--index >= 0 && text.charAt(index) == '\\') {
            count++;
        }
        return count % 2 == 1;
    }

    private static List<Alignment> parseAlignments(ParsedRow separator) {
        if (separator == null) {
            return null;
        }
        var result = new ArrayList<Alignment>();
        for (var cell : separator.cells()) {
            String value = cell.source();
            if (!SEPARATOR.matcher(value).matches()) {
                return null;
            }
            boolean left = value.startsWith(":");
            boolean right = value.endsWith(":");
            result.add(left && right ? Alignment.CENTER : left ? Alignment.LEFT
                    : right ? Alignment.RIGHT : Alignment.DEFAULT);
        }
        return List.copyOf(result);
    }

    private record Line(int start, int end, String ending, String content, boolean inFence) {}
    private record ParsedRow(String indent, List<Cell> cells) {}

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
            table.shift(position, removedEnd, delta, lineDelta);
        }
        for (var table : tables) {
            if (table.id.equals(tableId)) {
                table.valid = true;
                return;
            }
        }
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
        var matchedOld = new HashSet<Table>();
        var matchedNew = new HashSet<Table>();
        for (var next : parsed) {
            if (tables.stream().filter(old -> overlaps(old, next)).count() != 1) {
                continue;
            }
            Table exact = null;
            for (var old : tables) {
                if (!matchedOld.contains(old) && old.startOffset == next.startOffset) {
                    if (parsed.stream().filter(candidate -> overlaps(old, candidate)).count() != 1) {
                        continue;
                    }
                    if (exact != null) {
                        exact = null;
                        break;
                    }
                    exact = old;
                }
            }
            if (exact != null) {
                next.copyViewState(exact);
                matchedOld.add(exact);
                matchedNew.add(next);
            }
        }
        for (var next : parsed) {
            if (matchedNew.contains(next)) {
                continue;
            }
            Table candidate = null;
            for (var old : tables) {
                if (matchedOld.contains(old) || !overlaps(old, next)) {
                    continue;
                }
                if (candidate != null) {
                    candidate = null;
                    break;
                }
                candidate = old;
            }
            if (candidate != null && overlapCount(candidate, parsed, matchedNew) == 1) {
                next.copyViewState(candidate);
                matchedOld.add(candidate);
                matchedNew.add(next);
            }
        }

        var result = new ArrayList<>(parsed);
        for (var old : tables) {
            if (!matchedOld.contains(old) && !old.valid && old.endOffset > old.startOffset
                    && parsed.stream().noneMatch(next -> overlaps(old, next))) {
                result.add(old);
            }
        }
        result.sort((first, second) -> Integer.compare(first.startOffset, second.startOffset));
        tables = List.copyOf(result);
    }

    private static int overlapCount(Table old, List<Table> parsed, Set<Table> matchedNew) {
        int count = 0;
        for (var next : parsed) {
            if (!matchedNew.contains(next) && overlaps(old, next)) {
                count++;
            }
        }
        return count;
    }

    private static boolean overlaps(Table first, Table second) {
        return first.startOffset < second.endOffset && second.startOffset < first.endOffset;
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

        public Row(int line, int startOffset, int endOffset, boolean header, List<Cell> cells) {
            this.line = line;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.header = header;
            this.cells = List.copyOf(cells);
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
    }

    public static final class Cell {
        private int startOffset;
        private int endOffset;
        private String source;

        public Cell(int startOffset, int endOffset, String source) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.source = source;
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
        public void setSource(String source) { this.source = source; }
    }

    public enum Alignment {
        DEFAULT,
        LEFT,
        CENTER,
        RIGHT
    }
}

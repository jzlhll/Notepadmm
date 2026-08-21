package com.allan.atools.richtext.codearea.keywordhelper;

import com.allan.atools.bean.SearchParams;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Markdown 语法高亮：commonmark AST 解析生成 StyleSpans。
 * 嵌套元素（标题内加粗、粗斜体叠加、代码块内容不高亮等）由 AST 结构天然保证。
 */
public final class EditorKeywordHelperImplMarkdown extends EditorKeywordHelperAbstract {
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    private static final Pattern QUOTE_MARKER_PATTERN = Pattern.compile(">\\h?");
    private static final Pattern LIST_MARKER_PATTERN = Pattern.compile("(?:[-+*]|\\d+[.)])\\h+(?:\\[[ xX]\\]\\h+)?");

    private static final int STYLE_CODE = 5;
    private static final int STYLE_INLINE_CODE = 6;
    private static final int STYLE_TABLE_MARK = 7;
    private static final int STYLE_QUOTE = 8;
    private static final int STYLE_LIST = 9;
    private static final int STYLE_LINK = 10;
    private static final int STYLE_IMAGE = 11;
    private static final int STYLE_BOLD = 12;
    private static final int STYLE_ITALIC = 13;
    private static final int STYLE_STRIKETHROUGH = 14;
    private static final int STYLE_TEMPORARY = 15;
    private static final int STYLE_SEARCH = 16;
    private static final int STYLE_COUNT = 17;
    private static final int EVENT_META_BITS = 6;
    private static final int EVENT_STYLE_MASK = 31;

    private static final String[] STYLE_CLASSES = {
            "markdown-title-1", "markdown-title-2", "markdown-title-3", "markdown-title-4", "markdown-title-5",
            "markdown-code", "markdown-inline-code", "markdown-table-mark", "markdown-quote", "markdown-list",
            "markdown-link", "markdown-image", "markdown-bold", "markdown-italic", "markdown-strikethrough",
            "temporary", "search"
    };

    private final HashMap<Integer, Set<String>> styleClassAndSetMap = new HashMap<>();

    @Override
    public Pattern getPattern(SearchParams temporary, SearchParams search) {
        synchronized (LOCK) {
            var paramsPatterns = paramsToPatterns(temporary, search);
            var patternParts = new ArrayList<String>();
            if (paramsPatterns[0] != null) {
                patternParts.add("(?<TEMPORARY>" + paramsPatterns[0] + ")");
            }
            if (paramsPatterns[1] != null) {
                patternParts.add("(?<SEARCH>" + paramsPatterns[1] + ")");
            }
            return patternParts.isEmpty() ? null : compilePattern(String.join("|", patternParts));
        }
    }

    @Override
    protected StyleSpans<Collection<String>> computeHighlighting(
            String text, BooleanSupplier canContinue) {
        var root = PARSER.parse(text);
        if (!canContinue.getAsBoolean()) {
            return null;
        }
        var events = new EventBuffer();
        root.accept(new MarkdownRegionVisitor(text, events));
        if (!canContinue.getAsBoolean()) {
            return null;
        }
        addSearchRegions(text, events);
        if (!canContinue.getAsBoolean()) {
            return null;
        }
        return buildStyleSpans(events, text.length(), canContinue);
    }

    private void addSearchRegions(String text, EventBuffer events) {
        if (mLastMatcher == null) {
            return;
        }
        var matcher = mLastMatcher.matcher(text);
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) {
                continue;
            }
            int styleId = mIsTemporaryEnabled && matcher.group("TEMPORARY") != null ? STYLE_TEMPORARY :
                    mIsSearchEnabled && matcher.group("SEARCH") != null ? STYLE_SEARCH : -1;
            if (styleId >= 0) {
                events.addRegion(matcher.start(), matcher.end(), styleId);
            }
        }
    }

    /**
     * 事件扫描法合成重叠区间（如 **bold *italic*** 同时持有 bold 与 italic 样式类）。
     */
    private StyleSpans<Collection<String>> buildStyleSpans(EventBuffer events, int textLength,
                                                            BooleanSupplier canContinue) {
        events.sort();
        if (!canContinue.getAsBoolean()) {
            return null;
        }
        var activeCounts = new int[STYLE_COUNT];
        int activeMask = 0;
        var spansBuilder = new StyleSpansBuilder<Collection<String>>(Math.max(1, events.size() + 1));
        int prev = 0;
        int eventIndex = 0;
        while (eventIndex < events.size()) {
            if ((eventIndex & 1023) == 0 && !canContinue.getAsBoolean()) {
                return null;
            }
            int position = events.positionAt(eventIndex);
            if (position > prev) {
                spansBuilder.add(activeStyles(activeMask), position - prev);
                prev = position;
            }
            while (eventIndex < events.size() && events.positionAt(eventIndex) == position) {
                int styleId = events.styleIdAt(eventIndex);
                if (events.isAddAt(eventIndex)) {
                    activeCounts[styleId]++;
                    activeMask |= 1 << styleId;
                } else {
                    activeCounts[styleId]--;
                    if (activeCounts[styleId] == 0) {
                        activeMask &= ~(1 << styleId);
                    }
                }
                eventIndex++;
            }
        }
        spansBuilder.add(activeStyles(activeMask), textLength - prev);
        return spansBuilder.create();
    }

    private Collection<String> activeStyles(int activeMask) {
        if (activeMask == 0) {
            return Collections.emptyList();
        }
        return styleClassAndSetMap.computeIfAbsent(activeMask, mask -> {
            var styles = new HashSet<String>();
            for (int styleId = 0; styleId < STYLE_COUNT; styleId++) {
                if ((mask & (1 << styleId)) != 0) {
                    styles.add(STYLE_CLASSES[styleId]);
                }
            }
            return Set.copyOf(styles);
        });
    }

    private final class MarkdownRegionVisitor extends AbstractVisitor {
        private final String text;
        private final EventBuffer events;

        MarkdownRegionVisitor(String text, EventBuffer events) {
            this.text = text;
            this.events = events;
        }

        @Override
        public void visit(Heading heading) {
            addNodeRegions(heading, Math.min(heading.getLevel(), 5) - 1);
            visitChildren(heading);
        }

        @Override
        public void visit(FencedCodeBlock block) {
            addNodeRegions(block, STYLE_CODE);
        }

        @Override
        public void visit(IndentedCodeBlock block) {
            addNodeRegions(block, STYLE_CODE);
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            addMarkerRegions(blockQuote, QUOTE_MARKER_PATTERN, STYLE_QUOTE);
            visitChildren(blockQuote);
        }

        @Override
        public void visit(ListItem listItem) {
            addMarkerRegions(listItem, LIST_MARKER_PATTERN, STYLE_LIST);
            visitChildren(listItem);
        }

        @Override
        public void visit(Code code) {
            addNodeRegions(code, STYLE_INLINE_CODE);
        }

        @Override
        public void visit(Link link) {
            addNodeRegions(link, STYLE_LINK);
        }

        @Override
        public void visit(Image image) {
            addNodeRegions(image, STYLE_IMAGE);
        }

        @Override
        public void visit(StrongEmphasis emphasis) {
            addNodeRegions(emphasis, STYLE_BOLD);
            visitChildren(emphasis);
        }

        @Override
        public void visit(Emphasis emphasis) {
            addNodeRegions(emphasis, STYLE_ITALIC);
            visitChildren(emphasis);
        }

        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof TableBlock tableBlock) {
                addTableRegions(tableBlock);
            }
            visitChildren(customBlock);
        }

        @Override
        public void visit(CustomNode customNode) {
            if (customNode instanceof Strikethrough strikethrough) {
                addNodeRegions(strikethrough, STYLE_STRIKETHROUGH);
            }
            visitChildren(customNode);
        }

        private void addNodeRegions(Node node, int styleId) {
            for (var span : node.getSourceSpans()) {
                int start = span.getInputIndex();
                events.addRegion(start, start + span.getLength(), styleId);
            }
        }

        private void addMarkerRegions(Node node, Pattern markerPattern, int styleId) {
            var matcher = markerPattern.matcher(text);
            for (var span : node.getSourceSpans()) {
                int start = span.getInputIndex();
                int end = start + span.getLength();
                matcher.region(start, end);
                if (matcher.lookingAt()) {
                    events.addRegion(start, matcher.end(), styleId);
                }
            }
        }

        private void addTableRegions(TableBlock tableBlock) {
            var rowLines = new HashSet<Integer>();
            for (var section = tableBlock.getFirstChild(); section != null; section = section.getNext()) {
                if (section instanceof TableHead || section instanceof TableBody) {
                    for (var row = section.getFirstChild(); row != null; row = row.getNext()) {
                        if (row instanceof TableRow) {
                            for (var span : row.getSourceSpans()) {
                                rowLines.add(span.getLineIndex());
                            }
                        }
                    }
                }
            }
            for (var span : tableBlock.getSourceSpans()) {
                int start = span.getInputIndex();
                int end = start + span.getLength();
                if (rowLines.contains(span.getLineIndex())) {
                    int separator = text.indexOf('|', start);
                    while (separator >= 0 && separator < end) {
                        events.addRegion(separator, separator + 1, STYLE_TABLE_MARK);
                        separator = text.indexOf('|', separator + 1);
                    }
                } else {
                    events.addRegion(start, end, STYLE_TABLE_MARK);
                }
            }
        }
    }

    private static final class EventBuffer {
        private long[] values = new long[64];
        private int size;

        void addRegion(int start, int end, int styleId) {
            if (end <= start) {
                return;
            }
            ensureCapacity(size + 2);
            values[size++] = encode(start, styleId, true);
            values[size++] = encode(end, styleId, false);
        }

        void sort() {
            Arrays.sort(values, 0, size);
        }

        int size() {
            return size;
        }

        int positionAt(int index) {
            return (int) (values[index] >>> EVENT_META_BITS);
        }

        int styleIdAt(int index) {
            return (int) ((values[index] >>> 1) & EVENT_STYLE_MASK);
        }

        boolean isAddAt(int index) {
            return (values[index] & 1) != 0;
        }

        private void ensureCapacity(int minCapacity) {
            if (minCapacity <= values.length) {
                return;
            }
            int newCapacity = values.length * 2;
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            values = Arrays.copyOf(values, newCapacity);
        }

        private static long encode(int position, int styleId, boolean add) {
            return ((long) position << EVENT_META_BITS) | ((long) styleId << 1) | (add ? 1 : 0);
        }
    }
}

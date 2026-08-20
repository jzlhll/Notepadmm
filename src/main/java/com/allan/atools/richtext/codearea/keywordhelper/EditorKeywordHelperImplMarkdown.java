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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

    private final HashMap<String, Set<String>> styleClassAndSetMap = new HashMap<>();

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
            return patternParts.isEmpty() ? null : Pattern.compile(String.join("|", patternParts));
        }
    }

    @Override
    public Function<String, StyleSpans<Collection<String>>> getComputeHighlightFun() {
        return this::computeHighlighting;
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        var regions = new ArrayList<Region>();
        PARSER.parse(text).accept(new MarkdownRegionVisitor(text, regions));
        addSearchRegions(text, regions);
        return buildStyleSpans(regions, text.length());
    }

    private void addSearchRegions(String text, List<Region> regions) {
        if (mLastMatcher == null) {
            return;
        }
        var matcher = mLastMatcher.matcher(text);
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) {
                continue;
            }
            String styleClass = mIsTemporaryEnabled && matcher.group("TEMPORARY") != null ? "temporary" :
                    mIsSearchEnabled && matcher.group("SEARCH") != null ? "search" : null;
            if (styleClass != null) {
                regions.add(new Region(matcher.start(), matcher.end(), styleClass));
            }
        }
    }

    /**
     * 事件扫描法合成重叠区间（如 **bold *italic*** 同时持有 bold 与 italic 样式类）。
     */
    private StyleSpans<Collection<String>> buildStyleSpans(List<Region> regions, int textLength) {
        record Ev(int pos, boolean add, String styleClass) {
        }
        var events = new ArrayList<Ev>(regions.size() * 2);
        for (var region : regions) {
            events.add(new Ev(region.start(), true, region.styleClass()));
            events.add(new Ev(region.end(), false, region.styleClass()));
        }
        events.sort((a, b) -> a.pos() != b.pos()
                ? Integer.compare(a.pos(), b.pos())
                : Boolean.compare(a.add(), b.add()));

        var active = new HashMap<String, Integer>();
        var spansBuilder = new StyleSpansBuilder<Collection<String>>(Math.max(1, events.size() / 2 + 1));
        int prev = 0;
        for (var ev : events) {
            if (ev.pos() > prev) {
                spansBuilder.add(activeStyles(active), ev.pos() - prev);
                prev = ev.pos();
            }
            active.merge(ev.styleClass(), ev.add() ? 1 : -1, Integer::sum);
            if (active.get(ev.styleClass()) == 0) {
                active.remove(ev.styleClass());
            }
        }
        spansBuilder.add(activeStyles(active), textLength - prev);
        return spansBuilder.create();
    }

    private Collection<String> activeStyles(Map<String, Integer> active) {
        if (active.isEmpty()) {
            return Collections.emptyList();
        }
        var key = active.size() == 1 ? active.keySet().iterator().next() : String.join(" ", active.keySet());
        return styleClassAndSetMap.computeIfAbsent(key, k -> Set.copyOf(active.keySet()));
    }

    private final class MarkdownRegionVisitor extends AbstractVisitor {
        private final String text;
        private final List<Region> regions;

        MarkdownRegionVisitor(String text, List<Region> regions) {
            this.text = text;
            this.regions = regions;
        }

        @Override
        public void visit(Heading heading) {
            addNodeRegions(heading, "markdown-title-" + Math.min(heading.getLevel(), 5));
            visitChildren(heading);
        }

        @Override
        public void visit(FencedCodeBlock block) {
            addNodeRegions(block, "markdown-code");
        }

        @Override
        public void visit(IndentedCodeBlock block) {
            addNodeRegions(block, "markdown-code");
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            addMarkerRegions(blockQuote, QUOTE_MARKER_PATTERN, "markdown-quote");
            visitChildren(blockQuote);
        }

        @Override
        public void visit(ListItem listItem) {
            addMarkerRegions(listItem, LIST_MARKER_PATTERN, "markdown-list");
            visitChildren(listItem);
        }

        @Override
        public void visit(Code code) {
            addNodeRegions(code, "markdown-inline-code");
        }

        @Override
        public void visit(Link link) {
            addNodeRegions(link, "markdown-link");
        }

        @Override
        public void visit(Image image) {
            addNodeRegions(image, "markdown-image");
        }

        @Override
        public void visit(StrongEmphasis emphasis) {
            addNodeRegions(emphasis, "markdown-bold");
            visitChildren(emphasis);
        }

        @Override
        public void visit(Emphasis emphasis) {
            addNodeRegions(emphasis, "markdown-italic");
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
                addNodeRegions(strikethrough, "markdown-strikethrough");
            }
            visitChildren(customNode);
        }

        private void addNodeRegions(Node node, String styleClass) {
            for (var span : node.getSourceSpans()) {
                int start = span.getInputIndex();
                regions.add(new Region(start, start + span.getLength(), styleClass));
            }
        }

        private void addMarkerRegions(Node node, Pattern markerPattern, String styleClass) {
            var matcher = markerPattern.matcher(text);
            for (var span : node.getSourceSpans()) {
                int start = span.getInputIndex();
                if (matcher.find(start) && matcher.start() == start) {
                    regions.add(new Region(start, matcher.end(), styleClass));
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
                    for (int i = start; i < end; i++) {
                        if (text.charAt(i) == '|') {
                            regions.add(new Region(i, i + 1, "markdown-table-mark"));
                        }
                    }
                } else {
                    regions.add(new Region(start, end, "markdown-table-mark"));
                }
            }
        }
    }

    private record Region(int start, int end, String styleClass) {
    }
}

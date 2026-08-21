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
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
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
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
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
    /** HTML <img> 标签：属性值带引号时内部可含 '>'，尾部 '/' 不计入属性 */
    private static final Pattern HTML_IMG_TAG_PATTERN = Pattern.compile(
            "(?i)<img\\b((?:\"[^\"]*\"|'[^']*'|[^'\">])*?)/?>");
    /** XML 代码块属性段：name = "value"（组 1=属性名 2=等号 3=属性值），与 EditorKeywordHelperImplXml 一致 */
    private static final Pattern XML_ATTRIBUTE_PATTERN = Pattern.compile(
            "([-.:\\w]+\\h*)(=)(\\h*(?:\"[^\"]*\"|'[^']*'))");

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
    private static final int STYLE_CODE_KEYWORD = 17;
    private static final int STYLE_CODE_STRING = 18;
    private static final int STYLE_CODE_COMMENT = 19;
    private static final int STYLE_CODE_PUNCT = 20;
    private static final int STYLE_CODE_TAG = 21;
    private static final int STYLE_CODE_TAG_MARK = 22;
    private static final int STYLE_CODE_ATTRIBUTE = 23;
    private static final int STYLE_CODE_ATTRIBUTE_VALUE = 24;
    private static final int STYLE_COUNT = 25;
    private static final int EVENT_META_BITS = 6;
    private static final int EVENT_STYLE_MASK = 31;

    private static final String[] STYLE_CLASSES = {
            "markdown-title-1", "markdown-title-2", "markdown-title-3", "markdown-title-4", "markdown-title-5",
            "markdown-code", "markdown-inline-code", "markdown-table-mark", "markdown-quote", "markdown-list",
            "markdown-link", "markdown-image", "markdown-bold", "markdown-italic", "markdown-strikethrough",
            "temporary", "search",
            "markdown-code-keyword", "markdown-code-string", "markdown-code-comment", "markdown-code-punct",
            "markdown-code-tag", "markdown-code-tagmark", "markdown-code-attribute", "markdown-code-attribute-value"
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
        root.accept(new MarkdownRegionVisitor(text, events, canContinue));
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
        private final BooleanSupplier canContinue;

        MarkdownRegionVisitor(String text, EventBuffer events, BooleanSupplier canContinue) {
            this.text = text;
            this.events = events;
            this.canContinue = canContinue;
        }

        @Override
        public void visit(Heading heading) {
            addNodeRegions(heading, Math.min(heading.getLevel(), 5) - 1);
            visitChildren(heading);
        }

        @Override
        public void visit(FencedCodeBlock block) {
            addNodeRegions(block, STYLE_CODE);
            addFencedCodeTokenRegions(block);
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
        public void visit(HtmlBlock block) {
            addHtmlImgRegions(block);
        }

        @Override
        public void visit(HtmlInline inline) {
            addHtmlImgRegions(inline);
        }

        /** 高亮 HTML {@code <img>} 标签本体（markdown-image 类），与 ![alt](path) 图片语法一致 */
        private void addHtmlImgRegions(Node node) {
            var spans = node.getSourceSpans();
            if (spans.isEmpty()) {
                return;
            }
            String literal = node instanceof HtmlBlock block ? block.getLiteral()
                    : node instanceof HtmlInline inline ? inline.getLiteral() : null;
            if (literal == null) {
                return;
            }
            // HtmlBlock 可能跨多行，literal 首字符对应 spans[0].inputIndex，标签偏移在此基准上叠加
            int baseOffset = spans.get(0).getInputIndex();
            Matcher matcher = HTML_IMG_TAG_PATTERN.matcher(literal);
            while (matcher.find()) {
                int start = baseOffset + matcher.start();
                int end = baseOffset + matcher.end();
                if (end > start) {
                    events.addRegion(start, end, STYLE_IMAGE);
                }
            }
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

        /**
         * 代码块内容按 info 语言（java/kotlin/c/cpp/csharp/xml 等）做 token 高亮，样式叠加在 markdown-code 上。
         * literal 首字符对齐 spans[1]（内容首行行首）；列表项内代码块的 literal 已剥离列表缩进，
         * 起点对不上时放弃 token 高亮（整块仍为 markdown-code）。
         */
        private void addFencedCodeTokenRegions(FencedCodeBlock block) {
            CodeBlockLanguages.CodeLanguage language = CodeBlockLanguages.of(block.getInfo());
            String literal = block.getLiteral();
            if (language == null || literal.isEmpty()) {
                return;
            }
            var spans = block.getSourceSpans();
            if (spans.size() < 2) {
                return;
            }
            int base = spans.get(1).getInputIndex();
            int lineEnd = literal.indexOf('\n');
            String firstLine = lineEnd >= 0 ? literal.substring(0, lineEnd) : literal;
            if (firstLine.isEmpty() || !text.regionMatches(base, firstLine, 0, firstLine.length())) {
                return;
            }
            Matcher matcher = language.pattern().matcher(literal);
            int matchCount = 0;
            while (matcher.find()) {
                if ((matchCount++ & 255) == 0 && !canContinue.getAsBoolean()) {
                    return;
                }
                if (language.xml()) {
                    addXmlTokenRegions(matcher, base);
                } else {
                    addJavaTokenRegion(matcher, base);
                }
            }
        }

        /** Java 系语言（含 kotlin/c/cpp/csharp）：PAREN/BRACE/BRACKET/SEMICOLON 统一为标点色 */
        private void addJavaTokenRegion(Matcher matcher, int base) {
            int styleId = matcher.group("KEYWORD") != null ? STYLE_CODE_KEYWORD
                    : matcher.group("STRING") != null ? STYLE_CODE_STRING
                    : matcher.group("COMMENT") != null ? STYLE_CODE_COMMENT
                    : STYLE_CODE_PUNCT;
            events.addRegion(base + matcher.start(), base + matcher.end(), styleId);
        }

        /** XML/HTML：ELEMENT 组内再按 开闭尖括号/标签名/属性名/等号/属性值 细分 */
        private void addXmlTokenRegions(Matcher matcher, int base) {
            if (matcher.group("COMMENT") != null) {
                events.addRegion(base + matcher.start(), base + matcher.end(), STYLE_CODE_COMMENT);
                return;
            }
            addTokenRegion(base, matcher, 2, STYLE_CODE_TAG_MARK);
            addTokenRegion(base, matcher, 3, STYLE_CODE_TAG);
            int attributesStart = matcher.start(4);
            Matcher attrMatcher = XML_ATTRIBUTE_PATTERN.matcher(matcher.group(4));
            while (attrMatcher.find()) {
                addTokenRegion(base + attributesStart, attrMatcher, 1, STYLE_CODE_ATTRIBUTE);
                addTokenRegion(base + attributesStart, attrMatcher, 2, STYLE_CODE_TAG_MARK);
                addTokenRegion(base + attributesStart, attrMatcher, 3, STYLE_CODE_ATTRIBUTE_VALUE);
            }
            addTokenRegion(base, matcher, 5, STYLE_CODE_TAG_MARK);
        }

        private void addTokenRegion(int base, Matcher matcher, int group, int styleId) {
            int start = matcher.start(group);
            int end = matcher.end(group);
            if (end > start) {
                events.addRegion(base + start, base + end, styleId);
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

    /** 围栏代码块 info 语言 → token pattern（复用各语言 helper 的关键字/字符串/注释规则），懒加载缓存 */
    private static final class CodeBlockLanguages {
        private record CodeLanguage(Pattern pattern, boolean xml) {
        }

        private static final HashMap<String, CodeLanguage> CACHE = new HashMap<>();

        static synchronized CodeLanguage of(String info) {
            String language = normalize(info);
            return language == null ? null : CACHE.computeIfAbsent(language, CodeBlockLanguages::create);
        }

        /** 取 info 首个空白前的 token 归一化（如 "java xx" → java），不支持的语言返回 null */
        private static String normalize(String info) {
            if (info == null || info.isBlank()) {
                return null;
            }
            String token = info.trim();
            int space = token.indexOf(' ');
            if (space >= 0) {
                token = token.substring(0, space);
            }
            token = token.toLowerCase(Locale.ROOT);
            return switch (token) {
                case "java" -> "java";
                case "kotlin", "kt" -> "kotlin";
                case "c", "cpp", "c++", "cc", "h", "hpp" -> "c";
                case "csharp", "cs", "c#" -> "csharp";
                case "xml", "html", "htm" -> "xml";
                default -> null;
            };
        }

        private static CodeLanguage create(String language) {
            EditorKeywordHelperAbstract helper = switch (language) {
                case "java" -> new EditorKeywordHelperImplJava();
                case "kotlin" -> new EditorKeywordHelperImplKotlin();
                case "c" -> new EditorKeywordHelperImplCC();
                case "csharp" -> new EditorKeywordHelperImplCSharp();
                case "xml" -> new EditorKeywordHelperImplXml();
                default -> null;
            };
            // getPattern(null, null) 不含 temporary/search 组，返回纯语言 token pattern
            return helper == null ? null
                    : new CodeLanguage(helper.getPattern(null, null), "xml".equals(language));
        }
    }
}

package com.allan.atools.richtext.codearea.keywordhelper;

import com.allan.atools.bean.SearchParams;
import com.allan.uilibs.richtexts.CodeArea;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
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
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 语法高亮：commonmark AST 解析生成 StyleSpans。
 * 嵌套元素（标题内加粗、粗斜体叠加、代码块内容不高亮等）由 AST 结构天然保证。
 */
public final class EditorKeywordHelperImplMarkdown extends EditorKeywordHelperAbstract {
    private volatile boolean previewEnabled = true;

    public void setPreviewEnabled(boolean enabled) { previewEnabled = enabled; }

    private MarkdownAstCache astCache = new MarkdownAstCache();
    private record CodeTokensKey(String source, String literal, String info, int indent) {}
    private final LinkedHashMap<CodeTokensKey, long[]> codeTokens = new LinkedHashMap<>(16, 0.75f, true);
    private int cachedCodeCharacters;
    private static final Pattern QUOTE_MARKER_PATTERN = Pattern.compile(">\\h?");
    private static final Pattern LIST_MARKER_PATTERN = Pattern.compile("(?:[-+*]|\\d+[.)])\\h+(?:\\[[ xX]\\]\\h+)?");
    private static final Pattern BULLET_MARKER_PATTERN = Pattern.compile("\\h*([-+*])(?=\\h|$)");
    private static final Pattern ITEM_MARKER_PATTERN = Pattern.compile("\\h*([-+*]|\\d+[.)])(?=\\h|$)");
    private static final Pattern TASK_MARKER_PATTERN = Pattern.compile("\\h+(\\[[ xX]\\])(?=\\h|$)");
    private static final Pattern BARE_LINK_PATTERN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_])https?://[\\p{L}\\p{N}\\[][^\\s\\p{Z}<>\"'`\\\\，。；：！？、（）【】《》“”‘’]*");
    /** HTML <img> 标签：属性值带引号时内部可含 '>'，尾部 '/' 不计入属性 */
    private static final Pattern HTML_IMG_TAG_PATTERN = Pattern.compile(
            "(?i)<img\\b((?:\"[^\"]*\"|'[^']*'|[^'\">])*?)/?>");

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
    private static final int STYLE_EMOJI = 25;
    private static final int STYLE_CODE_FENCE = 26;
    private static final int STYLE_SYNTAX = 27;
    private static final int STYLE_HEADING_SIX = 28;
    private static final int STYLE_MATH = 29;
    private static final int STYLE_HIGHLIGHT = 30;
    private static final int STYLE_COLLAPSIBLE = 31;
    private static final int STYLE_COUNT = 32;
    private static final int EVENT_META_BITS = 6;
    private static final int EVENT_STYLE_MASK = 31;

    private static final String[] STYLE_CLASSES = {
            "markdown-title-1", "markdown-title-2", "markdown-title-3", "markdown-title-4", "markdown-title-5",
            "markdown-code", "markdown-inline-code", "markdown-table-mark", "markdown-quote", "markdown-list",
            "markdown-link", "markdown-image", "markdown-bold", "markdown-italic", "markdown-strikethrough",
            "temporary", "search",
            "markdown-code-keyword", "markdown-code-string", "markdown-code-comment", "markdown-code-punct",
            "markdown-code-tag", "markdown-code-tagmark", "markdown-code-attribute", "markdown-code-attribute-value",
            "markdown-emoji", "markdown-code-fence", "markdown-syntax-marker", "markdown-title-6", "markdown-math", "markdown-highlight",
            "markdown-syntax-collapsible"
    };
    private static final Collection<String> DEFAULT_TEXT_STYLE = Collections.singleton("editor-default-label");

    private final HashMap<Integer, Set<String>> styleClassAndSetMap = new HashMap<>();

    public void setAstCache(MarkdownAstCache astCache) {
        this.astCache = astCache;
    }

    public String findLinkDestination(String text, int position) {
        var destination = new String[1];
        astCache.parse(text).accept(new AbstractVisitor() {
            @Override
            protected void visitChildren(Node parent) {
                if (destination[0] == null) {
                    super.visitChildren(parent);
                }
            }

            @Override
            public void visit(Link link) {
                for (var span : link.getSourceSpans()) {
                    if (position >= span.getInputIndex()
                            && position < span.getInputIndex() + span.getLength()) {
                        destination[0] = link.getDestination();
                        return;
                    }
                }
            }

            @Override
            public void visit(Text node) {
                forEachBareLink(text, node, () -> destination[0] == null, (start, end) -> {
                    if (position >= start && position < end) {
                        destination[0] = text.substring(start, end);
                    }
                });
            }

            @Override
            public void visit(Image image) {}
        });
        return destination[0];
    }

    private static void forEachBareLink(String text, Text node, BooleanSupplier canContinue,
                                        BiConsumer<Integer, Integer> action) {
        var matcher = BARE_LINK_PATTERN.matcher(text);
        for (var span : node.getSourceSpans()) {
            matcher.region(span.getInputIndex(), span.getInputIndex() + span.getLength());
            while (matcher.find()) {
                if (!canContinue.getAsBoolean()) {
                    return;
                }
                int start = matcher.start();
                int end = matcher.end();
                int parentheses = 0;
                int brackets = 0;
                for (int index = start; index < end; index++) {
                    switch (text.charAt(index)) {
                        case '(' -> parentheses++;
                        case ')' -> parentheses--;
                        case '[' -> brackets++;
                        case ']' -> brackets--;
                    }
                }
                // 外围强调标记由 AST 排除，URL 尾部的合法字符保持原样。
                while (end > start) {
                    char last = text.charAt(end - 1);
                    if (last == ')' && parentheses < 0) {
                        parentheses++;
                        end--;
                    } else if (last == ']' && brackets < 0) {
                        brackets++;
                        end--;
                    } else {
                        break;
                    }
                }
                action.accept(start, end);
            }
        }
    }

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
        if (!canContinue.getAsBoolean()) {
            return null;
        }
        var events = new EventBuffer();
        var visitor = new MarkdownRegionVisitor(text, events, canContinue);
        if (previewEnabled) {
            var snapshot = astCache.snapshot(text);
            snapshot.getRoot().accept(visitor);
            for (var marker : snapshot.getMarkers()) {
                events.addRegion(marker.getStart(), marker.getEnd(), STYLE_SYNTAX);
            }
            for (var group : snapshot.getSyntaxGroups()) {
                for (var marker : group.getMarkers()) {
                    events.addRegion(marker.getStart(), marker.getEnd(), STYLE_COLLAPSIBLE);
                }
            }
        }
        if (!canContinue.getAsBoolean()) {
            return null;
        }
        addEmojiFontRegions(text, events);
        addSearchRegions(text, events);
        if (!canContinue.getAsBoolean()) {
            return null;
        }
        return buildStyleSpans(events, text, visitor.bulletGlyphs, visitor.taskMarkers, visitor.taskPrefixes, canContinue);
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

    private void addEmojiFontRegions(String text, EventBuffer events) {
        for (int index = 0; index < text.length(); ) {
            int end = MarkdownEmojiShortcodes.unicodeEnd(text, index);
            if (end > index) {
                events.addRegion(index, end, STYLE_EMOJI);
                index = end;
            } else {
                index += Character.charCount(text.codePointAt(index));
            }
        }
    }

    /**
     * 事件扫描法合成重叠区间（如 **bold *italic*** 同时持有 bold 与 italic 样式类）。
     */
    private StyleSpans<Collection<String>> buildStyleSpans(EventBuffer events, String text, Map<Integer, String> bulletGlyphs,
                                                            Set<Integer> taskMarkers, NavigableMap<Integer, Integer> taskPrefixes,
                                                            BooleanSupplier canContinue) {
        int textLength = text.length();
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
                var styles = activeStyles(activeMask);
                if ((activeMask & (1 << STYLE_EMOJI)) != 0) {
                    String emoji = MarkdownEmojiShortcodes.resolve(text.substring(prev, position));
                    if (emoji != null) {
                        var withGlyph = new HashSet<>(styles);
                        withGlyph.add(CodeArea.MARKDOWN_EMOJI_GLYPH_PREFIX + emoji);
                        styles = Set.copyOf(withGlyph);
                    }
                }
                String bullet = bulletGlyphs.get(prev);
                if (bullet != null && position == prev + 1) {
                    var withBullet = new HashSet<>(styles);
                    withBullet.add(CodeArea.MARKDOWN_LIST_BULLET_PREFIX + bullet);
                    styles = Set.copyOf(withBullet);
                }
                var prefix = taskPrefixes.floorEntry(prev);
                boolean taskPrefix = prefix != null && position <= prefix.getValue();
                boolean taskMarker = taskMarkers.contains(prev) && position == prev + 3;
                if (taskPrefix || taskMarker) {
                    var withTask = new HashSet<>(styles);
                    if (taskPrefix) withTask.add(CodeArea.MARKDOWN_TASK_PREFIX_CLASS);
                    if (taskMarker) withTask.add(CodeArea.MARKDOWN_TASK_MARKER_CLASS);
                    styles = Set.copyOf(withTask);
                }
                spansBuilder.add(styles, position - prev);
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
            return DEFAULT_TEXT_STYLE;
        }
        return styleClassAndSetMap.computeIfAbsent(activeMask, mask -> {
            var styles = new HashSet<String>();
            boolean hasTextColorStyle = false;
            for (int styleId = 0; styleId < STYLE_COUNT; styleId++) {
                if ((mask & (1 << styleId)) != 0) {
                    styles.add(STYLE_CLASSES[styleId]);
                    if (styleId == STYLE_SYNTAX || styleId == STYLE_HEADING_SIX || styleId <= STYLE_IMAGE
                            || styleId >= STYLE_CODE_KEYWORD && styleId <= STYLE_CODE_ATTRIBUTE_VALUE) {
                        hasTextColorStyle = true;
                    }
                }
            }
            if (!hasTextColorStyle) {
                styles.addAll(DEFAULT_TEXT_STYLE);
            }
            return Set.copyOf(styles);
        });
    }

    private final class MarkdownRegionVisitor extends AbstractVisitor {
        private final String text;
        private final EventBuffer events;
        private final BooleanSupplier canContinue;
        private final HashMap<Integer, String> bulletGlyphs = new HashMap<>();
        private final HashSet<Integer> taskMarkers = new HashSet<>();
        private final TreeMap<Integer, Integer> taskPrefixes = new TreeMap<>();

        MarkdownRegionVisitor(String text, EventBuffer events, BooleanSupplier canContinue) {
            this.text = text;
            this.events = events;
            this.canContinue = canContinue;
        }

        @Override
        public void visit(Heading heading) {
            addNodeRegions(heading, heading.getLevel() == 6 ? STYLE_HEADING_SIX : heading.getLevel() - 1);
            visitChildren(heading);
        }

        @Override
        public void visit(FencedCodeBlock block) {
            addNodeRegions(block, STYLE_CODE);
            addFencedCodeTokenRegions(block);
            var spans = block.getSourceSpans();
            if (spans.isEmpty()) {
                return;
            }
            char fence = block.getFenceCharacter().charAt(0);
            for (int index = 0; index < 2; index++) {
                if (index == 1 && block.getClosingFenceLength() == null) {
                    break;
                }
                var span = spans.get(index == 0 ? 0 : spans.size() - 1);
                int start = span.getInputIndex();
                int end = start + span.getLength();
                while (start < end && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) {
                    start++;
                }
                int fenceEnd = start;
                while (fenceEnd < end && text.charAt(fenceEnd) == fence) {
                    fenceEnd++;
                }
                if (fenceEnd > start) {
                    events.addRegion(start, fenceEnd, STYLE_CODE_FENCE);
                }
            }
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
            if (!listItem.getSourceSpans().isEmpty()) {
                var span = listItem.getSourceSpans().get(0);
                int end = span.getInputIndex() + span.getLength();
                var item = ITEM_MARKER_PATTERN.matcher(text).region(span.getInputIndex(), end);
                if (item.lookingAt()) {
                    var task = TASK_MARKER_PATTERN.matcher(text).region(item.end(), end);
                    if (task.lookingAt()) {
                        int start = task.start(1);
                        taskMarkers.add(start);
                        taskPrefixes.put(item.start(1), start);
                        events.addRegion(item.start(1), start, STYLE_LIST);
                        events.addRegion(start, start + 3, STYLE_LIST);
                    }
                }
            }
            if (listItem.getParent() instanceof BulletList && !listItem.getSourceSpans().isEmpty()) {
                var span = listItem.getSourceSpans().get(0);
                int end = span.getInputIndex() + span.getLength();
                var marker = BULLET_MARKER_PATTERN.matcher(text).region(span.getInputIndex(), end);
                if (marker.lookingAt() && !TASK_MARKER_PATTERN.matcher(text).region(marker.end(), end).lookingAt()) {
                    int depth = 0;
                    for (Node parent = listItem; parent != null; parent = parent.getParent()) {
                        if (parent instanceof ListItem) depth++;
                    }
                    int position = marker.start(1);
                    bulletGlyphs.put(position, depth == 1 ? "•" : depth == 2 ? "◦" : "▪");
                    events.addRegion(position, position + 1, STYLE_LIST);
                }
            }
            visitChildren(listItem);
        }

        @Override
        public void visit(Code code) {
            addNodeRegions(code, STYLE_INLINE_CODE);
        }

        @Override
        public void visit(Link link) {
            addNodeRegions(link, STYLE_LINK);
            visitChildren(link);
        }

        @Override
        public void visit(Text node) {
            forEachBareLink(text, node, canContinue, (start, end) -> events.addRegion(start, end, STYLE_LINK));
        }

        @Override
        public void visit(Image image) {
            addNodeRegions(image, STYLE_IMAGE);
        }

        @Override
        public void visit(LinkReferenceDefinition definition) {
            addNodeRegions(definition, STYLE_LINK);
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
            } else if (customBlock instanceof MarkdownMathBlock) {
                addNodeRegions(customBlock, STYLE_MATH);
            }
            visitChildren(customBlock);
        }

        @Override
        public void visit(CustomNode customNode) {
            if (customNode instanceof Strikethrough strikethrough) {
                addNodeRegions(strikethrough, STYLE_STRIKETHROUGH);
            } else if (customNode instanceof MarkdownEmoji) {
                addNodeRegions(customNode, STYLE_EMOJI);
            } else if (customNode instanceof MarkdownMath) {
                addNodeRegions(customNode, STYLE_MATH);
            } else if (customNode instanceof MarkdownDecoration decoration && "mark".equals(decoration.getTag())) {
                addNodeRegions(customNode, STYLE_HIGHLIGHT);
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

        /** 逐行记录 literal 与原文的对应位置，保留引用前缀和列表缩进。 */
        private void addFencedCodeTokenRegions(FencedCodeBlock block) {
            var spans = block.getSourceSpans();
            if (spans.isEmpty() || block.getLiteral().isEmpty() || MarkdownCodeLanguages.normalize(block.getInfo()) == null) return;
            int base = spans.get(0).getInputIndex();
            var last = spans.get(spans.size() - 1);
            int end = last.getInputIndex() + last.getLength();
            if (end - base > 262_144) {
                computeFencedCodeTokenRegions(block);
                return;
            }
            var key = new CodeTokensKey(text.substring(base, end), block.getLiteral(), block.getInfo(), block.getFenceIndent());
            var cached = codeTokens.get(key);
            if (cached != null) {
                events.addRelative(cached, base);
                return;
            }
            int firstEvent = events.size();
            computeFencedCodeTokenRegions(block);
            if (!canContinue.getAsBoolean()) return;
            codeTokens.put(key, events.relativeSlice(firstEvent, base));
            cachedCodeCharacters += key.source().length() + key.literal().length();
            while (codeTokens.size() > 128 || cachedCodeCharacters > 2_097_152) {
                var oldest = codeTokens.keySet().iterator().next();
                cachedCodeCharacters -= oldest.source().length() + oldest.literal().length();
                codeTokens.remove(oldest);
            }
        }

        private void computeFencedCodeTokenRegions(FencedCodeBlock block) {
            var language = MarkdownCodeLanguages.normalize(block.getInfo());
            String literal = block.getLiteral();
            if (language == null || literal.isEmpty()) return;
            var offsets = new int[literal.length()];
            java.util.Arrays.fill(offsets, -1);
            int cursor = 0;
            var spans = block.getSourceSpans();
            int literalLine = spans.get(0).getLineIndex() + 1;
            int contentEnd = spans.size() - (block.getClosingFenceLength() == null ? 0 : 1);
            for (int index = 1; index < contentEnd && cursor < literal.length(); index++) {
                if (!canContinue.getAsBoolean()) return;
                var span = spans.get(index);
                // 空白行的 SourceSpan 会被省略，按实际行号对齐，不能把后续代码逐行错配。
                while (literalLine < span.getLineIndex() && cursor < literal.length()) {
                    int next = literal.indexOf('\n', cursor);
                    cursor = next < 0 ? literal.length() : next + 1;
                    literalLine++;
                }
                if (cursor >= literal.length()) break;
                if (span.getLineIndex() != literalLine) continue;
                int next = literal.indexOf('\n', cursor);
                int end = next < 0 ? literal.length() : next;
                var source = text.substring(span.getInputIndex(), span.getInputIndex() + span.getLength());
                String value = literal.substring(cursor, end);
                int valueStart = cursor;
                // 嵌套围栏的缩进可能把 Tab 展开为空格，仍按未改变的正文后缀映射词法颜色。
                if (!source.endsWith(value)) {
                    while (valueStart < end && (literal.charAt(valueStart) == ' ' || literal.charAt(valueStart) == '\t')) {
                        valueStart++;
                    }
                    value = literal.substring(valueStart, end);
                }
                if (!value.isEmpty() && source.endsWith(value)) {
                    int base = span.getInputIndex() + source.length() - value.length();
                    for (int offset = valueStart; offset < end; offset++) offsets[offset] = base + offset - valueStart;
                }
                cursor = end + 1;
                literalLine++;
            }
            MarkdownCodeLanguages.forEachToken(literal, language, canContinue, token -> {
                int style = switch (token.getStyle()) {
                    case "keyword" -> STYLE_CODE_KEYWORD;
                    case "string" -> STYLE_CODE_STRING;
                    case "comment" -> STYLE_CODE_COMMENT;
                    case "tag" -> STYLE_CODE_TAG;
                    case "tagmark" -> STYLE_CODE_TAG_MARK;
                    case "attribute" -> STYLE_CODE_ATTRIBUTE;
                    case "attribute-value" -> STYLE_CODE_ATTRIBUTE_VALUE;
                    default -> STYLE_CODE_PUNCT;
                };
                addMappedRegion(offsets, token.getStart(), token.getEnd(), style);
            });
        }

        private void addMappedRegion(int[] offsets, int start, int end, int style) {
            for (int index = start; index < end; ) {
                if (index >= offsets.length || offsets[index] < 0) { index++; continue; }
                int source = offsets[index++];
                int length = 1;
                while (index < end && offsets[index] == source + length) { index++; length++; }
                events.addRegion(source, source + length, style);
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

        long[] relativeSlice(int start, int base) {
            var result = Arrays.copyOfRange(values, start, size);
            long shift = (long) base << EVENT_META_BITS;
            for (int index = 0; index < result.length; index++) result[index] -= shift;
            return result;
        }

        void addRelative(long[] cached, int base) {
            ensureCapacity(size + cached.length);
            long shift = (long) base << EVENT_META_BITS;
            for (long event : cached) values[size++] = event + shift;
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

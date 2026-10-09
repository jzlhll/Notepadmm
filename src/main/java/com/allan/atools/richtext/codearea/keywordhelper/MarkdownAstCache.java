package com.allan.atools.richtext.codearea.keywordhelper;

import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.InlineParser;
import org.commonmark.parser.InlineParserContext;
import org.commonmark.parser.Parser;
import java.util.LinkedHashMap;


/** 在同一文档版本的 Markdown 功能之间复用 AST。 */
public final class MarkdownAstCache {
    private volatile MarkdownStructureSnapshot latestSnapshot;
    private final MarkdownInlineCache inlineCache = new MarkdownInlineCache();
    private final Parser parser = Parser.builder()
            .extensions(MarkdownExtensions.all())
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .inlineParserFactory(inlineCache::create)
            .build();
    private final LinkedHashMap<String, Parsed> versions = new LinkedHashMap<>(3, 0.75f, true);

    private static final class Parsed {
        final Node root;
        MarkdownStructureSnapshot snapshot;

        Parsed(Node root) { this.root = root; }
    }

    /** 仅此适配点依赖 CommonMark 0.30.0 内部实现，编译与运行都需定向导出该包。 */
    static InlineParser createInlineParser(InlineParserContext context) {
        return new org.commonmark.internal.InlineParserImpl(context);
    }

    public synchronized Node parse(String text) {
        return entry(text).root;
    }

    public MarkdownStructureSnapshot snapshot(String text) {
        var known = latestSnapshot;
        if (known != null && known.getText().equals(text)) return known;
        synchronized (this) {
            var value = entry(text);
            if (value.snapshot == null) value.snapshot = new MarkdownStructureSnapshot(text, value.root);
            latestSnapshot = value.snapshot;
            return value.snapshot;
        }
    }

    private Parsed entry(String text) {
        var value = versions.get(text);
        if (value != null) return value;
        inlineCache.beginDocument();
        value = new Parsed(parser.parse(text));
        versions.put(text, value);
        // 保留相邻两个版本，避免不同功能的后台任务交错时反复挤掉同一版解析结果。
        if (versions.size() > 2) versions.remove(versions.keySet().iterator().next());
        return value;
    }
}

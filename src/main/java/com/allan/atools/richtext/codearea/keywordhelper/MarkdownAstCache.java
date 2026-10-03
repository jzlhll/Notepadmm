package com.allan.atools.richtext.codearea.keywordhelper;

import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;


/** 在同一文档版本的 Markdown 功能之间复用 AST。 */
public final class MarkdownAstCache {
    private final Parser parser = Parser.builder()
            .extensions(MarkdownExtensions.all())
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    private String cachedText;
    private Node cachedRoot;
    private MarkdownStructureSnapshot cachedSnapshot;

    public MarkdownAstCache() {
    }

    public synchronized Node parse(String text) {
        if (cachedRoot == null || !text.equals(cachedText)) {
            cachedRoot = parser.parse(text);
            cachedText = text;
            cachedSnapshot = null;
        }
        return cachedRoot;
    }

    public synchronized MarkdownStructureSnapshot snapshot(String text) {
        var root = parse(text);
        if (cachedSnapshot == null) {
            cachedSnapshot = new MarkdownStructureSnapshot(text, root);
        }
        return cachedSnapshot;
    }
}

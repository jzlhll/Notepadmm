package com.allan.atools.richtext.codearea.keywordhelper;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.List;

/** 在同一文档版本的 Markdown 功能之间复用 AST。 */
public final class MarkdownAstCache {
    private final Parser parser = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    private String cachedText;
    private Node cachedRoot;

    public MarkdownAstCache() {
    }

    public synchronized Node parse(String text) {
        if (cachedRoot == null || !text.equals(cachedText)) {
            cachedRoot = parser.parse(text);
            cachedText = text;
        }
        return cachedRoot;
    }
}

package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.Node
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.Parser

/** 在同一文档版本的 Markdown 功能之间复用 AST。 */
class MarkdownAstCache {
    @Volatile private var latestSnapshot: MarkdownStructureSnapshot? = null
    private val inlineCache = MarkdownInlineCache()
    private val parser = Parser.builder()
        .extensions(MarkdownExtensions.all())
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .inlineParserFactory(inlineCache::create)
        .build()
    private val versions = LinkedHashMap<String, Parsed>(3, 0.75f, true)

    private class Parsed(val root: Node, var snapshot: MarkdownStructureSnapshot? = null)

    companion object {
        @JvmStatic
        fun createInlineParser(context: InlineParserContext): InlineParser = MarkdownStrikethroughParser.create(context)

        /** 仅此适配点依赖 CommonMark 0.30.0 内部实现，编译与运行都需定向导出该包。 */
        @JvmStatic
        @Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
        fun createCommonMarkInlineParser(context: InlineParserContext): InlineParser =
            org.commonmark.internal.InlineParserImpl(context)
    }

    @Synchronized
    fun parse(text: String): Node = entry(text).root

    fun snapshot(text: String): MarkdownStructureSnapshot {
        val known = latestSnapshot
        if (known != null && known.text == text) return known
        return synchronized(this) {
            val value = entry(text)
            val snapshot = value.snapshot ?: MarkdownStructureSnapshot(text, value.root).also { value.snapshot = it }
            latestSnapshot = snapshot
            snapshot
        }
    }

    private fun entry(text: String): Parsed {
        versions[text]?.let { return it }
        inlineCache.beginDocument()
        val value = Parsed(parser.parse(text))
        versions[text] = value
        // 保留相邻两个版本，避免不同功能的后台任务交错时反复挤掉同一版解析结果。
        if (versions.size > 2) versions.remove(versions.keys.iterator().next())
        return value
    }
}

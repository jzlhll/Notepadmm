package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.Parser
import org.commonmark.parser.SourceLine
import org.commonmark.parser.SourceLines
import org.jsoup.parser.Tag
import java.util.Locale

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
        private val htmlTag = Regex("^</?([A-Za-z][A-Za-z0-9-]*)(?=[\\s/>])")

        @JvmStatic
        fun createInlineParser(context: InlineParserContext): InlineParser {
            val delegate = MarkdownStrikethroughParser.create(context)
            return InlineParser { lines, parent ->
                parseCjkEmphasis(lines, parent, delegate)
                // CommonMark 将泛型中的 <T> 也解析为标签；在共用 AST 中还原文字，避免预览、复制和选区各自补救。
                parent.accept(object : AbstractVisitor() {
                    override fun visit(node: HtmlInline) {
                        val name = htmlTag.find(node.literal)?.groupValues?.get(1) ?: return
                        if (Tag.isKnownTag(name.lowercase(Locale.ROOT))) return
                        node.insertAfter(Text(node.literal).apply { sourceSpans = node.sourceSpans })
                        node.unlink()
                    }
                })
            }
        }

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

private data class CjkEmphasisCandidate(val line: Int, val column: Int, val input: Int, val punctuation: Char)

private const val CJK_EMPHASIS_PUNCTUATION = "，。！？；：、（）【】《》“”‘’「」『』〔〕〈〉｛｝［］〝〞…—"

/** 仅在普通文字里的中文标点后放宽粗体闭合判断；替换字符等长，AST 源码坐标保持不变。 */
private fun parseCjkEmphasis(lines: SourceLines, parent: Node, delegate: InlineParser) {
    val candidates = ArrayList<CjkEmphasisCandidate>()
    lines.lines.forEachIndexed { lineIndex, line ->
        val span = line.sourceSpan ?: return@forEachIndexed
        val source = line.content.toString()
        for (column in 0 until source.length - 3) {
            val punctuation = source[column]
            if (punctuation !in CJK_EMPHASIS_PUNCTUATION || source[column + 1] != '*' ||
                source[column + 2] != '*' || !source[column + 3].isLetterOrDigit()) continue
            var slash = column - 1
            while (slash >= 0 && source[slash] == '\\') slash--
            if ((column - 1 - slash) % 2 != 0) continue
            candidates.add(CjkEmphasisCandidate(lineIndex, column, span.inputIndex + column, punctuation))
        }
    }
    delegate.parse(lines, parent)
    if (candidates.isEmpty()) return

    // 先用原始 AST 排除代码、HTML 等字面区域，避免改动其中的星号语义。
    val textSpans = ArrayList<SourceSpan>()
    val used = HashSet<Char>()
    lines.content.forEach { used.add(it) }
    parent.accept(object : AbstractVisitor() {
        override fun visit(node: Text) {
            textSpans.addAll(node.sourceSpans)
            node.literal.forEach { used.add(it) }
        }
    })
    val applicable = candidates.filter { candidate -> textSpans.any {
        candidate.input >= it.inputIndex && candidate.input < it.inputIndex + it.length
    } }
    if (applicable.isEmpty()) return

    val markers = LinkedHashMap<Char, Char>()
    var nextMarker = 0xE000
    for (candidate in applicable) {
        if (candidate.punctuation in markers) continue
        while (nextMarker <= 0xF8FF && nextMarker.toChar() in used) nextMarker++
        if (nextMarker > 0xF8FF) return
        markers[candidate.punctuation] = nextMarker.toChar()
        used.add(nextMarker.toChar())
        nextMarker++
    }
    val byLine = applicable.groupBy(CjkEmphasisCandidate::line)
    val adjusted = SourceLines.empty()
    lines.lines.forEachIndexed { index, line ->
        val replacements = byLine[index]
        if (replacements == null) adjusted.addLine(line) else {
            val content = line.content.toString().toCharArray()
            replacements.forEach { content[it.column] = requireNotNull(markers[it.punctuation]) }
            adjusted.addLine(SourceLine.of(String(content), line.sourceSpan))
        }
    }
    var child = parent.firstChild
    while (child != null) {
        val next = child.next
        child.unlink()
        child = next
    }
    delegate.parse(adjusted, parent)
    val originalPunctuation = markers.entries.associate { it.value to it.key }
    parent.accept(object : AbstractVisitor() {
        override fun visit(node: Text) {
            if (node.literal.any { it in originalPunctuation }) {
                node.literal = buildString(node.literal.length) {
                    node.literal.forEach { append(originalPunctuation[it] ?: it) }
                }
            }
        }
    })
}

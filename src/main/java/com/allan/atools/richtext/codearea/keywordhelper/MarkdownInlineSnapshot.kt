package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.Document
import org.commonmark.node.Paragraph
import org.commonmark.node.SourceSpan
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.Parser
import org.commonmark.parser.SourceLine
import org.commonmark.parser.SourceLines

/** 单元格只解析行内语法，所有源码坐标保持与格内编辑文本一致。 */
object MarkdownInlineSnapshot {
    private val context: InlineParserContext = run {
        lateinit var value: InlineParserContext
        // 空文档建立扩展所需的行内上下文，不引入块语法或引用定义。
        Parser.builder().extensions(MarkdownExtensions.all())
            .inlineParserFactory {
                value = it
                MarkdownAstCache.createInlineParser(it)
            }.build().parse("")
        value
    }

    @JvmStatic
    fun create(text: String): MarkdownStructureSnapshot {
        val lines = SourceLines.empty()
        val paragraph = Paragraph()
        var offset = 0
        text.split('\n').forEachIndexed { index, line ->
            val span = SourceSpan.of(index, 0, offset, line.length)
            lines.addLine(SourceLine.of(line, span))
            paragraph.addSourceSpan(span)
            offset += line.length + 1
        }
        MarkdownAstCache.createInlineParser(context).parse(lines, paragraph)
        val root = Document().apply { appendChild(paragraph) }
        return MarkdownStructureSnapshot(text, root)
    }
}

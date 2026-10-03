package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.text.TextContentRenderer

/** 自定义公式保留内容，强调仅去标记，复制与统计使用同一纯文本输出。 */
object MarkdownPlainText {
    @JvmStatic
    fun render(root: Node): String = TextContentRenderer.builder().extensions(MarkdownExtensions.all()).nodeRendererFactory { context ->
        object : NodeRenderer {
            override fun getNodeTypes() = setOf(MarkdownMath::class.java, MarkdownMathBlock::class.java, MarkdownDecoration::class.java, org.commonmark.node.Link::class.java, org.commonmark.node.Image::class.java,
                org.commonmark.node.HtmlInline::class.java, org.commonmark.node.HtmlBlock::class.java,
                org.commonmark.ext.footnotes.FootnoteDefinition::class.java, org.commonmark.ext.footnotes.FootnoteReference::class.java)
            override fun render(node: Node) {
                when (node) {
                    is org.commonmark.node.HtmlInline -> context.writer.write(org.jsoup.Jsoup.parseBodyFragment(node.literal).body().wholeText())
                    is org.commonmark.node.HtmlBlock -> { context.writer.write(org.jsoup.Jsoup.parseBodyFragment(node.literal).body().wholeText()); context.writer.block() }
                    is org.commonmark.ext.footnotes.FootnoteReference -> Unit
                    is MarkdownMath -> context.writer.write(node.literal)
                    is MarkdownMathBlock -> { context.writer.write(node.literal); context.writer.block() }
                    else -> {
                        var child = node.firstChild
                        while (child != null) { context.render(child); child = child.next }
                    }
                }
            }
        }
    }.build().render(root)
}

package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.keywordhelper.*
import org.commonmark.ext.front.matter.YamlFrontMatterBlock
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/** 预览、剪贴板和 HTML 导出共用正文输出及有限 HTML 范围。 */
object MarkdownHtmlRenderer {
    private fun allowed() = Safelist.relaxed().addTags("details", "summary", "u", "sub", "sup", "mark", "input", "section", "nav", "hr", "del", "s")
        .addAttributes(":all", "id", "class", "data-source-start", "data-source-line", "data-display")
        .addAttributes("input", "type", "checked", "disabled").addEnforcedAttribute("input", "type", "checkbox")
        .addAttributes("img", "width", "height").addAttributes("ol", "start").addAttributes("td", "align").addAttributes("th", "align")
        .addProtocols("img", "src", "file", "data").addProtocols("a", "href", "file", "mailto", "#")
        .preserveRelativeLinks(true)

    @JvmStatic
    fun body(state: MarkdownStructureSnapshot, base: String? = null): String {
        val renderer = HtmlRenderer.builder().extensions(MarkdownExtensions.all())
            .escapeHtml(false).sanitizeUrls(true).urlSanitizer(org.commonmark.renderer.html.DefaultUrlSanitizer(listOf("http", "https", "mailto", "file", "data"))).softbreak("<br>\n")
            .attributeProviderFactory {
                org.commonmark.renderer.html.AttributeProvider { node, _, attributes ->
                    node.sourceSpans.firstOrNull()?.let { span ->
                        attributes["data-source-start"] = span.inputIndex.toString()
                        attributes["data-source-line"] = span.lineIndex.toString()
                    }
                    if (node is org.commonmark.node.Heading) state.headings.firstOrNull { it.line == node.sourceSpans.firstOrNull()?.lineIndex }
                        ?.let { attributes["id"] = it.anchor }
                }
            }.nodeRendererFactory { context -> TechnicalRenderer(context, state) }.build()
        val clean = Jsoup.clean(renderer.render(state.root), base ?: "file:///", allowed(),
            org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
        val document = Jsoup.parseBodyFragment(clean)
        // 图片只保留有限的整数尺寸，文档自定义 CSS 不执行。
        for (image in document.select("img[width],img[height]")) {
            val width = image.attr("width")
            if (!width.matches(Regex("[0-9]{1,5}"))) image.removeAttr("width")
            val height = image.attr("height")
            if (!height.matches(Regex("[0-9]{1,5}"))) image.removeAttr("height")
        }
        document.outputSettings().prettyPrint(false)
        return document.body().html()
    }

    private class TechnicalRenderer(private val context: HtmlNodeRendererContext, private val state: MarkdownStructureSnapshot) : NodeRenderer {
        override fun getNodeTypes() = setOf(FencedCodeBlock::class.java, Paragraph::class.java, YamlFrontMatterBlock::class.java, org.commonmark.node.HtmlBlock::class.java)

        override fun render(node: Node) {
            val writer = context.writer
            when (node) {
                is org.commonmark.node.HtmlBlock -> {
                    writer.tag("section", context.extendAttributes(node, "section", mapOf("class" to "md-html-block")))
                    writer.raw(node.literal)
                    writer.tag("/section")
                }
                is YamlFrontMatterBlock -> {
                    val spans = node.sourceSpans
                    if (spans.isNotEmpty()) {
                        writer.tag("details", context.extendAttributes(node, "details", mapOf("class" to "front-matter")))
                        writer.tag("summary"); writer.text("Front Matter"); writer.tag("/summary")
                        writer.tag("pre"); writer.text(state.text.substring(spans.first().inputIndex, spans.last().let { it.inputIndex + it.length })); writer.tag("/pre")
                        writer.tag("/details")
                    }
                }
                is Paragraph -> {
                    val source = node.sourceSpans.joinToString("\n") { state.text.substring(it.inputIndex, it.inputIndex + it.length) }.trim()
                    if (source.equals("[TOC]", true)) {
                        writer.tag("nav", context.extendAttributes(node, "nav", mapOf("class" to "md-toc")))
                        state.headings.forEach { heading ->
                            writer.tag("p", mapOf("class" to "toc-level-${heading.level}"))
                            writer.tag("a", mapOf("href" to "#${heading.anchor}")); writer.text(heading.title); writer.tag("/a"); writer.tag("/p")
                        }
                        writer.tag("/nav")
                    } else {
                        // 保持松紧列表规则：紧列表中的段落不包裹 p。
                        val list = node.parent?.parent
                        val tight = list is org.commonmark.node.ListBlock && list.isTight
                        if (!tight) writer.tag("p", context.extendAttributes(node, "p", emptyMap()))
                        var child = node.firstChild
                        while (child != null) { context.render(child); child = child.next }
                        if (!tight) writer.tag("/p")
                        writer.line()
                    }
                }
                is FencedCodeBlock -> {
                    val language = node.info.trim().substringBefore(' ').lowercase(java.util.Locale.ROOT)
                    val mermaid = language == "mermaid" && Regex("^(?:flowchart|graph|sequenceDiagram)(?:\\s|$)").containsMatchIn(
                        node.literal.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("%%") }.orEmpty())
                    writer.tag("pre", context.extendAttributes(node, "pre", if (mermaid) mapOf("class" to "mermaid-source") else emptyMap()))
                    writer.tag("code", mapOf("class" to "language-$language"))
                    writeCode(node.literal, language)
                    writer.tag("/code"); writer.tag("/pre"); writer.line()
                }
            }
        }

        private fun writeCode(source: String, language: String) {
            val normal = when (language) { "js", "jsx" -> "javascript"; "ts", "tsx" -> "typescript"; "py" -> "python"; "sh", "bash", "zsh" -> "shell"; "yml" -> "yaml"; else -> language }
            val helper = when (normal) {
                "java" -> EditorKeywordHelperImplJava(); "kotlin", "kt" -> EditorKeywordHelperImplKotlin()
                "c", "cpp", "c++" -> EditorKeywordHelperImplCC(); "csharp", "cs" -> EditorKeywordHelperImplCSharp()
                else -> null
            }
            val pattern = helper?.getPattern(null, null) ?: MarkdownCodeLanguages.pattern(normal)
            if (pattern == null) { context.writer.text(source); return }
            val matcher = pattern.matcher(source)
            var end = 0
            while (matcher.find()) {
                context.writer.text(source.substring(end, matcher.start()))
                val style = when { matcher.group("STRING") != null -> "string"; matcher.group("COMMENT") != null -> "comment"; matcher.group("KEYWORD") != null -> "keyword"; else -> "punct" }
                context.writer.tag("span", mapOf("class" to "token-$style"))
                context.writer.text(matcher.group()); context.writer.tag("/span")
                end = matcher.end()
            }
            context.writer.text(source.substring(end))
        }
    }
}

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
        .addAttributes(":all", "id", "class", "data-source-start", "data-source-line", "data-source-text", "data-source-map", "data-display", "data-checked", "role", "aria-label", "title")
        .addAttributes("input", "type", "checked", "disabled").addEnforcedAttribute("input", "type", "checkbox")
        .addAttributes("img", "width", "height").addAttributes("ol", "start").addAttributes("td", "align").addAttributes("th", "align")
        .addAttributes("details", "open")
        .addProtocols("img", "src", "file", "data").addProtocols("a", "href", "file", "mailto", "#")
        .preserveRelativeLinks(true)

    @JvmStatic
    fun body(state: MarkdownStructureSnapshot, base: String? = null, sourceMap: Boolean = false): String {
        val headings = state.headings.associateBy { it.line }
        val renderer = HtmlRenderer.builder().extensions(MarkdownExtensions.all())
            .escapeHtml(false).sanitizeUrls(true).urlSanitizer(org.commonmark.renderer.html.DefaultUrlSanitizer(listOf("http", "https", "mailto", "file", "data"))).softbreak("<br>\n")
            .attributeProviderFactory {
                org.commonmark.renderer.html.AttributeProvider { node, _, attributes ->
                    node.sourceSpans.firstOrNull()?.let { span ->
                        attributes["data-source-start"] = span.inputIndex.toString()
                        attributes["data-source-line"] = span.lineIndex.toString()
                    }
                    if (node is org.commonmark.node.Heading) headings[node.sourceSpans.firstOrNull()?.lineIndex]
                        ?.let { attributes["id"] = it.anchor }
                }
            }.nodeRendererFactory { context -> TechnicalRenderer(context, state, sourceMap) }.build()
        val clean = Jsoup.clean(renderer.render(state.root), base ?: "file:///", allowed(),
            org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
        val document = Jsoup.parseBodyFragment(clean)
        // CommonMark 的任务扩展只输出 input；给所属列表项明确分类，不能同时保留圆点。
        document.select("li > input[type=checkbox], li > p > input[type=checkbox]").forEach { input ->
            input.parents().firstOrNull { it.normalName() == "li" }?.addClass("task-list-item")
        }
        // WebView 对表单控件背景图的绘制不一致；原 input 负责交互，普通元素负责画框和勾号。
        document.select("input[type=checkbox]").forEach { input ->
            input.wrap("<label class=\"md-task-control\"></label>")
            input.after("<span class=\"md-task-icon\" aria-hidden=\"true\"></span>")
        }
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

    private class TechnicalRenderer(private val context: HtmlNodeRendererContext, private val state: MarkdownStructureSnapshot,
                                    private val sourceMap: Boolean) : NodeRenderer {
        override fun getNodeTypes() = setOf(FencedCodeBlock::class.java, Paragraph::class.java, YamlFrontMatterBlock::class.java,
            org.commonmark.node.HtmlBlock::class.java, org.commonmark.node.Text::class.java, org.commonmark.node.Code::class.java)

        override fun render(node: Node) {
            val writer = context.writer
            when (node) {
                is org.commonmark.node.Code -> {
                    if (MarkdownTaskMarkers.isStatus(node.literal)) {
                        writer.tag("span", context.extendAttributes(node, "span", mapOf("class" to "md-task-icon",
                            "data-checked" to (node.literal != "[ ]").toString(), "role" to "img", "aria-label" to node.literal)))
                        writer.tag("span", mapOf("class" to "md-task-source")); writer.text(node.literal); writer.tag("/span")
                        writer.tag("/span")
                    } else {
                        writer.tag("code", context.extendAttributes(node, "code", emptyMap()))
                        writer.text(node.literal); writer.tag("/code")
                    }
                }
                is org.commonmark.node.Text -> {
                    val span = node.sourceSpans.firstOrNull()
                    val mapping = if (sourceMap && span != null) textMap(node) else null
                    if (mapping != null) {
                        val attributes = mutableMapOf("data-source-start" to span!!.inputIndex.toString(), "data-source-text" to "")
                        if (mapping.isNotEmpty()) attributes["data-source-map"] = mapping
                        writer.tag("span", attributes)
                    }
                    writer.text(node.literal)
                    if (mapping != null) writer.tag("/span")
                }
                is org.commonmark.node.HtmlBlock -> {
                    // HTML 容器可能跨越多个 AST 块；包装单个片段会提前关闭 details 等容器。
                    val opening = Regex("^(\\s*<[a-zA-Z][\\w:-]*)(?=[\\s/>])").find(node.literal)
                    val span = node.sourceSpans.firstOrNull()
                    val literal = if (opening != null && span != null) node.literal.replaceRange(opening.range,
                        "${opening.value} data-source-start=\"${span.inputIndex}\" data-source-line=\"${span.lineIndex}\"") else node.literal
                    writer.raw(literal)
                }
                is YamlFrontMatterBlock -> {
                    val spans = node.sourceSpans
                    if (spans.isNotEmpty()) {
                        val metadata = state.frontMatter.firstOrNull { it.firstLine == spans.first().lineIndex }
                        val content = if (metadata?.closed == true) state.text.substring(metadata.body.start, metadata.body.end)
                            else state.text.substring(spans.first().inputIndex, spans.last().let { it.inputIndex + it.length })
                        writer.tag("pre", context.extendAttributes(node, "pre", mapOf("class" to "front-matter")))
                        writer.text(content)
                        writer.tag("/pre")
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
                    val mermaid = MarkdownMermaidSupport.isSupported(node)
                    writer.tag("pre", context.extendAttributes(node, "pre", if (mermaid) mapOf("class" to "mermaid-source") else emptyMap()))
                    writer.tag("code", mapOf("class" to "language-$language"))
                    writeCode(node.literal, language)
                    writer.tag("/code"); writer.tag("/pre"); writer.line()
                }
            }
        }

        /** 转义和实体可能多对一，按 UTF-16 边界映射到原文；无法精确映射时保留块级定位。 */
        private fun textMap(node: org.commonmark.node.Text): String? {
            val raw = StringBuilder()
            val offsets = ArrayList<Int>()
            val begin = node.sourceSpans.first().inputIndex
            for (span in node.sourceSpans) {
                raw.append(state.text, span.inputIndex, span.inputIndex + span.length)
                repeat(span.length) { offsets.add(span.inputIndex + it - begin) }
            }
            if (raw.toString() == node.literal && offsets.withIndex().all { it.index == it.value }) return ""
            val visible = StringBuilder()
            val positions = ArrayList<Int>()
            var index = 0
            while (index < raw.length) {
                val from = index
                val character = raw[index]
                val token = if (character == '\\' && index + 1 < raw.length &&
                    (raw[index + 1] in '!'..'/' || raw[index + 1] in ':'..'@' || raw[index + 1] in '['..'`' || raw[index + 1] in '{'..'~')) {
                    index += 2
                    raw[index - 1].toString()
                } else if (character == '&') {
                    val entity = entityPattern.find(raw, index)?.takeIf { it.range.first == index }
                    val value = entity?.let { org.jsoup.parser.Parser.unescapeEntities(it.value, false) }
                    if (entity != null && value != entity.value) { index += entity.value.length; value!! }
                    else { index++; character.toString() }
                } else { index++; character.toString() }
                visible.append(token)
                repeat(token.length) { positions.add(offsets[from]) }
            }
            if (visible.toString() != node.literal) return null
            positions.add(node.sourceSpans.last().let { it.inputIndex + it.length - begin })
            return positions.joinToString(",")
        }

        private val entityPattern = Regex("&(?:#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]+);")

        private fun writeCode(source: String, language: String) {
            var end = 0
            MarkdownCodeLanguages.forEachToken(source, language, { true }) { token ->
                context.writer.text(source.substring(end, token.start))
                context.writer.tag("span", mapOf("class" to "token-${token.style}"))
                context.writer.text(source.substring(token.start, token.end)); context.writer.tag("/span")
                end = token.end
            }
            context.writer.text(source.substring(end))
        }
    }
}

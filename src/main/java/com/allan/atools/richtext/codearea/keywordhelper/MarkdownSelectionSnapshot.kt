package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.node.*
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import org.commonmark.parser.beta.LinkResult

/** 选区独立排版时补齐实际使用的链接与脚注定义，不修改共享 AST。 */
object MarkdownSelectionSnapshot {
    fun create(document: MarkdownStructureSnapshot, start: Int, end: Int): MarkdownStructureSnapshot {
        val links = DefinitionMap(LinkReferenceDefinition::class.java)
        val notes = DefinitionMap(FootnoteDefinition::class.java)
        val usedLinks = LinkedHashSet<LinkReferenceDefinition>()
        document.elements.forEach { entry ->
            when (val node = entry.node) {
                is LinkReferenceDefinition -> links.putIfAbsent(node.label, node)
                is FootnoteDefinition -> notes.putIfAbsent(node.label, node)
            }
        }
        val parser = Parser.builder().extensions(MarkdownExtensions.all())
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .linkProcessor { info, scanner, _ ->
                val marker = info.marker()?.literal
                if (info.destination() != null || marker != null && marker != "!") null
                else {
                    val label = info.label()?.ifEmpty { info.text() } ?: info.text()
                    val definition = links.get(label)
                    if (definition != null) {
                        usedLinks.add(definition)
                        val node = if (marker == "!") Image(definition.destination, definition.title)
                            else Link(definition.destination, definition.title)
                        val result = LinkResult.wrapTextIn(node, scanner.position())
                        if (marker == "!") result.includeMarker() else result
                    } else if (marker == null && info.text().startsWith('^') && notes.get(info.text().substring(1)) != null) {
                        LinkResult.replaceWith(FootnoteReference(info.text().substring(1)), info.afterTextBracket())
                    } else null
                }
            }.build()
        val source = StringBuilder(document.text.substring(start, end))
        val root = parser.parse(source.toString())
        val included = DefinitionMap(FootnoteDefinition::class.java)
        val includedLinks = DefinitionMap(LinkReferenceDefinition::class.java)
        val pending = ArrayDeque<String>()
        fun visit(node: Node, action: (Node) -> Unit) {
            action(node)
            var child = node.firstChild
            while (child != null) { visit(child, action); child = child.next }
        }
        var sourceLine = source.count { it == '\n' }
        fun appendDefinition(node: Node, raw: String) {
            source.append("\n\n")
            val offset = source.length
            sourceLine += 2
            visit(node) {
                it.sourceSpans = it.sourceSpans.map { span ->
                    SourceSpan.of(span.lineIndex + sourceLine, span.columnIndex, span.inputIndex + offset, span.length)
                }
                if (it is FootnoteReference) pending.add(it.label)
                if (it is LinkReferenceDefinition) includedLinks.putIfAbsent(it.label, it)
            }
            source.append(raw)
            sourceLine += raw.count { it == '\n' }
            node.unlink()
            root.appendChild(node)
        }
        visit(root) {
            if (it is FootnoteReference) pending.add(it.label)
            if (it is FootnoteDefinition) included.putIfAbsent(it.label, it)
            if (it is LinkReferenceDefinition) includedLinks.putIfAbsent(it.label, it)
        }
        while (pending.isNotEmpty()) {
            val label = pending.removeFirst()
            if (included.get(label) != null) continue
            val definition = notes.get(label) ?: continue
            val raw = definition.sourceSpans.joinToString("\n") {
                document.text.substring(it.inputIndex, it.inputIndex + it.length)
            }
            val parsed = parser.parse(raw)
            var copy: FootnoteDefinition? = null
            visit(parsed) { if (copy == null && it is FootnoteDefinition) copy = it }
            val node = copy ?: continue
            included.putIfAbsent(label, node)
            appendDefinition(node, raw)
        }
        // HTML 已解析引用地址，附带的 Markdown 也必须能在其他文档中独立使用。
        for (definition in usedLinks) {
            if (includedLinks.get(definition.label) != null) continue
            val raw = definition.sourceSpans.joinToString("\n") {
                document.text.substring(it.inputIndex, it.inputIndex + it.length)
            }
            if (raw.isEmpty()) continue
            val parsed = parser.parse(raw)
            val copy = parsed.firstChild as? LinkReferenceDefinition ?: continue
            appendDefinition(copy, raw)
        }
        return MarkdownStructureSnapshot(source.toString(), root)
    }
}

package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.node.*
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import org.commonmark.parser.beta.LinkResult

/** 选区独立排版时继承全文定义，仅附带实际引用的脚注，不修改共享 AST。 */
object MarkdownSelectionSnapshot {
    fun create(document: MarkdownStructureSnapshot, start: Int, end: Int): MarkdownStructureSnapshot {
        val links = DefinitionMap(LinkReferenceDefinition::class.java)
        val notes = DefinitionMap(FootnoteDefinition::class.java)
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
        val pending = ArrayDeque<String>()
        fun visit(node: Node, action: (Node) -> Unit) {
            action(node)
            var child = node.firstChild
            while (child != null) { visit(child, action); child = child.next }
        }
        visit(root) {
            if (it is FootnoteReference) pending.add(it.label)
            if (it is FootnoteDefinition) included.putIfAbsent(it.label, it)
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
            source.append("\n\n")
            val offset = source.length
            val lineOffset = source.count { it == '\n' }
            source.append(raw)
            visit(node) {
                it.sourceSpans = it.sourceSpans.map { span ->
                    SourceSpan.of(span.lineIndex + lineOffset, span.columnIndex, span.inputIndex + offset, span.length)
                }
                if (it is FootnoteReference) pending.add(it.label)
            }
            node.unlink()
            root.appendChild(node)
        }
        return MarkdownStructureSnapshot(source.toString(), root)
    }
}

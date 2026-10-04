package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.node.*
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserContext

/** 块结构仍由 CommonMark 全文扫描，未变化的段落复用行内语法；缓存树从不直接交给调用方。 */
class MarkdownInlineCache {
    private data class Key(val type: Class<*>, val content: String, val spans: List<SourceSpan>)
    private var previous = emptyMap<Key, List<Node>>()
    private var current = LinkedHashMap<Key, List<Node>>()
    private var characters = 0

    fun beginDocument() {
        previous = current
        current = LinkedHashMap()
        characters = 0
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    fun create(context: InlineParserContext): InlineParser {
        var definitionRead = false
        val tracked = object : InlineParserContext by context {
            override fun getLinkReferenceDefinition(label: String): LinkReferenceDefinition? {
                definitionRead = true
                return context.getLinkReferenceDefinition(label)
            }

            override fun <D : Any?> getDefinition(type: Class<D>, label: String): D? {
                definitionRead = true
                return context.getDefinition(type, label)
            }
        }
        val delegate = MarkdownAstCache.createInlineParser(tracked)
        return InlineParser { lines, parent ->
            val first = lines.sourceSpans.firstOrNull()
            val content = lines.content
            if (first == null || content.length > 65_536) {
                delegate.parse(lines, parent)
            } else {
                val key = Key(parent.javaClass, content, lines.sourceSpans.map {
                    SourceSpan.of(it.lineIndex - first.lineIndex, it.columnIndex,
                        it.inputIndex - first.inputIndex, it.length)
                })
                val cached = current[key] ?: previous[key]
                if (cached != null) {
                    cached.forEach { parent.appendChild(requireNotNull(copy(it, first.inputIndex, first.lineIndex))) }
                    remember(key, cached)
                } else {
                    definitionRead = false
                    delegate.parse(lines, parent)
                    // 引用链接与脚注受文档其他位置影响，包括未定义的引用；必须重新查询当前上下文。
                    if (!definitionRead && canRemember(key)) {
                        val children = copyChildren(parent, -first.inputIndex, -first.lineIndex)
                        if (children != null) remember(key, children)
                    }
                }
            }
        }
    }

    private fun remember(key: Key, nodes: List<Node>) {
        if (!canRemember(key)) return
        current[key] = nodes
        characters += key.content.length
    }

    private fun canRemember(key: Key) = key !in current && current.size < 2_048 &&
        characters + key.content.length <= 2_097_152

    private fun copyChildren(parent: Node, offset: Int, line: Int): List<Node>? {
        val children = ArrayList<Node>()
        var child = parent.firstChild
        while (child != null) {
            children.add(copy(child, offset, line) ?: return null)
            child = child.next
        }
        return children
    }

    private fun copy(source: Node, offset: Int, line: Int): Node? {
        val node = when (source) {
            is Text -> Text(source.literal)
            is Code -> Code(source.literal)
            is Emphasis -> Emphasis(source.openingDelimiter)
            is StrongEmphasis -> StrongEmphasis(source.openingDelimiter)
            is Strikethrough -> Strikethrough(source.openingDelimiter)
            is Link -> Link(source.destination, source.title)
            is Image -> Image(source.destination, source.title)
            is HtmlInline -> HtmlInline().apply { literal = source.literal }
            is SoftLineBreak -> SoftLineBreak()
            is HardLineBreak -> HardLineBreak()
            is MarkdownMath -> MarkdownMath(source.literal, source.display)
            is MarkdownDecoration -> MarkdownDecoration(source.tag, source.delimiter)
            else -> return null
        }
        node.sourceSpans = source.sourceSpans.map {
            SourceSpan.of(it.lineIndex + line, it.columnIndex, it.inputIndex + offset, it.length)
        }
        val children = copyChildren(source, offset, line) ?: return null
        children.forEach(node::appendChild)
        return node
    }
}

package com.allan.uilibs.richtexts

import com.allan.baseparty.Action
import com.allan.baseparty.utils.ReflectionUtils
import javafx.beans.NamedArg
import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.ChangeListener
import javafx.scene.Node
import javafx.scene.Cursor
import javafx.scene.text.Font
import javafx.scene.text.TextFlow
import javafx.scene.transform.Scale
import javafx.scene.transform.Shear
import org.fxmisc.richtext.StyledTextArea
import org.fxmisc.richtext.TextExt
import org.fxmisc.richtext.model.Codec
import org.fxmisc.richtext.model.EditableStyledDocument
import org.fxmisc.richtext.model.SimpleEditableStyledDocument
import org.fxmisc.richtext.model.TwoDimensional.Bias
import java.lang.reflect.InaccessibleObjectException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.text.BreakIterator
import java.util.function.BiFunction
import java.util.function.IntFunction
import java.util.function.Predicate
import java.util.function.UnaryOperator

/** 提供代码编辑、Markdown 段落与字形样式及图形装饰能力。 */
@JvmSuppressWildcards
abstract class CodeArea private constructor(
    document: EditableStyledDocument<Collection<String>, String, Collection<String>>,
    preserveStyle: Boolean,
    markdownStyleEnabled: Boolean,
    textLeftPadding: DoubleProperty
) : StyledTextArea<@JvmSuppressWildcards Collection<String>, @JvmSuppressWildcards Collection<String>>(
    emptyList(),
    { paragraph, styles -> applyParagraphStyle(paragraph, styles, textLeftPadding) },
    ArrayList<String>(1),
    { text, styles -> if (markdownStyleEnabled) applyMarkdownTextStyle(text, styles) else applyTextStyle(text, styles) },
    document,
    preserveStyle
) {
    companion object {
        private const val MARKDOWN_ITALIC_STYLE = "markdown-italic"
        private const val MARKDOWN_EMOJI_STYLE = "markdown-emoji"
        const val MARKDOWN_EMOJI_GLYPH_PREFIX = "markdown-emoji-glyph:"
        const val MARKDOWN_EMOJI_RENDERED_CLASS = "markdown-emoji-rendered"
        const val MARKDOWN_LIST_BULLET_PREFIX = "markdown-list-bullet:"
        const val MARKDOWN_TASK_MARKER_CLASS = "markdown-task-marker"
        const val MARKDOWN_TASK_PREFIX_CLASS = "markdown-task-prefix"
        const val MARKDOWN_TASK_RENDERED_CLASS = "markdown-task-rendered"
        private val EMOJI_FONT_FAMILY = findEmojiFontFamily()
        /** 段落样式条目转换为内联高度，用于撑高图片等内容所在的段落。 */
        const val PARAGRAPH_PREF_HEIGHT_PREFIX = "pref-height:"
        /** 用段落图形呈现内容时，为原文保留指定高度。 */
        const val PARAGRAPH_PREVIEW_HEIGHT_PREFIX = "preview-height:"
        /** Mermaid 独立维护预览高度，避免与表格、图片的段落样式相互清理。 */
        const val MERMAID_PREVIEW_HEIGHT_PREFIX = "mermaid-preview-height:"
        const val DETAILS_PREVIEW_HEIGHT_PREFIX = "details-preview-height:"
        const val EMBEDDED_PREVIEW_HEIGHT_PREFIX = "embedded-preview-height:"
        /** Mermaid 源码首段的操作栏留白，与预览占高分开维护。 */
        const val MERMAID_SOURCE_HEADER_HEIGHT_PREFIX = "mermaid-source-header-height:"
        /** 表格源码首段的操作栏留白，与正文段落的顶部间距合并。 */
        const val TABLE_SOURCE_HEADER_HEIGHT_PREFIX = "table-source-header-height:"

        private fun applyTextStyle(text: TextExt, styleClasses: Collection<String>) {
            text.styleClass.addAll(styleClasses)
        }

        /**
         * 段落样式应用在 ParagraphText（TextFlow）上。
         * 普通条目追加为样式类，高度条目使用 Region 的首选高度撑高段落。
         */
        private fun applyParagraphStyle(paragraph: TextFlow, styleClasses: Collection<String>?, textLeftPadding: DoubleProperty) {
            // 虚拟段落节点会复用，先移除上一轮的 Markdown 类，避免源码模式残留排版。
            paragraph.styleClass.removeIf { it.startsWith("md-") || it.startsWith("markdown-") }
            var preview = false
            for (style in styleClasses.orEmpty()) {
                val previewHeight = style.startsWith(PARAGRAPH_PREVIEW_HEIGHT_PREFIX)
                    || style.startsWith(MERMAID_PREVIEW_HEIGHT_PREFIX) || style.startsWith(DETAILS_PREVIEW_HEIGHT_PREFIX)
                    || style.startsWith(EMBEDDED_PREVIEW_HEIGHT_PREFIX)
                if (previewHeight || style == "md-front-matter-delimiter-collapsed") preview = true
                if (!previewHeight && !style.startsWith(PARAGRAPH_PREF_HEIGHT_PREFIX)
                    && !style.startsWith(MERMAID_SOURCE_HEADER_HEIGHT_PREFIX)
                    && !style.startsWith(TABLE_SOURCE_HEADER_HEIGHT_PREFIX) && !style.startsWith("md-layout:")
                ) paragraph.styleClass.add(style)
            }
            paragraph.isMouseTransparent = preview
            // 只重排仍被虚拟列表持有的段落，边距变化不写入文档样式或触发全文解析。
            paragraph.styleProperty().unbind()
            paragraph.styleProperty().bind(Bindings.createStringBinding(
                { createParagraphStyle(styleClasses, textLeftPadding.get()) }, textLeftPadding
            ))
        }

        private fun createParagraphStyle(styleClasses: Collection<String>?, textLeftPadding: Double): String {
            var inlineStyle = ""
            var previewHeight: String? = null
            var headerHeight = 0.0
            val blockBackground = styleClasses?.any { it.startsWith("md-code-block-") || it.startsWith("md-front-matter") } == true
            var sourceLeftPadding = textLeftPadding + 10
            for (style in styleClasses.orEmpty()) {
                if (style.startsWith(MERMAID_SOURCE_HEADER_HEIGHT_PREFIX)) {
                    headerHeight = style.substring(MERMAID_SOURCE_HEADER_HEIGHT_PREFIX.length).toDouble()
                } else if (style.startsWith(TABLE_SOURCE_HEADER_HEIGHT_PREFIX)) {
                    headerHeight = style.substring(TABLE_SOURCE_HEADER_HEIGHT_PREFIX.length).toDouble()
                    sourceLeftPadding = textLeftPadding
                }
            }
            var markdownLayout = if (headerHeight > 0) "-fx-padding: $headerHeight 10 0 $sourceLeftPadding;"
                else "-fx-padding:0 ${if (blockBackground) 10 else 0} 0 ${textLeftPadding + if (blockBackground) 10 else 0};"
            for (style in styleClasses.orEmpty()) {
                when {
                    style.startsWith(PARAGRAPH_PREVIEW_HEIGHT_PREFIX) -> previewHeight = style.substring(PARAGRAPH_PREVIEW_HEIGHT_PREFIX.length) + "px"
                    style.startsWith(MERMAID_PREVIEW_HEIGHT_PREFIX) -> previewHeight = style.substring(MERMAID_PREVIEW_HEIGHT_PREFIX.length) + "px"
                    style.startsWith(DETAILS_PREVIEW_HEIGHT_PREFIX) -> previewHeight = style.substring(DETAILS_PREVIEW_HEIGHT_PREFIX.length) + "px"
                    style.startsWith(EMBEDDED_PREVIEW_HEIGHT_PREFIX) -> previewHeight = style.substring(EMBEDDED_PREVIEW_HEIGHT_PREFIX.length) + "px"
                    style.startsWith(PARAGRAPH_PREF_HEIGHT_PREFIX) -> inlineStyle = "-fx-pref-height: " + style.substring(PARAGRAPH_PREF_HEIGHT_PREFIX.length) + "px;"
                    style.startsWith(MERMAID_SOURCE_HEADER_HEIGHT_PREFIX) || style.startsWith(TABLE_SOURCE_HEADER_HEIGHT_PREFIX) -> {
                        // 已提取操作栏高度，须与正文排版合并，避免内联 padding 覆盖顶部留白。
                    }
                    style.startsWith("md-layout:") -> {
                        val fields = style.split(":").dropLastWhile { it.isEmpty() }
                        if (fields.size == 6) {
                            val quote = fields[1].toInt()
                            val list = fields[2].toInt()
                            var left = textLeftPadding + quote * 12 + Math.max(0, list - 1) * 14 + if (fields[5].toBoolean()) 10 else 0
                            if (quote > 0) {
                                // JavaFX 会将最内层引用边框的 inset 与宽度计入内容留白，padding 只补足剩余间距。
                                left -= textLeftPadding + (quote - 1) * 12 + 2
                            } else if (styleClasses!!.contains("md-heading-1") || styleClasses.contains("md-heading-2")
                                || styleClasses.contains("md-thematic-break")
                            ) {
                                // 标题下划线与分隔线的边框已计入正文左侧留白，避免 padding 再叠加一次。
                                left -= textLeftPadding
                            }
                            markdownLayout = "-fx-padding: ${fields[3].toDouble() + headerHeight} 10 ${fields[4]} $left;"
                            if (quote > 0) {
                                val colors = ArrayList<String>()
                                val widths = ArrayList<String>()
                                val insets = ArrayList<String>()
                                for (depth in 0 until quote) {
                                    colors.add("transparent transparent transparent -au-md-quote-border")
                                    widths.add("0 0 0 2")
                                    insets.add("0 0 0 " + (textLeftPadding + depth * 12))
                                }
                                markdownLayout += "-fx-border-color:${colors.joinToString(",")};-fx-border-width:${widths.joinToString(",")};-fx-border-insets:${insets.joinToString(",")};"
                                if (!fields[5].toBoolean()) {
                                    markdownLayout += "-fx-background-color:-au-md-quote-bg;-fx-background-insets:0 0 0 $textLeftPadding;"
                                }
                            }
                        }
                    }
                }
            }
            if (blockBackground) markdownLayout += "-fx-background-insets:0 0 0 $textLeftPadding;"
            if (styleClasses != null && !styleClasses.contains("md-quote-paragraph")
                && (styleClasses.contains("md-heading-1") || styleClasses.contains("md-heading-2") || styleClasses.contains("md-thematic-break"))
            ) markdownLayout += "-fx-border-insets:0 0 0 $textLeftPadding;"
            if (styleClasses?.contains("md-front-matter-delimiter-collapsed") == true) previewHeight = "0px"
            if (previewHeight != null) {
                inlineStyle = "-fx-min-height: $previewHeight;-fx-pref-height: $previewHeight;-fx-max-height: $previewHeight;-fx-pref-width: 0;-fx-opacity: 0;"
            }
            return markdownLayout + inlineStyle
        }

        private fun applyMarkdownTextStyle(text: TextExt, styleClasses: Collection<String>) {
            applyTextStyle(text, styleClasses)
            val searched = "search" in styleClasses || "temporary" in styleClasses
            if (MARKDOWN_TASK_PREFIX_CLASS in styleClasses && !searched) {
                text.text = "\u2060".repeat(text.text.length)
                text.opacity = 0.0
                return
            }
            if (MARKDOWN_TASK_MARKER_CLASS in styleClasses && !searched) {
                val source = text.text
                if (source.length == 3 && source[0] == '[' && source[2] == ']'
                    && (source[1] == ' ' || source[1] == 'x' || source[1] == 'X')
                ) {
                    // 保留三个源码字符的位置，用单个占位字形给矢量复选框留出宽度。
                    text.text = "☐\u202F\u2060"
                    text.style = "-fx-font-family: \"System\";"
                    text.styleClass.add(MARKDOWN_TASK_RENDERED_CLASS)
                    if (source[1] != ' ') text.styleClass.add("markdown-task-checked")
                    text.cursor = if ("markdown-task-example" in styleClasses) Cursor.TEXT else Cursor.HAND
                    text.isPickOnBounds = true
                    return
                }
            }
            var emojiGlyph: String? = null
            var bulletGlyph: String? = null
            for (style in styleClasses) {
                if (style.startsWith(MARKDOWN_EMOJI_GLYPH_PREFIX)) {
                    emojiGlyph = style.substring(MARKDOWN_EMOJI_GLYPH_PREFIX.length)
                    break
                } else if (style.startsWith(MARKDOWN_LIST_BULLET_PREFIX)) {
                    bulletGlyph = style.substring(MARKDOWN_LIST_BULLET_PREFIX.length)
                    break
                }
            }
            if (bulletGlyph != null && !searched) {
                // 无序标记与显示符号均为一个 UTF-16 字符，保留源码坐标与原始列表语法。
                text.text = bulletGlyph
                text.styleClass.add("markdown-list-bullet")
                if (bulletGlyph == "•" || bulletGlyph == "◦") {
                    // 只放大标记字形，保留 TextFlow 的字符宽度、行高与列表缩进。
                    val factor = if (bulletGlyph == "•") 1.5 else 1.65
                    val scale = Scale(factor, factor)
                    scale.pivotXProperty().bind(Bindings.createDoubleBinding(
                        { text.layoutBounds.minX + text.layoutBounds.width / 2 }, text.layoutBoundsProperty()))
                    scale.pivotYProperty().bind(Bindings.createDoubleBinding(
                        { text.layoutBounds.minY + text.layoutBounds.height / 2 }, text.layoutBoundsProperty()))
                    text.transforms.add(scale)
                }
                return
            }
            val collapsed = "markdown-syntax-collapsible" in styleClasses && "markdown-syntax-expanded" !in styleClasses && !searched
            if (collapsed) {
                // 只替换排版节点：等长零宽占位保持 TextFlow 的 UTF-16 命中索引，源码、复制与撤销均不变。
                // WORD JOINER 不引入额外断行机会；保留 Text 节点参与排版，不能设为 unmanaged 丢失字符位置。
                if (emojiGlyph != null && emojiGlyph.length <= text.text.length) {
                    text.text = emojiGlyph + "\u2060".repeat(text.text.length - emojiGlyph.length)
                } else if (MARKDOWN_EMOJI_STYLE !in styleClasses) {
                    text.text = "\u2060".repeat(text.text.length)
                    text.opacity = 0.0
                    return
                }
            }
            // 搜索把短码拆成多个样式片段时保留源码；只有完整短码或直接输入的表情才切换字体。
            if (MARKDOWN_EMOJI_STYLE in styleClasses && ("markdown-syntax-collapsible" !in styleClasses || emojiGlyph != null && collapsed)) {
                if (EMOJI_FONT_FAMILY != null) {
                    text.style = "-fx-font-family: \"$EMOJI_FONT_FAMILY\";"
                    text.styleClass.add(MARKDOWN_EMOJI_RENDERED_CLASS)
                }
                return
            }
            if (MARKDOWN_ITALIC_STYLE in styleClasses) text.transforms.add(Shear(-0.18, 0.0))
        }

        private fun findEmojiFontFamily(): String? {
            val families = Font.getFamilies()
            return arrayOf("Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji").firstOrNull { it in families }
        }
    }

    private val graphicDecorators = LinkedHashMap<Any, BiFunction<Int, Node?, Node?>>()
    private var baseGraphicFactory: IntFunction<out Node?>? = null
    private var composingGraphicFactory = false
    private val graphicFactoryChanged = ChangeListener<IntFunction<out Node?>?> { _, _, now ->
        if (!composingGraphicFactory) {
            baseGraphicFactory = now
            installGraphicFactory()
        }
    }

    init {
        textLeftPadding.bind(Bindings.createDoubleBinding({ padding.right }, paddingProperty()))
        (initialTextStyle as MutableCollection<String>).add("editor-default-label")
        setStyleCodecs(Codec.collectionCodec(Codec.STRING_CODEC), Codec.styledTextCodec(Codec.collectionCodec(Codec.STRING_CODEC)))
        setUseInitialStyleForInsertion(true)
    }

    private constructor(
        @NamedArg("document") document: EditableStyledDocument<Collection<String>, String, Collection<String>>,
        @NamedArg("preserveStyle") preserveStyle: Boolean, markdownStyleEnabled: Boolean
    ) : this(document, preserveStyle, markdownStyleEnabled, SimpleDoubleProperty(85.0))

    private constructor(@NamedArg("text") text: String, beforeInitAction: Action<CodeArea>?, markdownStyleEnabled: Boolean) :
        this(SimpleEditableStyledDocument<Collection<String>, Collection<String>>(emptyList(), emptyList()), false, markdownStyleEnabled) {
        beforeInitAction?.invoke(this)
        appendText(text)
        undoManager.forgetHistory()
        undoManager.mark()
        selectRange(0, 0)
    }

    constructor(@NamedArg("text") text: String) : this(text, null, false)
    protected constructor(@NamedArg("text") text: String, markdownStyleEnabled: Boolean) : this(text, null, markdownStyleEnabled)

    /** 行号、图片与预览共用一个图形入口，支持各自独立释放。 */
    open fun addParagraphGraphicDecorator(owner: Any, decorator: BiFunction<Int, Node?, Node?>) {
        if (graphicDecorators.isEmpty()) {
            baseGraphicFactory = paragraphGraphicFactory
            paragraphGraphicFactoryProperty().addListener(graphicFactoryChanged)
        }
        graphicDecorators[owner] = decorator
        installGraphicFactory()
    }

    open fun removeParagraphGraphicDecorator(owner: Any) {
        if (graphicDecorators.remove(owner) == null) return
        if (graphicDecorators.isEmpty()) paragraphGraphicFactoryProperty().removeListener(graphicFactoryChanged)
        installGraphicFactory()
    }

    private fun installGraphicFactory() {
        composingGraphicFactory = true
        try {
            paragraphGraphicFactory = if (graphicDecorators.isEmpty()) baseGraphicFactory else IntFunction { index ->
                var graphic = baseGraphicFactory?.apply(index)
                for (decorator in graphicDecorators.values) graphic = decorator.apply(index, graphic)
                graphic
            }
        } finally {
            composingGraphicFactory = false
        }
    }

    private var suspendVisibleParsWhile: Method? = null
    private var suspendVisibleParsWhileResolved = false

    open fun suspendVisibleParsWhileInvoke(runnable: Runnable) {
        if (!suspendVisibleParsWhileResolved) {
            suspendVisibleParsWhileResolved = true
            try {
                suspendVisibleParsWhile = ReflectionUtils.iteratorGetPrivateMethod(this, "suspendVisibleParsWhile", Runnable::class.java)
                if (suspendVisibleParsWhile == null) {
                    System.getLogger(CodeArea::class.java.name).log(System.Logger.Level.WARNING,
                        "suspend visible paragraphs method not found; running updates directly")
                }
            } catch (e: InaccessibleObjectException) {
                System.getLogger(CodeArea::class.java.name).log(System.Logger.Level.WARNING,
                    "suspend visible paragraphs method unavailable; running updates directly", e)
            } catch (e: SecurityException) {
                System.getLogger(CodeArea::class.java.name).log(System.Logger.Level.WARNING,
                    "suspend visible paragraphs method unavailable; running updates directly", e)
            }
        }
        // 缺少暂停通知能力时仍执行更新，并缓存查找结果，避免重复反射和日志。
        val method = suspendVisibleParsWhile
        if (method == null) {
            runnable.run()
            return
        }
        try {
            method.invoke(this, runnable)
        } catch (e: IllegalAccessException) {
            suspendVisibleParsWhile = null
            System.getLogger(CodeArea::class.java.name).log(System.Logger.Level.WARNING,
                "suspend visible paragraphs method inaccessible; running updates directly", e)
            runnable.run()
        } catch (e: InvocationTargetException) {
            // 目标方法可能已执行部分更新，传播原始异常，不能再次执行 runnable。
            when (val cause = e.cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw IllegalStateException("suspend visible paragraphs update failed", cause)
            }
        }
    }

    // 选择单词时将下划线包含在同一个词中。
    override fun selectWord() {
        if (length == 0) return
        val csb = caretSelectionBind
        val paragraph = csb.paragraphIndex
        val position = csb.columnPosition
        val paragraphText = getText(paragraph)
        val breakIterator = BreakIterator.getWordInstance(locale)
        breakIterator.setText(paragraphText)
        breakIterator.preceding(position)
        var start = breakIterator.current()
        while (start > 0 && paragraphText[start - 1] == '_') {
            if (--start > 0 && !breakIterator.isBoundary(start - 1)) {
                breakIterator.preceding(start)
                start = breakIterator.current()
            }
        }
        breakIterator.following(position)
        var end = breakIterator.current()
        val len = paragraphText.length
        while (end < len && paragraphText[end] == '_') {
            if (++end < len && !breakIterator.isBoundary(end + 1)) {
                breakIterator.following(end)
                end = breakIterator.current()
            } else if (Character.isDigit(paragraphText[end])) {
                end++
            }
        }
        csb.selectRange(paragraph, start, paragraph, end)
    }

    open fun append(text: String, styleClass: String) = insert(length, text, styleClass)
    open fun insert(position: Int, text: String, styleClass: String) = replace(position, position, text, setOf(styleClass))
    open fun replace(start: Int, end: Int, text: String, styleClass: String) = replace(start, end, text, setOf(styleClass))
    open fun setStyleClass(from: Int, to: Int, styleClass: String) = setStyle(from, to, listOf(styleClass))

    /** 将指定段落折叠到范围内的第一段中。 */
    open fun foldParagraphs(startPar: Int, endPar: Int) = foldParagraphs(startPar, endPar, getAddFoldStyle())
    open fun foldSelectedParagraphs() = foldSelectedParagraphs(getAddFoldStyle())
    open fun foldText(start: Int, end: Int) = fold(start, end, getAddFoldStyle())
    open fun unfoldParagraphs(startingFromPar: Int) = unfoldParagraphs(startingFromPar, getFoldStyleCheck(), getRemoveFoldStyle())
    open fun unfoldText(startingFromPos: Int) = unfoldParagraphs(offsetToPosition(startingFromPos, Bias.Backward).major, getFoldStyleCheck(), getRemoveFoldStyle())

    protected open fun getFoldStyleCheck(): Predicate<Collection<String>?> = Predicate { it?.contains("collapse") == true }
    protected open fun getAddFoldStyle(): UnaryOperator<Collection<String>> = UnaryOperator { ArrayList(it).apply { add("collapse") } }
    protected open fun getRemoveFoldStyle(): UnaryOperator<Collection<String>> = UnaryOperator { ArrayList(it).apply { remove("collapse") } }
}

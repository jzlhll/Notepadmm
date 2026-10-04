package com.allan.uilibs.richtexts;

import com.allan.baseparty.Action;
import com.allan.baseparty.utils.ReflectionUtils;
import javafx.beans.NamedArg;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Cursor;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Shear;
import org.fxmisc.richtext.CaretSelectionBind;
import org.fxmisc.richtext.StyledTextArea;
import org.fxmisc.richtext.TextExt;
import org.fxmisc.richtext.model.Codec;
import org.fxmisc.richtext.model.EditableStyledDocument;
import org.fxmisc.richtext.model.SimpleEditableStyledDocument;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public abstract class CodeArea extends StyledTextArea<Collection<String>, Collection<String>> {
    private static final String MARKDOWN_ITALIC_STYLE = "markdown-italic";
    private static final String MARKDOWN_BOLD_STYLE = "markdown-bold";
    private static final String MARKDOWN_EMOJI_STYLE = "markdown-emoji";
    public static final String MARKDOWN_EMOJI_GLYPH_PREFIX = "markdown-emoji-glyph:";
    public static final String MARKDOWN_EMOJI_RENDERED_CLASS = "markdown-emoji-rendered";
    public static final String MARKDOWN_LIST_BULLET_PREFIX = "markdown-list-bullet:";
    public static final String MARKDOWN_TASK_MARKER_CLASS = "markdown-task-marker";
    public static final String MARKDOWN_TASK_PREFIX_CLASS = "markdown-task-prefix";
    public static final String MARKDOWN_TASK_RENDERED_CLASS = "markdown-task-rendered";
    private static final String EMOJI_FONT_FAMILY = findEmojiFontFamily();
    /** 段落样式特殊条目前缀：pref-height:240 会转为 ParagraphText 的 -fx-pref-height 内联样式（用于撑高段落，如 markdown 行内图片） */
    public static final String PARAGRAPH_PREF_HEIGHT_PREFIX = "pref-height:";
    /** 用段落图形呈现内容时，为原文保留指定高度。 */
    public static final String PARAGRAPH_PREVIEW_HEIGHT_PREFIX = "preview-height:";
    /** Mermaid 独立维护预览高度，避免与表格、图片的段落样式相互清理。 */
    public static final String MERMAID_PREVIEW_HEIGHT_PREFIX = "mermaid-preview-height:";
    public static final String DETAILS_PREVIEW_HEIGHT_PREFIX = "details-preview-height:";
    /** Mermaid 源码首段的操作栏留白，与预览占高分开维护。 */
    public static final String MERMAID_SOURCE_HEADER_HEIGHT_PREFIX = "mermaid-source-header-height:";

    private final LinkedHashMap<Object, BiFunction<Integer, Node, Node>> graphicDecorators = new LinkedHashMap<>();
    private IntFunction<? extends Node> baseGraphicFactory;
    private boolean composingGraphicFactory;
    private final ChangeListener<IntFunction<? extends Node>> graphicFactoryChanged = (obs, old, now) -> {
        if (!composingGraphicFactory) {
            baseGraphicFactory = now;
            installGraphicFactory();
        }
    };

    /** 行号、图片与预览共用一个图形入口，支持各自独立释放。 */
    public void addParagraphGraphicDecorator(Object owner, BiFunction<Integer, Node, Node> decorator) {
        if (graphicDecorators.isEmpty()) {
            baseGraphicFactory = getParagraphGraphicFactory();
            paragraphGraphicFactoryProperty().addListener(graphicFactoryChanged);
        }
        graphicDecorators.put(owner, decorator);
        installGraphicFactory();
    }

    public void removeParagraphGraphicDecorator(Object owner) {
        if (graphicDecorators.remove(owner) == null) {
            return;
        }
        if (graphicDecorators.isEmpty()) {
            paragraphGraphicFactoryProperty().removeListener(graphicFactoryChanged);
        }
        installGraphicFactory();
    }

    private void installGraphicFactory() {
        composingGraphicFactory = true;
        try {
            setParagraphGraphicFactory(graphicDecorators.isEmpty() ? baseGraphicFactory : index -> {
                Node graphic = baseGraphicFactory == null ? null : baseGraphicFactory.apply(index);
                for (var decorator : graphicDecorators.values()) {
                    graphic = decorator.apply(index, graphic);
                }
                return graphic;
            });
        } finally {
            composingGraphicFactory = false;
        }
    }

    private CodeArea(@NamedArg("document") EditableStyledDocument<Collection<String>, String, Collection<String>> document,
                     @NamedArg("preserveStyle") boolean preserveStyle,
                     boolean markdownStyleEnabled) {
        super(Collections.<String>emptyList(),
                CodeArea::applyParagraphStyle,
                new ArrayList<String>(1),
                markdownStyleEnabled ? CodeArea::applyMarkdownTextStyle : CodeArea::applyTextStyle,
                document,
                preserveStyle
        );

        getInitialTextStyle().add("editor-default-label");

        setStyleCodecs(
                Codec.collectionCodec(Codec.STRING_CODEC),
                Codec.styledTextCodec(Codec.collectionCodec(Codec.STRING_CODEC))
        );

        setUseInitialStyleForInsertion(true);
    }

    private static void applyTextStyle(TextExt text, Collection<String> styleClasses) {
        text.getStyleClass().addAll(styleClasses);
    }

    /**
     * 段落样式应用在 ParagraphText（TextFlow）上：
     * 普通条目作为样式类追加；pref-height: 开头的条目转为 -fx-pref-height 内联样式，
     * 利用 Region.prefHeight(double) 优先返回该属性的机制撑高行高（ParagraphBox.computePrefHeight 只算文本高度）。
     */
    private static void applyParagraphStyle(javafx.scene.text.TextFlow paragraph, Collection<String> styleClasses) {
        // 虚拟段落节点会复用，先移除上一轮的 Markdown 类，避免源码模式残留排版。
        paragraph.getStyleClass().removeIf(style -> style.startsWith("md-") || style.startsWith("markdown-"));
        String inlineStyle = "";
        String previewHeight = null;
        double headerHeight = 0;
        if (styleClasses != null) for (String style : styleClasses) {
            if (style.startsWith(MERMAID_SOURCE_HEADER_HEIGHT_PREFIX)) {
                headerHeight = Double.parseDouble(style.substring(MERMAID_SOURCE_HEADER_HEIGHT_PREFIX.length()));
            }
        }
        String markdownLayout = headerHeight > 0 ? "-fx-padding: " + headerHeight + " 10 0 95;" : "";
        for (String style : styleClasses == null ? Collections.<String>emptyList() : styleClasses) {
            if (style.startsWith(PARAGRAPH_PREVIEW_HEIGHT_PREFIX)) {
                previewHeight = style.substring(PARAGRAPH_PREVIEW_HEIGHT_PREFIX.length()) + "px";
            } else if (style.startsWith(MERMAID_PREVIEW_HEIGHT_PREFIX)) {
                previewHeight = style.substring(MERMAID_PREVIEW_HEIGHT_PREFIX.length()) + "px";
            } else if (style.startsWith(DETAILS_PREVIEW_HEIGHT_PREFIX)) {
                previewHeight = style.substring(DETAILS_PREVIEW_HEIGHT_PREFIX.length()) + "px";
            } else if (style.startsWith(PARAGRAPH_PREF_HEIGHT_PREFIX)) {
                inlineStyle = "-fx-pref-height: "
                        + style.substring(PARAGRAPH_PREF_HEIGHT_PREFIX.length()) + "px;";
            } else if (style.startsWith(MERMAID_SOURCE_HEADER_HEIGHT_PREFIX)) {
                // 已提取操作栏高度，须与正文排版合并，避免内联 padding 覆盖顶部留白。
            } else if (style.startsWith("md-layout:")) {
                var fields = style.split(":");
                if (fields.length == 6) {
                    int quote = Integer.parseInt(fields[1]);
                    int list = Integer.parseInt(fields[2]);
                    int left = 85 + quote * 12 + Math.max(0, list - 1) * 14 + (Boolean.parseBoolean(fields[5]) ? 10 : 0);
                    if (quote > 0) {
                        // JavaFX 会将最内层引用边框的 inset 与宽度计入内容留白，padding 只补足剩余间距。
                        left -= 85 + (quote - 1) * 12 + 2;
                    } else if (styleClasses.contains("md-heading-1") || styleClasses.contains("md-heading-2")
                            || styleClasses.contains("md-thematic-break")) {
                        // 标题下划线与分隔线的边框已计入正文左侧留白，避免 padding 再叠加一次。
                        left -= 85;
                    }
                    markdownLayout = "-fx-padding: " + (Double.parseDouble(fields[3]) + headerHeight)
                            + " 10 " + fields[4] + " " + left + ";";
                    if (quote > 0) {
                        var colors = new java.util.ArrayList<String>();
                        var widths = new java.util.ArrayList<String>();
                        var insets = new java.util.ArrayList<String>();
                        for (int depth = 0; depth < quote; depth++) {
                            colors.add("transparent transparent transparent -au-md-quote-border");
                            widths.add("0 0 0 2");
                            insets.add("0 0 0 " + (85 + depth * 12));
                        }
                        markdownLayout += "-fx-border-color:" + String.join(",", colors)
                                + ";-fx-border-width:" + String.join(",", widths)
                                + ";-fx-border-insets:" + String.join(",", insets) + ";";
                        if (!Boolean.parseBoolean(fields[5])) {
                            markdownLayout += "-fx-background-color:-au-md-quote-bg;-fx-background-insets:0 0 0 85;";
                        }
                    }
                }
            } else {
                paragraph.getStyleClass().add(style);
            }
        }
        if (styleClasses != null && styleClasses.contains("md-front-matter-delimiter-collapsed")) previewHeight = "0px";
        if (previewHeight != null) {
            inlineStyle = "-fx-min-height: " + previewHeight + ";-fx-pref-height: " + previewHeight
                    + ";-fx-max-height: " + previewHeight + ";-fx-pref-width: 0;-fx-opacity: 0;";
        }
        paragraph.setStyle(markdownLayout + inlineStyle);
        paragraph.setMouseTransparent(previewHeight != null);
    }

    private static void applyMarkdownTextStyle(TextExt text, Collection<String> styleClasses) {
        applyTextStyle(text, styleClasses);
        boolean searched = styleClasses.contains("search") || styleClasses.contains("temporary");
        if (styleClasses.contains(MARKDOWN_TASK_PREFIX_CLASS) && !searched) {
            text.setText("\u2060".repeat(text.getText().length()));
            text.setOpacity(0);
            return;
        }
        if (styleClasses.contains(MARKDOWN_TASK_MARKER_CLASS) && !searched) {
            String source = text.getText();
            if (source.length() == 3 && source.charAt(0) == '[' && source.charAt(2) == ']'
                    && (source.charAt(1) == ' ' || source.charAt(1) == 'x' || source.charAt(1) == 'X')) {
                // 保留三个源码字符的位置，用单个占位字形给矢量复选框留出宽度。
                text.setText("☐\u202F\u2060");
                text.setStyle("-fx-font-family: \"System\";");
                text.getStyleClass().add(MARKDOWN_TASK_RENDERED_CLASS);
                if (source.charAt(1) != ' ') text.getStyleClass().add("markdown-task-checked");
                text.setCursor(Cursor.HAND);
                text.setPickOnBounds(true);
                return;
            }
        }
        String emojiGlyph = null;
        String bulletGlyph = null;
        for (String style : styleClasses) {
            if (style.startsWith(MARKDOWN_EMOJI_GLYPH_PREFIX)) {
                emojiGlyph = style.substring(MARKDOWN_EMOJI_GLYPH_PREFIX.length());
                break;
            } else if (style.startsWith(MARKDOWN_LIST_BULLET_PREFIX)) {
                bulletGlyph = style.substring(MARKDOWN_LIST_BULLET_PREFIX.length());
                break;
            }
        }
        if (bulletGlyph != null && !styleClasses.contains("search") && !styleClasses.contains("temporary")) {
            // 无序标记与显示符号均为一个 UTF-16 字符，保留源码坐标与原始列表语法。
            text.setText(bulletGlyph);
            text.getStyleClass().add("markdown-list-bullet");
            if (bulletGlyph.equals("•") || bulletGlyph.equals("◦")) {
                // 只放大标记字形，保留 TextFlow 的字符宽度、行高与列表缩进。
                double factor = bulletGlyph.equals("•") ? 1.5 : 1.65;
                var scale = new Scale(factor, factor);
                scale.pivotXProperty().bind(Bindings.createDoubleBinding(
                        () -> text.getLayoutBounds().getMinX() + text.getLayoutBounds().getWidth() / 2,
                        text.layoutBoundsProperty()));
                scale.pivotYProperty().bind(Bindings.createDoubleBinding(
                        () -> text.getLayoutBounds().getMinY() + text.getLayoutBounds().getHeight() / 2,
                        text.layoutBoundsProperty()));
                text.getTransforms().add(scale);
            }
            return;
        }
        boolean collapsed = styleClasses.contains("markdown-syntax-collapsible")
                && !styleClasses.contains("markdown-syntax-expanded")
                && !styleClasses.contains("search") && !styleClasses.contains("temporary");
        if (collapsed) {
            // 只替换排版节点：等长零宽占位保持 TextFlow 的 UTF-16 命中索引，源码、复制与撤销均不变。
            // WORD JOINER 不引入额外断行机会；保留 Text 节点参与排版，不能设为 unmanaged 丢失字符位置。
            if (emojiGlyph != null && emojiGlyph.length() <= text.getText().length()) {
                text.setText(emojiGlyph + "\u2060".repeat(text.getText().length() - emojiGlyph.length()));
            } else if (!styleClasses.contains(MARKDOWN_EMOJI_STYLE)) {
                text.setText("\u2060".repeat(text.getText().length()));
                text.setOpacity(0);
                return;
            }
        }
        // 搜索把短码拆成多个样式片段时保留源码；只有完整短码或直接输入的表情才切换字体。
        if (styleClasses.contains(MARKDOWN_EMOJI_STYLE)
                && (!styleClasses.contains("markdown-syntax-collapsible") || emojiGlyph != null && collapsed)) {
            if (EMOJI_FONT_FAMILY != null) {
                text.setStyle("-fx-font-family: \"" + EMOJI_FONT_FAMILY + "\";");
                text.getStyleClass().add(MARKDOWN_EMOJI_RENDERED_CLASS);
            }
            return;
        }
        if (styleClasses.contains(MARKDOWN_ITALIC_STYLE)) {
            text.getTransforms().add(new Shear(-0.18, 0));
        }
        if (styleClasses.contains(MARKDOWN_BOLD_STYLE)
                || styleClasses.stream().anyMatch(style -> style.startsWith("markdown-title-"))) {
            // 自加载字体无 bold 变体，标题与强调共用按字号缩放的描边模拟粗体。
            text.setStrokeType(StrokeType.CENTERED);
            text.strokeProperty().bind(text.fillProperty());
            text.strokeWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> {
                        var font = text.getFont();
                        return (font == null ? 13.0 : font.getSize()) * 0.04;
                    },
                    text.fontProperty()));
        }
    }

    private static String findEmojiFontFamily() {
        var families = Font.getFamilies();
        for (var family : new String[]{"Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji"}) {
            if (families.contains(family)) {
                return family;
            }
        }
        return null;
    }

    private Method suspendVisibleParsWhile;
    //不同跟老的同名
    public void suspendVisibleParsWhileInvoke(Runnable runnable) {
        if (suspendVisibleParsWhile == null) {
            suspendVisibleParsWhile = ReflectionUtils.iteratorGetPrivateMethod(this, "suspendVisibleParsWhile", Runnable.class);
        }

        if (suspendVisibleParsWhile != null) {
            try {
                suspendVisibleParsWhile.invoke(this, runnable);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates a text area with initial text com.base.content.
     * Initial caret position is set at the beginning of text com.base.content.
     *
     * @param text Initial text com.base.content.
     */
    private CodeArea(@NamedArg("text") String text, Action<CodeArea> beforeInitAction,
                     boolean markdownStyleEnabled) {
        this(new SimpleEditableStyledDocument<>(
                Collections.<String>emptyList(), Collections.<String>emptyList()
        ), false, markdownStyleEnabled);
        if (beforeInitAction != null) {
            beforeInitAction.invoke(this);
        }

        appendText(text);
        getUndoManager().forgetHistory();
        getUndoManager().mark();

        // position the caret at the beginning
        selectRange(0, 0);
    }

    public CodeArea(@NamedArg("text") String text) {
        this(text, null, false);
    }

    protected CodeArea(@NamedArg("text") String text, boolean markdownStyleEnabled) {
        this(text, null, markdownStyleEnabled);
    }

    @Override // to select words containing underscores
    public void selectWord()
    {
        if ( getLength() == 0 ) return;

        CaretSelectionBind<?,?,?> csb = getCaretSelectionBind();
        int paragraph = csb.getParagraphIndex();
        int position = csb.getColumnPosition();

        String paragraphText = getText( paragraph );
        BreakIterator breakIterator = BreakIterator.getWordInstance( getLocale() );
        breakIterator.setText( paragraphText );

        breakIterator.preceding( position );
        int start = breakIterator.current();

        while ( start > 0 && paragraphText.charAt( start-1 ) == '_' )
        {
            if ( --start > 0 && ! breakIterator.isBoundary( start-1 ) )
            {
                breakIterator.preceding( start );
                start = breakIterator.current();
            }
        }

        breakIterator.following( position );
        int end = breakIterator.current();
        int len = paragraphText.length();

        while ( end < len && paragraphText.charAt( end ) == '_' )
        {
            if ( ++end < len && ! breakIterator.isBoundary( end+1 ) )
            {
                breakIterator.following( end );
                end = breakIterator.current();
            }
            // For some reason single digits aren't picked up so ....
            else if ( Character.isDigit( paragraphText.charAt( end ) ) )
            {
                end++;
            }
        }

        csb.selectRange( paragraph, start, paragraph, end );
    }


    /**
     * Convenient method to append text together with a single style class.
     */
    public void append( String text, String styleClass ) {
        insert( getLength(), text, styleClass );
    }

    /**
     * Convenient method to insert text together with a single style class.
     */
    public void insert( int position, String text, String styleClass ) {
        replace( position, position, text, Collections.singleton( styleClass ) );
    }

    /**
     * Convenient method to replace text together with a single style class.
     */
    public void replace( int start, int end, String text, String styleClass ) {
        replace( start, end, text, Collections.singleton( styleClass ) );
    }

    /**
     * Convenient method to assign a single style class.
     */
    public void setStyleClass( int from, int to, String styleClass ) {
        setStyle( from, to, Collections.singletonList( styleClass ) );
    }


    /**
     * Folds (hides/collapses) paragraphs from <code>startPar</code> to <code>
     * endPar</code>, "into" (i.e. excluding) the first paragraph of the range.
     */
    public void foldParagraphs( int startPar, int endPar ) {
        foldParagraphs( startPar, endPar, getAddFoldStyle() );
    }

    /**
     * Folds (hides/collapses) the currently selected paragraphs,
     * "into" (i.e. excluding) the first paragraph of the range.
     */
    public void foldSelectedParagraphs() {
        foldSelectedParagraphs( getAddFoldStyle() );
    }

    /**
     * Folds (hides/collapses) paragraphs from character position <code>start</code>
     * to <code>end</code>, "into" (i.e. excluding) the first paragraph of the range.
     */
    public void foldText( int start, int end ) {
        fold( start, end, getAddFoldStyle() );
    }

    /**
     * Unfolds paragraphs <code>startingFrom</code> onwards for the currently folded block.
     */
    public void unfoldParagraphs( int startingFromPar ) {
        unfoldParagraphs( startingFromPar, getFoldStyleCheck(), getRemoveFoldStyle() );
    }

    /**
     * Unfolds text <code>startingFromPos</code> onwards for the currently folded block.
     */
    public void unfoldText( int startingFromPos ) {
        startingFromPos = offsetToPosition( startingFromPos, Bias.Backward ).getMajor();
        unfoldParagraphs( startingFromPos, getFoldStyleCheck(), getRemoveFoldStyle() );
    }


    /**
     * @return a Predicate that given a paragraph style, returns true if it includes folding.
     */
    protected Predicate<Collection<String>> getFoldStyleCheck() {
        return styleList -> styleList != null && styleList.contains( "collapse" );
    }

    /**
     * @return a UnaryOperator that given a paragraph style, returns a style that includes fold styling.
     */
    protected UnaryOperator<Collection<String>> getAddFoldStyle() {
        return styleList -> {
            styleList = new ArrayList<>( styleList );
            // "collapse" is in styled-text-area.css:
            // .collapse { visibility: false; }
            styleList.add( "collapse" );
            return styleList;
        };
    }

    /**
     * @return a UnaryOperator that given a paragraph style, returns a style that excludes fold styling.
     */
    protected UnaryOperator<Collection<String>> getRemoveFoldStyle() {
        return styleList -> {
            styleList = new ArrayList<>( styleList );
            styleList.remove( "collapse" );
            return styleList;
        };
    }
}

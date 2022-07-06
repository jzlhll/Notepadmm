package com.allan.atools.richtext;

import javafx.scene.Node;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.StyledTextArea;
import org.fxmisc.richtext.TextExt;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledSegment;
import org.fxmisc.richtext.model.TextOps;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public abstract class FoldableTextArea extends GenericStyledArea<ParStyle, String, TextStyle>{
    private final static TextOps<String, TextStyle> styledTextOps = SegmentOps.styledTextOps();

    public FoldableTextArea()
    {
        this(null);
    }

    public FoldableTextArea(String name)
    {
        super(
                ParStyle.EMPTY,                                                 // default paragraph style
                (paragraph, style) -> paragraph.setStyle(style.toCss()),        // paragraph style setter
                TextStyle.EMPTY,
//                TextStyle.EMPTY
//                        .updateFontSize(name == null ? 14 : GlobalProfs.getFontSizeProperty().get())
//                        .updateFontFamily(FontTheme.fontFamily())
//                        .updateTextColor(Color.BLACK),  // default segment style
                styledTextOps,                            // segment operations
                seg -> createNode(seg, (text, style) -> text.setStyle(style.toCss())));                     // Node creator and segment style setter
    }

    private static Node createNode(StyledSegment<String, TextStyle> seg,
                                   BiConsumer<? super TextExt, TextStyle> applyStyle) {
        return StyledTextArea.createStyledTextNode(seg.getSegment(), seg.getStyle(), applyStyle);
    }

    public void foldParagraphs( int startPar, int endPar ) {
        foldParagraphs( startPar, endPar, getAddFoldStyle() );
    }

    public void foldSelectedParagraphs() {
        foldSelectedParagraphs( getAddFoldStyle() );
    }

    public void foldText( int start, int end ) {
        fold( start, end, getAddFoldStyle() );
    }

    public void unfoldParagraphs( int startingFromPar ) {
        unfoldParagraphs( startingFromPar, getFoldStyleCheck(), getRemoveFoldStyle() );
    }

    public void unfoldText( int startingFromPos ) {
        startingFromPos = offsetToPosition( startingFromPos, Bias.Backward ).getMajor();
        unfoldParagraphs( startingFromPos, getFoldStyleCheck(), getRemoveFoldStyle() );
    }

    protected UnaryOperator<ParStyle> getAddFoldStyle() {
        return pstyle -> pstyle.updateFold( true );
    }

    protected UnaryOperator<ParStyle> getRemoveFoldStyle() {
        return pstyle -> pstyle.updateFold( false );
    }

    protected Predicate<ParStyle> getFoldStyleCheck() {
        return pstyle -> pstyle.isFolded();
    }
}

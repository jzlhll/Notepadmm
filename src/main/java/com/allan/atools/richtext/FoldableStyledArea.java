package com.allan.atools.richtext;

import com.allan.atools.UIContext;
import com.allan.atools.FontTheme;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.StyledTextArea;
import org.fxmisc.richtext.TextExt;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledSegment;
import org.fxmisc.richtext.model.TextOps;
import org.reactfx.util.Either;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class FoldableStyledArea extends GenericStyledArea<ParStyle, Either<String, LinkedImage>, TextStyle>{
    private final static TextOps<String, TextStyle> styledTextOps = SegmentOps.styledTextOps();
    private final static LinkedImageOps<TextStyle> linkedImageOps = new LinkedImageOps<>();

    public FoldableStyledArea()
    {
        this(null);
    }

    public FoldableStyledArea(String name)
    {
        super(
                ParStyle.EMPTY,                                                 // default paragraph style
                (paragraph, style) -> paragraph.setStyle(style.toCss()),        // paragraph style setter
                TextStyle.EMPTY
                        .updateFontSize(
                                name == null ? 12 :
                                (UIContext.sharedPref.getInt(name + "FoldableStyledAreaFontSize", 16) - 4)
                        )
                        .updateFontFamily(FontTheme.fontFamily())
                        .updateTextColor(Color.BLACK),  // default segment style
                styledTextOps._or(linkedImageOps, (s1, s2) -> Optional.empty()),                            // segment operations
                seg -> createNode(seg, (text, style) -> text.setStyle(style.toCss())));                     // Node creator and segment style setter

        //getStyleClass().add("custom-styled-text-area");
    }

    private static Node createNode(StyledSegment<Either<String, LinkedImage>, TextStyle> seg,
                                   BiConsumer<? super TextExt, TextStyle> applyStyle ) {
        return seg.getSegment().unify(
                text -> StyledTextArea.createStyledTextNode(text, seg.getStyle(), applyStyle),
                LinkedImage::createNode
        );
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

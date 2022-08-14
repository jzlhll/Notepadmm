package com.allan.uilibs.richtexts;

import com.allan.baseparty.Action;
import com.allan.baseparty.utils.ReflectionUtils;
import javafx.beans.NamedArg;
import org.fxmisc.richtext.CaretSelectionBind;
import org.fxmisc.richtext.StyledTextArea;
import org.fxmisc.richtext.model.Codec;
import org.fxmisc.richtext.model.EditableStyledDocument;
import org.fxmisc.richtext.model.SimpleEditableStyledDocument;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public abstract class CodeArea extends StyledTextArea<Collection<String>, Collection<String>> {
    private CodeArea(@NamedArg("document") EditableStyledDocument<Collection<String>, String, Collection<String>> document,
                     @NamedArg("preserveStyle") boolean preserveStyle) {
        super(Collections.<String>emptyList(),
                (paragraph, styleClasses) -> paragraph.getStyleClass().addAll(styleClasses),
                new ArrayList<String>(1),
                (text, styleClasses) -> text.getStyleClass().addAll(styleClasses),
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
    public CodeArea(@NamedArg("text") String text, Action<CodeArea> beforeInitAction) {
        this(new SimpleEditableStyledDocument<>(
                Collections.<String>emptyList(), Collections.<String>emptyList()
        ), false);
        if (beforeInitAction != null) {
            beforeInitAction.invoke(this);
        }

        appendText(text);
        getUndoManager().forgetHistory();
        getUndoManager().mark();

        // position the caret at the beginning
        selectRange(0, 0);
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

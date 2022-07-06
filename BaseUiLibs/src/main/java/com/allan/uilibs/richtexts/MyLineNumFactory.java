package com.allan.uilibs.richtexts;

import com.allan.baseparty.ActionR;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import org.fxmisc.richtext.GenericStyledArea;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class MyLineNumFactory<PS> implements IntFunction<Node> {
    public static ActionR<Integer, String> offerFontFamily = (id) -> "styled-text-area";

    private static final Insets DEFAULT_INSETS = new Insets(0.0, 5.0, 0.0, 5.0);
    private static final Paint DEFAULT_TEXT_FILL = Color.web("#666");
    private static final ActionR<String, Font> FontSupply = (s) -> Font.font(s, FontPosture.ITALIC, 13);
    //private static final Font DEFAULT_FOLD_FONT = Font.font("monospace", FontWeight.BOLD, 13);
    private static final Background DEFAULT_BACKGROUND = new Background(new BackgroundFill(Color.web("#ddd"), null, null));

    public static IntFunction<Node> get(GenericStyledArea<?, ?, ?> area) {
        return get(area, digits -> "%1$" + digits + "s");
    }

    private static <PS> IntFunction<Node> get( GenericStyledArea<PS, ?, ?> area, IntFunction<String> format )
    {
        if (area instanceof MyCodeArea classArea) {
            return get( classArea, format, classArea.getFoldStyleCheck(), classArea.getRemoveFoldStyle() );
        }

        return get( area, format, null, null );
    }

    /**
     * Use this if you extended GenericStyledArea for your own text area and you're using paragraph folding.
     *
     * @param <PS> The paragraph style type being used by the text area
     * @param format Given an int convert to a String for the line number.
     * @param isFolded Given a paragraph style PS check if it's folded.
     * @param removeFoldStyle Given a paragraph style PS, return a <b>new</b> PS that excludes fold styling.
     */
    private static <PS> IntFunction<Node> get(
            GenericStyledArea<PS, ?, ?> area,
            IntFunction<String> format,
            Predicate<PS> isFolded,
            UnaryOperator<PS> removeFoldStyle )
    {
        return new MyLineNumFactory<>(area, format, isFolded, removeFoldStyle );
    }

    private final Val<Integer> nParagraphs;
    private final IntFunction<String> format;

    private MyLineNumFactory(
            GenericStyledArea<PS, ?, ?> area,
            IntFunction<String> format,
            Predicate<PS> isFolded,
            UnaryOperator<PS> removeFoldStyle )
    {
        nParagraphs = LiveList.sizeOf(area.getParagraphs());
        this.format = format;
    }

    @Override
    public Node apply(int idx) {
        Val<String> formatted = nParagraphs.map(n -> format(idx, n)); //idx + 1

        Label lineNo = new Label();
        lineNo.setFont(FontSupply.invoke(offerFontFamily.invoke(idx)));
        lineNo.setBackground(DEFAULT_BACKGROUND);
        lineNo.setTextFill(DEFAULT_TEXT_FILL);
        lineNo.setPadding(DEFAULT_INSETS);
        lineNo.setAlignment(Pos.TOP_RIGHT);
        lineNo.getStyleClass().add("lineno");

        // bind label's text to a Val that stops observing area's paragraphs
        // when lineNo is removed from scene
        lineNo.textProperty().bind(formatted.conditionOnShowing(lineNo));

        return lineNo;
    }

    private String format(int x, int max) {
        int digits = (int) Math.floor(Math.log10(max)) + 1;
        return String.format(format.apply(digits), x);
    }
}
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

import java.util.function.IntFunction;

public final class MyNoneLineNumFactory implements IntFunction<Node> {
    public static ActionR<Integer, String> offerFontFamily = (id) -> "styled-text-area";
    private static final Insets DEFAULT_INSETS = new Insets(0.0, 5.0, 0.0, 5.0);
    private static final Paint DEFAULT_TEXT_FILL = Color.web("#666");
    private static final ActionR<String, Font> FontSupply = (s) -> Font.font(s, FontPosture.ITALIC, 13);
    //private static final Font DEFAULT_FOLD_FONT = Font.font("monospace", FontWeight.BOLD, 13);
    private static final Background DEFAULT_BACKGROUND = new Background(new BackgroundFill(Color.web("#ddd"), null, null));

    public static IntFunction<Node> get() {
        return new MyNoneLineNumFactory();
    }
    private MyNoneLineNumFactory()
    {
    }

    @Override
    public Node apply(int idx) {
        Label lineNo = new Label();
        lineNo.setFont(FontSupply.invoke(offerFontFamily.invoke(idx)));
        lineNo.setBackground(DEFAULT_BACKGROUND);
        lineNo.setTextFill(DEFAULT_TEXT_FILL);
        lineNo.setPadding(DEFAULT_INSETS);
        lineNo.setAlignment(Pos.TOP_RIGHT);
        lineNo.getStyleClass().add("lineno");

        // bind label's text to a Val that stops observing area's paragraphs
        // when lineNo is removed from scene
        lineNo.setText(" ");
        return lineNo;
    }
}
package com.allan.atools.richtext.codearea;

import com.allan.atools.utils.Log;
import javafx.application.Platform;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyleSpans;
import org.reactfx.collection.ListModification;

import java.util.function.Consumer;
import java.util.function.Function;

public final class VisibleParagraphStyler<PS, SEG, S> implements Consumer<ListModification<? extends Paragraph<PS, SEG, S>>> {
    private final GenericStyledArea<PS, SEG, S> area;
    private final Function<String, StyleSpans<S>> computeStyles;
    private int prevParagraph, prevTextLength;

    public VisibleParagraphStyler(GenericStyledArea<PS, SEG, S> area, Function<String, StyleSpans<S>> computeStyles) {
        this.computeStyles = computeStyles;
        this.area = area;
    }

    @Override
    public void accept(ListModification<? extends Paragraph<PS, SEG, S>> lm) {
        int addedSize = lm.getAddedSize();
        int from = lm.getFrom();
        Log.d("VisibleParagraphStyler", "accept addedSize: " + addedSize + ",from: " + from);
        if (addedSize > 0) {
            int paragraph = Math.min(area.firstVisibleParToAllParIndex() + from, area.getParagraphs().size() - 1);
            String text = area.getText(paragraph, 0, paragraph, area.getParagraphLength(paragraph));

            if (paragraph != prevParagraph || text.length() != prevTextLength) {
                int startPos = area.getAbsolutePosition(paragraph, 0);
                Platform.runLater(() -> area.setStyleSpans(startPos, computeStyles.apply(text)));
                prevTextLength = text.length();
                prevParagraph = paragraph;
            }
        }
    }
}

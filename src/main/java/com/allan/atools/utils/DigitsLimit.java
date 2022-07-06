package com.allan.atools.utils;

import java.text.DecimalFormat;
import java.text.ParsePosition;

import javafx.scene.control.TextFormatter;

public class DigitsLimit {
    public static <V> TextFormatter<V> createDigitsLimit() {
        DecimalFormat format = new DecimalFormat("#.0");
        return new TextFormatter<>(c -> {
            if (c.getControlNewText().isEmpty()) {
                return c;
            }

            ParsePosition parsePosition = new ParsePosition(0);

            Object object = format.parse(c.getControlNewText(), parsePosition);

            return (object == null || parsePosition.getIndex() < c.getControlNewText().length()) ? null : c;
        });
    }
}
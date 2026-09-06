package com.allan.atools.tools.modulenotepad.manager;

import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.node.*;

import java.util.ArrayList;
import java.util.List;

/** 表格行内展示数据、字体测量及短列紧凑、长列自适应的列宽分配。 */
final class MarkdownTableLayout {
    static final double HORIZONTAL_INSETS = 26;
    static final double VERTICAL_INSETS = 20;
    static final double SCROLL_HEIGHT = 12;

    record Run(String text, boolean bold, boolean italic, boolean strike, boolean link, boolean code) {}

    static List<Run> parse(Node root) {
        var runs = new ArrayList<Run>();
        append(runs, root, false, false, false, false);
        if (runs.isEmpty()) {
            runs.add(new Run(" ", false, false, false, false, false));
        }
        return List.copyOf(runs);
    }

    private static void append(List<Run> runs, Node node, boolean bold, boolean italic,
                               boolean strike, boolean link) {
        bold |= node instanceof StrongEmphasis;
        italic |= node instanceof Emphasis;
        strike |= node instanceof Strikethrough;
        link |= node instanceof Link;
        String literal = null;
        boolean code = node instanceof Code;
        if (node instanceof org.commonmark.node.Text text) {
            literal = text.getLiteral();
        } else if (node instanceof Code value) {
            literal = value.getLiteral();
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            literal = "\n";
        } else if (node instanceof HtmlInline html) {
            literal = html.getLiteral().matches("(?i)<br\\s*/?>") ? "\n" : html.getLiteral();
        }
        if (literal != null) {
            runs.add(new Run(literal, bold, italic, strike, link, code));
        }
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            append(runs, child, bold, italic, strike, link);
        }
    }

    static void fill(TextFlow flow, List<Run> runs, Font font, boolean header) {
        while (flow.getChildren().size() > runs.size()) {
            flow.getChildren().remove(flow.getChildren().size() - 1);
        }
        for (int index = 0; index < runs.size(); index++) {
            var run = runs.get(index);
            if (index == flow.getChildren().size()) {
                flow.getChildren().add(new Text());
            }
            var text = (Text) flow.getChildren().get(index);
            text.setText(run.text());
            text.setFont(font);
            text.getStyleClass().setAll("markdown-table-preview-content");
            text.strokeProperty().unbind();
            text.setStroke(null);
            if (header || run.bold()) {
                text.setStrokeWidth(font.getSize() * 0.035);
                text.strokeProperty().bind(text.fillProperty());
            }
            text.getTransforms().clear();
            if (run.italic()) {
                text.getTransforms().add(new javafx.scene.transform.Shear(-0.18, 0));
            }
            text.setStrikethrough(run.strike());
            if (run.link()) {
                text.getStyleClass().add("markdown-link");
            } else if (run.code()) {
                text.getStyleClass().add("markdown-inline-code");
            }
        }
    }

    static double characterWidth(Font font) {
        var text = new Text("0");
        text.setFont(font);
        return text.getLayoutBounds().getWidth();
    }

    static double[] distribute(double[] natural, double characterWidth, double available) {
        double threshold = Math.ceil(characterWidth * 40 + HORIZONTAL_INSETS);
        double emptyWidth = Math.ceil(characterWidth + HORIZONTAL_INSETS);
        var widths = new double[natural.length];
        var longColumns = new ArrayList<Integer>();
        double total = 0;
        for (int column = 0; column < natural.length; column++) {
            double width = Math.ceil(Math.max(emptyWidth, natural[column]));
            if (width > threshold) {
                width = threshold;
                longColumns.add(column);
            }
            widths[column] = width;
            total += width;
        }
        double remaining = Math.floor(available) - total;
        if (longColumns.isEmpty() || remaining <= 0) {
            return widths;
        }
        var growing = new ArrayList<>(longColumns);
        while (remaining > 0 && !growing.isEmpty()) {
            double share = remaining / growing.size();
            boolean capped = false;
            for (int index = growing.size() - 1; index >= 0; index--) {
                int column = growing.get(index);
                double needed = Math.ceil(natural[column]) - widths[column];
                if (needed <= share) {
                    widths[column] += needed;
                    remaining -= needed;
                    growing.remove(index);
                    capped = true;
                }
            }
            if (!capped) {
                for (int column : growing) {
                    widths[column] += share;
                }
                remaining = 0;
            }
        }
        if (remaining > 0) {
            for (int column : longColumns) {
                widths[column] += remaining / longColumns.size();
            }
        }
        double roundedTotal = 0;
        for (int column = 0; column < widths.length; column++) {
            widths[column] = Math.floor(widths[column]);
            roundedTotal += widths[column];
        }
        int remainder = (int) (Math.floor(available) - roundedTotal);
        for (int index = 0; index < remainder; index++) {
            widths[longColumns.get(index % longColumns.size())]++;
        }
        return widths;
    }

    /** 仅保留同一单元格当前字体及当前列宽对应的测量值。 */
    static final class Cell {
        final String source;
        final List<Run> runs;
        final Font font;
        final boolean header;
        double natural = -1;
        double width = -1;
        double height;

        Cell(String source, List<Run> runs, Font font, boolean header) {
            this.source = source;
            this.runs = runs;
            this.font = font;
            this.header = header;
        }

        Cell copy() {
            var value = new Cell(source, runs, font, header);
            value.natural = natural;
            value.width = width;
            value.height = height;
            return value;
        }

        void measureNatural(TextFlow flow) {
            if (natural < 0) {
                fill(flow, runs, font, header);
                natural = Math.ceil(flow.prefWidth(-1) + HORIZONTAL_INSETS);
            }
        }

        void measureHeight(TextFlow flow, double nextWidth) {
            if (width != nextWidth) {
                fill(flow, runs, font, header);
                height = Math.ceil(flow.prefHeight(Math.max(1, nextWidth - HORIZONTAL_INSETS))
                        + VERTICAL_INSETS);
                width = nextWidth;
            }
        }
    }
}

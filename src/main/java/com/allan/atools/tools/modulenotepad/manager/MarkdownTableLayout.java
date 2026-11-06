package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.GlobalConfig;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.fxmisc.richtext.TextExt;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.node.*;

import java.util.ArrayList;
import java.util.List;

/** 表格行内展示数据、字体测量及短列紧凑、长列自适应的列宽分配。 */
public final class MarkdownTableLayout {
    static final double HORIZONTAL_INSETS = 26;
    static final double VERTICAL_INSETS = 20;
    static final double SCROLL_HEIGHT = 12;

    public record Run(String text, boolean bold, boolean italic, boolean strike, boolean link, boolean code,
               String destination, String image) {
        Run(String text, boolean bold, boolean italic, boolean strike, boolean link, boolean code) {
            this(text, bold, italic, strike, link, code, null, null);
        }
    }

    static List<Run> parse(Node root) {
        var runs = new ArrayList<Run>();
        append(runs, root, false, false, false, null);
        if (runs.isEmpty()) {
            runs.add(new Run(" ", false, false, false, false, false));
        }
        return List.copyOf(runs);
    }

    private static void append(List<Run> runs, Node node, boolean bold, boolean italic,
                               boolean strike, String destination) {
        bold |= node instanceof StrongEmphasis;
        italic |= node instanceof Emphasis;
        strike |= node instanceof Strikethrough;
        if (node instanceof Link value) destination = value.getDestination();
        if (node instanceof Image value) {
            String alt = com.allan.atools.richtext.codearea.keywordhelper.MarkdownPlainText.render(value);
            runs.add(new Run(alt, bold, italic, strike, destination != null, false, destination, value.getDestination()));
            return;
        }
        String literal = null;
        boolean code = node instanceof Code;
        if (node instanceof org.commonmark.node.Text text) {
            literal = text.getLiteral();
        } else if (node instanceof Code value) {
            literal = value.getLiteral();
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            literal = "\n";
        } else if (node instanceof com.allan.atools.richtext.codearea.keywordhelper.MarkdownEmoji emoji) {
            literal = emoji.getLiteral();
        } else if (node instanceof com.allan.atools.richtext.codearea.keywordhelper.MarkdownMath math) {
            literal = math.getLiteral();
        } else if (node instanceof HtmlInline html) {
            var image = org.jsoup.Jsoup.parseBodyFragment(html.getLiteral()).selectFirst("img[src]");
            if (image != null) {
                runs.add(new Run(image.attr("alt"), bold, italic, strike, destination != null, false, destination, image.attr("src")));
                return;
            }
            literal = html.getLiteral().matches("(?i)<br\\s*/?>") ? "\n" : html.getLiteral();
        }
        if (literal != null) {
            runs.add(new Run(literal, bold, italic, strike, destination != null, code, destination, null));
        }
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            append(runs, child, bold, italic, strike, destination);
        }
    }

    static void fill(TextFlow flow, List<Run> runs, Font font, boolean header) {
        fill(flow, runs, font, header, null);
    }

    static void fill(TextFlow flow, List<Run> runs, Font font, boolean header,
                     java.util.function.Function<Run, javafx.scene.Node> images) {
        if (flow instanceof MarkdownTableTextFlow tableFlow) tableFlow.clearCodeBackgrounds();
        // 子节点数按分段计数：一个 Run 可能拆出表情与文字多个分段。
        int index = 0;
        for (Run run : runs) {
            if (run.code() && com.allan.atools.richtext.codearea.keywordhelper.MarkdownTaskMarkers.isStatus(run.text())) {
                var icon = new com.allan.atools.richtext.codearea.MarkdownTaskIcon(font.getSize(), !run.text().equals("[ ]"));
                icon.setUserData(run.destination());
                if (index == flow.getChildren().size()) flow.getChildren().add(icon);
                else flow.getChildren().set(index, icon);
                index++;
                continue;
            }
            if (run.image() != null) {
                var picture = images == null ? new javafx.scene.layout.Region() : images.apply(run);
                if (images == null) {
                    var region = (javafx.scene.layout.Region) picture;
                    region.setMinSize(120, 80); region.setPrefSize(120, 80); region.setMaxSize(120, 80);
                }
                picture.setUserData(run.destination());
                if (index == flow.getChildren().size()) flow.getChildren().add(picture);
                else flow.getChildren().set(index, picture);
                index++;
                continue;
            }
            if (run.code()) {
                // 行内代码保持等宽语义，表情不拆分、不切换字体。
                index = applySegment(flow, index, run, run.text(), false, font, header);
                continue;
            }
            String value = run.text();
            int offset = 0;
            while (offset < value.length()) {
                int end = nextSegmentEnd(value, offset);
                // 只有分段起点本身是表情序列时才换表情字体。若按“分段非空”判断，普通文字也会被
                // 苹果表情字体接管：该字体含 0-9 字形且是全角、位置偏低，数字就会又宽又沉。
                boolean emoji = com.allan.atools.richtext.codearea.keywordhelper.MarkdownEmojiShortcodes.unicodeEnd(value, offset) > offset;
                index = applySegment(flow, index, run, value.substring(offset, end), emoji, font, header);
                offset = end;
            }
        }
        // 只收缩受管理的分段节点；表情覆盖层（unmanaged）由渲染层自行增删，不能在这里移除。
        for (int i = flow.getChildren().size() - 1; i >= index; i--) {
            if (flow.getChildren().get(i).isManaged()) flow.getChildren().remove(i);
        }
    }

    /** 定位下一段边界：表情序列在此起步时返回序列结尾，否则推进到下一个表情起点。 */
    private static int nextSegmentEnd(String value, int from) {
        int emojiEnd = com.allan.atools.richtext.codearea.keywordhelper.MarkdownEmojiShortcodes.unicodeEnd(value, from);
        if (emojiEnd > from) return emojiEnd;
        int cursor = from;
        while (cursor < value.length()) {
            int point = value.codePointAt(cursor);
            if (com.allan.atools.richtext.codearea.keywordhelper.MarkdownEmojiShortcodes.unicodeEnd(value, cursor) > cursor) {
                return cursor;
            }
            cursor += Character.charCount(point);
        }
        return value.length();
    }

    /** 表情分段切换彩色表情字体并交给正文同款高清覆盖渲染；普通分段保持原字体、粗斜体等样式。 */
    private static int applySegment(TextFlow flow, int index, Run run, String value, boolean emoji, Font font, boolean header) {
        if (index == flow.getChildren().size()) flow.getChildren().add(new TextExt());
        else if (!(flow.getChildren().get(index) instanceof TextExt)) flow.getChildren().set(index, new TextExt());
        var text = (Text) flow.getChildren().get(index);
        text.setUserData(run.destination());
        text.setText(value);
        text.setUnderline(run.link());
        text.setStrikethrough(run.strike());
        text.getTransforms().clear();
        String emojiFamily = com.allan.uilibs.richtexts.CodeArea.emojiFontFamily();
        if (emoji && emojiFamily != null) {
            var emojiFont = Font.font(emojiFamily, font.getSize());
            if (emojiFont != null) {
                text.setFont(emojiFont);
                text.getStyleClass().setAll("markdown-table-preview-content");
                if (run.link()) text.getStyleClass().add("markdown-link");
                text.getStyleClass().add(com.allan.uilibs.richtexts.CodeArea.MARKDOWN_EMOJI_RENDERED_CLASS);
                return index + 1;
            }
        }
        boolean bold = header || run.bold();
        text.setFont(bold ? Font.font(font.getFamily(), FontWeight.BOLD, font.getSize()) : font);
        text.getStyleClass().setAll("markdown-table-preview-content");
        if (bold) text.getStyleClass().add("markdown-bold");
        if (run.italic()) {
            text.getTransforms().add(new javafx.scene.transform.Shear(-0.18, 0));
        }
        if (run.link()) {
            text.getStyleClass().add("markdown-link");
        }
        if (run.code()) {
            text.getStyleClass().add("markdown-inline-code");
        }
        return index + 1;
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
        if (remaining < 0) {
            // 40 字符是长列的期望宽度，不作为硬下限；空间不足时收窄宽列，由单元格换行。
            // 按全局配置保留阅读宽度（短列维持原宽），连此下限也放不下时才横滑。
            double readableWidth = Math.ceil(characterWidth * GlobalConfig.MARKDOWN_TABLE_MIN_COLUMN_CHARACTERS
                    + HORIZONTAL_INSETS);
            double minimumTotal = 0;
            for (double width : widths) {
                minimumTotal += Math.min(width, readableWidth);
            }
            double shrinkable = total - minimumTotal;
            if (shrinkable > 0) {
                double shrinkRatio = Math.min(1, -remaining / shrinkable);
                for (int column = 0; column < widths.length; column++) {
                    double minimum = Math.min(widths[column], readableWidth);
                    widths[column] = Math.floor(widths[column] - (widths[column] - minimum) * shrinkRatio);
                }
            }
            return widths;
        }
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
    public static final class Cell {
        final String source;
        final List<Run> runs;
        final Font font;
        final boolean header;
        final Font textFont;
        final String plainText;
        double natural = -1;
        double width = -1;
        double height;

        Cell(String source, List<Run> runs, Font font, boolean header) {
            this.source = source;
            this.runs = runs;
            this.font = font;
            this.header = header;
            textFont = header ? Font.font(font.getFamily(), FontWeight.BOLD, font.getSize()) : font;
            plainText = MarkdownTablePlainText.plainText(source, runs);
        }

        Cell copy() {
            var value = new Cell(source, runs, font, header);
            value.natural = natural;
            value.width = width;
            value.height = height;
            return value;
        }

        void measureNatural(TextFlow flow, Text measure) {
            if (natural < 0) {
                if (plainText != null) {
                    measure.setFont(textFont);
                    measure.setText(plainText);
                    measure.setWrappingWidth(0);
                    natural = Math.ceil(measure.getLayoutBounds().getWidth() + HORIZONTAL_INSETS);
                } else {
                    fill(flow, runs, font, header);
                    natural = Math.ceil(flow.prefWidth(-1) + HORIZONTAL_INSETS);
                }
            }
        }

        void measureHeight(TextFlow flow, Text measure, double nextWidth) {
            if (width != nextWidth) {
                if (plainText != null) {
                    height = MarkdownTablePlainText.measureHeight(measure, plainText, textFont, nextWidth);
                } else {
                    fill(flow, runs, font, header);
                    height = Math.ceil(flow.prefHeight(Math.max(1, nextWidth - HORIZONTAL_INSETS))
                            + VERTICAL_INSETS);
                }
                width = nextWidth;
            }
        }
    }
}

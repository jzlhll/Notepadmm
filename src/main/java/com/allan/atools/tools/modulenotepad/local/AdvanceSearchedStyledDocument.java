package com.allan.atools.tools.modulenotepad.local;

import com.allan.atools.richtext.ParStyle;
import com.allan.atools.richtext.TextStyle;
import com.allan.atools.tools.modulenotepad.manager.ShowType;
import com.allan.atools.ui.ColorPickerUtil;
import com.allan.atools.utils.Locales;
import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.text.beans.AllFilesSearchResults;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.Colors;
import javafx.scene.paint.Color;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.*;
import org.reactfx.collection.MaterializedListModification;
import org.reactfx.util.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import static com.allan.atools.tools.modulenotepad.StaticsProf.sFindTextColorByAuto;
import static org.reactfx.util.Either.left;
import static org.reactfx.util.Either.right;
import static org.reactfx.util.Tuples.t;

/**
 * 搜索结果的构建document
 * @param <PS>
 * @param <SEG>
 * @param <S>
 */
public final class AdvanceSearchedStyledDocument<PS, SEG, S> implements StyledDocument<PS, SEG, S> {
    private static class Summary {
        private final int paragraphCount;
        private final int charCount;

        public Summary(int paragraphCount, int charCount) {
            assert paragraphCount > 0;
            assert charCount >= 0;

            this.paragraphCount = paragraphCount;
            this.charCount = charCount;
        }

        public int length() {
            return charCount + paragraphCount - 1;
        }
    }

    /**
     * Private method for quickly calculating the length of a portion (subdocument) of this document.
     */
    private static <PS, SEG, S> ToSemigroup<Paragraph<PS, SEG, S>, Summary> summaryProvider() {
        return new ToSemigroup<Paragraph<PS, SEG, S>, Summary>() {

            @Override
            public Summary apply(Paragraph<PS, SEG, S> p) {
                return new Summary(1, p.length());
            }

            @Override
            public Summary reduce(Summary left, Summary right) {
                return new Summary(
                        left.paragraphCount + right.paragraphCount,
                        left.charCount + right.charCount);
            }
        };

    }

    private static final BiFunction<Summary, Integer, Either<Integer, Integer>> NAVIGATE =
            (s, i) -> i <= s.length() ? left(i) : right(i - (s.length() + 1));

    private static final boolean FROM_OTHER_INFO = false;

    /**
     * 用于 result的一条一条自行创建的样式
     */
    public static AdvanceSearchedStyledDocument<ParStyle, String, TextStyle> from(
            GenericStyledArea<ParStyle, String, TextStyle> resultArea, AllFilesSearchResults all,
            int[] outTotalLines) {
        //这个其实就是searchPaneNumber显示第0行
        HashMap<String, StylePair> combineColorAndStylePairMap = new HashMap<>();

        var initTextStyle = resultArea.getInitialTextStyle();
        var initParaStyle = resultArea.getInitialParagraphStyle();
        var segmentOps = resultArea.getSegOps();

        var lineParaStyle = initParaStyle.updateBackgroundColor(Colors.DescLineBgColor.invoke());
        var lineTextStyle = initTextStyle.updateTextColor(Colors.DescLineTextColor.invoke());

        List<Paragraph<ParStyle, String, TextStyle>> retParas = new ArrayList<>();
        for (OneFileSearchResults oneFileResults : all.allResults) {
            var head = new ResultItemWrap();
            head.lineMode = ResultItemWrap.LineMode.FilePath;
            String s = String.format(Locales.str("result.hitTimes"), oneFileResults.results.size());
            head.setLine("  " + oneFileResults.file.getName() + "  (" + s + ")");
            oneFileResults.results.add(0, head);

            for (ResultItemWrap itemWrap : oneFileResults.results) {
                if(FROM_OTHER_INFO && outTotalLines != null) outTotalLines[0]++;

                if (itemWrap.lineMode == ResultItemWrap.LineMode.Real && itemWrap.items != null) {
                    //对于行模式来说，是可以直接每行都显示的 todo 给下特殊颜色
                    int itemsSize = itemWrap.items.length;
                    int tmpLen = itemWrap.getLine().length();

                    StyleSpansBuilder<TextStyle> ssb = new StyleSpansBuilder<>();
                    ParStyle firstParStyle = null;
                    TextStyle firstTextStyle = null;

                    int lastEnd = 0;
                    for (int i = 0; i < itemsSize; i++) {
                        var item = itemWrap.items[i];

                        String combineColor = item.searchParams.bgColor + ";" + item.searchParams.textColor;
                        StylePair pair = combineColorAndStylePairMap.get(combineColor);
                        if (pair == null) {
                            pair = new StylePair();
                            var bgColor = Color.valueOf(item.searchParams.bgColor);
                            pair.parStyle = initParaStyle.updateBackgroundColor(bgColor);
                            pair.textStyle = initTextStyle.updateTextColor(Color.valueOf(item.searchParams.textColor));
                            var bgAndTextColors = ColorPickerUtil.getMatchedColors(item.searchParams.bgColor);
                            if (sFindTextColorByAuto) {
                                pair.matchTextStyle = pair.textStyle
                                        .updateBackgroundColor(bgAndTextColors[0])
                                        .updateTextColor(bgAndTextColors[1]);
                            } else {
                                pair.matchTextStyle = pair.textStyle
                                        .updateBackgroundColor(bgAndTextColors[0]);
                            }

                            combineColorAndStylePairMap.put(combineColor, pair);
                        }

                        if (firstParStyle == null) {
                            firstParStyle = pair.parStyle;
                            firstTextStyle = pair.textStyle;
                        }

                        if (itemsSize == 1) { //一行只匹配一个。
                            if (item.searchParams.highLight) {
                                //1. 左边的文字
                                if (item.range.start > 0 || itemWrap.resultOffset > 0) {
                                    ssb.add(pair.textStyle, item.range.start + itemWrap.resultOffset);
                                }
                                //2. 中间的matched words todo 计算一个偏移色
                                ssb.add(pair.matchTextStyle, item.range.end - item.range.start);
                                //3. 后面的文字
                                if (tmpLen > item.range.end) {
                                    ssb.add(pair.textStyle, tmpLen - item.range.end - itemWrap.resultOffset);
                                }

                                retParas.add(new Paragraph<>(firstParStyle, segmentOps, segmentOps.create(itemWrap.getLine()), ssb.create()));
                            } else {
                                retParas.add(new Paragraph<>(firstParStyle, segmentOps, segmentOps.create(itemWrap.getLine()), pair.textStyle));
                            }
                        } else {
                            //1. 左边的文字
                            if (i== 0 && (item.range.start > 0 || itemWrap.resultOffset > 0)) {
                                ssb.add(pair.textStyle, item.range.start + itemWrap.resultOffset - lastEnd);
                            } else if (i > 0 && item.range.start > lastEnd) {
                                ssb.add(pair.textStyle, item.range.start - lastEnd);
                            }
                            //2. 中间的matched words todo 逐级改变
                            ssb.add(item.searchParams.highLight ? pair.matchTextStyle : pair.textStyle, item.range.end - item.range.start);

                            //3. 如果是最后一个item，则追加后面的文字。前面的则等着下一次循环的左边文字
                            if (i == itemsSize - 1) {
                                if (tmpLen > item.range.end) {
                                    ssb.add(firstTextStyle, tmpLen - item.range.end - itemWrap.resultOffset);
                                }
                            }
                            lastEnd = item.range.end;
                        }
//                        if (FROM_OTHER_INFO && outSsb != null) {
//                            tmpLen = item.range.start;
//                            if (tmpLen > 0) {
//                                outSsb.add(initTextStyle, tmpLen);
//                            }
//
//                            tmpLen = item.range.end - item.range.start;
//                            outSsb.add(matchedTextStyle, tmpLen);
//
//                            tmpLen = itemWrap.line.length() + 1 - item.range.end;
//                            outSsb.add(initTextStyle, tmpLen);
//                        }
                    }
                    if (itemsSize > 1) { //如果是多个时候则
                        retParas.add(new Paragraph<>(firstParStyle, segmentOps, segmentOps.create(itemWrap.getLine()), ssb.create()));
                    }
                } else if (itemWrap.lineMode == ResultItemWrap.LineMode.FilePath) {
                    retParas.add(new Paragraph<>(lineParaStyle, segmentOps, segmentOps.create(itemWrap.getLine()), lineTextStyle));
//                    if (FROM_OTHER_INFO && outSsb != null) {
//                        outSsb.add(initTextStyle, itemWrap.line.length() + 1);
//                    }
                }
            }
        }

        return new AdvanceSearchedStyledDocument<>(retParas);
    }

    private static class StylePair {
        TextStyle textStyle;
        ParStyle parStyle;
        TextStyle matchTextStyle;
    }

    private final FingerTree.NonEmptyFingerTree<Paragraph<PS, SEG, S>, Summary> tree;

    private String text = null;
    private List<Paragraph<PS, SEG, S>> paragraphs = null;

    private AdvanceSearchedStyledDocument(FingerTree.NonEmptyFingerTree<Paragraph<PS, SEG, S>, Summary> tree) {
        this.tree = tree;
    }

    private AdvanceSearchedStyledDocument(List<Paragraph<PS, SEG, S>> paragraphs) {
        this.tree =
                FingerTree.mkTree(paragraphs, summaryProvider()).caseEmpty().unify(
                        emptyTree -> { throw new AssertionError("Unreachable code"); },
                        neTree -> neTree);
    }

    @Override
    public int length() {
        return tree.getSummary().length();
    }

    @Override
    public String getText() {
        if(text == null) {
            String[] strings = getParagraphs().stream()
                    .map(Paragraph::getText)
                    .toArray(n -> new String[n]);
            text = String.join("\n", strings);
        }
        return text;
    }

    public int getParagraphCount() {
        return tree.getLeafCount();
    }

    public Paragraph<PS, SEG, S> getParagraph(int index) {
        return tree.getLeaf(index);
    }

    @Override
    public List<Paragraph<PS, SEG, S>> getParagraphs() {
        if(paragraphs == null) {
            paragraphs = tree.asList();
        }
        return paragraphs;
    }

    @Override
    public Position position(int major, int minor) {
        return new Pos(major, minor);
    }

    @Override
    public Position offsetToPosition(int offset, Bias bias) {
        return position(0, 0).offsetBy(offset, bias);
    }

    /**
     * Splits this document into two at the given position and returns both halves.
     */
    public Tuple2<AdvanceSearchedStyledDocument<PS, SEG, S>, AdvanceSearchedStyledDocument<PS, SEG, S>> split(int position) {
        return tree.locate(NAVIGATE, position).map(this::split);
    }

    /**
     * Splits this document into two at the given paragraph's column position and returns both halves.
     */
    public Tuple2<AdvanceSearchedStyledDocument<PS, SEG, S>, AdvanceSearchedStyledDocument<PS, SEG, S>> split(
            int paragraphIndex, int columnPosition) {
        return tree.splitAt(paragraphIndex).map((l, p, r) -> {
            Paragraph<PS, SEG, S> p1 = p.trim(columnPosition);
            Paragraph<PS, SEG, S> p2 = p.subSequence(columnPosition);
            AdvanceSearchedStyledDocument<PS, SEG, S> doc1 = new AdvanceSearchedStyledDocument<>(l.append(p1));
            AdvanceSearchedStyledDocument<PS, SEG, S> doc2 = new AdvanceSearchedStyledDocument<>(r.prepend(p2));
            return t(doc1, doc2);
        });
    }

    @Override
    public AdvanceSearchedStyledDocument<PS, SEG, S> concat(StyledDocument<PS, SEG, S> other) {
        return concat0(other, Paragraph::concat);
    }

    private AdvanceSearchedStyledDocument<PS, SEG, S> concatR(StyledDocument<PS, SEG, S> other) {
        return concat0(other, AdvanceSearchedStyledDocument::concatR);
    }

    static <PS,SEG,S> Paragraph<PS, SEG, S> concatR(Paragraph<PS, SEG, S> thiz, Paragraph<PS, SEG, S> that) {
        return thiz.length() == 0 && that.length() == 0
                ? that
                : thiz.concat(that);
    }

    private AdvanceSearchedStyledDocument<PS, SEG, S> concat0(StyledDocument<PS, SEG, S> other, BinaryOperator<Paragraph<PS, SEG, S>> parConcat) {
        int n = tree.getLeafCount() - 1;
        Paragraph<PS, SEG, S> p0 = tree.getLeaf(n);
        Paragraph<PS, SEG, S> p1 = other.getParagraphs().get(0);
        Paragraph<PS, SEG, S> p = parConcat.apply(p0, p1);
        FingerTree.NonEmptyFingerTree<Paragraph<PS, SEG, S>, Summary> tree1 = tree.updateLeaf(n, p);
        FingerTree<Paragraph<PS, SEG, S>, Summary> tree2 = (other instanceof AdvanceSearchedStyledDocument)
                ? ((AdvanceSearchedStyledDocument<PS, SEG, S>) other).tree.split(1)._2
                : FingerTree.mkTree(other.getParagraphs().subList(1, other.getParagraphs().size()), summaryProvider());
        return new AdvanceSearchedStyledDocument<>(tree1.join(tree2));
    }

    @Override
    public StyledDocument<PS, SEG, S> subSequence(int start, int end) {
        return split(end)._1.split(start)._2;
    }

    /**
     * Replaces the given portion {@code "from..to"} with the given replacement and returns
     * <ol>
     *     <li>
     *         the updated version of this document that includes the replacement,
     *     </li>
     *     <li>
     *         the {@link RichTextChange} that represents the change from this document to the returned one, and
     *     </li>
     *     <li>
     *         the modification used to update an area's list of paragraphs.
     *     </li>
     * </ol>
     */
    public Tuple3<AdvanceSearchedStyledDocument<PS, SEG, S>, RichTextChange<PS, SEG, S>, MaterializedListModification<Paragraph<PS, SEG, S>>> replace(
            int from, int to, AdvanceSearchedStyledDocument<PS, SEG, S> replacement) {
        return replace(from, to, x -> replacement);
    }

    /**
     * Replaces the given portion {@code "from..to"} in the document by getting that portion of this document,
     * passing it into the mapping function, and using the result as the replacement. Returns
     * <ol>
     *     <li>
     *         the updated version of this document that includes the replacement,
     *     </li>
     *     <li>
     *         the {@link RichTextChange} that represents the change from this document to the returned one, and
     *     </li>
     *     <li>
     *         the modification used to update an area's list of paragraphs.
     *     </li>
     * </ol>
     */
    public Tuple3<AdvanceSearchedStyledDocument<PS, SEG, S>, RichTextChange<PS, SEG, S>, MaterializedListModification<Paragraph<PS, SEG, S>>> replace(
            int from, int to, UnaryOperator<AdvanceSearchedStyledDocument<PS, SEG, S>> mapper) {
        ensureValidRange(from, to);
        BiIndex start = tree.locate(NAVIGATE, from);
        BiIndex end = tree.locate(NAVIGATE, to);
        return replace(start, end, mapper);
    }

    public Tuple3<AdvanceSearchedStyledDocument<PS, SEG, S>, RichTextChange<PS, SEG, S>, MaterializedListModification<Paragraph<PS, SEG, S>>> replace(
            int paragraphIndex, int fromCol, int toCol, UnaryOperator<AdvanceSearchedStyledDocument<PS, SEG, S>> f) {
        ensureValidParagraphRange(paragraphIndex, fromCol, toCol);
        return replace(new BiIndex(paragraphIndex, fromCol), new BiIndex(paragraphIndex, toCol), f);
    }

    // Note: there must be a "ensureValid_()" call preceding the call of this method
    private Tuple3<AdvanceSearchedStyledDocument<PS, SEG, S>, RichTextChange<PS, SEG, S>, MaterializedListModification<Paragraph<PS, SEG, S>>> replace(
            BiIndex start, BiIndex end, UnaryOperator<AdvanceSearchedStyledDocument<PS, SEG, S>> f) {
        int pos = tree.getSummaryBetween(0, start.major).map(s -> s.length() + 1).orElse(0) + start.minor;

        List<Paragraph<PS, SEG, S>> removedPars =
                getParagraphs().subList(start.major, end.major + 1);

        return end.map(this::split).map((l0, r) -> {
            return start.map(l0::split).map((l, removed) -> {
                AdvanceSearchedStyledDocument<PS, SEG, S> replacement = f.apply(removed);
                AdvanceSearchedStyledDocument<PS, SEG, S> doc = l.concatR(replacement).concat(r);
                // Next we use doc.subSequence instead of replacement because Paragraph.concat's returned paragraph style can vary.
                RichTextChange<PS, SEG, S> change = new RichTextChange<>(pos, removed, doc.subSequence(pos, pos+replacement.length()));
                List<Paragraph<PS, SEG, S>> addedPars = doc.getParagraphs().subList(start.major, start.major + replacement.getParagraphCount());
                MaterializedListModification<Paragraph<PS, SEG, S>> parChange =
                        MaterializedListModification.create(start.major, removedPars, addedPars);
                return t(doc, change, parChange);
            });
        });
    }

    /**
     * Maps the paragraph at the given index by calling {@link #replace(int, int, UnaryOperator)}. Returns
     * <ol>
     *     <li>
     *         the updated version of this document that includes the replacement,
     *     </li>
     *     <li>
     *         the {@link RichTextChange} that represents the change from this document to the returned one, and
     *     </li>
     *     <li>
     *         the modification used to update an area's list of paragraphs.
     *     </li>
     * </ol>
     */
    public Tuple3<AdvanceSearchedStyledDocument<PS, SEG, S>, RichTextChange<PS, SEG, S>, MaterializedListModification<Paragraph<PS, SEG, S>>> replaceParagraph(
            int parIdx, UnaryOperator<Paragraph<PS, SEG, S>> mapper) {
        ensureValidParagraphIndex(parIdx);
        return replace(
                new BiIndex(parIdx, 0),
                new BiIndex(parIdx, tree.getLeaf(parIdx).length()),
                doc -> doc.mapParagraphs(mapper));
    }

    /**
     * Maps all of this document's paragraphs using the given mapper and returns them in a new
     * {@link AdvanceSearchedStyledDocument}.
     */
    public AdvanceSearchedStyledDocument<PS, SEG, S> mapParagraphs(UnaryOperator<Paragraph<PS, SEG, S>> mapper) {
        int n = tree.getLeafCount();
        List<Paragraph<PS, SEG, S>> pars = new ArrayList<>(n);
        for(int i = 0; i < n; ++i) {
            pars.add(mapper.apply(tree.getLeaf(i)));
        }
        return new AdvanceSearchedStyledDocument<>(pars);
    }

    @Override
    public String toString() {
        return getParagraphs()
                .stream()
                .map(Paragraph::toString)
                .reduce((p1, p2) -> p1 + "\n" + p2)
                .orElse("");
    }

    @Override
    public final boolean equals(Object other) {
        if(other instanceof StyledDocument) {
            StyledDocument<?, ?, ?> that = (StyledDocument<?, ?, ?>) other;
            return Objects.equals(this.getParagraphs(), that.getParagraphs());
        } else {
            return false;
        }
    }

    @Override
    public final int hashCode() {
        return getParagraphs().hashCode();
    }


    private class Pos implements Position {

        private final int major;
        private final int minor;

        private Pos(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }

        @Override
        public String toString() {
            return "(" + major + ", " + minor + ")";
        }

        @Override
        public boolean sameAs(Position other) {
            return getTargetObject() == other.getTargetObject()
                    && major == other.getMajor()
                    && minor == other.getMinor();
        }

        @Override
        public TwoDimensional getTargetObject() {
            return AdvanceSearchedStyledDocument.this;
        }

        @Override
        public int getMajor() {
            return major;
        }

        @Override
        public int getMinor() {
            return minor;
        }

        @Override
        public Position clamp() {
            if(major == tree.getLeafCount() - 1) {
                int elemLen = tree.getLeaf(major).length();
                if(minor < elemLen) {
                    return this;
                } else {
                    return new Pos(major, elemLen-1);
                }
            } else {
                return this;
            }
        }

        @Override
        public Position offsetBy(int amount, Bias bias) {
            return tree.locateProgressively(s -> s.charCount + s.paragraphCount, toOffset() + amount)
                    .map(Pos::new);
        }

        @Override
        public int toOffset() {
            if(major == 0) {
                return minor;
            } else {
                return tree.getSummaryBetween(0, major).get().length() + 1 + minor;
            }
        }
    }

    private void ensureValidParagraphIndex(int parIdx) {
        Lists.checkIndex(parIdx, getParagraphCount());
    }

    private void ensureValidRange(int start, int end) {
        Lists.checkRange(start, end, length());
    }

    private void ensureValidParagraphRange(int par, int start, int end) {
        ensureValidParagraphIndex(par);
        Lists.checkRange(start, end, fullLength(par));
    }

    private int fullLength(int par) {
        int n = getParagraphCount();
        return getParagraph(par).length() + (par == n-1 ? 0 : 1);
    }


}


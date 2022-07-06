package com.allan.uilibs.richtexts;

import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.beans.Observable;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import org.fxmisc.flowless.Virtualized;
import org.reactfx.value.Val;
import org.reactfx.value.Var;

import static javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED;

public class MyVirtualScrollPane<V extends Node & Virtualized> extends Region implements Virtualized {
    private static final boolean DEBUG = false;//UIContext.DEBUG;
    private static final PseudoClass CONTENT_FOCUSED = PseudoClass.getPseudoClass("com.base.content-focused");

    private final ScrollBar hbar;
    private final ScrollBar vbar;
    private final V content;
    private final ChangeListener<Boolean> contentFocusedListener;
    private final ChangeListener<Double> hbarValueListener;
    private ChangeListener<Double> hPosEstimateListener;
    private final ChangeListener<Double> vbarValueListener;
    private ChangeListener<Double> vPosEstimateListener;

    private Var<Double> hbarValue;
    private Var<Double> vbarValue;
    private Var<Double> hPosEstimate;
    private Var<Double> vPosEstimate;

    /** The Policy for the Horizontal ScrollBar */
    private final Var<ScrollPane.ScrollBarPolicy> hbarPolicy;
    public final ScrollPane.ScrollBarPolicy getHbarPolicy() { return hbarPolicy.getValue(); }
    public final void setHbarPolicy(ScrollPane.ScrollBarPolicy value) { hbarPolicy.setValue(value); }
    public final Var<ScrollPane.ScrollBarPolicy> hbarPolicyProperty() { return hbarPolicy; }

    /** The Policy for the Vertical ScrollBar */
    private final Var<ScrollPane.ScrollBarPolicy> vbarPolicy;
    public final ScrollPane.ScrollBarPolicy getVbarPolicy() { return vbarPolicy.getValue(); }
    public final void setVbarPolicy(ScrollPane.ScrollBarPolicy value) { vbarPolicy.setValue(value); }
    public final Var<ScrollPane.ScrollBarPolicy> vbarPolicyProperty() { return vbarPolicy; }

//    private volatile boolean isScrollH = false;
//    private volatile double mLastScrollY = 0d;
//    private final Runnable mUpdateScrollHRunnable = () -> {
//        isScrollH = false;
//    };
//
//    private void updateIsScrollHState() {
//        isScrollH = true;
//        ThreadUtils.globalHandler().removeCallbacksAndMessages(mUpdateScrollHRunnable);
//        ThreadUtils.globalHandler().postDelayed(mUpdateScrollHRunnable, 150);
//    }

    /**
     * Constructs a VirtualizedScrollPane with the given com.base.content and policies
     */
    public MyVirtualScrollPane(
            @NamedArg("content") V content,
            @NamedArg("hPolicy") ScrollPane.ScrollBarPolicy hPolicy,
            @NamedArg("vPolicy") ScrollPane.ScrollBarPolicy vPolicy
    ) {
        this.getStyleClass().add("virtualized-scroll-pane");
        this.content = content;

        // create scrollbars
        hbar = new ScrollBar();
        vbar = new ScrollBar();
        hbar.setOrientation(Orientation.HORIZONTAL);
        vbar.setOrientation(Orientation.VERTICAL);

        // scrollbar ranges
        hbar.setMin(0);
        vbar.setMin(0);
        hbar.maxProperty().bind(content.totalWidthEstimateProperty());
        vbar.maxProperty().bind(content.totalHeightEstimateProperty());

        if (DEBUG) {
            content.totalWidthEstimateProperty().addListener((observable, oldValue, newValue) -> System.out.println("com.base.content total width " + newValue));
            content.totalHeightEstimateProperty().addListener((observable, oldValue, newValue) -> System.out.println("com.base.content total height " + newValue));
            content.estimatedScrollYProperty().addListener((observable, oldValue, newValue) -> {
                System.out.println("estimated ScrollYy Property " + newValue);
            });
            content.estimatedScrollXProperty().addListener((observable, oldValue, newValue) -> {
                System.out.println("estimated ScrollXx Property " + newValue);
            });

            content.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> System.out.println("com.base.content layout bound changed " + newValue));
        }

        // scrollbar increments
        setupUnitIncrement(hbar);
        setupUnitIncrement(vbar);
        hbar.blockIncrementProperty().bind(hbar.visibleAmountProperty());
        vbar.blockIncrementProperty().bind(vbar.visibleAmountProperty());

        // scrollbar positions
        hPosEstimate = Val.combine(
                        content.estimatedScrollXProperty(),
                        Val.map(content.layoutBoundsProperty(), Bounds::getWidth),
                        content.totalWidthEstimateProperty(),
                        MyVirtualScrollPane::offsetToScrollbarPositionH)
                .asVar(this::setHPosition);
        vPosEstimate = Val.combine(
                        content.estimatedScrollYProperty(),
                        Val.map(content.layoutBoundsProperty(), Bounds::getHeight),
                        content.totalHeightEstimateProperty(),
                        MyVirtualScrollPane::offsetToScrollbarPositionV)
                .orElseConst(0.0)
                .asVar(this::setVPosition);
        hbarValue = Var.doubleVar(hbar.valueProperty());
        vbarValue = Var.doubleVar(vbar.valueProperty());
        // The use of a pair of mirrored ChangeListener instead of a more natural bidirectional binding
        // here is a workaround following a change in JavaFX [1] which broke the behaviour of the scroll bar [2].
        // [1] https://bugs.openjdk.java.net/browse/JDK-8264770
        // [2] https://github.com/FXMisc/Flowless/issues/97
        hbarValueListener = (observable, oldValue, newValue) -> {
            // Fix for update anomaly reported here https://github.com/FXMisc/RichTextFX/issues/1030
            if(DEBUG) System.out.println("hbarValueListener " + newValue);
            hPosEstimate.removeListener(hPosEstimateListener);
            hPosEstimate.setValue(newValue);
            hPosEstimate.addListener(hPosEstimateListener);
        };
        hbarValue.addListener(hbarValueListener);
        hPosEstimateListener = (observable, oldValue, newValue) -> {
            // Fix for update anomaly reported here https://github.com/FXMisc/RichTextFX/issues/1030
            if(DEBUG) System.out.println("hPosEstimateListener " + newValue);
            hbarValue.removeListener(hbarValueListener);
            hbarValue.setValue(newValue);
            hbarValue.addListener(hbarValueListener);
        };
        hPosEstimate.addListener(hPosEstimateListener);
        vbarValueListener = (observable, oldValue, newValue) -> {
            if(DEBUG) System.out.println("vbarValueListener " + newValue);
            vPosEstimate.removeListener(vPosEstimateListener);
            vPosEstimate.setValue(newValue);
            vPosEstimate.addListener(vPosEstimateListener);
        };
        vbarValue.addListener(vbarValueListener);
        vPosEstimateListener = (observable, oldValue, newValue) -> {
            if(DEBUG) System.out.println("vPosEstimateListener " + newValue);
            vbarValue.removeListener(vbarValueListener);
            vbarValue.setValue(newValue);
            vbarValue.addListener(vbarValueListener);
        };
        vPosEstimate.addListener(vPosEstimateListener);

        // scrollbar visibility
        hbarPolicy = Var.newSimpleVar(hPolicy);
        vbarPolicy = Var.newSimpleVar(vPolicy);

        Val<Double> layoutWidth = Val.map(layoutBoundsProperty(), Bounds::getWidth);
        Val<Double> layoutHeight = Val.map(layoutBoundsProperty(), Bounds::getHeight);
        Val<Boolean> needsHBar0 = Val.combine(
                content.totalWidthEstimateProperty(),
                layoutWidth,
                (cw, lw) -> cw > lw);
        Val<Boolean> needsVBar0 = Val.combine(
                content.totalHeightEstimateProperty(),
                layoutHeight,
                (ch, lh) -> ch > lh);
        Val<Boolean> needsHBar = Val.combine(
                needsHBar0,
                needsVBar0,
                content.totalWidthEstimateProperty(),
                vbar.widthProperty(),
                layoutWidth,
                (needsH, needsV, cw, vbw, lw) -> needsH || needsV && cw + vbw.doubleValue() > lw);
        Val<Boolean> needsVBar = Val.combine(
                needsVBar0,
                needsHBar0,
                content.totalHeightEstimateProperty(),
                hbar.heightProperty(),
                layoutHeight,
                (needsV, needsH, ch, hbh, lh) -> needsV || needsH && ch + hbh.doubleValue() > lh);

        Val<Boolean> shouldDisplayHorizontal = Val.flatMap(hbarPolicy, policy -> switch (policy) {
            case NEVER -> Val.constant(false);
            case ALWAYS -> Val.constant(true);
            default -> // AS_NEEDED
                    needsHBar;
        });
        Val<Boolean> shouldDisplayVertical = Val.flatMap(vbarPolicy, policy -> switch (policy) {
            case NEVER -> Val.constant(false);
            case ALWAYS -> Val.constant(true);
            default -> // AS_NEEDED
                    needsVBar;
        });

        // request layout later, because if currently in layout, the request is ignored
        shouldDisplayHorizontal.addListener(obs -> Platform.runLater(this::requestLayout));
        shouldDisplayVertical.addListener(obs -> Platform.runLater(this::requestLayout));

        hbar.visibleProperty().bind(shouldDisplayHorizontal);
        vbar.visibleProperty().bind(shouldDisplayVertical);

        contentFocusedListener = (obs, ov, nv) -> pseudoClassStateChanged(CONTENT_FOCUSED, nv);
        content.focusedProperty().addListener(contentFocusedListener);
        getChildren().addAll(content, hbar, vbar);
        getChildren().addListener((Observable obs) -> dispose());
    }

    /**
     * Constructs a VirtualizedScrollPane that only displays its horizontal and vertical scroll bars as needed
     */
    public MyVirtualScrollPane(@NamedArg("content") V content) {
        this(content, AS_NEEDED, AS_NEEDED);
    }

    /**
     * Does not unbind scrolling from Content before returning Content.
     * @return - the com.base.content
     */
    public V getContent() {
        return content;
    }

    /**
     * Unbinds scrolling from Content before returning Content.
     * @return - the com.base.content
     */
    public V removeContent() {
        getChildren().clear();
        return content;
    }

    private void dispose() {
        content.focusedProperty().removeListener(contentFocusedListener);
        hbarValue.removeListener(hbarValueListener);
        hPosEstimate.removeListener(hPosEstimateListener);
        vbarValue.removeListener(vbarValueListener);
        vPosEstimate.removeListener(vPosEstimateListener);
        unbindScrollBar(hbar);
        unbindScrollBar(vbar);
    }

    private void unbindScrollBar(ScrollBar bar) {
        bar.maxProperty().unbind();
        bar.unitIncrementProperty().unbind();
        bar.blockIncrementProperty().unbind();
        bar.visibleProperty().unbind();
    }

    @Override
    public Val<Double> totalWidthEstimateProperty() {
        return content.totalWidthEstimateProperty();
    }

    @Override
    public Val<Double> totalHeightEstimateProperty() {
        return content.totalHeightEstimateProperty();
    }

    @Override
    public Var<Double> estimatedScrollXProperty() {
        return content.estimatedScrollXProperty();
    }

    @Override
    public Var<Double> estimatedScrollYProperty() {
        return content.estimatedScrollYProperty();
    }

    @Override
    public void scrollXBy(double deltaX) {
        content.scrollXBy(deltaX);
    }

    @Override
    public void scrollYBy(double deltaY) {
        content.scrollYBy(deltaY);
    }

    @Override
    public void scrollXToPixel(double pixel) {
        content.scrollXToPixel(pixel);
    }

    @Override
    public void scrollYToPixel(double pixel) {
        content.scrollYToPixel(pixel);
    }

    @Override
    protected double computePrefWidth(double height) {
        return content.prefWidth(height);
    }

    @Override
    protected double computePrefHeight(double width) {
        return content.prefHeight(width);
    }

    @Override
    protected double computeMinWidth(double height) {
        return vbar.minWidth(-1);
    }

    @Override
    protected double computeMinHeight(double width) {
        return hbar.minHeight(-1);
    }

    @Override
    protected double computeMaxWidth(double height) {
        return content.maxWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width) {
        return content.maxHeight(width);
    }

    @Override
    protected void layoutChildren() {
        double layoutWidth = snapSizeX(getLayoutBounds().getWidth());
        double layoutHeight = snapSizeY(getLayoutBounds().getHeight());
        boolean vbarVisible = vbar.isVisible();
        boolean hbarVisible = hbar.isVisible();
        double vbarWidth = snapSizeY(vbarVisible ? vbar.prefWidth(-1) : 0);
        double hbarHeight = snapSizeX(hbarVisible ? hbar.prefHeight(-1) : 0);

        double w = layoutWidth - vbarWidth;
        double h = layoutHeight - hbarHeight;

        content.resize(w, h);

        hbar.setVisibleAmount(w);
        vbar.setVisibleAmount(h);

        if(vbarVisible) {
            vbar.resizeRelocate(layoutWidth - vbarWidth, 0, vbarWidth, h);
        }

        if(hbarVisible) {
            hbar.resizeRelocate(0, layoutHeight - hbarHeight, w, hbarHeight);
        }
        if(DEBUG) System.out.println("layout children " + w + " " + h + ", " + layoutWidth + " " + layoutHeight);
    }

    private void setHPosition(double pos) {
        double offset = scrollbarPositionToOffset(
                pos,
                content.getLayoutBounds().getWidth(),
                content.totalWidthEstimateProperty().getValue());
        if(DEBUG) System.out.println("set HPosition " + offset);
        content.estimatedScrollXProperty().setValue(offset);
    }

    private void setVPosition(double pos) {
        double offset = scrollbarPositionToOffset(
                pos,
                content.getLayoutBounds().getHeight(),
                content.totalHeightEstimateProperty().getValue());
        if(DEBUG) System.out.println("set VPosition " + offset);
        content.estimatedScrollYProperty().setValue(offset);
    }

    private static void setupUnitIncrement(ScrollBar bar) {
        bar.unitIncrementProperty().bind(new DoubleBinding() {
            { bind(bar.maxProperty(), bar.visibleAmountProperty()); }

            @Override
            protected double computeValue() {
                double max = bar.getMax();
                double visible = bar.getVisibleAmount();
                return max > visible
                        ? 16 / (max - visible) * max
                        : 0;
            }
        });
    }

    private static double offsetToScrollbarPositionH(
            double contentOffset, double viewportSize, double contentSize) {
       var r = contentSize > viewportSize
                ? contentOffset / (contentSize - viewportSize) * contentSize
                : 0;
       if(DEBUG) System.out.println("offsetToScrollbarPositionH " + r);
       return r;
    }

    private static double offsetToScrollbarPositionV(
            double contentOffset, double viewportSize, double contentSize) {
        var r = contentSize > viewportSize
                ? contentOffset / (contentSize - viewportSize) * contentSize
                : 0;
        if(DEBUG) System.out.println("offsetToScrollbarPositionV " + r + ", cntOffset " + contentOffset + ", viewportSize " + viewportSize + ", contentSize " + contentSize);
        return r;
    }

    private static double scrollbarPositionToOffset(
            double scrollbarPos, double viewportSize, double contentSize) {
        var r = contentSize > viewportSize
                ? scrollbarPos / contentSize * (contentSize - viewportSize)
                : 0;
        if(DEBUG) System.out.println("scrollbarPositionToOffset " + r + ", scrollbarPos " + scrollbarPos + ", viewportSize " + viewportSize + ", contentSize " + contentSize);
        return r;
    }
}

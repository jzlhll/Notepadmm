package com.allan.uilibs.richtexts;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.NamedArg;
import javafx.beans.Observable;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
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
    private static final boolean LAYOUT_DEBUG = Boolean.getBoolean("atools.editor.layout.debug");
    private static final PseudoClass CONTENT_FOCUSED = PseudoClass.getPseudoClass("com.base.content-focused");
    /** 小于该像素的位置变化视为同一位置：Flowless 总高度是估算值，抖动时不应回写滚动位置。 */
    private static final double POSITION_EPSILON = 0.5;
    /** 出现时不延迟，隐藏时保留少量视口余量，避免估算值在临界位置反复改变正文尺寸。 */
    private static final double SCROLLBAR_HIDE_MARGIN = 4.0;

    private final ScrollBar hbar;
    private final ScrollBar vbar;
    private final V content;
    private boolean scrollBarLayoutPending;
    private boolean updatingScrollBars;
    private boolean disposed;
    private final Runnable scrollBarLayoutPulse = this::updateScrollBarLayout;
    private final InvalidationListener scrollBarMetricsChanged = obs -> scheduleScrollBarLayout();
    private final ChangeListener<Boolean> contentFocusedListener;
    private final ChangeListener<Double> hbarValueListener;
    private ChangeListener<Double> hPosEstimateListener;
    private final ChangeListener<Double> vbarValueListener;
    private ChangeListener<Double> vPosEstimateListener;
    private final ChangeListener<Double> scrollXDebugListener;
    private final ChangeListener<Double> scrollYDebugListener;

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
        scrollXDebugListener = (observable, oldValue, newValue) ->
                System.out.println("Editor horizontal offset changed: from=" + oldValue + ", to=" + newValue
                        + getScrollLayoutDebugContext());
        scrollYDebugListener = (observable, oldValue, newValue) ->
                System.out.println("Editor vertical offset changed: from=" + oldValue + ", to=" + newValue
                        + getScrollLayoutDebugContext());
        // create scrollbars
        hbar = new ScrollBar();
        vbar = new ScrollBar();
        hbar.setOrientation(Orientation.HORIZONTAL);
        vbar.setOrientation(Orientation.VERTICAL);

        // scrollbar ranges
        hbar.setMin(0);
        vbar.setMin(0);

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
        // 水平滚动可视宽需扣除 CodeArea 自身的左右 padding（.editor-area 的 -fx-padding），
        // 与 totalWidthEstimate（cell 宽，不含 area padding）保持口径一致，否则横滑到底会差一段显示不全
        Region contentRegion = (Region) content;
        Val<Double> contentViewportWidth = Val.combine(
                Val.map(content.layoutBoundsProperty(), Bounds::getWidth),
                contentRegion.paddingProperty(),
                (w, pad) -> w - pad.getLeft() - pad.getRight());
        hPosEstimate = Val.combine(
                        content.estimatedScrollXProperty(),
                        contentViewportWidth,
                        hbar.maxProperty(),
                        (offset, viewportSize, contentSize) ->
                                offsetToScrollbarPositionH(offset, viewportSize, contentSize.doubleValue()))
                .asVar(this::setHPosition);
        vPosEstimate = Val.combine(
                        content.estimatedScrollYProperty(),
                        Val.map(content.layoutBoundsProperty(), Bounds::getHeight),
                        vbar.maxProperty(),
                        (offset, viewportSize, contentSize) ->
                                offsetToScrollbarPositionV(offset, viewportSize, contentSize.doubleValue()))
                .orElseConst(0.0)
                .asVar(this::setVPosition);
        hbarValue = Var.doubleVar(hbar.valueProperty());
        vbarValue = Var.doubleVar(vbar.valueProperty());
        // The use of a pair of mirrored ChangeListener instead of a more natural bidirectional binding
        // here is a workaround following a change in JavaFX [1] which broke the behaviour of the scroll bar [2].
        // [1] https://bugs.openjdk.java.net/browse/JDK-8264770
        // [2] https://github.com/FXMisc/Flowless/issues/97
        hbarValueListener = (observable, oldValue, newValue) -> {
            if (updatingScrollBars) return;
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
            if (updatingScrollBars) return;
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

        // 段落替换时尺寸估算会暂时失效；只标记待更新，不能在通知中读取并改变滚动范围或显隐。
        content.totalWidthEstimateProperty().addListener(scrollBarMetricsChanged);
        content.totalHeightEstimateProperty().addListener(scrollBarMetricsChanged);
        layoutBoundsProperty().addListener(scrollBarMetricsChanged);
        hbarPolicy.addListener(scrollBarMetricsChanged);
        vbarPolicy.addListener(scrollBarMetricsChanged);
        vbar.widthProperty().addListener(scrollBarMetricsChanged);
        hbar.heightProperty().addListener(scrollBarMetricsChanged);
        hbar.setVisible(false);
        vbar.setVisible(false);
        sceneProperty().addListener((observable, previous, current) -> {
            if (previous != null) previous.removePostLayoutPulseListener(scrollBarLayoutPulse);
            if (LAYOUT_DEBUG) {
                content.estimatedScrollXProperty().removeListener(scrollXDebugListener);
                content.estimatedScrollYProperty().removeListener(scrollYDebugListener);
            }
            if (current != null && !disposed) {
                if (LAYOUT_DEBUG) {
                    content.estimatedScrollXProperty().addListener(scrollXDebugListener);
                    content.estimatedScrollYProperty().addListener(scrollYDebugListener);
                }
                current.addPostLayoutPulseListener(scrollBarLayoutPulse);
                scheduleScrollBarLayout();
            }
        });
        scheduleScrollBarLayout();

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
        disposed = true;
        if (getScene() != null) getScene().removePostLayoutPulseListener(scrollBarLayoutPulse);
        content.totalWidthEstimateProperty().removeListener(scrollBarMetricsChanged);
        content.totalHeightEstimateProperty().removeListener(scrollBarMetricsChanged);
        content.estimatedScrollXProperty().removeListener(scrollXDebugListener);
        content.estimatedScrollYProperty().removeListener(scrollYDebugListener);
        layoutBoundsProperty().removeListener(scrollBarMetricsChanged);
        hbarPolicy.removeListener(scrollBarMetricsChanged);
        vbarPolicy.removeListener(scrollBarMetricsChanged);
        vbar.widthProperty().removeListener(scrollBarMetricsChanged);
        hbar.heightProperty().removeListener(scrollBarMetricsChanged);
        content.focusedProperty().removeListener(contentFocusedListener);
        hbarValue.removeListener(hbarValueListener);
        hPosEstimate.removeListener(hPosEstimateListener);
        vbarValue.removeListener(vbarValueListener);
        vPosEstimate.removeListener(vPosEstimateListener);
        unbindScrollBar(hbar);
        unbindScrollBar(vbar);
    }

    private void scheduleScrollBarLayout() {
        if (disposed) return;
        scrollBarLayoutPending = true;
        // runLater 不保证已完成 CSS 和段落测量；统一在布局结束后读取估算并联合判定显隐。
        if (getScene() != null) Platform.requestNextPulse();
    }

    private void updateScrollBarLayout() {
        if (disposed || !scrollBarLayoutPending) return;
        scrollBarLayoutPending = false;
        var widthEstimate = content.totalWidthEstimateProperty().getValue();
        var heightEstimate = content.totalHeightEstimateProperty().getValue();
        // 没有可用估算时保留上次范围，避免暂时归零把滚动位置钳回文档开头。
        double contentWidth = widthEstimate != null && Double.isFinite(widthEstimate)
                ? Math.max(0, widthEstimate) : hbar.getMax();
        double contentHeight = heightEstimate != null && Double.isFinite(heightEstimate)
                ? Math.max(0, heightEstimate) : vbar.getMax();
        double layoutWidth = snapSizeX(getLayoutBounds().getWidth());
        double layoutHeight = snapSizeY(getLayoutBounds().getHeight());
        boolean horizontal = needsScrollBar(getHbarPolicy(), contentWidth, layoutWidth, hbar.isVisible());
        boolean vertical = needsScrollBar(getVbarPolicy(), contentHeight, layoutHeight, vbar.isVisible());
        if (horizontal) {
            vertical |= needsScrollBar(getVbarPolicy(), contentHeight,
                    layoutHeight - snapSizeY(hbar.prefHeight(-1)), vbar.isVisible());
        }
        if (vertical) {
            horizontal |= needsScrollBar(getHbarPolicy(), contentWidth,
                    layoutWidth - snapSizeX(vbar.prefWidth(-1)), hbar.isVisible());
        }
        boolean visibilityChanged = horizontal != hbar.isVisible() || vertical != vbar.isVisible();
        // 范围更新可能让 ScrollBarSkin 调整 value；这不是用户滚动，不能反向移动正文。
        updatingScrollBars = true;
        try {
            hbar.setMax(contentWidth);
            vbar.setMax(contentHeight);
            hbar.setVisible(horizontal);
            vbar.setVisible(vertical);
        } finally {
            updatingScrollBars = false;
        }
        if (visibilityChanged) {
            if (LAYOUT_DEBUG) System.out.println("Editor scrollbars changed: viewport=" + layoutWidth + "x" + layoutHeight
                    + ", content=" + contentWidth + "x" + contentHeight + ", horizontal=" + horizontal
                    + ", vertical=" + vertical + ", scroll=" + content.estimatedScrollXProperty().getValue()
                    + "," + content.estimatedScrollYProperty().getValue() + getScrollLayoutDebugContext());
            requestLayout();
            Platform.requestNextPulse();
        }
    }

    private static boolean needsScrollBar(ScrollPane.ScrollBarPolicy policy, double contentSize,
                                         double viewportSize, boolean visible) {
        return policy == ScrollPane.ScrollBarPolicy.ALWAYS
                || policy == AS_NEEDED && contentSize > 0
                && contentSize > viewportSize - (visible ? SCROLLBAR_HIDE_MARGIN : 0);
    }

    protected String getScrollLayoutDebugContext() {
        return "";
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
        double vbarWidth = snapSizeX(vbarVisible ? vbar.prefWidth(-1) : 0);
        double hbarHeight = snapSizeY(hbarVisible ? hbar.prefHeight(-1) : 0);

        double w = layoutWidth - vbarWidth;
        double h = layoutHeight - hbarHeight;

        updatingScrollBars = true;
        try {
            content.resize(w, h);

            hbar.setVisibleAmount(w);
            vbar.setVisibleAmount(h);

            if(vbarVisible) {
                vbar.resizeRelocate(layoutWidth - vbarWidth, 0, vbarWidth, h);
            }

            if(hbarVisible) {
                hbar.resizeRelocate(0, layoutHeight - hbarHeight, w, hbarHeight);
            }
        } finally {
            updatingScrollBars = false;
        }
        if(DEBUG) System.out.println("layout children " + w + " " + h + ", " + layoutWidth + " " + layoutHeight);
    }

    private void setHPosition(double pos) {
        double offset = scrollbarPositionToOffset(
                pos,
                getContentViewportWidth(),
                hbar.getMax());
        if(DEBUG) System.out.println("set HPosition " + offset);
        Double current = content.estimatedScrollXProperty().getValue();
        if (current != null && Math.abs(offset - current) < POSITION_EPSILON) return;
        content.estimatedScrollXProperty().setValue(offset);
    }

    // CodeArea 内容区的净可视宽：总宽扣除自身左右 padding，与 totalWidthEstimate 口径一致
    private double getContentViewportWidth() {
        Region r = (Region) content;
        Insets ins = r.getInsets();
        return r.getLayoutBounds().getWidth() - ins.getLeft() - ins.getRight();
    }

    private void setVPosition(double pos) {
        double offset = scrollbarPositionToOffset(
                pos,
                content.getLayoutBounds().getHeight(),
                vbar.getMax());
        if(DEBUG) System.out.println("set VPosition " + offset);
        // 文档总高度是 Flowless 的估算值，编辑时会小幅抖动；滚动范围一变动这条链就会把同一个位置
        // 反复写回 estimatedScrollY，每次写回都触发一次列表重新布局，表现为正文整篇闪烁。
        Double current = content.estimatedScrollYProperty().getValue();
        if (current != null && Math.abs(offset - current) < POSITION_EPSILON) return;
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

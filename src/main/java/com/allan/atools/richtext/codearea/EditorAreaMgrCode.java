package com.allan.atools.richtext.codearea;

import com.allan.atools.UIContext;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperAbstract;
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperImplMarkdown;
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownAstCache;
import com.allan.atools.threads.ClosedDroppedHandler;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.allan.baseparty.Action0;
import com.allan.baseparty.handler.HandlerThread;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.util.Duration;
import org.commonmark.node.Node;
import org.reactfx.Subscription;

import java.io.File;
import java.awt.Desktop;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public final class EditorAreaMgrCode extends EditorAreaMgr {
    private static final long STYLE_INTERVAL_MS = 120;
    static boolean sJavaKeywordCssFileLoad = false;
    private static volatile ClosedDroppedHandler sStylerHandler;

    private EditorKeywordHelperAbstract mKeywordHelper;
    private final MarkdownAstCache markdownAstCache = new MarkdownAstCache();
    private final AtomicLong styleRequestId = new AtomicLong();
    private final PauseTransition styleDelay = new PauseTransition();
    private Subscription styleTextSubscription;
    private volatile Runnable pendingStyleTask;
    private SearchParams latestTemporaryText;
    private SearchParams latestSearchText;
    private Action0 latestEndCallback;
    private boolean styleDirty;
    private boolean styleRunning;
    private long runningStyleRequestId;
    private volatile long styleOptionsVersion;
    private int runningStablePrefixLimit;
    private long lastStyleStartedAt;

    EditorAreaMgrCode(EditorArea area, File sourceFile, Tab tab,
                      EditorDocumentState documentState) {
        super(area, sourceFile, tab, documentState);
        styleDelay.setOnFinished(event -> startLatestStyle());
        mKeywordHelper = createKeywordHelper(sourceFile);
        ensureKeywordStylesheet(mKeywordHelper);
        if (mKeywordHelper != null) {
            bindStyleTextChanges();
            requestStyle(null, null, null, true);
        }
    }

    private static ClosedDroppedHandler stylerHandler() {
        if (sStylerHandler == null) {
            synchronized (EditorAreaMgrCode.class) {
                if (sStylerHandler == null) {
                    var thread = new HandlerThread("code-styler");
                    thread.start();
                    sStylerHandler = new ClosedDroppedHandler(thread.getLooper());
                }
            }
        }
        return sStylerHandler;
    }

    @Override
    public boolean isEditorCodeMode() {
        return mKeywordHelper != null;
    }

    public void bindKeywordHelper(File sourceFile) {
        resetStyleScheduler();
        mKeywordHelper = createKeywordHelper(sourceFile);
        ensureKeywordStylesheet(mKeywordHelper);
        bindStyleTextChanges();
        if (mKeywordHelper == null) {
            var area = getArea();
            if (area != null && area.getLength() > 0) {
                area.setStyle(0, area.getLength(), area.getInitialTextStyle());
            }
            return;
        }
        requestStyle(null, null, null, true);
    }

    private void bindStyleTextChanges() {
        if (styleTextSubscription != null) {
            styleTextSubscription.unsubscribe();
            styleTextSubscription = null;
        }
        var area = getArea();
        if (mKeywordHelper != null && area != null) {
            styleTextSubscription = area.plainTextChanges().subscribe(change -> {
                if (styleRunning && change.getPosition() < runningStablePrefixLimit) {
                    runningStablePrefixLimit = change.getPosition();
                }
                styleDirty = true;
                scheduleLatestStyle(false);
            });
        }
    }

    private EditorKeywordHelperAbstract createKeywordHelper(File sourceFile) {
        var helper = EditorKeywordHelperFactory.create(sourceFile);
        if (helper instanceof EditorKeywordHelperImplMarkdown markdownHelper) {
            markdownHelper.setAstCache(markdownAstCache);
        }
        return helper;
    }

    public Node parseMarkdown(String text) {
        return markdownAstCache.parse(text);
    }

    public void openMarkdownLinkAt(int position) {
        if (!(mKeywordHelper instanceof EditorKeywordHelperImplMarkdown helper) || isDestroyed()) {
            return;
        }
        String text = getArea().getText();
        ThreadUtils.execute(() -> {
            try {
                String destination = helper.findLinkDestination(text, position);
                if (destination == null) {
                    return;
                }
                var bytes = destination.getBytes(StandardCharsets.UTF_8);
                var encoded = new StringBuilder(bytes.length);
                // 保留 URL 分隔符与已有百分号编码，其余字符按 UTF-8 编码。
                for (int index = 0; index < bytes.length; index++) {
                    int value = bytes[index] & 0xff;
                    if (value == '%' && index + 2 < bytes.length
                            && Character.digit((char) bytes[index + 1], 16) >= 0
                            && Character.digit((char) bytes[index + 2], 16) >= 0) {
                        encoded.append('%').append((char) bytes[++index]).append((char) bytes[++index]);
                    } else if (value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                            || value >= '0' && value <= '9' || "-._~:/?#[]@!$&'()*+,;=".indexOf(value) >= 0) {
                        encoded.append((char) value);
                    } else {
                        encoded.append('%').append(Character.forDigit(value >>> 4, 16))
                                .append(Character.forDigit(value & 0xf, 16));
                    }
                }
                var uri = new URI(encoded.toString());
                if ((!"http".equalsIgnoreCase(uri.getScheme())
                        && !"https".equalsIgnoreCase(uri.getScheme())) || uri.getRawAuthority() == null) {
                    return;
                }
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Log.e("open markdown link: system browser is unavailable");
                    return;
                }
                Desktop.getDesktop().browse(uri);
            } catch (Exception e) {
                Log.e("open markdown link failed", e);
            }
        });
    }

    private static void ensureKeywordStylesheet(EditorKeywordHelperAbstract helper) {
        if (sJavaKeywordCssFileLoad || helper == null) {
            return;
        }
        sJavaKeywordCssFileLoad = true;
        try {
            var url = ResLocation.getURLByRealPath(ResLocation.getRealPath("css", "editor_keywords.css"));
            UIContext.mainController.getStage().getScene().getStylesheets().add(url.toExternalForm());
        } catch (MalformedURLException e) {
            Log.e("load editor keyword stylesheet failed", e);
        }
    }

    @Override
    public void trigger(SearchParams temporaryText, SearchParams searchText, Action0 endSetStyleCallback) {
        requestStyle(temporaryText, searchText, endSetStyleCallback, true);
    }

    private void requestStyle(SearchParams temporaryText, SearchParams searchText,
                              Action0 endSetStyleCallback, boolean immediate) {
        SearchParams temporaryCopy = temporaryText == null ? null : temporaryText.copy();
        SearchParams searchCopy = searchText == null ? null : searchText.copy();
        Runnable action = () -> {
            if (mKeywordHelper == null || isDestroyed()) {
                return;
            }
            latestTemporaryText = temporaryCopy;
            latestSearchText = searchCopy;
            latestEndCallback = endSetStyleCallback;
            styleOptionsVersion++;
            styleDirty = true;
            scheduleLatestStyle(immediate);
        };
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void scheduleLatestStyle(boolean immediate) {
        if (!styleDirty || styleRunning || mKeywordHelper == null || isDestroyed()) {
            return;
        }
        long elapsed = (System.nanoTime() - lastStyleStartedAt) / 1_000_000;
        if (immediate || lastStyleStartedAt == 0 || elapsed >= STYLE_INTERVAL_MS) {
            startLatestStyle();
            return;
        }
        styleDelay.setDuration(Duration.millis(STYLE_INTERVAL_MS - elapsed));
        styleDelay.playFromStart();
    }

    private void startLatestStyle() {
        if (!styleDirty || styleRunning || mKeywordHelper == null || isDestroyed()) {
            return;
        }
        if (disableStylerIfNeeded(latestEndCallback)) {
            styleDirty = false;
            latestEndCallback = null;
            return;
        }
        EditorArea area = (EditorArea) getArea();
        if (area == null) {
            return;
        }
        styleDelay.stop();
        styleDirty = false;
        styleRunning = true;
        lastStyleStartedAt = System.nanoTime();
        long requestId = styleRequestId.incrementAndGet();
        runningStyleRequestId = requestId;
        long optionsVersion = styleOptionsVersion;
        long contentVersion = getContentVersion();
        String text = area.getText();
        runningStablePrefixLimit = text.length();
        var currentSpans = area.getStyleSpans(0, text.length());
        var temporaryText = latestTemporaryText;
        var searchText = latestSearchText;
        var endCallback = latestEndCallback;
        latestEndCallback = null;
        var helper = mKeywordHelper;

        Runnable task = () -> {
            EditorKeywordHelperAbstract.StyleUpdate update = null;
            try {
                if (canComputeStyle(requestId, optionsVersion, contentVersion, helper)) {
                    update = helper.computeStyleUpdate(text, temporaryText, searchText, currentSpans,
                            () -> canComputeStyle(requestId, optionsVersion, contentVersion, helper));
                }
            } catch (RuntimeException e) {
                Log.e("Code styler failed", e);
            }
            var result = update;
            Platform.runLater(() -> finishStyle(
                    requestId, optionsVersion, contentVersion, text,
                    helper, area, result, endCallback));
        };
        pendingStyleTask = task;
        stylerHandler().post(task);
    }

    private void finishStyle(long requestId, long optionsVersion, long contentVersion, String text,
                             EditorKeywordHelperAbstract helper, EditorArea area,
                             EditorKeywordHelperAbstract.StyleUpdate update, Action0 endCallback) {
        if (requestId != runningStyleRequestId) {
            return;
        }
        int stablePrefixLimit = runningStablePrefixLimit;
        pendingStyleTask = null;
        styleRunning = false;
        runningStyleRequestId = 0;
        runningStablePrefixLimit = 0;
        boolean alive = isStyleTaskAlive(requestId, optionsVersion, helper);
        boolean contentCurrent = contentVersion == getContentVersion();
        if (alive && update != null && update.spans() != null) {
            if (contentCurrent) {
                area.setStyleSpans(update.start(), update.spans());
            } else if (helper instanceof EditorKeywordHelperImplMarkdown) {
                applyStablePrefix(area, text, stablePrefixLimit, update);
            }
        }
        if (alive && contentCurrent && endCallback != null) {
            endCallback.invoke();
        }
        if (styleDirty) {
            scheduleLatestStyle(false);
        }
    }

    private void applyStablePrefix(EditorArea area, String text, int prefixLimit,
                                   EditorKeywordHelperAbstract.StyleUpdate update) {
        int stableEnd = stableMarkdownBoundary(text, prefixLimit);
        int applyEnd = Math.min(stableEnd, update.start() + update.spans().length());
        if (applyEnd > update.start()) {
            area.setStyleSpans(update.start(),
                    update.spans().subView(0, applyEnd - update.start()));
        }
    }

    private static int stableMarkdownBoundary(String text, int prefixLimit) {
        int end = Math.min(prefixLimit, text.length());
        for (int index = end - 1; index > 0; index--) {
            if (text.charAt(index) != '\n') {
                continue;
            }
            int previous = index - 1;
            if (text.charAt(previous) == '\r') {
                previous--;
            }
            if (previous >= 0 && text.charAt(previous) == '\n') {
                return index + 1;
            }
        }
        return 0;
    }

    private boolean isStyleTaskAlive(long requestId, long optionsVersion,
                                     EditorKeywordHelperAbstract helper) {
        return requestId == styleRequestId.get()
                && optionsVersion == styleOptionsVersion
                && helper == mKeywordHelper
                && !isRealtimeProcessingLimitReached()
                && !isDestroyed();
    }

    private boolean canComputeStyle(long requestId, long optionsVersion, long contentVersion,
                                    EditorKeywordHelperAbstract helper) {
        return isStyleTaskAlive(requestId, optionsVersion, helper)
                && (helper instanceof EditorKeywordHelperImplMarkdown
                || contentVersion == getContentVersion());
    }

    private void resetStyleScheduler() {
        styleDelay.stop();
        styleRequestId.incrementAndGet();
        Runnable task = pendingStyleTask;
        var handler = sStylerHandler;
        if (task != null && handler != null) {
            handler.removeCallback(task);
        }
        pendingStyleTask = null;
        latestTemporaryText = null;
        latestSearchText = null;
        latestEndCallback = null;
        styleDirty = false;
        styleRunning = false;
        runningStyleRequestId = 0;
        styleOptionsVersion++;
        runningStablePrefixLimit = 0;
        lastStyleStartedAt = 0;
    }

    @Override
    public void destroy() {
        if (styleTextSubscription != null) {
            styleTextSubscription.unsubscribe();
            styleTextSubscription = null;
        }
        resetStyleScheduler();
        styleDelay.setOnFinished(null);
        super.destroy();
    }
}

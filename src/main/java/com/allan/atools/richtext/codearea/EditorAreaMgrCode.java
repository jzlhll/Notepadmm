package com.allan.atools.richtext.codearea;

import com.allan.atools.UIContext;
import com.allan.atools.MarkdownThemes;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperAbstract;
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperImplMarkdown;
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownAstCache;
import com.allan.atools.richtext.codearea.keywordhelper.MarkdownStructureSnapshot;
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
    private long lastStyleStartedAt;
    private MarkdownStructureSnapshot currentMarkdownSnapshot;
    private long markdownSnapshotVersion = -1L;

    EditorAreaMgrCode(EditorArea area, File sourceFile, Tab tab,
                      EditorDocumentState documentState) {
        super(area, sourceFile, tab, documentState);
        styleDelay.setOnFinished(event -> startLatestStyle());
        mKeywordHelper = createKeywordHelper(sourceFile);
        area.getStyleClass().remove("markdown-editor");
        if (mKeywordHelper instanceof EditorKeywordHelperImplMarkdown) area.getStyleClass().add("markdown-editor");
        ensureKeywordStylesheet(mKeywordHelper);
        MarkdownThemes.attachEditor(area);
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
        if (getArea() instanceof EditorArea editorArea) editorArea.getMarkdownPresentation().clear();
        mKeywordHelper = createKeywordHelper(sourceFile);
        getArea().getStyleClass().remove("markdown-editor");
        if (mKeywordHelper instanceof EditorKeywordHelperImplMarkdown) getArea().getStyleClass().add("markdown-editor");
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
                if (mKeywordHelper instanceof EditorKeywordHelperImplMarkdown && area instanceof EditorArea editorArea) {
                    editorArea.getMarkdownPresentation().onTextChanged(change.getPosition(), change.getRemoved(), change.getInserted());
                }
                styleDirty = true;
                scheduleLatestStyle(false);
            });
        }
    }

    private EditorKeywordHelperAbstract createKeywordHelper(File sourceFile) {
        var helper = EditorKeywordHelperFactory.create(sourceFile != null ? sourceFile : new File(getDocumentState().getDisplayName()));
        if (helper instanceof EditorKeywordHelperImplMarkdown markdownHelper) {
            markdownHelper.setAstCache(markdownAstCache);
        }
        return helper;
    }

    public MarkdownStructureSnapshot markdownSnapshot(String text) {
        return markdownAstCache.snapshot(text);
    }

    public MarkdownStructureSnapshot currentMarkdownSnapshot() {
        return markdownSnapshotVersion == getContentVersion() ? currentMarkdownSnapshot : null;
    }

    public void openMarkdownLinkAt(int position) {
        if (!(mKeywordHelper instanceof EditorKeywordHelperImplMarkdown helper) || isDestroyed()) return;
        String text = getArea().getText();
        ThreadUtils.execute(() -> {
            String destination = helper.findLinkDestination(text, position);
            if (destination != null) openMarkdownDestination(destination);
        });
    }

    public void openMarkdownDestination(String destination) {
        if (isDestroyed()) return;
        ThreadUtils.execute(() -> {
            try {
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
                if (uri.getScheme() == null || "file".equalsIgnoreCase(uri.getScheme())
                        || ResLocation.isWindow && uri.getScheme().length() == 1) {
                    Platform.runLater(() -> {
                        if (!isDestroyed() && getArea() instanceof EditorArea area) MarkdownNavigation.open(area, uri);
                    });
                    return;
                }
                if ("mailto".equalsIgnoreCase(uri.getScheme())) {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) Desktop.getDesktop().mail(uri);
                    return;
                }
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

    public void refreshMarkdownPresentation() {
        requestStyle(latestTemporaryText, latestSearchText, null, true);
    }

    /** 正文订阅已负责版本刷新，底部搜索只在条件改变时重新计算样式。 */
    public void triggerSearch(SearchParams temporaryText, SearchParams searchText, Action0 endCallback) {
        if (sameSearch(latestTemporaryText, temporaryText) && sameSearch(latestSearchText, searchText)) {
            if (endCallback != null) endCallback.invoke();
            return;
        }
        trigger(temporaryText, searchText, endCallback);
    }

    private static boolean sameSearch(SearchParams first, SearchParams second) {
        boolean firstEmpty = first == null || first.words == null || first.words.isEmpty();
        boolean secondEmpty = second == null || second.words == null || second.words.isEmpty();
        return firstEmpty || secondEmpty ? firstEmpty == secondEmpty : first.isSameGeneric(second)
                && java.util.Objects.equals(first.textColor, second.textColor)
                && java.util.Objects.equals(first.bgColor, second.bgColor)
                && first.highLight == second.highLight && first.enable == second.enable;
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
        var styledDocument = area.getContent().snapshot();
        var temporaryText = latestTemporaryText;
        var searchText = latestSearchText;
        var endCallback = latestEndCallback;
        latestEndCallback = null;
        var helper = mKeywordHelper;
        boolean renderMarkdown = helper instanceof EditorKeywordHelperImplMarkdown && area.getMarkdownPreviewEnabled();
        if (helper instanceof EditorKeywordHelperImplMarkdown markdownHelper) {
            markdownHelper.setPreviewEnabled(renderMarkdown);
        }

        Runnable task = () -> {
            EditorKeywordHelperAbstract.StyleUpdate update = null;
            MarkdownStructureSnapshot structure = null;
            try {
                if (canComputeStyle(requestId, optionsVersion, contentVersion, helper)) {
                    var currentSpans = styledDocument.getStyleSpans(0, text.length());
                    var syntaxSpans = helper instanceof EditorKeywordHelperImplMarkdown
                            ? MarkdownSyntaxPresentation.highlightingStyles(currentSpans) : currentSpans;
                    update = helper.computeStyleUpdate(text, temporaryText, searchText, syntaxSpans,
                            () -> canComputeStyle(requestId, optionsVersion, contentVersion, helper));
                    if (update != null && renderMarkdown
                            && canComputeStyle(requestId, optionsVersion, contentVersion, helper)) {
                        structure = markdownAstCache.snapshot(text);
                    }
                }
            } catch (RuntimeException e) {
                Log.e("Code styler failed", e);
            }
            var result = update;
            var resultStructure = structure;
            Platform.runLater(() -> area.runAfterMarkdownComposition(() -> area.getMarkdownSyntax().runAfterPointer(() -> finishStyle(
                    requestId, optionsVersion, contentVersion,
                    helper, area, result, resultStructure, endCallback))));
        };
        pendingStyleTask = task;
        stylerHandler().post(task);
    }

    private void finishStyle(long requestId, long optionsVersion, long contentVersion,
                             EditorKeywordHelperAbstract helper, EditorArea area,
                             EditorKeywordHelperAbstract.StyleUpdate update,
                             MarkdownStructureSnapshot structure, Action0 endCallback) {
        if (requestId != runningStyleRequestId) {
            return;
        }
        pendingStyleTask = null;
        styleRunning = false;
        runningStyleRequestId = 0;
        boolean alive = isStyleTaskAlive(requestId, optionsVersion, helper);
        boolean contentCurrent = contentVersion == getContentVersion();
        if (alive && update != null && contentCurrent) {
            if (structure != null) {
                currentMarkdownSnapshot = structure;
                markdownSnapshotVersion = contentVersion;
            }
            area.suspendVisibleParsWhileInvoke(() -> {
                if (update.spans() != null) area.setStyleSpans(update.start(), update.spans());
                if (structure != null) area.getMarkdownPresentation().apply(structure);
            });
        }
        if (alive && contentCurrent && endCallback != null) {
            endCallback.invoke();
        }
        if (styleDirty) {
            scheduleLatestStyle(false);
        }
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
                && contentVersion == getContentVersion();
    }

    private void resetStyleScheduler() {
        currentMarkdownSnapshot = null;
        markdownSnapshotVersion = -1L;
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

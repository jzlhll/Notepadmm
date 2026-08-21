package com.allan.atools.richtext.codearea;

import com.allan.atools.UIContext;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperAbstract;
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperImplMarkdown;
import com.allan.atools.threads.ClosedDroppedHandler;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.allan.baseparty.Action0;
import com.allan.baseparty.handler.HandlerThread;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import org.fxmisc.richtext.model.StyleSpans;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

public final class EditorAreaMgrCode extends EditorAreaMgr {
    static boolean sJavaKeywordCssFileLoad = false;
    private static volatile ClosedDroppedHandler sMarkdownHandler;

    private final EditorKeywordHelperAbstract mKeywordHelper;
    private final EditorKeywordHelperImplMarkdown mMarkdownHelper;
    private final boolean mIsDropDown; //是否采用掉落为，父类的逻辑
    private final AtomicLong markdownRequestId = new AtomicLong();
    private volatile Runnable pendingMarkdownTask;

    EditorAreaMgrCode(EditorArea area, File sourceFile, Tab tab, boolean isFake) {
        this(EditorKeywordHelperFactory.create(sourceFile), area, sourceFile, tab, isFake);
    }

    private EditorAreaMgrCode(EditorKeywordHelperAbstract helper, EditorArea area, File sourceFile, Tab tab, boolean isFake) {
        super(area, sourceFile, tab, isFake);
        mIsDropDown = helper == null;
        if (!sJavaKeywordCssFileLoad && !mIsDropDown) {
            sJavaKeywordCssFileLoad = true;
            try {
                var url = ResLocation.getURLByRealPath(ResLocation.getRealPath("css", "editor_keywords.css"));
                UIContext.mainController.getStage().getScene().getStylesheets().add(url.toExternalForm());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        mKeywordHelper = helper;
        mMarkdownHelper = helper instanceof EditorKeywordHelperImplMarkdown markdownHelper ? markdownHelper : null;

        if(!mIsDropDown) trigger(null, null, null);
    }

    private static ClosedDroppedHandler markdownHandler() {
        if (sMarkdownHandler == null) {
            synchronized (EditorAreaMgrCode.class) {
                if (sMarkdownHandler == null) {
                    var thread = new HandlerThread("markdown-styler");
                    thread.start();
                    sMarkdownHandler = new ClosedDroppedHandler(thread.getLooper());
                }
            }
        }
        return sMarkdownHandler;
    }

    @Override
    public boolean isEditorCodeMode() {
        return !mIsDropDown;
    }

    @Override
    public boolean isMarkdownStyler() {
        return mMarkdownHelper != null;
    }

    public void invalidateMarkdownStyleRequest() {
        if (mMarkdownHelper != null) {
            beginMarkdownRequest();
        }
    }

    @Override
    public void trigger(SearchParams temporaryText, SearchParams searchText, Action0 endSetStyleCallback) {
        Log.d(getSourceFile() + " trigger");
        if (mMarkdownHelper != null) {
            long requestId = beginMarkdownRequest();
            Runnable captureAction = () -> captureAndScheduleMarkdown(
                    requestId, temporaryText, searchText, endSetStyleCallback);
            if (Platform.isFxApplicationThread()) {
                captureAction.run();
            } else {
                Platform.runLater(captureAction);
            }
            return;
        }
        if (disableStylerIfNeeded(endSetStyleCallback)) {
            return;
        }
        long contentVersion = getContentVersion();
        mKeywordHelper.triggerAllText(getArea(), temporaryText, searchText, endSetStyleCallback,
                () -> contentVersion == getContentVersion() && !isRealtimeProcessingLimitReached());
    }

    @Override
    public void triggerWithSnapshot(String text, long contentVersion,
                                    StyleSpans<Collection<String>> currentSpans,
                                    SearchParams temporaryText, SearchParams searchText,
                                    Action0 endSetStyleCallback) {
        if (mMarkdownHelper == null) {
            trigger(temporaryText, searchText, endSetStyleCallback);
            return;
        }
        long requestId = beginMarkdownRequest();
        scheduleMarkdown(requestId, text, contentVersion, currentSpans,
                temporaryText, searchText, endSetStyleCallback);
    }

    private long beginMarkdownRequest() {
        long requestId = markdownRequestId.incrementAndGet();
        Runnable task = pendingMarkdownTask;
        var handler = sMarkdownHandler;
        if (task != null && handler != null) {
            handler.removeCallback(task);
            pendingMarkdownTask = null;
        }
        return requestId;
    }

    private void captureAndScheduleMarkdown(long requestId, SearchParams temporaryText,
                                            SearchParams searchText, Action0 endSetStyleCallback) {
        if (requestId != markdownRequestId.get() || isDestroyed()) {
            return;
        }
        if (disableStylerIfNeeded(endSetStyleCallback)) {
            return;
        }
        long contentVersion = getContentVersion();
        var area = getArea();
        if (area == null) {
            return;
        }
        String text = area.getText();
        var currentSpans = area.getStyleSpans(0, text.length());
        scheduleMarkdown(requestId, text, contentVersion, currentSpans,
                temporaryText, searchText, endSetStyleCallback);
    }

    private void scheduleMarkdown(long requestId, String text, long contentVersion,
                                  StyleSpans<Collection<String>> currentSpans,
                                  SearchParams temporaryText, SearchParams searchText,
                                  Action0 endSetStyleCallback) {
        if (requestId != markdownRequestId.get()
                || contentVersion != getContentVersion() || isDestroyed()) {
            return;
        }
        if (disableStylerIfNeeded(endSetStyleCallback)) {
            return;
        }
        Runnable task = () -> {
            if (!isMarkdownRequestValid(requestId, contentVersion)) {
                return;
            }
            if (pendingMarkdownTask != null && requestId == markdownRequestId.get()) {
                pendingMarkdownTask = null;
            }
            var area = getArea();
            if (area == null) {
                return;
            }
            try {
                var update = mMarkdownHelper.computeStyleUpdate(text, temporaryText, searchText, currentSpans,
                        () -> isMarkdownRequestValid(requestId, contentVersion));
                if (update == null) {
                    return;
                }
                Platform.runLater(() -> {
                    if (!isMarkdownRequestValid(requestId, contentVersion)) {
                        return;
                    }
                    if (update.spans() != null) {
                        area.setStyleSpans(update.start(), update.spans());
                    }
                    if (endSetStyleCallback != null) {
                        endSetStyleCallback.invoke();
                    }
                });
            } catch (RuntimeException e) {
                Log.e("Markdown styler failed", e);
            }
        };
        pendingMarkdownTask = task;
        markdownHandler().post(task);
    }

    private boolean isMarkdownRequestValid(long requestId, long contentVersion) {
        return requestId == markdownRequestId.get()
                && contentVersion == getContentVersion()
                && !isRealtimeProcessingLimitReached()
                && !isDestroyed();
    }

    @Override
    public void destroy() {
        markdownRequestId.incrementAndGet();
        Runnable task = pendingMarkdownTask;
        var handler = sMarkdownHandler;
        if (task != null && handler != null) {
            handler.removeCallback(task);
        }
        pendingMarkdownTask = null;
        super.destroy();
    }
}

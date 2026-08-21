package com.allan.atools.richtext.codearea;

import com.allan.atools.UIContext;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.richtext.codearea.keywordhelper.EditorKeywordHelperAbstract;
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
    private static volatile ClosedDroppedHandler sStylerHandler;

    private final EditorKeywordHelperAbstract mKeywordHelper;
    private final boolean mIsDropDown; //是否采用掉落为，父类的逻辑
    private final AtomicLong styleRequestId = new AtomicLong();
    private volatile Runnable pendingStyleTask;

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

        if(!mIsDropDown) trigger(null, null, null);
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
        return !mIsDropDown;
    }

    public void invalidateStyleRequest() {
        if (mKeywordHelper != null) {
            beginStyleRequest();
        }
    }

    @Override
    public void trigger(SearchParams temporaryText, SearchParams searchText, Action0 endSetStyleCallback) {
        Log.d(getSourceFile() + " trigger");
        if (mKeywordHelper == null) {
            return;
        }
        long requestId = beginStyleRequest();
        Runnable captureAction = () -> captureAndScheduleStyle(
                requestId, temporaryText, searchText, endSetStyleCallback);
        if (Platform.isFxApplicationThread()) {
            captureAction.run();
        } else {
            Platform.runLater(captureAction);
        }
    }

    @Override
    public void triggerWithSnapshot(String text, long contentVersion,
                                    StyleSpans<Collection<String>> currentSpans,
                                    SearchParams temporaryText, SearchParams searchText,
                                    Action0 endSetStyleCallback) {
        if (mKeywordHelper == null) {
            return;
        }
        long requestId = beginStyleRequest();
        scheduleStyle(requestId, text, contentVersion, currentSpans,
                temporaryText, searchText, endSetStyleCallback);
    }

    private long beginStyleRequest() {
        long requestId = styleRequestId.incrementAndGet();
        Runnable task = pendingStyleTask;
        var handler = sStylerHandler;
        if (task != null && handler != null) {
            handler.removeCallback(task);
            pendingStyleTask = null;
        }
        return requestId;
    }

    private void captureAndScheduleStyle(long requestId, SearchParams temporaryText,
                                         SearchParams searchText, Action0 endSetStyleCallback) {
        if (requestId != styleRequestId.get() || isDestroyed()) {
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
        scheduleStyle(requestId, text, contentVersion, currentSpans,
                temporaryText, searchText, endSetStyleCallback);
    }

    private void scheduleStyle(long requestId, String text, long contentVersion,
                               StyleSpans<Collection<String>> currentSpans,
                               SearchParams temporaryText, SearchParams searchText,
                               Action0 endSetStyleCallback) {
        if (requestId != styleRequestId.get()
                || contentVersion != getContentVersion() || isDestroyed()) {
            return;
        }
        if (disableStylerIfNeeded(endSetStyleCallback)) {
            return;
        }
        Runnable task = () -> {
            if (!isStyleRequestValid(requestId, contentVersion)) {
                return;
            }
            if (pendingStyleTask != null && requestId == styleRequestId.get()) {
                pendingStyleTask = null;
            }
            var area = getArea();
            if (area == null) {
                return;
            }
            try {
                var update = mKeywordHelper.computeStyleUpdate(text, temporaryText, searchText, currentSpans,
                        () -> isStyleRequestValid(requestId, contentVersion));
                if (update == null) {
                    return;
                }
                Platform.runLater(() -> {
                    if (!isStyleRequestValid(requestId, contentVersion)) {
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
                Log.e("Code styler failed", e);
            }
        };
        pendingStyleTask = task;
        stylerHandler().post(task);
    }

    private boolean isStyleRequestValid(long requestId, long contentVersion) {
        return requestId == styleRequestId.get()
                && contentVersion == getContentVersion()
                && !isRealtimeProcessingLimitReached()
                && !isDestroyed();
    }

    @Override
    public void destroy() {
        styleRequestId.incrementAndGet();
        Runnable task = pendingStyleTask;
        var handler = sStylerHandler;
        if (task != null && handler != null) {
            handler.removeCallback(task);
        }
        pendingStyleTask = null;
        super.destroy();
    }
}

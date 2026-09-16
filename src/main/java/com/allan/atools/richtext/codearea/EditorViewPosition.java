package com.allan.atools.richtext.codearea;

import com.allan.atools.GlobalCfgStores;
import com.allan.atools.UIContext;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import org.reactfx.Subscription;

import java.util.HashMap;
import java.util.Map;

/** 按文件路径保存顶部行；每个新编辑器仅在首次展示时恢复一次。 */
public final class EditorViewPosition {
    private static final String KEY = "fileTopLines";
    private static final TypeToken<Map<String, Integer>> TYPE = new TypeToken<>() {};
    private final EditorArea area;
    private final Subscription viewportChanges;
    private int topLine;
    private boolean pendingRestore;
    private boolean restoreQueued;
    private boolean captureQueued;
    private boolean destroyed;

    public EditorViewPosition(EditorArea area) {
        this.area = area;
        var file = area.getEditor().getSourceFile();
        if (file != null) {
            var positions = GlobalCfgStores.recent().getObject(KEY, TYPE, Map.of());
            var saved = positions.get(file.toPath().toAbsolutePath().normalize().toString());
            if (saved != null && saved >= 0) {
                topLine = saved;
                pendingRestore = true;
            }
        }
        // 怀疑同步查询可见行会触发布局，再次发出 viewportDirty，导致 ReactFX 通知重入。
        // 延后并合并采样；保留最后可见行，避免标签隐藏后保存为零，后续可按复现结果调整。
        viewportChanges = area.viewportDirtyEvents().subscribe(event -> {
            if (destroyed || pendingRestore || captureQueued || UIContext.currentAreaProp.get() != area) {
                return;
            }
            captureQueued = true;
            Platform.runLater(() -> {
                try {
                    if (!destroyed && !pendingRestore && UIContext.currentAreaProp.get() == area
                            && !area.getVisibleParagraphs().isEmpty()) {
                        topLine = area.firstVisibleParToAllParIndex();
                    }
                } finally {
                    captureQueued = false;
                }
            });
        });
    }

    public void save() {
        var file = area.getEditor().getSourceFile();
        if (file == null || pendingRestore) {
            return;
        }
        if (UIContext.currentAreaProp.get() == area && !area.getVisibleParagraphs().isEmpty()) {
            topLine = area.firstVisibleParToAllParIndex();
        }
        if (topLine < 0) {
            return;
        }
        var positions = new HashMap<>(GlobalCfgStores.recent().getObject(KEY, TYPE, Map.of()));
        String path = file.toPath().toAbsolutePath().normalize().toString();
        if (!Integer.valueOf(topLine).equals(positions.put(path, topLine))) {
            GlobalCfgStores.recent().set(KEY, positions);
        }
    }

    public void restoreIfPending() {
        if (!pendingRestore || restoreQueued) {
            return;
        }
        restoreQueued = true;
        Platform.runLater(() -> {
            restoreQueued = false;
            if (destroyed || area.getEditor().isDestroyed() || UIContext.currentAreaProp.get() != area) {
                return;
            }
            area.applyCss();
            area.layout();
            int lastLine = area.getParagraphs().size() - 1;
            if (topLine > lastLine) {
                area.showParagraphAtBottom(lastLine);
            } else {
                area.showParagraphAtTop(topLine);
            }
            pendingRestore = false;
        });
    }

    public void destroy() {
        save();
        destroyed = true;
        viewportChanges.unsubscribe();
    }
}

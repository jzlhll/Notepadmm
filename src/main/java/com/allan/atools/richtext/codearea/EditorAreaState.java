package com.allan.atools.richtext.codearea;

import com.allan.atools.GlobalCfgStores;
import com.allan.atools.bean.EditorDocumentOptions;
import com.allan.atools.text.IEditorAreaState;
import com.allan.atools.utils.Log;
import com.allan.baseparty.utils.ReflectionUtils;
import com.google.gson.reflect.TypeToken;
import org.reactfx.collection.MemoizationList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class EditorAreaState implements IEditorAreaState {
    private static final String KEY_DOCUMENT_OPTIONS = "editorDocumentOptions";
    private static final int MAX_DOCUMENT_OPTIONS = 100;
    private static final TypeToken<List<EditorDocumentOptions>> TYPE_DOCUMENT_OPTIONS = new TypeToken<>() {};

    private final EditorArea area;
    private final EditorDocumentState documentState;
    public EditorAreaState(EditorArea area, EditorDocumentState documentState) {
        this.area = area;
        this.documentState = documentState;
        var file = documentState.getSourceFile();
        isWrap = file == null
                || file.getName().toLowerCase(Locale.ROOT).endsWith(".txt")
                || MarkdownEditorSupport.isMarkdownFile(file);
        restoreDocumentOptions();
        isWrap = supportsWrap() && (MarkdownEditorSupport.isMarkdownFile(documentState.getSourceFile()) || isWrap);
        area.setEditable(!isReadonly);
        area.setWrapText(isWrap);
        if (isWrap) forgetFlowCellSizes();
    }

    private boolean isReadonly = false;
    private boolean isWrap = false;

    @Override
    public void setFileEncoding(String fileEncoding) {
        documentState.setEncoding(fileEncoding);
    }

    @Override
    public String getFileEncoding() {return documentState.getEncoding();}

    @Override
    public boolean isCurrentReadonly() {
        return isReadonly;
    }

    @Override
    public void setCurrentReadonly(boolean readonly) {
        isReadonly = readonly;
        area.setEditable(!readonly);
        saveDocumentOptions();
    }

    @Override
    public boolean isWrap() {
        return isWrap;
    }

    @Override
    public boolean supportsWrap() {
        var file = documentState.getSourceFile();
        return file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".log");
    }

    @Override
    public void setWrap(boolean wrap) {
        isWrap = supportsWrap() && (MarkdownEditorSupport.isMarkdownFile(documentState.getSourceFile()) || wrap);
        if (area.isWrapText() != isWrap) {
            // 换行状态与尺寸缓存在同一次更新中切换，不让延迟布局使用新旧混合的尺寸。
            area.suspendVisibleParsWhileInvoke(() -> {
                area.setWrapText(isWrap);
                forgetFlowCellSizes();
            });
        }
        saveDocumentOptions();
    }

    private void restoreDocumentOptions() {
        var options = GlobalCfgStores.recent().getObject(
                KEY_DOCUMENT_OPTIONS, TYPE_DOCUMENT_OPTIONS, List.of());
        for (var option : options) {
            if (matches(option)) {
                isWrap = option.wrap();
                isReadonly = option.readonly();
                return;
            }
        }
    }

    void saveDocumentOptions() {
        var options = new ArrayList<>(GlobalCfgStores.recent().getObject(
                KEY_DOCUMENT_OPTIONS, TYPE_DOCUMENT_OPTIONS, List.of()));
        var sourcePath = documentState.getSourcePath();
        var sessionId = documentState.getSessionId();
        options.removeIf(option -> option == null
                || sourcePath != null && sourcePath.equals(option.file())
                || sessionId.equals(option.sessionId()));
        options.add(0, new EditorDocumentOptions(sourcePath, sessionId, isWrap,
                isReadonly));
        if (options.size() > MAX_DOCUMENT_OPTIONS) {
            options.subList(MAX_DOCUMENT_OPTIONS, options.size()).clear();
        }
        GlobalCfgStores.recent().set(KEY_DOCUMENT_OPTIONS, options);
    }

    private boolean matches(EditorDocumentOptions option) {
        if (option == null) {
            return false;
        }
        var sourcePath = documentState.getSourcePath();
        return sourcePath == null
                ? documentState.getSessionId().equals(option.sessionId())
                : sourcePath.equals(option.file());
    }

    private void forgetFlowCellSizes() {
        try {
            Object virtualFlow = ReflectionUtils.iteratorGetPrivateFieldValue(area, "virtualFlow");
            if (virtualFlow == null) return;
            Object sizeTracker = ReflectionUtils.iteratorGetPrivateFieldValue(virtualFlow, "sizeTracker");
            if (sizeTracker == null) return;
            var breadths = (MemoizationList<?>) ReflectionUtils.iteratorGetPrivateFieldValue(sizeTracker, "breadths");
            var lengths = (MemoizationList<?>) ReflectionUtils.iteratorGetPrivateFieldValue(sizeTracker, "lengths");
            if (breadths == null || lengths == null) return;
            int count = area.getParagraphs().size();
            // 按范围失效，避免逐段清除时反复发布部分更新的宽度和高度估算。
            breadths.forget(0, count);
            lengths.forget(0, count);
            area.estimatedScrollXProperty().setValue(0.0);
            area.requestLayout();
        } catch (Exception e) {
            Log.e("forget flow cell sizes failed", e);
        }
    }

    int currentCaretPos, selectedLength, selectLineCount, currentCaretColNum, currentCaretLineNum;

    public int getCurrentCaretPos() {
        return currentCaretPos;
    }

    public int getSelectLineCount() {return selectLineCount;}

    public int getSelectedLen() {
        return selectedLength;
    }

    public int getCurrentCaretColNum() {
        return currentCaretColNum;
    }

    public int getCurrentCaretLineNum() {
        return currentCaretLineNum;
    }
}

package com.allan.atools.richtext.codearea;

import com.allan.atools.GlobalCfgStores;
import com.allan.atools.SettingPreferences;
import com.allan.atools.bean.EditorDocumentOptions;
import com.allan.atools.text.IEditorAreaState;
import com.allan.atools.utils.Log;
import com.allan.baseparty.utils.ReflectionUtils;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class EditorAreaState implements IEditorAreaState {
    private static final String KEY_DOCUMENT_OPTIONS = "editorDocumentOptions";
    private static final int MAX_DOCUMENT_OPTIONS = 200;
    private static final TypeToken<List<EditorDocumentOptions>> TYPE_DOCUMENT_OPTIONS = new TypeToken<>() {};

    private final EditorArea area;
    private final EditorDocumentState documentState;
    public EditorAreaState(EditorArea area, EditorDocumentState documentState) {
        this.area = area;
        this.documentState = documentState;
        restoreDocumentOptions();
        isWrap = MarkdownEditorSupport.isMarkdownFile(documentState.getSourceFile()) || isWrap;
        area.setEditable(!isReadonly);
        area.setWrapText(isWrap);
        if (isWrap) {
            Platform.runLater(this::forgetFlowCellSizes);
        }
    }

    private boolean isReadonly = false;
    private boolean isWrap = false;
    // 文档没有独立配置时，使用全局“输入法中文标点”设置作为初始值
    private boolean isChinesePunctuation = SettingPreferences.getBoolean(SettingPreferences.editorChinesePunctuationKey);

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
    public boolean isChinesePunctuation() {
        return isChinesePunctuation;
    }

    @Override
    public void setChinesePunctuation(boolean chinesePunctuation) {
        isChinesePunctuation = chinesePunctuation;
        saveDocumentOptions();
    }

    @Override
    public void setWrap(boolean wrap) {
        isWrap = MarkdownEditorSupport.isMarkdownFile(documentState.getSourceFile()) || wrap;
        area.setWrapText(isWrap);
        // 切换 wrap 后，flowless 缓存的 cell 最小宽度(minBreadth)不会自动失效，导致 totalWidthEstimate
        // 滞后偏大、横向滚动条不消失。反射清除 SizeTracker 的尺寸备忘，强制下次 layout 按新 wrap 重算
        Platform.runLater(this::forgetFlowCellSizes);
        saveDocumentOptions();
    }

    private void restoreDocumentOptions() {
        var options = GlobalCfgStores.recent().getObject(
                KEY_DOCUMENT_OPTIONS, TYPE_DOCUMENT_OPTIONS, List.of());
        for (var option : options) {
            if (matches(option)) {
                isWrap = option.wrap();
                isReadonly = option.readonly();
                isChinesePunctuation = option.chinesePunctuation();
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
                isReadonly, isChinesePunctuation));
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
            Method forget = sizeTracker.getClass().getDeclaredMethod("forgetSizeOf", int.class);
            forget.setAccessible(true);
            int count = area.getParagraphs().size();
            for (int i = 0; i < count; i++) {
                forget.invoke(sizeTracker, i);
            }
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

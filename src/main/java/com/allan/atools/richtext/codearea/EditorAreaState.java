package com.allan.atools.richtext.codearea;

import com.allan.atools.text.IEditorAreaState;
import com.allan.atools.utils.Log;
import com.allan.baseparty.utils.ReflectionUtils;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import java.lang.reflect.Method;

final class EditorAreaState implements IEditorAreaState {
    private static final PseudoClass WRAPPED = PseudoClass.getPseudoClass("wrapped");

    private EditorArea area;
    public EditorAreaState(EditorArea area) {
        this.area = area;
    }

    private boolean isReadonly = false;
    private boolean isWrap = false;

    private String fileEncoding;
    @Override
    public void setFileEncoding(String fileEncoding) {this.fileEncoding = fileEncoding;}

    @Override
    public String getFileEncoding() {return fileEncoding;}

    @Override
    public boolean isCurrentReadonly() {
        return isReadonly;
    }

    @Override
    public void setCurrentReadonly(boolean readonly) {
        isReadonly = readonly;
        area.setEditable(!readonly);
    }

    @Override
    public boolean isWrap() {
        return isWrap;
    }

    @Override
    public void setWrap(boolean wrap) {
        isWrap = wrap;
        area.pseudoClassStateChanged(WRAPPED, wrap);
        area.setWrapText(wrap);
        // 切换 wrap 后，flowless 缓存的 cell 最小宽度(minBreadth)不会自动失效，导致 totalWidthEstimate
        // 滞后偏大、横向滚动条不消失。反射清除 SizeTracker 的尺寸备忘，强制下次 layout 按新 wrap 重算
        Platform.runLater(this::forgetFlowCellSizes);
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

package com.allan.atools.richtext.codearea;

import com.allan.atools.text.IEditorAreaState;

final class EditorAreaState implements IEditorAreaState {
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
        area.setWrapText(wrap);
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

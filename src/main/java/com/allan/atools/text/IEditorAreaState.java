package com.allan.atools.text;

public interface IEditorAreaState {
    boolean isCurrentReadonly();
    void setCurrentReadonly(boolean readonly);

    boolean isWrap();
    boolean supportsWrap();
    void setWrap(boolean wrap);

    boolean isChinesePunctuation();
    void setChinesePunctuation(boolean chinesePunctuation);

    void setFileEncoding(String fileEncoding);

    String getFileEncoding();


    int getCurrentCaretPos();

    int getSelectLineCount();

    int getSelectedLen();

    int getCurrentCaretColNum();

    int getCurrentCaretLineNum();
}

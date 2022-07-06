package com.allan.atools.text;

public interface IEditorAreaEx<PS, SEG, S> extends IAreaEx<PS, SEG, S> {
    boolean isEditorCodeFind();
    void rename();
    void resetText(String text);
    boolean canClosed();
    void removeStageFocus();
    void addStageFocus();
    void checkFileIfChanged();
}

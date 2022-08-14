package com.allan.atools.tools.modulenotepad.base;

import com.allan.atools.keyevent.IKeyDispatcherLeaf;
import com.allan.atools.richtext.codearea.EditorArea;
import javafx.scene.control.Tab;

import java.io.File;

public interface INotepadMainAreaManager extends INotepadManager, ITextMultiFindAndReplace, IKeyDispatcherLeaf {
    boolean isCurrentAreaOnFront(EditorArea area);
    void bringAreaToFront(EditorArea area);

    String getCurrentTabFilePath();
    String[] getAllTabsFilePaths();

    EditorArea getAreaByFilePath(File fil);
    EditorArea getAreaByFilePath(String filePath);
    Tab getTabByCodeArea(EditorArea area);
    EditorArea[] getAllAreas();

    void openFile(File textFile, boolean checkAlreadyHasFile, boolean toFront);
    void newFakeFile(File fakeFile);

    void reOpenCurrentFile(Tab tab, File file, String forceEncoding);

    void saveListFilePaths();

    void saveUnSaved();

    void removeAllOtherTabs(Tab tab);

    boolean hasAnyUnSaved();
}

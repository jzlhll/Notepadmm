package com.allan.atools.tools.modulenotepad.base;

import com.allan.atools.keyevent.IKeyDispatcherLeaf;
import com.allan.atools.richtext.codearea.EditorAreaImpl;
import javafx.scene.control.Tab;

import java.io.File;

public interface INotepadMainAreaManager extends INotepadManager, ITextMultiFindAndReplace, IKeyDispatcherLeaf {
    boolean isCurrentAreaOnFront(EditorAreaImpl area);
    void bringAreaToFront(EditorAreaImpl area);

    String getCurrentTabFilePath();
    String[] getAllTabsFilePaths();

    EditorAreaImpl getAreaByFilePath(File fil);
    EditorAreaImpl getAreaByFilePath(String filePath);
    Tab getTabByCodeArea(EditorAreaImpl area);
    EditorAreaImpl[] getAllAreas();

    void openFile(File textFile, boolean checkAlreadyHasFile, boolean toFront);
    void newFakeFile(File fakeFile);

    void reOpenCurrentFile(Tab tab, File file, String forceEncoding);

    void saveListFilePaths();

    void saveUnSaved();

    void removeAllOtherTabs(Tab tab);

    boolean hasAnyUnSaved();
}

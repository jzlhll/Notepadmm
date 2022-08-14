package com.allan.atools.richtext.codearea;

import com.allan.atools.UIContext;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import javafx.scene.control.Tab;

import java.io.File;
import java.net.MalformedURLException;

public final class EditorAreaMgrCode extends EditorAreaMgr {
    static boolean sJavaKeywordCssFileLoad = false;

    private final EditorKeywordHelperAbstract mKeywordHelper;
    public EditorKeywordHelperAbstract getHelper() {return mKeywordHelper;}
    private final boolean mIsDropDown; //是否采用掉落为，父类的逻辑

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

        if(!mIsDropDown) trigger(null, null);
    }

    @Override
    public boolean isEditorCodeFind() {
        return !mIsDropDown;
    }

    public void trigger(SearchParams temporaryText, SearchParams searchText) {
        Log.d(getSourceFile() + " trigger");
        mKeywordHelper.triggerAllText(getArea(), temporaryText, searchText);
    }
}


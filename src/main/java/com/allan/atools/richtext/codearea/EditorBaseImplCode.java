package com.allan.atools.richtext.codearea;

import com.allan.atools.UIContext;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import javafx.scene.control.Tab;

import java.io.File;
import java.net.MalformedURLException;

public final class EditorBaseImplCode extends EditorBase {
    static boolean sJavaKeywordCssFileLoad = false;

    private final AbstractEditorKeywordHelper mKeywordHelper;
    public AbstractEditorKeywordHelper getHelper() {return mKeywordHelper;}
    private final boolean mIsDropDown; //是否采用掉落为，父类的逻辑

    EditorBaseImplCode(EditorAreaImpl area, File sourceFile, Tab tab, boolean isFake) {
        this(AbstractEditorKeywordHelperFactory.create(sourceFile), area, sourceFile, tab, isFake);
    }

    private EditorBaseImplCode(AbstractEditorKeywordHelper helper, EditorAreaImpl area, File sourceFile, Tab tab, boolean isFake) {
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
        Log.d(sourceFile + " trigger");
        mKeywordHelper.triggerAllText(area, temporaryText, searchText);
    }
}


package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.richtext.codearea.EditorAreaImpl;
import com.allan.atools.utils.Log;
import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.text.beans.AllFilesSearchResults;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.modulenotepad.Highlight;
import com.allan.atools.UIContext;
import com.allan.atools.tools.modulenotepad.base.INotepadResultManager;
import com.allan.uilibs.richtexts.MyCodeArea;

public final class ResultAreaManager implements INotepadResultManager {
    private ResultAreaManager() {}
    public static final ResultAreaManager Instance = new ResultAreaManager();

    private static AbstractResultUpdater update;

    @Override
    public void updateSearchLayout(AllFilesSearchResults results) {
        if (update == null) {
            update = AbstractResultUpdater.createInstance();
        }

        var area = update.createArea(results);
        update.setClears(area);
        update.assetRoot();
        update.areaIntoVPaneIntoTitledPane(area, results);

        if (update.bringToFront()) {
            Log.d("bring to front!");
        }
    }

    @Override
    public void switchResultStyle(boolean splitOrNewWindow) {
        if(update != null) update.close();
        update = null;
    }

    @Override
    public void closeResult() {
        if(update != null) update.close();
        update = null;
    }

    @Override
    public void init() {
    }

    static void clickOnLine(Integer lineNum, int colIndex, AllFilesSearchResults mResults) {
        if (lineNum == 0) {
            return;
        }

        //Log.d("double click " + lineNum);
        var areaAndItem = mResults.getByLineNum(lineNum, colIndex);
        var editor = areaAndItem.areaEx.get();
        if (editor != null) {
            var item = areaAndItem.searchResultItem;
            //Log.d("double click " + item);
            if (item.lineMode == ResultItemWrap.LineMode.Real) {
                var firstItem = item.items[areaAndItem.secondIndex];
                var editArea = (EditorAreaImpl) editor.getArea();
                if (editArea != null) {
                    if (!AllEditorsManager.Instance.isCurrentAreaOnFront(editArea)) {
                        AllEditorsManager.Instance.bringAreaToFront(editArea);
                    }

                    if (SettingPreferences.getBoolean(SettingPreferences.resultAreaInNewWindowKey) && UIContext.focus.isNeedRequestMainStage()) {
                        UIContext.context().getStage().requestFocus();
                        update.requestFocus();
                    }

                    editArea.getBottom().disableSelectionListenerTemporary(500);
                    if (editArea.getEditor().isDestroyed()) {
                        Log.e("jump to next but edior is destroyed!");
                    }
                    Highlight.jumpToLineAndSelectWordMore((MyCodeArea) editor.getArea(), Highlight.JumpMode.JumpCenter, item.getOrigLine(),
                            item.lineNum, firstItem.range.start, firstItem.range.end);
                }
            }
        }
    }
}
package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.SettingPreferences;
import com.allan.atools.UIContext;
import com.allan.atools.text.beans.AllFilesSearchResults;

/**
 * 结果区宿主：新窗口和分屏共用同一份会话视图，不再为每次搜索追加折叠标题。
 */
public abstract class AbstractResultUpdater {
    static final int MAX_SESSIONS_COUNT = UIContext.DEBUG ? 5 : 15;

    static ResultSessionView mResultRoot;

    static AbstractResultUpdater createInstance() {
        return SettingPreferences.getBoolean(SettingPreferences.resultAreaInNewWindowKey)
                ? new ResultUpdaterNewWindowImpl()
                : new ResultUpdaterSplitPaneImpl();
    }

    abstract void assetRoot();

    public abstract boolean bringToFront();

    void addSession(AllFilesSearchResults results) {
        if (mResultRoot == null) {
            return;
        }
        mResultRoot.addSession(results, MAX_SESSIONS_COUNT);
        afterShown();
    }

    void destroyResultRoot() {
        if (mResultRoot != null) {
            mResultRoot.dispose();
        }
    }

    abstract void afterShown();

    public abstract void close();

    void requestFocus() {}
}

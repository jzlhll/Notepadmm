package com.allan.atools.tools.modulenotepad.base;

import java.io.File;

public interface IWorkspace {
    void initWhenAppStart();
    void removeWorkspace(boolean savedCfg);
    void openLastWorkspace();
    void refreshWorkspace();

    /**
     * 这个文件变动了，是否应该改变呢。
     * @param changedFile
     */
    void ifRefreshWorkspace(File changedFile);
    void selectDirAsWorkspaceDialog();
}

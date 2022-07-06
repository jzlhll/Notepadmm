package com.allan.atools.toolsstartup;

import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.SettingPreferences;
import com.allan.atools.utils.FileLog;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.allan.baseparty.handler.TextUtils;
import com.allan.baseparty.memory.RefWatcher;
import javafx.application.Application;

import java.awt.*;

public final class Startup {
    public static String[] sInitArgs;
    public static volatile boolean isArgsInit;

    //这个里面的所有执行代码必须能让如下去执行；因此需要exports他们
    // java.base/jdk.internal.loader.BuiltinClassLoader.loadClass
    // (BuiltinClassLoader.java:641)
    public static void main(String[] args) {
        if (!SettingPreferences.getBoolean(SettingPreferences.hdScreen2Key)) {
            System.setProperty("prism.lcdtext", "false");
            //System.setProperty("prism.subpixeltext", "false");
        }

        var watchMode = System.getProperty("A_MEM_WATCHER");
        if (TextUtils.equals("real", watchMode)) {
            RefWatcher.initReal(8000);
        } else if (TextUtils.equals("print", watchMode) || TextUtils.equals("watch", watchMode)) {
            RefWatcher.initDebugEveryPrint(20*1000, 3);
        }

        //如何监听打开的文件：根据这个java8的时候文章，
        // https://docs.oracle.com/javase/tutorial/deployment/selfContainedApps/fileassociation.html
        // 解释到，linux+window，直接从args中提取；
        // 而mac需要通过openFileHandler来做。Application.getApplication().setOpenFileHandler((AppEvent.OpenFilesEvent
        //而javafx，我找到了如下的代码
        if (ResLocation.isOsx) { //todo 验证windows 是不是不会触发
            Desktop.getDesktop().setOpenFileHandler(e -> {
                isArgsInit = true;
                if (e != null) {
                    var files = e.getFiles();
                    Log.e("open file handler!!! size: " + files.size());
                    if (files != null) {
                        String[] ss = new String[files.size()];
                        for (int i = 0; i < ss.length; i++) {
                            ss[i] = files.get(i).getAbsolutePath();
                        }
                        sInitArgs = ss;
                    }
                }
            });
        } else {
            FileLog.write("open file handler!!! not support: ", false);
            isArgsInit = true;
            Startup.sInitArgs = args;
        }
        Application.launch(StartupApplication.class, args);
        ThreadUtils.shutdown();
    }
}


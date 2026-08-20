package com.allan.atools.toolsstartup;

import com.allan.atools.UIContext;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.FileOpenSupportsKt;
import com.allan.atools.utils.FileLog;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.allan.baseparty.handler.TextUtils;
import com.allan.baseparty.memory.RefWatcher;
import javafx.application.Application;
import javafx.application.Platform;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Startup {
    public static volatile String[] sInitArgs;
    public static volatile boolean isArgsInit;

    private static void onMacOpenFiles(List<File> files) {
        if (ThreadUtils.sBeClosing || files == null || files.isEmpty()) {
            return;
        }
        var paths = files.stream()
                .map(File::getAbsolutePath)
                .toArray(String[]::new);
        sInitArgs = paths;
        try {
            Platform.runLater(() -> {
                if (ThreadUtils.sBeClosing || UIContext.mainController == null) {
                    return;
                }
                for (var path : paths) {
                    FileOpenSupportsKt.open(path);
                }
                if (sInitArgs == paths) {
                    sInitArgs = null;
                }
            });
        } catch (IllegalStateException ignored) {
            // JavaFX 尚未启动，主界面初始化后会读取 sInitArgs。
        }
    }

    //已有实例在运行时，把命令行传入的文件通过 Launch Services 转发给运行中的实例（触发其 openFileHandler 打开文件）
    private static void forwardFilesToRunningInstance(String[] files) {
        try {
            var command = ProcessHandle.current().info().command();
            if (command.isEmpty()) {
                return;
            }
            // /path/App.app/Contents/MacOS/exe -> /path/App.app
            var exe = new File(command.get());
            var macOSDir = exe.getParentFile();
            var contentsDir = macOSDir == null ? null : macOSDir.getParentFile();
            var appBundle = contentsDir == null ? null : contentsDir.getParentFile();
            if (appBundle == null || !appBundle.getName().endsWith(".app")) {
                return;
            }
            var cmd = new ArrayList<String>(files.length + 3);
            cmd.add("open");
            cmd.add("-a");
            cmd.add(appBundle.getAbsolutePath());
            for (var f : files) {
                cmd.add(f);
            }
            new ProcessBuilder(cmd).inheritIO().start();
        } catch (Throwable t) {
            Log.e("forward files to running instance failed", t);
        }
    }

    public static void shutdownAfterMainWindowClosed() {
        if (ResLocation.isOsx) {
            try {
                Desktop.getDesktop().setOpenFileHandler(null);
            } catch (RuntimeException e) {
                Log.e("clear mac open file handler failed", e);
            }
            ThreadUtils.shutdownAndExitProcess();
            return;
        }
        ThreadUtils.shutdown();
    }

    //这个里面的所有执行代码必须能让如下去执行；因此需要exports他们
    // java.base/jdk.internal.loader.BuiltinClassLoader.loadClass
    // (BuiltinClassLoader.java:641)
    public static void main(String[] args) {
        if (!InstanceLock.tryLock()) {
            if (ResLocation.isOsx && args.length > 0) {
                //命令行带文件转发给已有实例时静默退出，不打扰终端
                forwardFilesToRunningInstance(args);
            } else {
                Log.e("another instance is running, exit");
            }
            System.exit(0);
        }

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
                    Log.e("open file handler size: " + files.size());
                    onMacOpenFiles(files);
                }
            });
            //终端直接执行二进制（如 ATools file.txt）冷启动时，文件只会出现在命令行参数里；
            //Launch Services 启动（open -a / 双击文件）不会传命令行参数，不会与 openFileHandler 重复打开
            if (args.length > 0) {
                isArgsInit = true;
                sInitArgs = args;
            }
        } else {
            FileLog.write("open file handler!!! not support: ", false);
            isArgsInit = true;
            Startup.sInitArgs = args;
        }
        Application.launch(StartupApplication.class, args);

        ThreadUtils.shutdown();
    }
}

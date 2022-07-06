package com.allan.atools.utils;

import com.allan.baseparty.handler.Handler;
import com.allan.baseparty.handler.HandlerThread;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

final class FileItem {
    private final String filePath;

    private final String log;

    public String getFilePath() {
        return this.filePath;
    }

    public String getLog() {
        return this.log;
    }

    public FileItem(String filePath, String log) {
        this.filePath = filePath;
        this.log = log;
    }
}

public final class FileLog {
    private static class HolderHandler {
        static volatile Handler mHandler;
        static {
            HandlerThread handlerThread = new HandlerThread("FileLog_thread");
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper());
        }
    }

    ///更新
    public static void updateDay() {
        currentFilePath = calDayInner();
    }

    public enum LogFileCreateType {
        OneFileEveryDay, OneFileAnHour;
    }

    private FileLog(){}

    private static final LogFileCreateType logFileCreateType = LogFileCreateType.OneFileEveryDay; //必须放在前面
    private static String currentFilePath;
    private static String getCurrentFilePath() {
        if (currentFilePath == null) {
            currentFilePath = calDayInner();
        }
        return currentFilePath;
    }

    private static String calDayInner() {
        Calendar c = Calendar.getInstance();
        String path = CacheLocation.getLogRoot() + File.separatorChar;

        if (logFileCreateType == LogFileCreateType.OneFileEveryDay) { //把这个放在最前面吧。
            path += String.format("%02d",c.get(Calendar.MONTH) + 1) + "_" + String.format("%02d", c.get(Calendar.DAY_OF_MONTH)) + ".log";
        } else if (logFileCreateType == LogFileCreateType.OneFileAnHour) {
            path += String.format("%02d",c.get(Calendar.MONTH) + 1)
                    + "_" + String.format("%02d", c.get(Calendar.DAY_OF_MONTH))
                    + "_" + String.format("%02d", c.get(Calendar.HOUR))
                    + ".log";
        }

        return path;
    }

    private static void writeToDisk2(FileItem item, String stace) {
        if (item == null || item.getFilePath() == null)
            return;
        int lastIndex = item.getFilePath().lastIndexOf("/");
        if (lastIndex == -1) {
            return;
        }
        String dirPath = item.getFilePath().substring(0, lastIndex);
        if (dirPath.length() <= 1) {
            return;
        }
        File file = new File(dirPath);
        if (!file.exists())
            file.mkdirs();
        file = new File(item.getFilePath());
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        try (var fis = new FileOutputStream(file, true);
             var osw = new OutputStreamWriter(fis, StandardCharsets.UTF_8);
             var out = new BufferedWriter(osw)) {
            if (stace == null) {
                out.write(item.getLog());
            } else {
                out.write(stace + "\n" + item.getLog());
            }
            out.write("\n");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void write(String log, boolean needStace) {
        final String stace;
        if (needStace) {
            var ex = new Exception();
            StringBuilder sb = new StringBuilder();
            sb.append(ex.getMessage()).append("\n").append(ex.getCause()).append("\n");
            for (StackTraceElement element : ex.getStackTrace())
                sb.append(element.toString()).append(System.lineSeparator());
            stace = sb.toString();
        } else {
            stace = null;
        }
        try {
            String curFile = getCurrentFilePath();
            FileItem item = new FileItem(curFile, log);
            //排队写日志
            HolderHandler.mHandler.post(()-> {
                var cur = System.currentTimeMillis();
                if (cur - lastClearTime > CLEAR_DELTA_TIME) {
                    lastClearTime = cur;
                    clearLog();
                }
                writeToDisk2(item, stace);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final long CLEAR_LOG_TIME = 15L * 3600 * 24 * 1000; //N天前的日志删除。
    private static final long CLEAR_DELTA_TIME = 5L * 3600 * 24 * 1000; //M天清理一次。
    private static long lastClearTime = 0L;
    public static void clearLog() {
        var filePath = CacheLocation.getLogRoot();
        int count = 0;
        do {
            var file = new File(filePath);
            if (!file.exists()) {
                break;
            }
            var files = file.listFiles();
            if (files == null) {
                break;
            }
            for (var f : files) {
                if (f.exists()) {
                    try {
                        var time = f.lastModified();
                        if (System.currentTimeMillis() - time > CLEAR_LOG_TIME) {
                            if (f.delete()) {
                                count++;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } while (false);
        if (count > 0) {
            Log.e("clear old log file over! " + count);
        }
    }
}

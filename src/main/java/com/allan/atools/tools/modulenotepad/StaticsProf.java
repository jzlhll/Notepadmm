package com.allan.atools.tools.modulenotepad;

import com.allan.atools.utils.ResLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StaticsProf {
    private static volatile boolean mIsInit = false;
    private static final int MAX_FILE_SIZE_FOR_STYLE = 10*1024*1024;//10M的文件就不做setStyle了。TODO 改成可以做。
    private static final int MAX_FILE_SIZE = 150000000; //150 MB
    private static final int MAX_CFG_SIZE = 12; //高级搜索的个数
    private static final long JUMP_LINES_DELTA_TIME = 48; //跳转代码行的内部间隔时间段

    public static final boolean sFindTextColorByAuto = true;

    private static Integer sMaxFileSizeForSetStyle = null;
    private static Integer sMaxFileSize = null;
    private static Integer sMaxCfgSize = null;
    private static Long sJumpLinesDeltaTime = null;

    public static int getMaxFileSizeForStyle() {
        assetLoad();
        return sMaxFileSizeForSetStyle;
    }

    public static long getJumpLinesDeltaTime() {
        assetLoad();
        return sJumpLinesDeltaTime;
    }

    public static int getMaxFileSize() {
        assetLoad();
        return sMaxFileSize;
    }

    public static int getsMaxCfgSize() {
        assetLoad();
        return sMaxCfgSize;
    }

    private static void assetLoad() {
        if (!mIsInit) {
            synchronized (StaticsProf.class) {
                if (!mIsInit) {
                    try {
                        var ss = Files.readAllLines(Path.of(ResLocation.getRealPath("statics.config")));
                        for (var s : ss) {
                            if (sMaxFileSizeForSetStyle == null) {
                                try {
                                    String maxSizeSetStyle = "maxFileSizeForSetStyle=";
                                    if (s.startsWith(maxSizeSetStyle)) {
                                        sMaxFileSizeForSetStyle = Integer.parseInt(s.substring(maxSizeSetStyle.length()));
                                    }
                                } catch (Exception e) {
                                    //
                                    sMaxFileSizeForSetStyle = MAX_FILE_SIZE_FOR_STYLE;
                                }
                            }

                            if (sMaxFileSize == null) {
                                try {
                                    String maxFileSize = "maxFileSize=";
                                    if (s.startsWith(maxFileSize)) {
                                        sMaxFileSize = Integer.parseInt(s.substring(maxFileSize.length()));
                                        continue;
                                    }
                                } catch (Exception e) {
                                    //
                                    sMaxFileSize = MAX_FILE_SIZE;
                                }
                            }

                            if (sMaxCfgSize == null) {
                                try {
                                    String maxCfgSize = "advanceMaxCfgCount=";
                                    if (s.startsWith(maxCfgSize)) {
                                        sMaxCfgSize = Integer.parseInt(s.substring(maxCfgSize.length()));
                                        continue;
                                    }
                                } catch (Exception e) {
                                    //
                                    sMaxCfgSize = MAX_CFG_SIZE;
                                }
                            }

                            if (sJumpLinesDeltaTime == null) {
                                try {
                                    String jumpToLineExcluteTime = "jumpToLineExcluteTime=";
                                    if (s.startsWith(jumpToLineExcluteTime)) {
                                        sJumpLinesDeltaTime = Long.parseLong(s.substring(jumpToLineExcluteTime.length()));
                                    }
                                } catch (Exception e) {
                                    //
                                    sJumpLinesDeltaTime = JUMP_LINES_DELTA_TIME;
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    mIsInit = true;
                }
            }
        }
    }
}

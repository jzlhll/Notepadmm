package com.allan.atools.utils;

import com.allan.atools.threads.ThreadUtils;
import javafx.application.Platform;

public final class ManualGC {
    private ManualGC() {}
    private static int mCount = 0, mCount2 = 0, mCount3 = 0;
    private static final int REACH_COUNT = 3;
    private static final int REACH_COUNT_2 = 10;
    private static final int REACH_COUNT_3 = 50;

    private static final long DELAY_MS = 50;

    /**
     * 不太经常的操作，并且这些操作进来以后，尝试手动调用gc。适用于经常移除的操作。
     */
    public static void decupleGC() {
        if (mCount2++ > REACH_COUNT_2) {
            mCount2 = 0;
            ThreadUtils.globalHandler().postDelayed(()-> {
                if (!ThreadUtils.sBeClosing) {
                    Platform.runLater(() -> {
                        TimerCounter.start("decuple_manual_gc");
                        mCount = mCount2 = mCount3 = 0;
                        System.gc();
                        Log.d(TimerCounter.end("decuple_manual_gc"));
                    });
                }
            }, DELAY_MS);
        }
    }

    /**
     * 不太经常的操作，并且这些操作进来以后，尝试手动调用gc。适用于经常移除的操作。
     */
    public static void lessGC() {
        if (mCount3++ > REACH_COUNT_3) {
            mCount3 = 0;
            ThreadUtils.globalHandler().postDelayed(()-> {
                if (!ThreadUtils.sBeClosing) {
                    Platform.runLater(() -> {
                        TimerCounter.start("decuple_manual_gc");
                        mCount = mCount2 = mCount3 = 0;
                        System.gc();
                        Log.d(TimerCounter.end("decuple_manual_gc"));
                    });
                }
            }, DELAY_MS);
        }
    }

    /**
     * 不太经常的操作，并且这些操作进来以后，尝试手动调用gc。适用于经常移除的操作。
     */
    public static void triplyGC() {
        if (mCount++ > REACH_COUNT) {
            mCount = 0;
            ThreadUtils.globalHandler().postDelayed(()-> {
                if (!ThreadUtils.sBeClosing) {
                    Platform.runLater(() -> {
                        TimerCounter.start("triply_manual_gc");
                        mCount = mCount2 = mCount3 = 0;
                        System.gc();
                        Log.d(TimerCounter.end("triply_manual_gc"));
                    });
                }
            }, DELAY_MS);
        }
    }

    /**
     * 不太经常的操作，当退出某个大的页面。尝试直接GC。
     */
    public static void directlyGC() {
        ThreadUtils.globalHandler().postDelayed(()-> {
            if (!ThreadUtils.sBeClosing) {
                Platform.runLater(() -> {
                    TimerCounter.start("just_manual_gc");
                    mCount = mCount2 = mCount3 = 0;
                    System.gc();
                    Log.d(TimerCounter.end("just_manual_gc"));
                });
            }
        }, DELAY_MS);
    }

    public static void directlyGCLater() {
        ThreadUtils.globalHandler().postDelayed(()-> {
            if (!ThreadUtils.sBeClosing) {
                Platform.runLater(() -> {
                    TimerCounter.start("just_manual_gc");
                    mCount = mCount2 = mCount3 = 0;
                    System.gc();
                    Log.d(TimerCounter.end("just_manual_gc"));
                });
            }
        }, DELAY_MS * 100);
    }
}

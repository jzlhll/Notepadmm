package com.allan.atools.utils;

import com.allan.baseparty.Action0;

public final class DoubleClickUtil {
    private static final long DOUBLE_CLICK_DELTA_TIME = 250;
    private static final long DOUBLE_CLICK_EACH_TIME = 1000;
    private static volatile long mLastClickTime;
    private static volatile long mLastDoubleClickTime;

    private final Action0 mDoubleClick;

    public DoubleClickUtil(Action0 mDoubleClick) {
        this.mDoubleClick = mDoubleClick;
    }

    public void onClick() {
        long cur = System.currentTimeMillis();
        long lastTime = mLastClickTime;
        mLastClickTime = cur;

        if (DOUBLE_CLICK_DELTA_TIME > (cur - lastTime) && mDoubleClick != null) {
            mLastClickTime = 0L;
            if (DOUBLE_CLICK_EACH_TIME < cur - mLastDoubleClickTime) {
                mLastDoubleClickTime = cur;
                mDoubleClick.invoke();
            }
        }
    }
}

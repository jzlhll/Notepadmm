package com.allan.atools.threads;

import com.allan.baseparty.handler.Handler;
import com.allan.baseparty.handler.Looper;

public final class ClosedDroppedHandler extends Handler {
    public ClosedDroppedHandler(Looper looper) {
        super(looper);
    }

    public void postDelayedCheckClosed(Runnable r, long delay) {
        if (ThreadUtils.sBeClosing) {
            return;
        }
        super.postDelayed(() -> {
            if (!ThreadUtils.sBeClosing) {
                r.run();
            }
        }, delay);
    }
}
package com.allan.atools.threads;

import com.allan.baseparty.handler.HandlerThread;

class HolderHandler {
    static volatile ClosedDroppedHandler mBackgroundHandler;
    static HandlerThread mBackgroundThread;

    static {
        HandlerThread handlerThread = new HandlerThread("main_global_handler");
        mBackgroundThread = handlerThread;
        handlerThread.start();
        mBackgroundHandler = new ClosedDroppedHandler(handlerThread.getLooper());
    }
}

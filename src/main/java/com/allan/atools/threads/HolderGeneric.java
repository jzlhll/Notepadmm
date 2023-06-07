package com.allan.atools.threads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class HolderGeneric{
    static final ExecutorService genericService = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
            120, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(),
            new ExDefaultThreadFactory());
    static void shutdown() {
        if (genericService.isShutdown()) {
            return;
        }
        genericService.shutdown();
    }
}

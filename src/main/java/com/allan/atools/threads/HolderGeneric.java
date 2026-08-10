package com.allan.atools.threads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class HolderGeneric{
    static final ExecutorService genericService = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
            120, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ExDefaultThreadFactory());
    static final ExecutorService fileIoService = Executors.newSingleThreadExecutor(new ExDefaultThreadFactory());

    static void shutdown() {
        if (!genericService.isShutdown()) {
            genericService.shutdown();
        }
        if (!fileIoService.isShutdown()) {
            fileIoService.shutdown();
        }
        try {
            fileIoService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.allan.atools.threads;

import com.allan.atools.GlobalCfgStores;
import com.allan.baseparty.handler.HandlerThread;
import com.allan.atools.utils.Log;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadFactory;

class ExDefaultThreadFactory implements ThreadFactory {
    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    ExDefaultThreadFactory() {
        namePrefix = "atools-pool-" +
                poolNumber.getAndIncrement() +
                "-thread-";
    }

    private static void uncaughtException(Thread t1, Throwable e) {
        if (e != null) {
            Log.e("ExDefaultThreadFactory: ", e);
        }
    }

    public Thread newThread(Runnable r) {
        Thread t = new Thread(null, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
        if (t.isDaemon())
            t.setDaemon(false);
        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);
        t.setUncaughtExceptionHandler(ExDefaultThreadFactory::uncaughtException);
        return t;
    }
}

public final class ThreadUtils {
    public volatile static boolean sBeClosing = false;
    public static final SimpleStringProperty sClosingProper = new SimpleStringProperty();
    private static final AtomicInteger sShutdownStarted = new AtomicInteger(0);

    public static ClosedDroppedHandler globalHandler() {
        return HolderHandler.mBackgroundHandler;
    }

    /**
     * @param action
     * 		Runnable to start in new thread.
     *
     * @return Thread future.
     */
    public static Future<?> submit(Runnable action) {
        return HolderGeneric.genericService.submit(action);
    }

    public static void execute(Runnable action) {
        HolderGeneric.genericService.execute(action);
    }

    public static void executeFileIo(Runnable action) {
        HolderGeneric.fileIoService.execute(action);
    }

    public static Future<?> submitFileIo(Runnable action) {
        return HolderGeneric.fileIoService.submit(action);
    }

    /**
     * @param action
     * 		Task to start in new thread.
     * @param <T>
     * 		Type of task return value.
     *
     * @return Thread future.
     */
    @SuppressWarnings("unchecked")
    public static <T> Future<T> run(Task<T> action) {
        return (Future<T>) HolderGeneric.genericService.submit(action);
    }

    /**
     * @param time
     * 		Delay to wait in milliseconds.
     * @param action
     * 		Runnable to start in new thread.
     */
    public static void executeDelay(long time, Runnable action) {
        HolderHandler.mBackgroundHandler.postDelayed(() -> {
            HolderGeneric.genericService.execute(action);
        }, time);
    }

    /**
     * Run a given action with a timeout.
     *
     * @param time
     * 		Timeout in milliseconds.
     * @param action
     * 		Runnable to execute.
     *
     * @return {@code true}
     */
    public static boolean timeout(int time, Runnable action) {
        try {
            Future<?> future = submit(action);
            future.get(time, TimeUnit.MILLISECONDS);
            return true;
        } catch(TimeoutException e) {
            // Expected: Timeout
            return false;
        } catch(Throwable t) {
            // Other error
            return true;
        }
    }

    /**
     * @param consumer
     * 		JavaFx runnable action.
     */
    public static void checkJfxAndEnqueue(Runnable consumer) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(consumer);
        } else {
            consumer.run();
        }
    }

    /**
     * Shutdowns executors.
     */
    public static void shutdown() {
        shutdown(null);
    }

    public static void shutdown(Runnable completedAction) {
        if (!sShutdownStarted.compareAndSet(0, 1)) {
            return;
        }
        sBeClosing = true;
        sClosingProper.set("close");

        new Thread(()-> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            GlobalCfgStores.flushAll();
            HolderGeneric.shutdown();
            HolderHandler.mBackgroundThread.safelyQuit();

            HandlerThread.quitAllDelay(600, completedAction);
        }).start();
    }

    public static void shutdownAndExitProcess() {
        shutdown(() -> System.exit(0));
    }

    public static void main(String[] args) {
        Runnable run = ()-> {
            int[] aa = {0};
            int a = 10;
            a = a / 0;
//            System.out.println("aa " + aa[1]);
        };
        Log.d("run " + run);
        ThreadUtils.execute(run);
    }
}

package com.allan.baseparty.memory;
import com.allan.baseparty.handler.Handler;
import com.allan.baseparty.handler.HandlerThread;
import com.allan.baseparty.handler.Looper;
import com.allan.baseparty.handler.Message;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class RefWatcher {
    private RefWatcher() {}

    private static final boolean IS_ANDROID = false;

    public static final String TAG = "RefWatcher";

    static void log(String s) {
        if (IS_ANDROID) {
        } else {
            System.out.println(RefWatcher.TAG + ": " + s);
        }
    }

    private static class KeyedWeakReference extends WeakReference<Object> {
        private final String key;
//        public KeyedWeakReference(Object r, String key, ReferenceQueue<Object> q) {
//            super(r, q);
//            this.key = key;
//        }

        public KeyedWeakReference(Object r, String key) {
            super(r);
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    public static final boolean DEBUG_LOG = true;

    /**
     * 分为2种case：
     * 1. 如果是real模式。则表示调用后，监听期待object(通常为你认为它即将要回收）对象。先delay时间进行GC，再delay时间打印是否object被回收。
     * 2. 如果是everyPrint模式，则表示调用后，一直打印整个监控列表。
     */
    abstract void watch(Object object, String tag);

    private static RefWatcher refWatcher;
    static GcTrigger mGc;

    public static void initReal(long delayGcTime) {
        log("init mode real!!!");
        if (delayGcTime > 0) {
            RefWatcherReal.DelayHandler.DELAY_MILLIS = delayGcTime;
        }
        mGc = GcTrigger.DEFAULT;
        refWatcher = new RefWatcherReal();
    }

    public static void watchs(Object obj, String tag) {
        if (refWatcher != null) {
            refWatcher.watch(obj, tag);
        }
    }

    /**
     *
     * @param gapInMs 多少秒打印一次. 如果传入0，则是不做gc。
     * @param gcEveryCount 重复多少次gcEveryMs才gc一次。
     */
    public static void initDebugEveryPrint(long gapInMs, int gcEveryCount) {
        if (gapInMs < 20000L) {
            mGc = GcTrigger.None;
            log("init mode every time Print!! never gc and " + RefWatcherAllSaved.gapPrintInMs + "ms print ref list once.");
        } else {
            mGc = GcTrigger.DEFAULT;
            RefWatcherAllSaved.gcEveryCount = gcEveryCount;
            RefWatcherAllSaved.gapPrintInMs = gapInMs;
            log("init mode every time Print!! gc every " + gcEveryCount + "times, and " + RefWatcherAllSaved.gapPrintInMs + "ms print ref list once.");
        }

        refWatcher = new RefWatcherAllSaved();
    }

    private static class RefWatcherReal extends RefWatcher {
        private final Set<String> retainedKeys = new CopyOnWriteArraySet<>();
        private DelayHandler delayer;
        private RefWatcherReal() {
            super();
        }

        private DelayHandler getDelayer() {
            if (delayer == null) {
                delayer = new DelayHandler();
            }
            return delayer;
        }
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

        @Override
        void watch(Object object, String tag) {
            final String key = tag + ": " + UUID.randomUUID() + ", " + object.toString();
            if(DEBUG_LOG) log(">>> added you want this " + key + " to be gc after " + DelayHandler.DELAY_MILLIS);
            retainedKeys.add(key);

            getDelayer().post(() -> {
                mGc.runGc();
                getDelayer().post(()-> {
                    removeWeaklyReachableReferences();
                    //触发gc后，仍然存在：
                    if (!gone(key)) {
                        log(" MEMORY LEAK: " + key);
                    }
                });
            });
        }

        //不在keys列表中，则证明已经不存在了。
        private boolean gone(String key) {
            return !retainedKeys.contains(key);
        }

        private void removeWeaklyReachableReferences() {
            // WeakReferences are enqueued as soon as the object to which they point to becomes weakly
            // reachable. This is before finalization or garbage collection has actually happened.
            KeyedWeakReference ref;
            while ((ref = (KeyedWeakReference) queue.poll()) != null) {
                log(" ref removed!!! " + ref.getKey());
                retainedKeys.remove(ref.getKey());
            }
        }

        private static final class DelayHandler {
            static final String LEAK_CANARY_THREAD_NAME = "LeakCanary-Heap-Dump";
            static long DELAY_MILLIS = 6000;

            private final Handler backgroundHandler;

            DelayHandler() {
                HandlerThread handlerThread = new HandlerThread(LEAK_CANARY_THREAD_NAME);
                handlerThread.start();
                backgroundHandler = new Handler(handlerThread.getLooper());
            }

            public void post(final Runnable command) {
                if(command != null) backgroundHandler.postDelayed(command, DELAY_MILLIS);
            }
        }
    }

    private static final class RefWatcherAllSaved extends RefWatcher {
        static int gcEveryCount = 1;
        static long gapPrintInMs = 20 * 1000L;

        private static final List<KeyedWeakReference> list = new ArrayList<>();
        private MyHandler myHandler;

        private static final class MyHandler extends Handler {
            private int mCount;
            MyHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                synchronized (list) {
                    if(DEBUG_LOG) log("======>>>>>====size: " + list.size());
                    for (int i = 0; i < list.size(); i++) { //list.size不得改成count
                        KeyedWeakReference r = list.get(i);
                        if (r == null || r.get() == null) {
                            list.remove(i);
                            i--;
                            if(DEBUG_LOG) log("===remove!!! " + (r != null ? r.getKey() : ""));
                        } else {
                            if(DEBUG_LOG) log("===alive item " + r.getKey());
                        }
                    }
                    if(DEBUG_LOG) log("====after size: " + list.size());
                }

                ++mCount;
                if(mCount == gcEveryCount) {
                    mGc.runGc();
                    mCount = 0;
                }
                sendMessageDelayed(obtainMessage(1), gapPrintInMs);
            }
        }

        @Override
        void watch(Object object, String tag) {
            synchronized (list) {
                if(DEBUG_LOG) log(">>> add " + object);
                list.add(new KeyedWeakReference(object, tag + ": " + object.toString()));
            }

            if (myHandler == null) {
                HandlerThread handlerThread = new HandlerThread("AllSavedRefWatcher-Thread");
                handlerThread.start();
                myHandler = new MyHandler(handlerThread.getLooper());
                myHandler.sendMessageDelayed(myHandler.obtainMessage(1),  gapPrintInMs);
            }
        }
    }
}

interface GcTrigger {
    GcTrigger None = () -> {

    };

    GcTrigger DEFAULT = new GcTrigger() {
        @Override
        public void runGc() {
            RefWatcher.log("run gc!!!");
            System.gc();
            enqueueReferences();
            System.runFinalization();
        }

        private void enqueueReferences() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new AssertionError();
            }
        }
    };

    void runGc();
}
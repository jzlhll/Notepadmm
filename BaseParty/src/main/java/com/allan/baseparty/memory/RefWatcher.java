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
import java.util.concurrent.Executor;


public abstract class RefWatcher {
    static final String TAG = "<RefWatcher> ";

    public static class KeyedWeakReference extends WeakReference<Object> {
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

    public abstract void watch(Object object, String tag);

    private static RefWatcher refWatcher;
    static GcTrigger mGc;

    public static void initReal(long delayGcTime) {
        System.out.println(TAG + "init mode real!!");
        if (delayGcTime > 0) {
            WatchExecutor.DELAY_MILLIS = delayGcTime;
        }
        mGc = GcTrigger.DEFAULT;
        refWatcher = new RefWatcherReal();
    }

    /**
     *
     * @param gcEveryMs 多少秒打印一次
     * @param gcEveryCount 重复多少次gcEveryMs才gc一次。
     */
    public static void initDebugEveryPrint(long gcEveryMs, int gcEveryCount) {
        System.out.println(TAG + "init mode Every Print!");
        if (gcEveryCount == 0) {
            mGc = GcTrigger.None;
        } else {
            mGc = GcTrigger.DEFAULT;
            RefWatcherAllSaved.mGcEveryCount = gcEveryCount;
        }

        if (gcEveryMs > 0L) {
            RefWatcherAllSaved.mGcEveryMs = gcEveryMs;
        }
        refWatcher = new RefWatcherAllSaved();
    }

    public static RefWatcher getInstance() {
        return refWatcher;
    }
}

interface GcTrigger {
    GcTrigger None = () -> {

    };

    GcTrigger DEFAULT = new GcTrigger() {
        @Override
        public void runGc() {
            System.out.println(RefWatcher.TAG + "run gc!!!");
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

final class WatchExecutor implements Executor {
    static final String LEAK_CANARY_THREAD_NAME = "LeakCanary-Heap-Dump";
    static long DELAY_MILLIS = 6000;

    private final Handler backgroundHandler;

    public WatchExecutor() {
        HandlerThread handlerThread = new HandlerThread(LEAK_CANARY_THREAD_NAME);
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void execute(final Runnable command) {
        if(command != null) backgroundHandler.postDelayed(command, DELAY_MILLIS);
    }
}

final class RefWatcherReal extends RefWatcher {
    private final Set<String> retainedKeys = new CopyOnWriteArraySet<>();
    private Executor watchExecutor;
    public RefWatcherReal() {}

    private Executor getWatchExecutor() {
        if (watchExecutor == null) {
            watchExecutor = new WatchExecutor();
        }
        return watchExecutor;
    }
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    @Override
    public void watch(Object object, String tag) {
        final String key = tag + ": " + UUID.randomUUID() + ", " + object.toString();
        if(DEBUG_LOG) System.out.println(RefWatcher.TAG + ">>> added you want this " + key + " to be gc after " + WatchExecutor.DELAY_MILLIS);
        retainedKeys.add(key);

        getWatchExecutor().execute(() -> {
            mGc.runGc();
            getWatchExecutor().execute(()-> {
                removeWeaklyReachableReferences();
                //触发gc后，仍然存在：
                if (!gone(key)) {
                    System.out.println(RefWatcher.TAG + " MEMORY LEAK: " + key);
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
            System.out.println(RefWatcher.TAG + " ref removed!!! " + ref.getKey());
            retainedKeys.remove(ref.getKey());
        }
    }
}
final class RefWatcherAllSaved extends RefWatcher {
    static int mGcEveryCount = 0;
    static long mGcEveryMs = 20 * 1000L;

    private final List<KeyedWeakReference> list = new ArrayList<>();
    private MyHandler myHandler;

    private final class MyHandler extends Handler {
        private int mCount;
        MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            synchronized (list) {
                if(DEBUG_LOG) System.out.println(RefWatcher.TAG + "======>>>>>====size: " + list.size());
                for (int i = 0; i < list.size(); i++) { //list.size不得改成count
                    KeyedWeakReference r = list.get(i);
                    if (r == null || r.get() == null) {
                        list.remove(i);
                        i--;
                        if(DEBUG_LOG) System.out.println(RefWatcher.TAG + "===remove!!! " + (r != null ? r.getKey() : ""));
                    } else {
                        if(DEBUG_LOG) System.out.println(RefWatcher.TAG + "===alive item " + r.getKey());
                    }
                }
                if(DEBUG_LOG) System.out.println(RefWatcher.TAG + "====after size: " + list.size());
            }

            if(mCount++ == mGcEveryCount) {
                mGc.runGc();
                mCount = 0;
            }
            sendMessageDelayed(obtainMessage(1), 10 * 1000L);
        }
    }

    @Override
    public void watch(Object object, String tag) {
        synchronized (list) {
            if(DEBUG_LOG) System.out.println(RefWatcher.TAG + ">>> add " + object);
            list.add(new KeyedWeakReference(object, tag + ": " + object.toString()));
        }

        if (myHandler == null) {
            HandlerThread handlerThread = new HandlerThread("AllSavedRefWatcher-Thread");
            handlerThread.start();
            myHandler = new MyHandler(handlerThread.getLooper());
            myHandler.sendMessageDelayed(myHandler.obtainMessage(1),  mGcEveryMs);
        }
    }
}

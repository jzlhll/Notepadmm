package com.allan.baseparty.handler;

import java.util.HashSet;

public class HandlerThread extends Thread {
    Looper mLooper;
    private final String name;

    private static final boolean DEBUG = false;

    public HandlerThread(String name) {
        super(name);
        this.name = name;
        synchronized (mAllThreads) {
            mAllThreads.add(this);
        }
    }
    protected void onLooperPrepared() { }

    @Override
    public void run() {
        Looper.prepare();
        synchronized (this) {
            mLooper = Looper.myLooper();
            notifyAll();
        }
        onLooperPrepared();
        Looper.loop();
        synchronized (mAllThreads) {
            mAllThreads.remove(this);
        }
    }

    public Looper getLooper() {
        if (!isAlive()) return null;
        synchronized (this) {
            while (isAlive() && mLooper == null)
                try { wait(); } catch (InterruptedException e) { }
        }
        return mLooper;
    }

    public final boolean directlyQuit() {
        Looper looper = getLooper();
        if (looper != null) {
            looper.quit();
            return true;
        }
        return false;
    }

    public final void safelyQuit() {
        safelyQuit(true);
    }

    private void safelyQuit(boolean remove) {
        Looper looper = getLooper();
        if (looper != null) {
            looper.quitSafely();
            if (remove) {
                synchronized (mAllThreads) {
                    mAllThreads.remove(this);
                }
            }
        }
    }

    public static void quitAllDelay(long delayMs) {
        synchronized (mAllThreads) {
            if (mAllThreads.size() == 0) {
                if(DEBUG) System.out.println("no more mAllThreads");
                return;
            }
        }

        new Thread(() -> {
            long piece = delayMs > 1000 ? delayMs / 4 : delayMs / 2;
            long delta = 0;
            if(DEBUG) System.out.println("开始准备移除所有未关闭的HandlerThread.... " + delayMs);
            while (delta < delayMs) {
                delta += piece;
                try {
                    Thread.sleep(piece);
                    synchronized (mAllThreads) {
                        for (var t : mAllThreads) {
                            if(DEBUG) System.out.println(delta + "ms后，仍然存活着" + mAllThreads.size() + "个handlerThread, " + t.name);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            synchronized (mAllThreads) {
                for (HandlerThread t : mAllThreads) {
                    if(DEBUG) System.out.println(delta + "ms后" + mAllThreads.size() + "个handlerThread, 开始逐个关闭：" + t.name);
                    t.safelyQuit(false);
                }
            }

        }).start();
    }

    private static final HashSet<HandlerThread> mAllThreads = new HashSet<>();
}
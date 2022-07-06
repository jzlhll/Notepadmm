package com.allan.baseparty.handler;

public final class Looper {
    //private static Looper sMainLooper; javafx没必要主线程。没有框架
    private static final ThreadLocal<Looper> THREAD_LOCAL = new ThreadLocal<Looper>();
    private final MessageQueue messageQueue;

    public static void prepare() {
        if (THREAD_LOCAL.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        THREAD_LOCAL.set(new Looper());
    }

//    public static void prepareMainLooper() {
//        prepare();
//        synchronized (Looper.class) {
//            if (sMainLooper != null) {
//                throw new IllegalStateException("The main Looper has already been prepared.");
//            }
//            sMainLooper = myLooper();
//        }
//    }
//
//    public static Looper getMainLooper() {
//        synchronized (Looper.class) {
//            return sMainLooper;
//        }
//    }

    public static Looper myLooper() {
        return THREAD_LOCAL.get();
    }

    private Looper() {
        messageQueue = new MessageQueue();
    }

    public MessageQueue getQueue() {
        return messageQueue;
    }

    public static void loop() {
        Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No looper");
        }
        while(true) {
            Message msg = me.messageQueue.next();
            if (msg == null) {
                return;
            }
            Handler tar = msg.target;
            if (tar == null) {
                if(Message.DEBUG) System.out.println("target null cause already removed");
            } else {
                tar.dispatchMessage(msg);
            }

            msg.recycleUnchecked();
        }
    }

    public void quit() {
        messageQueue.quit(false);
    }

    public void quitSafely() {
        messageQueue.quit(true);
    }
}
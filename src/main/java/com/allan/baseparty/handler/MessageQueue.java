package com.allan.baseparty.handler;

import com.allan.baseparty.handler.utils.Logger;

import java.util.concurrent.DelayQueue;

final class MessageQueue {
    private static final Message POISON = new Message();

    private volatile boolean isQuited = false;

    private final DelayQueue<Message> queue = new DelayQueue<>();

    public void enqueueMessage(Message msg) {
        if (isQuited) {
            msg.recycle();
            return;
        }

        if(Message.DEBUG) System.out.println("add " + msg.hashCode());
        queue.add(msg);
    }

    public Message next() {
        try {
            Message next;
            do {
                next = queue.take();
                if(Message.DEBUG) System.out.println(" next " + next.hashCode());
                //有可能被移除了，已经标记位但是仍然还在这里；
                if (!next.isInRecycled()) {
                    break;
                } else {
                    //next.recycleUnchecked(); //不应该再添加进去了。因为刚刚标记为回收肯定在pool中。
                    if(Message.DEBUG) System.out.println(" is already recycled " + next.hashCode());
                }
            } while (true);

            if (next == POISON) {
                return null;
            }
            return next;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    boolean hasMessage(Handler h, int what) {
        for (var msg : queue) {
            if (msg.target  == h && msg.what == what) {
                return true;
            }
        }

        return false;
    }

    boolean hasRunnable(Handler h, Runnable runnable) {
        for (var msg : queue) {
            if (msg.target  == h && msg.callback == runnable) {
                return true;
            }
        }

        return false;
    }

    boolean removeMessages(Handler h, int what) {
        if (h == null) {
            return false;
        }
        return queue.removeIf(message -> {
            boolean shouldRemove = message.target == h && message.what == what;
            if (shouldRemove) {
                if(Message.DEBUG) System.out.println("remove " + message.hashCode());
                message.recycleUnchecked();
            }
            return shouldRemove;
        });
    }

    void removeCallbacks(Handler h, Runnable runnable) {
        if (h == null) {
            return;
        }

        queue.removeIf(message -> {
            boolean shouldRemove = message.target == h && message.callback == runnable;
            if (shouldRemove) {
                if(Message.DEBUG) System.out.println("remove " + message.hashCode());
                message.recycleUnchecked();
            }
            return shouldRemove;
        });
    }

    void removeAllCallbacksAndMessages(Handler h) {
        if (h == null) {
            return;
        }

        queue.removeIf(message -> {
            boolean shouldRemove = message.target == h;
            if (shouldRemove) {
                if(Message.DEBUG) System.out.println("remove " + message.hashCode());
                message.recycleUnchecked();
            }
            return shouldRemove;
        });
    }

    void quit(boolean safe) {
        if (isQuited) return;
        isQuited = true;
        Logger.debug("Quit, messages in queue: " + queue.size());
        if (!safe) {
            queue.clear();
        }
        // Tell looper to quit.
        POISON.when = System.currentTimeMillis();
        POISON.who = "KILLER";
        Logger.debug("Feed poison: %s", POISON);
        queue.offer(POISON);
    }
}

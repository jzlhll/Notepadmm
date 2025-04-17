package com.allan.baseparty.handler;

public class Handler {
    private final MessageQueue mQueue;
    private final Callback mCallback;

    public Handler() {
        this(Looper.myLooper());
    }

    public Handler(Looper looper) {
        this(looper, null);
    }

    public Handler(Looper looper, Callback callback) {
        this.mQueue = looper.getQueue();
        this.mCallback = callback;
    }

    public final void sendMessage(Message msg) {
        sendMessageDelayed(msg, 0L);
    }

    public final void sendMessageDelayed(Message msg, long delay) {
        msg.target = this;
        delay = Math.max(delay, 0L);
        msg.when = System.currentTimeMillis() + delay;
        mQueue.enqueueMessage(msg);
    }

    public final void post(Runnable r) {
        postDelayed(r, 0L);
    }

    public final void postDelayed(Runnable r, long delay) {
        Message msg = obtainMessage();
        msg.callback = r;
        sendMessageDelayed(msg, delay);
    }

    public void handleMessage(Message msg) {

    }

    private void handleCallback(Message msg) {
        msg.callback.run();
    }

    public final void dispatchMessage(Message msg) {
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            handleMessage(msg);
        }
    }

    /**
     * Remove any pending posts of messages with code 'what' that are in the
     * message queue.
     */
    public final boolean removeMessages(int what) {
        return mQueue.removeMessages(this, what);
    }

    /**
     * Remove any pending posts of callbacks and sent messages whose
     * <var>obj</var> is <var>token</var>.  If <var>token</var> is null,
     * all callbacks and messages will be removed.
     */
    public final void removeAllCallbacksAndMessages() {
        mQueue.removeAllCallbacksAndMessages(this);
    }

    public final void removeCallback(Runnable runnable) {
        mQueue.removeCallbacks(this, runnable);
    }

    /**
     * Returns a new {@link Message Message} from the global message pool. More efficient than
     * creating and allocating new instances. The retrieved message has its handler set to this instance (Message.target == this).
     *  If you don't want that facility, just call Message.obtain() instead.
     */
    public final Message obtainMessage()
    {
        return Message.obtain(this);
    }

    /**
     * Same as {@link #obtainMessage()}, except that it also sets the what member of the returned Message.
     *
     * @param what Value to assign to the returned Message.what field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what)
    {
        return Message.obtain(this, what);
    }

    /**
     *
     * Same as {@link #obtainMessage()}, except that it also sets the what and obj members
     * of the returned Message.
     *
     * @param what Value to assign to the returned Message.what field.
     * @param obj Value to assign to the returned Message.obj field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, Object obj)
    {
        return Message.obtain(this, what, obj);
    }

    /**
     *
     * Same as {@link #obtainMessage()}, except that it also sets the what, arg1 and arg2 members of the returned
     * Message.
     * @param what Value to assign to the returned Message.what field.
     * @param arg1 Value to assign to the returned Message.arg1 field.
     * @param arg2 Value to assign to the returned Message.arg2 field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, int arg1, int arg2)
    {
        return Message.obtain(this, what, arg1, arg2);
    }

    /**
     *
     * Same as {@link #obtainMessage()}, except that it also sets the what, obj, arg1,and arg2 values on the
     * returned Message.
     * @param what Value to assign to the returned Message.what field.
     * @param arg1 Value to assign to the returned Message.arg1 field.
     * @param arg2 Value to assign to the returned Message.arg2 field.
     * @param obj Value to assign to the returned Message.obj field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, int arg1, int arg2, Object obj)
    {
        return Message.obtain(this, what, arg1, arg2, obj);
    }

    public final boolean hasMessage(int what) {
        return mQueue.hasMessage(this, what);
    }

    public final boolean hasRunnable( Runnable runnable) {
        return mQueue.hasRunnable(this, runnable);
    }

    /**
     * Callback interface you can use when instantiating a Handler to avoid
     * having to implement your own subclass of Handler.
     */
    public interface Callback {
        /**
         * @param msg A {@link Message Message} object
         * @return True if no further handling is desired
         */
        public boolean handleMessage(Message msg);
    }
}

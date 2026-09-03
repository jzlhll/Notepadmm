package com.allan.atools.tools.modulenotepad.manager;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.util.function.LongConsumer;

/** 合并连续刷新请求，并限制单个后台任务运行。 */
final class LatestRefreshScheduler {
    private final long delayMs;
    private final long maxWaitMs;
    private final LongConsumer startAction;
    private final PauseTransition delay = new PauseTransition();
    private boolean pending;
    private boolean running;
    private boolean disposed;
    private long pendingSince;
    private long generation;

    LatestRefreshScheduler(long delayMs, long maxWaitMs, LongConsumer startAction) {
        this.delayMs = delayMs;
        this.maxWaitMs = maxWaitMs;
        this.startAction = startAction;
        delay.setOnFinished(event -> startNow());
    }

    void request() {
        if (disposed) {
            return;
        }
        pending = true;
        if (pendingSince == 0) {
            pendingSince = System.nanoTime();
        }
        schedule();
    }

    void startNow() {
        if (disposed || running) {
            return;
        }
        delay.stop();
        pending = false;
        pendingSince = 0;
        running = true;
        startAction.accept(++generation);
    }

    void complete(long taskGeneration, Runnable applyAction) {
        if (disposed || !running || taskGeneration != generation) {
            return;
        }
        running = false;
        try {
            if (applyAction != null) {
                applyAction.run();
            }
        } finally {
            schedule();
        }
    }

    void invalidate() {
        delay.stop();
        generation++;
        pending = false;
        running = false;
        pendingSince = 0;
    }

    void dispose() {
        disposed = true;
        invalidate();
        delay.setOnFinished(null);
    }

    private void schedule() {
        if (disposed || running || !pending) {
            return;
        }
        long waiting = (System.nanoTime() - pendingSince) / 1_000_000;
        long nextDelay = Math.min(delayMs, maxWaitMs - waiting);
        if (nextDelay <= 0) {
            startNow();
            return;
        }
        delay.setDuration(Duration.millis(nextDelay));
        delay.playFromStart();
    }
}

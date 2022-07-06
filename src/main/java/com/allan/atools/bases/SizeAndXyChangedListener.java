package com.allan.atools.bases;

import com.allan.baseparty.Action;
import com.allan.atools.beans.SizeAndXy;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Optional;

public final class SizeAndXyChangedListener {
    public SizeAndXyChangedListener(Stage s) {
        stage = s;
        mWindowInfo = new SizeAndXy();
        mWindowInfo.width = (float) s.getWidth();
        mWindowInfo.height = (float) s.getHeight();
        mWindowInfo.x = (float) s.getX();
        mWindowInfo.y = (float) s.getY();
    }

    public SizeAndXy getWindowInfo() { return mWindowInfo;}

    private static final boolean DEBUG = false;

    private final Stage stage;

    private final SizeAndXy mWindowInfo;

    private static final long DELAY_TIME = 1000;
    //这个时间是用于通知有变化size的，必须小一些
    private static final long DELAY_CHANGE_NOTIFY_TIME = 60;

    private Action<SizeAndXy> mChanged;
    private Runnable mChangedRunnable;

    private void trigger() {
        if (mChangedRunnable != null) {
            ThreadUtils.globalHandler().removeCallback(mChangedRunnable);
            ThreadUtils.globalHandler().postDelayed(mChangedRunnable, DELAY_CHANGE_NOTIFY_TIME);
        }
    }

    public void addListener(Action<SizeAndXy> changed) {
        mChanged = changed;
        if (mChanged != null) {
            mChangedRunnable = () -> Platform.runLater(()-> Optional.ofNullable(mChanged).ifPresent(a -> a.invoke(mWindowInfo)));
        } else {
            mChangedRunnable = null;
        }

        stage.widthProperty().addListener((observable, oldValue, newValue) -> {
            mWindowInfo.width = newValue.floatValue();
            if (DEBUG) {
                Log.d("width: " + oldValue + " new " + newValue);
            }
            trigger();
        });

        stage.heightProperty().addListener((observable, oldValue, newValue) -> {
            mWindowInfo.height = newValue.floatValue();
            if (DEBUG) {
                Log.d("height: " + oldValue + " new " + newValue);
            }
            trigger();
        });

        stage.xProperty().addListener((observable, oldValue, newValue) -> {
            mWindowInfo.x = newValue.floatValue();
            if (DEBUG) {
                Log.d("x: " + oldValue + " new " + newValue);
            }
            trigger();
        });
        stage.yProperty().addListener((observable, oldValue, newValue) -> {
            mWindowInfo.y = newValue.floatValue();
            if (DEBUG) {
                Log.d("y: " + oldValue + " new " + newValue);
            }
            trigger();
        });
    }
}

package com.allan.atools.bases;

import com.allan.atools.UIContext;
import com.allan.baseparty.Action;
import com.allan.atools.beans.SizeAndXy;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Optional;

public final class SizeAndXySaverImpl extends ISizeAndXySaver implements Runnable{
    private static final boolean DEBUG = false;

    private final Stage stage;
    private final String prefix;

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
        ThreadUtils.globalHandler().removeCallback(this);
        ThreadUtils.globalHandler().postDelayed(this, DELAY_TIME);
    }

    public SizeAndXySaverImpl(Stage stage, String prefix) {
        this.stage = stage;
        this.prefix = prefix;

        mWindowInfo = new SizeAndXy();
    }

    @Override
    public void run() {
        if (DEBUG) {
            Log.d("size changed confirm!!");
        }
        UIContext.sharedPref.edit()
                .putFloat(prefix + "width", mWindowInfo.width)
                .putFloat(prefix + "height", mWindowInfo.height)
                .putFloat(prefix + "x", mWindowInfo.x)
                .putFloat(prefix + "y", mWindowInfo.y).commit();
    }

    @Override
    public void loadCached() {
        if(UIContext.sharedPref.contains(prefix + "width")) {
            mWindowInfo.width = UIContext.sharedPref.getFloat(prefix + "width", 0);
            mWindowInfo.height = UIContext.sharedPref.getFloat(prefix + "height", 0);
            mWindowInfo.x = UIContext.sharedPref.getFloat(prefix + "x", 0);
            mWindowInfo.y = UIContext.sharedPref.getFloat(prefix + "y", 0);
        }
    }

    @Override
    public void afterSetData() {
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

    @Override
    public void setXyChangedListener(Action<SizeAndXy> changed) {
        mChanged = changed;
        if (mChanged != null) {
            mChangedRunnable = () -> Platform.runLater(()-> Optional.ofNullable(mChanged).ifPresent(a -> a.invoke(mWindowInfo)));
        } else {
            mChangedRunnable = null;
        }
    }

    @Override
    public void setSizeAndXy() {
        if (mWindowInfo.width > 1f) {
            stage.setHeight(mWindowInfo.height);
            stage.setWidth(mWindowInfo.width);
            stage.setX(mWindowInfo.x);
            stage.setY(mWindowInfo.y);
        }
    }
}

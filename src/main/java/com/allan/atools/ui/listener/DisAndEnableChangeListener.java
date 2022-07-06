package com.allan.atools.ui.listener;

import java.util.Optional;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public final class DisAndEnableChangeListener<T>
        implements ChangeListener<T> {
    private OtherControlChangeListener<T> listener;

    public void setListener(OtherControlChangeListener<T> listener) {
        this.listener = listener;
    }


    public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
        Optional.ofNullable(this.listener).ifPresent(l -> {
            l.onRemoveBeforeSetValue();
            l.onSetValue(newValue);
            l.onAfterSetValue();
        });
    }

    public interface OtherControlChangeListener<T> {
        void onRemoveBeforeSetValue();

        void onSetValue(T param1T);

        void onAfterSetValue();
    }
}

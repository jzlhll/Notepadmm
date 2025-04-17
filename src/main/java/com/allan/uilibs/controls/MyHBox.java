package com.allan.uilibs.controls;

import javafx.scene.layout.HBox;

public class MyHBox<T1> extends HBox {
    private T1 ex;

    public T1 getEx() {
        return ex;
    }

    public void setEx(T1 ex) {
        this.ex = ex;
    }
}

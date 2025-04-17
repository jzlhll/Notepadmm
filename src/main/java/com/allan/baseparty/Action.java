package com.allan.baseparty;

public interface Action<T> {
    void invoke(T t);
}
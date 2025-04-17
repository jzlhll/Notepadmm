package com.allan.baseparty;

public interface ActionR<T, R> {
    R invoke(T t);
}
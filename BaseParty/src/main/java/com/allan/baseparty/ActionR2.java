package com.allan.baseparty;

public interface ActionR2<T1, T2, R> {
    R invoke(T1 t1, T2 t2);
}

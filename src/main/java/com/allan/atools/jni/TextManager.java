package com.allan.atools.jni;

public final class TextManager {
    public native void say(String str);
    public static native Object generate(String str);
}
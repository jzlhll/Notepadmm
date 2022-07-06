package com.allan.atools.beans;

public final class SizeAndXy {
    public volatile float x, y;
    public volatile float width, height;

    @Override
    public String toString() {
        return "x " + x + " y " + y
                 + ", width*height " + width + ", " + height;
    }
}

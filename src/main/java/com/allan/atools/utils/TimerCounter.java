package com.allan.atools.utils;

import java.util.HashMap;
import java.util.Map;

public final class TimerCounter {
    private TimerCounter() {}

    private static class LongWrap {
        long cur;
        long start;
    }

    private static final class MMapHolder {
        private static final Map<String, LongWrap> mMap = new HashMap<>(2);
    }

    private static Map<String, LongWrap> getMap() {
        return MMapHolder.mMap;
    }

    public static void start(String tag) {
        Map<String, LongWrap> map = getMap();
        LongWrap wrap = new LongWrap();
        wrap.start = wrap.cur = System.currentTimeMillis();
        map.put(tag, wrap);
    }

    public static String center(String tag) {
        Map<String, LongWrap> map = getMap();
        LongWrap wrap = map.get(tag);
        if (wrap != null) {
            long cur = System.currentTimeMillis();
            String r = tag + ": 目前总耗时 " + (cur - wrap.start) + "ms, 一段耗时 " + (cur - wrap.cur) + "ms";
            wrap.cur = cur;
            return r;
        }

        return tag + ": 没有开始";
    }

    public static String end(String tag) {
        Map<String, LongWrap> map = getMap();
        LongWrap wrap = map.remove(tag);
        if (wrap != null) {
            long cur = System.currentTimeMillis();
            String r = tag + ": 总耗时 " + (cur - wrap.start) + "ms, 一段耗时 " + (cur - wrap.cur) + "ms";
            wrap.cur = cur;
            return r;
        }

        return tag + ": 没有开始何来结束";
    }
}

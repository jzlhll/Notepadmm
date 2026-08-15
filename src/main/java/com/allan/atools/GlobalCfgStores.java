package com.allan.atools;

import com.allan.baseparty.content.GsonCfgStore;

/**
 * 全局配置存储单例：持有 user / advance / recent 三个 GsonCfgStore，
 * 分别对应 ~/.atools_notepadmm/ 下的 user.json / advance.json / recent.json。
 */
public final class GlobalCfgStores {
    private static final GsonCfgStore USER = new GsonCfgStore("user");
    private static final GsonCfgStore ADVANCE = new GsonCfgStore("advance");
    private static final GsonCfgStore RECENT = new GsonCfgStore("recent");

    private GlobalCfgStores() {
    }

    public static GsonCfgStore user() {
        return USER;
    }

    public static GsonCfgStore advance() {
        return ADVANCE;
    }

    public static GsonCfgStore recent() {
        return RECENT;
    }

    public static void flushAll() {
        USER.flush();
        ADVANCE.flush();
        RECENT.flush();
    }
}

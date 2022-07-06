package com.allan.atools.tools.modulenotepad.manager;

public final class Util {
    /**
     * 50MB我们就提示超大。避免加载太慢导致bug
     */
    public static final long MAX_ALERT_FILE_SIZE = 50 * 1024 * 1024L;
}

package com.allan.atools.toolsstartup;

import com.allan.atools.utils.CacheLocation;
import com.allan.atools.utils.Log;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public final class InstanceLock {
    private static RandomAccessFile sLockFile;
    private static FileChannel sLockChannel;
    private static FileLock sLock;
    private static boolean sHookRegistered = false;

    private InstanceLock() {}

    /**
     * 尝试获取单实例文件锁。
     * @return true=获取成功（首个实例），false=已存在运行实例
     */
    public static boolean tryLock() {
        try {
            String lockPath = CacheLocation.get("instance.lock");
            sLockFile = new RandomAccessFile(lockPath, "rw");
            sLockChannel = sLockFile.getChannel();
            sLock = sLockChannel.tryLock();
            if (sLock == null) {
                closeQuietly();
                return false;
            }
            registerShutdownHook();
            return true;
        } catch (Throwable t) {
            Log.e("instance lock tryAcquire failed, fallback allow start", t);
            closeQuietly();
            return true;
        }
    }

    private static void registerShutdownHook() {
        if (sHookRegistered) {
            return;
        }
        sHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(InstanceLock::release));
    }

    private static void release() {
        try {
            if (sLock != null && sLock.isValid()) {
                sLock.release();
            }
        } catch (Throwable ignored) {
        }
        closeQuietly();
    }

    private static void closeQuietly() {
        try {
            if (sLockChannel != null) {
                sLockChannel.close();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (sLockFile != null) {
                sLockFile.close();
            }
        } catch (Throwable ignored) {
        }
        sLock = null;
        sLockChannel = null;
        sLockFile = null;
    }
}

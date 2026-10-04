package com.allan.atools.tools.modulenotepad.log

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/** 所有日志页和在途任务共用的额度；实时堆余量同时涵盖普通编辑器及 Markdown。 */
object LogMemoryBudget {
    const val MIB = 1024L * 1024
    const val READ_BYTES = 5 * 1024 * 1024
    const val INITIAL_BYTES = 50 * MIB
    const val MAX_PAGE_CHARS = 250_000
    private val os = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
    val physicalMemory: Long = try { os?.totalMemorySize ?: -1L } catch (_: Exception) { -1L }
    val targetBytes: Long = if (physicalMemory > 0) physicalMemory / 10 else Runtime.getRuntime().maxMemory() / 2
    private var reserved = 0L

    // 未经平台实测的保守初值，不能据此宣称满足进程内存验收目标。
    const val TASK_RESERVATION = 24 * MIB

    class Lease(private val bytes: Long) : AutoCloseable {
        private var closed = false
        override fun close() = synchronized(LogMemoryBudget) {
            if (!closed) {
                reserved -= bytes
                closed = true
            }
        }
    }

    @Synchronized
    fun reserve(bytes: Long): Lease {
        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val heapHeadroom = runtime.maxMemory() - usedHeap
        val freeSystem = try { os?.freeMemorySize ?: Long.MAX_VALUE } catch (_: Exception) { Long.MAX_VALUE }
        val dataLimit = Math.min(targetBytes, runtime.maxMemory() * 3 / 4)
        if (reserved + bytes > dataLimit || heapHeadroom < bytes + 32 * MIB
            || freeSystem in 0 until bytes * 2) {
            throw IllegalStateException("内存额度不足，请关闭其他标签后重试")
        }
        reserved += bytes
        return Lease(bytes)
    }

    @JvmStatic
    fun isLog(path: Path): Boolean {
        val extension = path.fileName.toString().substringAfterLast('.', "")
        return extension.equals("txt", true) || extension.equals("log", true)
    }

    /** 大日志延迟分块读取，小文件及其他文本保留普通打开流程。 */
    @JvmStatic
    fun readEditable(path: Path, charset: Charset): String? {
        return if (isLog(path) && Files.size(path) > INITIAL_BYTES) null else Files.readString(path, charset)
    }
}

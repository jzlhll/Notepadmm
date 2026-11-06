package com.allan.atools.tools.modulenotepad.log

import com.allan.atools.utils.Log
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
        require(bytes >= 0)
        val runtime = Runtime.getRuntime()
        val maxHeap = runtime.maxMemory()
        val dataLimit = Math.min(targetBytes, maxHeap * 3 / 4)
        val withinBudget = bytes <= dataLimit - reserved
        var usedHeap = runtime.totalMemory() - runtime.freeMemory()
        if (withinBudget && maxHeap - usedHeap < bytes + 32 * MIB) {
            // 关闭标签后旧文档可能尚未回收；仅在堆余量不足时回收并复查一次。
            runtime.gc()
            usedHeap = runtime.totalMemory() - runtime.freeMemory()
        }
        // 系统空闲页不等于可分配内存，不能用它拒绝读取；保留全局额度和 JVM 堆保护。
        val reason = when {
            !withinBudget -> "data limit"
            maxHeap - usedHeap < bytes + 32 * MIB -> "heap headroom"
            else -> null
        }
        if (reason != null) {
            Log.e("memory reservation rejected: reason=$reason, requestedBytes=$bytes, reservedBytes=$reserved, " +
                "dataLimitBytes=$dataLimit, usedHeapBytes=$usedHeap, maxHeapBytes=$maxHeap")
            throw IllegalStateException("内存额度不足，请关闭其他标签后重试")
        }
        reserved += bytes
        return Lease(bytes)
    }

    /** 所有大文本延迟分块读取；小文件也须为文档模型预留堆余量。 */
    @JvmStatic
    fun readEditable(path: Path, charset: Charset): String? {
        val size = Files.size(path)
        if (size > INITIAL_BYTES) return null
        val lease = try { reserve(size * 6 + TASK_RESERVATION) } catch (_: IllegalStateException) { return null }
        val text = lease.use {
            Files.newBufferedReader(path, charset).use { reader ->
                val content = StringBuilder()
                val buffer = CharArray(32 * 1024)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    // 文件在打开过程中增长时仍必须受限。
                    if (content.length.toLong() + count > INITIAL_BYTES) return null
                    content.append(buffer, 0, count)
                }
                content.toString()
            }
        }
        val documentCost = text.length * 6L + text.count { it == '\n' } * 256L
        try { reserve(documentCost + TASK_RESERVATION).close() }
        catch (_: IllegalStateException) { return null }
        return text
    }
}

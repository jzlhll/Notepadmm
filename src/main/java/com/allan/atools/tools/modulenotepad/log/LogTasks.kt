package com.allan.atools.tools.modulenotepad.log

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 读取与全文搜索分别排队，搜索耗时不阻塞首屏加载。 */
object LogTasks {
    private fun executor(name: String) = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(16), { task -> Thread(task, name).apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    val pages = executor("log-page-reader")
    val scans = executor("log-file-scan")

    init {
        Runtime.getRuntime().addShutdownHook(Thread({
            pages.shutdownNow()
            scans.shutdownNow()
        }, "log-tasks-cleanup"))
    }

    fun cancel(task: Future<*>?) {
        task?.cancel(true)
        pages.purge()
        scans.purge()
    }
}

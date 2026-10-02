package com.allan.atools.toolsstartup

import com.allan.atools.tools.modulenotepad.manager.TyporaManager
import com.allan.atools.utils.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 主窗口显示后延迟启动后台任务队列，按入队顺序串行执行，退出时取消剩余任务。 */
object StartupDelayManager {
    private val pendingTasks = mutableListOf<Runnable>()
    private val executor = Executors.newSingleThreadExecutor { action ->
        Thread(action, "atools-startup-delay").apply { isDaemon = true }
    }
    private var started = false

    init {
        enqueue("Typora detection") { TyporaManager.getInstance().checkAvailability() }
    }

    @JvmStatic
    fun getInstance(): StartupDelayManager = this

    /** 任务在后台串行执行，应在 action 返回前完成工作，以保持队列顺序。 */
    @Synchronized
    fun enqueue(name: String, action: Runnable) {
        if (executor.isShutdown) return
        val task = Runnable {
            try {
                action.run()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                // 单个任务失败不阻止后续初始化任务。
                Log.e("Startup delayed task failed: $name", e)
            }
        }
        if (started) executor.execute(task) else pendingTasks.add(task)
    }

    @Synchronized
    fun start() {
        if (started || executor.isShutdown) return
        started = true
        // 等待仅发生在独立后台线程，后续任务由执行器自带队列按顺序执行。
        executor.execute {
            try {
                TimeUnit.SECONDS.sleep(1)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        pendingTasks.forEach(executor::execute)
        pendingTasks.clear()
    }

    @Synchronized
    fun shutdown() {
        pendingTasks.clear()
        executor.shutdownNow()
    }
}

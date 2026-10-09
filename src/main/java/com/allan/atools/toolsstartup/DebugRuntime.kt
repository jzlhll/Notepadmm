package com.allan.atools.toolsstartup

import java.nio.file.Files

/** 调试实例使用独立临时主目录，隔离锁、配置、日志和会话，并关闭系统文档集成。 */
object DebugRuntime {
    @JvmStatic
    val isEnabled = java.lang.Boolean.getBoolean("atools.debug")

    private var initialized = false

    @JvmStatic
    fun initialize() {
        if (!isEnabled || initialized) return
        // 必须先于日志和配置初始化；创建失败时直接终止，不能退回用户目录。
        val home = Files.createTempDirectory("atools-debug-").toRealPath()
        System.setProperty("user.home", home.toString())
        System.setProperty("apple.awt.application.name", "ATools Debug")
        initialized = true
        println("startup: isolated debug runtime, pid: ${ProcessHandle.current().pid()}, home: $home")
    }
}

package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.richtext.codearea.EditorAreaMgr
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.session.SaveResult
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import javafx.application.Platform
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 检测本机 Typora，并统一通过指定应用打开已保存的文档。 */
object TyporaManager {
    private val extensions = setOf(
        "md", "markdown", "mdown", "mmd", "text", "txt", "rmarkdown",
        "mkd", "mdwn", "mdtxt", "rmd", "qmd", "mdtext", "mdx"
    )
    @Volatile
    private var application: File? = null

    @JvmStatic
    fun getInstance(): TyporaManager = this

    fun isAvailable(): Boolean = application != null

    fun supports(file: File?): Boolean =
        file != null && file.extension.lowercase(Locale.ROOT) in extensions

    /** 由 StartupDelayManager 的后台队列调用，检测结果只在本次运行中保留。 */
    fun checkAvailability() {
        application = when {
            ResLocation.isOsx -> findMacApplication()
            ResLocation.isWindow -> findWindowsApplication()
            else -> null
        }
        Log.d("Typora detection completed: available=${isAvailable()}")
    }

    private fun findMacApplication(): File? {
        val candidates = sequence {
            yield(File("/Applications/Typora.app"))
            yield(File(System.getProperty("user.home"), "Applications/Typora.app"))
            // 按需查询 Spotlight，常见目录命中后不再启动外部进程。
            val paths = readCommand("/usr/bin/mdfind", "kMDItemCFBundleIdentifier == 'abnerworks.Typora'")
            yieldAll(paths.lineSequence().filter { it.isNotBlank() }.map { File(it.trim()) })
        }
        return candidates.firstOrNull { File(it, "Contents/MacOS/Typora").isFile }
    }

    private fun findWindowsApplication(): File? {
        val candidates = sequence {
            for (variable in listOf("ProgramW6432", "ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA")) {
                val root = System.getenv(variable)
                if (root.isNullOrBlank()) continue
                yield(File(root, "Typora/Typora.exe"))
                yield(File(root, "Programs/Typora/Typora.exe"))
            }
            for (directory in System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)) {
                if (directory.isNotBlank()) yield(File(directory.trim().trim('"'), "Typora.exe"))
            }
        }
        candidates.firstOrNull { it.isFile }?.let { return it }
        val systemRoot = System.getenv("SystemRoot") ?: return null
        // 使用固定脚本读取注册表，支持自定义安装目录；显式 UTF-8 保留中文路径。
        val script = """
            [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
            ${'$'}ErrorActionPreference = 'SilentlyContinue'
            ${'$'}roots = @('HKCU:\Software', 'HKLM:\Software', 'HKLM:\Software\WOW6432Node')
            foreach (${'$'}root in ${'$'}roots) {
                ${'$'}app = Get-ItemProperty (${'$'}root + '\Microsoft\Windows\CurrentVersion\App Paths\Typora.exe')
                if (${'$'}app.'(default)') { [Environment]::ExpandEnvironmentVariables(${'$'}app.'(default)') }
                Get-ItemProperty (${'$'}root + '\Microsoft\Windows\CurrentVersion\Uninstall\*') |
                    Where-Object { ${'$'}_.DisplayName -like 'Typora*' } | ForEach-Object {
                        if (${'$'}_.InstallLocation) { Join-Path ${'$'}_.InstallLocation 'Typora.exe' }
                        if (${'$'}_.DisplayIcon) { ${'$'}_.DisplayIcon -replace ',\s*-?\d+${'$'}', '' }
                    }
            }
            exit 0
        """.trimIndent()
        val paths = readCommand(
            File(systemRoot, "System32/WindowsPowerShell/v1.0/powershell.exe").path,
            "-NoProfile", "-NonInteractive", "-Command", script
        )
        return paths.lineSequence().filter { it.isNotBlank() }.map { File(it.trim().trim('"')) }
            .firstOrNull { it.name.equals("Typora.exe", ignoreCase = true) && it.isFile }
    }

    private fun readCommand(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        // 同时限制等待和输出，防止系统查询挂起或输出过多堵塞后台任务。
        process.onExit().orTimeout(4, TimeUnit.SECONDS).exceptionally {
            process.destroyForcibly()
            process
        }
        try {
            process.inputStream.use { output ->
                val bytes = output.readNBytes(65536)
                return if (process.waitFor(4, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    String(bytes, Charsets.UTF_8)
                } else ""
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    fun openEditor(editor: EditorAreaMgr) {
        if (!isAvailable() || editor.isDestroyed) return
        editor.save().whenComplete { result, throwable ->
            ThreadUtils.checkJfxAndEnqueue {
                if (editor.isDestroyed || ThreadUtils.sBeClosing) return@checkJfxAndEnqueue
                when {
                    throwable != null -> {
                        Log.e("Save before opening Typora failed", throwable)
                        SnackbarUtils.show(Locales.str("saveFileFailed"))
                    }
                    result == SaveResult.SUCCESS_CLEAN && !editor.documentState.isDirty ->
                        launchFile(editor.sourceFile)
                    result == SaveResult.SUCCESS_DIRTY || result == SaveResult.SUCCESS_CLEAN ->
                        SnackbarUtils.show(Locales.str("editor.typoraUnsavedChanges"))
                }
            }
        }
    }

    fun openFile(file: File) {
        val editor = AllEditorsManager.Instance.getAreaByFilePath(file)?.editor
        if (editor == null) launchFile(file) else openEditor(editor)
    }

    private fun launchFile(file: File?) {
        val app = application
        ThreadUtils.execute {
            if (ThreadUtils.sBeClosing) return@execute
            try {
                if (app == null || !app.exists() || file == null || !supports(file) || !file.isFile) {
                    throw IOException("Typora or target file is unavailable")
                }
                val builder = if (ResLocation.isOsx) {
                    ProcessBuilder("/usr/bin/open", "-a", app.absolutePath, file.absolutePath)
                } else {
                    ProcessBuilder(app.absolutePath, file.absolutePath)
                }
                val process = builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start()
                // Windows 主进程可能持续运行，只检查启动期间的失败，不等待应用退出。
                val exited = process.waitFor(if (ResLocation.isOsx) 5L else 1L, TimeUnit.SECONDS)
                if (exited && process.exitValue() != 0) {
                    throw IOException("Typora launch exited with code ${process.exitValue()}")
                }
                if (!exited && ResLocation.isOsx) {
                    process.destroyForcibly()
                    throw IOException("Typora launch timed out")
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                Log.e("Open in Typora failed", e)
                Platform.runLater {
                    if (!ThreadUtils.sBeClosing) SnackbarUtils.show(Locales.str("editor.openInTyporaFailed"))
                }
            }
        }
    }
}

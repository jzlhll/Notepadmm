package com.allan.atools.richtext.codearea

import java.io.BufferedReader
import java.io.Writer
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** 并发读写 CLI 管道，停止或超时时一并回收本次启动的子进程。 */
fun <T> runSelectionCli(builder: ProcessBuilder, timeoutSeconds: Long, write: (Writer) -> Unit,
                        read: (BufferedReader) -> T): T {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    val process = builder.start()
    val input = FutureTask { process.outputWriter(Charsets.UTF_8).use(write) }
    val output = FutureTask { process.inputReader(Charsets.UTF_8).use(read) }
    val stderr = FutureTask {
        process.errorReader(Charsets.UTF_8).useLines { lines -> lines.lastOrNull { it.isNotBlank() }?.take(500) }
    }
    val tasks = listOf(input, output, stderr)
    try {
        tasks.forEach { Thread.startVirtualThread(it) }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw CodexSelectionCli.Failure("editor.codex.timeout")
        val result = output.get(2, TimeUnit.SECONDS)
        if (process.exitValue() != 0) throw CodexSelectionCli.Failure("editor.codex.failed", stderr.get(2, TimeUnit.SECONDS))
        input.get(2, TimeUnit.SECONDS)
        return result
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    } catch (_: TimeoutException) {
        throw CodexSelectionCli.Failure("editor.codex.timeout")
    } finally {
        if (process.isAlive) {
            process.descendants().use { children -> children.forEach { it.destroyForcibly() } }
            process.destroyForcibly()
        }
        tasks.forEach { it.cancel(true) }
        if (process.isAlive) process.waitFor(1, TimeUnit.SECONDS)
    }
}

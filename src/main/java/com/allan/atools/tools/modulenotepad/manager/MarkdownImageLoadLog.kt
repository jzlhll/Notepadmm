package com.allan.atools.tools.modulenotepad.manager

import com.allan.atools.utils.FileLog
import com.allan.atools.utils.Log
import javafx.scene.image.Image
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** 图片单次加载的诊断记录，关联异步完成、失败和重试，并写入应用日志文件。 */
class MarkdownImageLoadLog(scope: String, destination: String, document: File?, line: Int) {
    companion object {
        private val sequence = AtomicLong()
    }

    private val started = System.nanoTime()
    private val finished = AtomicBoolean()
    private val context = "markdown image request=${sequence.incrementAndGet()} scope=$scope" +
        " document=${document?.absolutePath ?: "untitled"} line=${if (line > 0) line.toString() else "unknown"} url=$destination"

    init {
        event("start")
    }

    fun event(details: String) {
        val message = "$context elapsedMs=${(System.nanoTime() - started) / 1_000_000} $details"
        Log.d(message)
        FileLog.write("${Log.time()}: INFO: $message", false)
    }

    fun complete(image: Image?) {
        if (image == null) {
            fail("no image returned", null)
        } else if (image.isError) {
            fail("image load or decode failed progress=${image.progress}", image.exception)
        } else if (image.progress >= 1.0) {
            if (image.width <= 0 || image.height <= 0) {
                fail("invalid image dimensions width=${image.width} height=${image.height}", null)
            } else if (finished.compareAndSet(false, true)) {
                event("success width=${image.width} height=${image.height}")
            }
        }
    }

    fun fail(reason: String, error: Throwable?) {
        if (!finished.compareAndSet(false, true)) return
        val causes = ArrayList<String>()
        val seen = HashSet<Throwable>()
        var cause = error
        while (cause != null && seen.add(cause)) {
            causes.add("${cause.javaClass.name}: ${cause.message}")
            cause = cause.cause
        }
        Log.e("$context elapsedMs=${(System.nanoTime() - started) / 1_000_000} failed reason=$reason" +
            " causes=${causes.joinToString(" -> ")}", error)
    }
}

package com.allan.atools.toolsstartup

import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.sun.jna.CallbackProxy
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** 将应用的最近文件同步到 macOS 文档历史，由系统提供程序坞菜单和退出后的历史入口。 */
object MacRecentDocuments {
    @Volatile
    private var started = false
    @Volatile
    private var unavailable = false
    private val syncPending = AtomicBoolean(false)
    private val bridge by lazy { DocumentBridge() }

    @JvmStatic
    fun start() {
        if (!ResLocation.isOsx || DebugRuntime.isEnabled || started) return
        started = true
        requestSync()
    }

    @JvmStatic
    fun requestSync() {
        if (!started || unavailable || ThreadUtils.sBeClosing || !syncPending.compareAndSet(false, true)) return
        ThreadUtils.globalHandler().postDelayed({
            syncPending.set(false)
            if (unavailable || ThreadUtils.sBeClosing) return@postDelayed
            try {
                val files = (AllEditorsManager.readPinnedRecentFiles() +
                    AllEditorsManager.saveOrReadRecentFiles(null))
                    .distinct().filter { File(it).isFile }
                bridge.sync(files)
            } catch (e: Exception) {
                unavailable = true
                Log.e("macOS recent documents: synchronization failed", e)
            } catch (e: LinkageError) {
                unavailable = true
                Log.e("macOS recent documents: native bridge unavailable", e)
            }
        }, 200)
    }

    /** AppKit 操作在系统主线程执行；保留回调直到执行结束，避免原生代码引用被回收的回调。 */
    private class DocumentBridge {
        private val appKit = NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit")
        private val objc = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib")
        private val system = NativeLibrary.getInstance("/usr/lib/libSystem.B.dylib")
        private val message = objc.getFunction("objc_msgSend")
        private val getClass = objc.getFunction("objc_getClass")
        private val registerSelector = objc.getFunction("sel_registerName")
        private val mainQueue = system.getGlobalVariableAddress("_dispatch_main_q")
        private val dispatch = system.getFunction("dispatch_async_f")
        private val callbacks = ConcurrentHashMap.newKeySet<CallbackProxy>()

        fun sync(files: List<String>) {
            val callback = object : CallbackProxy {
                override fun getParameterTypes(): Array<Class<*>> = arrayOf(Pointer::class.java)
                override fun getReturnType(): Class<*> = Void.TYPE

                override fun callback(args: Array<Any?>): Any? {
                    try {
                        if (!unavailable && !ThreadUtils.sBeClosing) updateDocuments(files)
                    } catch (e: Throwable) {
                        unavailable = true
                        Log.e("macOS recent documents: AppKit synchronization failed", e)
                    } finally {
                        callbacks.remove(this)
                    }
                    return null
                }
            }
            callbacks.add(callback)
            try {
                dispatch.invokeVoid(arrayOf(mainQueue, null, callback))
            } catch (e: Throwable) {
                callbacks.remove(callback)
                throw e
            }
        }

        private fun updateDocuments(files: List<String>) {
            val pool = sendPointer(nativeClass("NSAutoreleasePool"), "new")
            try {
                val controller = sendPointer(nativeClass("NSDocumentController"), "sharedDocumentController")
                // 先准备 URL，避免转换失败时清空已有的系统历史。路径使用 UTF-8，保留中文、空格等字符。
                val urls = files.map { path ->
                    val bytes = File(path).absolutePath.toByteArray(Charsets.UTF_8)
                    Memory(bytes.size.toLong() + 1).use { text ->
                        text.write(0, bytes, 0, bytes.size)
                        text.setByte(bytes.size.toLong(), 0)
                        val string = sendPointer(nativeClass("NSString"), "stringWithUTF8String:", text)
                        sendPointer(nativeClass("NSURL"), "fileURLWithPath:", string)
                    }
                }
                message.invokeVoid(arrayOf(controller, selector("clearRecentDocuments:"), null))
                // 系统将每次登记的文件放在最前面，因此从旧到新登记，保持应用的固定及最近文件顺序。
                for (url in urls.asReversed()) {
                    message.invokeVoid(arrayOf(controller, selector("noteNewRecentDocumentURL:"), url))
                }
            } finally {
                message.invokeVoid(arrayOf(pool, selector("drain")))
            }
        }

        private fun nativeClass(name: String): Pointer =
            checkNotNull(getClass.invokePointer(arrayOf(name))) { "Objective-C class unavailable: $name" }

        private fun selector(name: String): Pointer =
            checkNotNull(registerSelector.invokePointer(arrayOf(name))) { "Objective-C selector unavailable: $name" }

        private fun sendPointer(receiver: Pointer, name: String, vararg arguments: Any): Pointer =
            checkNotNull(message.invokePointer(arrayOf(receiver, selector(name), *arguments))) {
                "Objective-C call returned nil: $name"
            }
    }
}

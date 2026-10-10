package com.allan.atools.richtext.codearea

import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.NativeLibrary

/** 记录系统剪贴板修订号，避免后台复制覆盖较新的内容。 */
class MarkdownClipboardSnapshot private constructor(private val revision: Long) {
    fun matches(): Boolean = readRevision() == revision

    companion object {
        private var unavailable = false
        private val counter: Counter? by lazy {
            when {
                ResLocation.isOsx -> MacCounter()
                ResLocation.isWindow -> WindowsCounter()
                else -> null
            }
        }

        fun capture(): MarkdownClipboardSnapshot? = readRevision()?.let { MarkdownClipboardSnapshot(it) }

        private fun readRevision(): Long? {
            if (unavailable) return null
            return try {
                counter?.read()
            } catch (error: Exception) {
                unavailable = true
                Log.e("Clipboard revision read failed", error)
                null
            } catch (error: LinkageError) {
                unavailable = true
                Log.e("Clipboard revision bridge unavailable", error)
                null
            }
        }
    }

    private interface Counter {
        fun read(): Long?
    }

    /** 系统维护的序号覆盖文本、图片、文件和自定义格式，无需读取内容。 */
    private class WindowsCounter : Counter {
        private val sequence = NativeLibrary.getInstance("user32")
            .getFunction("GetClipboardSequenceNumber", Function.ALT_CONVENTION)

        override fun read(): Long? {
            val value = sequence.invokeInt(emptyArray()).toLong() and 0xffffffffL
            // 系统返回零表示无法取得剪贴板访问权，不能视为有效修订号。
            return if (value == 0L) null else value
        }
    }

    /** 只读取通用剪贴板的所有权计数，每次调用独立释放临时 Objective-C 对象。 */
    private class MacCounter : Counter {
        init {
            check(Native.POINTER_SIZE == 8 && Native.LONG_SIZE == 8) { "Unsupported macOS native ABI" }
        }

        private val appKit = NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit")
        private val objc = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib")
        private val message = objc.getFunction("objc_msgSend")
        private val getClass = objc.getFunction("objc_getClass")
        private val registerSelector = objc.getFunction("sel_registerName")
        private val poolClass = checkNotNull(getClass.invokePointer(arrayOf("NSAutoreleasePool")))
        private val pasteboardClass = checkNotNull(getClass.invokePointer(arrayOf("NSPasteboard")))
        private val newSelector = checkNotNull(registerSelector.invokePointer(arrayOf("new")))
        private val drainSelector = checkNotNull(registerSelector.invokePointer(arrayOf("drain")))
        private val generalSelector = checkNotNull(registerSelector.invokePointer(arrayOf("generalPasteboard")))
        private val changeSelector = checkNotNull(registerSelector.invokePointer(arrayOf("changeCount")))

        override fun read(): Long {
            val pool = checkNotNull(message.invokePointer(arrayOf(poolClass, newSelector)))
            return try {
                val pasteboard = checkNotNull(message.invokePointer(arrayOf(pasteboardClass, generalSelector)))
                message.invokeLong(arrayOf(pasteboard, changeSelector))
            } finally {
                message.invokeVoid(arrayOf(pool, drainSelector))
            }
        }
    }
}

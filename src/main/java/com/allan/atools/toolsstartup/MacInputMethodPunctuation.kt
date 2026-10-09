package com.allan.atools.toolsstartup

import com.allan.atools.utils.FileLog
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure

/** 试验性补齐 JavaFX 27 在 macOS 上遗漏的单字符标点，并记录原生提交与按键转发。 */
object MacInputMethodPunctuation {
    private var bridge: InputBridge? = null
    private const val PREFIX = "macOS IME: "
    private const val PUNCTUATION = "，。？！：；（）【】「」『』《》〈〉、‘’“”…—·"
    private const val RAW_PUNCTUATION = ",.?!:;()[]<>/'\"\\-^"

    @JvmStatic
    fun start() {
        if (!ResLocation.isOsx || bridge != null ||
            System.getProperty("atools.macos.imePunctuation", "true") == "false") return
        try {
            check(System.getProperty("javafx.runtime.version", "").substringBefore('.').substringBefore('+') == "27") {
                "Unsupported JavaFX runtime"
            }
            check(Native.POINTER_SIZE == 8 && Native.LONG_SIZE == 8) { "Unsupported native ABI" }
            // Objective-C 持有回调地址，桥接对象必须存活到进程退出。
            val inputBridge = InputBridge()
            bridge = inputBridge
            inputBridge.install()
        } catch (error: Throwable) {
            Log.e(PREFIX + "bridge unavailable", error)
        }
    }

    @Structure.FieldOrder("location", "length")
    class TextRange : Structure(), Structure.ByValue {
        @JvmField var location: Long = -1L
        @JvmField var length: Long = 0L
    }

    interface InsertTextCallback : Callback {
        fun invoke(view: Pointer, selector: Pointer, text: Pointer?, replacementRange: TextRange)
    }

    interface KeyEventCallback : Callback {
        fun invoke(delegate: Pointer, selector: Pointer, event: Pointer, isDown: Byte, character: Short): Byte
    }

    interface DispatchCallback : Callback {
        fun invoke(context: Pointer?)
    }

    private data class Submission(
        val delegateId: Long,
        val eventId: Long,
        val timestamp: Double,
        val character: Char
    )

    /** 两个拦截点均运行于 AppKit 主线程，只读取 Glass 字段，不改写输入法组合状态。 */
    private class InputBridge {
        private val objc = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib")
        private val system = NativeLibrary.getInstance("/usr/lib/libSystem.B.dylib")
        private val message = objc.getFunction("objc_msgSend")
        private val registerSelector = objc.getFunction("sel_registerName")
        private val setImplementation = objc.getFunction("method_setImplementation")
        private val length = selector("length")
        private val characterAtIndex = selector("characterAtIndex:")
        private val insertText = selector("insertText:replacementRange:")
        private val sendKey = selector("sendJavaKeyEvent:isDown:character:")
        private val characters = selector("characters")
        private val timestamp = selector("timestamp")
        private val pending = HashMap<Long, Submission>()
        private var originalInsert: Function? = null
        private var originalKey: Function? = null
        private var bufferOffset = 0L
        private var enabledOffset = 0L
        private var handlingOffset = 0L
        private var committedOffset = 0L
        private var eventOffset = 0L
        private var delegateOffset = 0L
        private var attributedString: Pointer? = null
        private var disabled = false
        private var traceCount = 0
        private var insertCount = 0

        private val insertCallback = object : InsertTextCallback {
            override fun invoke(view: Pointer, selector: Pointer, text: Pointer?, replacementRange: TextRange) {
                var submission: Submission? = null
                if (!disabled) {
                    try {
                        submission = captureSubmission(view, text)
                    } catch (error: Throwable) {
                        disable("insert inspection failed", error)
                    }
                }
                // 原方法只调用一次；组合文本及正常英文输入仍由 Glass 处理。
                try {
                    originalInsert!!.invokeVoid(arrayOf(view, selector, text, replacementRange))
                } catch (error: Throwable) {
                    disable("original insert failed", error)
                    return
                }
                if (!disabled && submission != null) {
                    try {
                        if (view.getByte(committedOffset).toInt() == 0) {
                            pending[submission.delegateId] = submission
                        } else {
                            trace("insert already committed, event=${submission.eventId}")
                        }
                    } catch (error: Throwable) {
                        disable("insert tracking failed", error)
                    }
                }
            }
        }

        private val keyCallback = object : KeyEventCallback {
            override fun invoke(delegate: Pointer, selector: Pointer, event: Pointer, isDown: Byte, character: Short): Byte {
                var forwarded = character
                if (!disabled && isDown.toInt() != 0) {
                    try {
                        val submission = pending.remove(Pointer.nativeValue(delegate))
                        val raw = singleCharacter(message.invokePointer(arrayOf(event, characters)))
                        val matches = submission != null && submission.eventId == Pointer.nativeValue(event) &&
                                submission.timestamp == message.invokeDouble(arrayOf(event, timestamp))
                        if (matches && character.toInt() == 0) {
                            forwarded = submission!!.character.code.toShort()
                        }
                        if (matches || isPunctuation(raw)) {
                            trace("key down event=${Pointer.nativeValue(event)}, raw=${code(raw)}, " +
                                    "nativeOverride=${code(character)}, forwarded=${code(forwarded)}, " +
                                    "matched=$matches, repaired=${forwarded != character}")
                        }
                    } catch (error: Throwable) {
                        forwarded = character
                        disable("key inspection failed", error)
                    }
                }
                // 只替换字符参数，保留原始物理键、修饰键及消费结果，不另发一次输入事件。
                return try {
                    (originalKey!!.invokeInt(arrayOf(delegate, selector, event, isDown, forwarded)) and 0xff).toByte()
                } catch (error: Throwable) {
                    disable("original key forwarding failed", error)
                    1.toByte()
                }
            }
        }

        private val installCallback = object : DispatchCallback {
            override fun invoke(context: Pointer?) {
                try {
                    installOnMainThread()
                } catch (error: Throwable) {
                    disable("hook installation failed", error)
                }
            }
        }

        fun install() {
            val mainQueue = system.getGlobalVariableAddress("_dispatch_main_q")
            system.getFunction("dispatch_async_f").invokeVoid(arrayOf(mainQueue, null, installCallback))
        }

        private fun installOnMainThread() {
            val viewClass = nativeClass("GlassView3D")
            val delegateClass = nativeClass("GlassViewDelegate")
            attributedString = nativeClass("NSAttributedString")
            val insertMethod = checkedMethod(viewClass, insertText, "v@:@{_NSRange=QQ}", "v@:@{NSRange=QQ}")
            val keyMethod = checkedMethod(delegateClass, sendKey, "B@:@BS", "c@:@cS")
            bufferOffset = fieldOffset(viewClass, "nsAttrBuffer", "@\"NSAttributedString\"")
            enabledOffset = fieldOffset(viewClass, "imEnabled", "B", "c")
            handlingOffset = fieldOffset(viewClass, "handlingKeyEvent", "B", "c")
            committedOffset = fieldOffset(viewClass, "didCommitText", "B", "c")
            eventOffset = fieldOffset(viewClass, "lastKeyEvent", "@\"NSEvent\"")
            delegateOffset = fieldOffset(viewClass, "_delegate", "@\"GlassViewDelegate\"")
            val getImplementation = objc.getFunction("method_getImplementation")
            val insertImplementation = checkNotNull(getImplementation.invokePointer(arrayOf(insertMethod)))
            val keyImplementation = checkNotNull(getImplementation.invokePointer(arrayOf(keyMethod)))
            originalInsert = Function.getFunction(insertImplementation)
            originalKey = Function.getFunction(keyImplementation)
            val insertReplacement = CallbackReference.getFunctionPointer(insertCallback)
            val keyReplacement = CallbackReference.getFunctionPointer(keyCallback)
            setImplementation.invokePointer(arrayOf(keyMethod, keyReplacement))
            try {
                setImplementation.invokePointer(arrayOf(insertMethod, insertReplacement))
            } catch (error: Throwable) {
                setImplementation.invokePointer(arrayOf(keyMethod, keyImplementation))
                throw error
            }
            trace("hooks installed, runtime=${System.getProperty("javafx.runtime.version")}")
        }

        private fun captureSubmission(view: Pointer, value: Pointer?): Submission? {
            val delegate = view.getPointer(delegateOffset)
            val delegateId = Pointer.nativeValue(delegate)
            pending.remove(delegateId)
            val text = if (value != null && (message.invokeInt(arrayOf(value, selector("isKindOfClass:"), attributedString)) and 0xff) != 0) {
                message.invokePointer(arrayOf(value, selector("string")))
            } else value
            val textLength = if (text == null) 0L else message.invokeLong(arrayOf(text, length))
            val committed = singleCharacter(text)
            val event = view.getPointer(eventOffset)
            val raw = if (event == null) null else singleCharacter(message.invokePointer(arrayOf(event, characters)))
            val enabled = view.getByte(enabledOffset).toInt() != 0
            val handling = view.getByte(handlingOffset).toInt() != 0
            val buffer = view.getPointer(bufferOffset)
            val markedLength = if (buffer == null) 0L else message.invokeLong(arrayOf(buffer, length))
            val flags = if (event == null) 0L else message.invokeLong(arrayOf(event, selector("modifierFlags")))
            // Cmd/Ctrl 快捷键不参与修复；Shift/Option 产生的实际文本保持输入法提交值。
            val repair = enabled && handling && markedLength == 0L && delegate != null && event != null &&
                    committed != null && committed in PUNCTUATION && committed != raw &&
                    (flags and ((1L shl 18) or (1L shl 20))) == 0L
            if (insertCount++ < 3 || isPunctuation(committed) || isPunctuation(raw)) {
                val context = message.invokePointer(arrayOf(view, selector("inputContext")))
                val source = if (context == null) null else message.invokePointer(arrayOf(context, selector("selectedKeyboardInputSource")))
                val sourceBytes = if (source == null) null else message.invokePointer(arrayOf(source, selector("UTF8String")))
                trace("insert event=${Pointer.nativeValue(event)}, source=${sourceBytes?.getString(0, "UTF-8") ?: "unknown"}, " +
                        "length=$textLength, committed=${if (isPunctuation(committed)) code(committed) else "other"}, " +
                        "raw=${if (isPunctuation(raw)) code(raw) else "other"}, " +
                        "enabled=$enabled, handling=$handling, marked=$markedLength, flags=$flags, candidate=$repair")
            }
            if (!repair) return null
            return Submission(delegateId, Pointer.nativeValue(event),
                message.invokeDouble(arrayOf(event, timestamp)), committed!!)
        }

        private fun singleCharacter(text: Pointer?): Char? {
            if (text == null || message.invokeLong(arrayOf(text, length)) != 1L) return null
            return (message.invokeInt(arrayOf(text, characterAtIndex, 0L)) and 0xffff).toChar()
        }

        private fun fieldOffset(type: Pointer, name: String, vararg encodings: String): Long {
            val field = checkNotNull(objc.getFunction("class_getInstanceVariable").invokePointer(arrayOf(type, name))) {
                "Native field unavailable: $name"
            }
            val encoding = objc.getFunction("ivar_getTypeEncoding").invokePointer(arrayOf(field))?.getString(0)
            check(encoding in encodings) { "Unsupported field type: $name ($encoding)" }
            return objc.getFunction("ivar_getOffset").invokeLong(arrayOf(field))
        }

        private fun checkedMethod(type: Pointer, selector: Pointer, vararg encodings: String): Pointer {
            val method = checkNotNull(objc.getFunction("class_getInstanceMethod").invokePointer(arrayOf(type, selector))) {
                "Native method unavailable"
            }
            val encoding = objc.getFunction("method_getTypeEncoding").invokePointer(arrayOf(method))?.getString(0)
            check(encoding?.replace(Regex("[0-9]+"), "") in encodings) { "Unsupported method signature: $encoding" }
            return method
        }

        private fun nativeClass(name: String): Pointer =
            checkNotNull(objc.getFunction("objc_getClass").invokePointer(arrayOf(name))) { "Native class unavailable: $name" }

        private fun selector(name: String): Pointer =
            checkNotNull(registerSelector.invokePointer(arrayOf(name))) { "Native selector unavailable: $name" }

        private fun disable(reason: String, error: Throwable) {
            disabled = true
            pending.clear()
            Log.e(PREFIX + reason, error)
        }

        private fun trace(message: String) {
            // 只记录有限次标点码点和状态，不记录普通正文；安装记录和错误不受调试日志开关影响。
            if (traceCount >= 200) return
            traceCount++
            val line = PREFIX + message
            Log.d(line)
            FileLog.write(line, false)
        }
    }

    private fun isPunctuation(character: Char?): Boolean =
        character != null && (character in PUNCTUATION || character in RAW_PUNCTUATION)

    private fun code(character: Char?): String = if (character == null) "none" else "U+%04X".format(character.code)

    private fun code(character: Short): String = if (character.toInt() == 0) "none" else "U+%04X".format(character.toInt() and 0xffff)
}

package com.allan.atools.ui.controls

import javafx.scene.AccessibleAttribute
import javafx.scene.control.TextArea

/** 拒绝辅助功能客户端过期的文本范围，避免 macOS 桥接层截取字符串时越界。 */
open class AccessibleTextArea : TextArea() {
    override fun queryAccessibleAttribute(attribute: AccessibleAttribute, vararg parameters: Any?): Any? {
        val result = super.queryAccessibleAttribute(attribute, *parameters)
        if (attribute == AccessibleAttribute.TEXT && result is String) {
            val range = parameters.singleOrNull() as? IntArray
            if (range != null && (range.size != 2 || range[0] < 0 || range[1] < 0
                        || range[0] > result.length || range[1] > result.length - range[0])) {
                // JavaFX 会用原始范围再次截取返回值；无效请求必须返回 null，不能仅裁剪文本。
                return null
            }
        }
        return result
    }
}

package com.allan.atools.richtext.codearea

import com.allan.atools.tools.modulenotepad.StaticsProf

/** Markdown 使用独立的实时处理阈值，其他文件沿用原有高亮配置。 */
object EditorProcessingLimits {
    data class Limits(val maxSize: Int, val maxLines: Int)

    private val markdown = Limits(10 * 1024 * 1024, 80_000)

    @JvmStatic
    fun forName(name: String): Limits {
        return if (MarkdownEditorSupport.isMarkdownName(name)) markdown
            else Limits(StaticsProf.getMaxFileSizeForStyle(), 10_000)
    }
}

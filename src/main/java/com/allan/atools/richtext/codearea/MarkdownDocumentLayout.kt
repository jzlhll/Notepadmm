package com.allan.atools.richtext.codearea

import javafx.scene.Node
import java.util.Locale

/** 根据 Markdown 系列扩展名选择文档留白，不改变各格式的语法支持范围。 */
object MarkdownDocumentLayout {
    private val extensions = setOf(
        "md", "markdown", "mdown", "mmd", "rmarkdown", "mkd", "mdwn",
        "mdtxt", "rmd", "qmd", "mdtext", "mdx"
    )

    @JvmStatic
    fun apply(area: Node, fileName: String) {
        val markdown = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions
        val styleClass = "markdown-document-editor"
        if (markdown) {
            if (styleClass !in area.styleClass) area.styleClass.add(styleClass)
        } else area.styleClass.remove(styleClass)
    }
}

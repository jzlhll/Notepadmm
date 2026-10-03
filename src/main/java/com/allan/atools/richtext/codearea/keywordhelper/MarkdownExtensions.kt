package com.allan.atools.richtext.codearea.keywordhelper

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension

/** 解析和输出使用相同扩展，避免预览、目录与编辑器对语法理解不一致。 */
object MarkdownExtensions {
    @JvmStatic
    fun all(): List<Extension> = listOf(TablesExtension.create(),
        StrikethroughExtension.builder().requireTwoTildes(true).build(),
        AutolinkExtension.create(), TaskListItemsExtension.create(), FootnotesExtension.create(),
        YamlFrontMatterExtension.create(), MarkdownDocumentExtension())
}

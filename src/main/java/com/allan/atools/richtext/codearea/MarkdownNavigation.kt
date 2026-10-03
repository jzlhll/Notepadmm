package com.allan.atools.richtext.codearea

import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.Locales
import java.io.File
import java.net.URI

/** 本地跳转等待目标文档加载完成，锚点与大纲共用标题规则。 */
object MarkdownNavigation {
    @JvmStatic
    fun open(area: EditorArea, uri: URI) {
        if (area.editor.isDestroyed) return
        val path = uri.path.orEmpty()
        val anchor = uri.fragment.orEmpty()
        if (path.isEmpty()) { jump(area, anchor); return }
        if (uri.scheme == null && uri.rawAuthority != null) return
        val file = try {
            if (uri.scheme.equals("file", true)) File(URI("file", uri.authority, path, null, null))
            else {
                val localPath = if (com.allan.atools.utils.ResLocation.isWindow && uri.scheme?.length == 1)
                    "${uri.scheme}:$path" else path
                val direct = File(localPath.replace('\\', '/'))
                if (direct.isAbsolute) direct else area.editor.sourceFile?.parentFile?.resolve(localPath.replace('\\', '/')) ?: return
            }
        } catch (_: Exception) { return }
        if (!file.isFile) { SnackbarUtils.show(Locales.str("markdown.localLinkMissing")); return }
        // 文档以标签打开，其他文件交由系统处理。
        if (file.extension.lowercase(java.util.Locale.ROOT) !in setOf("md", "markdown", "txt", "log", "json", "yaml", "yml", "xml", "csv")) {
            com.allan.atools.threads.ThreadUtils.execute {
                try { if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(file) }
                catch (error: Exception) { com.allan.atools.utils.Log.e("Open local markdown destination failed", error) }
            }
            return
        }
        AllEditorsManager.Instance.openMarkdownDocument(file.canonicalFile) { target -> jump(target, anchor) }
    }

    private fun jump(area: EditorArea, anchor: String) {
        if (anchor.isEmpty() || area.editor.isDestroyed || area.editor.isRealtimeProcessingLimitReached) return
        val manager = area.editor as? EditorAreaMgrCode ?: return
        val state = manager.markdownSnapshot(area.text)
        val heading = state.headings.firstOrNull { it.anchor == anchor } ?: run {
            SnackbarUtils.show(Locales.str("markdown.anchorMissing")); return
        }
        area.moveTo(state.lines[heading.line].start)
        area.showParagraphAtTop(heading.line)
        area.requestFocus()
    }
}

package com.allan.atools.richtext.codearea;

import java.io.File;
import java.util.Locale;

/** Markdown 文件识别及段落预览共用的编辑器布局参数。 */
public final class MarkdownEditorSupport {
    private MarkdownEditorSupport() {}

    public static boolean supportsMarkdown(EditorArea area) {
        return area != null && isMarkdownFile(area.getEditor().getSourceFile());
    }

    public static boolean isMarkdownFile(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    public static double textLeftPadding(EditorArea area) {
        // editor.css 中正文左留白与编辑器右留白对称，直接读取 CSS 布局值。
        return area.getPadding().getRight();
    }
}

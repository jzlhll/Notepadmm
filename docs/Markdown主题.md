# Markdown 主题

功能、操作和验收待办统一见 [Markdown 文档](Markdown.md)。内置明暗主题及以下自定义 CSS 功能已有实现，不属于待实现的第三批功能。

ATools 内置 GitHub 浅色与 GitHub Dark 深色两套 Markdown 主题。设置中点击“Markdown 主题”旁的主题名称打开选择对话框，点击主题即时应用。总黑暗模式关闭时只能选择浅色主题，开启时只能选择深色主题；另一类仍显示，并说明不可选原因。两种模式分别保存选择，切换总开关或重启应用后恢复对应主题。

主题只作用于 Markdown 编辑器；普通文本、日志编辑器和应用整体界面保持自己的配色。Markdown 排版预览和 HTML 导出同步主题配色，JavaFX 专用的布局和控件样式只作用于编辑器。

## 制作和替换主题

1. 在主题对话框点击“导出主题模板”，得到当前明暗模式对应的完整 CSS 模板。
2. 修改顶部的 `ATools-Theme-Name` 和 `ATools-Theme-Mode`，再修改 `.markdown-editor` 配色块中的颜色。
3. 点击“导入 CSS”。文件会复制到用户主题目录，列入主题列表；模式匹配时自动应用。
4. 点击“打开主题文件夹”，在该目录编辑已导入的 CSS，然后点击“重新加载”即时替换样式。重新加载会更新 JavaFX 样式缓存，无需重启应用。

主题目录为 `~/.atools_notepadmm/markdown-themes/`，Windows 位于用户主目录下的同名路径。应用启动时和点击“重新加载”时扫描其中的 `.css` 文件，支持多个自定义主题。导入时如果文件名重复，会自动添加数字后缀保留已有文件。需要替换同一主题时直接编辑主题目录中的原文件；删除文件后重新加载即可移除主题。主题无效、丢失或模式不匹配时回退到对应内置主题，CSS 加载错误会在对话框说明。

## CSS 格式

主题是 UTF-8 编码的 JavaFX CSS；单个文件不超过 1 MB。文件顶部第一个注释声明模式，`light` / `dark` 为固定值；名称可省略，此时使用文件名。主题标识使用文件名，修改显示名称不会丢失选择，重命名文件会成为另一个主题。

```css
/*
 * ATools-Theme-Name: 我的浅色主题
 * ATools-Theme-Mode: light
 */
.markdown-editor {
    -au-editor-bg-color: #ffffff;
    -au-editor-text-color: #333333;
    -au-md-link: #4183c4;
    -au-md-code-bg: #f8f8f8;
}

.markdown-editor .markdown-title-1 {
    -fx-font-size: 2.25em;
}
```

配色块只需写要覆盖的颜色，其余颜色继承当前模式的内置主题。配色变量放在独立的 `.markdown-editor { ... }` 规则内；允许多个独立配色块，重复变量按 CSS 顺序与 `!important` 优先级确定最终值，使用十六进制、`rgb` / `rgba` 或颜色名，并以分号结束。配色块中的 `-au-*` 值必须是颜色字面量，以保证预览和导出同步；不要使用 `derive(...)`、`var(...)` 或引用其他变量。

主题样式仅安装到编辑器节点，所有规则均须限定在 `.markdown-editor` 内，否则拒绝加载。配色变量不能放在带额外类名、伪类或祖先条件的规则中。允许使用 `.markdown-editor:hover` 或 `.markdown-editor .markdown-title-1` 等规则调整控件样式；字体使用应用现有字体或已安装的系统字体，不支持 `@font-face`。控件属性使用 `-fx-*`；文字节点使用 `-fx-fill`，行内文本背景使用 RichTextFX 的 `-rtfx-background-color`。不支持直接导入 Typora 的浏览器 CSS：`#write`、`h1`、CSS 自定义属性 `--xxx` 和 CodeMirror 选择器需要适配为 ATools 的节点选择器和配色变量。涉及相对路径的附加资源需自行放入主题文件旁；“导入 CSS”仅复制选中的 CSS 文件。

完整模板见 `src/main/resources/css/markdown-themes/github.css` 和 `github-dark.css`。常用变量如下：

| 作用 | 配色变量 |
| --- | --- |
| 正文背景、文字、光标、选区 | `-au-editor-bg-color`、`-au-editor-text-color`、`-au-editor-caret-color`、`-au-editor-selection-words-color` |
| 六级标题、标题下划线 | `-au-md-title-1` 至 `-au-md-title-6`、`-au-md-heading-border` |
| 语法标记、文本高亮 | `-au-md-syntax`、`-au-md-highlight` |
| 引用文字、竖线、背景 | `-au-md-quote`、`-au-md-quote-border`、`-au-md-quote-bg` |
| 代码块背景、文字、围栏 | `-au-md-code-bg`、`-au-md-code-fill`、`-au-md-code-fence` |
| 行内代码背景、文字 | `-au-md-inline-code-bg`、`-au-md-inline-code-fill` |
| 代码 token | `-au-md-code-keyword`、`-au-md-code-string`、`-au-md-code-comment`、`-au-md-code-punct`、`-au-md-code-tag`、`-au-md-code-tagmark`、`-au-md-code-attribute`、`-au-md-code-attribute-value` |
| 表格边框、交替行背景 | `-au-md-table-mark`、`-au-md-table-alternate` |
| 列表、链接、图片标记 | `-au-md-list`、`-au-md-link`、`-au-md-image` |
| 图片边框、占位文字 | `-au-md-image-frame-border`、`-au-md-image-placeholder-fill` |

标题样式类为 `.markdown-title-1` 至 `.markdown-title-6`；其他可用样式包括 `.markdown-code`、`.markdown-inline-code`、`.markdown-link`、`.markdown-quote`、`.markdown-list`、`.markdown-table-preview-cell`，以及段落类 `.md-heading-1` 至 `.md-heading-6`、`.md-code-block-first` / `mid` / `last` / `single`。完整语义样式见 `src/main/resources/css/editor_markdown.css`，JavaFX CSS 属性见 [官方 CSS 参考](https://openjfx.io/javadoc/27/javafx.graphics/javafx/scene/doc-files/cssref.html)。

## 参考资料与适配

内置主题配色参考：

- [Typora 官方 GitHub 主题](https://github.com/typora/typora-default-themes/blob/master/themes/github.css)：正文 `#333333`、链接 `#4183c4`、浅灰代码底色、六级标题比例、一级和二级标题分隔线、表格交替行。
- [GitHub Night 0.6.2](https://github.com/kinoute/typora-github-night-theme/blob/master/github-night.css)：背景 `#0d1117`、正文 `#c9d1d9`、链接 `#58a6ff`、代码背景 `#161b22`、低饱和引用色及 CodeMirror token 配色。
- [Typora 主题说明](https://theme.typora.io/doc/)：主题采用独立 CSS 文件的组织方式。

只移植 Markdown 配色和适合现有编辑器的排版规则，保留 ATools 的字体设置、编辑器留白、段落定位、表格编辑及离线图表机制；不引入 Typora 的字体文件、应用界面样式或网络字体依赖。

下载文件 SHA-256：

- `github.css`：`caa2453ea01990f66331fe8781c51766b692d7c95f74853e16e2128c2d33b560`
- `github-night.css`：`8e1f758ff151a1d4de9c0b4f077e798c3460466c89a17f1446f580d6d82206fe`

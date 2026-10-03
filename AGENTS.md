# AGENTS.md

专业日志分析与 Markdown 编辑并重的桌面工具，项目名 Notepadmm，应用名 ATools；支持 Windows 与 macOS。使用 Java 17、Kotlin、JavaFX 21、RichTextFX 0.11.7 和 Gradle。

- 凡是新增代码，能使用 Kotlin 就使用 Kotlin；不因此改写已有代码。

## 架构概览

- `app`（目录 `src`）是主应用；`BaseUiLibs` 提供编辑器与 UI 基础能力，`BaseParty` 提供线程、回调和通用工具。
- 主应用以 JavaFX Controller／FXML 组织界面，功能管理器负责多标签、工作区、搜索结果、Markdown 展示与会话恢复。编辑器基于 RichTextFX `CodeArea`，文档内容、保存状态与视图位置分别管理。
- 日志分析由文本搜索、底部定位、高级多条件着色及结果窗口协作完成；Markdown 使用 commonmark-java AST（GFM table、strikethrough），大纲、图片、表格、代码块与离线 Mermaid 由各自管理器提供。
- 会话快照与文件保存由后台任务处理，UI 更新回到 JavaFX 线程；高亮与预览按文档版本调度，并保留大文档降级逻辑。
- 主应用源码在 `src/main/java/com/allan/atools/`，资源在 `src/main/resources/`；Markdown 样式位于 `css/editor_markdown.css`，主题颜色由 `css/colors*.css` 提供。

## Skills

全局 skills 中仅使用以下项目定制 skills：

- `better-rebase`：仅在用户明确点名时使用。
- `code-review`：仅在用户要求 Code Review 或审查提交时使用。

忽略且不加载以下 Android 项目 skills：

- `android-shadow-blur`、`android-strings-dev`、`api-auto-generation`、`bitmap-handling`
- `compose-usage`、`fragment-bottom-sheet-dialog`、`glide-imageview`、`gson-usage`
- `image-picker-camera`、`input-method`、`koin-di`、`layout-xml-fragment`
- `livedata-usage`、`mmkv-usage`、`recycler-view-framework`、`room-database`
- `uri-parse-info`、`viewmodel-flow-framework`

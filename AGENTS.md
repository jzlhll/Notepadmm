# AGENTS.md

JavaFX + Gradle 多模块笔记编辑器（Java 17 / JavaFX 21 / richtextfx 0.11.5）。

## 模块

| 模块 | 职责 |
|---|---|
| `BaseParty` | 基础工具库（Handler、Action、反射等） |
| `BaseUiLibs` | UI 基础库（CodeArea、行号工厂等） |
| `src` | 主应用 `atools`，源码在 `src/main/java/com/allan/atools/` |

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

## 编辑器 / Markdown

- 编辑器基于 RichTextFX `CodeArea`。
- Markdown 语法使用 commonmark-java AST 解析，支持 GFM table 与 strikethrough。
- Markdown 样式位于 `src/main/resources/css/editor_markdown.css`，主题颜色由 `colors*.css` 提供。

### 关键文件

| 文件 | 职责 |
|---|---|
| `src/main/java/com/allan/atools/richtext/codearea/EditorArea.kt` | 编辑器入口 |
| `src/main/java/com/allan/atools/richtext/codearea/keywordhelper/EditorKeywordHelperImplMarkdown.java` | Markdown 语法高亮 |
| `src/main/java/com/allan/atools/tools/modulenotepad/manager/MarkdownImageManager.java` | Markdown 行内图片 |
| `src/main/java/com/allan/atools/tools/modulenotepad/manager/MarkdownCodeBlockManager.java` | Markdown 代码块背景 |
| `BaseUiLibs/src/main/java/com/allan/uilibs/richtexts/CodeArea.java` | 编辑器基础能力 |

## 其他

- 编译安装参考 `build-install` skill 约定（不主动运行 Gradle）。

# AGENTS.md

JavaFX + Gradle 多模块笔记编辑器（Java 17 / JavaFX 21 / richtextfx 0.11.5）。

## 模块

| 模块 | 职责 |
|---|---|
| `BaseParty` | 基础工具库（Handler、Action、反射等） |
| `BaseUiLibs` | UI 基础库（CodeArea、行号工厂等） |
| `src` | 主应用 `atools`，源码在 `src/main/java/com/allan/atools/` |

## 编辑器 / Markdown 架构速查

- 渲染链路：`EditorArea.kt`（继承 `CodeArea`，markdown 文件时 `markdownStyleEnabled=true`）→ `CodeArea`（BaseUiLibs，纯文本 segment）→ `EditorKeywordHelperFactory.create(file)` → `EditorKeywordHelperImplMarkdown`（**commonmark-java 0.30 AST 解析**，GFM tables/strikethrough 扩展，`IncludeSourceSpans.BLOCKS_AND_INLINES` 拿 `SourceSpan.getInputIndex()` 全文偏移，事件扫描合成重叠区间生成 StyleSpans，支持嵌套样式叠加如 title+bold）→ `EditorKeywordHelperAbstract.triggerAllText()` → `area.setStyleSpans(0, spans)` → CSS 类 `src/main/resources/css/editor_keywords.css`
- `Parser` 为静态单例（commonmark Parser 线程安全）；temporary/search 高亮仍走基类 `getPattern()` 正则，与 AST 结果一起参与区间合成。

### 关键文件

| 文件 | 职责 |
|---|---|
| `src/main/java/com/allan/atools/richtext/codearea/EditorArea.kt` | 编辑器入口 |
| `src/main/java/com/allan/atools/richtext/codearea/EditorAreaMgr.java` | 保存（`area.getText()` + `Files.writeString`）、`resetText`、行号开关 |
| `src/main/java/com/allan/atools/richtext/codearea/EditorAreaMgrCode.java` | trigger 高亮、加载 editor_keywords.css |
| `src/main/java/com/allan/atools/richtext/codearea/keywordhelper/EditorKeywordHelperImplMarkdown.java` | Markdown AST 高亮核心（Image 节点标 `markdown-image`，与 link 区分） |
| `BaseUiLibs/src/main/java/com/allan/uilibs/richtexts/CodeArea.java` | `applyMarkdownTextStyle`：italic 加 Shear 变换、bold 加描边伪粗体（自加载 JetBrains Mono 字体无变体，CSS font-weight/font-style 不生效，效果均用代码实现） |
| `BaseUiLibs/src/main/java/com/allan/uilibs/richtexts/MyLineNumFactory.java` | 行号走 `setParagraphGraphicFactory` |

### 遗留资产与已知约束

- commonmark 扩展节点（TableBlock/Strikethrough 等 CustomNode）无专门 `visit()` 重载，需在 `visit(CustomBlock)`/`visit(CustomNode)` 中 instanceof 分发。
- `src/main/java/com/allan/atools/richtext/` 下 `LinkedImage` / `RealLinkedImage` / `LinkedImageOps` / `FoldableStyledArea` 是一套未启用的富文本图片体系（`Either<String, LinkedImage>` segment），未接入 Markdown 编辑器，`FoldableStyledArea` 无人实例化；segment ops 写法可参考。
- richtextfx 0.11.5 `ParagraphBox.computePrefHeight` 只算文本高度，**paragraph graphic 不撑高行高**；行内显示图片需 segment 方案或行高 hack（AST 已就绪，Image 节点可拿到 alt/destination）。
- Markdown 图片标签改造待办见 `~/Downloads/Markdown图片支持改造TODO.md`（个人文件，不入库）。

## 其他

- 图片解码已依赖 `com.twelvemonkeys.imageio:imageio-webp:3.13.1`。
- 编译安装参考 `build-install` skill 约定（不主动运行 Gradle）。

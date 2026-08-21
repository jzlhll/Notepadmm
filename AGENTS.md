# AGENTS.md

JavaFX + Gradle 多模块笔记编辑器（Java 17 / JavaFX 21 / richtextfx 0.11.5）。

## 模块

| 模块 | 职责 |
|---|---|
| `BaseParty` | 基础工具库（Handler、Action、反射等） |
| `BaseUiLibs` | UI 基础库（CodeArea、行号工厂等） |
| `src` | 主应用 `atools`，源码在 `src/main/java/com/allan/atools/` |

## 编辑器 / Markdown 架构速查

- 渲染链路：`EditorArea.kt`（继承 `CodeArea`，markdown 文件时 `markdownStyleEnabled=true`）→ `CodeArea`（BaseUiLibs，纯文本 segment）→ `EditorKeywordHelperFactory.create(file)` → `EditorKeywordHelperImplMarkdown`（**commonmark-java 0.30 AST 解析**，GFM tables/strikethrough 扩展，`IncludeSourceSpans.BLOCKS_AND_INLINES` 拿 `SourceSpan.getInputIndex()` 全文偏移，事件扫描合成重叠区间生成 StyleSpans，支持嵌套样式叠加如 title+bold）→ `EditorKeywordHelperAbstract.triggerAllText()` -> `area.setStyleSpans(0, spans)` -> CSS 类 `src/main/resources/css/editor_markdown.css`（markdown 皮肤：全部语法样式 + `-au-md-*` 配色变量，含 editor 背景/文字默认色；启动时先于 colors*.css 加载，深色主题在 colors_dark.css 覆盖变量）
- `Parser` 为静态单例（commonmark Parser 线程安全）；temporary/search 高亮仍走基类 `getPattern()` 正则，与 AST 结果一起参与区间合成。
- 行内图片显示：`NotepadController` 接线 `MarkdownImageManager`（仅 `.md`/`.markdown` 生效，随 currentAreaProp 切换绑定/解绑）→ 独立图片段落（含列表项/引用块中）经 commonmark 解析出 lineIndex/alt/destination → 相对 md 文件解析本地路径（含 URL 编码回退）或远程 URL → LRU 缓存（48 张）+ 后台线程加载（JavaFX 不支持的格式走 ImageIO + twelvemonkeys，如 webp）→ 图片本体放在 paragraph graphic 的零宽 Pane 中（relocate 到标签行下方，子节点溢出绘制，不推移文本），行高经段落样式 `pref-height:xxx` 撑高；滚轮缩放（按地址记忆比例）、双击复位；超大文档（`isRealtimeProcessingLimitReached`）自动停用并清理样式。

### 关键文件

| 文件 | 职责 |
|---|---|
| `src/main/java/com/allan/atools/richtext/codearea/EditorArea.kt` | 编辑器入口 |
| `src/main/java/com/allan/atools/richtext/codearea/EditorAreaMgr.java` | 保存（`area.getText()` + `Files.writeString`）、`resetText`、行号开关 |
| `src/main/java/com/allan/atools/richtext/codearea/EditorAreaMgrCode.java` | trigger 高亮、加载 editor_keywords.css |
| `src/main/java/com/allan/atools/richtext/codearea/keywordhelper/EditorKeywordHelperImplMarkdown.java` | Markdown AST 高亮核心（Image 节点标 `markdown-image`，与 link 区分；围栏代码块内容按 info 语言复用各语言 helper pattern 做 token 高亮，叠加样式 `markdown-code-*`） |
| `src/main/java/com/allan/atools/tools/modulenotepad/manager/MarkdownImageManager.java` | 行内图片显示：解析/缓存/渲染/缩放（装饰 `paragraphGraphicFactoryProperty()` 与行号共存） |
| `src/main/java/com/allan/atools/tools/modulenotepad/manager/MarkdownCodeBlockManager.java` | 代码块整块圆角矩形背景：后台解析收集代码块行区间，段落样式类 `md-code-block-first/mid/last/single` 在 TextFlow 上画背景；左右内缩与 editor.css 文本 padding 一致（65px，换行 105px），12px 圆角 |
| `BaseUiLibs/src/main/java/com/allan/uilibs/richtexts/CodeArea.java` | `applyMarkdownTextStyle`：italic 加 Shear 变换、bold 加描边伪粗体（自加载 JetBrains Mono 字体无变体，CSS font-weight/font-style 不生效，效果均用代码实现）；`PARAGRAPH_PREF_HEIGHT_PREFIX` 段落样式条目转 `-fx-pref-height` 内联样式 |
| `BaseUiLibs/src/main/java/com/allan/uilibs/richtexts/MyLineNumFactory.java` | 行号走 `setParagraphGraphicFactory`（ViewActions 接口 default 方法） |

### 遗留资产与已知约束

- commonmark 扩展节点（TableBlock/Strikethrough 等 CustomNode）无专门 `visit()` 重载，需在 `visit(CustomBlock)`/`visit(CustomNode)` 中 instanceof 分发。
- `src/main/java/com/allan/atools/richtext/` 下 `LinkedImage` / `RealLinkedImage` / `LinkedImageOps` / `FoldableStyledArea` 是一套未启用的富文本图片体系（`Either<String, LinkedImage>` segment），未接入 Markdown 编辑器，`FoldableStyledArea` 无人实例化；segment ops 写法可参考。
- richtextfx 0.11.5 `ParagraphBox.computePrefHeight` 只算文本高度，**paragraph graphic 不撑高行高**；行内图片已用 `pref-height:` 段落样式 hack 落地（见 MarkdownImageManager）。graphic 里零宽 Pane 放图片依赖"子节点溢出绘制"这一行为，ParagraphBox 若裁剪子节点则方案失效（0.11.5 不裁剪）。
- Markdown 图片标签改造待办见 `~/Downloads/Markdown图片支持改造TODO.md`（个人文件，不入库）。

## 其他

- 图片解码已依赖 `com.twelvemonkeys.imageio:imageio-webp:3.13.1`。
- 编译安装参考 `build-install` skill 约定（不主动运行 Gradle）。

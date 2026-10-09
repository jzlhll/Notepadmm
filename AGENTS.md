# AGENTS.md

专业日志分析与 Markdown 编辑并重的桌面工具，项目名 Notepadmm，应用名 ATools；支持 Windows 与 macOS。使用 Java 25、Kotlin 2.4.20、JavaFX 27、RichTextFX 0.11.7 和 Gradle 9.6.1。

- 凡是新增代码，能使用 Kotlin 就使用 Kotlin；不因此改写已有代码。

## 架构概览

- `app`（目录 `src`）是主应用；`BaseUiLibs` 提供编辑器与 UI 基础能力，`BaseParty` 提供线程、回调和通用工具。
- 主应用以 JavaFX Controller／FXML 组织界面，功能管理器负责多标签、工作区、搜索结果、Markdown 展示与会话恢复。编辑器基于 RichTextFX `CodeArea`，文档内容、保存状态与视图位置分别管理。
- 日志分析由文本搜索、底部定位、高级多条件着色及结果窗口协作完成；Markdown 使用 commonmark-java AST（GFM table、strikethrough），大纲、图片、表格、代码块与离线 Mermaid 由各自管理器提供。
- 会话快照与文件保存由后台任务处理，UI 更新回到 JavaFX 线程；高亮与预览按文档版本调度，并保留大文档降级逻辑。
- 主应用源码在 `src/main/java/com/allan/atools/`，资源在 `src/main/resources/`；Markdown 样式位于 `css/editor_markdown.css`，主题颜色由 `css/colors*.css` 提供。

## 日志查看

查看 macOS 日志使用 `rtk proxy rg -n '关键词' ~/.atools_notepadmm/log/MM_DD.log`（文件名替换为当天月日），debug 实例则读取其独立 `user.home` 下的同名日志目录。

## Agent 自测验证

- 仅在用户明确要求自测时执行，不主动新增测试代码；具备条件时必须编译当前源码、启动并验证相关操作，界面问题须验证实际交互。
- 开始前估算准备、编译、启动、交互及清理的总耗时：预计不超过 15 分钟直接执行，超过则列出项目、耗时及原因交由用户决定；执行中需超出预算也须说明。单次从准备开始最多 20 分钟，到时停止验证、关闭自测实例并清理，未经用户明确要求不得延长。
- 启动前检查 ATools／Java 进程、锁及数据路径，记录自测 PID；禁止操作用户实例、删除或绕过其锁，禁止复制用户会话、打开用户原始文档或修改系统 `HOME`／`CODEX_HOME`。
- 使用 `rtk proxy ./gradlew :app:runDebug --args='临时样例文件的绝对路径'`，或以独立 Java 进程启动当前编译产物并加入 JVM 参数 `-Datools.debug=true`；确认参数传入应用 JVM。`DebugRuntime.initialize()` 在日志及配置初始化前创建独立临时 `user.home`，锁、配置、日志和会话写入其 `.atools_notepadmm/`，标题为 `ATools Debug`，每次启动使用新目录及独立锁。
- 隔离须双向成立：debug 与正式版互不阻止启动，不共享锁、应用身份或文件转发；确认独立锁路径、持锁进程及无获取锁异常，不能仅凭窗口或成功日志判断（`InstanceLock` 异常时会放行）。检查 JVM 目录外的共享状态；debug 须关闭 macOS 最近文档同步及系统打开文件／退出事件注册。禁止通过 `open -a ATools`、文件关联或双击启动，无法确认隔离时不启动并报告原因。
- 自测允许必要的 Gradle 编译及运行，仅生成开发产物；禁止安装、发布、覆盖 `/Applications/ATools.app`、执行 `build.sh -f`，或修改用户安装包、系统关联及最近文档。
- 用临时样例覆盖触发步骤及相关交互；结束时只关闭自己启动且已记录 PID 的实例，清理自建资源，保留失败日志和证据。交付报告验证项目、结果、未覆盖项及具体原因；仅文档修改说明无需运行，不能以“待用户验收”代替未完成自测的说明。

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

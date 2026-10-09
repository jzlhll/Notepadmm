# AGENTS.md

专业日志分析与 Markdown 编辑并重的桌面工具，项目名 Notepadmm，应用名 ATools；支持 Windows 与 macOS。使用 Java 25、Kotlin 2.4.20、JavaFX 27、RichTextFX 0.11.7 和 Gradle 9.6.1。

- 凡是新增代码，能使用 Kotlin 就使用 Kotlin；不因此改写已有代码。

## 架构概览

- `app`（目录 `src`）是主应用；`BaseUiLibs` 提供编辑器与 UI 基础能力，`BaseParty` 提供线程、回调和通用工具。
- 主应用以 JavaFX Controller／FXML 组织界面，功能管理器负责多标签、工作区、搜索结果、Markdown 展示与会话恢复。编辑器基于 RichTextFX `CodeArea`，文档内容、保存状态与视图位置分别管理。
- 日志分析由文本搜索、底部定位、高级多条件着色及结果窗口协作完成；Markdown 使用 commonmark-java AST（GFM table、strikethrough），大纲、图片、表格、代码块与离线 Mermaid 由各自管理器提供。
- 会话快照与文件保存由后台任务处理，UI 更新回到 JavaFX 线程；高亮与预览按文档版本调度，并保留大文档降级逻辑。
- 主应用源码在 `src/main/java/com/allan/atools/`，资源在 `src/main/resources/`；Markdown 样式位于 `css/editor_markdown.css`，主题颜色由 `css/colors*.css` 提供。

## Agent 自测验证

- 仅在用户描述明确包含自测意图（如“修正并自测”“修改后自行验证”）时执行自测；用户未提到自测时，不主动自测。用户要求自测后，具备运行和操作条件的 agent 必须自行编译、启动程序并验证相关操作；界面问题必须验证实际交互，不能仅凭源码检查或编译通过判断已修复。不主动新增测试代码。
- 开始前简单评估自测范围和总耗时，包含编译、隔离准备、启动、交互验证及清理。预计不超过 15 分钟时直接执行；预计超过 15 分钟时，先列出验证项目、预计耗时及原因，由用户决定是否进行，不自行开始耗时自测。执行中发现需要超出该预算时，说明剩余工作并交由用户决定，妥善处理已启动的自测实例。
- 单次自测总耗时上限为 20 分钟，从开始自测准备时计时，包含编译、隔离准备、启动及交互验证。达到 20 分钟时停止验证，不再启动新的验证步骤；仅进行必要的自测进程关闭和资源清理，并保留排查所需日志。报告已验证结果、停止原因和未验证项，未经用户明确要求继续，不得自行延长自测。
- 启动前检查现有 ATools／Java 进程，区分用户实例和自测实例，并检查单实例锁与数据路径。禁止关闭、重启或操作用户的 ATools，禁止删除、占用或绕过用户实例的锁。
- 自测使用 `rtk proxy ./gradlew :app:runDebug --args='临时样例文件的绝对路径'`，或在独立 Java 启动命令的 JVM 参数中加入 `-Datools.debug=true`。`DebugRuntime.initialize()` 在日志及配置初始化前自动创建独立临时 `user.home`；锁、配置、日志和会话均写入该目录下的 `.atools_notepadmm/`，窗口标题为 `ATools Debug`。每次启动使用新目录，保留独立实例锁；不修改系统 `HOME` 或 `CODEX_HOME`，不复制用户会话或打开用户原始文档。
- 隔离必须双向成立：用户实例运行时可启动 debug；debug 先运行时也不能阻止用户启动安装版。debug 不接触正式版锁、不占用正式版应用身份、不转发文件到正式版；正式版继续使用原有目录和单实例规则。
- 使用当前源码编译出的程序，通过独立 Java 进程直接启动；验证启动参数确实传给应用 JVM，并确认独立锁文件路径、持有锁的进程及启动日志无获取锁异常。`InstanceLock` 遇到异常会放行启动，后续仍可能输出成功日志，不能仅以出现窗口或成功日志判断隔离成功。不得通过 `open -a ATools`、文件关联或双击文档启动自测，避免将操作转发给用户实例。
- 调试模式关闭 macOS 最近文档同步及系统打开文件／退出事件处理器注册，不向用户实例转发文件。仍需检查新增功能是否使用 JVM 数据目录之外的共享状态，例如应用身份和文件关联；仅设置 `user.home` 不代表所有系统状态已经隔离。不能修改用户安装包、系统关联或最近文档。无法确认隔离时不启动，明确报告阻塞原因。
- 用户要求自测时，允许执行必要的 Gradle 编译和运行任务，该自测所需验证不受通用“不主动运行 Gradle”规则限制。只生成开发验证所需产物，不执行安装、覆盖 `/Applications/ATools.app` 或发布操作；尤其不得使用会复制到用户安装目录的 `build.sh -f`。
- 自测使用临时样例文件，覆盖本次修复的触发步骤及直接相关的交互。完成后仅关闭自己启动且已记录 PID 的实例，清理自建临时资源；保留排查失败所需的日志和证据。执行自测后，交付时报告验证项目、结果和未覆盖项；用户已要求自测但未执行或仅完成部分自测时，必须列出具体原因，不能以“待用户验收”代替说明。仅文档修改等无需运行程序的情况，说明不适用的原因。

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

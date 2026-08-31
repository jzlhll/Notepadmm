# 编辑器会话快照与未保存内容恢复

## 目标与用户语义

- Hot Exit 负责恢复未保存内容，源文件仅在用户显式保存时写入。
- 未命名标签与 dirty 标签始终恢复，不提供关闭开关。
- 设置项“恢复上次打开的已保存文件”只控制 clean 且已命名的标签。
- 应用退出时保存 session，不逐个弹出保存确认。
- 单独关闭 dirty 标签保留“保存 / 不保存 / 取消”。
- 未命名标签使用 `New1`、`New2`、`New3`，编号取已打开和待恢复标签中未占用的最小正整数。

## 文档状态模型

新增 `EditorDocumentState`，集中保存以下状态：

| 字段 | 语义 |
|---|---|
| `sessionId` | 标签稳定 UUID |
| `displayName` | 标签名称 |
| `sourcePath` | 正式文件路径；未命名标签为 `null` |
| `untitled` | 是否尚未绑定正式文件 |
| `dirty` | 唯一的未保存状态事实源 |
| `externalState` | `UNCHANGED`、`MODIFIED` 或 `DELETED` |
| `encoding` | 正式文件读写编码 |
| `baseLastModified` | 编辑基线对应的磁盘修改时间 |
| `baseFileSize` | 编辑基线对应的磁盘文件大小 |
| `initialSaveDirectory` | 未命名标签首次保存建议目录 |
| `savedUndoPosition` | UndoFX `UndoPosition`，用于标记正式文件对应的编辑位置 |
| `savedUndoPositionValid` | Undo 基线是否可用于计算 clean |

`dirty` 的状态转换规则：

1. 正常打开已命名文件：调用 UndoManager `mark()` 建立基线，`dirty=false`。
2. 新建空白标签：`untitled=true`，调用 UndoManager `mark()` 建立空文本基线，`dirty=false`；标签仍进入 session。
3. 普通编辑：Undo 基线有效时，`dirty = !undoManager.isAtMarkedPosition()`。
4. session 恢复 dirty 内容：`dirty=true`，Undo 基线无效；正式保存或采用磁盘版本前保持 dirty。
5. 显式保存开始时捕获正文、`contentVersion` 和 `UndoPosition`。写入成功后该 `UndoPosition` 仍有效时将其设为 mark；当前位置等于 mark 时为 clean，保存期间产生的后续编辑保持 dirty。
6. 采用磁盘版本：以程序性文本更新重建 Undo 历史并调用 `mark()`，`dirty=false`。
7. dirty 通过 Undo 回到 clean 时，manifest 立即停止引用该备份；manifest 提交成功后清理备份。Redo 使其再次 dirty 时重新进入快照调度。
8. 标签星号、关闭确认、session 快照和保存按钮状态全部读取 `dirty`，不解析标签文本。

`untitled` 与 `dirty` 相互独立。空白 `New1` 可以是 clean，但仍作为未命名标签恢复。

`externalState` 是运行时状态，不写入 manifest；启动恢复时根据磁盘文件重新计算。

## sourcePath 可空约束

`EditorAreaMgr`、`EditorArea` 及关联管理器统一支持 `sourcePath=null`：

- `isDestroyed()` 只根据 editor、area 和 tab 生命周期判断，不使用 `sourcePath`。
- 文件大小、修改时间、tooltip、重命名、打开目录、终端定位、外部变化监听仅对已命名标签执行。
- `UIContext.allOpenedFileList` 只收录已命名文件。
- `Tab.userData` 统一存放 `EditorDocumentState`，路径查询从其可空 `sourcePath` 派生，不再假定 `userData` 为 `File`。
- 未命名标签的路径展示为空，复制完整路径、重命名和打开所在目录操作禁用。
- 所有 `EditorArea` 使用支持 Markdown 样式类的文本渲染器；非 Markdown helper 不产生 Markdown 样式类。
- `EditorAreaMgr` 持有可动态绑定的 nullable keyword helper。未命名标签以 helper 为 `null` 的纯文本模式初始化；首次保存后根据正式扩展名绑定 helper，重算全文样式并刷新 Markdown 目录、表格、图片和代码块管理器。
- `EditorKeywordHelperFactory.create(File)` 接受 `null`，文件为 `null`、文件名无扩展名或扩展名不受支持时返回 `null`；扩展名解析结果为 `null` 时不进入 `switch`。
- `EditorAreaMgr` 的构造、销毁、文件大小与时间读取、`UIContext.allOpenedFileList`、`RefWatcher`、标签标题与 tooltip、焦点监听均以已命名状态为文件路径访问边界。
- 全文搜索结果统一携带 `sessionId`、`displayName` 和 nullable `sourcePath`；未命名标签不向文件 API 传递路径。
- 已命名路径统一使用规范化绝对路径比较。“另存为”目标已被其他标签打开时，保留当前标签并提示目标文件已打开。
- `isFake` 体系由 `untitled` 状态承接，代码中不存在预设实体路径。

## Session 存储

目录结构：

```text
~/.atools_notepadmm/session/
├── session.json
├── session.json.prev
└── backups/
    ├── <sessionId>-<backupVersion>.txt
    └── <sessionId>-<backupVersion>.txt
```

`session.json` 结构：

```json
{
  "version": 1,
  "activeSessionId": "uuid",
  "tabs": [
    {
      "sessionId": "uuid",
      "order": 0,
      "displayName": "a.txt",
      "sourcePath": "/Users/name/a.txt",
      "untitled": false,
      "dirty": true,
      "encoding": "UTF-8",
      "backupVersion": 42,
      "backupFile": "uuid-42.txt",
      "backupByteSize": 2176,
      "backupSha256": "sha256-hex",
      "baseLastModified": 1788171000000,
      "baseFileSize": 2048,
      "caretPosition": 120,
      "initialSaveDirectory": null
    }
  ]
}
```

存储规则：

- 所有标签都在 manifest 中保存完整元数据、顺序和光标位置。
- clean 已命名标签没有 `backupFile`，正文从 `sourcePath` 读取。
- dirty 标签和有内容的未命名标签使用 UTF-8 正文快照。
- 空白未命名标签没有正文快照，恢复内容为空。
- 每次成功快照递增 `backupVersion`，写入不可变的 `<sessionId>-<backupVersion>.txt`。新备份提交前不覆盖 manifest 正在引用的备份。
- 正文先写同目录唯一临时文件，计算 UTF-8 字节数和 SHA-256，再原子移动为带版本的正式备份；全部正文成功后原子提交 manifest。
- manifest 提交前保存上一份有效内容到 `session.json.prev`。启动时优先使用校验通过的 `session.json`，其校验失败时使用校验通过的 `session.json.prev`。
- 文件系统不支持原子移动时，使用同目录替换，并依靠 `session.json.prev` 保证至少存在一份可校验的 manifest。
- 普通快照提交保留 `session.json.prev` 作为上一份可恢复状态。显式保存进入 clean、采用磁盘版本、Undo 回到 clean、关闭标签和“不保存”属于状态收口提交；该类提交成功后将同一份新 manifest 同步到 `session.json.prev`，再清理两份 manifest 都未引用的备份。
- session 恢复后保留快照，直至正式保存、采用磁盘版本或明确“不保存”。
- manifest 提交成功后清理同时未被 `session.json` 和 `session.json.prev` 引用的备份。
- 标签新增、排序和活动标签变化使用独立的 1 秒 manifest debounce，不受正文两分钟限频影响。单标签关闭立即提交 manifest。
- 光标变化只更新内存状态，在正文快照、标签结构提交或应用退出时随 manifest 一并落盘，不单独触发磁盘写入。

## 快照触发、线程与限频

每个标签维护：

- `lastEditAt`
- `lastSnapshotAt`
- `contentVersion`
- `backupVersion`
- `lifecycleGeneration`
- 一条标签专用写入链
- 一份待写入的最新快照
- 一个 1 秒 debounce 任务
- 一个两分钟到期补偿任务

调度规则：

1. 文本变化发生在 JavaFX 线程，更新 `lastEditAt` 和 `contentVersion`，重启 1 秒 debounce。
2. 停止输入 1 秒后，debounce 必须调用一次 `trySnapshot(sessionId, expectedVersion)`。
3. `trySnapshot()` 每次都会执行；两分钟限制只控制实际磁盘写入：
   - `lastSnapshotAt` 为空时允许写入。
   - 距离成功快照达到两分钟时允许写入。
   - 未达到两分钟时不写盘，并注册唯一的到期补偿任务。
4. 补偿任务由 `EditorSessionManager` 的调度线程触发：
   - 到期后通过 `Platform.runLater` 回到 JavaFX 线程。
   - editor 仍然 dirty 且已经停止输入至少 1 秒时捕获最新正文。
   - editor 仍在输入时，补偿任务重新安排到 `lastEditAt + 1 秒`。
5. 正文只能在 JavaFX 线程读取；捕获完成后提交到 session 专用 IO 执行器。
6. 同一 `sessionId` 同时只有一个正文写入任务。写入期间到达的请求只保留最新 `contentVersion` 和正文，当前任务完成后继续写入该最新版本。
7. 每个任务捕获 `lifecycleGeneration`。只有 generation 一致的任务才能替换正式备份、更新 `lastSnapshotAt` 和提交 manifest。
8. 显式保存、采用磁盘版本、“不保存”和 editor 销毁会递增 generation，停止后续捕获，并在标签写入链上排队执行 manifest 提交和备份清理。早于该屏障的在途任务不会在屏障之后重建备份。
9. 显式保存进入 clean 后将 `lastSnapshotAt` 清空。保存后的首次编辑在停止输入 1 秒后生成第一份新快照。
10. 标签关闭、采用磁盘版本或 editor 销毁时停止 debounce 和补偿任务。
11. 应用退出时设置 session closing 状态，停止调度任务，在 JavaFX 线程一次性捕获全部 dirty 正文，绕过 debounce 和限频。

session IO 使用最多两个工作线程。退出时并行写入各标签备份，`CompletableFuture.allOf` 等待全部备份结束，再单次提交 manifest。所有 manifest 提交通过单一协调队列串行执行，不与尚未完成的备份并行写入。

## 显式保存事务

- 每个标签的正式文件写入使用一条串行保存链；多次保存按捕获顺序执行，后一次请求对应的正文最终留在磁盘。
- 保存请求捕获 `saveOperationId`、`contentVersion`、正文、编码、目标路径和 `UndoPosition`。
- 写入成功后更新 `baseLastModified`、`baseFileSize` 和 `externalState`。`UndoPosition` 有效时将其设为 mark，并依据 `isAtMarkedPosition()` 计算 dirty；位置已失效时保持 dirty。
- 保存完成回调仅处理同一 `sessionId`、目标路径和 `saveOperationId` 的请求。
- 保存结果使用 `SUCCESS_CLEAN`、`SUCCESS_DIRTY`、`CANCELLED` 或 `FAILED`。关闭流程只在 `SUCCESS_CLEAN` 后进入免确认关闭；`SUCCESS_DIRTY` 表示保存期间产生了后续编辑，标签保持打开。
- `SUCCESS_CLEAN` 通过标签写入链提交无备份引用的 manifest，提交成功后清理备份并将 `lastSnapshotAt` 置空。
- `SUCCESS_DIRTY` 保留 manifest 当前引用的有效备份，并立即捕获最新正文，绕过两分钟限频写入新版本备份。新 manifest 提交成功后清理上一版备份。
- 正式文件写入和 session 备份写入使用独立队列；两者的状态收口统一回到 JavaFX 线程。

## 新建与首次保存

- 工具栏新建调用 `newUntitledFile(null)`。
- 工作区目录内新建调用 `newUntitledFile(currentDirectory)`。
- 首次保存目录优先级：
  1. 存在且可读写的 `initialSaveDirectory`
  2. `GlobalCfgStores.user()` 中存在且可读写的 `lastSaveDir`
  3. 存在且可读写的 `user.home`
- `lastSaveDir` 是内部状态，不出现在设置页。
- 保存对话框默认文件名为 `NewN.txt`。
- 用户填写的名称没有扩展名时追加 `.txt`；明确填写扩展名时保留该扩展名。
- 未命名文件的默认编码为 UTF-8。
- 取消文件选择后标签、dirty、session 和关闭流程均保持可继续操作状态。
- 首次保存返回 `SUCCESS_CLEAN` 或 `SUCCESS_DIRTY` 时绑定正式路径，并记录 `lastSaveDir`。
- 正式路径绑定、helper 绑定、标签名、tooltip、`UIContext.allOpenedFileList`、最近文件和 Markdown 管理器在首次成功写入的收口中一次性刷新。`SUCCESS_CLEAN` 进入 clean 清理，`SUCCESS_DIRTY` 进入即时快照。

## 编码规则

- session 快照始终使用 UTF-8，能够无损保存编辑器中的 Unicode 内容。
- 已命名文件显式保存时使用其 `encoding`。
- 正式写入前使用 `CharsetEncoder.canEncode()` 检查全部正文。
- 正式编码无法表示正文字符时，提示“使用 UTF-8 保存 / 取消”：
  - 使用 UTF-8：更新 editor 编码并保存。
  - 取消：不写源文件，保留 dirty 与快照。
- 写入过程中禁止使用替换字符静默丢失内容。

## 外部文件变化

焦点变化、标签切换和 session 恢复统一调用同一套磁盘状态检查：

- 磁盘读取开始时捕获 `contentVersion`、dirty 和基线元数据；回到 JavaFX 线程时三者仍一致才能应用读取结果。
- 重载、恢复、编码切换和采用磁盘版本使用 `PROGRAMMATIC_REPLACE` 变更来源。该来源不触发 dirty 快照，文本应用完成后再统一建立 Undo 基线。
- 读取期间发生用户编辑时，不应用过期正文，按 dirty 外部变化规则进入冲突状态。

| 文档状态 | 磁盘状态 | 行为 |
|---|---|---|
| clean 已命名 | 内容变化 | 读取磁盘内容并建立新的 clean 基线；保留重载前的光标和选区位置，并分别钳制到新正文有效范围 |
| clean 已命名 | 文件删除 | 提示“重新创建 / 关闭”，不自动创建文件 |
| dirty 已命名 | 内容变化 | 保留编辑器内容与快照，设置 `externalState=MODIFIED`，禁止自动重载 |
| dirty 已命名 | 文件删除 | 保留编辑器内容与快照，设置 `externalState=DELETED` |
| untitled | 任意 | 不参与磁盘变化检查 |

冲突状态提供以下出路：

- “采用磁盘版本”：确认丢弃编辑内容后读取磁盘，清除 dirty、冲突和快照。
- “重新加载”：编辑器菜单入口，行为与“采用磁盘版本”一致。
- “覆盖保存”：`MODIFIED` 状态下覆盖同一路径，成功后建立新基线。
- “重新创建”：`DELETED` 状态下写回同一路径。
- “另存为”：保存到新路径，原磁盘文件不受影响。
- “取消”：保留编辑器内容、dirty、冲突和快照。

冲突状态下执行普通保存时：

- `MODIFIED`：显示“覆盖 / 另存为 / 取消”。
- `DELETED`：显示“重新创建 / 另存为 / 取消”。

同一个 `baseLastModified + baseFileSize` 变化只提示一次；后续焦点变化保留冲突标记，不重复弹窗。

## 单标签关闭流程

`requestClose()` 使用一次性关闭上下文，不通过标签后缀判断：

1. clean 标签递增 generation，立即提交不包含该条目的 manifest，提交成功后关闭标签并清理备份；提交失败时保持标签打开并提示写入失败。
2. dirty 标签显示“保存 / 不保存 / 取消”。
3. “保存”调用 `saveForClose()` 并处理 `SaveResult`：
   - `SUCCESS_CLEAN` 使用免确认关闭通道关闭标签。
   - `SUCCESS_DIRTY` 结束本次关闭上下文，标签保持打开。
   - `FAILED` 结束本次关闭上下文，标签保持打开并提示写入失败。
   - 保存对话框取消、编码转换取消或冲突操作取消时结束本次关闭上下文，标签保持打开。
   - 冲突状态允许选择“另存为”，成功后正常关闭。
4. “不保存”递增 generation，立即提交不包含该条目的 manifest，提交成功后清理备份，再使用免确认关闭通道关闭标签；提交失败时保持标签打开并提示写入失败。
5. “取消”结束本次关闭上下文。

每次关闭请求结束后都重置关闭状态。再次点击关闭按钮会产生新的正常请求，不存在递归触发或无法退出的对话框状态。

## 启动恢复与缺失数据降级

恢复顺序：

1. 读取并校验 manifest 版本。
2. 预留 manifest 中全部未命名标签的 `NewN` 编号。
3. session IO 线程并行读取条目正文，每份备份同时校验 UTF-8、`backupByteSize` 和 `backupSha256`。
4. 全部读取结束后在 JavaFX 线程按 `order` 一次性创建标签。
5. dirty 或 untitled 条目始终恢复。
6. clean 已命名条目根据“恢复上次打开的已保存文件”设置决定是否恢复。
7. 恢复 `activeSessionId`；目标不存在时选择第一个成功恢复的标签。
8. 恢复光标位置，并限制在正文有效范围内。
9. 以实际恢复成功的标签重建内存 session，立即提交 manifest，清理被设置过滤、恢复失败或已缺失的条目与备份。
10. `restoreSession()` 完成后再处理命令行、macOS OpenFiles 和单实例转发的打开文件请求；恢复期间到达的请求按到达顺序排队。

缺失或损坏处理：

| 条目 | 备份 | 源文件 | 降级行为 |
|---|---|---|---|
| dirty 已命名 | 缺失/损坏 | 存在 | 打开源文件为 clean，并集中提示未保存内容无法恢复 |
| dirty 已命名 | 有效 | 不存在 | 打开快照为 dirty，标记 `DELETED` |
| dirty 已命名 | 缺失/损坏 | 不存在 | 跳过条目并集中提示 |
| dirty 未命名 | 缺失/损坏 | 无 | 恢复同名空白标签为 clean，并集中提示内容无法恢复 |
| clean 已命名 | 无 | 不存在 | 跳过条目并集中提示文件不存在 |

恢复警告在全部条目处理结束后合并显示一次。单个条目失败不阻断其他标签。

`session.json` 某条目的备份校验失败时，先查找 `session.json.prev` 中相同 `sessionId` 且校验通过的 dirty 备份。命中时恢复该较早快照并集中提示；未命中时执行缺失或损坏降级规则。

`session.json` 和 `session.json.prev` 均无法通过 JSON、schema 或版本校验时，将两者分别保存为带时间戳的 `.invalid` 文件，创建空 session，设置 `sessionMigrationVersion=1` 并集中提示。`backupFile` 只接受 `<sessionId>-<backupVersion>.txt` 格式，解析后的绝对路径必须位于 `backups` 目录内。

## 配置与兼容数据导入

- 设置页显示“恢复上次打开的已保存文件”，配置键使用 `restoreSavedFilesOnStartup`。
- `saveLastOpenedFile` 的布尔值首次复制到 `restoreSavedFilesOnStartup`。
- `autoSaveOnExit`、`newFileDir` 和 `lastFile` 只用于首次兼容数据导入。
- session manifest 不存在且 `sessionMigrationVersion < 1` 时执行一次导入：
  1. 按 `lastFile` 顺序导入仍存在的路径，生成 clean 已命名条目，光标位置为 0。
  2. 读取 `newFileDir` 指向目录中的隐藏临时文件，按文件名排序，依次导入为 `NewN` dirty 未命名条目。
  3. 有 clean 条目时选择第一个 clean 条目为活动标签；只有未命名条目时选择第一个未命名标签。
  4. 写入全部备份并成功原子提交 manifest。
  5. manifest 提交成功后设置 `sessionMigrationVersion=1`，删除已导入隐藏临时文件，并清理 `lastFile`、`autoSaveOnExit`、`newFileDir`、`saveLastOpenedFile`。
- 任一步失败时保留兼容数据，后续启动可以重新导入。
- 无兼容数据时提交空 manifest 并设置 `sessionMigrationVersion=1`。

## 同版本代码收口

Hot Exit 交付版本同时完成以下收口：

- 应用退出路径只调用 `EditorSessionManager.flushAndWait(Duration.ofSeconds(30))`，不调用源文件保存。
- `saveUnSaved()`、`saveHiddenTempFile()`、`restoreHiddenTempFiles()`、`newHiddenTempFile()` 和退出场景中的 `saveContentAndWait()` 不存在于运行链路。
- 时间戳文件名生成、`newFileDir` 前置校验及隐藏临时文件写入逻辑退出运行链路。
- `autoSaveOnExit` 与新建目录控件不出现在设置页。
- `lastFile` 的读取与写入由 session manifest 承担。
- `isFake` 判断统一替换为 `untitled`。
- 标签标题只负责展示，不承载 dirty 状态。

## 退出事务

- 窗口关闭、Dock 退出和系统退出共用同一个退出事务。退出请求先拦截窗口关闭，再调用 `flushAndWait(Duration.ofSeconds(30))`，其他管理器的销毁发生在 session 提交成功之后。
- `flushAndWait()` 在 JavaFX 线程捕获全部标签状态和 dirty 正文，后续备份、校验和 manifest 写入不依赖 `Platform.runLater`，因此 JavaFX 线程可以同步等待结果。
- 退出刷盘总等待上限为 30 秒。超时或任一备份、manifest 写入失败时返回 `FAILED`，并显示“重试 / 仍然退出 / 返回应用”。
- “重试”继续等待已在途任务并重试失败写入；“仍然退出”继续销毁和进程退出；“返回应用”恢复 session 调度，重置退出状态并保留全部标签。
- 强制结束进程只保证恢复最近一份已原子提交的 manifest 和备份。

## 主要内部接口

`EditorSessionManager`：

- `CompletionStage<RestoreResult> restoreSession()`
- `track(EditorArea)`
- `onTextChanged(EditorArea, long contentVersion)`
- `trySnapshot(String sessionId, long expectedVersion)`
- `onSaved(EditorArea, SaveResult)`
- `CompletionStage<SessionCommitResult> discard(EditorArea)`
- `CompletionStage<SessionCommitResult> untrackAndCommit(EditorArea)`
- `FlushResult flushAndWait(Duration timeout)`
- `destroy()`

`EditorAreaMgr`：

- `EditorDocumentState getDocumentState()`
- `boolean isDirty()`
- `boolean isUntitled()`
- `String snapshotText()`
- `void restoreSessionText(String text, boolean dirty)`
- `CompletionStage<SaveResult> save()`
- `CompletionStage<SaveResult> saveForClose()`
- `void requestClose()`
- `void reloadFromDisk()`

`AllEditorsManager`：

- `EditorArea newUntitledFile(File initialDirectory)`
- `EditorArea restoreSessionEntry(SessionTab entry, String text)`

## 验收场景

- `New1` 输入内容后正常退出、Dock 退出及强制结束，分别恢复最近有效快照。
- 已命名 dirty 文件退出后源文件保持不变，启动后恢复 dirty 内容和光标。
- clean 标签恢复编码、光标、顺序和活动标签。
- 每次停止输入 1 秒都会进入 `trySnapshot()`，两分钟内不会产生重复磁盘写入。
- 两分钟到期时仍在输入，快照等待最后输入停止 1 秒后执行。
- 显式保存后立即编辑，停止输入 1 秒即可生成新一轮首份快照。
- 保存写盘期间继续编辑，磁盘保留已捕获正文，标签保持 dirty，再次保存后磁盘为最新正文。
- 连续快速保存两次，后一次捕获的正文最终留在磁盘。
- 快照写入期间正式保存、采用磁盘版本或“不保存”，在途任务不会重建已停止引用的备份。
- 新版本备份写入完成但 manifest 尚未提交时强制结束进程，启动时使用上一份 manifest 及其引用的不可变备份。
- dirty 已命名文件 Undo 回到保存基线后为 clean，关闭时不弹出保存确认，启动时从源文件恢复。
- `New1` 首次保存为 `.md`、`.java` 和 `.txt` 时，分别绑定 Markdown、Java 和纯文本能力，正文、光标和选区保持不变。
- dirty 文件运行期间被外部修改或删除，编辑内容不会被自动重载覆盖。
- 冲突状态可以采用磁盘版本、覆盖、重新创建、另存为或取消。
- dirty 已命名条目备份丢失时降级打开磁盘文件，标签不会无故消失。
- clean 文件在磁盘删除后启动时跳过并给出合并提示。
- 保存对话框、编码提示和冲突提示取消后，标签保持打开且可以再次正常关闭。
- `New1` 至 `New10` 命名唯一，首次保存默认文件名带 `.txt`。
- 关闭“恢复上次打开的已保存文件”后，仅 clean 已命名标签不恢复。
- 多个 dirty 标签退出时最多两个备份并行写入，manifest 在全部备份完成后提交。
- session 恢复期间到达命令行或 macOS OpenFiles 请求，恢复标签顺序和活动标签正确，外部请求在恢复结束后按顺序打开。
- 备份字节数或 SHA-256 不匹配时执行损坏数据降级规则。
- 退出刷盘超时或失败时可以重试、仍然退出或返回应用，返回应用后快照调度继续有效。
- 兼容数据只有在 session 提交成功后清理。

## 默认约定

- session 快照属于应用恢复数据，不进入最近文件、工作区树和系统文件选择记录。
- session 专用 IO 执行器最大并发数为 2。
- 磁盘变化判断同时比较修改时间和文件大小。
- 应用单实例约束保证同一份 session 不会被多个进程并发写入。

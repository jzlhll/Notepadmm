# TODO：优化双击工作区打开现有文件耗时

## 问题描述

在工作区文件树中双击一个已经在 editor 中打开的文本文件时，从收到双击事件到目标 editor 真正显示之间存在可感知等待。

交互要求保持不变：

- 必须双击才执行打开或切换。
- 不能改成单击已打开文件就切换。
- 已打开文件应直接切换到对应 Tab，不应重新读取或重置文件内容。
- 未打开文件仍走正常的文件读取与 editor 创建流程。

## 相关代码

- `WorkspaceManager`：处理工作区文件树的双击事件。
- `AllEditorsManager.openFile`：查找已有 Tab、读取文件、重置 editor、创建新 Tab。
- `AllEditorsManager.init`：监听 Tab 选择变化并更新当前 editor。
- `JFXTabPane`：承载 editor Tab，其 Skin 会在布局 pulse 中更新内容位置。
- `EditorAreaMgr.resetText`：通过 `replaceText` 重置整篇内容。

## 原始流程

工作区双击文本文件后无条件调用：

```java
AllEditorsManager.Instance.openFile(f, true, true);
```

即使 `isFilePathAlreadyInTabs` 已找到对应 Tab，原流程仍会：

1. 进入单线程文件 I/O 队列。
2. 读取编码缓存或检测文件编码。
3. 重新读取整个文件。
4. 回到 JavaFX 线程执行 `resetText`。
5. 最后选择已有 Tab。

这会让已打开文件产生不必要的重新读取和整篇内容重置。

## 已采集的日志

临时日志统一使用 `EditorOpenTrace` 标签。

### 原流程：约 57 KB 文件

```text
tab lookup completed existing=true elapsedMs=0
file IO completed readMs=0 ioMs=1 totalMs=1
existing editor reset completed resetMs=12 totalMs=14
tab selection completed durationMs=1
existing tab reached next UI turn totalMs=21
```

结论：路径查找和读盘很快，主要同步耗时来自 `resetText`，总计约 21ms。

### 原流程：约 3 KB 文件

```text
tab lookup completed existing=true elapsedMs=0
file IO completed readMs=0 ioMs=1 totalMs=1
existing editor reset completed resetMs=1 totalMs=5
tab selection completed durationMs=1
existing tab reached next UI turn totalMs=9
```

结论：文件越大，`resetText` 越慢；小文件约 9ms，大一些的文件约 21ms。

### 已有 Tab 快速返回后

```text
opened tab fast lookup completed existing=true elapsedMs=0
tab selection completed durationMs=1
opened tab fast select returned totalMs=1
opened tab reached next UI turn totalMs=2
```

结论：快速分支已经消除读盘和 `resetText`，业务调用链本身仅需约 1～2ms，但目标内容真正显示仍可能晚于这些日志。

### JFXTabPane 动画开启时

```text
tab display pulse=1 totalMs=15 minX=1469
tab display pulse=5 totalMs=81 minX=1378
tab display pulse=10 totalMs=165 minX=455
tab display stabilized pulse=18 totalMs=299 minX=257
```

结论：目标内容持续改变横向位置，约 299ms 后稳定，确认 `JFXTabPane` 默认滑动动画会造成明显等待。

### 关闭 JFXTabPane 动画后

```text
tab selection completed durationMs=0
opened tab fast select returned totalMs=0
tab display pulse=1 totalMs=2 minX=-4591 intersects=false needsLayout=false
tab display pulse=2 totalMs=19 minX=257 intersects=true needsLayout=false
tab display stabilized pulse=4 totalMs=52 minX=257 needsLayout=false
```

需要注意：早期日志强制至少到第 5 个 pulse 才输出“稳定”，曾产生约 68ms 的误导数据；后来已增加逐 pulse 和首次进入可视区日志。以双击事件被接收为起点，目标内容实际在第 2 个 pulse、约 19ms 时进入可视区。

## 已尝试方向

### 1. 新增已打开文件列表单例

未采用。

原因：`tabPane.getTabs()` 已经是当前打开 editor 的唯一列表，并已有 `isFilePathAlreadyInTabs`、`getAreaByFilePath`、`bringAreaToFront` 等遍历能力，再维护一份列表会产生双数据源和同步问题。

### 2. 普通 openFile 命中已有 Tab 后直接返回

已尝试，并通过日志确认可以消除读盘和 `resetText`。

实现要点：

- 仅处理 `checkAlreadyHasFile == true && reOpenExistTab == null` 的普通打开请求。
- 找到已有 Tab 后直接执行 `selectionModel.select` 并返回。
- `reOpenCurrentFile` 传入 `reOpenExistTab`，仍保留显式重新读取能力。

该方向只能解决重新读取耗时，不能消除 JavaFX 下一帧才更新内容位置的问题。

### 3. 在 WorkspaceManager 中直接查找 EditorArea 并切换

已尝试后回撤。

方案是先调用 `getAreaByFilePath`，命中后执行 `bringAreaToFront`，未命中才调用 `openFile`。

该方案和 `openFile` 快速返回在最终 Tab 选择行为上基本等价，没有解决选择后等待下一次布局 pulse 的问题。

### 4. 跳过一次 checkFileIfChanged

已尝试后回撤。

最初怀疑 Tab 选择监听中的 `checkFileIfChanged` 会再次触发 `reOpenCurrentFile`。后续日志显示选择期间没有出现 `reopen=true` 请求，且 Tab 选择监听仅耗时约 1ms，因此该方向不成立。

### 5. 关闭 JFXTabPane 动画

已尝试。

在主 editor 的 `JFXTabPane` 上设置：

```xml
disableAnimation="true"
```

日志确认默认约 299ms 的横向滑动被消除，但无动画时仍需要等到下一个 JavaFX pulse，目标内容约 19ms 后才进入可视区。

### 6. 单击已打开文件立即切换

已尝试后明确回撤，禁止继续该方向。

原因：产品交互要求以双击触发切换，不能把单击改成打开或切换动作。第一击到第二击的时间不计入本问题耗时，计时起点必须是收到 `clickCount == 2` 的事件。

### 7. 使用 Platform.runLater 判断显示完成

日志已证明不准确。

`Platform.runLater` 只表示 JavaFX 事件队列执行到了回调，不代表 CSS、布局和渲染已经完成。快速分支在约 1～2ms 到达下一次 UI 回调，但目标内容仍可能位于屏幕外。

### 8. 使用 AnimationTimer 记录后续 pulse

已尝试，可观察 Tab 内容的 `localToScene` 横向位置、是否与 TabPane 相交以及 `needsLayout` 状态。

当前证据表明：

- 动画开启时，内容位置会持续变化约 299ms。
- 动画关闭时，第 1 个 pulse 仍是旧位置，第 2 个 pulse 才进入可视区。
- editor 内容节点的 `needsLayout=false`，剩余等待更可能来自 JFXTabPane Skin 的容器布局，而不是 editor 自身重新布局。

## 当前判断

已经确认并区分出两段耗时：

1. 已打开文件被重新读取和 `resetText`：随文件大小增长，可以通过已有 Tab 快速返回消除。
2. Tab 选择后等待 JFXTabPane Skin 在下一次 pulse 更新内容位置：动画关闭后仍约为一个 60Hz 帧周期，当前日志约 19ms。

当前尚未解决的是第 2 段。业务代码在双击事件后的同步耗时约为 0～2ms。

## 后续可尝试方向

### 1. 选择 Tab 后立即触发布局

在已有 Tab 快速选择后，尝试对主 `JFXTabPane` 调用：

```java
tabPane.applyCss();
tabPane.layout();
```

目标是让 JFXTabPane Skin 在当前事件周期内更新内容容器位置，而不是等待下一次 pulse。

风险：

- 强制同步布局可能增加当前 UI 事件耗时。
- 需要防止布局重入。
- 必须通过日志确认调用后目标内容的 `minX` 是否立即到达可视位置。

### 2. 改用 post-layout pulse 记录

`AnimationTimer.handle` 位于 pulse 的动画阶段，不等价于绘制完成。可以使用 Scene 的 post-layout pulse 回调记录布局后的目标位置，更准确地区分：

- 选择事件完成时间。
- Skin 完成布局时间。
- 内容首次处于正确屏幕位置的时间。

### 3. 检查或定制 JFXTabPaneSkin

检查 `JFXTabPaneSkin` 在选择变化后只调用 `requestLayout` 的行为，确认是否能在程序化选择时直接更新 `tabsContainer.translateX`。

如果公开 API 无法同步更新，可考虑最小范围定制 Skin，但该方案侵入性较高，应放在强制布局无效之后。

### 4. 评估替换主 editor TabPane

如果问题确认来自 JFoenix Skin 且无法低成本修复，可评估主 editor 改用标准 JavaFX `TabPane`。该方向影响样式和 Tab 标题交互，属于最后选项。

## 验收标准

- 计时起点为工作区收到 `clickCount == 2` 的鼠标事件。
- 已打开文件不执行文件 I/O、编码检测或 `resetText`。
- 已打开文件切换后，目标内容在当前 pulse 或首个可用 pulse 内进入正确位置。
- 切换耗时不随文件大小增长。
- 保持双击交互，不引入单击切换行为。
- 未打开文件、显式重新加载、编码切换和外部文件变更检查行为保持不变。

## 临时代码清理

问题解决后需要清理：

- `EditorOpenTrace` 相关日志。
- 为诊断新增的无条件 INFO 日志方法。
- `AnimationTimer` pulse 跟踪代码。

保留内容应仅包括最终有效的快速切换和布局修复。

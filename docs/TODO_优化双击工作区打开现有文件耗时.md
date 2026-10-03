# TODO：优化双击工作区切换已有标签的显示延迟

## 当前边界

`AllEditorsManager.openFile()` 命中已有标签后直接选择并返回，已避免重复读盘和 `resetText`。主编辑器仍使用 `JFXTabPane`；当前代码未见禁用其动画或针对切换的布局修复，实际显示延迟需重新测量。

## 待完成

- [ ] 从工作区收到双击事件开始，测量目标内容首次进入正确可视位置的耗时；不能用 `Platform.runLater` 返回时间代替显示完成时间。
- [ ] 确认并消除 Tab 滑动动画造成的等待，再评估是否需要同步布局；仅在公开 API 无法解决时考虑定制 Skin。
- [ ] 完成后移除新增的诊断日志和 pulse 跟踪代码。

## 验收

- 保持双击打开或切换，不能改成单击切换。
- 普通切换不重新读盘、检测编码或重置正文，不因文件大小增加等待。
- 目标内容在当前或首个可用布局 pulse 中进入正确位置。
- 未打开文件、显式重载、编码切换和外部文件变化检查保持正常。

## 入口

- `src/main/java/com/allan/atools/tools/modulenotepad/workspace/WorkspaceManager.java`
- `src/main/java/com/allan/atools/tools/modulenotepad/manager/AllEditorsManager.java`
- `src/main/resources/notepad/main_notepad.fxml`

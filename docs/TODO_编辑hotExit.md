# TODO：会话恢复收尾

## 待完成

- [ ] 启动恢复时最多并行读取、校验两份标签正文，全部完成后按保存顺序创建标签。当前 `loadRestoreData()` 仍逐条同步读取；单条失败不能阻断其他标签。
- [ ] 将恢复后的 manifest 提交纳入 `restoreSession()` 完成链，处理提交失败。当前 `applyRestoreData()` 调用 `commitCurrentState(true)` 后直接返回，调用方无法获知该次提交结果。

## 验收

- 多标签恢复保持顺序、活动标签和光标位置；损坏备份按现有规则降级并合并提示。
- 恢复状态提交完成后再处理启动打开请求；提交失败有明确结果，不误报恢复收尾成功。
- 保持 manifest version 1、现有旧配置迁移和备份兼容性。

## 入口

- `src/main/java/com/allan/atools/tools/modulenotepad/session/EditorSessionManager.java`
- `src/main/java/com/allan/atools/controller/NotepadController.java`

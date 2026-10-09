# JFoenix 本地模块

本模块替代外部依赖 `com.jfoenix:jfoenix:9.0.10`，由本项目直接维护。

- 上游仓库：https://github.com/sshahine/JFoenix
- 导入分支：`JFoenix-9.0.0`
- 导入提交：`d77d60f5081b248e73ded265868a8637389535d7`（2021-03-17）
- JPMS 模块名：`com.jfoenix`；构建产物：`jfoenix.jar`。
- 编译环境：Java 25、JavaFX 27，与主工程一致。

保留上游库的 Java 源码、CSS、字体、包名和类名，不导入演示工程及旧版发布脚本。该分支包含 9.0.10 发布后的 OSGi 声明修正、`com.jfoenix.controls.base` 导出以及 `JFXSpinner` 内存泄漏修复。

本地适配：

- `JFXChipViewSkin` 指定键盘焦点遍历方式；`JFXSpinnerSkin` 与 `JFXProgressBarSkin` 使用 `TreeShowingProperty` 监听实际显示状态，并在销毁皮肤时释放监听。
- `JFXGenericPickerSkin` 对没有编辑器的颜色选择器跳过 FakeFocus 监听反射，并处理监听容器为空、仅有单个监听的情况，使用方无需添加空焦点监听。
- `JFXDecorator` 内置 ATools 的标题栏高度、间距和边框；`MyJFXDecorator` 仅保留原构造入口及隐藏全屏按钮的选项。
- `JFXTreeCell.updateDisplay` 允许子类定制文件显示；工作区文件树复用本模块的波纹、选中条及图标监听，文件分类和打开策略仍由主应用负责。
- `JFXTabPaneSkin.getTabLabel(Tab)` 提供标题节点访问；主应用随标签所属面板及皮肤变化在 JavaFX 线程绑定标题交互，销毁时取消订阅，无需反射及后台轮询。

上游源码采用 Apache License 2.0，见 [LICENSE](LICENSE)；Roboto 字体许可证保留于原资源目录。模块 JAR 同时携带 JFoenix 和字体许可证。后续修改在此模块内完成；新增 Kotlin 代码时需同时启用 Kotlin 插件。

JFoenix 依赖 JavaFX 内部接口，编译所需导出配置位于本模块 `build.gradle`，运行所需配置沿用根目录 `gradle.properties`。升级 JavaFX 时应核对相关接口。发行包保留 JFoenix 类和成员名称，以兼容 FXML、CSS 及反射访问。

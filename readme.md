# Notepadmm

[English](readme_en.md)

**ATools**（项目名 Notepadmm）是一款支持 Windows 与 macOS 的专业日志分析和 Markdown 编辑工具。

## 1. 专业日志分析

- 支持打开 **GB 级大日志文件**，采用分块缓冲、按需加载，避免打开时一次性读入整个文件。
- 支持多条件搜索、正则匹配、分色高亮和搜索方案保存，点击搜索结果即可定位原文，方便追踪错误、业务事件和请求链路。

## 2. Markdown 支持

支持标题大纲、图片、可编辑表格、代码高亮和离线 Mermaid 图表，提供源码／预览切换、HTML 与 PDF 导出。详细功能与快捷键见 [Markdown 文档](docs/Markdown.md)。

light green：

![light green 主题下的 Markdown 编辑](previews/green.png)

dracula：

![dracula 主题下的 Markdown 编辑](previews/dralura.png)

## 3. 打包

准备 JDK 25，将 `local.properties.example` 复制为 `local.properties` 并配置本机 JDK 路径；macOS 完整打包还需配置签名证书。在项目根目录执行：

| macOS 命令 | 用途 |
| --- | --- |
| `./macOSBuild.sh -s` | 编译并快速同步到已安装的 `/Applications/ATools.app`，然后重启。 |
| `./macOSBuild.sh -p` | 完整打包生成 DMG，自动替换 `/Applications/ATools.app` 并启动。 |

首次安装或升级 JDK、JavaFX、第三方依赖时使用 `-p`，日常开发使用 `-s`。Windows 使用 `./windowsBuild.sh -p` 生成 EXE 安装包。配置细节见 [编译注意事项](docs/编译注意事项.md)。

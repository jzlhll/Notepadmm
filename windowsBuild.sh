#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

usage() {
    echo "用法: $0 [-0 | -p | --package]"
    echo ""
    echo "  -0            只编译，不执行 buildRoot 下的脚本"
    echo "  -p, --package 编译后执行 buildRoot/jpackageCmdExe.bat（生成 exe 安装包，不自动安装）"
    echo "  -h, --help    显示本帮助"
}

# ---------- 步骤 0：解析参数 ----------
build_action=""
while [ $# -gt 0 ]; do
    case "$1" in
        -0) build_action="0" ;;
        -s|--sync) echo "Windows 暂不支持 fastCopy 快速同步，请使用 -p 打包或 -0 只编译。"; exit 1 ;;
        -p|--package) build_action="2" ;;
        -h|--help) usage; exit 0 ;;
        *) echo "未知参数: $1"; echo ""; usage; exit 1 ;;
    esac
    shift
done

if [ -z "$build_action" ]; then
    usage
    exit 1
fi

# 优先使用项目本机配置，与 Gradle Wrapper 保持一致。
if [ -f local.properties ]; then
    build_jdk_home=$(sed -n 's/^buildJdk\.home=//p' local.properties | tr -d '\r' | tail -n 1)
    if [ -n "$build_jdk_home" ]; then
        JAVA_HOME=$build_jdk_home
        export JAVA_HOME
    fi
fi

# 检查 Java 25 JDK 编译环境
java_cmd="java"
javac_cmd="javac"
if [ -n "${JAVA_HOME:-}" ]; then
    java_cmd="${JAVA_HOME}/bin/java"
    javac_cmd="${JAVA_HOME}/bin/javac"
    if [ -x "${java_cmd}.exe" ]; then
        java_cmd="${java_cmd}.exe"
        javac_cmd="${javac_cmd}.exe"
    fi
fi

if ! command -v "$java_cmd" >/dev/null 2>&1; then
    echo "未找到 Java。请配置 local.properties 的 buildJdk.home 为 JDK 25，或设置 JAVA_HOME / PATH。"
    exit 1
fi
if ! command -v "$javac_cmd" >/dev/null 2>&1; then
    echo "未找到 javac。请配置完整的 JDK 25。"
    exit 1
fi

java_version_output="$("$java_cmd" -version 2>&1)"
javac_version_output="$("$javac_cmd" -version 2>&1)"
java_version="$(printf '%s\n' "$java_version_output" | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
javac_version="$(printf '%s\n' "$javac_version_output" | sed -n '1s/^javac[[:space:]]*\([^[:space:]]*\).*/\1/p')"
java_major="${java_version%%[.-]*}"
javac_major="${javac_version%%[.-]*}"

if [ "$java_major" != "25" ] || [ "$javac_major" != "25" ]; then
    echo "当前编译环境不是 JDK 25（java: ${java_version:-未知}，javac: ${javac_version:-未知}）。"
    echo "请配置 local.properties 的 buildJdk.home，或切换 JAVA_HOME / PATH 到 JDK 25 后重试。"
    exit 1
fi

echo "Java 编译环境检查通过：JDK ${javac_version}。"

# 检测系统平台
os_name="$(uname -s)"
arch_name="$(uname -m)"
current_os="Windows"
current_arch=""
current_task=""
gradlew_cmd="./gradlew"

case "$os_name" in
    MINGW*|MSYS*|CYGWIN*)
        # Shell Wrapper 已支持 MSYS2、Git Bash 和 Cygwin。
        ;;
    *)
        echo "请在 Windows 的 MSYS2、Git Bash 或 Cygwin 环境中运行此脚本。"
        exit 1
        ;;
esac

# 检测当前架构与对应的默认 Gradle task
case "$arch_name" in
    arm64|aarch64)
        current_arch="ARM（Windows Arm64）"
        current_task="mainShAllWindowsArm64"
        ;;
    x86_64|amd64)
        current_arch="Intel（x64）"
        current_task="mainShAllWindowsX64"
        ;;
    *)
        current_arch="未知（${arch_name}）"
        current_task=""
        ;;
esac

# ---------- 步骤 1：选择编译架构 ----------
echo "请选择需要编译的 ${current_os} 架构："
echo "0) 当前电脑平台：${current_arch}（直接回车默认选此项）"
echo "1) ARM（Windows Arm64）"
echo "2) Intel（x64）"

architecture=""
seconds_left=3
while [ "$seconds_left" -gt 0 ]; do
    printf "\r请输入 0、1 或 2 [默认 0]（%d 秒后自动执行 0）：" "$seconds_left"
    if read -t 1 -r architecture; then
        break
    fi
    seconds_left=$((seconds_left - 1))
done
printf "\r%*s\r" 60 ""
architecture="${architecture:-0}"

case "$architecture" in
    0)
        if [ -z "$current_task" ]; then
            echo "无法识别当前电脑架构，请手动选择 1 或 2。"
            exit 1
        fi
        echo "已选择：0) 当前电脑平台 -> ${current_arch}"
        "$gradlew_cmd" "$current_task"
        ;;
    1)
        echo "已选择：1) ARM（Windows Arm64）"
        "$gradlew_cmd" mainShAllWindowsArm64
        ;;
    2)
        echo "已选择：2) Intel（x64）"
        "$gradlew_cmd" mainShAllWindowsX64
        ;;
    *)
        echo "输入无效，已取消编译。"
        exit 1
        ;;
esac

echo ""
echo "========== Gradle 编译完成 =========="
echo ""
echo ""

# ---------- 步骤 2：按参数执行 buildRoot 脚本 ----------
case "$build_action" in
    0)
        echo "已选择：0) 不执行 buildRoot 脚本，脚本结束。"
        ;;
    2)
        # 防止 MSYS2 / Git Bash 将 Windows 命令的 / 参数转换为路径。
        export MSYS2_ARG_CONV_EXCL='*'
        export MSYS_NO_PATHCONV=1
        running_apps=$(tasklist.exe /FI "IMAGENAME eq ATools.exe" /NH)
        case "$running_apps" in
            *ATools.exe*)
                trap 'printf "\n已取消结束程序和打包。\n"; exit 130' INT TERM
                seconds_left=3
                while [ "$seconds_left" -gt 0 ]; do
                    printf "\r%d 秒后结束现有 ATools 并开始打包，按 Ctrl+C 取消。" "$seconds_left"
                    sleep 1
                    seconds_left=$((seconds_left - 1))
                done
                printf "\n"
                taskkill.exe /IM ATools.exe /F
                trap - INT TERM
                ;;
        esac
        echo "已选择：2) 执行 jpackageCmdExe.bat"
        cmd.exe /d /c 'call buildRoot\jpackageCmdExe.bat'
        ;;
esac

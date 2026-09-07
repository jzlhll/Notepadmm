#!/bin/sh
set -e

cd "$(dirname "$0")"

# 检查 Java 17 JDK 编译环境
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
    echo "未找到 Java。请安装 JDK 17，并正确设置 JAVA_HOME 或 PATH。"
    exit 1
fi
if ! command -v "$javac_cmd" >/dev/null 2>&1; then
    echo "未找到 javac。请安装完整的 JDK 17，并正确设置 JAVA_HOME 或 PATH。"
    exit 1
fi

java_version_output="$("$java_cmd" -version 2>&1)"
javac_version_output="$("$javac_cmd" -version 2>&1)"
java_version="$(printf '%s\n' "$java_version_output" | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
javac_version="$(printf '%s\n' "$javac_version_output" | sed -n '1s/^javac[[:space:]]*\([^[:space:]]*\).*/\1/p')"
java_major="${java_version%%[.-]*}"
javac_major="${javac_version%%[.-]*}"

if [ "$java_major" != "17" ] || [ "$javac_major" != "17" ]; then
    echo "当前编译环境不是 JDK 17（java: ${java_version:-未知}，javac: ${javac_version:-未知}）。"
    echo "请切换 JAVA_HOME 或 PATH 到 JDK 17 后重试。"
    exit 1
fi

echo "Java 编译环境检查通过：JDK ${javac_version}。"

# 检测系统平台
os_name="$(uname -s)"
arch_name="$(uname -m)"
current_os=""
current_arch=""
current_task=""
gradlew_cmd="./gradlew"
default_build_action="0"   # buildRoot 脚本默认值，按平台设置
app_path=""

case "$os_name" in
    Darwin)
        current_os="macOS"
        default_build_action="1"
        app_path='/Applications/ATools.app'
        ;;
    MINGW*|MSYS*|CYGWIN*)
        current_os="Windows"
        default_build_action="0"
        # Windows 下优先使用 gradlew.bat
        if [ -f "./gradlew.bat" ]; then
            gradlew_cmd="./gradlew.bat"
        fi
        ;;
    *)
        echo "当前系统不支持运行此脚本。"
        exit 1
        ;;
esac

# 检测当前架构与对应的默认 Gradle task
case "$arch_name" in
    arm64|aarch64)
        if [ "$current_os" = "macOS" ]; then
            current_arch="ARM（Apple Silicon）"
            current_task="mainShAllMacArm64"
        elif [ "$current_os" = "Windows" ]; then
            current_arch="ARM（Windows Arm64）"
            current_task="mainShAllWindowsArm64"
        fi
        ;;
    x86_64)
        if [ "$current_os" = "macOS" ]; then
            current_arch="Intel（x64）"
            current_task="mainShAllMacX64"
        elif [ "$current_os" = "Windows" ]; then
            current_arch="Intel（x64）"
            current_task="mainShAllWindowsX64"
        fi
        ;;
    *)
        current_arch="未知（${arch_name}）"
        current_task=""
        ;;
esac

# ---------- 步骤 1：选择编译架构 ----------
echo "请选择需要编译的 ${current_os} 架构："
echo "0) 当前电脑平台：${current_arch}（直接回车默认选此项）"
if [ "$current_os" = "macOS" ]; then
    echo "1) ARM（Apple Silicon）"
    echo "2) Intel（x64）"
else
    echo "1) ARM（Windows Arm64）"
    echo "2) Intel（x64）"
fi

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
        if [ "$current_os" = "macOS" ]; then
            echo "已选择：1) ARM（Apple Silicon）"
            "$gradlew_cmd" mainShAllMacArm64
        else
            echo "已选择：1) ARM（Windows Arm64）"
            "$gradlew_cmd" mainShAllWindowsArm64
        fi
        ;;
    2)
        if [ "$current_os" = "macOS" ]; then
            echo "已选择：2) Intel（x64）"
            "$gradlew_cmd" mainShAllMacX64
        else
            echo "已选择：2) Intel（x64）"
            "$gradlew_cmd" mainShAllWindowsX64
        fi
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

# ---------- 步骤 2：是否执行 buildRoot 脚本（默认值按平台挂钩） ----------
echo "请选择是否执行 buildRoot 下的脚本："
echo "0) 不执行"
echo "1) 执行 copyToApplications.sh"
echo "2) 执行 jpackageCmd.sh"

build_action_prompt="请输入 0、1 或 2 [默认 ${default_build_action}]（%d 秒后自动执行 ${default_build_action}）："
build_action=""
seconds_left=3
while [ "$seconds_left" -gt 0 ]; do
    # shellcheck disable=SC2059
    printf "\r${build_action_prompt}" "$seconds_left"
    if read -t 1 -r build_action; then
        break
    fi
    seconds_left=$((seconds_left - 1))
done
printf "\r%*s\r" 60 ""
build_action="${build_action:-${default_build_action}}"

case "$build_action" in
    0)
        echo "已选择：0) 不执行，脚本结束。"
        ;;
    1)
        echo "已选择：1) 执行 copyToApplications.sh"
        ./buildRoot/copyToApplications.sh
        ;;
    2)
        echo "已选择：2) 执行 jpackageCmd.sh"
        ./buildRoot/jpackageCmd.sh
        ;;
    *)
        echo "输入无效，未执行任何脚本。"
        ;;
esac

# ---------- 步骤 3：macOS 下自动结束并重启应用（仅 Darwin 平台执行） ----------
if [ "$current_os" = "macOS" ] && [ -n "$app_path" ]; then
    if [ ! -d "$app_path" ]; then
        echo "警告: 找不到待启动的应用: $app_path，跳过重启步骤。"
        exit 0
    fi

    cancel_restart() {
        echo ""
        echo "已取消结束和重启程序。"
        exit 130
    }
    trap cancel_restart INT TERM

    if /usr/bin/pgrep -x ATools >/dev/null 2>&1; then
        echo ""
        echo "3 秒后结束现有 ATools，按 Ctrl+C 取消。"
        sleep 3
        /usr/bin/pkill -x ATools 2>/dev/null || true
    fi

    echo ""
    restart_now=""
    seconds_left=3
    while [ "$seconds_left" -gt 0 ]; do
        printf "\r%d 秒后重新打开 ATools，按回车立即执行，按 Ctrl+C 取消。" "$seconds_left"
        if read -t 1 -r restart_now; then
            break
        fi
        seconds_left=$((seconds_left - 1))
    done
    printf "\r%*s\r" 70 ""
    /usr/bin/open "$app_path"

    trap - INT TERM
fi

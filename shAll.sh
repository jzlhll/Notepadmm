#!/bin/sh
set -e

cd "$(dirname "$0")"

case "$(uname -s)" in
    Darwin)
        ;;
    MINGW*|MSYS*|CYGWIN*)
        echo "当前系统为 Windows，请在 IDEA 中依次打开 Gradle -> distribution，然后运行 mainShAllWindowsArm64 或 mainShAllWindowsX64。"
        exit 0
        ;;
    *)
        echo "当前系统不支持运行此脚本。"
        exit 1
        ;;
esac

case "$(uname -m)" in
    arm64|aarch64)
        current_arch="ARM（Apple Silicon）"
        current_task="mainShAllMacArm64"
        ;;
    x86_64)
        current_arch="Intel（x64）"
        current_task="mainShAllMacX64"
        ;;
    *)
        current_arch="未知（$(uname -m)）"
        current_task=""
        ;;
esac

echo "请选择需要编译的 macOS 架构："
echo "0) 当前电脑平台：${current_arch}（直接回车默认选此项）"
echo "1) ARM（Apple Silicon）"
echo "2) Intel（x64）"
printf "请输入 0、1 或 2 [默认 0]："
read -r architecture
architecture="${architecture:-0}"

case "$architecture" in
    0)
        if [ -z "$current_task" ]; then
            echo "无法识别当前电脑架构，请手动选择 1 或 2。"
            exit 1
        fi
        echo "已选择：0) 当前电脑平台 -> ${current_arch}"
        ./gradlew "$current_task"
        ;;
    1)
        echo "已选择：1) ARM（Apple Silicon）"
        ./gradlew mainShAllMacArm64
        ;;
    2)
        echo "已选择：2) Intel（x64）"
        ./gradlew mainShAllMacX64
        ;;
    *)
        echo "输入无效，已取消编译。"
        exit 1
        ;;
esac

echo ""
echo "========== Gradle 编译完成 =========="
echo "请选择是否执行 buildRoot 下的脚本："
echo "0) 不执行（直接回车默认选此项）"
echo "1) 执行 copyToApplications.sh"
echo "2) 执行 jpackageCmd.sh"
printf "请输入 0、1 或 2 [默认 0]："
read -r build_action
build_action="${build_action:-0}"

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

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

echo "请选择需要编译的 macOS 架构："
echo "1) ARM（Apple Silicon）"
echo "2) Intel（x64）"
printf "请输入 1 或 2："
read -r architecture

case "$architecture" in
    1)
        ./gradlew mainShAllMacArm64
        ;;
    2)
        ./gradlew mainShAllMacX64
        ;;
    *)
        echo "输入无效，已取消编译。"
        exit 1
        ;;
esac

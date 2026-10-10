#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

usage() {
    echo "用法: $0 [-0 | -f | --fast | -p | --package]"
    echo ""
    echo "  -0            只编译，不执行 buildRoot 下的脚本"
    echo "  -f, --fast    编译后执行 buildRoot/fastCopy.sh（日常开发使用，同步到 /Applications）"
    echo "  -p, --package 编译后生成 dmg，自动替换 /Applications/ATools.app 并启动"
    echo "  -h, --help    显示本帮助"
}

# 关闭现有应用并等待退出，避免打包或替换时文件仍被占用。
stop_running_app() {
    if ! /usr/bin/pgrep -x ATools >/dev/null 2>&1; then
        return
    fi

    trap 'printf "\n已取消结束程序和后续操作。\n"; exit 130' INT TERM
    seconds_left=3
    while [ "$seconds_left" -gt 0 ]; do
        printf "\r%d 秒后结束现有 ATools，按 Ctrl+C 取消。" "$seconds_left"
        sleep 1
        seconds_left=$((seconds_left - 1))
    done
    printf "\n"
    /usr/bin/pkill -x ATools 2>/dev/null || true

    seconds_left=10
    while /usr/bin/pgrep -x ATools >/dev/null 2>&1; do
        if [ "$seconds_left" -eq 0 ]; then
            echo "错误: ATools 尚未退出，请手动关闭后重试。"
            exit 1
        fi
        sleep 1
        seconds_left=$((seconds_left - 1))
    done
    trap - INT TERM
}

# 使用本次打包产物安装；临时应用与备份均放在目标目录所在文件系统。
install_package() (
    dmg_path=$1
    mount_dir=""
    install_dir=""
    install_complete=false
    installer_command=()

    cleanup_install() {
        install_status=$?
        trap - EXIT INT TERM
        keep_backup=false
        if [ "$install_complete" = false ] && [ -n "$install_dir" ] &&
            "${installer_command[@]}" /bin/test -d "$install_dir/previous.app"; then
            # 若替换已完成但收到中断，先移走新应用，避免 mv 将备份嵌入应用目录。
            if [ -e "$app_path" ] && ! "${installer_command[@]}" /bin/mv "$app_path" "$install_dir/incomplete.app"; then
                keep_backup=true
            elif ! "${installer_command[@]}" /bin/mv "$install_dir/previous.app" "$app_path"; then
                keep_backup=true
            fi
            if [ "$keep_backup" = true ]; then
                echo "错误: 恢复旧应用失败，备份保留在 $install_dir/previous.app。"
                install_status=1
            fi
        fi
        if [ -n "$install_dir" ] && [ "$keep_backup" = false ]; then
            if ! "${installer_command[@]}" /bin/rm -rf "$install_dir"; then
                echo "警告: 未能清理安装临时目录: $install_dir"
            fi
        fi
        if [ -n "$mount_dir" ]; then
            if /usr/bin/hdiutil detach "$mount_dir"; then
                /bin/rmdir "$mount_dir" || true
            elif ! /bin/rmdir "$mount_dir" 2>/dev/null; then
                echo "错误: 无法卸载 DMG，请手动卸载: $mount_dir"
                install_status=1
            fi
        fi
        exit "$install_status"
    }
    trap cleanup_install EXIT
    trap 'exit 130' INT TERM

    if [ ! -f "$dmg_path" ]; then
        echo "错误: 找不到本次生成的 DMG: $dmg_path"
        exit 1
    fi
    if [ ! -w /Applications ]; then
        echo "安装到 /Applications 需要管理员权限。"
        /usr/bin/sudo -v
        installer_command=(/usr/bin/sudo)
    fi

    install_dir=$("${installer_command[@]}" /usr/bin/mktemp -d '/Applications/.ATools-install.XXXXXX')
    mount_dir=$(/usr/bin/mktemp -d "${TMPDIR:-/tmp}/ATools-dmg.XXXXXX")
    /usr/bin/hdiutil attach -readonly -nobrowse -mountpoint "$mount_dir" "$dmg_path"
    if [ ! -d "$mount_dir/ATools.app" ]; then
        echo "错误: DMG 中找不到 ATools.app。"
        exit 1
    fi

    "${installer_command[@]}" /usr/bin/ditto "$mount_dir/ATools.app" "$install_dir/ATools.app"
    "${installer_command[@]}" /usr/bin/codesign --verify --deep --strict "$install_dir/ATools.app"
    stop_running_app
    # stop_running_app 会重置退出信号处理，此处恢复安装清理逻辑。
    trap 'exit 130' INT TERM
    if [ -e "$app_path" ]; then
        "${installer_command[@]}" /bin/mv "$app_path" "$install_dir/previous.app"
    fi
    "${installer_command[@]}" /bin/mv "$install_dir/ATools.app" "$app_path"
    install_complete=true
    echo "已安装本次打包的应用: $app_path"
)

# ---------- 步骤 0：解析参数 ----------
build_action=""
while [ $# -gt 0 ]; do
    case "$1" in
        -0) build_action="0" ;;
        -f|--fast) build_action="1" ;;
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
current_os=""
current_arch=""
current_task=""
gradlew_cmd="./gradlew"
app_path=""

case "$os_name" in
    Darwin)
        current_os="macOS"
        app_path='/Applications/ATools.app'
        ;;
    *)
        echo "此脚本仅支持 macOS；Windows 请使用 windowsBuild.sh。"
        exit 1
        ;;
esac

# 检测当前架构与对应的默认 Gradle task
case "$arch_name" in
    arm64|aarch64)
        current_arch="ARM（Apple Silicon）"
        current_task="mainShAllMacArm64"
        ;;
    x86_64)
        current_arch="Intel（x64）"
        current_task="mainShAllMacX64"
        ;;
    *)
        current_arch="未知（${arch_name}）"
        current_task=""
        ;;
esac

# ---------- 步骤 1：选择编译架构 ----------
echo "请选择需要编译的 ${current_os} 架构："
echo "0) 当前电脑平台：${current_arch}（直接回车默认选此项）"
echo "1) ARM（Apple Silicon）"
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
        echo "已选择：1) ARM（Apple Silicon）"
        "$gradlew_cmd" mainShAllMacArm64
        ;;
    2)
        echo "已选择：2) Intel（x64）"
        "$gradlew_cmd" mainShAllMacX64
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
    1)
        echo "已选择：1) 执行 fastCopy.sh（日常开发使用）"
        ./buildRoot/fastCopy.sh
        ;;
    2)
        stop_running_app
        echo "已选择：2) 执行 pack.sh"
        ./buildRoot/pack.sh
        IFS= read -r dmg_path < ./buildRoot/package-dmg.path
        install_package "$dmg_path"
        ;;
esac

# ---------- 步骤 3：同步或安装后自动重启应用 ----------
if [ "$build_action" != "0" ]; then
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

    stop_running_app
    trap cancel_restart INT TERM

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

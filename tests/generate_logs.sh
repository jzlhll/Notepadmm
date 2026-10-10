#!/bin/sh
set -eu

if [ "$#" -eq 0 ]; then set -- --help; fi

lines=1000000
output=
seed=
start='2026-10-10 15:44:52.956'
force=0
max_file_bytes=3000000000
estimated_bytes_per_line=250
min_free_bytes=5000000000
confirm_file_bytes=1000000000
warning_free_bytes=10000000000
max_lines=$((max_file_bytes / estimated_bytes_per_line))
newline_bytes=1

while [ "$#" -gt 0 ]; do
    case "$1" in
        --help|-h)
            cat <<'HELP'
用法：sh tests/generate_logs.sh [--lines 行数] [--output 文件路径] [--seed 随机种子]
                              [--start '2026-10-10 15:44:52.956'] [--force] [--help]

示例：
  sh tests/generate_logs.sh --lines 1000000  # 100 万行
  sh tests/generate_logs.sh --lines 100w  # 100 万行
  sh tests/generate_logs.sh --lines 100k  # 10 万行
HELP
            exit 0
            ;;
        --force) force=1; shift ;;
        --lines|--output|--seed|--start)
            if [ "$#" -lt 2 ]; then
                printf 'Missing value for %s\n' "$1" >&2
                exit 1
            fi
            case "$1" in
                --lines) lines=$2 ;;
                --output) output=$2 ;;
                --seed) seed=$2 ;;
                --start) start=$2 ;;
            esac
            shift 2
            ;;
        *) printf 'Unknown option: %s\n' "$1" >&2; exit 1 ;;
    esac
done

lines_multiplier=1
case "$lines" in
    *[wW]) lines=${lines%?}; lines_multiplier=10000 ;;
    *[kK]) lines=${lines%?}; lines_multiplier=1000 ;;
esac
case "$lines" in
    ''|*[!0-9]*) printf '%s\n' '--lines must be a positive integer, optionally followed by w/W or k/K' >&2; exit 1 ;;
esac
while [ "${lines#0}" != "$lines" ]; do lines=${lines#0}; done
# 先按单位检查数值上限，再相乘，避免过大的后缀输入发生整数溢出。
max_line_units=$((max_lines / lines_multiplier))
if [ -z "$lines" ] || [ "${#lines}" -gt "${#max_line_units}" ] || [ "$lines" -gt "$max_line_units" ]; then
    printf 'Generation refused: --lines must resolve to 1..%s lines; the file limit is 3 GB.\n' "$max_lines" >&2
    exit 1
fi
lines=$((lines * lines_multiplier))

# 按观察到的约 200 字节每行预留 25% 余量，超限时不创建输出文件。
estimated_bytes=$((lines * estimated_bytes_per_line))
if [ "$estimated_bytes" -gt "$max_file_bytes" ]; then
    printf 'Generation refused: estimated %s bytes exceeds the 3 GB limit. Use --lines <= %s.\n' \
        "$estimated_bytes" "$((max_file_bytes / estimated_bytes_per_line))" >&2
    exit 1
fi

if [ -z "$seed" ]; then
    seed=$(( ($(date +%s) + $$) % 2147483647 ))
fi
case "$seed" in
    *[!0-9]*) printf '%s\n' '--seed must be a nonnegative integer' >&2; exit 1 ;;
esac
while [ "${seed#0}" != "$seed" ]; do seed=${seed#0}; done
seed=${seed:-0}
if [ "${#seed}" -gt 10 ] || [ "$seed" -gt 2147483646 ]; then
    printf '%s\n' '--seed must be in 0..2147483646' >&2
    exit 1
fi

case "$start" in
    [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' '[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]) ;;
    *) printf '%s\n' '--start must use YYYY-MM-DD HH:MM:SS.mmm' >&2; exit 1 ;;
esac
date_part=${start%% *}
clock_part=${start#* }
year=${date_part%%-*}
date_part=${date_part#*-}
month=${date_part%%-*}
day=${date_part#*-}
hour=${clock_part%%:*}
clock_part=${clock_part#*:}
minute=${clock_part%%:*}
clock_part=${clock_part#*:}
second=${clock_part%%.*}
millisecond=${clock_part#*.}
# 先添加十进制前缀，避免带前导零的时间字段被按八进制解析。
year=$((1$year - 10000))
month=$((1$month - 100))
day=$((1$day - 100))
hour=$((1$hour - 100))
minute=$((1$minute - 100))
second=$((1$second - 100))
millisecond=$((1$millisecond - 1000))
month_days=31
case "$month" in
    4|6|9|11) month_days=30 ;;
    2)
        month_days=28
        if [ "$((year % 400))" -eq 0 ] || { [ "$((year % 4))" -eq 0 ] && [ "$((year % 100))" -ne 0 ]; }; then
            month_days=29
        fi
        ;;
esac
if [ "$year" -lt 1 ] || [ "$month" -lt 1 ] || [ "$month" -gt 12 ] ||
   [ "$day" -lt 1 ] || [ "$day" -gt "$month_days" ] ||
   [ "$hour" -gt 23 ] || [ "$minute" -gt 59 ] || [ "$second" -gt 59 ]; then
    printf '%s\n' '--start contains an invalid date or time' >&2
    exit 1
fi

user_directory=$HOME
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        newline_bytes=2
        user_directory=${USERPROFILE:-$HOME}
        user_directory=$(cygpath -u "$user_directory")
        if [ -n "$output" ]; then output=$(cygpath -u "$output"); fi
        ;;
esac
if [ -z "$output" ]; then output="$user_directory/Downloads/generated/log-$lines.log"; fi
case "$output" in
    '~/'*) output="$user_directory/${output#\~/}" ;;
esac
output_directory=$(dirname -- "$output")
if [ "$force" -eq 0 ] && { [ -e "$output" ] || [ -L "$output" ]; }; then
    printf 'Generation refused: output already exists: %s. Use --force to overwrite it.\n' "$output" >&2
    exit 1
fi
mkdir -p -- "$output_directory"

# 初次预判和用户确认后都重新获取磁盘预算，检查通过前不打开输出文件。
checkDiskBudget() {
    if ! disk_usage=$(LC_ALL=C df -Pk "$output_directory"); then
        printf '%s\n' 'Generation refused: cannot determine available disk space.' >&2
        exit 1
    fi
    if ! available_bytes=$(printf '%s\n' "$disk_usage" | LC_ALL=C awk '
        NR > 1 {
            for (field = 2; field <= NF; field++) {
                if ($field ~ /^[0-9]+%$/ && $(field - 1) ~ /^[0-9]+$/) {
                    available = $(field - 1)
                    found = 1
                    break
                }
            }
        }
        END {
            if (!found) exit 1
            printf "%.0f\n", available * 1024
        }
    '); then
        printf '%s\n' 'Generation refused: cannot parse available disk space.' >&2
        exit 1
    fi
    if [ "$available_bytes" -le "$min_free_bytes" ]; then
        printf 'Generation refused: available %s bytes; at least 5 GB must remain free.\n' "$available_bytes" >&2
        exit 1
    fi
    write_limit=$((available_bytes - min_free_bytes))
    if [ "$write_limit" -gt "$max_file_bytes" ]; then write_limit=$max_file_bytes; fi
    if [ "$estimated_bytes" -gt "$write_limit" ]; then
        printf 'Generation refused: estimated %s bytes exceeds the disk budget of %s bytes after reserving 5 GB.\n' \
            "$estimated_bytes" "$write_limit" >&2
        exit 1
    fi
}

checkDiskBudget
if [ "$estimated_bytes" -gt "$confirm_file_bytes" ] || [ "$available_bytes" -lt "$warning_free_bytes" ]; then
    if [ "$estimated_bytes" -gt "$confirm_file_bytes" ]; then
        printf '%s\n' '本次预估生成量超过 1GB，需要再次确认。' >&2
    fi
    if [ "$available_bytes" -lt "$warning_free_bytes" ]; then
        printf '%s\n' '你的电脑磁盘空间不太充足：可用空间少于 10GB。' >&2
    fi
    LC_ALL=C awk -v requested="$lines" -v estimated="$estimated_bytes" -v available="$available_bytes" '
        BEGIN {
            printf "目标 %.0f 行；当前可用 %.2fGB；预估生成 %.2fGB。\n", requested, available / 1000000000, estimated / 1000000000
        }
    ' >&2
    if [ ! -t 0 ]; then
        printf '%s\n' 'Generation refused: interactive confirmation is required. Run from a terminal.' >&2
        exit 1
    fi
    printf '是否执行？输入 y 或 yes 确认，其他输入取消 [y/N]：' >&2
    answer=
    if ! IFS= read -r answer; then
        printf '%s\n' 'Generation cancelled: confirmation not received.' >&2
        exit 1
    fi
    case "$answer" in
        y|Y|yes|Yes|YES) ;;
        *) printf '%s\n' 'Generation cancelled.' >&2; exit 1 ;;
    esac
    checkDiskBudget
fi
printf 'Target: %s\nEstimated bytes: %s; write limit: %s; disk reserve: %s\n' \
    "$output" "$estimated_bytes" "$write_limit" "$min_free_bytes"
if [ "$force" -eq 0 ]; then set -C; fi
# 整个生成过程只打开一次输出文件，默认以 noclobber 防止覆盖。
exec 3>"$output"
set +C
trap 'printf "Generation interrupted; partial output file preserved.\n" >&2; exit 130' INT
trap 'printf "Generation terminated; partial output file preserved.\n" >&2; exit 143' TERM

LOG_GEN_LINES="$lines" LOG_GEN_SEED="$seed" LOG_GEN_START="$start" \
LOG_GEN_WRITE_LIMIT="$write_limit" LOG_GEN_NEWLINE_BYTES="$newline_bytes" LC_ALL=C awk '
function randomInt(limit) {
    randomState = (randomState * 16807) % 2147483647
    return int(randomState / 2147483647 * limit)
}

function addEntry(entries, entryIndex, tag, level, message, systemRecord) {
    entries[entryIndex, "tag"] = tag
    entries[entryIndex, "level"] = level
    entries[entryIndex, "message"] = message
    entries[entryIndex, "system"] = systemRecord
}

function loadEntry(entries, entryIndex) {
    entryTag = entries[entryIndex, "tag"]
    entryLevel = entries[entryIndex, "level"]
    message = entries[entryIndex, "message"]
    systemEntry = entries[entryIndex, "system"]
}

# 固定字节环境下，非 UTF-8 续字节的数量就是字符数量。
function utf8Length(text, copy) {
    copy = text
    return gsub(/[^\200-\277]/, "", copy)
}

function utf8Prefix(text, limit, byteIndex, count) {
    for (byteIndex = 1; byteIndex <= length(text); byteIndex++) {
        if (substr(text, byteIndex, 1) !~ /[\200-\277]/) {
            count++
            if (count > limit) return substr(text, 1, byteIndex - 1)
        }
    }
    return text
}

function daysInMonth(year, month) {
    if (month == 2) return 28 + (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0))
    if (month == 4 || month == 6 || month == 9 || month == 11) return 30
    return 31
}

# 自行处理日历进位，兼容缺少 mktime、strftime 的系统 awk。
function advance(gap, choice, days) {
    gap = 0
    if (remaining == 0) {
        choice = randomInt(100)
        if (choice < 70) { mode = 0; remaining = 100 + randomInt(2401) }
        else if (choice < 95) { mode = 1; remaining = 100 + randomInt(501) }
        else { mode = 2; remaining = 5 + randomInt(26) }
        if (randomInt(10) == 0) gap = 30000 + randomInt(570001)
    }
    # 密集时段可增加 0 毫秒，允许相邻多条日志使用同一时间。
    if (mode == 0) gap += randomInt(5)
    else if (mode == 1) gap += 10 + randomInt(791)
    else gap += 1000 + randomInt(29001)
    remaining--
    millisecond += gap
    second += int(millisecond / 1000); millisecond %= 1000
    minute += int(second / 60); second %= 60
    hour += int(minute / 60); minute %= 60
    if (hour >= 24) {
        day += int(hour / 24); hour %= 24
        while (day > (days = daysInMonth(year, month))) {
            day -= days
            month++
            if (month > 12) { month = 1; year++ }
        }
        if (year > 9999) {
            print "Generation failed: timestamp exceeds year 9999" > "/dev/stderr"
            exit 1
        }
        dateText = sprintf("%04d-%02d-%02d", year, month, day)
    }
}

function normalEntry(request, count, english, choice, result, scanned, elapsed) {
    entryTag = "Api"
    request = sprintf("%06x", randomInt(16777216))
    count = 1 + randomInt(500)
    english = randomInt(2)
    choice = randomInt(8)
    if (choice == 0) {
        message = "D Api: request [" request "]: https://logs.example.invalid/api/v1/messages method: GET"
    } else if (choice == 1) {
        message = "D Api: request [" request "] paramsStr [1/1]: {\"category\":" (1 + randomInt(5)) ",\"page\":" (1 + randomInt(99)) "}"
    } else if (choice == 2) {
        result = "{\"status\":true,\"code\":0,\"message\":\"" (english ? "success" : "请求成功") "\",\"data\":{\"total\":" count ",\"items\":[{\"id\":\"sample-" request "\",\"name\":\"" (english ? "demo photo" : "示例照片") "\"}]}}"
        message = "D Api: result [" request "]: " result
    } else if (choice == 3) {
        entryTag = "PhotoScanner"
        scanned = 100 + randomInt(99901)
        message = english ? "scan progress: batch=" count " scanned=" scanned : "扫描进度：批次=" count " 已扫描=" scanned
    } else if (choice == 4) {
        entryTag = "TransferManager"; entryLevel = "I"
        message = "Transfer lifecycle changed: generation=" count ", foreground=" (randomInt(2) ? "true" : "false")
    } else if (choice == 5) {
        entryTag = "ActivityManager"; entryLevel = "I"
        message = "execute start, ActivityRecord{" request " token=synthetic-" request " {" appPackage "/com.example.ui.DemoActivity}}"
    } else if (choice == 6) {
        entryTag = "SystemObserver"; entryLevel = "I"; systemEntry = 1
        message = "notifyActivityState pkg:" appPackage "/com.example.ui.DemoActivity state:15 fg:false uid:12000"
    } else {
        entryTag = "TaskScheduler"
        elapsed = 1 + randomInt(4999)
        message = english ? "task completed: id=job-" request " elapsed=" elapsed "ms count=" count : "任务完成：id=job-" request " 耗时=" elapsed "ms 数量=" count
    }
}

function formatPrefix(pid, packageName) {
    pid = systemEntry ? 4200 : appPid
    packageName = systemEntry ? "com.example.systemservice" : appPackage
    return sprintf("%s %5d-%-5d %-24s %-36s %s  ", timestamp, pid, tid, entryTag, packageName, entryLevel)
}

function longEntry(targetLength, head, tail, detailLength, detail, fragmentIndex, fragment) {
    entryTag = "Api"
    targetLength = 600 + randomInt(901)
    head = "D Api: result: {\"status\":true,\"code\":0,\"message\":\"success\",\"data\":{\"id\":\"sample-" randomInt(100000) "\",\"detail\":\""
    tail = "\"}}"
    detailLength = targetLength - length(formatPrefix()) - length(head) - length(tail)
    detail = ""
    while (detailLength > 0) {
        fragmentIndex = 1 + randomInt(8)
        fragment = detailFragments[fragmentIndex]
        if (detailLengths[fragmentIndex] > detailLength) {
            detail = detail utf8Prefix(fragment, detailLength)
            break
        }
        detail = detail fragment
        detailLength -= detailLengths[fragmentIndex]
    }
    message = head detail tail
}

function generate(line, percentage, checkpoint, choice, text, lineBytes, writtenBytes, packageChoice, packageIndex) {
    percentage = 10
    checkpoint = int((lineCount * percentage + 99) / 100)
    for (line = 1; line <= lineCount; line++) {
        if (line > 1) advance()
        timestamp = sprintf("%s %02d:%02d:%02d.%03d", dateText, hour, minute, second, millisecond)
        packageChoice = randomInt(10)
        packageIndex = packageChoice < 7 ? 1 : packageChoice - 5
        appPackage = appPackages[packageIndex]
        appPid = appPids[packageIndex]
        tid = threads[1 + randomInt(6)] - 12000 + appPid
        entryTag = "logDemo"; entryLevel = "D"; systemEntry = 0
        if (lineCount >= 100 && percentage <= 100 && line == checkpoint) {
            entryTag = "SearchCheckpoint"; entryLevel = "I"
            message = "SEARCH_AHCHOR：" percentage "%"
            percentage += 10
            checkpoint = int((lineCount * percentage + 99) / 100)
            counters[5]++
        } else {
            choice = randomInt(10000)
            if (choice < 8000) { loadEntry(commonEntries, 1 + randomInt(commonCount)); counters[0]++ }
            else if (choice < 9600) { normalEntry(); counters[1]++ }
            else if (choice < 9900) { longEntry(); counters[2]++ }
            else if (choice < 9999) { loadEntry(warningEntries, 1 + randomInt(warningCount)); counters[3]++ }
            else { loadEntry(rareEntries, 1 + randomInt(rareCount)); counters[4]++ }
        }
        if (systemEntry) tid = 4300
        text = formatPrefix() message
        if (length(text) > 1500 && utf8Length(text) > 1500) {
            printf "Generation failed: line %.0f exceeds 1500 characters\n", line > "/dev/stderr"
            exit 1
        }
        # 正文按实际 UTF-8 字节计数；Windows 为可能的 CRLF 换行预留两个字节。
        lineBytes = length(text) + newlineBytes
        if (writtenBytes + lineBytes > writeLimit) {
            printf "Generation stopped: next line would exceed the %.0f-byte write limit; generated %.0f/%.0f lines, %.0f budgeted bytes. Partial output file preserved.\n", writeLimit, line - 1, lineCount, writtenBytes > "/dev/stderr"
            exit 2
        }
        print text
        writtenBytes += lineBytes
        if (line % 100000 == 0) printf "Generated %.0f/%.0f lines\n", line, lineCount > "/dev/stderr"
    }
    printf "Common: %.0f; normal: %.0f; long: %.0f; warning: %.0f; rare: %.0f; checkpoints: %.0f\n", counters[0], counters[1], counters[2], counters[3], counters[4], counters[5] > "/dev/stderr"
    print "Time range: " ENVIRON["LOG_GEN_START"] " -> " timestamp > "/dev/stderr"
}

BEGIN {
    lineCount = ENVIRON["LOG_GEN_LINES"] + 0
    writeLimit = ENVIRON["LOG_GEN_WRITE_LIMIT"] + 0
    newlineBytes = ENVIRON["LOG_GEN_NEWLINE_BYTES"] + 0
    randomState = ENVIRON["LOG_GEN_SEED"] + 0
    if (randomState == 0) randomState = 1
    split(ENVIRON["LOG_GEN_START"], dateFields, /[- :.]/)
    year = dateFields[1] + 0; month = dateFields[2] + 0; day = dateFields[3] + 0
    hour = dateFields[4] + 0; minute = dateFields[5] + 0; second = dateFields[6] + 0; millisecond = dateFields[7] + 0
    dateText = sprintf("%04d-%02d-%02d", year, month, day)
    split("12000 12015 13069 17138 18870 18874", threads, " ")
    appPackages[1] = "com.example.photolab"; appPids[1] = 12000
    appPackages[2] = "com.example.notebox"; appPids[2] = 14000
    appPackages[3] = "com.example.syncservice"; appPids[3] = 16000
    appPackages[4] = "com.example.mediastore"; appPids[4] = 18000

    addEntry(commonEntries, ++commonCount, "logDemo", "D", "D PhotoScanner: start Scan LocalPhotos...")
    addEntry(commonEntries, ++commonCount, "PhotoScanner", "D", "D PhotoScanner: 开始扫描本地照片，等待索引更新")
    addEntry(commonEntries, ++commonCount, "PhotoScanner", "D", "D PhotoScanner: stopped and resources released.")
    addEntry(commonEntries, ++commonCount, "WindowManager", "I", "trimMemory level: 5")
    addEntry(commonEntries, ++commonCount, "WindowManager", "I", "trimMemory level: 40")
    addEntry(commonEntries, ++commonCount, "TaskScheduler", "D", "heartbeat: worker alive, queue empty")
    addEntry(commonEntries, ++commonCount, "TaskScheduler", "D", "任务队列为空，等待下一次调度")
    addEntry(commonEntries, ++commonCount, "MQTT", "D", "D MqttManager: network.state foreground=false validated=false source=background")
    addEntry(commonEntries, ++commonCount, "MQTT", "D", "D MqttManager: app.lifecycle foreground=false")
    addEntry(commonEntries, ++commonCount, "MQTT", "D", "D MqttManager: heartbeat acknowledged")
    addEntry(commonEntries, ++commonCount, "LifecycleTransaction", "I", "activityCallbacks TopResumedActivityChangeItem{onTop=false}")
    addEntry(commonEntries, ++commonCount, "LifecycleTransaction", "I", "lifecycleStateRequest PauseActivityItem{finished=false,userLeaving=true,configChanges=0}")
    addEntry(commonEntries, ++commonCount, "LifecycleTransaction", "I", "lifecycleStateRequest StopActivityItem{showWindow=false,configChanges=0}")
    addEntry(commonEntries, ++commonCount, "ViewRoot", "I", "remove sceneId 10 topId: 0")
    addEntry(commonEntries, ++commonCount, "CacheManager", "D", "cache hit: thumbnail ready")
    addEntry(commonEntries, ++commonCount, "CacheManager", "D", "缓存命中：缩略图已准备完成")
    addEntry(commonEntries, ++commonCount, "Api", "D", "D Api: result: {\"status\":true,\"code\":0,\"message\":\"success\",\"data\":{\"total\":0,\"items\":[]}}")
    addEntry(commonEntries, ++commonCount, "Renderer", "D", "render completed")
    addEntry(commonEntries, ++commonCount, "Renderer", "D", "页面渲染完成")
    addEntry(commonEntries, ++commonCount, "SystemScheduler", "I", "process idle, waiting for work", 1)
    addEntry(commonEntries, ++commonCount, "Database", "D", "query completed: table=media_index rows=0")
    addEntry(commonEntries, ++commonCount, "FileWatcher", "I", "目录监控已刷新，等待文件变更")
    addEntry(commonEntries, ++commonCount, "ImageLoader", "D", "decode completed: thumbnail ready")
    addEntry(commonEntries, ++commonCount, "UploadWorker", "D", "上传队列为空，等待新任务")
    addEntry(commonEntries, ++commonCount, "SyncService", "I", "sync completed: no pending changes")
    addEntry(commonEntries, ++commonCount, "ConnectivityMonitor", "D", "网络状态正常，连接已验证")
    addEntry(commonEntries, ++commonCount, "DownloadManager", "D", "download queue idle")
    addEntry(commonEntries, ++commonCount, "DiskCache", "D", "磁盘缓存检查完成")

    addEntry(warningEntries, ++warningCount, "NetworkClient", "W", "NETWORK_TIMEOUT: connection timed out, retry scheduled")
    addEntry(warningEntries, ++warningCount, "NetworkClient", "W", "NETWORK_TIMEOUT: 请求超时，已安排重试")
    addEntry(warningEntries, ++warningCount, "PhotoScanner", "W", "INDEX_RETRY: media index temporarily unavailable")
    addEntry(warningEntries, ++warningCount, "PhotoScanner", "W", "INDEX_RETRY: 媒体索引暂不可用，稍后重试")
    addEntry(warningEntries, ++warningCount, "CacheManager", "W", "CACHE_EVICT: memory pressure detected, clearing stale entries")
    addEntry(warningEntries, ++warningCount, "CacheManager", "W", "CACHE_EVICT: 内存紧张，正在清理过期条目")

    addEntry(rareEntries, ++rareCount, "TransferManager", "E", "RARE_EVENT: CHECKSUM_MISMATCH, transfer aborted")
    addEntry(rareEntries, ++rareCount, "TransferManager", "E", "RARE_EVENT: CHECKSUM_MISMATCH，数据校验失败，传输已终止")
    addEntry(rareEntries, ++rareCount, "SessionManager", "E", "RARE_EVENT: TOKEN_EXPIRED, synthetic session rejected")
    addEntry(rareEntries, ++rareCount, "StorageManager", "E", "RARE_EVENT: STORAGE_READ_ONLY，模拟存储只读异常")

    detailFragments[1] = "thumbnail ready; "; detailFragments[2] = "scan batch completed; "
    detailFragments[3] = "cache hit; "; detailFragments[4] = "upload pending; "
    detailFragments[5] = "网络连接正常；"; detailFragments[6] = "照片索引已更新；"
    detailFragments[7] = "后台任务等待调度；"; detailFragments[8] = "缓存命中；"
    for (fragmentIndex = 1; fragmentIndex <= 8; fragmentIndex++) detailLengths[fragmentIndex] = utf8Length(detailFragments[fragmentIndex])
    generate()
    exit
}
' </dev/null >&3

exec 3>&-
bytes=$(wc -c <"$output")
printf 'Output: %s\nLines: %s; bytes: %s; seed: %s\n' "$output" "$lines" "$bytes" "$seed"

#!/bin/bash
# run_machud.sh - MacHUD 综合服务运行与自愈守护进程

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$BASE_DIR"

# 清理残留进程
pkill -f "$BASE_DIR/stats_server" 2>/dev/null || true
pkill -f "$BASE_DIR/audio_bridge" 2>/dev/null || true

echo "[$(date)] 启动 stats_server..."
"$BASE_DIR/stats_server" > "$BASE_DIR/stats_server.log" 2>&1 &
STATS_PID=$!

echo "[$(date)] 启动 audio_bridge..."
"$BASE_DIR/audio_bridge" > "$BASE_DIR/audio_bridge.log" 2>&1 &
AUDIO_PID=$!

# 自动守护循环：检测进程存活与 ADB reverse 端口映射
cleanup() {
    echo "[$(date)] 接收到退出信号，关闭子服务..."
    kill $STATS_PID 2>/dev/null || true
    kill $AUDIO_PID 2>/dev/null || true
    exit 0
}
trap cleanup SIGINT SIGTERM

echo "[$(date)] MacHUD 服务已就绪 (stats: $STATS_PID, audio: $AUDIO_PID)"

while true; do
    # 1. 检查 stats_server 进程
    if ! kill -0 $STATS_PID 2>/dev/null; then
        echo "[$(date)] stats_server 异常退出，立即拉起..."
        "$BASE_DIR/stats_server" >> "$BASE_DIR/stats_server.log" 2>&1 &
        STATS_PID=$!
    fi

    # 2. 检查 audio_bridge 进程
    if ! kill -0 $AUDIO_PID 2>/dev/null; then
        echo "[$(date)] audio_bridge 异常退出，立即拉起..."
        "$BASE_DIR/audio_bridge" >> "$BASE_DIR/audio_bridge.log" 2>&1 &
        AUDIO_PID=$!
    fi

    # 3. 检查 ADB reverse 规则
    if adb get-state 2>/dev/null | grep -q "device"; then
        REV_LIST=$(adb reverse --list 2>/dev/null)
        if ! echo "$REV_LIST" | grep -q "tcp:9527"; then
            adb reverse tcp:9527 tcp:9527 2>/dev/null || true
        fi
        if ! echo "$REV_LIST" | grep -q "tcp:9528"; then
            adb reverse tcp:9528 tcp:9528 2>/dev/null || true
        fi
    fi

    sleep 3
done

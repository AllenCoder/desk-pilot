#!/system/bin/sh
# ==============================================================================
# Samsung Galaxy Note 3 (N9008V) 电池健康管家 (Battery Guard Daemon)
# 作用：限制充电至 80% 停止充电，70% 恢复充电，防止 7x24 小时插线过充发热鼓包。
# ==============================================================================

NODE_SLATE="/sys/class/power_supply/battery/batt_slate_mode"
NODE_CAPACITY="/sys/class/power_supply/battery/capacity"
NODE_STATUS="/sys/class/power_supply/battery/status"
LOG_FILE="/data/local/tmp/battery_guard.log"

MAX_LIMIT=80  # 停止充电阈值
MIN_LIMIT=70  # 恢复充电阈值

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Battery Guard 启动成功，保护区间: ${MIN_LIMIT}% - ${MAX_LIMIT}%" >> "$LOG_FILE"

while true; do
    if [ -f "$NODE_CAPACITY" ]; then
        CAP=$(cat "$NODE_CAPACITY" 2>/dev/null)
        STATUS=$(cat "$NODE_STATUS" 2>/dev/null)
        SLATE=$(cat "$NODE_SLATE" 2>/dev/null)

        # 当电量达到或超过 80% 且还在充电或尚未处于停充模式时 -> 立即切断充电
        if [ "$CAP" -ge "$MAX_LIMIT" ]; then
            if [ "$SLATE" != "1" ]; then
                echo 1 > "$NODE_SLATE"
                echo "[$(date '+%Y-%m-%d %H:%M:%S')] 电量已达 ${CAP}% (>=${MAX_LIMIT}%) -> 切断充电，开启保护" >> "$LOG_FILE"
            fi
        fi

        # 当电量自然消耗低于 70% 且当前处于停充模式时 -> 恢复充电补电
        if [ "$CAP" -le "$MIN_LIMIT" ]; then
            if [ "$SLATE" != "0" ]; then
                echo 0 > "$NODE_SLATE"
                echo "[$(date '+%Y-%m-%d %H:%M:%S')] 电量降至 ${CAP}% (<=${MIN_LIMIT}%) -> 恢复充电补电" >> "$LOG_FILE"
            fi
        fi
    fi
    sleep 20
done

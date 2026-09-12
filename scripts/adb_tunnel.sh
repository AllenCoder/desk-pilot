#!/bin/bash
# 建立 USB ADB 反向端口映射
echo "==> Configuring ADB reverse port forwarding (9527: Stats, 9528: Audio)..."
adb reverse tcp:9527 tcp:9527
adb reverse tcp:9528 tcp:9528
echo "==> Current reverse rules:"
adb reverse --list
echo "==> Success! Note 3 can now access Mac host via 127.0.0.1"

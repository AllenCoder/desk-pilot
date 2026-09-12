#!/bin/bash
# 安装与配置 BlackHole 2ch 虚拟音频驱动 (让 Note 3 作为 Mac 实体麦克风输入)
echo "==> Checking for Homebrew and BlackHole..."
if ! brew list blackhole-2ch >/dev/null 2>&1; then
    echo "==> Installing BlackHole 2ch via Homebrew..."
    brew install blackhole-2ch
    echo "==> Installed! Restart CoreAudio or apps if necessary."
else
    echo "==> BlackHole 2ch is already installed."
fi
echo "==> Note: Please go to System Settings -> Sound -> Input and select 'BlackHole 2ch' as default input."

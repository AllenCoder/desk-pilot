<div align="center">

# ⚡ DeskPilot

**为 Mac 而生的终极桌面副驾：全景工控遥测 · 环境气象微站 · 低延迟无线麦克风**
*(Powered by Reborn Flagship AMOLED Hardware)*

[![macOS](https://img.shields.io/badge/macOS-11.0%2B-black?style=flat-square&logo=apple)](https://apple.com)
[![Android](https://img.shields.io/badge/Android-5.0%2B-green?style=flat-square&logo=android)](https://android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Swift](https://img.shields.io/badge/Swift-5.0%2B-orange?style=flat-square&logo=swift)](https://swift.org)
[![AMOLED Care](https://img.shields.io/badge/AMOLED-Burn--in%20Protected-purple?style=flat-square)](https://github.com/AllenCoder/desk-pilot)

[English Documentation](README_EN.md) · [功能特性](#-核心功能) · [快速开始](#-快速开始) · [护屏机制](#-amoled-全方位防烧屏体系) · [架构原理](#-系统架构)

<br/>

<img src="docs/images/hud_preview.png" alt="DeskPilot 主仪表盘界面" width="880"/>

</div>

---

## 📖 项目起源与理念

**DeskPilot** 的初心是打造属于 macOS 极客的 **“桌面第二视界 / 智能副驾”**。

与其让十年前的三星 Galaxy Note 3 等一代经典老机皇在抽屉里吃灰，或沦为电子垃圾，我们深度挖掘其超越现代许多塑料副屏的绝妙硬件天赋：
- **5.7 英寸 1080P Super AMOLED 屏幕**（纯黑像素完全断电、高对比度、极客发色）；
- **内置博世（BOSCH）工业级微型气压计**（`Sensor.TYPE_PRESSURE`，实时感知环境微气压与海拔）；
- **内置盛思锐（Sensirion SHTC1）温湿度传感器**（`Sensor.TYPE_RELATIVE_HUMIDITY` & `AMBIENT_TEMP`）；
- **美信（Maxim MAX88921）环境光度与 RGB 色温传感器**；
- **双路物理降噪麦克风** 与 **内核级旁路供电（Slate Mode，插电不给电池充电，从根本上杜绝电池鼓包）**。

通过 macOS 内核遥测守护进程与极轻量原生 Android 客户端，在 USB 直连（带 Wi-Fi 无感自愈双链路）下，构建出毫秒级响应、信息密度饱和、视效硬核的 **桌面领航员（DeskPilot）**。

---

## ✨ 核心功能

<div align="center">
<table>
<tr>
  <td align="center"><b>🖥️ 纯黑 AOD 漂移时钟模式 (98% 像素断电 0 功耗)</b></td>
  <td align="center"><b>🌈 持续全光谱抗衰洗屏保养模式 (单击即退)</b></td>
</tr>
<tr>
  <td><img src="docs/images/aod_preview.png" width="420"/></td>
  <td><img src="docs/images/pixel_refresh_preview.png" width="420"/></td>
</tr>
</table>
</div>

### 1. 💻 Mac 内核全景遥测 (CPU & 内存架构)
- **8 核动态立体均衡器频谱**：以 60FPS 渲染 Mac 8 核心独立负载律动与峰值保持；
- **60s CPU 历史脉冲折线 (Sparkline)**：实时记录整机负荷波动，自适应三色警示；
- **macOS 活动监视器风格多段内存条**：精确剖析 **App 内存（极客蓝）** + **Wired 联动核心（翡翠绿）** + **Compressed 压缩（能量橙）**；
- **macOS Swap 虚拟内存交换区监控**：直接抓取 `vm.swapusage`，洞察虚拟内存高压。

### 2. 🌡️ 硬件真机物理气象微站 (Note 3 传感器实测)
- **博世气压计直读**：实时测量百帕（hPa）大气压强，微积分推算本地物理海拔；
- **盛思锐温湿度直读**：采集桌面环境室温与相对湿度（RH）；
- **环境舒适度智能演算**：自适应计算当前环境体感（`舒适 COMFORT` / `潮湿 HUMID` / `偏热 WARM` 等）。

### 3. 🎙️ 广播级低延迟无线麦克风
- 将 Note 3 物理麦克风通过 UDP 高保真低延迟（<15ms）直推给 Mac 系统的 **BlackHole 2ch 虚拟音频驱动**；
- 手机端带实体声波跳动指示与触控胶囊，Mac 无缝支持系统语音输入法、微信、飞书、会议沟通。

### 4. 🛡️ AMOLED 五重防烧屏与抗衰老体系
- **16 点多轴环形像素漫游**：全界面每 45 秒在 `±5px` 环形矩阵上位移，消除边缘光子烧印；
- **子像素微色温呼吸**：告别刺眼硬白 `#FFFFFF`，基准白字在冷白/冰川蓝/象牙暖白/翡翠微绿间平缓轮换，平衡 R/G/B 子像素衰减；
- **光感自适应调光**：动态识别桌面照度，暗室自动压低屏幕发光负荷 70% 以上；
- **智能 AOD 漂移时钟**：Mac 空闲（CPU<8%）持续 10 分钟或断网超 3 分钟自动进入纯黑 0 功耗 AOD 漂移时钟；
- **持续全光谱抗衰洗屏模式**：长按屏幕 1 秒进入全屏柔和全光谱波流，单击任意位置即刻退出。

### 5. 🔋 电池卫士 (Slate Mode 旁路供电)
- 自动写入三星高通内核供电节点 `/sys/class/power_supply/battery/batt_slate_mode`；
- 插着 USB 线时由充电器直接向主板供电，**停止电池充电（充放电循环清零）**，从根本上消灭长期插电副屏的电池鼓包隐患。

---

## 🏗️ 系统架构

```mermaid
flowchart TD
    subgraph Mac ["💻 macOS 主机"]
        SWIFT_SERVER["stats_server.swift\n(HTTP 9527 + UDP 广播)"]
        AUDIO_BRIDGE["audio_bridge.swift\n(UDP 9528 PCM 接收)"]
        BLACKHOLE["BlackHole 2ch\n(CoreAudio 虚拟麦克风)"]
        SYS_STATS["内核/电池/电源/Swap/网络\n(vm_stat, sysctl, ioreg)"]

        SYS_STATS --> SWIFT_SERVER
        AUDIO_BRIDGE --> BLACKHOLE
    end

    subgraph Comm ["⚡ 双通道通信链路"]
        USB["USB 直连通道 (ADB Reverse Tunnel)\n127.0.0.1:9527 / 9528 (零网络延迟)"]
        WIFI["Wi-Fi 局域网自愈 (LAN UDP Discovery)\n自动广播探测 Mac IP，无感热备"]
    end

    subgraph Note3 ["📱 三星 Galaxy Note 3 HUD"]
        APP["MacHUD.apk\n(原生 Android 60FPS 渲染)"]
        SENSORS["物理传感器阵列\n(博世气压计 / 盛思锐温湿度 / 光感)"]
        BATTERY_BYPASS["内核旁路供电保护\n(batt_slate_mode 杜绝鼓包)"]

        SENSORS --> APP
        BATTERY_BYPASS -.-> APP
    end

    SWIFT_SERVER <==> USB <==> APP
    SWIFT_SERVER <..> WIFI <..> APP
    APP -- "PCM 音频流" --> USB --> AUDIO_BRIDGE
```

---

## 🚀 快速开始

### 1. 环境准备 (Mac 端)
确保已安装 [Homebrew](https://brew.sh) 及基本开发工具：
```bash
# 安装虚拟音频驱动 (用于麦克风功能)
brew install blackhole-2ch

# (可选) 安装 Android 命令行工具 adb
brew install android-platform-tools
```
> **提示**：安装完 BlackHole 后，前往 **macOS 系统设置 -> 声音 -> 输入**，将默认输入设备勾选为 **BlackHole 2ch**。

### 2. 启动 Mac 端遥测与音频服务
克隆本仓库并一键运行守护脚本：
```bash
git clone https://github.com/AllenCoder/desk-pilot.git
cd desk-pilot

# 编译并启动 Mac 服务守护进程
./mac/run_machud.sh
```

### 3. 安装手机端并连接
1. 将 Note 3 或其他 Android 手机通过 USB 连接到 Mac，开启 **开发者选项 -> USB 调试**；
2. 运行安装脚本直接推送预编译 APK 到手机：
```bash
adb install -r android/DeskPilot.apk

# 启动 DeskPilot 主应用
adb shell am start -n com.antigravity.machud/.MainActivity

# (针对 Note 3 等三星机型) 启动旁路供电保护 (杜绝电池鼓包)
./scripts/battery_guard.sh
```

---

## 💡 手势交互指南

| 手势操作 | 响应动作 |
| :--- | :--- |
| **轻触顶部时钟卡片** / **双击屏幕** | 切换 **全功能仪表盘** 与 **纯黑 AOD 漂移时钟** |
| **长按屏幕 1 秒** | 进入 **持续全光谱抗衰洗屏模式** (消除潜在残影) |
| **单击屏幕任意位置** | 退出洗屏保养模式 / 从 AOD 模式瞬间唤醒主界面 |
| **轻触麦克风胶囊** | 手动开启 / 闭麦（支持 Mac 端语音输入法即时联动） |

---

## 📁 目录结构说明

```
desk-pilot/
├── android/               # Android 客户端工程 (无需 Gradle, 秒级极速构建)
│   ├── src/               # 原生 Java 核心类 (传感器、UI波形、音频采集)
│   ├── res/               # 矢量图标、深色卡片背景与布局 XML
│   ├── AndroidManifest.xml
│   ├── DeskPilot.apk      # 开箱即用预编译 APK 安装包
│   └── build_apk.sh       # 极轻量免 Gradle 构建脚本
├── mac/                   # macOS 服务端程序
│   ├── stats_server.swift # 系统内核指标采集 HTTP/UDP 服务
│   ├── audio_bridge.swift # UDP PCM 转 CoreAudio BlackHole 音频网桥
│   ├── build_mac.sh       # 一键编译 Swift 源码
│   ├── run_machud.sh      # 综合保活守护与 ADB 映射守护进程
│   └── com.allencoder.machud.plist # LaunchAgent 登录自启配置
├── scripts/               # 运维与物理保护脚本
│   ├── battery_guard.sh   # Note 3 内核 Slate Mode 旁路供电脚本
│   └── adb_tunnel.sh      # USB ADB 反向端口映射
├── docs/images/           # 高清实机演示与架构截图
├── LICENSE                # MIT 开源协议
└── README.md              # 项目说明文档
```

---

## 🛠️ 自行编译 Android 客户端 (免 Gradle)

本项目特别摒弃了臃肿的 Gradle 依赖链，采用极简高效的纯 SDK 编译流水线：
```bash
# 只需本地配置好 ANDROID_SDK_ROOT 与 OpenJDK
./android/build_apk.sh
```
全程 2 秒内极速生成签名对齐的 `DeskPilot.apk`！

---

## 📄 开源许可证

本项目基于 [MIT 许可证](LICENSE) 开源。欢迎 Star、Fork 与贡献代码！

<div align="center">
<b>Made with ❤️ by <a href="https://github.com/AllenCoder">AllenCoder</a> for Hardware Geeks</b>
</div>

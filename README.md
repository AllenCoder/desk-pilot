<div align="center">

# ⚡ DeskPilot

**为 Mac 而生的终极桌面副驾：全景工控遥测 · 环境气象微站 · 低延迟无线麦克风**
*(Powered by Reborn Flagship AMOLED Hardware)*

[![CI](https://github.com/AllenCoder/desk-pilot/actions/workflows/ci.yml/badge.svg)](https://github.com/AllenCoder/desk-pilot/actions/workflows/ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/AllenCoder/desk-pilot?style=flat-square&color=blue)](https://github.com/AllenCoder/desk-pilot/releases)
[![macOS](https://img.shields.io/badge/macOS-12.0%2B-black?style=flat-square&logo=apple)](https://apple.com)
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

## 🎛️ 贯通式底栏与 5 大触控交互 (Scheme A)

为了充分发挥 Note 3 物理触控屏在桌面支架上的单手操作体验，DeskPilot 引入了 **贯通式全局底部控制坞（Panoramic Dock）** 与深度触控下钻体系：

<div align="center">
<table>
<tr>
  <td align="center"><b>🌦️ 气象微站深度下钻抽屉 (轻触左半区)</b></td>
  <td align="center"><b>🖥️ Mac 核心总线下钻抽屉 (轻触右半区)</b></td>
</tr>
<tr>
  <td><img src="docs/images/drawer_weather.png" width="420"/></td>
  <td><img src="docs/images/drawer_host.png" width="420"/></td>
</tr>
<tr>
  <td align="center"><b>⚙️ 副屏硬件快捷调控抽屉 (长按底栏)</b></td>
  <td align="center"><b>📈 多功能 Widget 跑马灯 (底栏横滑/点击指示点)</b></td>
</tr>
<tr>
  <td><img src="docs/images/drawer_control.png" width="420"/></td>
  <td><img src="docs/images/dock_page2.png" width="420"/></td>
</tr>
</table>
</div>

1. **黄金触控热区**：底栏高度扩充至标准 48dp，左侧（气象微站）与右侧（主机总线）各占 50%，随手一点即刻命中；
2. **轻触下钻与弹层抽屉 (Tap-to-Inspect)**：
   - **轻触气象区**：弹出毛玻璃半透明抽屉，实时查看博世气压计精密百帕数值、物理海拔微积分、温湿度舒适度判定、桌面光照 Lux 与传感器采样率；
   - **轻触主机区**：弹出 Mac 硬件档案，包括 CPU 具体架构代号、核心规格、Turbo 睿频状态、UNIX 1/5/15m 运行负载、活跃线程与系统 Uptime；
3. **边缘横滑多功能跑马灯 (Widget Dock)**：
   - 在底栏区域左右滑动或轻触右上角 `● ○ ○` 指示点，即可平滑切换三大微视图：
     - **视图 1**：环境微站 + 主机总线；
     - **视图 2**：网络吞吐合计 + 磁盘 I/O 读写速；
     - **视图 3**：旁路供电工况 + OLED 护屏状态；
4. **异常阈值动态微动效 (Alert Pulse)**：当传感器实测湿度高于 80% 或极端环境时，水滴/温度计图标自动开启平缓柔和的呼吸律动，警示桌面湿气；
5. **长按唤出副屏硬件级调控 (Quick Controls)**：
   - 长按底栏任意位置唤出副屏控制卡片；
   - **实时亮度滑动条**（10%~100% 随心拉动）；
   - **快捷一键 AOD**、**一键全光谱抗衰洗屏**、**一键夜间红光极低亮模式 (8%)**。

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

## 🌐 换设备与网络多场景连接指南 (零配置 / 自动扫网)

DeskPilot 采用全解耦通信总线架构，针对不同的使用场景提供了 4 层自愈发现策略：

<div align="center">
<img src="docs/images/connection_dialog.png" alt="Mac 主机连接与配对设置面板" width="600"/>
</div>

| 场景 / 模式 | 连接方式 | 是否需要配置 IP | 说明 |
| :--- | :--- | :---: | :--- |
| **场景 A：USB 插线（推荐）** | 物理数据线 | **❌ 零配置** | 只要通过 USB 线插在任何一台 Mac 上，ADB 反向隧道自动走硬件总线 `127.0.0.1`，**换任何 Mac 或手机即插即连，延迟 <1ms**，无需知晓 IP。 |
| **场景 B：同一 Wi-Fi 无线互联** | 局域网 UDP 组播 | **❌ 零配置** | 拔掉数据线后，Mac 端会自动对外发送 UDP 9529 信标广播。手机端捕获后**自动记忆保存新 IP** 并无感切换为无线模式。 |
| **场景 C：复杂路由 (广播受限)** | 局域网全网段并发探测 | **❌ 零配置** | 若办公室/企业路由器开启了 AP 隔离或禁用了 UDP 广播，手机端在断开后会**全自动并发并发盲测整个 Wi-Fi 网段的 9527 端口**，1.5 秒内自动寻机并连接。 |
| **场景 D：跨网段 / 手动指定** | 交互面板手动输入 | **✔️ 仅需一次** | 长按底栏点击 **“🌐 配置 Mac 主机 IP / 自动扫网”**，或者轻触仪表盘的 **“网络通信雷达”** 卡片，直接填入目标 Mac IP 或点击【自动扫描局域网】即可！ |

---

## 💡 手势与触控交互指南

| 手势 / 操作 | 触发区域 | 响应动作 |
| :--- | :--- | :--- |
| **轻触左侧底栏** | 底部 Dock (气象微站) | 唤出 **环境气象下钻抽屉**（温湿度舒适度、博世微气压、海拔、露点评价） |
| **轻触右侧底栏** | 底部 Dock (主机总线) | 唤出 **Mac 硬件规格与调度抽屉**（Intel 核心代号、Turbo 睿频、系统负载、Uptime） |
| **长按底栏 (0.8s)** | 底部 Dock 任意区域 | 唤出 **副屏硬件控制面板**（屏幕亮度无级滑块、快捷 AOD、全光谱洗屏、夜间红光模式） |
| **左右滑动手势** / **轻触指示点** | 底部 Dock (`● ○ ○`) | 平滑切换底栏 Widget（微站/主机 ⇄ 吞吐/磁盘 ⇄ 旁路供电/护屏工况） |
| **轻触顶部时钟卡片** / **双击屏幕** | 顶部时钟 / 屏幕中央 | 切换 **全功能仪表盘** 与 **纯黑 AOD 漂移时钟** |
| **长按屏幕中央 1 秒** | 屏幕中央区域 | 进入 **持续全光谱抗衰洗屏模式** (消除潜在残影) |
| **单击屏幕任意位置** | 全屏覆盖 | 退出洗屏保养模式 / 退出抽屉卡片 / 从 AOD 模式瞬间唤醒主界面 |
| **轻触麦克风胶囊** | 麦克风图标胶囊 | 手动开启 / 闭麦（支持 Mac 端语音输入法即时联动） |

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

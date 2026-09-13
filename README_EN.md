<div align="center">

# ⚡ DeskPilot

**Your Ultimate Mac Desktop Co-Pilot · Ambient Weather Station · Low-Latency Wireless Microphone**
*(Powered by Reborn Flagship AMOLED Hardware)*

[![CI](https://github.com/AllenCoder/desk-pilot/actions/workflows/ci.yml/badge.svg)](https://github.com/AllenCoder/desk-pilot/actions/workflows/ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/AllenCoder/desk-pilot?style=flat-square&color=blue)](https://github.com/AllenCoder/desk-pilot/releases)
[![macOS](https://img.shields.io/badge/macOS-12.0%2B-black?style=flat-square&logo=apple)](https://apple.com)
[![Android](https://img.shields.io/badge/Android-5.0%2B-green?style=flat-square&logo=android)](https://android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Swift](https://img.shields.io/badge/Swift-5.0%2B-orange?style=flat-square&logo=swift)](https://swift.org)
[![AMOLED Care](https://img.shields.io/badge/AMOLED-Burn--in%20Protected-purple?style=flat-square)](https://github.com/AllenCoder/desk-pilot)

[简体中文说明](README.md) · [Features](#-key-features) · [Quick Start](#-quick-start) · [AMOLED Care](#-amoled-burn-in-protection) · [Architecture](#-architecture)

<br/>

<img src="docs/images/hud_preview.png" alt="DeskPilot Dashboard Interface" width="880"/>

</div>

---

## 📖 Concept

**DeskPilot** is engineered as the ultimate secondary command station for macOS workstations.

Rather than letting vintage flagship devices like the legendary Samsung Galaxy Note 3 gather dust, we tap into their incredible built-in hardware gifts that surpass many modern USB displays:
- **5.7-inch 1080P Super AMOLED display** (pitch black pixels completely power down, rich contrast);
- **Built-in Bosch industrial barometer** (`Sensor.TYPE_PRESSURE`, real-time atmospheric pressure and altitude);
- **Built-in Sensirion SHTC1 temperature & relative humidity sensor** (`Sensor.TYPE_RELATIVE_HUMIDITY` & `AMBIENT_TEMP`);
- **Maxim MAX88921 ambient light sensor**;
- **Dual noise-canceling physical microphones** and **Kernel-level battery bypass (Slate Mode, power directly from USB without charging battery to prevent swelling)**.

**DeskPilot** brings this hardware back to life: backed by a lightweight macOS kernel telemetry daemon and a smooth 60FPS native Android app, operating over USB direct connection with Wi-Fi failover.

---

## ✨ Key Features

<div align="center">
<table>
<tr>
  <td align="center"><b>🖥️ Pure Black AOD Drift Clock (98% Pixels Off, 0 Power)</b></td>
  <td align="center"><b>🌈 Continuous Spectrum Wash Mode (Tap to Exit)</b></td>
</tr>
<tr>
  <td><img src="docs/images/aod_preview.png" width="420"/></td>
  <td><img src="docs/images/pixel_refresh_preview.png" width="420"/></td>
</tr>
</table>
</div>

### 1. 💻 macOS Kernel Telemetry
- **8-Core Dynamic Audio Equalizer Spectrum**: 60FPS per-core utilization and peak tracking;
- **60s CPU Sparkline Trend**: Live load pulse history with dynamic alert colors;
- **macOS Activity Monitor Multi-Segment Memory**: App (Cyan) + Wired (Emerald) + Compressed (Amber);
- **macOS Swap Monitoring**: Kernel `vm.swapusage` monitoring for paging pressure.

### 2. 🌡️ Physical Desktop Weather Station (Genuine Note 3 Sensors)
- **Bosch Barometer**: Atmospheric pressure in hPa and calculated physical altitude;
- **Sensirion Temp & Humidity**: Room temperature and relative humidity (% RH);
- **Adaptive Comfort Index**: Real-time comfort rating badge.

### 3. 🎙️ Wireless Low-Latency Microphone
- Note 3 dual mic streaming 16-bit 48kHz PCM over UDP (<15ms latency) directly into macOS via **BlackHole 2ch**;
- Seamless support for Mac voice dictation, Siri, Zoom, Lark, and Teams.

### 4. 🛡️ 5-Layer AMOLED Burn-in Protection
- **16-Point Multi-Axis Orbiting**: Entire interface shifts within a `±5px` orbit every 45 seconds;
- **Subpixel Chroma Cycling**: Rotating primary text colors across 4 tints every 3 minutes to balance RGB subpixel fatigue;
- **Ambient Light Sensor Auto-Dimming**: Automatically lowers OLED drive current by >70% in dark environments;
- **Smart AOD Drift Saver**: Automatically transitions into a pure black floating clock when Mac is idle (>10m);
- **Continuous Spectrum Pixel Refresh**: Long-press to activate full-spectrum moving wave, single tap to exit.

### 5. 🔋 Battery Guard (Slate Mode Bypass Charging)
- Sets `/sys/class/power_supply/battery/batt_slate_mode` to power the device directly from the charger, eliminating battery charge cycles and swelling risk.

---

## 🎛️ Panoramic Bottom Dock & 5 Touch Interactions (Scheme A)

To take full advantage of Note 3's high-responsiveness touch display on a desktop stand, DeskPilot features a **Panoramic Bottom Dock** with drill-down touch inspectors:

<div align="center">
<table>
<tr>
  <td align="center"><b>🌦️ Weather Station Drill-Down Drawer (Tap Left Dock)</b></td>
  <td align="center"><b>🖥️ Mac Core Specs & Telemetry Drawer (Tap Right Dock)</b></td>
</tr>
<tr>
  <td><img src="docs/images/drawer_weather.png" width="420"/></td>
  <td><img src="docs/images/drawer_host.png" width="420"/></td>
</tr>
<tr>
  <td align="center"><b>⚙️ Display Quick Controls (Long-Press Dock)</b></td>
  <td align="center"><b>📈 Multi-Widget Dock Carousel (Swipe / Tap Dots)</b></td>
</tr>
<tr>
  <td><img src="docs/images/drawer_control.png" width="420"/></td>
  <td><img src="docs/images/dock_page2.png" width="420"/></td>
</tr>
</table>
</div>

1. **Ergonomic Golden Touch Zones**: Full-height 48dp bottom dock split 50/50 for Weather Station (left) and Host Bus (right), perfect for natural single-finger desktop tapping;
2. **Tap-to-Inspect Overlay Drawers**:
   - **Tap Weather Area**: Semi-transparent frosted glass drawer displaying exact Bosch barometer pressure in hPa, barometric altitude calculation, Sensirion humidity & comfort ratings, ambient light in Lux, and sensor polling rate;
   - **Tap Host Area**: Deep Mac hardware spec sheet featuring CPU model, core topologies, Turbo state, UNIX load averages (1/5/15m), thread count, and system uptime;
3. **Swipeable Widget Dock (Carousel)**:
   - Swipe left/right or tap the `● ○ ○` indicator dots to cycle through 3 widget views:
     - **View 1**: Ambient Weather + Host Bus;
     - **View 2**: Network Aggregate Traffic + Disk I/O;
     - **View 3**: Battery Bypass (Slate Mode) + OLED Care Health;
4. **Dynamic Threshold Alert Pulse**: Ambient icons gently pulse with smooth breathing animations whenever humidity exceeds 80% or sensor anomalies are detected;
5. **Secondary Display Quick Controls**:
   - Long-press any area of the bottom dock to invoke the quick control panel;
   - **Live Brightness Slider** (10% - 100% fine tuning);
   - **Instant AOD Mode**, **Full-Spectrum OLED Wash**, and **Night Low-Light Mode (8%)**.

---

## 🚀 Quick Start

### 1. Requirements (Mac Host)
```bash
# Install virtual audio driver for microphone support
brew install blackhole-2ch

# (Optional) Install Android adb
brew install android-platform-tools
```
> Select **BlackHole 2ch** as default input in **System Settings -> Sound -> Input**.

### 2. Start Mac Daemon
```bash
git clone https://github.com/AllenCoder/desk-pilot.git
cd desk-pilot

./mac/run_machud.sh
```

### 3. Install on Android Device
Connect your Note 3 or Android device via USB with **USB Debugging** enabled:
```bash
adb install -r android/DeskPilot.apk
adb shell am start -n com.antigravity.machud/.MainActivity

# Activate battery bypass protection
./scripts/battery_guard.sh
```

---

## 💡 Touch & Gesture Guide

| Gesture / Trigger | Hit Area | Action |
| :--- | :--- | :--- |
| **Tap Left Dock** | Bottom Dock (Weather) | Open **Weather Station Inspector** (hPa, altitude, comfort index, light) |
| **Tap Right Dock** | Bottom Dock (Host) | Open **Mac Hardware & Telemetry Inspector** (CPU model, load, uptime) |
| **Long-Press Dock (0.8s)** | Bottom Dock (Anywhere) | Open **Display Quick Controls** (Brightness slider, AOD, wash, night mode) |
| **Horizontal Swipe / Tap Dots** | Bottom Dock (`● ○ ○`) | Cycle Dock Widgets (Weather/Host ⇄ Network/Disk ⇄ Bypass/OLED) |
| **Tap Clock Card / Double Tap** | Clock / Center Screen | Toggle **Full HUD** and **Pure Black AOD Clock** |
| **Long Press Center (1s)** | Center Screen | Enter **Continuous Spectrum Wash Mode** (Burn-in recovery) |
| **Single Tap Anywhere** | Full Screen | Exit Wash Mode / Dismiss Drawers / Wake Up from AOD |
| **Tap Mic Capsule** | Mic Capsule | Toggle microphone audio streaming |

---

## 📄 License

Distributed under the [MIT License](LICENSE).

<div align="center">
<b>Made with ❤️ by <a href="https://github.com/AllenCoder">AllenCoder</a> for Hardware Geeks</b>
</div>

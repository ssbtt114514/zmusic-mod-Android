<div align="center">

# ZMusic Android Mod

![][version]
![][java]
![][license]
![][platforms]

**基于 [starhui-dev/zmusic-mod](https://github.com/starhui-dev/zmusic-mod) 的 Android 适配分支**
**An Android adaptation fork of [starhui-dev/zmusic-mod](https://github.com/starhui-dev/zmusic-mod)**

为 Minecraft Android 启动器（FCL、PojavLauncher 等）提供纯 Java 音频播放能力，无需原生 so 库即可在 Android 上正常播放音乐。

Provides pure-Java audio playback for Minecraft Android launchers (FCL, PojavLauncher, etc.) — works on Android without any native `.so` library.

</div>

## 与原版 ZMusic 的区别 / Differences from Original ZMusic

| 特性 / Feature | 原版 ZMusic / Original | 本分支（Android Mod） / This Fork |
|------|------------|----------------------|
| 播放后端 / Playback backend | JNI native（libzmusic.so） | 纯 Java / Pure Java（JLayer + OpenAL） |
| Android 支持 / Android support | 依赖 native 库，需交叉编译 / Requires native lib, cross-compile needed | 开箱即用，jar 自包含 / Out of the box, self-contained jar |
| MP3 解码 / MP3 decoder | miniaudio（C 库 / C library） | JLayer（纯 Java / Pure Java） |
| 音频输出 / Audio output | miniaudio OpenSL ES | LWJGL OpenAL（MC 自带 / bundled with MC） |
| 平台检测 / Platform detection | 无 / None | Dalvik/ART/FCL 启动器自动识别 / Auto-detect Dalvik/ART/FCL |
| Android MediaPlayer | 无 / None | 反射调用，原生支持 HTTP 流 / Reflective call, native HTTP streaming |
| 日志输出 / Logging | 基础 / Basic | 全链路详细日志 / End-to-end detailed logs |
| jar 体积 / jar size | 含 so 约 2-18MB / ~2-18MB with so | 约 200KB（无 so）/ ~200KB (no so) |

## 支持版本 / Supported Versions

全版本覆盖（1.12.2 ~ 26.2），共 29 个构建目标：

Full version coverage (1.12.2 ~ 26.2), 29 build targets in total:

### Forge（10 个版本 / 10 versions）
`1.12.2` `1.14.4` `1.15.2` `1.16.5` `1.17.1` `1.18.2` `1.19.2` `1.19.4` `1.20.1` `1.20.4`

### Fabric（14 个版本 / 14 versions）
`1.14.4` `1.15.2` `1.16.5` `1.17.1` `1.18.2` `1.19.2` `1.19.4` `1.20.1` `1.20.4` `1.20.6` `1.21.5` `1.21.11` `26.1.2` `26.2`

### NeoForge（5 个版本 / 5 versions）
`1.20.4` `1.20.6` `1.21.1` `26.1.2` `26.2`

## 下载 / Download

前往 [Releases](../../releases/latest) 下载对应版本的 jar 文件，放入 Minecraft 的 `mods` 文件夹即可。

Go to [Releases](../../releases/latest), download the jar for your version, and drop it into Minecraft's `mods` folder.

## 工作原理 / How It Works

### 播放流程 / Playback Flow

```
服务器插件 / Server plugin ──[网络包 / packet]──> ClientEvent.onPacket()
                              │
                              ▼
                        PacketEvent.onPlay(url)
                              │
                              ▼
                        ZMusicPlayer.playAsync(url)
                              │
                              ▼
                        后端选择 / Backend selection (createBackend)
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
            AndroidMediaPlayer       JLayerBackend
            （Dalvik/ART）          （桌面/FCL / Desktop/FCL）
                    │                   │
                    ▼                   ▼
              MediaPlayer          HTTP 下载 MP3 / Download MP3
              原生 HTTP 流 /        JLayer 解码 / Decode
              Native HTTP stream   OpenAL 播放 / Play
                    │                   │
                    ▼                   ▼
              系统音频输出 /          OpenAL 音频输出 /
              System audio out       OpenAL audio out
```

### Android 平台检测 / Android Platform Detection

启动时自动检测运行环境，按优先级尝试：

At startup the runtime environment is auto-detected, in priority order:

1. **VM 名称 / VM name**：`Dalvik` 或 `ART` 开头 → Android 原生 / Native Android
2. **类加载 / Class lookup**：`android.os.Build` 可加载 → Android 原生 / Native Android
3. **临时目录 / Temp dir**：`/storage/emulated/` 或 `/data/data/` → FCL 启动器 / FCL launcher
4. **用户目录 / User dir**：`user.dir`/`user.home` 含 `FCL` → FCL 启动器 / FCL launcher

检测到 Android 原生环境使用 `MediaPlayer` 后端，否则使用 `JLayer + OpenAL` 后端。

Native Android environments use the `MediaPlayer` backend; otherwise the `JLayer + OpenAL` backend is used.

### 后端实现 / Backend Implementations

- **AndroidMediaPlayerBackend**：通过反射调用 `android.media.MediaPlayer`，原生支持 HTTP MP3 流式播放，无需额外解码库
  - Calls `android.media.MediaPlayer` via reflection; natively supports HTTP MP3 streaming, no extra decoder needed.
- **JLayerBackend**：使用 JLayer 纯 Java MP3 解码器 + LWJGL OpenAL 双缓冲流式播放，适用于桌面 JVM 和 FCL 启动器
  - Uses JLayer (pure-Java MP3 decoder) + LWJGL OpenAL double-buffered streaming; works on desktop JVM and FCL launcher.

## 开发 / Development

### 环境要求 / Requirements

- JDK 8/16/17/21/25（多版本构建需要，单一版本构建只需对应 JDK / multi-version builds need all; single-version builds only need the matching JDK）
- Gradle 9.5+（wrapper 已包含 / wrapper included）

### 构建指定版本 / Build a Specific Version

```shell
git clone https://github.com/ssbtt114514/zmusic-mod-Android
cd zmusic-mod-Android

# 构建 Fabric 1.20.1 / Build Fabric 1.20.1
./gradlew --project-dir builds/fabric -Pzmusic.project=zmusic-fabric-1.20.1 build

# 构建 Forge 1.20.1 / Build Forge 1.20.1
./gradlew --project-dir builds/forge -Pzmusic.project=zmusic-forge-1.20.1 build

# 构建 NeoForge 1.21.1 / Build NeoForge 1.21.1
./gradlew --project-dir builds/neoforge -Pzmusic.project=zmusic-neoforge-1.21.1 build
```

### 构建全部版本 / Build All Versions

```shell
# 需要 mise 管理多版本 JDK / Requires mise to manage multiple JDKs
mise install
./gradlew --project-dir builds/fabric build -Porg.gradle.java.installations.paths="$(mise where java@temurin-8),$(mise where java@temurin-16),$(mise where java@temurin-17),$(mise where java@temurin-21),$(mise where java@temurin-25)"
./gradlew --project-dir builds/forge build -Porg.gradle.java.installations.paths="$(mise where java@temurin-8),$(mise where java@temurin-16),$(mise where java@temurin-17),$(mise where java@temurin-21),$(mise where java@temurin-25)"
./gradlew --project-dir builds/neoforge build -Porg.gradle.java.installations.paths="$(mise where java@temurin-8),$(mise where java@temurin-16),$(mise where java@temurin-17),$(mise where java@temurin-21),$(mise where java@temurin-25)"
```

构建产物位于各子项目的 `build/libs/` 目录下。

Build artifacts are located in each subproject's `build/libs/` directory.

## 调试 / Debugging

模组输出详细日志到 Minecraft 的 `logs/latest.log`，所有日志带 `ZMusic` 前缀，便于定位问题：

The mod writes detailed logs to Minecraft's `logs/latest.log`, all prefixed with `ZMusic` for easy troubleshooting:

- **平台检测 / Platform detection**：VM 名称、tmpdir、user.dir、android.os.Build 检测结果 / VM name, tmpdir, user.dir, android.os.Build lookup result
- **网络包 / Packet**：收到消息的原始内容、URL 解析、线程名 / raw message content, URL parsing, thread name
- **HTTP 连接 / HTTP connection**：响应码、Content-Type/Length、连接耗时 / status code, Content-Type/Length, connection latency
- **解码播放 / Decode & playback**：MP3 采样率/通道数、每帧 PCM 样本数、OpenAL 状态 / MP3 sample rate/channels, per-frame PCM sample count, OpenAL state
- **状态回调 / State callbacks**：状态变化（含可读名称）、错误、播放完成 / state changes (with readable names), errors, playback completion

Android 原生环境也可通过 `adb logcat -s ZMusic` 查看 native 层日志。

On native Android you can also view native-layer logs via `adb logcat -s ZMusic`.

## 反馈 / Feedback

* 提交 / Submit [Issues](../../issues)

## 开源协议 / License

本项目使用 [GPL-3.0](LICENSE) 协议开放源代码

This project is open-sourced under the [GPL-3.0](LICENSE) license.

```text
ZMusic
Copyright (C) 2023 RealHeart
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```

## 鸣谢 / Acknowledgements

* [starhui-dev/zmusic-mod](https://github.com/starhui-dev/zmusic-mod) - 原版 ZMusic 模组 / Original ZMusic mod
* [JetBrains](https://www.jetbrains.com/zh-cn/)
* [FabricMC](https://fabricmc.net/)
* [JLayer](https://www.javazoom.net/javalayer/javalayer.html) - 纯 Java MP3 解码器 / Pure-Java MP3 decoder
* [LWJGL](https://www.lwjgl.org/) - OpenAL 绑定 / OpenAL bindings
* [AllMusic Mod](https://github.com/Coloryr/AllMusic_M)

[version]: https://img.shields.io/badge/version-3.7.1-blue?style=for-the-badge

[java]: https://img.shields.io/badge/java-8%2B-blue?style=for-the-badge

[license]: https://img.shields.io/github/license/ssbtt114514/zmusic-mod-Android?style=for-the-badge

[platforms]: https://img.shields.io/badge/platforms-Forge%20%7C%20Fabric%20%7C%20NeoForge-brightgreen?style=for-the-badge

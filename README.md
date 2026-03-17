# RayClaw

**RayClaw** 是一款专为 [RayNeo X3](https://www.rayneo.com/) AR 眼镜设计的智能语音 AI 助手。它将实时语音识别、流式大模型对话、多语言翻译集成在一起，全程通过镜腿触控手势驱动，让双手彻底解放。

本仓库同时附带一份完整的 **[RayNeo X3 AR 应用开发指引](rayneo-x3-ar-dev-guide/README.md)**（25 篇文章），供开发者搭建自己的 AR 应用参考。

---

## 目录

- [功能特性](#功能特性)
- [手势操作](#手势操作)
- [技术架构](#技术架构)
- [环境要求](#环境要求)
- [OpenClaw 智能体网关部署](#openclaw-智能体网关部署)
- [快速开始](#快速开始)
- [从应用商店安装后配置 API Key](#从应用商店安装后配置-api-key)
- [配置详解](#配置详解)
- [项目结构](#项目结构)
- [开发指引](#开发指引rayneo-x3-ar-dev-guide)
- [License](#license)

---

## 功能特性

| 功能 | 说明 |
|------|------|
| **实时语音识别** | 接入阿里云 DashScope Paraformer，WebSocket 流式推送，延迟极低 |
| **流式 AI 对话** | 通过 OpenClaw 智能体网关（SSE 协议）与 LLM 实时交互，逐字渲染 |
| **Markdown 渲染** | AI 回复支持标题、加粗、代码块、表格等完整 Markdown 格式 |
| **多语言 ASR** | 支持中文、英语、日语、韩语、法语、德语、西班牙语、俄语 |
| **多翻译引擎** | 内置 6 种提供商：MyMemory / 百度 / 有道 / 腾讯 / DeepL / Azure |
| **双眼同步渲染** | 继承 Mercury SDK `BaseMirrorActivity`，左右眼画面完全同步 |
| **全手势操控** | 7 种镜腿手势覆盖全部交互，无需手机 |
| **运行时配置** | 无需重编译，通过 `adb push` 覆盖 API Key 和服务器地址 |

---

## 手势操作

| 手势 | 动作 |
|------|------|
| **单击** | 开始 / 暂停语音监听 |
| **双击** | 退出应用 |
| **三击** | 重置当前对话（同步清除服务端上下文） |
| **向前滑动** | 切换到下一识别语言 |
| **向后滑动** | 切换到上一识别语言 |
| **向上滑动** | AI 回复区域向上滚动 |
| **向下滑动** | AI 回复区域向下滚动 |

---

## 技术架构

```
com.rayclaw.app
│
├── AgentChatActivity          主界面：继承 BaseMirrorActivity，管理手势 + 状态机
├── MyApplication              Application 入口：初始化 MercurySDK 和 RuntimeConfig
├── RuntimeConfig              运行时配置系统：读取 rayclaw.conf，优先级高于 BuildConfig
│
├── agent/
│   ├── AgentConfig            OpenClaw 网关参数（运行时 > BuildConfig）
│   └── OpenClawClient         HTTP SSE 流式客户端（OkHttp + Kotlin Coroutines）
│
├── asr/
│   ├── SpeechEngine           DashScope WebSocket 实时语音识别引擎
│   ├── AsrConfig              ASR 参数：采样率 16kHz、帧大小 3200B、模型选择
│   └── AppLanguage            支持语言枚举（代码、显示名、Locale）
│
├── translation/
│   ├── TranslationProvider    翻译提供商接口
│   ├── TranslationManager     工厂（策略模式）：按配置选择提供商
│   ├── TranslationConfig      各提供商 Key 集中配置
│   └── providers/             MyMemory · 百度 · 有道 · 腾讯 · DeepL · Azure
│
└── ui/
    └── MarkdownRenderer       Markdown → HTML 转换（commonmark-java，防 XSS）
```

### 关键设计决策

| 设计 | 说明 |
|------|------|
| **Generation 计数器** | `AgentChatActivity` 中每次重置对话都递增 `generation`，所有流式回调捕获启动时的值，过期回调静默丢弃，彻底避免 race condition |
| **渲染节流** | Markdown 渲染有 120ms 节流（`STREAM_RENDER_THROTTLE_MS`），避免频繁 WebView load 阻塞 UI |
| **密钥分级** | `rayclaw.conf`（运行时）> `local.properties`（编译时 BuildConfig）> 代码默认值；日志只记录 key 名，不记录值 |
| **HTML 转义** | `MarkdownRenderer` 在 commonmark 解析前 sanitize 输入，防止恶意 Markdown 注入 XSS |

---

## 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Meerkat（2024.3.1）以上 |
| JDK | 17（Android Studio 内置 JBR） |
| Gradle | 9.3.1（Wrapper 自动下载） |
| compileSdk | 36（Android 16） |
| minSdk | 26（Android 8.0） |
| 目标硬件 | RayNeo X3 眼镜（或其他基于 Mercury SDK 的兼容设备） |

**外部服务**

| 服务 | 用途 | 申请地址 |
|------|------|----------|
| 阿里云 DashScope | Paraformer 实时语音识别 | https://dashscope.console.aliyun.com/ |
| OpenClaw 智能体网关 | LLM 流式对话 | 本地部署（见下方） |

---

## OpenClaw 智能体网关部署

RayClaw 依赖 OpenClaw 智能体网关提供 LLM 流式对话能力。本部分指引如何在本地或服务器部署 OpenClaw。

### 前置要求

- **Node.js** 16.0 或更高版本
- **npm** 8.0 或更高版本
- 能连接到互联网（下载 OpenClaw 包）

### 安装 OpenClaw

#### 1. 卸载旧版本（如有）

```bash
npm uninstall -g openclaw
```

#### 2. 安装指定版本

由于最新版本在某些模型的 function call 上存在问题，建议安装以下稳定版本：

```bash
npm install -g openclaw@2026.3.2
```

#### 3. 初始化配置

运行交互式引导程序，同时注册 OpenClaw 为后台服务：

```bash
openclaw onboard --install-daemon
```

### 配置注意事项

在初始化过程中，**请注意以下选项不能使用默认值**：

| 配置项 | 推荐值 | 说明 |
|------|--------|------|
| **Gateway bind** | `LAN` 或 `tailnet` | 选择 LAN 模式支持局域网访问，选择 tailnet 需额外配置 Tailscale |
| **Gateway auth** | `Token` | 使用 Token 认证保护 API 安全 |
| **Gateway token** | 自生成强密钥 | 建议使用随机数生成器：`openssl rand -hex 32` |

其他配置项（如模型、API 提供商等）根据个人需求选择。

### 修改配置文件

部署完成后，需要修改配置文件以支持 HTTP 端点：

**配置文件位置：**
```
~/.openclaw/openclaw.json
```

**修改步骤：**

1. 打开上述配置文件
2. 定位到 `gateway.bind` 配置项，确保设置为 `lan` 或 `tailnet`
3. 在 `tailscale` 或相关部分下方，新增以下 HTTP 端点配置：

```json
"http": {
  "endpoints": {
    "chatCompletions": { "enabled": true },
    "responses": { "enabled": true }
  }
}
```

### 验证部署

完成后，启动或重启 OpenClaw 服务：

```bash
# 重启守护进程
openclaw restart

# 查看服务状态
openclaw status
```

在眼镜应用中配置 OpenClaw 的网关地址（如 `http://192.168.1.x:port`）和 Token，即可连接使用。

---

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/lunanightshade-z/rayclaw.git
cd rayclaw
```

### 2. 配置 API Keys

```bash
# 复制模板
cp local.properties.template local.properties
```

编辑 `local.properties`（此文件已在 `.gitignore` 中，不会被提交）：

```properties
# Android SDK 路径（根据本机修改）
sdk.dir=/Users/yourname/Library/Android/sdk      # macOS
# sdk.dir=C:\Users\yourname\AppData\Local\Android\Sdk  # Windows

# 阿里云 DashScope — 语音识别
DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# OpenClaw 智能体网关
OPENCLAW_BASE_URL=https://your-openclaw-gateway.example.com
OPENCLAW_GATEWAY_TOKEN=your_gateway_token_here
OPENCLAW_AGENT_ID=main
```

### 3. 构建并安装到眼镜

**方式 A：构建脚本（Windows PowerShell）**

```powershell
.\build_and_install.ps1
```

脚本自动定位 `adb`（优先读取 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 环境变量，回退到 PATH），不含任何硬编码路径。

**方式 B：手动命令**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. 运行时配置（可选，无需重编译）

如果你想在不重新构建的情况下更换 API Key 或切换服务器，使用运行时配置：

```bash
# 1. 基于模板创建配置文件
cp rayclaw.conf.template rayclaw.conf

# 2. 填入你的参数，然后推送到眼镜
adb push rayclaw.conf /sdcard/Android/data/com.rayclaw.app/files/rayclaw.conf

# 3. 重启应用即生效（无需重新安装）
adb shell am force-stop com.rayclaw.app
adb shell am start -n com.rayclaw.app/.AgentChatActivity
```

`rayclaw.conf` 已在 `.gitignore` 中，不会入库。

### 5. 连接并使用

- 确保眼镜与手机/电脑处于同一 Wi-Fi，或通过 USB 连接
- 在眼镜 Launcher 中找到 **rayclaw** 应用并打开
- **单击镜腿** 开始说话，AI 回复将实时渲染在眼前

---

## 从应用商店安装后配置 API Key

从应用商店下载安装后，APK 中没有内置任何 API Key，需要通过 USB 将配置文件推送到眼镜才能使用。整个过程无需任何开发经验，只需安装一个命令行工具 ADB。

### 第一步：在眼镜上开启开发者模式

RayNeo X3 Pro 的开发者模式开启方式与普通 Android 手机不同：

1. 在眼镜上打开 **设置（Settings）**
2. 在设置界面中，**用右镜腿触控板向左滑动 10 次**（即所谓的"撞墙 10 次"）
3. 完成后，ADB 开发者模式即被激活

> **提示：** 再次执行相同操作（向左滑动 10 次）会关闭开发者模式。请确保使用的是 USB-C **数据线**而非纯充电线，随眼镜附带的线缆即可。

### 第二步：在电脑上安装 ADB

ADB（Android Debug Bridge）是 Android 官方工具，**无需安装完整 Android Studio**。

<details>
<summary><b>macOS</b></summary>

```bash
# 使用 Homebrew 安装（推荐）
brew install android-platform-tools
```

或从 [Android 官方下载页](https://developer.android.com/tools/releases/platform-tools)下载 Platform Tools 压缩包，解压后将目录加入 `PATH`。

</details>

<details>
<summary><b>Windows</b></summary>

从 [Android 官方下载页](https://developer.android.com/tools/releases/platform-tools)下载 **Windows 版 Platform Tools** 压缩包，解压到任意目录（如 `C:\platform-tools`）。

**使用方法：** 解压后在该目录中打开 PowerShell，直接运行 `.\adb` 命令；或将该目录添加到系统环境变量 `PATH` 后全局使用 `adb`。

</details>

<details>
<summary><b>Linux</b></summary>

```bash
# Ubuntu / Debian
sudo apt install android-tools-adb

# Fedora / RHEL
sudo dnf install android-tools
```

</details>

### 第三步：连接眼镜

使用 USB-C 数据线将眼镜连接到电脑，然后验证连接：

```bash
adb devices
```

输出中应出现眼镜的序列号（状态为 `device`）：

```
List of devices attached
XXXXXXXXXXXXXXXX    device
```

<details>
<summary><b>⚠️ 连接故障排查</b></summary>

| 问题 | 解决方案 |
|------|----------|
| 列表为空（`device not found`） | 确认使用的是数据线而非充电线；检查眼镜开发者模式是否已开启；尝试重启眼镜 |
| 状态显示 `unauthorized` | 检查眼镜屏幕上是否有待确认的"允许 USB 调试"弹窗 |
| **Windows 11 无法识别设备** | 已知驱动兼容问题——需使用 [Zadig](https://zadig.akeo.ie/) 工具为眼镜安装 WinUSB 驱动：打开 Zadig → 选择 RayNeo 设备 → 安装 WinUSB 驱动 → 重试 `adb devices` |

</details>

### 第四步：创建配置文件

在电脑上新建一个名为 `rayclaw.conf` 的文本文件，填入以下内容：

```properties
# 阿里云 DashScope — 语音识别 API Key
DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# OpenClaw 智能体网关
OPENCLAW_BASE_URL=http://192.168.1.x:18789
OPENCLAW_GATEWAY_TOKEN=your_gateway_token_here
OPENCLAW_AGENT_ID=main
```

将各字段替换为你的实际值：

| 字段 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope 控制台获取 |
| `OPENCLAW_BASE_URL` | OpenClaw 网关所在机器的局域网 IP 和端口 |
| `OPENCLAW_GATEWAY_TOKEN` | 部署 OpenClaw 时设置的 Gateway Token |
| `OPENCLAW_AGENT_ID` | 保持默认值 `main` 即可 |

> **提示：** 眼镜和 OpenClaw 服务器须处于同一局域网。你可以在服务器上运行 `ifconfig`（macOS/Linux）或 `ipconfig`（Windows）查询局域网 IP。

### 第五步：推送配置并重启应用

```bash
# 推送配置文件到眼镜
adb push rayclaw.conf /sdcard/Android/data/com.rayclaw.app/files/rayclaw.conf

# 重启应用使配置生效
adb shell am force-stop com.rayclaw.app
adb shell am start -n com.rayclaw.app/.AgentChatActivity
```

应用重启后即加载新配置。日志只记录已加载的 Key 名称，不记录值，密钥不会泄露到 logcat。

### 更新配置

如需更换 API Key 或切换服务器，直接修改本地的 `rayclaw.conf` 文件，重新执行第五步中的三条命令即可，无需重新安装应用。

---

## 配置详解

### local.properties（编译时注入）

| Key | 说明 | 必填 |
|-----|------|------|
| `sdk.dir` | Android SDK 本地路径 | ✅ |
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key | ✅ |
| `OPENCLAW_BASE_URL` | OpenClaw 网关基础地址 | ✅ |
| `OPENCLAW_GATEWAY_TOKEN` | 网关鉴权 Token | ✅ |
| `OPENCLAW_AGENT_ID` | Agent ID（默认 `main`） | 可选 |

这些值通过 `BuildConfig` 字段注入，编译时固化在 APK 中。

### rayclaw.conf（运行时覆盖）

格式与 `local.properties` 相同的 Java Properties 文件，支持的 Key 与上表一致。运行时优先级高于 BuildConfig，适合频繁切换测试环境，无需重新编译。

```properties
# rayclaw.conf 示例（adb push 到设备后生效）
OPENCLAW_BASE_URL=http://192.168.1.100:18789
OPENCLAW_GATEWAY_TOKEN=new_token
DASHSCOPE_API_KEY=sk-new_key
```

日志安全：`RuntimeConfig` 只记录已加载的 key 名称，**不记录 key 值**，防止 logcat 泄露密钥。

---

## 项目结构

```
rayclaw/
├── app/
│   ├── build.gradle.kts                    构建配置：API Key 注入、依赖声明
│   ├── libs/
│   │   └── MercuryAndroidSDK-*.aar         RayNeo Mercury SDK（本地依赖）
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   └── markdown.css            AI 回复 WebView 样式
│           ├── java/com/rayclaw/app/
│           │   ├── AgentChatActivity.kt    主 Activity（手势 + UI 状态机）
│           │   ├── MyApplication.kt        Application：SDK 初始化
│           │   ├── RuntimeConfig.kt        运行时配置覆盖系统
│           │   ├── agent/
│           │   │   ├── AgentConfig.kt      OpenClaw 网关参数
│           │   │   └── OpenClawClient.kt   SSE 流式客户端
│           │   ├── asr/
│           │   │   ├── AppLanguage.kt      语言枚举（8 种）
│           │   │   ├── AsrConfig.kt        ASR 参数配置
│           │   │   └── SpeechEngine.kt     WebSocket 语音引擎
│           │   ├── translation/
│           │   │   ├── TranslationConfig.kt    各翻译商 Key 配置
│           │   │   ├── TranslationManager.kt   工厂选择器
│           │   │   ├── TranslationProvider.kt  接口定义
│           │   │   └── providers/              6 种翻译实现
│           │   └── ui/
│           │       └── MarkdownRenderer.kt     Markdown → HTML
│           └── res/
│               ├── layout/
│               │   └── activity_agent_chat.xml 主界面布局
│               └── values/
│                   ├── strings.xml
│                   ├── colors.xml
│                   └── themes.xml              Theme.RayClaw
│
├── rayneo-x3-ar-dev-guide/                 AR 开发完整指引（见下方）
├── local.properties.template               API Key 配置模板（需复制为 local.properties）
├── local.properties.example                配置示例（另一格式参考）
├── rayclaw.conf.template                   运行时配置模板
├── test_openclaw_gateway.py                OpenClaw 网关连通测试脚本（CLI 对话客户端）
├── build_and_install.ps1                   一键构建安装脚本（Windows PowerShell）
└── LICENSE                                 MIT License
```

---

## 开发指引：rayneo-x3-ar-dev-guide

本仓库内置完整的 RayNeo X3 AR 应用开发文档，记录了从零搭建 AR 应用的全部经验和踩坑记录。

| 章节 | 内容 |
|------|------|
| `01-introduction/` | X3 硬件特性、AR 开发与普通 Android 开发的核心差异 |
| `02-environment-setup/` | 开发环境配置、项目搭建、Mercury SDK 接入、API Key 管理 |
| `03-core-concepts/` | 双屏渲染（Fusion Vision）原理、镜腿输入事件、焦点管理、3D 视差效果 |
| `04-ui-components/` | BaseMirrorActivity / Fragment / View 用法、FToast / FDialog、RecyclerView 手势导航 |
| `05-hardware-apis/` | Camera2 双眼预览、IMU 头部姿态、设备状态监控、语音识别踩坑 |
| `06-recipes/` | 5 个完整 Demo：Hello World · 菜单导航 · 滚动列表 · 视频播放 · Camera AR 叠层 |
| `07-debugging/` | ADB 常用命令、单眼投屏调试技巧、性能分析 |
| `08-faq.md` | 高频问题汇总 |

入口：[rayneo-x3-ar-dev-guide/README.md](rayneo-x3-ar-dev-guide/README.md)

---

## 依赖说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| Mercury Android SDK | 0.2.5 | RayNeo X3 硬件抽象：双眼渲染、镜腿手势 |
| OkHttp | 4.12.0 | HTTP/WebSocket 网络层（SSE 流式请求） |
| commonmark-java | 0.24.0 | Markdown 解析（含 GFM 表格扩展） |
| Kotlin Coroutines | 1.7.3 | 异步并发 |
| AndroidX Lifecycle | 2.6.1 | Lifecycle-aware 协程作用域 |

---

## 网络安全说明

`AndroidManifest.xml` 中启用了 `usesCleartextTraffic="true"`。
原因：OpenClaw 网关 URL 由用户在运行时自行配置（`rayclaw.conf`），可能指向本地网络或内网的 HTTP 服务器。
**若你的部署环境全程使用 HTTPS/WSS，可安全移除该标志。**

---

## License

[MIT License](LICENSE) © 2026 RayClaw Contributors

> Mercury Android SDK（`app/libs/MercuryAndroidSDK-*.aar`）版权归 RayNeo / FFalcon Technology 所有，以自有许可随本项目分发，仅用于 RayNeo X3 硬件支持。

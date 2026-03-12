# RayClaw

**RayClaw** 是一款专为 [RayNeo X3](https://www.rayneo.com/) AR 眼镜设计的智能语音 AI 助手。它将实时语音识别、流式大模型对话、多语言翻译集成在一起，全程通过镜腿触控手势驱动，让双手彻底解放。

本仓库同时附带一份完整的 **[RayNeo X3 AR 应用开发指引](rayneo-x3-ar-dev-guide/README.md)**（25 篇文章），供开发者搭建自己的 AR 应用参考。

---

## 目录

- [功能特性](#功能特性)
- [手势操作](#手势操作)
- [技术架构](#技术架构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
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
| OpenClaw 智能体网关 | LLM 流式对话 | 自部署或联系提供方 |

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

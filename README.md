# RayClaw — RayNeo X3 AR 语音 AI 助手

**RayClaw** 是一款专为 [RayNeo X3](https://www.rayneo.com/) AR 眼镜设计的智能语音助手应用，集成了实时语音识别、流式 LLM 对话和多语言翻译功能，完全通过镜腿触控手势操作，无需手机屏幕介入。

> 本仓库同时包含一份完整的 [RayNeo X3 AR 应用开发指引](#开发指引rayneo-x3-ar-dev-guide)，供开发者参考。

---

## 功能特性

| 功能 | 说明 |
|------|------|
| **语音对话** | 单击镜腿开始/暂停录音，实时语音识别（DashScope Paraformer） |
| **AI 流式回复** | 接入 OpenClaw 智能体网关，流式 SSE 输出，实时渲染 Markdown |
| **多语言 ASR** | 向前/向后滑动镜腿切换识别语言（中文、英文、日文等） |
| **双眼同步渲染** | 基于 Mercury SDK `BaseMirrorActivity`，左右眼画面完全同步 |
| **多翻译引擎** | 内置 6 种翻译提供商（MyMemory / 百度 / 有道 / 腾讯 / DeepL / Azure） |
| **手势全控** | 单击 · 双击 · 长按 · 向前滑 · 向后滑，5 种手势覆盖全部交互 |

---

## 手势操作说明

| 手势 | 动作 |
|------|------|
| 单击镜腿 | 开始 / 暂停监听 |
| 向前滑动 | 切换到下一语言 |
| 向后滑动 | 切换到上一语言 |
| 长按镜腿 | 清空当前对话 |
| 双击镜腿 | 退出应用 |

---

## 技术架构

```
RayClaw
├── UI 层
│   └── AgentChatActivity         — 继承 BaseMirrorActivity，双眼同步 Chat 界面
├── 语音识别 (ASR)
│   ├── SpeechEngine              — DashScope WebSocket 实时识别
│   └── AsrConfig / AsrLanguage   — 配置与多语言枚举
├── AI 对话
│   ├── OpenClawClient            — HTTP SSE 流式客户端
│   └── AgentConfig               — 网关地址 / Agent ID / Token 配置
├── 翻译
│   ├── TranslationManager        — Provider 工厂（策略模式）
│   └── providers/                — MyMemory · 百度 · 有道 · 腾讯 · DeepL · Azure
└── UI 工具
    └── MarkdownRenderer          — Markdown → HTML（WebView 渲染）
```

**核心依赖**

- [Mercury Android SDK v0.2.5](rayneo-x3-ar-dev-guide/assets/) — RayNeo X3 AR 框架（双眼渲染、触控手势）
- [OkHttp 4.12](https://square.github.io/okhttp/) — SSE 流式网络请求
- [commonmark-java 0.24](https://github.com/commonmark/commonmark-java) — Markdown 解析
- Kotlin Coroutines 1.7 — 异步并发

---

## 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Meerkat 以上 |
| JDK | 17 |
| Gradle | 9.3.1（Wrapper 自动下载） |
| compileSdk | 36（Android 16） |
| minSdk | 26（Android 8.0） |
| 硬件 | RayNeo X3 眼镜（或兼容设备） |

---

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/lunanightshade-z/rayclaw.git
cd rayclaw
```

### 2. 配置 API Keys

复制模板并填入你的密钥：

```bash
cp local.properties.example local.properties
```

编辑 `local.properties`：

```properties
# Android SDK 路径
sdk.dir=C:\Users\<你的用户名>\AppData\Local\Android\Sdk

# DashScope 语音识别（阿里云）
# 申请地址：https://dashscope.console.aliyun.com/
DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx

# OpenClaw 智能体网关
OPENCLAW_BASE_URL=https://your-openclaw-gateway.example.com
OPENCLAW_GATEWAY_TOKEN=your_token_here
OPENCLAW_AGENT_ID=your_agent_id_here
```

> `local.properties` 已在 `.gitignore` 中，不会被提交到版本控制。

### 3. 构建并推送到设备

通过 Android Studio 运行，或使用构建脚本：

```powershell
# Windows PowerShell
.\build_and_install.ps1
```

或手动执行：

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. 启动应用

在 RayNeo X3 上找到 **rayclaw** 应用并打开，单击镜腿即可开始语音对话。

---

## 项目结构

```
rayclaw/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/xrappinit/
│   │   │   ├── AgentChatActivity.kt        — 主界面，手势 + 对话管理
│   │   │   ├── TranslatorActivity.kt       — 翻译功能界面
│   │   │   ├── MyApplication.kt            — Application 初始化 MercurySDK
│   │   │   ├── agent/                      — OpenClaw 客户端
│   │   │   ├── asr/                        — 语音识别
│   │   │   ├── translation/                — 翻译引擎
│   │   │   └── ui/                         — Markdown 渲染工具
│   │   ├── res/layout/                     — 界面布局
│   │   └── AndroidManifest.xml
│   ├── libs/
│   │   └── MercuryAndroidSDK-*.aar         — Mercury SDK 本地依赖
│   └── build.gradle.kts
├── rayneo-x3-ar-dev-guide/                 — AR 开发完整指引（见下方）
├── local.properties.example                — API Key 配置模板
├── build_and_install.ps1                   — 一键构建安装脚本
└── test_openclaw_gateway.py                — OpenClaw 网关连接测试脚本
```

---

## 开发指引：rayneo-x3-ar-dev-guide

本仓库内置完整的 RayNeo X3 AR 应用开发文档，共 25 篇 Markdown 文章：

| 章节 | 内容 |
|------|------|
| `01-introduction/` | X3 硬件特性、AR 开发与移动开发的核心差异 |
| `02-environment-setup/` | 开发环境配置、项目搭建、SDK 接入、API Key 管理 |
| `03-core-concepts/` | 双屏渲染（Fusion Vision）、镜腿输入、焦点管理、3D 视差效果 |
| `04-ui-components/` | BaseMirrorActivity / Fragment / View、FToast / FDialog、RecyclerView 手势导航 |
| `05-hardware-apis/` | Camera2 双眼预览、IMU 头部姿态、设备状态、语音识别（含坑点说明） |
| `06-recipes/` | Hello World · 菜单导航 · 滚动列表 · 视频播放 · Camera AR 叠层——5 个完整示例 |
| `07-debugging/` | ADB 常用命令、单眼投屏调试技巧 |
| `08-faq.md` | 高频问题汇总 |

开发指引入口：[rayneo-x3-ar-dev-guide/README.md](rayneo-x3-ar-dev-guide/README.md)

---

## 配置说明

所有密钥通过 `local.properties` 注入 `BuildConfig`，在代码中通过 `BuildConfig.DASHSCOPE_API_KEY` 等字段访问，**不硬编码在源码中**。

如需测试 OpenClaw 网关连通性：

```bash
python test_openclaw_gateway.py
```

---

## License

本项目仅供学习和参考，Mercury Android SDK 版权归 RayNeo / FFalcon 所有。

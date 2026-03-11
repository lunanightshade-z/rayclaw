# RayNeo X3 AR 应用开发完全指南

> 基于 Mercury Android SDK v0.2.5，面向 Android 原生开发者

本指南面向有一定 Android 开发基础（熟悉 Kotlin、ViewBinding、RecyclerView）但**从未接触过 AR 眼镜开发**的工程师。它不仅告诉你"用哪个 API"，还会解释"为什么要这样做"——让你真正理解眼镜与手机开发的本质差异，从而写出高质量的 AR 应用。

---

## 文档结构

```
rayneo-x3-ar-dev-guide/
├── README.md                              ← 当前文件：总览与阅读路线
│
├── 01-introduction/
│   ├── 01-what-is-rayneo-x3.md           ← X3 硬件特性与架构说明
│   └── 02-ar-vs-mobile-differences.md    ← AR 开发与手机开发的核心差异
│
├── 02-environment-setup/
│   ├── 01-prerequisites.md               ← 开发环境要求与版本对照表
│   ├── 02-project-setup.md               ← 新建项目完整步骤
│   ├── 03-sdk-integration.md             ← SDK 接入、初始化与验证
│   └── 04-api-key-management.md          ← API Key 安全管理（local.properties + BuildConfig）
│
├── 03-core-concepts/
│   ├── 01-dual-screen-rendering.md       ← 合目渲染：双屏同步的核心原理
│   ├── 02-temple-input-events.md         ← 镜腿触控：AR 的唯一交互入口
│   ├── 03-focus-management.md            ← 焦点系统：无触屏 UI 导航的基础
│   └── 04-3d-parallax-effects.md         ← 3D 视差：让内容"悬浮"在眼前
│
├── 04-ui-components/
│   ├── 01-mirror-activity.md             ← BaseMirrorActivity 详解
│   ├── 02-mirror-fragment.md             ← BaseMirrorFragment 详解
│   ├── 03-mirror-views.md                ← View 级合目组件
│   ├── 04-toast-dialog.md                ← FToast 与 FDialog 使用指南
│   └── 05-recyclerview-navigation.md     ← RecyclerView 镜腿导航增强
│
├── 05-hardware-apis/
│   ├── 01-camera.md                      ← Camera2 API：双屏预览与帧捕获
│   ├── 02-imu-sensors.md                 ← IMU 传感器：头部姿态追踪
│   ├── 03-device-state.md                ← 设备连接状态与手机 GPS 推流
│   └── 04-speech-recognition.md          ← 语音转文本：为什么 SpeechRecognizer 无效及正确方案
│
├── 06-recipes/
│   ├── 01-hello-world.md                 ← 第一个 AR 应用（完整步骤）
│   ├── 02-menu-with-focus.md             ← 带焦点导航的 AR 菜单
│   ├── 03-scrollable-list.md             ← 镜腿驱动的可滑动列表
│   ├── 04-video-mirroring.md             ← 视频播放双屏同步
│   └── 05-camera-ar-overlay.md           ← 摄像头预览 + AR 信息叠加
│
├── 07-debugging/
│   ├── 01-adb-commands.md                ← 常用 ADB 命令速查
│   └── 02-single-eye-preview.md          ← 单目投屏调试技巧
│
└── 08-faq.md                              ← 常见问题汇总
```

---

## 推荐阅读路线

### 路线 A：完全新手（从未做过 AR 开发）

```
01-introduction/01  →  01-introduction/02  →  02-environment-setup（全部）
→  03-core-concepts（全部）  →  06-recipes/01-hello-world
→  按需阅读其余内容
```

### 路线 B：有 Android 经验、想快速上手

```
01-introduction/02（了解差异）  →  02-environment-setup/03（SDK接入）
→  03-core-concepts/01（合目）  →  03-core-concepts/02（手势）
→  06-recipes/01-hello-world  →  06-recipes/02-menu-with-focus
```

### 路线 C：已有基础，查阅特定功能

直接进入对应章节：
- **相机/视觉**：`05-hardware-apis/01-camera.md`
- **头部追踪**：`05-hardware-apis/02-imu-sensors.md`
- **语音识别**：`05-hardware-apis/04-speech-recognition.md`
- **API Key 管理**：`02-environment-setup/04-api-key-management.md`
- **列表 UI**：`04-ui-components/05-recyclerview-navigation.md`
- **调试技巧**：`07-debugging/`

---

## SDK 版本信息

| 组件 | 版本 |
|------|------|
| Mercury Android SDK | v0.2.5 (20260212) |
| 最低 Android SDK | API 26 (Android 8.0) |
| 目标 Android SDK | API 32 (Android 12) |
| 推荐 Android Studio | Otter 2 Feature Drop 2025.2.2+ |
| 推荐 AGP | 8.3.x+ |
| 推荐 JDK | 17 |
| 开发语言 | Kotlin |

---

## 关键概念速览

| 概念 | 说明 | 相关文档 |
|------|------|---------|
| **合目（Fusion Vision）** | 左右眼各一份 UI，合并呈现无撕裂画面 | `03-core-concepts/01` |
| **镜腿触控（Temple Input）** | 眼镜框架上的触控板，唯一交互输入源 | `03-core-concepts/02` |
| **TempleAction** | 镜腿手势的事件类型（Click/Slide等） | `03-core-concepts/02` |
| **FocusHolder** | 管理当前哪个 View 持有焦点 | `03-core-concepts/03` |
| **BindingPair** | 同步操作左右两份 ViewBinding 的工具类 | `04-ui-components/01` |
| **make3DEffect** | 通过视差偏移实现 3D 悬浮感 | `03-core-concepts/04` |

---

## 重要警告

> **眼镜不是手机** — 以下假设在 AR 开发中全部失效：
> - 用户不能"点击屏幕"
> - 只有一块"逻辑"屏幕（实为左右两块合并）
> - UI 必须在黑色背景上，否则遮挡现实视野
> - 所有导航依赖镜腿触控板，而非系统返回键

详见 `01-introduction/02-ar-vs-mobile-differences.md`

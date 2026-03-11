# SDK 接入、初始化与验证

## 1. 获取 SDK 文件

SDK 文件位于本仓库：

```
ai_ar/rayneo_x3_build_skill/assets/
├── MercuryAndroidSDK-v0.2.5-20260212110627_ceaebc13.aar   ← 主 SDK
└── MercuryAndroidSample.zip                                 ← 示例代码
```

---

## 2. 添加 AAR 到项目

### 步骤 1：创建 libs 目录

在 `app/` 模块目录下新建 `libs/` 文件夹（如果不存在）：

```
app/
└── libs/           ← 在这里放置 AAR 文件
    └── MercuryAndroidSDK-v0.2.5-20260212110627_ceaebc13.aar
```

### 步骤 2：配置 build.gradle 依赖

打开 `app/build.gradle`，在 `dependencies {}` 中添加：

```groovy
dependencies {
    // 引入 libs 目录下所有 AAR 文件
    implementation(fileTree("libs"))

    // 以下是 Mercury SDK 需要的 AndroidX 基础依赖
    implementation 'androidx.core:core-ktx:1.10.1'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.1'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1'

    // RecyclerView（如果使用列表功能）
    implementation 'androidx.recyclerview:recyclerview:1.3.1'
}
```

### 步骤 3：同步项目

点击 Android Studio 工具栏中的 **Sync Now**（或 `File → Sync Project with Gradle Files`）。

同步成功后，你应该能看到 Mercury SDK 的类出现在代码补全中，例如 `BaseMirrorActivity`。

---

## 3. 初始化 SDK

SDK 必须在 Application 的 `onCreate()` 中初始化，**在任何 Activity 创建之前完成**。

```kotlin
// MyApplication.kt
package com.yourcompany.myarapp

import android.app.Application
import com.ffalcon.mercury.android.sdk.MercurySDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MercurySDK.init(this)   // ← SDK 初始化，必须最先调用
    }
}
```

确保 `AndroidManifest.xml` 中 application 节点的 `name` 属性指向这个类：

```xml
<application
    android:name=".MyApplication"
    ... >
```

---

## 4. 验证 SDK 接入成功

### 验证方法 1：代码补全测试

在你的 `MainActivity.kt` 中，输入 `BaseMirrorActivity`，如果出现来自 `com.ffalcon.mercury.android.sdk` 的补全提示，说明 AAR 引入成功。

### 验证方法 2：编译运行

连接 X3 眼镜（ADB），点击运行按钮。如果应用成功安装并在眼镜上看到双份 UI（未佩戴时看到左右两个相同的布局），说明一切正常。

### 验证方法 3：日志确认

在 Activity 中使用 SDK 提供的日志工具：

```kotlin
import com.ffalcon.mercury.android.sdk.util.FLogger

class MainActivity : BaseMirrorActivity<ActivityMainBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FLogger.i("MainActivity", "SDK initialized, screen count = ${mBindingPair != null}")
    }
}
```

在 Android Studio Logcat 中过滤 `MainActivity`，应该看到对应日志。

---

## 5. 混淆配置

如果你的项目开启了代码混淆（`minifyEnabled true`），需要保留 SDK 和 ViewBinding 相关类。

AAR 内已提供 `proguard.txt`，当使用 `implementation(fileTree("libs"))` 时，Gradle 会自动合并这份配置。但如果你遇到混淆问题，可以在 `proguard-rules.pro` 中手动追加：

```proguard
# Mercury SDK
-keep class com.ffalcon.mercury.** { *; }

# ViewBinding（如果使用反射访问）
-keep class * implements androidx.viewbinding.ViewBinding { *; }

# Kotlin 协程（SDK 使用 Flow）
-keepnames class kotlinx.coroutines.** { *; }
```

---

## 6. 完整的 build.gradle 示例

以下是一份完整的、可直接参考的 `app/build.gradle`：

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.yourcompany.myarapp'
    compileSdk 34

    defaultConfig {
        applicationId "com.yourcompany.myarapp"
        minSdk 26
        targetSdk 32
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    buildFeatures {
        viewBinding = true   // ← 必须开启
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation(fileTree("libs"))   // ← Mercury SDK AAR

    implementation 'androidx.core:core-ktx:1.10.1'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.1'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1'
    implementation 'androidx.recyclerview:recyclerview:1.3.1'
}
```

---

## 6.1 实战修正：接入过程中的常见错误（正文流程）

本节不是 FAQ，而是接入主流程里的”真实排错顺序”。
错误分两类：**构建期**（Gradle 报错，APK 打不出来）和**运行期**（APK 装上去打开就崩溃）。
如果你在接入 Mercury AAR 时遇到类似报错，请按下面顺序处理。

### 场景 A：`Cannot add extension with name 'kotlin'` / `plugin is already on the classpath`

**现象**：应用 `org.jetbrains.kotlin.android` 后立即失败，提示 `kotlin` 扩展重复，或报：
```
Error resolving plugin [id: 'org.jetbrains.kotlin.android', version: '2.0.21']
> The request for this plugin could not be satisfied because the plugin is already
  on the classpath with an unknown version
```

**根本原因**：AGP 9.x 已将 Kotlin Android 支持内置到 `com.android.application` 插件中，无需也不能再显式声明 `org.jetbrains.kotlin.android`。

**正确做法**：`app/build.gradle.kts` 只保留一个插件声明：
```kotlin
plugins {
    alias(libs.plugins.android.application)
    // ✅ 到此为止。不要再加 kotlin.android，AGP 9.x 已内置 Kotlin 支持
}
```

`libs.versions.toml` 中可以保留 kotlin 版本定义供参考，但不在 app 的 plugins 块中使用。

### 场景 B：`Unresolved reference 'kotlinOptions'`

**现象**：`build.gradle.kts` 脚本编译失败，指向 `kotlinOptions`。  
**处理**：
1. 先移除 `kotlinOptions {}`（避免卡在脚本层）。
2. 保留 `compileOptions`（例如 Java 17）与 `viewBinding`，先让工程可编译。
3. 等基础链路跑通后，再按你工程当前的 Kotlin 插件版本补回对应 DSL。

### 场景 C：`checkDebugAarMetadata`（`compileSdk` 过低）

**现象**：依赖提示 `requires compileSdk 35/36+`，而示例中是 `compileSdk 34`。  
**处理**：
1. 将 `compileSdk` 提升到依赖要求版本（建议 36）。
2. `targetSdk` 可保留业务目标版本（例如 32），无需强制与 `compileSdk` 一致。
3. 再次执行 `assembleDebug`，确认 `checkDebugAarMetadata` 通过。

### 场景 D：APK 安装成功，但点击图标后立即崩溃（运行时 ClassNotFoundException）

**现象**：`assembleDebug` 构建成功，APK 正常安装，但一打开应用就闪退，Logcat 报：
```
Caused by: java.lang.ClassNotFoundException:
  Didn't find class “androidx.lifecycle.ViewModelKt”
  on path: DexPathList[[zip file “.../base.apk”], ...]
```

**根本原因**：Mercury SDK 的 `TempleActionViewModel` 在运行时依赖 `androidx.lifecycle.ViewModelKt`（位于 `lifecycle-viewmodel-ktx`）。这个依赖没有被 AAR 传递声明，必须由宿主 App **显式引入**。
构建阶段之所以不报错，是因为 AAR 已包含类引用的桩；只有到真机运行时，Dalvik 找不到实际类文件才会崩溃。

**修复**：在 `app/build.gradle.kts` 的 `dependencies` 中补上：
```kotlin
implementation(“androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1”)
```

**关键教训**：Mercury SDK 有些运行时依赖不会体现在构建错误中——构建通过≠能正常运行。遇到”安装成功但启动崩溃”，**第一步永远是查 Logcat 的 `AndroidRuntime` / `crash` 缓冲区**：
```bash
adb logcat -b crash
```

---

### 推荐执行顺序（给新手）

1. 先解决插件/DSL 错误（A、B），让 Gradle 脚本可解析。
2. 再解决 AAR Metadata 错误（C），让依赖版本通过检查。
3. 能打包后，安装到设备并打开 App，确认不崩溃。
4. 若启动就崩溃，查 `adb logcat -b crash`，按场景 D 排查运行时依赖缺失。
5. 最后处理业务代码报错（如 `BaseMirrorActivity`、`TempleAction` 导包等）。

按这个顺序排错，通常能最快把项目带到”可安装可运行”的状态。

---

## 7. 常见接入问题

### 问题：`Duplicate class` 错误
**原因**：项目其他依赖与 AAR 内部依赖版本冲突
**解决**：在 `dependencies` 中排除冲突包：
```groovy
implementation(fileTree("libs")) {
    exclude group: 'androidx.core', module: 'core'
}
```

### 问题：`Cannot resolve symbol BaseMirrorActivity`
**原因**：AAR 未正确引入，或 Gradle Sync 未完成
**解决**：
1. 确认 AAR 文件在 `app/libs/` 目录下
2. 重新执行 `File → Sync Project with Gradle Files`
3. 尝试 `Build → Clean Project` 后重新 Build

### 问题：`minSdkVersion XX is lower than version 26 declared in library`
**原因**：项目 `minSdk` 低于 Mercury SDK 要求的 26
**解决**：在 `defaultConfig` 中设置 `minSdk = 26`

### 问题：`Cannot add extension with name 'kotlin'`（AGP 9.x）
**典型报错**：应用 `org.jetbrains.kotlin.android` 插件时，提示已存在同名 `kotlin` 扩展。  
**原因**：在某些 AGP 9.x 工程模板中，Kotlin 扩展已由现有插件链提供；此时再次显式应用 Kotlin 插件会重复注册。  
**解决步骤**：
1. 先只保留 Android Application 插件（`com.android.application`），移除重复的 `org.jetbrains.kotlin.android` 显式声明。
2. 重新 Sync 并执行一次 `assembleDebug` 验证。
3. 若你的工程确实需要显式 Kotlin 插件（例如纯 Java 模板改造），确保只声明一次，且在 root 与 app 模块不重复。

### 问题：`Unresolved reference 'kotlinOptions'`（`build.gradle.kts`）
**典型报错**：`kotlinOptions { jvmTarget = "17" }` 在 Kotlin DSL 下无法识别。  
**原因**：当前插件组合未暴露该 DSL 扩展，直接写 `kotlinOptions` 会脚本编译失败。  
**解决步骤**：
1. 临时移除 `kotlinOptions {}`，先保证项目可构建。
2. 保留 `compileOptions` 的 Java 版本设置（例如 Java 17）。
3. 构建通过后，再根据你项目使用的 Kotlin 插件版本补充对应 DSL（不同 AGP/Kotlin 版本写法可能不同）。

### 问题：应用安装成功，点击图标后立即退出（闪退/无响应）
**典型 Logcat 报错**：
```
Caused by: java.lang.ClassNotFoundException:
  Didn't find class "androidx.lifecycle.ViewModelKt"
```
**原因**：Mercury SDK 内部的 `TempleActionViewModel` 依赖 `lifecycle-viewmodel-ktx`，但该包不在 AAR 的传递依赖中，须宿主 App 显式声明。构建期不会报错，只在运行时崩溃。
**解决**：
```kotlin
// app/build.gradle.kts
dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
}
```
**排查方法**：出现"装上去打不开"时，第一步看 crash 日志：
```bash
adb logcat -b crash
```

### 问题：`checkDebugAarMetadata` 失败，提示 `compileSdk` 过低
**典型报错**：依赖（如 `androidx.core:1.17.0`、`recyclerview:1.4.0`）要求 `compileSdk >= 35/36`，而项目是 `compileSdk 34`。  
**原因**：你项目里 AndroidX 版本较新，已超过文档示例中的 `compileSdk 34` 要求。  
**正确做法**：
1. 将 `compileSdk` 提升到依赖要求的版本（推荐 `36`）。
2. `targetSdk` 可继续按产品策略保持 `32`（`compileSdk` 与 `targetSdk` 可以分开设置）。
3. 再次执行 `assembleDebug`，确认 `checkDebugAarMetadata` 通过。

---

## 下一步

SDK 接入完成后，进入核心概念学习：

- [`../03-core-concepts/01-dual-screen-rendering.md`](../03-core-concepts/01-dual-screen-rendering.md)：理解合目渲染机制

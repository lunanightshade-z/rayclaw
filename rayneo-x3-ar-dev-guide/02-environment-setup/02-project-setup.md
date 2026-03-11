# 新建 AR 项目完整步骤

本文从零开始，完整演示如何在 Android Studio 中创建一个面向 RayNeo X3 的 AR 应用项目。

---

## 步骤 1：新建 Android 项目

1. 打开 Android Studio，选择 **New Project**
2. 模板选择 **No Activity**（我们会手动创建 Activity，让你理解每个文件的作用）
3. 点击 **Next**，配置项目：

| 字段 | 建议值 | 说明 |
|------|-------|------|
| Name | `MyARApp` | 应用名称 |
| Package name | `com.yourcompany.myarapp` | 唯一包名 |
| Save location | 自定义路径 | 项目存储位置 |
| Language | **Kotlin** | SDK 基于 Kotlin 封装 |
| Minimum SDK | **API 26** | Mercury SDK 要求 |

4. 点击 **Finish**，等待项目初始化完成（首次可能需要下载 Gradle 依赖）

---

## 步骤 2：启用 ViewBinding

Mercury SDK 完全基于 ViewBinding 封装，这是**必须的配置**。

打开 `app/build.gradle`，在 `android {}` 块内添加：

```groovy
android {
    compileSdk 34

    defaultConfig {
        applicationId "com.yourcompany.myarapp"
        minSdk 26          // ← Mercury SDK 要求最低 26
        targetSdk 32       // ← 与 SDK AAR 对齐
        versionCode 1
        versionName "1.0"
    }

    // ↓ 添加这个块
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}
```

### 实战修正（Kotlin DSL / AGP 9.x 项目）

上面是通用配置思路，但在 **Android Studio 新模板（Kotlin DSL + AGP 9.x）** 中，实战经常遇到以下差异。  
如果你直接照抄旧版示例，可能会在 Sync 或 Build 阶段报错。

1. **不要机械照抄 `kotlinOptions {}`**
- 在部分模板中，`kotlinOptions` 可能提示 `Unresolved reference`。
- 建议先保证下面两项生效：
  - `buildFeatures { viewBinding = true }`
  - `compileOptions { sourceCompatibility / targetCompatibility }`
- 先构建通过，再按你当前 Kotlin 插件版本补 Kotlin 编译参数。

2. **`compileSdk` 和 `targetSdk` 要分开理解**
- `compileSdk` 是“编译时 API 上限”，主要受依赖库要求影响。
- `targetSdk` 是“运行行为适配目标”。
- 实战中可以出现：`compileSdk = 36`，`targetSdk = 32`。这是允许且常见的组合。

3. **先跑通一版再做“版本对齐优化”**
- 推荐流程：先 `assembleDebug` 成功，再回头优化依赖版本与 DSL 写法。
- 这样可以避免新手在同一时间处理多个报错，降低定位复杂度。

---

## 步骤 3：创建 Application 类

Mercury SDK 需要在 Application 初始化时执行 `MercurySDK.init()`。

在 `app/src/main/java/<你的包名>/` 下新建 `MyApplication.kt`：

```kotlin
package com.yourcompany.myarapp

import android.app.Application
import com.ffalcon.mercury.android.sdk.MercurySDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MercurySDK.init(this)
    }
}
```

---

## 步骤 4：配置 AndroidManifest.xml

打开 `app/src/main/AndroidManifest.xml`，进行以下配置：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".MyApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.MyARApp">

        <!--
            关键：让眼镜系统识别此应用，使其出现在 Launcher 中
            如果不加这个，应用不会在眼镜 launcher 里显示图标
        -->
        <meta-data
            android:name="com.rayneo.mercury.app"
            android:value="true" />

        <!-- 后续步骤会在这里添加 Activity -->

    </application>

</manifest>
```

> **为什么需要 meta-data？**
> RayNeo X3 的 Launcher 通过扫描所有 App 的 `meta-data` 来识别哪些是"AR 应用"。没有这个标签的 App 不会出现在眼镜主界面。

---

## 步骤 5：配置应用主题（黑色背景）

打开 `app/src/main/res/values/themes.xml`，确保应用默认背景为黑色：

```xml
<resources>
    <style name="Theme.MyARApp" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <!-- AR 应用必须：黑色背景 = 透明显示现实世界 -->
        <item name="android:windowBackground">@android:color/black</item>
        <!-- 状态栏透明 -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <!-- 导航栏透明 -->
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

如果你支持深色模式，同样修改 `res/values-night/themes.xml`。

---

## 步骤 6：创建第一个布局文件

在 `app/src/main/res/layout/` 下新建 `activity_main.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">  <!-- 黑色背景 = AR 透明叠加 -->

    <TextView
        android:id="@+id/tvHello"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello RayNeo AR!"
        android:textColor="#FFFFFF"
        android:textSize="36sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

**关键设计决策**：
- 背景 `#000000`：AR 透明效果
- 文字色 `#FFFFFF`：在透明背景上清晰可见
- 字号 `36sp`：足够大，在眼镜上易读

---

## 步骤 7：创建 MainActivity

在 `app/src/main/java/<包名>/` 下新建 `MainActivity.kt`：

```kotlin
package com.yourcompany.myarapp

import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.yourcompany.myarapp.databinding.ActivityMainBinding

class MainActivity : BaseMirrorActivity<ActivityMainBinding>() {
    // BaseMirrorActivity 会自动处理：
    // 1. 将 activity_main.xml 布局同时渲染到左右两屏
    // 2. 处理双屏同步逻辑
    // 3. 将镜腿手势转换为 TempleAction 事件流

    // 目前不需要额外代码，双屏显示已经生效
}
```

> **继承 `BaseMirrorActivity<B>` 说明**：
> - 泛型参数 `B` 是你的布局对应的 ViewBinding 类
> - ViewBinding 类名规则：布局文件名转驼峰 + `Binding`
>   - `activity_main.xml` → `ActivityMainBinding`
> - `BaseMirrorActivity` 内部会自动创建两份 ViewBinding，分别渲染到左右屏

---

## 步骤 8：注册 Activity

回到 `AndroidManifest.xml`，在 `<application>` 内注册 Activity：

```xml
<application ... >

    <meta-data
        android:name="com.rayneo.mercury.app"
        android:value="true" />

    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:screenOrientation="landscape">  <!-- 眼镜应用通常横屏 -->
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

</application>
```

---

## 步骤 9：验证项目结构

完成后，你的项目结构应该是：

```
app/
├── libs/
│   └── MercuryAndroidSDK-v0.2.5-....aar    ← 下一步会添加
├── src/main/
│   ├── java/com/yourcompany/myarapp/
│   │   ├── MyApplication.kt
│   │   └── MainActivity.kt
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml
│   │   └── values/
│   │       └── themes.xml
│   └── AndroidManifest.xml
└── build.gradle
```

---

## 下一步

- [`03-sdk-integration.md`](./03-sdk-integration.md)：添加 SDK AAR 依赖并完成初始化

# Recipe 1：第一个完整 AR 应用

本文是一个完整的、可直接运行的 AR 应用示例。通过它，你将亲手实现：
- 双屏合目显示
- 镜腿手势响应
- AR 风格的 UI 设计

---

## 目标效果

- 眼镜屏幕中央显示"Hello AR"文字
- 单击镜腿：弹出 FToast 提示
- 双击镜腿：退出应用
- 背景透明（可以看到真实世界）

---

## 文件清单

```
app/
├── libs/
│   └── MercuryAndroidSDK-v0.2.5-....aar
├── src/main/
│   ├── java/com/example/helloar/
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

## 步骤 1：build.gradle

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.helloar'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.helloar"
        minSdk 26
        targetSdk 32
        versionCode 1
        versionName "1.0"
    }

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

dependencies {
    implementation(fileTree("libs"))
    implementation 'androidx.core:core-ktx:1.10.1'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.1'
}
```

---

## 步骤 2：AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".MyApplication"
        android:icon="@mipmap/ic_launcher"
        android:label="Hello AR"
        android:theme="@style/Theme.HelloAR">

        <!-- 让眼镜 Launcher 识别此应用 -->
        <meta-data
            android:name="com.rayneo.mercury.app"
            android:value="true" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

---

## 步骤 3：res/values/themes.xml

```xml
<resources>
    <style name="Theme.HelloAR" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <!-- 黑色背景 = AR 透明叠加效果 -->
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

---

## 步骤 4：res/layout/activity_main.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000"
    android:paddingHorizontal="@dimen/rayneo_safety_padding_horizontal"
    android:paddingVertical="@dimen/rayneo_safety_padding_vertical">

    <!-- 主标题 -->
    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello AR"
        android:textColor="#FFFFFF"
        android:textSize="48sp"
        android:fontFamily="sans-serif-light"
        app:layout_constraintBottom_toTopOf="@id/tvHint"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_chainStyle="packed" />

    <!-- 操作提示 -->
    <TextView
        android:id="@+id/tvHint"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="单击镜腿体验 · 双击退出"
        android:textColor="#80FFFFFF"
        android:textSize="18sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/tvTitle" />

    <!-- 点击计数 -->
    <TextView
        android:id="@+id/tvCounter"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="24dp"
        android:text=""
        android:textColor="#4FC3F7"
        android:textSize="24sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## 步骤 5：MyApplication.kt

```kotlin
package com.example.helloar

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

## 步骤 6：MainActivity.kt

```kotlin
package com.example.helloar

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.example.helloar.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : BaseMirrorActivity<ActivityMainBinding>() {

    private var clickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 UI（updateView 同步更新两屏）
        mBindingPair.updateView {
            tvTitle.text = "Hello AR"
            tvHint.text = "单击镜腿体验 · 双击退出"
        }

        // 监听镜腿手势
        collectTempleActions()
    }

    private fun collectTempleActions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.Click -> {
                            clickCount++
                            onUserClick()
                        }
                        is TempleAction.DoubleClick -> {
                            // 约定：双击退出
                            FToast.show("再见！")
                            finish()
                        }
                        is TempleAction.LongClick -> {
                            FToast.show("长按了！")
                        }
                        is TempleAction.SlideForward -> {
                            FToast.show("前滑")
                        }
                        is TempleAction.SlideBackward -> {
                            FToast.show("后滑")
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun onUserClick() {
        // 同步更新两屏的计数显示
        mBindingPair.updateView {
            tvCounter.text = "已点击 $clickCount 次"
        }

        // 显示 AR Toast（自动双屏同步）
        FToast.show("单击！第 $clickCount 次")
    }
}
```

---

## 运行与测试

1. 连接 X3 眼镜（USB）
2. 确认 `adb devices` 能看到设备
3. 点击 Android Studio 运行按钮
4. 在眼镜上看到应用出现在 Launcher
5. 打开应用，佩戴眼镜，验证：
   - 左右眼看到相同内容（合目效果）
   - 单击镜腿触发计数更新和 Toast
   - 双击退出

---

## 常见问题

**问：应用安装成功但在 Launcher 看不到图标**
答：确认 `AndroidManifest.xml` 中有 `meta-data` 标签，且 `android:value="true"`。

**问：看到两份 UI 并排但没有合目效果**
答：未佩戴眼镜时，在手机或外接显示器上，确实能看到两份并排。佩戴眼镜后双目融合才能看到合目效果，这是正常的。

**问：单击镜腿没有反应**
答：检查 `templeActionViewModel.state.collect` 是否在 `repeatOnLifecycle(Lifecycle.State.RESUMED)` 中，且 Activity 确实处于 RESUMED 状态。

---

## 下一步

- [`02-menu-with-focus.md`](./02-menu-with-focus.md)：添加焦点导航菜单

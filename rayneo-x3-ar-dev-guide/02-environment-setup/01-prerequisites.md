# 开发环境要求与版本对照表

## 1. 必备工具

| 工具 | 最低版本 | 推荐版本 | 说明 |
|------|---------|---------|------|
| Android Studio | Otter 2 Feature Drop 2025.2.2 | 同左或更高 | 官方 IDE |
| JDK | 17 | 17 | AGP 8.x 要求 JDK 17 |
| AGP (Android Gradle Plugin) | 8.0.0 | 8.3.x+ | 构建工具 |
| Gradle | 8.0 | 8.4+ | 配合 AGP 8.3.x |
| Kotlin | 1.8.x | 2.0.x+ | 开发语言 |
| minSdkVersion | 26 | - | AAR 硬性要求 |
| targetSdkVersion | 32 | 32 | AAR 配置 |

> **注意**：Mercury SDK AAR 的 `minSdkVersion = 26`（Android 8.0），如果你的项目设置低于 26，会导致编译失败。

---

## 2. AGP / Gradle / JDK 版本对照表

这是 Android 生态中最容易踩坑的地方。版本不匹配会导致各种奇怪的构建错误。

```
AGP 版本           最低 Gradle   推荐 Gradle 范围     Kotlin 插件范围    最低 JDK
─────────────────────────────────────────────────────────────────────────────────
8.3.x - 8.10.x    8.3          8.4 - 8.12+          2.0.x - 2.1.x     JDK 17
8.0.x - 8.2.x     8.0          8.0 - 8.3            1.8.x - 2.0.x     JDK 17
7.4.x             7.5          7.5 - 7.6             1.8.x - 1.9.x     JDK 11
7.0.x - 7.3.x     7.0          7.0 - 7.5             1.5.x - 1.8.x     JDK 11
4.2.0+            6.7.1        6.7.1 - 6.9           1.4.x - 1.5.x     JDK 8
```

### 检查当前版本

**检查 AGP 版本**（`build.gradle` 根目录或 `settings.gradle`）：
```groovy
// 根目录 build.gradle
plugins {
    id 'com.android.application' version '8.3.2' apply false
}
```

**检查 Gradle 版本**（`gradle/wrapper/gradle-wrapper.properties`）：
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
```

**检查 JDK 版本**：
```bash
java -version
# 应输出: openjdk version "17.x.x" ...
```

在 Android Studio 中：`File → Project Structure → SDK Location → JDK location`

---

## 3. 物理设备要求

- **RayNeo X3 眼镜**：必须有真机，模拟器无法测试双屏和镜腿手势
- **USB 数据线**：用于 ADB 连接
- **ADB 驱动**：Windows 用户需要确保 ADB 驱动已安装

### 开启 USB 调试

在眼镜设备上：
1. 进入「设置」→「关于」→ 连续点击「版本号」7 次，开启「开发者模式」
2. 进入「设置」→「开发者选项」→ 开启「USB 调试」
3. 连接 USB 后，眼镜上出现「允许 USB 调试」弹窗，选择允许

### 验证连接

```bash
adb devices
# 应看到类似：
# List of devices attached
# XXXXXXXX    device
```

---

## 4. 常见环境问题排查

### 问题：`java.lang.UnsupportedClassVersionError`
**原因**：JDK 版本低于 AGP 要求
**解决**：升级 JDK 到 17，并在 Android Studio 中配置正确的 JDK 路径

### 问题：`Minimum supported Gradle version is X.X`
**原因**：Gradle 版本低于 AGP 要求
**解决**：更新 `gradle-wrapper.properties` 中的 `distributionUrl`

### 问题：编译时 AAR 相关报错
**原因**：`minSdkVersion < 26`
**解决**：在 `app/build.gradle` 中设置 `minSdk = 26`

### 问题：`D8: Cannot fit requested classes in a single dex file`
**原因**：方法数超限
**解决**：在 `app/build.gradle` 中启用 multidex：
```groovy
android {
    defaultConfig {
        multiDexEnabled true
    }
}
dependencies {
    implementation 'androidx.multidex:multidex:2.0.1'
}
```

---

## 下一步

- [`02-project-setup.md`](./02-project-setup.md)：新建项目的完整步骤

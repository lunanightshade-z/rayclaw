# 常用 ADB 命令速查

开发 AR 眼镜应用时，ADB 是最主要的调试工具。以下是在 X3 开发中最常用的命令。

---

## 设备连接

```bash
# 查看连接的设备
adb devices

# 如果有多个设备，指定设备执行命令
adb -s <device-id> <command>

# 无线调试（先用 USB 连接，然后切换到无线）
adb tcpip 5555
adb connect <眼镜IP>:5555
```

---

## 应用安装与管理

```bash
# 安装 APK
adb install -r myapp.apk

# 卸载应用
adb uninstall com.example.myapp

# 强制停止应用
adb shell am force-stop com.example.myapp

# 启动特定 Activity
adb shell am start -n com.example.myapp/.MainActivity

# 启动眼镜系统设置页
adb shell am start com.android.settings/com.android.settings.Settings

# 查看当前运行的顶层 Activity
adb shell dumpsys activity | grep "mResumedActivity"
```

---

## 日志查看

```bash
# 查看实时日志
adb logcat

# 过滤特定标签的日志
adb logcat -s MyTag:D

# 过滤多个标签
adb logcat -s MyTag:D AnotherTag:I

# 过滤关键词
adb logcat | grep "MyApp"

# 清空日志缓存后查看
adb logcat -c && adb logcat

# 只看错误和警告
adb logcat *:W

# SDK 的日志标签（FLogger 输出）
adb logcat -s FLogger:V
```

---

## 防休眠设置（开发调试专用）

眼镜默认会在短时间内进入休眠，影响调试体验。可以临时禁用：

```bash
# 禁用深度休眠（重启后失效）
adb shell settings put global deep_suspend_disabled_persist 1

# 恢复默认休眠设置
adb shell settings put global deep_suspend_disabled_persist 0

# 保持屏幕常亮（需要应用有 WAKE_LOCK 权限，也可以 ADB 临时设置）
adb shell settings put global stay_on_while_plugged_in 3
```

> 开发完成后记得恢复默认设置，避免眼镜快速耗电。

---

## 投屏调试

```bash
# 用 scrcpy 投屏（只显示左眼区域，裁剪右半部分）
scrcpy --crop 640:480:0:0

# 不同分辨率的眼镜，根据实际屏幕宽度调整
# 格式：--crop 宽:高:x偏移:y偏移
scrcpy --crop 1280:720:0:0   # 取左眼（假设逻辑屏总宽 2560）

# 降低比特率，减少延迟（适合 Wi-Fi 连接）
scrcpy --crop 640:480:0:0 --bit-rate 4M
```

---

## 截图与录屏

```bash
# 截图并保存到 PC
adb exec-out screencap -p > screenshot.png

# 录制 30 秒视频到设备
adb shell screenrecord /sdcard/demo.mp4

# 将视频拉取到 PC
adb pull /sdcard/demo.mp4 .
```

---

## 文件传输

```bash
# 推送文件到设备
adb push local_file.txt /sdcard/Documents/

# 从设备拉取文件
adb pull /sdcard/Documents/file.txt .

# 推送整个目录
adb push ./assets/ /sdcard/MyApp/assets/
```

---

## 网络与权限

```bash
# 授予权限（避免弹窗打断测试流程）
adb shell pm grant com.example.myapp android.permission.CAMERA
adb shell pm grant com.example.myapp android.permission.RECORD_AUDIO
adb shell pm grant com.example.myapp android.permission.ACCESS_FINE_LOCATION

# 查看应用已有权限
adb shell dumpsys package com.example.myapp | grep permission

# 查看设备 IP 地址
adb shell ip addr show wlan0
```

---

## 性能分析

```bash
# 查看 CPU 使用率
adb shell top -n 1 | grep com.example.myapp

# 查看内存使用
adb shell dumpsys meminfo com.example.myapp

# 查看 GPU 渲染时间（需要在开发者选项中开启）
adb shell dumpsys gfxinfo com.example.myapp
```

---

## 常见 ADB 问题

**问：`adb devices` 显示 `unauthorized`**
答：设备上会弹出"允许 USB 调试"确认框，在眼镜上通过镜腿操作选择"始终允许"。

**问：执行命令后提示 `error: device not found`**
答：
1. 检查 USB 线是否连接稳定
2. 尝试 `adb kill-server && adb start-server`
3. 检查是否安装了正确的 ADB 驱动（Windows）

**问：`adb install` 失败**
答：
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：先卸载旧版本
- `INSTALL_FAILED_VERSION_DOWNGRADE`：旧版本比新版本号高，需要卸载后安装
- `INSTALL_FAILED_INSUFFICIENT_STORAGE`：设备存储不足

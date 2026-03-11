# 单目投屏调试技巧

## 1. 问题背景

在 PC 上通过 scrcpy 查看 X3 眼镜屏幕时，默认会看到**完整的逻辑屏幕**——即左右两份 UI 并排显示。这对于调试双屏布局很不方便，因为单个显示区域很小。

解决方案：使用 scrcpy 的 `--crop` 参数，只截取左眼（或右眼）区域。

---

## 2. scrcpy 单目截取

```bash
# 只显示左眼区域（裁剪出左半屏）
scrcpy --crop 640:480:0:0

# 参数含义：--crop 宽度:高度:X偏移:Y偏移
# 640:480:0:0 = 从坐标(0,0)开始，截取 640×480 像素
```

> 注意：上面的尺寸 `640:480` 是示例。实际值取决于 X3 的逻辑屏幕总分辨率（单眼宽度）。你需要先用 `adb shell wm size` 查看实际尺寸后调整。

```bash
# 查看逻辑屏幕总分辨率
adb shell wm size
# 例如输出：Physical size: 2560x720
# 则单眼宽度 = 2560 / 2 = 1280
# 所以单目截取命令：
scrcpy --crop 1280:720:0:0
```

---

## 3. 在 Android Studio 中调试布局

Android Studio 的 **Layout Inspector** 可以实时查看运行中的 Activity 视图层级：

1. 运行应用（连接 X3 眼镜）
2. 菜单 `Tools → Layout Inspector`
3. 选择对应进程
4. 观察布局树和属性

注意：Layout Inspector 显示的是逻辑视图，你会看到两份 ViewBinding 的结构（左屏 + 右屏），这是正常的。

---

## 4. 日志辅助调试

使用 `FLogger` 在关键位置记录状态：

```kotlin
import com.ffalcon.mercury.android.sdk.util.FLogger

// 记录手势事件
templeActionViewModel.state.collect { action ->
    FLogger.d("TAG", "TempleAction received: $action")
    // ...
}

// 记录焦点变化
FocusInfo(
    target = btnSomething,
    focusChangeHandler = { hasFocus ->
        FLogger.d("TAG", "Focus changed: hasFocus=$hasFocus")
        // ...
    }
)

// 记录双屏操作
mBindingPair.updateView {
    val isLeft = mBindingPair.checkIsLeft(this)
    FLogger.v("TAG", "updateView executing for: ${if (isLeft) "LEFT" else "RIGHT"}")
    // ...
}
```

---

## 5. 常见布局问题排查

### 问题：合目后内容错位（左右眼内容不对齐）

**可能原因**：
- `updateView` 中混入了外部状态修改，导致两次执行结果不同
- 某个 View 的尺寸/位置在首次创建和后续更新时不一致

**排查方法**：
1. 在 `updateView` 中打印每次执行时的关键值
2. 检查是否有 `isLeft` 判断逻辑影响了布局参数

### 问题：镜腿手势没有响应

**可能原因**：
- `repeatOnLifecycle(Lifecycle.State.RESUMED)` 未正确使用，Activity 不在 RESUMED 状态
- `focusObj.reqFocus()` 未调用，导致 FocusTracker 不响应事件
- 事件被上层 View 拦截

**排查方法**：
```kotlin
// 在最顶层直接 collect，确认事件是否到达
templeActionViewModel.state.collect { action ->
    FLogger.d("DEBUG", "Raw action: $action, consumed: ${action.consumed}")
}
```

### 问题：FToast 没有显示

**可能原因**：
- `MercurySDK.init()` 未在 Application 中调用
- Toast 显示在后台时被系统抑制

**排查方法**：
```kotlin
// 确认 MercurySDK 已初始化
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MercurySDK.init(this)
        FLogger.i("App", "MercurySDK initialized")
    }
}
```

### 问题：双屏只有一屏有内容

**可能原因**：
- 使用了 `mBindingPair.left.xxx` 直接操作，而不是 `updateView`
- RecyclerView 的 Adapter 只设置到了一侧

**排查方法**：仔细检查是否所有 UI 更新都通过 `updateView` 进行，或者左右两侧都显式赋值。

---

## 6. 开发者工具配置

### 保持应用调试时不休眠

在 `Activity` 中临时设置：

```kotlin
override fun onResume() {
    super.onResume()
    // 调试模式下保持屏幕常亮
    if (BuildConfig.DEBUG) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```

### 显示 TempleAction 调试覆盖层

在开发期间，可以在 UI 上显示当前接收到的手势，便于调试：

```kotlin
// 在 updateView 可以访问的位置添加一个 debug TextView
// 每次收到事件时更新显示
templeActionViewModel.state.collect { action ->
    if (BuildConfig.DEBUG) {
        mBindingPair.updateView {
            tvDebugAction?.text = action::class.simpleName
        }
    }
    // 正常业务逻辑...
}
```

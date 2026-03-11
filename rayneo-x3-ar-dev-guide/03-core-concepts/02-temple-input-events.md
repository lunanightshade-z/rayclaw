# 镜腿触控：AR 的唯一交互入口

在 AR 眼镜上，用户的双手不自由（可能正在工作、走路），屏幕也不是触摸屏。镜腿触控板是**唯一的主要交互方式**。理解并正确使用镜腿事件系统，是 AR 应用可用性的关键。

---

## 1. 事件流架构

```
物理触控板（镜腿）
       ↓
  原始 MotionEvent（Android 系统）
       ↓
  TouchDispatcher / TouchDispatcherX3
  （识别手势类型：单击、双击、滑动等）
       ↓
  CommonTouchCallback 接口
       ↓
  TempleActionViewModel（Kotlin Flow）
       ↓
  你的业务逻辑
```

SDK 封装了底层所有细节，你只需要：
1. 继承 `BaseMirrorActivity`（它内部继承自 `BaseEventActivity`）
2. 收集 `templeActionViewModel.state` 这个 Flow

---

## 2. TempleAction 事件类型完整列表

| TempleAction 子类 | 触发动作 | 典型使用场景 |
|-------------------|---------|------------|
| `Click` | 单击触控板 | 确认、选中当前焦点项 |
| `DoubleClick` | 快速双击 | **返回上一页**（约定俗成） |
| `LongClick` | 长按不放 | 次要操作、上下文菜单 |
| `TripleClick` | 快速三击 | 特殊快捷操作（如切换模式）|
| `SlideForward` | 向前滑（朝镜片方向）| 下一项/下一页 |
| `SlideBackward` | 向后滑（朝镜腿方向）| 上一项/上一页 |
| `SlideUpwards` | 向上滑 | 垂直导航向上（X3 新增）|
| `SlideDownwards` | 向下滑 | 垂直导航向下（X3 新增）|
| `SlideContinuous` | 持续滑动（跟手）| 列表跟手滚动 |
| `ActionDown` | 手指按下（原始）| 底层事件，一般不直接用 |
| `ActionUp` | 手指抬起（原始）| 底层事件，一般不直接用 |
| `MoveUp` | 移动后抬起 | 底层事件 |
| `DoubleFingerClick` | 双指单击 | 辅助操作（X3 新增）|
| `DoubleFingerLongClick` | 双指长按 | 辅助操作（X3 新增）|
| `Idle` | 无操作状态 | 通常忽略 |

---

## 3. 基础使用：监听所有手势

### 在 BaseMirrorActivity 中

```kotlin
class MyActivity : BaseMirrorActivity<ActivityMyBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectTempleActions()
    }

    private fun collectTempleActions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // templeActionViewModel 由父类 BaseEventActivity 提供，无需手动创建
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.Click -> onUserClick()
                        is TempleAction.DoubleClick -> finish()  // 约定：双击退出
                        is TempleAction.LongClick -> showContextMenu()
                        is TempleAction.SlideForward -> goToNext()
                        is TempleAction.SlideBackward -> goToPrevious()
                        is TempleAction.SlideUpwards -> scrollUp()
                        is TempleAction.SlideDownwards -> scrollDown()
                        else -> Unit  // 忽略其他事件
                    }
                }
            }
        }
    }
}
```

> **为什么用 `repeatOnLifecycle(Lifecycle.State.RESUMED)`？**
> - 只在页面可见时响应事件，避免后台操作
> - 页面 pause 时自动取消收集，resume 时重新开始
> - 避免事件在后台积压后一次性触发

---

## 4. 事件已消费标记（consumed）

当事件被焦点系统或某个组件处理后，会标记为 `consumed = true`。检查这个标记可以避免重复处理：

```kotlin
templeActionViewModel.state.collect { action ->
    if (action.consumed) return@collect  // 事件已被其他组件处理，跳过

    when (action) {
        is TempleAction.Click -> { ... }
        else -> Unit
    }
}
```

---

## 5. 自然模式与非自然模式

用户可在系统设置中切换滑动方向模式，这影响 `SlideForward` 和 `SlideBackward` 对应的物理方向：

| 模式 | 从镜腿→镜片方向滑动 | 从镜片→镜腿方向滑动 |
|------|-------------------|-------------------|
| 自然模式（默认）| `SlideBackward` | `SlideForward` |
| 非自然模式 | `SlideForward` | `SlideBackward` |

**建议**：始终用手势**语义**（"下一项"/"上一项"）来理解，不要依赖物理方向。你的应用应该：
- `SlideForward` → 移动到下一个/前进
- `SlideBackward` → 移动到上一个/后退

---

## 6. X3 新增特性：SlideContinuous 与 FilterMode

### SlideContinuous

X3 支持连续滑动数据（类似手机上的跟手滚动），主要用于列表跟手效果：

```kotlin
// SlideContinuous 携带以下数据：
is TempleAction.SlideContinuous -> {
    val delta = action.delta        // 本次滑动的距离（像素）
    val isVertical = action.vertical // 是垂直方向还是水平方向的滑动
    val longClick = action.longClick // 是否伴随长按
}
```

### FilterMode

通过设置 `filterMode`，可以让 `SlideContinuous` 只返回单一轴向的数据：

```kotlin
// 在 BaseTouchActivity 或相关类中设置：
// FilterMode.OnlyX  → SlideContinuous 只返回 X 轴（水平）滑动数据
// FilterMode.OnlyY  → SlideContinuous 只返回 Y 轴（垂直）滑动数据
// FilterMode.Both   → 默认，返回两个方向
```

---

## 7. 底层 API：CommonTouchCallback

如果你需要更细粒度的控制，可以直接使用 `CommonTouchCallback`（而不是 TempleAction Flow）：

```kotlin
class MyTouchActivity : BaseTouchActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BaseTouchActivity 提供了 touchDispatcher
        touchDispatcher.registerCallback(object : CommonTouchCallback {
            override fun onTPClick(): Boolean {
                // 单击处理
                return true  // true = 消费事件
            }

            override fun onTPDoubleClick(): Boolean {
                finish()
                return true
            }

            override fun onTPSlideForward(args: FlingArgs): Boolean {
                // args 包含滑动速度等信息
                return true
            }

            override fun onTPSlideBackward(args: FlingArgs): Boolean {
                return true
            }

            // X3 新增
            override fun onTPSlideUpwards(args: FlingArgs): Boolean {
                return true
            }

            override fun onTPSlideDownwards(args: FlingArgs): Boolean {
                return true
            }

            override fun onTPDoubleFingerClick() {
                // 双指单击
            }

            override fun onTPDoubleFingerLongClick() {
                // 双指长按
            }

            override fun onTPSlideContinuous(
                delta: Float,
                longClick: Boolean,
                vertical: Boolean  // X3 新增参数
            ) {
                // 连续滑动处理
            }
        })
    }
}
```

> **推荐使用 TempleAction Flow 而非直接 CommonTouchCallback**
> 因为：Flow 支持 Kotlin 协程生命周期绑定，代码更简洁；而 CommonTouchCallback 是传统回调模式，需要手动管理生命周期。

---

## 8. 关键限制

### `BaseEventActivity` 的 final 方法

`BaseEventActivity`（`BaseMirrorActivity` 的父类）将部分手势回调声明为 `final`，**不能 override**：

```kotlin
// 以下方法在 BaseEventActivity 中是 final，不要尝试 override：
// onClick()
// onDoubleClick()
// onLongClick()
// onSlideForward()
// onSlideBackward()
```

这是因为 SDK 需要在这些方法中做统一的事件转换（映射为 TempleAction）。直接 override 会破坏事件流。

**正确做法**：通过收集 `templeActionViewModel.state` 来响应事件，而不是 override 这些方法。

---

## 9. X2 迁移到 X3

如果你有 X2 的应用需要迁移到 X3：

```kotlin
// X3 SDK 提供了设备判断函数
import com.ffalcon.mercury.android.sdk.util.DeviceUtil

if (DeviceUtil.isX3Device()) {
    // 在 X3 上的特有逻辑
    // 例如：使用上滑/下滑手势
} else {
    // X2 上的逻辑（向后兼容）
}
```

X3 新增的手势（上滑、下滑、双指操作）在 X2 上不会触发，代码本身是兼容的，只是功能不可用。

---

## 下一步

了解手势输入后，学习如何用焦点系统将手势与 UI 元素绑定：

- [`03-focus-management.md`](./03-focus-management.md)：焦点系统详解

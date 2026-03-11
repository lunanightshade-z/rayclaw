# 焦点系统：无触屏 UI 导航的基础

AR 眼镜没有触摸屏，用户无法直接"点击"某个 View。焦点系统解决了这个问题：通过**前滑/后滑手势**在可交互元素间移动"焦点"，通过**单击**触发当前焦点元素的操作。

---

## 1. 焦点系统概念模型

```
┌─────────────────────────────────────────┐
│              AR 界面                     │
│                                         │
│  [按钮 A] ←──焦点──→ [按钮 B]  [按钮 C] │
│       ↑                                 │
│  当前焦点（高亮显示）                    │
│                                         │
│  用户 SlideForward → 焦点移到 [按钮 B]   │
│  用户 Click → 触发 [按钮 B] 的操作       │
└─────────────────────────────────────────┘
```

焦点系统的核心组件：

| 组件 | 职责 |
|------|------|
| `FocusHolder` | 管理"当前焦点是谁"，维护可聚焦元素列表 |
| `FocusInfo` | 描述一个可聚焦元素（目标 View + 事件处理器 + 焦点变化处理器）|
| `FixPosFocusTracker` | 将 TempleAction 手势翻译为焦点切换操作 |
| `IFocusable` | 接口，让自定义 View 也能参与焦点系统 |

---

## 2. 基础焦点系统搭建

### 步骤一：创建 FocusHolder

```kotlin
// loop: true = 焦点循环（从最后一个可以回到第一个）
// loop: false = 焦点到头停止（推荐，与系统 TV 导航习惯一致）
val focusHolder = FocusHolder(loop = false)
```

### 步骤二：定义可聚焦元素（FocusInfo）

```kotlin
// 在 setLeft 中操作，因为 FocusInfo 需要绑定到具体 View
mBindingPair.setLeft {

    val btnAInfo = FocusInfo(
        // 目标 View（必须是左屏中的 View）
        target = btnA,

        // 当这个元素持有焦点时，用户操作镜腿的事件处理
        eventHandler = { action ->
            when (action) {
                is TempleAction.Click -> {
                    FToast.show("按钮 A 被点击")
                    doSomethingA()
                }
                else -> Unit
            }
        },

        // 焦点变化时的视觉反馈处理
        focusChangeHandler = { hasFocus ->
            // 焦点变化时需要同步更新两屏的视觉效果
            mBindingPair.updateView {
                btnA.setBackgroundColor(
                    getColor(
                        if (hasFocus) R.color.highlight_color
                        else R.color.normal_color
                    )
                )
            }
        }
    )

    val btnBInfo = FocusInfo(
        target = btnB,
        eventHandler = { action ->
            when (action) {
                is TempleAction.Click -> FToast.show("按钮 B 被点击")
                else -> Unit
            }
        },
        focusChangeHandler = { hasFocus ->
            mBindingPair.updateView {
                btnB.setBackgroundColor(
                    getColor(if (hasFocus) R.color.highlight_color else R.color.normal_color)
                )
            }
        }
    )

    // 注册所有可聚焦元素到 FocusHolder
    focusHolder.addFocusTarget(btnAInfo, btnBInfo)

    // 设置默认焦点（哪个元素一开始获得焦点）
    focusHolder.currentFocus(mBindingPair.left.btnA)
}
```

### 步骤三：创建 FixPosFocusTracker 并激活

```kotlin
val focusTracker = FixPosFocusTracker(focusHolder).apply {
    // 必须调用 reqFocus()，否则 tracker 不会响应任何事件
    focusObj.reqFocus()
}
```

### 步骤四：将手势事件接入焦点系统

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.RESUMED) {
        templeActionViewModel.state.collect { action ->
            when (action) {
                is TempleAction.DoubleClick -> finish()  // 双击退出
                else -> focusTracker.handleFocusTargetEvent(action)  // 其他事件交给焦点系统
            }
        }
    }
}
```

这样，当用户：
- 前滑/后滑 → 焦点在 btnA 和 btnB 之间移动（视觉高亮变化）
- 单击 → 当前焦点元素的 `eventHandler` 被调用
- 双击 → Activity 退出

---

## 3. FixPosFocusTracker 配置详解

`FixPosFocusTracker` 的构造函数有多个参数可以调整交互手感：

```kotlin
val focusTracker = FixPosFocusTracker(
    focusHolder = focusHolder,

    // continuous: 是否连续切换焦点
    // false（默认）= 每次滑动只切换一个焦点（单步模式）
    // true = 根据滑动总距离切换多个焦点（连续模式）
    continuous = false,

    // isVertical: 接受哪个方向的滑动来切换焦点
    // true（默认）= 只接受垂直方向（上下滑）
    // false = 只接受水平方向（前后滑）
    isVertical = true,

    // ignoreDelta: 触发焦点切换所需的最小滑动距离（单位：dp）
    // 默认 50dp，防止微小抖动触发焦点切换
    ignoreDelta = 50
)
```

**建议**：使用默认值，与 RayNeo 官方应用的交互手感保持一致，减少用户学习成本。

---

## 4. FocusHolder 配置

```kotlin
// loop: 焦点是否循环
// false（默认）: 第一个元素的 previous、最后一个元素的 next 不生效
// true: 最后一个的 next 跳到第一个，第一个的 previous 跳到最后一个
val focusHolder = FocusHolder(loop = true)
```

---

## 5. 动态焦点：运行时添加/移除可聚焦元素

有时需要在运行时动态添加可交互元素（如 AI 生成的操作项）：

```kotlin
// 使用扩展函数 addFocusView
var focusHandle: FocusViewHandle<View>? = null

focusHandle = mBindingPair.addFocusView(
    // 父容器（在左屏中的 ViewGroup）
    parent = mBindingPair.left.llContainer,

    // 动态创建 View 的工厂
    viewFactory = {
        Button(this@MyActivity).apply {
            text = "动态按钮"
            background = null
        }
    },

    // 关联的 FocusHolder
    focusHolder = focusHolder,

    // 焦点配置
    focusConfig = {
        // 布局参数
        layoutParamsFactory = { parent, view ->
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dp, 8.dp, 16.dp, 8.dp)
            }
        }

        // 事件处理
        eventHandler = { action ->
            when (action) {
                is TempleAction.Click -> FToast.show("动态按钮被点击！")
                else -> Unit
            }
        }

        // 焦点变化
        onFocusChange = { view, hasFocus, isLeft ->
            view.setBackgroundColor(
                getColor(if (hasFocus) R.color.highlight else R.color.normal)
            )
        }
    }
)

// 移除动态焦点
focusHandle?.clearFocusView()
```

---

## 6. 嵌套焦点：复杂页面的层级导航

对于复杂页面（如设置页有多个分类，每个分类内有多个选项），可以用**嵌套 FocusHolder** 实现层级焦点：

```kotlin
// 外层焦点：在不同分类间切换
val outerFocusHolder = FocusHolder(loop = false)

// 内层焦点：在某个分类的选项内切换
val innerFocusHolder = FocusHolder(loop = false)
val innerTracker = FixPosFocusTracker(innerFocusHolder)

// 将内层 tracker 作为外层焦点的一个目标
outerFocusHolder.addFocusTarget(
    FocusInfo(
        target = innerTracker.focusObj,  // FixPosFocusTracker 自身实现了 IFocusable
        eventHandler = { action ->
            innerTracker.handleFocusTargetEvent(action)
        },
        focusChangeHandler = { hasFocus ->
            // 整个分类获得/失去焦点时的视觉反馈
            mBindingPair.updateView {
                cardCategory.strokeWidth = if (hasFocus) 4.dp else 0
            }
        }
    )
)
```

---

## 7. RecyclerView 中的焦点

RecyclerView 有特殊的焦点处理，详见 `04-ui-components/05-recyclerview-navigation.md`。

简单来说，SDK 提供两个工具：
- `RecyclerViewSlidingTracker`：固定焦点位置，列表跟手滚动
- `RecyclerViewFocusTracker`：焦点跟随列表项移动

---

## 8. 通用焦点激活/释放 API

使用 `IFocusable` 接口的扩展函数来控制焦点状态：

```kotlin
import com.ffalcon.mercury.android.sdk.focus.IFocusableKt.reqFocus
import com.ffalcon.mercury.android.sdk.focus.IFocusableKt.releaseFocus

// 激活焦点（开始响应事件）
focusTracker.focusObj.reqFocus()

// 释放焦点（暂停响应，但保留焦点状态）
focusTracker.focusObj.releaseFocus()
```

---

## 9. 完整代码示例

```kotlin
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.touch.TempleAction

class FocusMenuActivity : BaseMirrorActivity<ActivityFocusMenuBinding>() {

    private var focusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFocusSystem()
        collectEvents()
    }

    private fun setupFocusSystem() {
        val focusHolder = FocusHolder(loop = false)

        mBindingPair.setLeft {
            focusHolder.addFocusTarget(
                FocusInfo(
                    target = btnOption1,
                    eventHandler = { action ->
                        if (action is TempleAction.Click) FToast.show("选项1")
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            btnOption1.isSelected = hasFocus
                        }
                    }
                ),
                FocusInfo(
                    target = btnOption2,
                    eventHandler = { action ->
                        if (action is TempleAction.Click) FToast.show("选项2")
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            btnOption2.isSelected = hasFocus
                        }
                    }
                )
            )
            focusHolder.currentFocus(mBindingPair.left.btnOption1)
        }

        focusTracker = FixPosFocusTracker(focusHolder).apply {
            focusObj.reqFocus()
        }
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> finish()
                        else -> focusTracker?.handleFocusTargetEvent(action)
                    }
                }
            }
        }
    }
}
```

---

## 下一步

- [`04-3d-parallax-effects.md`](./04-3d-parallax-effects.md)：为焦点元素添加 3D 视差效果

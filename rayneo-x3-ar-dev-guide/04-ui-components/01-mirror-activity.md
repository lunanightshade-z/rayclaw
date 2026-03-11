# BaseMirrorActivity 完整指南

`BaseMirrorActivity` 是开发 AR 应用最常用的基类，它封装了所有双屏合目、镜腿事件到 TempleAction 转换的基础逻辑。本文详细说明其用法和所有可用的 API。

---

## 1. 类继承关系

```
Activity
└── BaseTouchActivity          ← 注册 TouchDispatcher，处理原始 MotionEvent
    └── BaseEventActivity      ← 将手势转为 TempleAction Flow
        └── BaseMirrorActivity<B>  ← 合目布局自动生成（你继承这个）
```

继承 `BaseMirrorActivity<B>` 后，你自动获得：
- `mBindingPair: BindingPair<B>` —— 左右屏幕的 ViewBinding 对
- `templeActionViewModel: TempleActionViewModel` —— 镜腿手势事件 Flow
- `motionEventDispatcher: MotionEventDispatcher` —— 原始 MotionEvent 分发器（高级用法）

---

## 2. 基础用法

### 最简示例

```kotlin
// 布局文件：res/layout/activity_demo.xml
// ViewBinding 类：ActivityDemoBinding（自动生成）

class DemoActivity : BaseMirrorActivity<ActivityDemoBinding>() {
    // 不需要 override setContentView()
    // 不需要 override inflateBinding()
    // BaseMirrorActivity 自动完成双屏布局

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通过 mBindingPair 访问左右屏
        mBindingPair.updateView {
            tvTitle.text = "我的 AR 应用"
        }
    }
}
```

### ViewBinding 类名规则

布局文件名 → ViewBinding 类名：
- `activity_demo.xml` → `ActivityDemoBinding`
- `activity_my_home.xml` → `ActivityMyHomeBinding`
- `activity_camera_preview.xml` → `ActivityCameraPreviewBinding`

---

## 3. mBindingPair 完整 API

### 3.1 updateView {} —— 同步更新两屏

```kotlin
mBindingPair.updateView {
    // this = 当前屏的 ViewBinding（左或右，代码执行两次）
    tvStatus.text = "已连接"
    btnAction.isEnabled = true
    ivIcon.visibility = View.VISIBLE
}
```

**使用场景**：更新纯 UI 属性（文字、颜色、可见性、图片等）

**禁止在其中**：修改外部变量、注册监听器、发网络请求

### 3.2 setLeft {} —— 只操作左屏

```kotlin
mBindingPair.setLeft {
    // this = 左屏的 ViewBinding（只执行一次）

    // 适合：事件绑定、Adapter 设置、只需执行一次的逻辑
    recyclerView.adapter = myAdapter
    btnConfirm.setOnClickListener { handleConfirm() }
}
```

**使用场景**：事件监听、Adapter、一次性初始化

### 3.3 checkIsLeft() —— 在 updateView 中区分左右

```kotlin
mBindingPair.updateView {
    val isLeft = mBindingPair.checkIsLeft(this)

    // 根据左右屏分别处理
    if (isLeft) {
        // 左屏特有处理
    } else {
        // 右屏特有处理
    }
}
```

### 3.4 直接访问 left / right

```kotlin
// 直接操作左屏
mBindingPair.left.tvTitle.text = "直接操作左屏"

// 直接操作右屏
mBindingPair.right.tvTitle.text = "直接操作右屏"
```

---

## 4. 镜腿事件 API

`BaseMirrorActivity` 继承自 `BaseEventActivity`，提供了 `templeActionViewModel`：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            templeActionViewModel.state.collect { action ->
                handleTempleAction(action)
            }
        }
    }
}

private fun handleTempleAction(action: TempleAction) {
    when (action) {
        is TempleAction.Click -> { /* 单击 */ }
        is TempleAction.DoubleClick -> finish()
        is TempleAction.LongClick -> { /* 长按 */ }
        is TempleAction.SlideForward -> { /* 前滑 */ }
        is TempleAction.SlideBackward -> { /* 后滑 */ }
        is TempleAction.SlideUpwards -> { /* 上滑（X3）*/ }
        is TempleAction.SlideDownwards -> { /* 下滑（X3）*/ }
        else -> Unit
    }
}
```

---

## 5. 生命周期最佳实践

### 5.1 在正确的生命周期阶段操作 UI

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ✅ 初始化：设置初始 UI 状态
    setupInitialUI()
    // ✅ 初始化：注册事件监听
    setupEvents()
}

override fun onResume() {
    super.onResume()
    // ✅ 可见时：启动实时数据刷新
    startDataRefresh()
    // ✅ 可见时：注册传感器（如果有）
}

override fun onPause() {
    super.onPause()
    // ✅ 不可见时：停止数据刷新（节省电量）
    stopDataRefresh()
    // ✅ 不可见时：注销传感器（节省电量）
}

override fun onDestroy() {
    super.onDestroy()
    // ✅ 销毁时：释放所有资源（Camera、Player、连接等）
    releaseResources()
}
```

### 5.2 协程生命周期绑定

```kotlin
// ✅ 推荐：与生命周期绑定的协程
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.RESUMED) {
        // 只在 RESUMED 状态下收集，页面不可见时自动取消
        templeActionViewModel.state.collect { ... }
    }
}

// ✅ 也可以用 launchWhenResumed（旧 API，功能类似）
lifecycleScope.launchWhenResumed {
    templeActionViewModel.state.collect { ... }
}
```

---

## 6. 自定义 Activity 模板

以下是一个完整的生产级 Activity 模板，包含所有常见配置：

```kotlin
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.BindingPairKt.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.dialog.FDialog
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.ffalcon.mercury.android.sdk.util.FLogger
import kotlinx.coroutines.launch

class TemplateActivity : BaseMirrorActivity<ActivityTemplateBinding>() {

    private val TAG = "TemplateActivity"
    private var focusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FLogger.i(TAG, "onCreate")
        setupUI()
        setupFocusSystem()
        collectEvents()
    }

    private fun setupUI() {
        mBindingPair.updateView {
            tvTitle.text = "页面标题"
        }
    }

    private fun setupFocusSystem() {
        val focusHolder = FocusHolder(loop = false)

        mBindingPair.setLeft {
            focusHolder.addFocusTarget(
                FocusInfo(
                    target = btnPrimary,
                    eventHandler = { action ->
                        if (action is TempleAction.Click) onPrimaryAction()
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            val isLeft = mBindingPair.checkIsLeft(this)
                            btnPrimary.isSelected = hasFocus
                            make3DEffectForSide(btnPrimary, isLeft, hasFocus)
                        }
                    }
                )
            )
            focusHolder.currentFocus(mBindingPair.left.btnPrimary)
        }

        focusTracker = FixPosFocusTracker(focusHolder).apply {
            focusObj.reqFocus()
        }
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    FLogger.d(TAG, "TempleAction: $action")
                    when (action) {
                        is TempleAction.DoubleClick -> finish()
                        else -> focusTracker?.handleFocusTargetEvent(action)
                    }
                }
            }
        }
    }

    private fun onPrimaryAction() {
        FToast.show("执行主操作")
    }

    override fun onDestroy() {
        super.onDestroy()
        FLogger.i(TAG, "onDestroy")
    }
}
```

---

## 7. 常见错误

### 错误 1：在 updateView 中注册监听器

```kotlin
// ❌ 错误：监听器会被注册两次，回调触发两次
mBindingPair.updateView {
    btnConfirm.setOnClickListener { doSomething() }  // 注册两次！
}

// ✅ 正确：用 setLeft
mBindingPair.setLeft {
    btnConfirm.setOnClickListener { doSomething() }  // 只注册一次
}
```

### 错误 2：在 updateView 中修改外部状态

```kotlin
var count = 0

// ❌ 错误：count 会被加两次（左屏一次，右屏一次）
mBindingPair.updateView {
    count++       // 执行两次！
    tvCount.text = count.toString()
}

// ✅ 正确：先修改状态，再更新 UI
count++
mBindingPair.updateView {
    tvCount.text = count.toString()
}
```

### 错误 3：忘记 `super.onCreate()`

```kotlin
// ❌ 忘记 super.onCreate()，BaseMirrorActivity 的初始化逻辑不会执行
override fun onCreate(savedInstanceState: Bundle?) {
    setupUI()  // 这时 mBindingPair 还是 null，会崩溃！
}

// ✅ 必须先调用 super
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)  // ← 必须第一个调用
    setupUI()
}
```

---

## 下一步

- [`02-mirror-fragment.md`](./02-mirror-fragment.md)：Fragment 级别的合目封装
- [`04-toast-dialog.md`](./04-toast-dialog.md)：AR 专用 Toast 和 Dialog

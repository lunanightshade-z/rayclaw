# 合目渲染：双屏同步的核心原理

合目（Fusion Vision）是 AR 眼镜开发中最基础也最核心的概念。本文深入解释它的原理，以及 SDK 如何帮你解决它。

---

## 1. 为什么需要合目

### 物理原理

X3 眼镜有两块物理显示屏，Android 系统将它们合并为**一块逻辑屏幕**：

```
逻辑屏幕（系统视角）
┌──────────────────────────────────────┐
│                                      │  总宽度 = 左屏宽 + 右屏宽
│      你的 App 内容在这里渲染          │
│                                      │
└──────────────────────────────────────┘
         ↓ 系统分割 ↓
┌─────────────────┬────────────────────┐
│    左眼显示屏    │    右眼显示屏      │
│  (→ 左眼看到)   │  (→ 右眼看到)     │
└─────────────────┴────────────────────┘
```

### 撕裂问题

如果你的 UI 居中，系统把逻辑屏幕一分为二后：
- 左眼看到 UI 的右半边
- 右眼看到 UI 的左半边

两眼看到不同内容，大脑无法融合，产生严重的**撕裂感**，内容完全无法辨认。

### 合目方案

正确做法：**同一份 UI 放两份**，左眼区域一份、右眼区域一份：

```
逻辑屏幕
┌──────────────────┬──────────────────┐
│   左眼内容（完整）│  右眼内容（完整）│
│  ┌────────────┐  │  ┌────────────┐  │
│  │  Hello AR  │  │  │  Hello AR  │  │
│  └────────────┘  │  └────────────┘  │
└──────────────────┴──────────────────┘
    左眼看到              右眼看到
         ↓ 大脑双目融合
         ┌────────────┐
         │  Hello AR  │   清晰单一画面
         └────────────┘
```

---

## 2. SDK 的解决方案：BindingPair

手动维护两份布局意味着：每次更新 UI，需要对左右两份布局各操作一次——代码量翻倍，且容易出现不同步问题。

SDK 提供了 `BindingPair<T>` 来解决这个问题：

```
BindingPair<ActivityMainBinding>
├── left: ActivityMainBinding   ← 左屏绑定
└── right: ActivityMainBinding  ← 右屏绑定
```

通过 `BindingPair`，你只需操作一次，SDK 自动同步到两个屏幕。

---

## 3. 核心 API 详解

### 3.1 `updateView {}` —— 同时更新左右屏

```kotlin
// 这一行代码 = 同时更新左右两份布局中的 tvTitle
mBindingPair.updateView {
    tvTitle.text = "我的AR应用"
    tvSubtitle.textColor = Color.WHITE
}

// 等价于（不要这样写，太繁琐）：
mBindingPair.left.tvTitle.text = "我的AR应用"
mBindingPair.right.tvTitle.text = "我的AR应用"
mBindingPair.left.tvSubtitle.textColor = Color.WHITE
mBindingPair.right.tvSubtitle.textColor = Color.WHITE
```

**重要注意事项**：`updateView {}` 中的代码块会执行两次（左一次、右一次）。因此：
- **只做 UI 操作**（设置文字、颜色、可见性等）
- **不要在其中修改外部状态变量**（会被执行两次）
- **不要在其中绑定事件监听器**（监听器会注册两次，回调触发两次）

### 3.2 `setLeft {}` —— 只操作左屏（事件绑定推荐用法）

```kotlin
mBindingPair.setLeft {
    // 这里的代码只在左屏 ViewBinding 上执行一次
    // 适合：绑定事件监听器、设置 Adapter、处理单侧逻辑

    btnConfirm.setOnClickListener {
        // 点击确认按钮的逻辑
    }
}
```

**为什么事件绑定要用 `setLeft`？**
- 因为实际触摸事件分发到逻辑屏幕后，SDK 内部会以左屏为"主控"来处理事件
- 如果用 `updateView` 绑定，会注册两个监听器，事件触发两次

### 3.3 `checkIsLeft {}` —— 在 updateView 中区分左右

有时你需要在同步操作中对左右屏做微小差异处理（例如 3D 视差效果）：

```kotlin
mBindingPair.updateView {
    // 这个 lambda 会被执行两次：一次 this = left binding, 一次 this = right binding
    val isLeft = mBindingPair.checkIsLeft(this)

    // 根据是否是左屏，对 view 做差异化处理
    make3DEffectForSide(ivIcon, isLeft, hasFocus = true)
}
```

### 3.4 直接访问左右 Binding

有时你需要直接操作某一侧：

```kotlin
// 直接访问左侧 binding
val leftText = mBindingPair.left.tvTitle

// 直接访问右侧 binding
val rightText = mBindingPair.right.tvTitle
```

---

## 4. 合目组件层级

SDK 提供了不同层级的合目封装，根据业务场景选择合适的层级：

```
Activity 级别（最常用）
└── BaseMirrorActivity<B>
    - 自动创建 mBindingPair
    - 继承后直接使用 mBindingPair

Fragment 级别（嵌套场景）
└── BaseMirrorFragment<B, H>
    - 不能添加到 BaseMirrorActivity 中（避免嵌套渲染冲突）
    - 用于独立的 Fragment 合目场景

View 级别（局部合目）
├── MirrorContainerView    ← 组合方式，需要调用 bindTo()
└── BaseMirrorContainerView<B>  ← 继承方式，实现自定义 MirrorView

特殊场景
└── MirroringView          ← SurfaceView/TextureView 视频内容镜像
```

---

## 5. 实际使用场景示例

### 场景 A：Activity 中更新文字

```kotlin
class StatusActivity : BaseMirrorActivity<ActivityStatusBinding>() {

    private fun updateStatus(message: String) {
        mBindingPair.updateView {
            tvStatus.text = message
            tvStatus.visibility = View.VISIBLE
        }
    }
}
```

### 场景 B：Activity 中绑定按钮事件

```kotlin
class MenuActivity : BaseMirrorActivity<ActivityMenuBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupButtons()
    }

    private fun setupButtons() {
        // 事件绑定用 setLeft，只绑定一次
        mBindingPair.setLeft {
            btnStart.setOnClickListener {
                startActivity(Intent(this@MenuActivity, NextActivity::class.java))
            }
        }

        // 初始视觉状态用 updateView，同步两屏
        mBindingPair.updateView {
            btnStart.text = "开始"
            btnStart.setBackgroundColor(
                getColor(com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_0)
            )
        }
    }
}
```

### 场景 C：根据数据更新列表项

```kotlin
private fun bindData(items: List<String>) {
    mBindingPair.updateView {
        // RecyclerView 的 adapter 设置建议放在 setLeft 中
        // 但是 visibility 控制可以放 updateView
        recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // adapter 只需要设置一次，放 setLeft
    mBindingPair.setLeft {
        recyclerView.adapter = MyAdapter(items)
    }
}
```

---

## 6. 视频合目（SurfaceView / TextureView）

普通 UI 合目用 `mBindingPair.updateView {}` 就够，但**视频播放**因为用了 `SurfaceView` 或 `TextureView`，无法通过 ViewBinding 直接镜像，需要使用 `MirroringView`。

### 布局设计

```xml
<!-- 左屏区域放原始 TextureView -->
<TextureView
    android:id="@+id/textureView"
    android:layout_width="..."
    android:layout_height="..." />

<!-- 右屏区域放 MirroringView（用于镜像左屏内容） -->
<com.ffalcon.mercury.android.sdk.ui.wiget.MirroringView
    android:id="@+id/mirrorView"
    android:layout_width="..."
    android:layout_height="..." />
```

### 代码实现

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 将左屏 TextureView 设为 MirroringView 的源
    mBindingPair.left.textureView.let { leftTexture ->
        mBindingPair.right.mirrorView.setSource(leftTexture)
        mBindingPair.right.mirrorView.startMirroring()
    }

    // 正常播放视频到 textureView
    setupPlayer()
}

override fun onDestroy() {
    super.onDestroy()
    // 必须停止 mirroring，释放资源
    mBindingPair.right.mirrorView.stopMirroring()
    mPlayer?.release()
}
```

---

## 7. 安全边距

为避免双目边缘区域的光学不适，所有核心 UI 内容应在安全边距内：

```xml
<!-- 推荐：使用 SDK 预定义的安全边距 -->
<ConstraintLayout
    android:paddingHorizontal="@dimen/rayneo_safety_padding_horizontal"
    android:paddingVertical="@dimen/rayneo_safety_padding_vertical"
    ... >

    <!-- 你的 UI 内容 -->

</ConstraintLayout>
```

安全边距值：
- 水平：`rayneo_safety_padding_horizontal = 30sp`
- 垂直：`rayneo_safety_padding_vertical = 20dp`

---

## 下一步

- [`02-temple-input-events.md`](./02-temple-input-events.md)：了解镜腿手势事件系统

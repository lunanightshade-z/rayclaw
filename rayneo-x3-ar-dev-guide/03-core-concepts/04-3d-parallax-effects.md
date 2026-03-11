# 3D 视差效果：让内容"悬浮"在眼前

AR 眼镜最令人印象深刻的体验之一，是内容在视觉上"悬浮"在空间中，而不是平贴在屏幕上。SDK 提供了基于双目视差的 3D 效果实现，通过简单的 API 就能让你的 UI 元素产生景深感。

---

## 1. 3D 视差的基本原理

人眼感知深度，是因为左右眼从略微不同的角度看同一物体（双目视差）：

```
真实世界中悬浮的物体：
  左眼看到 → [物体]  偏右一点
  右眼看到 → [物体]  偏左一点
  大脑融合 → 感知到物体"在前方悬浮"

AR 眼镜模拟：
  左屏 → [UI元素] 向右偏移 N 像素
  右屏 → [UI元素] 向左偏移 N 像素
  大脑融合 → 感知到 UI 元素"悬浮在空中"
```

偏移量越大，感知到的"距离越近"（更凸出）；偏移量越小，感知越"平贴"。

---

## 2. SDK 提供的 API

### `make3DEffect()` —— 同时设置左右两个 View

```kotlin
// 函数签名：
fun make3DEffect(
    leftView: View,    // 左屏中的 View
    rightView: View,   // 右屏中的 View
    enable: Boolean,   // 是否启用 3D 效果
    delta: Float = 10f // 偏移量（正值）
)

// 使用示例：
make3DEffect(
    left.ivIcon,       // 左屏的图标
    right.ivIcon,      // 右屏的图标
    enable = true,
    delta = 10f        // 偏移 10px，产生中等景深
)
```

### `make3DEffectForSide()` —— 单侧设置（在 updateView 中使用）

```kotlin
// 函数签名：
fun make3DEffectForSide(
    view: View,        // 当前屏（左或右）的 View
    isLeft: Boolean,   // 是否是左屏
    enable: Boolean,   // 是否启用 3D 效果
    delta: Float = 10f // 偏移量
)

// 在 updateView 中使用（推荐）：
mBindingPair.updateView {
    val isLeft = mBindingPair.checkIsLeft(this)
    make3DEffectForSide(ivIcon, isLeft, enable = true, delta = 10f)
}
```

---

## 3. 典型使用场景

### 场景 A：焦点高亮时触发 3D 效果

这是最常见的用法：当某个元素获得焦点时，添加 3D 效果；失去焦点时，恢复平面。

```kotlin
FocusInfo(
    target = btnCard,
    focusChangeHandler = { hasFocus ->
        mBindingPair.updateView {
            val isLeft = mBindingPair.checkIsLeft(this)
            // 获得焦点 → 启用 3D；失去焦点 → 关闭 3D
            make3DEffectForSide(btnCard, isLeft, enable = hasFocus)
        }
    }
)
```

### 场景 B：静态 3D 效果（某个元素始终有景深）

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 页面加载后，标题图标始终有 3D 效果
    mBindingPair.updateView {
        val isLeft = mBindingPair.checkIsLeft(this)
        make3DEffectForSide(ivLogo, isLeft, enable = true, delta = 8f)
    }
}
```

### 场景 C：通过 delta 值模拟不同深度层次

```kotlin
// 不同元素使用不同 delta，模拟前后深度层次
mBindingPair.updateView {
    val isLeft = mBindingPair.checkIsLeft(this)

    // 背景元素（距离远，偏移小）
    make3DEffectForSide(viewBackground, isLeft, true, delta = 3f)

    // 内容元素（中间层）
    make3DEffectForSide(cardContent, isLeft, true, delta = 8f)

    // 焦点高亮元素（最近，偏移大）
    make3DEffectForSide(btnPrimary, isLeft, true, delta = 15f)
}
```

---

## 4. delta 参数调优指南

| delta 值 | 视觉效果 | 适用场景 |
|---------|---------|---------|
| 0 | 无效果，平面显示 | 背景、装饰元素 |
| 3-5 | 轻微景深，隐约立体 | 卡片背景层 |
| 8-12 | 明显立体，推荐默认值 | 主要内容卡片 |
| 15-20 | 强烈凸出，非常显眼 | 焦点高亮元素 |
| > 20 | 可能过于夸张，易疲劳 | 慎用 |

> **从 `delta = 10f` 开始**，在实际眼镜上佩戴测试，根据视觉效果调整。不同尺寸的元素最佳 delta 值不同。

---

## 5. 注意事项

### 不要对所有元素都加 3D 效果
过多的 3D 效果会造成视觉混乱，反而降低体验。建议：
- 只对焦点元素或主要内容元素使用
- 背景和装饰元素保持平面

### 3D 效果会修改 View 的 translationX
`make3DEffect` 通过设置 `view.translationX` 来实现偏移。如果你的 View 已经有其他动画在修改 `translationX`，可能会冲突。

### 必须区分左右屏
`make3DEffect` 对左屏向右偏移、右屏向左偏移。如果搞反了（左屏向左偏移），反而会产生"凹陷"感而不是"凸出"感，效果相反。

---

## 完整示例：带 3D 效果的焦点卡片

```kotlin
class CardActivity : BaseMirrorActivity<ActivityCardBinding>() {

    private var focusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupCards()
        collectEvents()
    }

    private fun setupCards() {
        val focusHolder = FocusHolder(loop = true)

        mBindingPair.setLeft {
            listOf(card1, card2, card3).forEachIndexed { index, card ->
                focusHolder.addFocusTarget(
                    FocusInfo(
                        target = card,
                        eventHandler = { action ->
                            if (action is TempleAction.Click) {
                                FToast.show("卡片 ${index + 1} 被选中")
                            }
                        },
                        focusChangeHandler = { hasFocus ->
                            // 同时更新视觉和 3D 效果
                            mBindingPair.updateView {
                                val isLeft = mBindingPair.checkIsLeft(this)
                                val currentCard = listOf(card1, card2, card3)[index]

                                // 背景颜色变化
                                currentCard.setCardBackgroundColor(
                                    getColor(
                                        if (hasFocus) R.color.card_focused
                                        else R.color.card_normal
                                    )
                                )

                                // 3D 效果（聚焦时凸出，失焦时平面）
                                make3DEffectForSide(
                                    view = currentCard,
                                    isLeft = isLeft,
                                    enable = hasFocus,
                                    delta = 12f
                                )
                            }
                        }
                    )
                )
            }
            focusHolder.currentFocus(mBindingPair.left.card1)
        }

        focusTracker = FixPosFocusTracker(
            focusHolder,
            continuous = false,
            isVertical = false  // 横向卡片用水平滑动切换
        ).apply {
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

核心概念学习完成，进入 UI 组件详细文档：

- [`../04-ui-components/01-mirror-activity.md`](../04-ui-components/01-mirror-activity.md)：BaseMirrorActivity 完整指南

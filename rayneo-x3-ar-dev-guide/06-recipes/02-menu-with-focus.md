# Recipe 2：带焦点导航的 AR 菜单

本 Recipe 构建一个可通过镜腿手势操作的 AR 功能菜单，包含焦点高亮、3D 视差效果和手势确认。

---

## 目标效果

- 4 个功能选项卡片排列
- 前滑/后滑切换焦点（高亮 + 3D 凸出效果）
- 单击选中当前焦点项（导航到对应功能）
- 双击返回/退出

---

## 布局文件：activity_menu.xml

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

    <!-- 页面标题 -->
    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="功能菜单"
        android:textColor="#FFFFFF"
        android:textSize="28sp"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- 操作提示 -->
    <TextView
        android:id="@+id/tvHint"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="滑动选择 · 单击确认 · 双击退出"
        android:textColor="#60FFFFFF"
        android:textSize="14sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- 菜单卡片容器（水平排列） -->
    <LinearLayout
        android:id="@+id/llMenuContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="20dp"
        android:gravity="center"
        android:orientation="horizontal"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/tvTitle">

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardCamera"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:layout_margin="8dp"
            app:cardBackgroundColor="#1A1A2E"
            app:cardCornerRadius="12dp"
            app:strokeColor="#333366"
            app:strokeWidth="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:orientation="vertical">

                <ImageView
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:src="@drawable/ic_camera"
                    android:tint="#4FC3F7" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="相机"
                    android:textColor="#FFFFFF"
                    android:textSize="16sp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- 重复上述结构，创建其余 3 个卡片：cardIMU, cardList, cardSettings -->
        <!-- 简化起见，这里仅展示结构，实际实现请参考完整代码 -->

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## Activity 实现：MenuActivity.kt

```kotlin
package com.example.myarapp

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.BindingPairKt.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.example.myarapp.databinding.ActivityMenuBinding
import kotlinx.coroutines.launch

class MenuActivity : BaseMirrorActivity<ActivityMenuBinding>() {

    // 菜单项定义
    private data class MenuItem(
        val label: String,
        val targetClass: Class<*>?
    )

    private val menuItems = listOf(
        MenuItem("相机", CameraActivity::class.java),
        MenuItem("IMU", IMUActivity::class.java),
        MenuItem("列表", ListActivity::class.java),
        MenuItem("设置", null)  // null = 暂未实现
    )

    private var focusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupMenuUI()
        setupFocusSystem()
        collectEvents()
    }

    private fun setupMenuUI() {
        mBindingPair.updateView {
            tvTitle.text = "功能菜单"
        }
    }

    private fun setupFocusSystem() {
        val focusHolder = FocusHolder(loop = true)  // 循环焦点，最后一个后回到第一个

        // 将 4 个卡片 View 列表化，便于统一处理
        val cardViews = listOf(
            mBindingPair.left.cardCamera,
            mBindingPair.left.cardIMU,
            mBindingPair.left.cardList,
            mBindingPair.left.cardSettings
        )

        mBindingPair.setLeft {
            menuItems.forEachIndexed { index, item ->
                val cardView = cardViews[index]

                focusHolder.addFocusTarget(
                    FocusInfo(
                        target = cardView,

                        // 单击：导航到对应功能
                        eventHandler = { action ->
                            when (action) {
                                is TempleAction.Click -> navigateToFeature(item)
                                else -> Unit
                            }
                        },

                        // 焦点变化：更新视觉效果
                        focusChangeHandler = { hasFocus ->
                            updateCardFocusState(index, hasFocus)
                        }
                    )
                )
            }

            // 默认选中第一个卡片
            focusHolder.currentFocus(mBindingPair.left.cardCamera)
        }

        // 水平滑动切换焦点（isVertical = false）
        focusTracker = FixPosFocusTracker(
            focusHolder = focusHolder,
            isVertical = false,  // 卡片是横向排列的，用水平滑动切换
            continuous = false,
            ignoreDelta = 50
        ).apply {
            focusObj.reqFocus()
        }
    }

    private fun updateCardFocusState(index: Int, hasFocus: Boolean) {
        mBindingPair.updateView {
            val isLeft = mBindingPair.checkIsLeft(this)

            // 根据 index 获取当前屏的对应卡片 View
            val card = when (index) {
                0 -> cardCamera
                1 -> cardIMU
                2 -> cardList
                3 -> cardSettings
                else -> return@updateView
            }

            // 焦点视觉：边框高亮
            (card as? com.google.android.material.card.MaterialCardView)?.apply {
                strokeWidth = if (hasFocus) 4 else 0
                strokeColor = getColor(
                    if (hasFocus) com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_0
                    else android.R.color.transparent
                )
            }

            // 3D 视差效果：获得焦点时凸出
            make3DEffectForSide(card, isLeft, hasFocus, delta = 12f)
        }
    }

    private fun navigateToFeature(item: MenuItem) {
        if (item.targetClass != null) {
            startActivity(android.content.Intent(this, item.targetClass))
        } else {
            FToast.show("${item.label} 功能即将推出")
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

## 关键设计决策说明

### 为什么 `isVertical = false`？
卡片是横向排列的，用户自然期望左右滑动来切换选项。`isVertical = false` 让 `FixPosFocusTracker` 响应水平方向（`SlideForward`/`SlideBackward`）的滑动来切换焦点。

### 为什么焦点循环（`loop = true`）？
菜单场景下，最后一项的"下一个"跳回第一项，符合用户对菜单的直觉预期，避免用户到了边界不知所措。

### 3D 效果的 delta 选择
`delta = 12f` 是经过测试的合适值：既有明显的立体感，又不会因偏移过大显得突兀。对于较小的卡片（<100dp），这个值合适；更大的元素可以适当增加到 15-20f。

---

## 下一步

- [`03-scrollable-list.md`](./03-scrollable-list.md)：可滑动内容列表

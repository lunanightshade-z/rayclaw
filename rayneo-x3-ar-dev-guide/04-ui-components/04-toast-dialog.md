# FToast 与 FDialog 使用指南

SDK 提供了 AR 原生的 Toast 和 Dialog 组件，它们自动支持合目（双屏同步显示），无需额外配置。

---

## 1. FToast —— AR 原生提示条

`FToast` 是 AR 版本的 Toast，自动在左右两屏同步显示。

### 基础用法

```kotlin
import com.ffalcon.mercury.android.sdk.ui.toast.FToast

// 最简单的用法：显示文字
FToast.show("操作成功")

// 指定 Context（通常不需要，SDK 内部会找合适的 Context）
FToast.show(context, "操作成功")

// 使用字符串资源 ID
FToast.show(R.string.action_success)

// 自定义显示时长（毫秒）
FToast.show("操作成功", duration = 3000)
```

### 自定义布局 Toast

```kotlin
// 使用自定义布局
FToast.show(
    layoutRes = R.layout.my_custom_toast,
    bindingAction = { binding ->
        // 在这里操作自定义布局中的 View
        binding.tvMessage.text = "自定义 Toast"
        binding.ivIcon.setImageResource(R.drawable.ic_success)
    }
)
```

### 使用建议

- 用于即时反馈，不需要用户操作（如"已复制"、"保存成功"）
- 文字简洁，不超过 20 个字
- 不要频繁连续弹出，避免干扰用户视野
- AR 场景下 Toast 比手机更突出，更短时间即可

---

## 2. FDialog —— AR 原生对话框

`FDialog` 是支持合目的对话框，同时支持焦点控制，用户可以通过镜腿手势操作。

### 基础构建

```kotlin
import com.ffalcon.mercury.android.sdk.ui.dialog.FDialog

// FDialog 使用 Builder 模式构建
FDialog.Builder<DialogConfirmBinding>(this)
    .setLayout(R.layout.dialog_confirm)        // 对话框布局
    .setEventHandler { binding, action ->      // 镜腿事件处理
        when (action) {
            is TempleAction.Click -> {
                // 确认操作
                performAction()
                dismissDialog()
            }
            is TempleAction.DoubleClick -> {
                // 取消/关闭
                dismissDialog()
            }
            else -> Unit
        }
    }
    .setBindingAction { binding ->             // 初始化对话框内容
        binding.tvTitle.text = "确认操作"
        binding.tvMessage.text = "是否确认执行此操作？"
        binding.btnConfirm.text = "确认"
        binding.btnCancel.text = "取消"
    }
    .show()
```

### 带焦点管理的 Dialog

更复杂的 Dialog 可能有多个可操作区域（如"确认"和"取消"按钮），需要在 Dialog 内部实现焦点管理：

```kotlin
var dialog: FDialog? = null

fun showConfirmDialog() {
    val focusHolder = FocusHolder(loop = false)

    dialog = FDialog.Builder<DialogConfirmBinding>(this)
        .setLayout(R.layout.dialog_confirm)
        .setBindingAction { binding ->
            binding.tvMessage.text = "确认删除这条记录？"

            // 在 Dialog 内部设置焦点
            focusHolder.addFocusTarget(
                FocusInfo(
                    target = binding.btnConfirm,
                    eventHandler = { action ->
                        if (action is TempleAction.Click) {
                            performDelete()
                            dialog?.dismiss()
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        binding.btnConfirm.isSelected = hasFocus
                    }
                ),
                FocusInfo(
                    target = binding.btnCancel,
                    eventHandler = { action ->
                        if (action is TempleAction.Click) {
                            dialog?.dismiss()
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        binding.btnCancel.isSelected = hasFocus
                    }
                )
            )
            focusHolder.currentFocus(binding.btnConfirm)
        }
        .setEventHandler { binding, action ->
            when (action) {
                is TempleAction.DoubleClick -> dialog?.dismiss()
                else -> {
                    // 将事件交给 Dialog 内部的焦点 tracker
                    dialogFocusTracker?.handleFocusTargetEvent(action)
                }
            }
        }
        .show()

    // Dialog 显示后设置焦点 tracker
    dialogFocusTracker = FixPosFocusTracker(focusHolder).apply {
        focusObj.reqFocus()
    }
}
```

### Dialog 使用建议

- 用于需要用户确认的操作（删除、提交、退出等）
- Dialog 出现时，底层页面的焦点系统应暂时停止响应
- Dialog 关闭后，恢复底层页面的焦点
- 每个 Dialog 应该有明确的关闭方式（通常是双击）
- Dialog 文字要大、选项要少（最多 2-3 个）

---

## 3. 组合使用示例

```kotlin
// 用户单击 → 弹出确认 Dialog
// 用户在 Dialog 内确认 → 执行操作 + 弹出 FToast 反馈

private fun onDeleteAction() {
    showDeleteConfirmDialog()
}

private fun showDeleteConfirmDialog() {
    FDialog.Builder<DialogDeleteBinding>(this)
        .setLayout(R.layout.dialog_delete)
        .setBindingAction { binding ->
            binding.tvTitle.text = "删除记录"
            binding.tvMessage.text = "此操作不可撤销"
        }
        .setEventHandler { _, action ->
            when (action) {
                is TempleAction.Click -> {
                    performDelete()
                    FToast.show("删除成功")
                }
                is TempleAction.DoubleClick -> { /* 关闭 */ }
                else -> Unit
            }
        }
        .show()
}
```

---

## 下一步

- [`05-recyclerview-navigation.md`](./05-recyclerview-navigation.md)：RecyclerView 镜腿导航增强

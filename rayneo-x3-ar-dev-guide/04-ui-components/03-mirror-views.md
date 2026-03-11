# View 级别合目组件

有时你不想替换整个 Activity 或 Fragment 的基类，而是只对某个区域实现合目效果。SDK 提供了 View 级别的合目组件。

---

## 1. 两种 View 级合目方案

| 组件 | 方式 | 适用场景 |
|------|------|---------|
| `MirrorContainerView` | 组合（composition）| 将任意 View 包装成合目容器 |
| `BaseMirrorContainerView<B>` | 继承（inheritance）| 创建自定义的合目 View 组件 |

---

## 2. MirrorContainerView（组合方式）

### 使用步骤

**步骤 1：在布局中添加 MirrorContainerView**

```xml
<!-- 在普通 Activity 的布局中，左右各放一个 MirrorContainerView -->
<LinearLayout android:orientation="horizontal" ...>

    <!-- 左屏区域 -->
    <com.ffalcon.mercury.android.sdk.ui.wiget.MirrorContainerView
        android:id="@+id/mirrorLeft"
        android:layout_width="0dp"
        android:layout_weight="1"
        android:layout_height="match_parent" />

    <!-- 右屏区域 -->
    <com.ffalcon.mercury.android.sdk.ui.wiget.MirrorContainerView
        android:id="@+id/mirrorRight"
        android:layout_width="0dp"
        android:layout_weight="1"
        android:layout_height="match_parent" />

</LinearLayout>
```

**步骤 2：将两个 MirrorContainerView 绑定在一起**

```kotlin
class MyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 关键：必须调用 bindTo，否则合目不生效
        binding.mirrorLeft.bindTo(binding.mirrorRight)

        // 之后对 mirrorLeft 的操作会自动同步到 mirrorRight
        binding.mirrorLeft.setContent(R.layout.content_layout)
    }
}
```

> **注意**：如果忘记调用 `bindTo()`，两个 MirrorContainerView 相互独立，合目效果不生效。

---

## 3. BaseMirrorContainerView（继承方式）

适合创建可复用的合目自定义 View 组件：

```kotlin
// 自定义合目标题栏
class TitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMirrorContainerView<ViewTitleBinding>(context, attrs) {

    // 通过 mBindingPair 操作内部 View
    fun setTitle(title: String) {
        mBindingPair.updateView {
            tvTitle.text = title
        }
    }

    fun setBackButtonVisible(visible: Boolean) {
        mBindingPair.updateView {
            btnBack.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
}
```

在布局中使用：

```xml
<com.yourapp.TitleView
    android:id="@+id/titleView"
    android:layout_width="match_parent"
    android:layout_height="56dp" />
```

在 Activity 中：

```kotlin
// TitleView 内部自动处理合目，外部只需调用业务接口
binding.titleView.setTitle("我的页面")
binding.titleView.setBackButtonVisible(true)
```

---

## 重要限制

`MirrorContainerView` 和 `BaseMirrorContainerView` **不能添加到 `BaseMirrorActivity` 中**。

原因：`BaseMirrorActivity` 已经将整个布局做了合目处理，View 级组件在其中会产生双重合目，造成渲染问题。

**正确场景分工**：
- 整个页面需要合目 → 使用 `BaseMirrorActivity`
- 普通 Activity 中某个区域需要合目 → 使用 View 级组件

---

## 下一步

- [`04-toast-dialog.md`](./04-toast-dialog.md)：AR 专用 Toast 和 Dialog

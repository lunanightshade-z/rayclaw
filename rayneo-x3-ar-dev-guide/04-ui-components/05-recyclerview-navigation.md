# RecyclerView 镜腿导航增强

AR 眼镜上使用列表（RecyclerView）需要特殊处理，因为用户无法直接触摸滚动。SDK 提供了两种列表导航模式的工具类。

---

## 1. 两种列表导航模式

| 模式 | 工具类 | 特点 |
|------|--------|------|
| 固定焦点位 + 列表跟手滚动 | `RecyclerViewSlidingTracker` | 焦点停在固定位置（如第一项），列表内容跟手滑动 |
| 移动焦点位 + 列表跟手 | `RecyclerViewFocusTracker` | 焦点跟随当前选中项移动，滑动一定距离后切换焦点 |

---

## 2. RecyclerViewSlidingTracker —— 固定焦点位

**视觉效果**：焦点框始终在固定位置（如列表顶部），用户滑动镜腿时列表内容上下滚动，就像在翻页。

### 使用步骤

```kotlin
import com.ffalcon.mercury.android.sdk.ui.util.RecyclerViewSlidingTracker
import com.ffalcon.mercury.android.sdk.util.StartSnapHelper

class FixedFocusListActivity : BaseMirrorActivity<ActivityListBinding>() {

    private var slidingTracker: RecyclerViewSlidingTracker? = null
    private var focusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupList()
        collectEvents()
    }

    private fun setupList() {
        val items = loadDataItems()

        // 在左屏设置 RecyclerView
        mBindingPair.setLeft {
            recyclerView.apply {
                layoutManager = LinearLayoutManager(this@FixedFocusListActivity)
                adapter = MyAdapter(items)

                // 吸附到起始位置（让列表项对齐到列表顶部）
                StartSnapHelper().attachToRecyclerView(this)
            }
        }

        // 创建固定焦点的外层 FocusHolder
        val outerFocusHolder = FocusHolder(loop = false)
        slidingTracker = RecyclerViewSlidingTracker(mBindingPair.left.recyclerView)

        mBindingPair.setLeft {
            outerFocusHolder.addFocusTarget(
                FocusInfo(
                    target = slidingTracker!!.focusObj,
                    eventHandler = { action ->
                        // 将列表操作事件委托给 slidingTracker
                        slidingTracker?.handleActionEvent(action) { unhandledAction ->
                            when (unhandledAction) {
                                is TempleAction.Click -> {
                                    // 点击当前可见的第一个 Item
                                    val adapter = mBindingPair.left.recyclerView.adapter
                                    if (adapter is MyAdapter) {
                                        val firstVisible = (mBindingPair.left.recyclerView
                                            .layoutManager as LinearLayoutManager)
                                            .findFirstVisibleItemPosition()
                                        adapter.getItem(firstVisible)?.let {
                                            FToast.show(it.name)
                                        }
                                    }
                                }
                                is TempleAction.DoubleClick -> finish()
                                else -> Unit
                            }
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            // 整个列表获得/失去焦点时的视觉反馈
                            cardListContainer.strokeWidth = if (hasFocus) 4 else 0
                        }
                    }
                )
            )
            outerFocusHolder.currentFocus(slidingTracker!!.focusObj)
        }

        focusTracker = FixPosFocusTracker(outerFocusHolder).apply {
            focusObj.reqFocus()
        }

        // 关键：让 SlidingTracker 监听原始 MotionEvent 以实现跟手效果
        slidingTracker?.observeOriginMotionEventStream(
            motionEventDispatcher  // BaseTouchActivity 提供的原始事件分发器
        ) { event ->
            // 将原始事件坐标转换为 RecyclerView 可以识别的坐标
            // Y 轴固定到 RecyclerView 中心，X 轴用滑动量
            MotionEvent.obtain(
                event.downTime,
                event.eventTime,
                event.action,
                mBindingPair.left.recyclerView.width / 2f,  // 固定 X 到中心
                event.x,  // 将原始 X 作为模拟 Y（镜腿是横向的，列表是竖向的）
                event.metaState
            )
        }
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    if (!action.consumed) {
                        focusTracker?.handleFocusTargetEvent(action)
                    }
                }
            }
        }
    }
}
```

---

## 3. RecyclerViewFocusTracker —— 移动焦点位

**视觉效果**：焦点框跟随当前选中的 Item 移动，用户滑动时焦点从一个 Item 跳到下一个 Item。

```kotlin
import com.ffalcon.mercury.android.sdk.ui.util.RecyclerViewFocusTracker

class MovedFocusListActivity : BaseMirrorActivity<ActivityListBinding>() {

    private var rvFocusTracker: RecyclerViewFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupList()
        collectEvents()
    }

    private fun setupList() {
        val items = loadDataItems()

        mBindingPair.setLeft {
            recyclerView.apply {
                layoutManager = LinearLayoutManager(this@MovedFocusListActivity)
                adapter = MyAdapter(items) { position, item ->
                    // Item 被点击（焦点在该 Item 上时单击）
                    FToast.show(item.name)
                }
            }
        }

        // RecyclerViewFocusTracker 管理焦点在 Item 间的移动
        rvFocusTracker = RecyclerViewFocusTracker(
            recyclerView = mBindingPair.left.recyclerView,
            onFocusChange = { position, hasFocus ->
                // 某个 Item 获得/失去焦点时的回调
                // 更新该 Item 的视觉状态
                val adapter = mBindingPair.left.recyclerView.adapter as MyAdapter
                adapter.setFocusPosition(position, hasFocus)
                // 同时更新右屏
                (mBindingPair.right.recyclerView.adapter as MyAdapter)
                    .setFocusPosition(position, hasFocus)
            }
        ).apply {
            focusObj.reqFocus()  // 激活焦点
        }
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> finish()
                        else -> rvFocusTracker?.handleActionEvent(action) { unhandled ->
                            // rvFocusTracker 未处理的事件（如 Click）
                            if (unhandled is TempleAction.Click) {
                                val position = rvFocusTracker?.currentFocusPosition ?: return@handleActionEvent
                                // 处理当前焦点 Item 的点击
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

## 4. Adapter 实现要点

RecyclerView 在 AR 应用中，Adapter 需要特别考虑焦点状态的视觉反馈：

```kotlin
class MyAdapter(
    private val items: List<DataItem>,
    private val onItemClick: (Int, DataItem) -> Unit
) : BaseBindingAdapter<ItemCardBinding, DataItem>() {

    private var focusedPosition = -1

    fun setFocusPosition(position: Int, hasFocus: Boolean) {
        val oldPos = focusedPosition
        focusedPosition = if (hasFocus) position else -1
        notifyItemChanged(oldPos)
        notifyItemChanged(position)
    }

    override fun onBindViewHolder(holder: BaseBindingHolder<ItemCardBinding>, position: Int) {
        val item = items[position]
        val isFocused = position == focusedPosition

        holder.binding.apply {
            tvTitle.text = item.name
            tvSubtitle.text = item.description

            // 焦点视觉反馈
            root.isSelected = isFocused
            cardView.strokeWidth = if (isFocused) 4.dp else 0

            // 焦点时添加 3D 效果
            if (isFocused) {
                cardView.translationZ = 8f
            } else {
                cardView.translationZ = 0f
            }
        }
    }
}
```

---

## 5. StartSnapHelper

`StartSnapHelper` 让列表在滚动停止后，自动将最近的 Item 吸附到列表起始位置（顶部）：

```kotlin
import com.ffalcon.mercury.android.sdk.util.StartSnapHelper

// 在设置 RecyclerView 时附加
mBindingPair.setLeft {
    recyclerView.apply {
        layoutManager = LinearLayoutManager(context)
        adapter = myAdapter
        StartSnapHelper().attachToRecyclerView(this)  // 吸附到起始位置
    }
}
```

这对固定焦点位模式特别有用，确保每次滑动后 Item 整齐对齐，不会出现只显示半个 Item 的情况。

---

## 6. 选择哪种模式

| 场景 | 推荐模式 |
|------|---------|
| AR 信息卡片流（识别历史、通知列表）| 固定焦点位（SlidingTracker）|
| 菜单选项列表（功能选择、设置项）| 移动焦点位（FocusTracker）|
| 内容预览列表（图片、视频）| 固定焦点位（配合 StartSnapHelper）|
| 分类筛选（横向 Tab 切换）| 移动焦点位（isVertical = false）|

---

## 下一步

- [`../05-hardware-apis/01-camera.md`](../05-hardware-apis/01-camera.md)：Camera2 API 与双屏预览

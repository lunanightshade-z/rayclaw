# Recipe 3：镜腿驱动的可滑动列表

本 Recipe 实现一个 AR 历史记录列表（如识别结果历史），支持镜腿滑动浏览，单击查看详情，双击退出。

---

## 目标效果

- 列表显示多条历史记录
- 滑动镜腿使列表内容跟手滚动
- 焦点固定在列表顶部
- 单击当前最顶部的 Item 查看详情
- 双击退出

---

## 布局文件

**activity_list.xml**

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

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="识别历史"
        android:textColor="#FFFFFF"
        android:textSize="24sp"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/tvHint"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="滑动浏览 · 单击查看 · 双击退出"
        android:textColor="#60FFFFFF"
        android:textSize="12sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="12dp"
        android:clipToPadding="false"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/tvTitle" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

**item_history.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/cardRoot"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginVertical="6dp"
    app:cardBackgroundColor="#0D1B2A"
    app:cardCornerRadius="10dp"
    app:strokeColor="#1A3A5C"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/tvItemTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="#FFFFFF"
            android:textSize="18sp" />

        <TextView
            android:id="@+id/tvItemSubtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="#80FFFFFF"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/tvItemTime"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="#40FFFFFF"
            android:textSize="12sp" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

---

## 数据模型

```kotlin
data class HistoryItem(
    val title: String,
    val subtitle: String,
    val timestamp: String
)
```

---

## Adapter

```kotlin
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.myarapp.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var focusedPosition = 0

    fun setFocusedPosition(position: Int) {
        val oldPos = focusedPosition
        focusedPosition = position
        notifyItemChanged(oldPos)
        notifyItemChanged(position)
    }

    fun getItemAt(position: Int): HistoryItem? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isFocused = position == focusedPosition

        holder.binding.apply {
            tvItemTitle.text = item.title
            tvItemSubtitle.text = item.subtitle
            tvItemTime.text = item.timestamp

            // 焦点视觉
            cardRoot.strokeWidth = if (isFocused) 3 else 1
            cardRoot.strokeColor = if (isFocused) {
                root.context.getColor(com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_0)
            } else {
                root.context.getColor(android.R.color.darker_gray)
            }
        }
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)
}
```

---

## Activity 实现

```kotlin
import android.view.MotionEvent
import androidx.recyclerview.widget.LinearLayoutManager
import com.ffalcon.mercury.android.sdk.ui.util.RecyclerViewSlidingTracker
import com.ffalcon.mercury.android.sdk.util.StartSnapHelper

class HistoryListActivity : BaseMirrorActivity<ActivityListBinding>() {

    private var slidingTracker: RecyclerViewSlidingTracker? = null
    private var outerFocusTracker: FixPosFocusTracker? = null
    private val adapter by lazy {
        HistoryAdapter(generateSampleData()) { item ->
            onItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupRecyclerView()
        setupFocusSystem()
        collectEvents()
    }

    private fun setupRecyclerView() {
        mBindingPair.setLeft {
            recyclerView.apply {
                layoutManager = LinearLayoutManager(this@HistoryListActivity)
                adapter = this@HistoryListActivity.adapter
                // 吸附到顶部，确保每次滑动后 Item 整齐对齐
                StartSnapHelper().attachToRecyclerView(this)
            }
        }

        // 右屏的 RecyclerView 使用相同 adapter（共享数据，焦点状态通过 adapter.notifyItemChanged 同步）
        mBindingPair.setLeft {  // 注意：右屏 RV 也需要设置 adapter，但实际上 BaseMirrorActivity 会镜像布局
            // 在实际项目中，左右屏的 RecyclerView 各自需要设置 adapter
            // 可以共用同一个 adapter 实例，也可以各自独立
        }
    }

    private fun setupFocusSystem() {
        slidingTracker = RecyclerViewSlidingTracker(mBindingPair.left.recyclerView)

        val outerFocusHolder = FocusHolder(loop = false)

        mBindingPair.setLeft {
            outerFocusHolder.addFocusTarget(
                FocusInfo(
                    target = slidingTracker!!.focusObj,
                    eventHandler = { action ->
                        slidingTracker?.handleActionEvent(action) { unhandledAction ->
                            when (unhandledAction) {
                                is TempleAction.Click -> {
                                    // 点击第一个可见 Item
                                    val layoutManager = mBindingPair.left.recyclerView
                                        .layoutManager as LinearLayoutManager
                                    val firstPos = layoutManager.findFirstVisibleItemPosition()
                                    adapter.getItemAt(firstPos)?.let { onItemSelected(it) }
                                }
                                else -> Unit
                            }
                        }
                    },
                    focusChangeHandler = { _ -> /* 列表容器焦点变化处理 */ }
                )
            )
            outerFocusHolder.currentFocus(slidingTracker!!.focusObj)
        }

        outerFocusTracker = FixPosFocusTracker(outerFocusHolder).apply {
            focusObj.reqFocus()
        }

        // 关键：让 SlidingTracker 拦截原始 MotionEvent，实现跟手滚动
        slidingTracker?.observeOriginMotionEventStream(
            motionEventDispatcher  // BaseTouchActivity 提供的原始事件分发器
        ) { event ->
            // 将镜腿的横向滑动坐标（event.x）映射为 RecyclerView 的竖向坐标
            MotionEvent.obtain(
                event.downTime,
                event.eventTime,
                event.action,
                mBindingPair.left.recyclerView.width / 2f,  // X 固定到列表中心
                event.x,   // 将原始 X（镜腿横向滑动量）作为竖向 Y
                event.metaState
            )
        }
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    if (!action.consumed) {
                        when (action) {
                            is TempleAction.DoubleClick -> finish()
                            else -> outerFocusTracker?.handleFocusTargetEvent(action)
                        }
                    }
                }
            }
        }
    }

    private fun onItemSelected(item: HistoryItem) {
        FToast.show("查看：${item.title}")
        // 实际应用：启动详情 Activity
        // startActivity(Intent(this, DetailActivity::class.java).apply {
        //     putExtra("title", item.title)
        // })
    }

    private fun generateSampleData(): List<HistoryItem> {
        return (1..20).map { i ->
            HistoryItem(
                title = "识别结果 #$i",
                subtitle = "置信度: ${(80..99).random()}%",
                timestamp = "2026-03-05 ${(10..23).random()}:${(10..59).random()}"
            )
        }
    }
}
```

---

## 理解跟手坐标转换

这是列表跟手效果的关键。镜腿触控板产生的是**水平方向**的 MotionEvent，而 RecyclerView 需要**垂直方向**的滑动来滚动。转换逻辑：

```kotlin
MotionEvent.obtain(
    event.downTime,    // 保持原始时间戳（用于速度计算）
    event.eventTime,
    event.action,      // 保持原始动作类型
    RV_CENTER_X,       // X 固定：让事件落在 RecyclerView 的 X 中心（必须在 RV 区域内）
    event.x,           // Y 使用原始的 X 值：镜腿左右滑动 → 列表上下滚动
    event.metaState
)
```

这个坐标转换将镜腿的一维横向输入，映射为 RecyclerView 可识别的竖向触摸事件，从而实现跟手效果。

---

## 下一步

- [`04-video-mirroring.md`](./04-video-mirroring.md)：视频播放双屏同步

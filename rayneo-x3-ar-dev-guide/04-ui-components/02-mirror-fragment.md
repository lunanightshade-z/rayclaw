# BaseMirrorFragment 详解

## 1. 使用场景

`BaseMirrorFragment` 用于在普通 Activity（非 `BaseMirrorActivity`）中嵌入带合目效果的 Fragment。

> **重要限制**：`BaseMirrorFragment` **不能添加到** `BaseMirrorActivity` 中。如果你的 Activity 已经是 `BaseMirrorActivity`，直接用普通 Fragment 即可，因为 Activity 已经处理了合目逻辑。

典型使用场景：
- 非 Mercury 基类的 Activity（如继承自第三方库的 Activity），需要在其中嵌入合目 Fragment
- Fragment 内有独立的合目需求，与外部 Activity 合目逻辑解耦

---

## 2. 基础用法

### 定义 Fragment

```kotlin
import com.ffalcon.mercury.android.sdk.ui.fragment.BaseMirrorFragment
import com.yourapp.databinding.FragmentDemoBinding
import com.yourapp.databinding.FragmentDemoBinding  // HolderBinding 可与 B 相同

// 泛型参数：
// B = 布局 ViewBinding 类
// H = HolderPair 的 ViewBinding 类（通常与 B 相同）
class DemoFragment : BaseMirrorFragment<FragmentDemoBinding, FragmentDemoBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 与 BaseMirrorActivity 一样，使用 mBindingPair
        mBindingPair.updateView {
            tvFragmentTitle.text = "Fragment 内容"
        }
    }
}
```

### 在 Activity 中添加 Fragment

```kotlin
// 在普通 Activity 中（非 BaseMirrorActivity）
class ContainerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container)

        // 像添加普通 Fragment 一样添加 BaseMirrorFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, DemoFragment())
            .commit()
    }
}
```

---

## 3. Fragment + RecyclerView 合目

Fragment 内包含 RecyclerView 时，需要特别注意合目的配合。参考 SDK Demo 中的 `FragmentRecyclerViewDemo` 实现：

```kotlin
class RecyclerFragment : BaseMirrorFragment<FragmentRecyclerBinding, FragmentRecyclerBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val items = listOf("Item 1", "Item 2", "Item 3")

        // RecyclerView 的 adapter 设置放在 setLeft 中
        mBindingPair.setLeft {
            recyclerView.adapter = SimpleAdapter(items)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
        }
    }
}
```

---

## 4. 与 Activity 通信

Fragment 与外部 Activity 通信的方式与普通 Fragment 相同：

```kotlin
// 方式一：通过 ViewModel 共享
class SharedViewModel : ViewModel() {
    val selectedItem = MutableLiveData<String>()
}

class RecyclerFragment : BaseMirrorFragment<...>() {
    private val sharedViewModel: SharedViewModel by activityViewModels()

    // Fragment 内通知 Activity
    private fun onItemSelected(item: String) {
        sharedViewModel.selectedItem.value = item
    }
}
```

---

## 下一步

- [`03-mirror-views.md`](./03-mirror-views.md)：View 级别的合目组件

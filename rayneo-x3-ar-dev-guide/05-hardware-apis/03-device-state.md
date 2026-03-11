# 设备连接状态与手机 GPS 推流

## 1. 手机连接状态监听

X3 眼镜可以通过蓝牙与配对的手机（运行"雷鸟AR眼镜" App）保持连接。许多功能（如 GPS、网络代理）依赖这个蓝牙连接。

### 使用 MobileState 监听连接状态

```kotlin
import com.ffalcon.mercury.android.sdk.api.MobileState
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

class MyActivity : BaseMirrorActivity<ActivityMyBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitorPhoneConnection()
    }

    private fun monitorPhoneConnection() {
        // isMobileConnected() 返回 Flow<Boolean>
        MobileState.isMobileConnected()
            .onEach { isConnected ->
                // 这个回调在 Flow 的线程中，需要切换到主线程更新 UI
                mBindingPair.updateView {
                    tvConnectionStatus.text = if (isConnected) "手机已连接" else "手机未连接"
                    ivConnectionIcon.setImageResource(
                        if (isConnected) R.drawable.ic_connected
                        else R.drawable.ic_disconnected
                    )
                }

                // 连接断开时降级处理（如禁用需要网络的功能）
                if (!isConnected) {
                    onPhoneDisconnected()
                }
            }
            .launchIn(lifecycleScope)  // 绑定 Activity 生命周期，自动在 onDestroy 取消
    }

    private fun onPhoneDisconnected() {
        // 业务降级：禁用云端功能，提示用户
        mBindingPair.updateView {
            btnCloudFeature.isEnabled = false
            tvHint.text = "请连接手机以使用云端功能"
        }
    }
}
```

---

## 2. 手机 GPS 推流

X3 眼镜本身没有 GPS 模块。通过 IPC SDK 可以从配对的手机获取实时 GPS 数据。

> **前置条件**：
> 1. 集成 IPC SDK（`RayNeoIPCSDK-For-Android-V0.1.0-....aar`，位于 Sample 的 libs 目录）
> 2. 在"雷鸟AR眼镜"手机 App 中授予定位权限（首次使用）

### 实现 GPS 数据接收

```kotlin
import org.json.JSONObject
import org.json.JSONException

class GPSActivity : BaseMirrorActivity<ActivityGpsBinding>() {

    // IPC SDK 的响应回调（注意：回调在异步线程）
    private val gpsResponseListener = OnResponseListener { response ->
        if (response?.getData() == null) return@OnResponseListener

        try {
            val data = JSONObject(response.getData())

            // GPS 数据格式
            if (data.has("mLatitude") && data.has("mLongitude")) {
                val latitude = data.getDouble("mLatitude")
                val longitude = data.getDouble("mLongitude")
                val altitude = data.optDouble("mAltitude", 0.0)
                val provider = data.optString("mProvider", "unknown")
                val timestamp = data.optLong("mTime", 0L)

                // 必须切换到主线程更新 UI
                runOnUiThread {
                    mBindingPair.updateView {
                        tvLatitude.text = "纬度: %.6f".format(latitude)
                        tvLongitude.text = "经度: %.6f".format(longitude)
                        tvAltitude.text = "海拔: %.1fm".format(altitude)
                        tvProvider.text = "来源: $provider"
                    }
                }

                // 用于 AR 定位的坐标数据
                onGPSDataReceived(latitude, longitude, altitude)
            }
        } catch (e: JSONException) {
            FLogger.e("GPSActivity", "GPS 数据解析失败: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startGPSStream()
    }

    private fun startGPSStream() {
        // IPC SDK 调用（参考 IPC SDK 文档）
        // IPCClient.getInstance().registerResponseListener(GPS_CHANNEL, gpsResponseListener)
    }

    private fun onGPSDataReceived(lat: Double, lon: Double, alt: Double) {
        // 在这里使用坐标数据
        // 例如：在 AR 视图中标注地理位置相关的信息
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消 GPS 监听
        // IPCClient.getInstance().unregisterResponseListener(GPS_CHANNEL, gpsResponseListener)
    }
}
```

---

## 3. 设备类型判断

如果你需要同时支持 X2 和 X3，可以使用 `DeviceUtil` 区分：

```kotlin
import com.ffalcon.mercury.android.sdk.util.DeviceUtil

class MyActivity : BaseMirrorActivity<ActivityMyBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (DeviceUtil.isX3Device()) {
            // X3 专有功能：上滑/下滑手势、双指手势等
            setupX3Features()
        } else {
            // X2 兼容模式
            setupX2CompatibleFeatures()
        }
    }

    private fun setupX3Features() {
        // 启用 X3 专有的交互方式
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.SlideUpwards -> handleUpSwipe()   // X3 新增
                        is TempleAction.SlideDownwards -> handleDownSwipe() // X3 新增
                        is TempleAction.DoubleFingerClick -> handleDoubleFingerTap() // X3 新增
                        else -> handleCommonActions(action)
                    }
                }
            }
        }
    }
}
```

---

## 4. 蓝牙音频路由

X3 支持蓝牙 A2DP（音乐）和 HFP（通话）两种音频模式。SDK 提供 `SoundBridge` 工具类控制音频路由：

```kotlin
import com.ffalcon.mercury.android.sdk.util.SoundBridge

// 切换到 A2DP（音乐播放模式）
SoundBridge.switchToA2DP()

// 切换到 HFP（通话模式，使用麦克风）
SoundBridge.switchToHFP()
```

> **使用建议**：仅在明确需要特定音频路由的场景调用，例如语音播报识别结果前切换到合适的模式。避免随意切换，否则可能影响系统的整体音频状态。

---

## 5. 输入源判定

`MyTouchUtils` 提供工具方法判断当前触控事件来自哪个输入源：

```kotlin
import com.ffalcon.mercury.android.sdk.util.MyTouchUtils

// 判断事件是否来自左侧输入
val isLeft = MyTouchUtils.isLeftInput(motionEvent)

// 判断事件是否是虚拟输入（如戒指配件）
val isVirtual = MyTouchUtils.isVirtualInput(motionEvent)
```

---

## 下一步

进入实战案例章节：

- [`../06-recipes/01-hello-world.md`](../06-recipes/01-hello-world.md)：第一个完整 AR 应用

# Recipe 5：相机预览 + AR 信息叠加

这是 AR 应用最典型的场景：透过相机看到的画面上叠加数字信息。本 Recipe 实现一个基础的"AR 扫描"界面：相机实时预览 + 识别结果文字叠加。

---

## 目标效果

- 相机实时预览（双屏同步）
- 叠加文字信息（识别结果、状态）
- 单击触发"扫描"
- 双击退出

---

## 布局文件：activity_ar_overlay.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- 层 1：相机预览（最底层） -->
    <TextureView
        android:id="@+id/textureView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 层 2：右屏镜像组件 -->
    <com.ffalcon.mercury.android.sdk.ui.wiget.MirroringView
        android:id="@+id/mirrorView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 层 3：AR 叠加 UI（黑色背景区域透明，内容叠加在相机画面上） -->

    <!-- 扫描框（中央定位框） -->
    <View
        android:id="@+id/viewScanFrame"
        android:layout_width="160dp"
        android:layout_height="160dp"
        android:layout_gravity="center"
        android:background="@drawable/scan_frame_border" />

    <!-- 顶部状态栏 -->
    <LinearLayout
        android:id="@+id/llTopBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <View
            android:id="@+id/viewStatusDot"
            android:layout_width="8dp"
            android:layout_height="8dp"
            android:background="@drawable/status_dot_green" />

        <TextView
            android:id="@+id/tvStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:text="就绪"
            android:textColor="#FFFFFF"
            android:textSize="14sp" />

    </LinearLayout>

    <!-- 底部结果区域 -->
    <LinearLayout
        android:id="@+id/llResultArea"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_marginBottom="20dp"
        android:layout_marginHorizontal="20dp"
        android:background="#80000000"
        android:orientation="vertical"
        android:padding="12dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/tvResultTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="识别结果"
            android:textColor="#4FC3F7"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/tvResultContent"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="#FFFFFF"
            android:textSize="20sp" />

        <TextView
            android:id="@+id/tvResultConfidence"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textColor="#80FFFFFF"
            android:textSize="13sp" />

    </LinearLayout>

    <!-- 单击操作提示 -->
    <TextView
        android:id="@+id/tvHint"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal|bottom"
        android:layout_marginBottom="8dp"
        android:text="单击扫描 · 双击退出"
        android:textColor="#60FFFFFF"
        android:textSize="12sp" />

</FrameLayout>
```

---

## Activity 实现

```kotlin
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity

class AROverlayActivity : BaseMirrorActivity<ActivityArOverlayBinding>() {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var isScanning = false

    companion object {
        private const val REQUEST_CAMERA = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupMirroring()
        checkCameraPermission()
        collectEvents()
    }

    private fun setupMirroring() {
        mBindingPair.left.textureView.let { leftTexture ->
            mBindingPair.right.mirrorView.setSource(leftTexture)
            mBindingPair.right.mirrorView.startMirroring()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        } else {
            FToast.show("需要相机权限")
        }
    }

    private fun startCameraPreview() {
        val textureView = mBindingPair.left.textureView
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: android.graphics.SurfaceTexture, w: Int, h: Int) {
                    openCamera()
                }
                override fun onSurfaceTextureSizeChanged(
                    surface: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(
                    surface: android.graphics.SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(
                    surface: android.graphics.SurfaceTexture) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        cameraManager.openCamera("0", object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createPreviewSession()
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                FToast.show("相机启动失败")
            }
        }, null)
    }

    private fun createPreviewSession() {
        val texture = mBindingPair.left.textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(1280, 720)
        val surface = Surface(texture)

        val previewRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequest.addTarget(surface)

        cameraDevice!!.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(previewRequest.build(), null, null)
                    runOnUiThread {
                        updateStatus("就绪 - 对准目标后单击扫描")
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    FToast.show("预览配置失败")
                }
            },
            null
        )
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.Click -> onScanTriggered()
                        is TempleAction.DoubleClick -> finish()
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun onScanTriggered() {
        if (isScanning) return
        isScanning = true

        updateStatus("扫描中...")
        mBindingPair.updateView {
            viewScanFrame.alpha = 0.5f
        }

        // 模拟异步识别（实际应用替换为真实的 AI 识别调用）
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)  // 模拟处理时间

            val result = "示例物品"
            val confidence = 95

            runOnUiThread {
                showRecognitionResult(result, confidence)
                isScanning = false
            }
        }
    }

    private fun showRecognitionResult(result: String, confidence: Int) {
        mBindingPair.updateView {
            // 更新状态
            tvStatus.text = "识别完成"
            viewScanFrame.alpha = 1.0f

            // 显示结果区域
            llResultArea.visibility = android.view.View.VISIBLE
            tvResultContent.text = result
            tvResultConfidence.text = "置信度: $confidence%"
        }

        FToast.show("识别到：$result")

        // 3 秒后隐藏结果，恢复就绪状态
        lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            runOnUiThread {
                mBindingPair.updateView {
                    llResultArea.visibility = android.view.View.GONE
                    tvStatus.text = "就绪 - 单击扫描"
                }
            }
        }
    }

    private fun updateStatus(message: String) {
        mBindingPair.updateView {
            tvStatus.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mBindingPair.right.mirrorView.stopMirroring()
        captureSession?.close()
        cameraDevice?.close()
    }
}
```

---

## 扩展：接入真实 AI 识别

将模拟识别替换为实际的模型调用：

```kotlin
private suspend fun performRecognition(): Pair<String, Int> {
    return withContext(Dispatchers.IO) {
        // 方式 1：从 TextureView 截取当前帧
        val bitmap = mBindingPair.left.textureView.bitmap

        // 方式 2：使用 Camera2 ImageReader 捕获帧
        // ...

        // 方式 3：调用云端 API
        // val result = myApiClient.recognize(bitmap)

        // 返回识别结果
        Pair("识别到的物品", 92)
    }
}
```

---

这是一个完整的 AR 扫描应用骨架，你可以在此基础上：
- 接入任何 AI 识别模型（本地 TFLite 或云端 API）
- 设计更丰富的结果展示
- 添加历史记录功能（参考 Recipe 3）

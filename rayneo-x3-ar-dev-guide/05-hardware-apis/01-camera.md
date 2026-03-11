# Camera2 API：双屏预览与帧捕获

X3 眼镜上的相机开发与普通 Android 相机开发基本一致，使用标准 Camera2 API。主要差异在于：需要将相机预览同时显示到双屏，以及了解 X3 特有的摄像头配置。

---

## 1. X3 摄像头配置

### 枚举摄像头

```kotlin
private fun enumerateCameras() {
    val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

    for (cameraId in cameraManager.cameraIdList) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        if (map != null) {
            val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)
            val pictureSizes = map.getOutputSizes(ImageFormat.JPEG)

            FLogger.d("Camera ID: $cameraId")
            previewSizes.forEach { size ->
                FLogger.d("  Preview: ${size.width}x${size.height}")
            }
        }
    }
}
```

### X3 摄像头说明

| Camera ID | 类型 | 主要用途 | 最高分辨率 |
|-----------|------|---------|-----------|
| 0 | 主摄像头 | AR 识别、拍照、录像 | 根据实际枚举 |
| 1 | VGA 摄像头 | 空间定位追踪 | 640×480 |

**VGA 摄像头（Camera ID: 1）支持的分辨率**：
```
640x480, 640x400, 640x360, 480x360,
352x288, 320x240, 320x180, 176x144
```

> **注意**：VGA 摄像头主要用于空间定位，开发者通常不需要直接操作。正常 AR 识别应用使用 Camera ID: 0（主摄像头）。

---

## 2. 权限声明

在 `AndroidManifest.xml` 中声明权限：

```xml
<uses-permission android:name="android.permission.CAMERA" />

<!-- 如果需要录像 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

在代码中动态申请权限：

```kotlin
private fun checkAndRequestCameraPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    } else {
        startCamera()
    }
}

override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_CAMERA_PERMISSION) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            FToast.show("需要相机权限才能使用此功能")
        }
    }
}
```

---

## 3. 双屏相机预览

AR 眼镜需要在左右两屏同时显示相机预览。方案是：
1. 在左屏放 `TextureView` 作为主预览
2. 在右屏放 `MirroringView` 镜像左屏内容

### 布局文件

```xml
<!-- activity_camera.xml -->
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000">

    <!-- 左屏：TextureView 作为相机预览目标 -->
    <!-- 注意：在 BaseMirrorActivity 中，左屏在布局的左半部分 -->
    <TextureView
        android:id="@+id/textureView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 右屏：MirroringView 镜像左屏的 TextureView 内容 -->
    <com.ffalcon.mercury.android.sdk.ui.wiget.MirroringView
        android:id="@+id/mirrorView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- AR 叠加层（放在相机预览上方） -->
    <FrameLayout
        android:id="@+id/overlayContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```

### Activity 实现

```kotlin
class CameraActivity : BaseMirrorActivity<ActivityCameraBinding>() {

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private val cameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupMirroring()
        checkAndRequestCameraPermission()
        collectEvents()
    }

    private fun setupMirroring() {
        // 将左屏的 TextureView 内容镜像到右屏的 MirroringView
        mBindingPair.left.textureView.let { leftTexture ->
            mBindingPair.right.mirrorView.setSource(leftTexture)
            mBindingPair.right.mirrorView.startMirroring()
        }
    }

    private fun startCamera() {
        val textureView = mBindingPair.left.textureView

        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera()
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraId = "0"  // 主摄像头

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startPreview()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraDevice = null
                FLogger.e("CameraActivity", "Camera error: $error")
            }
        }, null)
    }

    private fun startPreview() {
        val texture = mBindingPair.left.textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(1280, 720)  // 设置预览分辨率

        val surface = Surface(texture)
        val previewRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequest.addTarget(surface)

        cameraDevice!!.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    session.setRepeatingRequest(previewRequest.build(), null, null)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    FToast.show("相机预览配置失败")
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
                        is TempleAction.Click -> capturePhoto()
                        is TempleAction.DoubleClick -> finish()
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 必须停止镜像，释放 Camera 资源
        mBindingPair.right.mirrorView.stopMirroring()
        cameraCaptureSession?.close()
        cameraDevice?.close()
    }
}
```

---

## 4. 帧捕获（拍照）

```kotlin
private fun capturePhoto() {
    val cameraDevice = cameraDevice ?: return
    val textureView = mBindingPair.left.textureView

    // 设置 ImageReader 接收拍照结果
    val imageReader = ImageReader.newInstance(
        1920, 1080,      // 拍照分辨率
        ImageFormat.JPEG,
        1                // 最多缓存 1 张
    )

    imageReader.setOnImageAvailableListener({ reader ->
        val image = reader.acquireLatestImage()
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        // bytes 是 JPEG 格式的图片数据
        processImage(bytes)
        image.close()
    }, null)

    val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
    captureRequest.addTarget(imageReader.surface)

    cameraCaptureSession?.capture(captureRequest.build(), null, null)
    FToast.show("拍照中...")
}
```

---

## 5. 资源管理最佳实践

```kotlin
// onPause：停止预览（节省电量和计算资源）
override fun onPause() {
    super.onPause()
    cameraCaptureSession?.stopRepeating()
}

// onResume：恢复预览
override fun onResume() {
    super.onResume()
    if (cameraDevice != null) {
        startPreview()
    }
}

// onDestroy：完全释放
override fun onDestroy() {
    super.onDestroy()
    mBindingPair.right.mirrorView.stopMirroring()  // ← 必须
    cameraCaptureSession?.close()
    cameraCaptureSession = null
    cameraDevice?.close()
    cameraDevice = null
}
```

---

## 下一步

- [`02-imu-sensors.md`](./02-imu-sensors.md)：IMU 传感器与头部姿态追踪

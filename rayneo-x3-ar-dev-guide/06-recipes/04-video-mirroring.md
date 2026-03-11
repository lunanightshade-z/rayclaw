# Recipe 4：视频播放双屏同步

视频播放需要使用 `SurfaceView` 或 `TextureView`，这类控件无法通过普通的 ViewBinding 方式镜像。本 Recipe 演示如何使用 `MirroringView` 实现视频的双屏同步。

---

## 目标效果

- 在 AR 眼镜上播放本地视频
- 左右两屏同步显示相同的视频内容
- 单击暂停/继续，双击退出

---

## 布局文件：activity_video.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!--
        BaseMirrorActivity 会将此布局分为左右两份。
        我们在布局中放置 TextureView（实际播放）和 MirroringView（镜像）。
        SDK 会把 TextureView 的内容实时镜像到 MirroringView 中。

        注意：在 BaseMirrorActivity 中，左屏包含 textureView，右屏包含 mirrorView。
        它们在同一个布局文件中，但 SDK 会正确地将其分配到左右屏。
    -->

    <!-- 视频实际播放到这里（出现在左屏区域） -->
    <TextureView
        android:id="@+id/textureView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 右屏镜像组件（将左屏 TextureView 的内容实时复制过来） -->
    <com.ffalcon.mercury.android.sdk.ui.wiget.MirroringView
        android:id="@+id/mirrorView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 播放控制层（叠加在视频上方，两屏都显示） -->
    <LinearLayout
        android:id="@+id/llControls"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="20dp"
        android:background="#80000000"
        android:orientation="horizontal"
        android:padding="8dp">

        <TextView
            android:id="@+id/tvPlayState"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="▶ 播放中"
            android:textColor="#FFFFFF"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/tvHint"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:text="单击暂停 · 双击退出"
            android:textColor="#80FFFFFF"
            android:textSize="14sp" />

    </LinearLayout>

</FrameLayout>
```

---

## Activity 实现

```kotlin
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast

class VideoPlayActivity : BaseMirrorActivity<ActivityVideoBinding>() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 步骤 1：设置镜像关系
        // 左屏的 textureView 作为视频渲染目标
        // 右屏的 mirrorView 实时镜像左屏内容
        setupMirroring()

        // 步骤 2：等待 TextureView 准备好后开始播放
        mBindingPair.left.textureView.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    startVideoPlayback(surface)
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) {}

                override fun onSurfaceTextureDestroyed(
                    surface: android.graphics.SurfaceTexture
                ): Boolean {
                    mediaPlayer?.stop()
                    return true
                }

                override fun onSurfaceTextureUpdated(
                    surface: android.graphics.SurfaceTexture
                ) {}
            }

        // 步骤 3：监听手势
        collectEvents()
    }

    private fun setupMirroring() {
        // 必须在 textureView 实际渲染内容之前调用 setSource 和 startMirroring
        mBindingPair.left.textureView.let { leftTexture ->
            mBindingPair.right.mirrorView.setSource(leftTexture)
            mBindingPair.right.mirrorView.startMirroring()
        }
    }

    private fun startVideoPlayback(surfaceTexture: android.graphics.SurfaceTexture) {
        try {
            val surface = Surface(surfaceTexture)

            mediaPlayer = MediaPlayer().apply {
                // 使用 assets 目录中的测试视频
                val assetFileDescriptor = assets.openFd("sample_video.mp4")
                setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                assetFileDescriptor.close()

                setSurface(surface)
                isLooping = true  // 循环播放
                prepareAsync()

                setOnPreparedListener { player ->
                    player.start()
                    isPlaying = true
                    updatePlayState()
                }

                setOnErrorListener { _, what, extra ->
                    FLogger.e("VideoActivity", "播放错误: what=$what, extra=$extra")
                    FToast.show("视频播放出错")
                    true
                }
            }
        } catch (e: Exception) {
            FLogger.e("VideoActivity", "无法播放视频: ${e.message}")
            FToast.show("无法播放视频")
        }
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.Click -> togglePlayPause()
                        is TempleAction.DoubleClick -> finish()
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun togglePlayPause() {
        val player = mediaPlayer ?: return

        if (isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            player.start()
            isPlaying = true
        }
        updatePlayState()
    }

    private fun updatePlayState() {
        mBindingPair.updateView {
            tvPlayState.text = if (isPlaying) "▶ 播放中" else "⏸ 已暂停"
        }
    }

    override fun onPause() {
        super.onPause()
        // 页面不可见时暂停播放（节省电量）
        if (isPlaying) {
            mediaPlayer?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        // 页面恢复时继续播放
        if (isPlaying) {
            mediaPlayer?.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 顺序重要：先停止 mirroring，再释放 MediaPlayer
        mBindingPair.right.mirrorView.stopMirroring()   // ← 必须调用，否则可能崩溃

        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }
}
```

---

## 关键点说明

### 为什么顺序是 setSource → startMirroring → 然后播放？

`MirroringView` 需要在 `TextureView` 开始接收内容之前就绑定好，才能完整捕获每一帧。如果先播放后绑定，会丢失最初的帧，甚至可能出现同步问题。

### 为什么 onDestroy 中必须先 stopMirroring？

`MirroringView` 内部持有对 `TextureView` 的引用，如果直接释放 MediaPlayer 导致 `TextureView` 的 Surface 销毁，而 `MirroringView` 还在尝试读取，会导致崩溃。`stopMirroring()` 正确地断开这个引用。

---

## 下一步

- [`05-camera-ar-overlay.md`](./05-camera-ar-overlay.md)：相机预览 + AR 信息叠加

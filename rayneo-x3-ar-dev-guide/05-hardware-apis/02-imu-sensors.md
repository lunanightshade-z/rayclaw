# IMU 传感器与头部姿态追踪

X3 眼镜内置完整的 IMU（惯性测量单元），可以精确追踪用户的头部姿态。这是实现 AR 内容"锁定"在空间中特定位置的基础。

---

## 1. 可用传感器

| 传感器 | Android Sensor 类型 | 数据 | 说明 |
|--------|---------------------|------|------|
| 加速度计 | `TYPE_ACCELEROMETER` | 三轴线性加速度 (m/s²) | 含重力 |
| 陀螺仪 | `TYPE_GYROSCOPE` | 三轴角速度 (rad/s) | 旋转速率 |
| 磁力计 | `TYPE_MAGNETIC_FIELD` | 三轴磁场强度 (μT) | 地磁方向 |
| **游戏旋转矢量** | `TYPE_GAME_ROTATION_VECTOR` | 四元数 (x, y, z, w) | **推荐用于头部姿态** |
| 旋转矢量 | `TYPE_ROTATION_VECTOR` | 四元数 | 使用磁力计，有绝对朝向 |

---

## 2. 推荐：游戏旋转矢量传感器

**为什么推荐 `TYPE_GAME_ROTATION_VECTOR`？**

- 融合了加速度计和陀螺仪的数据
- **不使用磁力计**，不受磁场干扰（金属环境、电磁设备旁不受影响）
- 数据平滑、延迟低，适合实时跟踪
- 缺点：偏航角（Yaw/左右转头）是**相对值**，没有绝对的"正北"参考，长时间使用会漂移

对于 AR 应用（需要实时响应头部运动），这些特性使它成为最佳选择。

---

## 3. 完整实现

```kotlin
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity

class IMUActivity : BaseMirrorActivity<ActivityImuBinding>(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gameRotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // 获取传感器实例
        gameRotationVectorSensor = sensorManager.getDefaultSensor(
            Sensor.TYPE_GAME_ROTATION_VECTOR
        )
        accelerometerSensor = sensorManager.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        )
        gyroscopeSensor = sensorManager.getDefaultSensor(
            Sensor.TYPE_GYROSCOPE
        )

        // 检查传感器可用性
        if (gameRotationVectorSensor == null) {
            FToast.show("设备不支持游戏旋转矢量传感器")
        }

        collectEvents()
    }

    override fun onResume() {
        super.onResume()
        // 在 onResume 中注册传感器
        gameRotationVectorSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME  // 适合实时追踪的采样率
            )
        }
        // 如果需要原始数据，也可以注册加速度计和陀螺仪
        // accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // gyroscopeSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        // 必须在 onPause 中注销，避免后台耗电
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                // event.values 包含四元数：[x*sin(θ/2), y*sin(θ/2), z*sin(θ/2), cos(θ/2)]
                val qx = event.values[0]
                val qy = event.values[1]
                val qz = event.values[2]
                val qw = event.values[3]

                // 转换为欧拉角（更直观）
                val euler = quaternionToEuler(qx, qy, qz, qw)
                val pitch = euler[0]  // 俯仰角：抬头/低头
                val roll = euler[1]   // 横滚角：左倾/右倾
                val yaw = euler[2]    // 偏航角：左转/右转（相对值）

                // 更新 UI（需要在主线程）
                runOnUiThread {
                    mBindingPair.updateView {
                        tvPitch.text = "俯仰角: %.1f°".format(Math.toDegrees(pitch.toDouble()))
                        tvRoll.text = "横滚角: %.1f°".format(Math.toDegrees(roll.toDouble()))
                        tvYaw.text = "偏航角: %.1f°（相对）".format(Math.toDegrees(yaw.toDouble()))
                    }
                }

                // 也可以直接使用四元数（用于 OpenGL/游戏引擎）
                onHeadPoseChanged(qx, qy, qz, qw)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                // 处理加速度数据
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                // 处理陀螺仪数据（角速度，rad/s）
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 传感器精度变化时的回调（通常可以忽略）
    }

    /**
     * 四元数转欧拉角
     * 返回 FloatArray [pitch（俯仰）, roll（横滚）, yaw（偏航）]，单位：弧度
     */
    private fun quaternionToEuler(x: Float, y: Float, z: Float, w: Float): FloatArray {
        val euler = FloatArray(3)

        // 俯仰角（绕 X 轴旋转）
        val sinP = 2.0f * (w * x + y * z)
        val cosP = 1.0f - 2.0f * (x * x + y * y)
        euler[0] = Math.atan2(sinP.toDouble(), cosP.toDouble()).toFloat()

        // 横滚角（绕 Y 轴旋转）
        val sinR = 2.0f * (w * y - z * x)
        euler[1] = if (Math.abs(sinR) >= 1) {
            (Math.PI / 2).toFloat() * Math.signum(sinR)  // 万向节锁情况
        } else {
            Math.asin(sinR.toDouble()).toFloat()
        }

        // 偏航角（绕 Z 轴旋转）
        val sinY = 2.0f * (w * z + x * y)
        val cosY = 1.0f - 2.0f * (y * y + z * z)
        euler[2] = Math.atan2(sinY.toDouble(), cosY.toDouble()).toFloat()

        return euler
    }

    private fun onHeadPoseChanged(qx: Float, qy: Float, qz: Float, qw: Float) {
        // 在这里根据头部姿态更新 AR 内容位置
        // 例如：将某个 AR 元素"锁定"在用户视野中心
    }
}
```

---

## 4. 采样率选择

| 常量 | 大约采样率 | 适用场景 |
|------|-----------|---------|
| `SENSOR_DELAY_FASTEST` | 尽可能快（~200Hz+）| 高精度实时追踪 |
| `SENSOR_DELAY_GAME` | ~50Hz | 游戏/AR 实时响应（**推荐**）|
| `SENSOR_DELAY_UI` | ~16Hz | UI 方向变化 |
| `SENSOR_DELAY_NORMAL` | ~5Hz | 缓慢变化的状态 |

对于 AR 头部追踪，推荐 `SENSOR_DELAY_GAME`，在精度和功耗之间取得良好平衡。

---

## 5. 实用场景：头部姿态驱动 AR 内容

```kotlin
// 根据俯仰角控制 AR 信息显示/隐藏
private fun onHeadPoseChanged(qx: Float, qy: Float, qz: Float, qw: Float) {
    val euler = quaternionToEuler(qx, qy, qz, qw)
    val pitchDegrees = Math.toDegrees(euler[0].toDouble()).toFloat()

    // 低头超过 15 度时显示地面信息，抬头时隐藏
    val showGroundInfo = pitchDegrees < -15f

    runOnUiThread {
        mBindingPair.updateView {
            groundInfoPanel.visibility = if (showGroundInfo) View.VISIBLE else View.GONE
        }
    }
}
```

---

## 6. 注意事项

- **必须在 `onPause` 注销**：传感器持续工作会大量消耗电量
- **UI 更新要在主线程**：`onSensorChanged` 在传感器线程回调，用 `runOnUiThread` 或协程切换到主线程
- **偏航角会漂移**：`TYPE_GAME_ROTATION_VECTOR` 的偏航角是相对值，长时间使用会漂移。如需绝对朝向参考，使用 `TYPE_ROTATION_VECTOR`（但受磁场影响）
- **四元数 vs 欧拉角**：四元数没有万向节锁问题，适合数学计算；欧拉角更直观，适合 UI 展示

---

## 下一步

- [`03-device-state.md`](./03-device-state.md)：设备连接状态与手机 GPS 推流

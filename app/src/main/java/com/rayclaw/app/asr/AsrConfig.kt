package com.rayclaw.app.asr

import com.rayclaw.app.BuildConfig
import com.rayclaw.app.RuntimeConfig

/**
 * ════════════════════════════════════════════════
 *  语音识别（ASR）配置
 *
 *  优先级（高→低）：
 *   1. rayclaw.conf（运行时，开发者通过 adb push 配置，无需重新编译）
 *   2. local.properties → BuildConfig（编译时注入，baked into APK）
 *
 *  rayclaw.conf 示例：
 *      DASHSCOPE_API_KEY=sk-xxxxxxxxxxxx
 *
 *  申请地址：https://dashscope.console.aliyun.com/ → API-KEY 管理
 * ════════════════════════════════════════════════
 */
object AsrConfig {

    // 运行时配置优先，fallback 到编译时 BuildConfig
    val DASHSCOPE_API_KEY: String
        get() = RuntimeConfig.get("DASHSCOPE_API_KEY", BuildConfig.DASHSCOPE_API_KEY)

    // ASR 模型（支持中英日韩法德西俄等多语言实时识别）
    const val MODEL = "paraformer-realtime-v2"

    // WebSocket 端点
    const val WS_ENDPOINT = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"

    // 音频参数（paraformer-realtime-v2 要求 16kHz 单声道 16-bit PCM）
    const val SAMPLE_RATE = 16000

    // 每帧字节数 = 100ms @ 16kHz 16-bit mono = 16000 * 2 * 0.1 = 3200 bytes
    const val FRAME_BYTES = 3200
}

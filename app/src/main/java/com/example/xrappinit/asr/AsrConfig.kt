package com.example.xrappinit.asr

import com.example.xrappinit.BuildConfig

/**
 * ════════════════════════════════════════════════
 *  语音识别（ASR）配置
 *
 *  API Key 存放在项目根目录的 local.properties（已被 .gitignore 排除）：
 *
 *      DASHSCOPE_API_KEY=sk-xxxxxxxxxxxx
 *
 *  构建时由 build.gradle.kts 读取并注入 BuildConfig，
 *  源代码中不出现任何明文密钥。
 *
 *  申请地址：https://dashscope.console.aliyun.com/ → API-KEY 管理
 * ════════════════════════════════════════════════
 */
object AsrConfig {

    // 从 BuildConfig 读取，Key 来源于 local.properties
    val DASHSCOPE_API_KEY: String get() = BuildConfig.DASHSCOPE_API_KEY

    // ASR 模型（支持中英日韩法德西俄等多语言实时识别）
    const val MODEL = "paraformer-realtime-v2"

    // WebSocket 端点
    const val WS_ENDPOINT = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"

    // 音频参数（paraformer-realtime-v2 要求 16kHz 单声道 16-bit PCM）
    const val SAMPLE_RATE = 16000

    // 每帧字节数 = 100ms @ 16kHz 16-bit mono = 16000 * 2 * 0.1 = 3200 bytes
    const val FRAME_BYTES = 3200
}

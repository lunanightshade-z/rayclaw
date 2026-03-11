package com.example.xrappinit.agent

import com.example.xrappinit.BuildConfig

/**
 * ════════════════════════════════════════════════
 *  OpenClaw 智能体网关配置
 *
 *  凭证存放在项目根目录的 local.properties（已被 .gitignore 排除）：
 *
 *      OPENCLAW_GATEWAY_TOKEN=your-token-here
 *      OPENCLAW_BASE_URL=http://your-server:18789
 *      OPENCLAW_AGENT_ID=main
 *
 *  构建时由 build.gradle.kts 读取并注入 BuildConfig。
 * ════════════════════════════════════════════════
 */
object AgentConfig {

    val GATEWAY_TOKEN: String get() = BuildConfig.OPENCLAW_GATEWAY_TOKEN

    val BASE_URL: String get() = BuildConfig.OPENCLAW_BASE_URL

    val AGENT_ID: String get() = BuildConfig.OPENCLAW_AGENT_ID

    /** HTTP 请求超时（秒） */
    const val TIMEOUT_SECONDS: Long = 120

    /** 用于会话关联的用户标识（固定值可保持多轮上下文） */
    const val USER_ID: String = "ar-glasses-user"
}

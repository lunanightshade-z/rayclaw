package com.openclaw.app.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * OpenClaw 智能体 HTTP SSE 流式客户端。
 *
 * 协议：POST /v1/responses，stream=true，返回 SSE 事件流。
 * 事件类型：
 *  - response.output_text.delta → 增量文本
 *  - response.completed → 完成
 *
 * 所有方法均为阻塞调用，请在 IO 线程执行。
 */
class OpenClawClient(
    private val baseUrl: String,
    private val agentId: String,
    private val token: String,
    private val user: String,
    private val timeoutSeconds: Long = 120
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 向智能体发送消息并流式接收回答。
     *
     * @param text       用户消息
     * @param onDelta    每收到一个增量文本片段时回调（参数：增量, 已累积全文）
     * @param onComplete 回答完成时回调（参数：完整回答文本）
     * @param onError    出错时回调（参数：错误描述）
     */
    fun askStreaming(
        text: String,
        onDelta: (delta: String, fullText: String) -> Unit,
        onComplete: (fullText: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        val payload = JSONObject().apply {
            put("model", "openclaw:$agentId")
            put("input", text)
            put("user", user)
            put("stream", true)
        }

        val body = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/responses")
            .post(body)
            .header("Accept", "text/event-stream")
            .header("x-openclaw-agent-id", agentId)

        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200) ?: "unknown error"
                response.close()
                onError("HTTP ${response.code}: $errBody")
                return
            }

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                response.close()
                onError("响应体为空")
                return
            }

            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val fullText = StringBuilder()
            var eventType: String? = null
            val dataLines = mutableListOf<String>()

            try {
                while (true) {
                    val line = reader.readLine() ?: break

                    // 空行 = SSE 事件边界
                    if (line.isEmpty()) {
                        if (dataLines.isEmpty()) {
                            eventType = null
                            continue
                        }
                        val dataPayload = dataLines.joinToString("\n")
                        dataLines.clear()

                        if (dataPayload == "[DONE]") break

                        try {
                            val event = JSONObject(dataPayload)
                            val type = event.optString("type").ifEmpty { eventType }

                            when (type) {
                                "response.output_text.delta" -> {
                                    val delta = event.optString("delta")
                                    if (delta.isNotEmpty()) {
                                        fullText.append(delta)
                                        onDelta(delta, fullText.toString())
                                    }
                                }
                                "response.completed" -> {
                                    val respObj = event.optJSONObject("response")
                                    val outputText = respObj?.optString("output_text")
                                    if (!outputText.isNullOrBlank()) {
                                        onComplete(outputText)
                                    } else {
                                        onComplete(fullText.toString())
                                    }
                                    return
                                }
                            }
                        } catch (_: Exception) {
                            // 跳过格式异常的 JSON
                        }
                        eventType = null
                        continue
                    }

                    // SSE 注释行
                    if (line.startsWith(":")) continue

                    // event: <type>
                    if (line.startsWith("event:")) {
                        eventType = line.substringAfter("event:").trim()
                        continue
                    }

                    // data: <json>
                    if (line.startsWith("data:")) {
                        dataLines.add(line.substringAfter("data:").trimStart())
                        continue
                    }
                }
            } finally {
                reader.close()
                response.close()
            }

            // 如果没有收到 response.completed 事件，也返回已累积的文本
            onComplete(fullText.toString())
        } catch (e: Exception) {
            onError("请求失败: ${e.message}")
        }
    }

    fun release() {
        client.dispatcher.executorService.shutdown()
    }
}

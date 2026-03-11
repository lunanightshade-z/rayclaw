package com.example.xrappinit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.ColorInt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.xrappinit.agent.AgentConfig
import com.example.xrappinit.agent.OpenClawClient
import com.example.xrappinit.asr.AppLanguage
import com.example.xrappinit.asr.SpeechEngine
import com.example.xrappinit.databinding.ActivityAgentChatBinding
import com.example.xrappinit.ui.MarkdownRenderer
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AgentChatActivity : BaseMirrorActivity<ActivityAgentChatBinding>() {

    private var speechEngine: SpeechEngine? = null
    private var openClawClient: OpenClawClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isListening = false
    private var isProcessing = false

    private val languages = AppLanguage.entries
    private var langIndex = 0

    // Current conversation session state
    private var sessionTurnCount = 0

    private var latestResponseMarkdown = ""
    private var responseScrollToBottom = true
    private val responseRenderRunnable = Runnable {
        renderResponseMarkdownNow(scrollToBottom = responseScrollToBottom)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureResponseWebViewsConfigured()
        initOpenClawClient()
        renderIdleUI()
        if (hasMicPermission()) {
            initSpeechEngine()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQ_MIC
            )
        }
        collectTempleActions()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(responseRenderRunnable)
        speechEngine?.release()
        openClawClient?.release()
        super.onDestroy()
    }

    // ─────────────────────── Init ───────────────────────

    private fun initOpenClawClient() {
        openClawClient = OpenClawClient(
            baseUrl = AgentConfig.BASE_URL,
            agentId = AgentConfig.AGENT_ID,
            token = AgentConfig.GATEWAY_TOKEN,
            user = AgentConfig.USER_ID,
            timeoutSeconds = AgentConfig.TIMEOUT_SECONDS
        )
    }

    private fun initSpeechEngine() {
        speechEngine = SpeechEngine(
            context = this,
            onPartial = { text ->
                setSpeechInput("$text…", isPartial = true)
            },
            onFinal = { text ->
                setSpeechInput(text, isPartial = false)
                askAgent(text)
            },
            onError = { msg ->
                setStatus("ASR 错误: $msg", COLOR_ERROR)
                isListening = false
            }
        )
    }

    // ─────────────────────── UI state ───────────────────────

    private fun renderIdleUI() {
        mBindingPair.updateView {
            tvStatus.text = "单击镜腿开始对话"
            tvStatus.setTextColor(COLOR_IDLE)
            tvLanguage.text = "语音: ${languages[langIndex].displayName}"
            tvProvider.text = "OpenClaw"
            tvUserInput.text = ""
            tvUserInput.setTextColor(COLOR_INPUT_EMPTY)
        }
        setResponseMarkdown("", immediate = true, scrollToBottom = false)
    }

    private fun setStatus(text: String, @ColorInt color: Int = COLOR_IDLE) {
        mBindingPair.updateView {
            tvStatus.text = text
            tvStatus.setTextColor(color)
        }
    }

    private fun setSpeechInput(text: String, isPartial: Boolean = false) {
        mBindingPair.updateView {
            tvUserInput.text = text
            tvUserInput.setTextColor(
                when {
                    text.isBlank() -> COLOR_INPUT_EMPTY
                    isPartial      -> COLOR_INPUT_PARTIAL
                    else           -> COLOR_INPUT_FINAL
                }
            )
        }
    }

    private fun setResponseMarkdown(
        markdown: String,
        immediate: Boolean = false,
        scrollToBottom: Boolean = true
    ) {
        latestResponseMarkdown = markdown
        responseScrollToBottom = scrollToBottom
        mainHandler.removeCallbacks(responseRenderRunnable)
        if (immediate) {
            mainHandler.post(responseRenderRunnable)
        } else {
            mainHandler.postDelayed(responseRenderRunnable, STREAM_RENDER_THROTTLE_MS)
        }
    }

    private fun refreshAsrLanguageLabel() {
        mBindingPair.updateView {
            tvLanguage.text = "语音: ${languages[langIndex].displayName}"
        }
    }

    // ─────────────────────── Session sidebar ───────────────────────

    private fun addSession() {
        sessionTurnCount++
        updateSidebarSession()
    }

    private fun updateSidebarSession() {
        val text = if (sessionTurnCount > 0) {
            "●  当前对话\n    已交流 $sessionTurnCount 轮"
        } else {
            ""
        }
        mBindingPair.updateView { tvSessions.text = text }
    }

    // ─────────────────────── WebView ───────────────────────

    private fun ensureResponseWebViewsConfigured() {
        mBindingPair.updateView {
            configureResponseWebView(wvResponse)
        }
    }

    private fun configureResponseWebView(webView: WebView) {
        if (webView.tag == WEBVIEW_READY_TAG) return
        webView.tag = WEBVIEW_READY_TAG
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            defaultTextEncodingName = "utf-8"
        }
        webView.loadDataWithBaseURL(
            ASSET_BASE_URL,
            MarkdownRenderer.buildHtmlDocument(""),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun renderResponseMarkdownNow(scrollToBottom: Boolean) {
        val html = MarkdownRenderer.buildHtmlDocument(latestResponseMarkdown)
        mBindingPair.updateView {
            configureResponseWebView(wvResponse)
            wvResponse.loadDataWithBaseURL(
                ASSET_BASE_URL,
                html,
                "text/html",
                "utf-8",
                null
            )
            if (scrollToBottom) {
                wvResponse.postDelayed({ wvResponse.scrollTo(0, Int.MAX_VALUE) }, 60L)
            } else {
                wvResponse.scrollTo(0, 0)
            }
        }
    }

    // ─────────────────────── Agent interaction ───────────────────────

    private fun askAgent(text: String) {
        if (isProcessing) return
        isProcessing = true
        setStatus("AI 思考中…", COLOR_THINKING)
        setResponseMarkdown("", immediate = true, scrollToBottom = false)

        lifecycleScope.launch(Dispatchers.IO) {
            openClawClient?.askStreaming(
                text = text,
                onDelta = { _, fullText ->
                    mainHandler.post {
                        setResponseMarkdown(
                            markdown = fullText,
                            immediate = false,
                            scrollToBottom = true
                        )
                    }
                },
                onComplete = { finalText ->
                    mainHandler.post {
                        isProcessing = false
                        setResponseMarkdown(
                            markdown = finalText,
                            immediate = true,
                            scrollToBottom = true
                        )
                        addSession()
                        if (isListening) {
                            setStatus("正在监听 ${languages[langIndex].displayName}…", COLOR_LISTENING)
                        } else {
                            setStatus("已暂停，单击继续", COLOR_IDLE)
                        }
                    }
                },
                onError = { error ->
                    mainHandler.post {
                        isProcessing = false
                        setResponseMarkdown(
                            markdown = "⚠ $error",
                            immediate = true,
                            scrollToBottom = false
                        )
                        if (isListening) {
                            setStatus("正在监听 ${languages[langIndex].displayName}…", COLOR_LISTENING)
                        } else {
                            setStatus("已暂停，单击继续", COLOR_IDLE)
                        }
                    }
                }
            )
        }
    }

    // ─────────────────────── Speech control ───────────────────────

    private fun startListening() {
        val langHints = listOf(languages[langIndex].code)
        speechEngine?.start(langHints)
        setStatus("正在监听 ${languages[langIndex].displayName}…", COLOR_LISTENING)
    }

    private fun stopListening() {
        speechEngine?.stop()
    }

    private fun toggleListening() {
        if (speechEngine == null) {
            FToast.show("语音引擎未就绪")
            return
        }
        isListening = !isListening
        if (isListening) {
            startListening()
            FToast.show("开始监听: ${languages[langIndex].displayName}")
        } else {
            stopListening()
            setStatus("已暂停，单击继续", COLOR_IDLE)
        }
    }

    private fun switchAsrLanguage(direction: Int) {
        val wasListening = isListening
        if (wasListening) {
            stopListening()
            isListening = false
        }
        langIndex = (langIndex + direction + languages.size) % languages.size
        refreshAsrLanguageLabel()
        FToast.show("语音语言: ${languages[langIndex].displayName}")
        if (wasListening) {
            isListening = true
            startListening()
        }
    }

    private fun clearDisplay() {
        sessionTurnCount = 0
        setSpeechInput("", isPartial = false)
        setResponseMarkdown("", immediate = true, scrollToBottom = false)
        mBindingPair.updateView { tvSessions.text = "" }
        val (statusText, color) = if (isListening) {
            "正在监听 ${languages[langIndex].displayName}…" to COLOR_LISTENING
        } else {
            "单击镜腿开始对话" to COLOR_IDLE
        }
        setStatus(statusText, color)
        FToast.show("已清空")
    }

    // ─────────────────────── Gesture actions ───────────────────────

    private fun collectTempleActions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.Click        -> toggleListening()
                        is TempleAction.DoubleClick  -> { FToast.show("退出"); finish() }
                        is TempleAction.SlideForward -> switchAsrLanguage(+1)
                        is TempleAction.SlideBackward -> switchAsrLanguage(-1)
                        is TempleAction.LongClick    -> clearDisplay()
                        else -> Unit
                    }
                }
            }
        }
    }

    // ─────────────────────── Permissions ───────────────────────

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            initSpeechEngine()
        } else {
            setStatus("需要麦克风权限才能使用", COLOR_ERROR)
        }
    }

    companion object {
        private const val REQ_MIC = 100
        private const val STREAM_RENDER_THROTTLE_MS = 120L
        private const val ASSET_BASE_URL = "file:///android_asset/"
        private const val WEBVIEW_READY_TAG = "agent_response_webview_ready"

        // Status text colors
        @ColorInt private val COLOR_IDLE      = Color.parseColor("#1E2D4A")
        @ColorInt private val COLOR_LISTENING = Color.parseColor("#00D48A")
        @ColorInt private val COLOR_THINKING  = Color.parseColor("#FFB020")
        @ColorInt private val COLOR_ERROR     = Color.parseColor("#FF6B6B")

        // User input text colors
        @ColorInt private val COLOR_INPUT_EMPTY   = Color.parseColor("#1A2840")
        @ColorInt private val COLOR_INPUT_PARTIAL = Color.parseColor("#4A7099")
        @ColorInt private val COLOR_INPUT_FINAL   = Color.parseColor("#7AAED4")
    }
}

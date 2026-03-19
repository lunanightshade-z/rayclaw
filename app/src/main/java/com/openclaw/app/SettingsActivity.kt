package com.openclaw.app

import android.graphics.Color
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.openclaw.app.asr.AppLanguage
import com.openclaw.app.databinding.ActivitySettingsBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import kotlinx.coroutines.launch

/**
 * In-glasses settings screen.
 *
 * Launched from [AgentChatActivity] via a TripleClick gesture.
 * Language and listen-mode changes are held in temporary state and only committed
 * to [AppSettings] when the user double-clicks (save) or selects the reset row.
 * Triple-click cancels without saving.
 *
 * Rows:
 *  0 — 监听模式   (cycle: 持续监听 / 单次识别)
 *  1 — 识别语言   (cycle: 8 languages)
 *  2 — 重置对话   (action: sets AppSettings.pendingReset, saves, exits)
 *
 * Gesture map:
 *  SlideUpwards   / SlideDownwards  → navigate between rows
 *  SlideForward   / SlideBackward   → cycle the selected row's value
 *  Click                            → cycle forward / confirm action row
 *  DoubleClick                      → save language + mode, exit
 *  TripleClick                      → cancel and exit (no save)
 *
 * NOTE: LongClick deliberately NOT used — it triggers the system settings panel.
 */
class SettingsActivity : BaseMirrorActivity<ActivitySettingsBinding>() {

    // ── Temporary editing state (not persisted until DoubleClick or reset) ────

    private val languages   = AppLanguage.entries
    private val listenModes = listOf(
        "continuous" to "持续监听",
        "oneshot"    to "单次识别"
    )

    private var selectedRow     = 0   // 0 = listen mode, 1 = language, 2 = reset
    private var langIndex       = 0   // index into AppLanguage.entries
    private var listenModeIndex = 0   // index into listenModes

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadCurrentSettings()
        renderSettings()
        collectTempleActions()
    }

    // ── Initialisation ─────────────────────────────────────────────────────────

    /** Populate temp state from whatever AppSettings currently holds. */
    private fun loadCurrentSettings() {
        langIndex = languages.indexOfFirst { it.code == AppSettings.language }
            .coerceAtLeast(0)
        listenModeIndex = listenModes.indexOfFirst { it.first == AppSettings.listenModeKey }
            .coerceAtLeast(0)
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    private fun renderSettings() {
        val listenLabel   = listenModes[listenModeIndex].second
        val languageLabel = languages[langIndex].displayName

        mBindingPair.updateView {
            // ── Listen Mode row ────────────────────────────────────────────────
            val listenSel = selectedRow == ROW_LISTEN_MODE
            rowListenMode.setBackgroundColor(
                if (listenSel) COLOR_ROW_SELECTED else Color.TRANSPARENT
            )
            tvListenModeLabel.setTextColor(
                if (listenSel) COLOR_LABEL_ACTIVE else COLOR_LABEL_IDLE
            )
            tvListenModeValue.text = if (listenSel) "← $listenLabel →" else listenLabel
            tvListenModeValue.setTextColor(
                if (listenSel) COLOR_VALUE_ACTIVE else COLOR_VALUE_IDLE
            )

            // ── Language row ───────────────────────────────────────────────────
            val langSel = selectedRow == ROW_LANGUAGE
            rowLanguage.setBackgroundColor(
                if (langSel) COLOR_ROW_SELECTED else Color.TRANSPARENT
            )
            tvLanguageLabel.setTextColor(
                if (langSel) COLOR_LABEL_ACTIVE else COLOR_LABEL_IDLE
            )
            tvLanguageValue.text = if (langSel) "← $languageLabel →" else languageLabel
            tvLanguageValue.setTextColor(
                if (langSel) COLOR_VALUE_ACTIVE else COLOR_VALUE_IDLE
            )

            // ── Reset row (action, no cycle arrows) ────────────────────────────
            val resetSel = selectedRow == ROW_RESET
            rowReset.setBackgroundColor(
                if (resetSel) COLOR_RESET_ROW_SELECTED else Color.TRANSPARENT
            )
            tvResetLabel.setTextColor(
                if (resetSel) COLOR_RESET_ACTIVE else COLOR_RESET_IDLE
            )
        }
    }

    // ── Gesture handling ───────────────────────────────────────────────────────

    private fun collectTempleActions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.SlideUpwards   -> navigateRow(-1)
                        is TempleAction.SlideDownwards -> navigateRow(+1)
                        is TempleAction.SlideForward   -> cycleOrConfirm(+1)
                        is TempleAction.SlideBackward  -> cycleOrConfirm(-1)
                        is TempleAction.Click          -> cycleOrConfirm(+1)
                        is TempleAction.DoubleClick    -> saveAndExit()
                        is TempleAction.TripleClick    -> cancelAndExit()
                        // LongClick intentionally absent — would trigger system settings panel
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun navigateRow(delta: Int) {
        selectedRow = (selectedRow + delta + ROW_COUNT) % ROW_COUNT
        renderSettings()
    }

    private fun cycleOrConfirm(delta: Int) {
        when (selectedRow) {
            ROW_LISTEN_MODE -> {
                listenModeIndex = (listenModeIndex + delta + listenModes.size) % listenModes.size
                FToast.show(listenModes[listenModeIndex].second)
                renderSettings()
            }
            ROW_LANGUAGE -> {
                langIndex = (langIndex + delta + languages.size) % languages.size
                FToast.show(languages[langIndex].displayName)
                renderSettings()
            }
            ROW_RESET -> {
                // Immediate action: save current settings and request a conversation reset
                AppSettings.pendingReset = true
                saveAndExit()
            }
        }
    }

    private fun saveAndExit() {
        AppSettings.language      = languages[langIndex].code
        AppSettings.listenModeKey = listenModes[listenModeIndex].first
        if (AppSettings.pendingReset) {
            FToast.show("已保存，对话已重置")
        } else {
            FToast.show("设置已保存")
        }
        finish()
    }

    private fun cancelAndExit() {
        FToast.show("已取消")
        finish()
    }

    // ── Constants ──────────────────────────────────────────────────────────────

    companion object {
        private const val ROW_COUNT       = 3
        private const val ROW_LISTEN_MODE = 0
        private const val ROW_LANGUAGE    = 1
        private const val ROW_RESET       = 2

        @ColorInt private val COLOR_ROW_SELECTED       = Color.parseColor("#1500C896")
        @ColorInt private val COLOR_LABEL_ACTIVE       = Color.parseColor("#00C896")
        @ColorInt private val COLOR_LABEL_IDLE         = Color.parseColor("#484838")
        @ColorInt private val COLOR_VALUE_ACTIVE       = Color.parseColor("#EEE8DC")
        @ColorInt private val COLOR_VALUE_IDLE         = Color.parseColor("#484838")

        // Reset row uses orange/warning palette
        @ColorInt private val COLOR_RESET_ROW_SELECTED = Color.parseColor("#15FF6B35")
        @ColorInt private val COLOR_RESET_ACTIVE       = Color.parseColor("#FF6B35")
        @ColorInt private val COLOR_RESET_IDLE         = Color.parseColor("#4A3020")
    }
}

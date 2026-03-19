package com.openclaw.app.translation

/**
 * Translation engine abstraction interface.
 *
 * [fromLang] uses unified language codes: en / ja / ko / fr / de / es / ru
 * Returns translated text, or a string starting with "⚠" on error.
 *
 * Note: AppLanguage enum has been moved to com.openclaw.app.asr.AppLanguage.
 */
interface TranslationProvider {
    val name: String
    val isConfigured: Boolean
    suspend fun translate(text: String, fromLang: String): String
}

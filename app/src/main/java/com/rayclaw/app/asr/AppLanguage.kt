package com.rayclaw.app.asr

/** Speech recognition language options for the AR glasses ASR engine. */
enum class AppLanguage(val code: String, val displayName: String, val locale: String) {
    CHINESE("zh",  "中文",     "zh-CN"),
    ENGLISH("en",  "英语",     "en-US"),
    JAPANESE("ja", "日语",     "ja-JP"),
    KOREAN("ko",   "韩语",     "ko-KR"),
    FRENCH("fr",   "法语",     "fr-FR"),
    GERMAN("de",   "德语",     "de-DE"),
    SPANISH("es",  "西班牙语", "es-ES"),
    RUSSIAN("ru",  "俄语",     "ru-RU");

    companion object {
        fun fromCode(code: String) = entries.first { it.code == code }
    }
}

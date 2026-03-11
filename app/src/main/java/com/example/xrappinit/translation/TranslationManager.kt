package com.example.xrappinit.translation

import com.example.xrappinit.translation.providers.*

/**
 * 翻译管理器：根据 [TranslationConfig.activeProvider] 返回对应的引擎实例。
 * 调用 [translate] 即可，无需关心具体引擎。
 */
object TranslationManager {

    private fun buildProvider(): TranslationProvider =
        when (TranslationConfig.activeProvider) {
            TranslationConfig.ProviderType.MY_MEMORY -> MyMemoryProvider()
            TranslationConfig.ProviderType.BAIDU     -> BaiduProvider()
            TranslationConfig.ProviderType.YOUDAO    -> YoudaoProvider()
            TranslationConfig.ProviderType.TENCENT   -> TencentProvider()
            TranslationConfig.ProviderType.DEEPL     -> DeepLProvider()
            TranslationConfig.ProviderType.AZURE     -> AzureProvider()
        }

    val currentProviderName: String
        get() = TranslationConfig.activeProvider.displayName

    /** 翻译入口，所有异常已在内部捕获，失败时返回错误说明字符串 */
    suspend fun translate(text: String, fromLang: String): String {
        if (text.isBlank()) return ""
        val provider = buildProvider()
        if (!provider.isConfigured) {
            return "⚠ ${provider.name} 未配置凭证，请编辑 TranslationConfig.kt"
        }
        return try {
            provider.translate(text.trim(), fromLang)
        } catch (e: Exception) {
            "⚠ 翻译失败：${e.message?.take(60)}"
        }
    }
}

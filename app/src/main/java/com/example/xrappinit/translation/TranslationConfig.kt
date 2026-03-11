package com.example.xrappinit.translation

/**
 * ════════════════════════════════════════════════
 *  翻译引擎配置文件
 *  修改步骤：
 *  1. 设置 [activeProvider] 指定要使用的引擎
 *  2. 在对应 object 中填入 API 凭证
 *  3. 重新编译并推送到眼镜即可
 * ════════════════════════════════════════════════
 */
object TranslationConfig {

    /**
     * ★ 在此选择当前使用的翻译引擎 ★
     *   MY_MEMORY  — 免费，无需 Key，国内可用，每日 5K 字符
     *   BAIDU      — 百度翻译，国内最稳定，免费额度充足
     *   YOUDAO     — 有道智云，质量较高，国内可用
     *   TENCENT    — 腾讯翻译君，国内可用
     *   DEEPL      — 高质量，500K/月免费，需 VPN
     *   AZURE      — 微软翻译，2M/月免费，需 VPN
     */
    var activeProvider: ProviderType = ProviderType.MY_MEMORY

    enum class ProviderType(val displayName: String, val usable: String) {
        MY_MEMORY("MyMemory（免费）",  "国内可用，5K字/天"),
        BAIDU("百度翻译",              "国内可用，需 AppID+Key"),
        YOUDAO("有道智云",             "国内可用，需 AppKey+Secret"),
        TENCENT("腾讯翻译",            "国内可用，需 SecretId+Key"),
        DEEPL("DeepL",                "需 VPN，500K字/月免费"),
        AZURE("微软 Azure Translator", "需 VPN，2M字/月免费"),
    }

    // ─────────────────────────────────────────────
    // MyMemory（免费，无需注册）
    // 申请更高配额：https://mymemory.translated.net/doc/usagelimits.php
    // 填入注册邮箱可将每日限额从 5K 提升到 50K 字符
    // ─────────────────────────────────────────────
    object MyMemory {
        const val EMAIL = ""  // 可选，填入邮箱提升限额
    }

    // ─────────────────────────────────────────────
    // 百度翻译 API
    // 申请：https://fanyi-api.baidu.com/
    // 通用版免费 100 万字符/月，高级版 200 万字符/月
    // ─────────────────────────────────────────────
    object Baidu {
        const val APP_ID     = ""
        const val SECRET_KEY = ""
    }

    // ─────────────────────────────────────────────
    // 有道智云翻译
    // 申请：https://ai.youdao.com/
    // 注册后有免费额度，超出按量计费
    // ─────────────────────────────────────────────
    object Youdao {
        const val APP_KEY    = ""
        const val APP_SECRET = ""
    }

    // ─────────────────────────────────────────────
    // 腾讯云翻译（TMT）
    // 申请：https://cloud.tencent.com/product/tmt
    // 每月 500 万字符免费
    // ─────────────────────────────────────────────
    object Tencent {
        const val SECRET_ID  = ""
        const val SECRET_KEY = ""
        const val REGION     = "ap-guangzhou"  // 可选：ap-beijing / ap-shanghai
    }

    // ─────────────────────────────────────────────
    // DeepL（中国大陆需要 VPN）
    // 申请：https://www.deepl.com/pro-api
    // 免费版 500K 字符/月，付费版按用量
    // ─────────────────────────────────────────────
    object DeepL {
        const val API_KEY      = ""
        const val USE_FREE_API = true  // true=免费版 false=付费版（域名不同）
    }

    // ─────────────────────────────────────────────
    // 微软 Azure Translator（中国大陆需要 VPN）
    // 申请：https://azure.microsoft.com/zh-cn/products/ai-services/translator
    // 免费 F0 套餐：2M 字符/月
    // ─────────────────────────────────────────────
    object Azure {
        const val SUBSCRIPTION_KEY = ""
        const val REGION           = "eastasia"  // 推荐选亚太区，延迟低
    }
}

package com.openclaw.app

import android.app.Application
import com.ffalcon.mercury.android.sdk.MercurySDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MercurySDK.init(this)
        // AppSettings must be initialised first so it can serve as the fallback tier
        // for AsrConfig / AgentConfig before RuntimeConfig loads openclaw.conf overrides.
        AppSettings.init(this)
        // RuntimeConfig must be initialised before any Config object (AgentConfig / AsrConfig)
        // is first accessed, so that runtime overrides take effect immediately.
        RuntimeConfig.init(this)
    }
}

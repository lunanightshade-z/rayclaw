package com.rayclaw.app

import android.app.Application
import com.ffalcon.mercury.android.sdk.MercurySDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MercurySDK.init(this)
        // RuntimeConfig must be initialised before any Config object (AgentConfig / AsrConfig)
        // is first accessed, so that runtime overrides take effect immediately.
        RuntimeConfig.init(this)
    }
}

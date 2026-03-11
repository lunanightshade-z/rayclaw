package com.example.xrappinit

import android.app.Application
import com.ffalcon.mercury.android.sdk.MercurySDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MercurySDK.init(this)
    }
}

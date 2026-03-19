package com.openclaw.app

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Properties

/**
 * Runtime configuration overlay for openclaw.
 *
 * Reads `openclaw.conf` from the app's external-files directory at startup,
 * allowing any developer to override API keys and server URLs **without
 * recompiling**. Values found here take precedence over BuildConfig
 * (which is baked in at compile-time from local.properties).
 *
 * ─────────────────────────────────────────────────────────────────────
 *  HOW TO USE (developer quickstart)
 * ─────────────────────────────────────────────────────────────────────
 *  1. Copy the template in the project root:
 *       openclaw.conf.template  →  openclaw.conf
 *
 *  2. Fill in your own keys:
 *       DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx
 *       OPENCLAW_BASE_URL=http://192.168.x.x:18789
 *       OPENCLAW_GATEWAY_TOKEN=your-token
 *       OPENCLAW_AGENT_ID=main
 *
 *  3. Push to the glasses once:
 *       adb push openclaw.conf /sdcard/Android/data/com.openclaw.app/files/openclaw.conf
 *
 *  4. (Re-)launch the app — no rebuild needed.
 *
 *  To update a key later, edit the file and push again, then relaunch the app.
 *  To remove a key and fall back to BuildConfig, delete it from the file.
 * ─────────────────────────────────────────────────────────────────────
 *
 *  The file lives in the app's *own* external-files directory, so no
 *  READ_EXTERNAL_STORAGE permission is required (Android 4.4+).
 */
object RuntimeConfig {

    private const val TAG         = "RuntimeConfig"
    private const val CONFIG_FILE = "openclaw.conf"

    private val props  = Properties()
    private var loaded = false

    /**
     * Call once from [MyApplication.onCreate] before any config is consumed.
     */
    fun init(context: Context) {
        val file = File(context.getExternalFilesDir(null), CONFIG_FILE)
        if (!file.exists()) {
            Log.i(TAG, "No runtime config at ${file.absolutePath} — using BuildConfig values")
            return
        }
        try {
            file.inputStream().bufferedReader().use { reader ->
                props.load(reader)
            }
            loaded = true
            Log.i(TAG, "Runtime config loaded: ${file.absolutePath} (${props.size} key(s))")
            props.stringPropertyNames().forEach { key ->
                // Log keys but NOT values — avoid leaking secrets to logcat
                Log.d(TAG, "  key present: $key")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load runtime config: ${e.message}")
        }
    }

    /**
     * Returns the runtime value for [key] if the config file was loaded and
     * contains the key; otherwise returns [fallback] (typically a BuildConfig value).
     */
    fun get(key: String, fallback: String): String =
        if (loaded) props.getProperty(key, fallback) else fallback

    /** True if openclaw.conf was found and loaded successfully. */
    val isLoaded: Boolean get() = loaded
}

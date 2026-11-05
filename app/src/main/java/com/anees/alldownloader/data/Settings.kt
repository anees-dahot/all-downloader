package com.anees.alldownloader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
enum class EngineChannel(val label: String) { STABLE("Stable"), NIGHTLY("Nightly") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultQuality: Quality = Quality.BEST,
    val wifiOnly: Boolean = false,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val subtitles: Boolean = false,
    val sponsorBlock: Boolean = false,
    val aria2: Boolean = true,
    /** Parallel connections/fragments per download. 1 = off. */
    val speedBoost: Int = 8,
    val autoRetry: Boolean = true,
    /** How many downloads run at the same time. */
    val parallel: Int = 2,
    val engineChannel: EngineChannel = EngineChannel.STABLE,
    val autoUpdateEngine: Boolean = true,
    val autoCheckApp: Boolean = true,
)

object Settings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current get() = _state.value

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = read()
    }

    private fun read() = AppSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamic", false),
        defaultQuality = Quality.of(prefs.getString("quality", null)),
        wifiOnly = prefs.getBoolean("wifi_only", false),
        embedMetadata = prefs.getBoolean("embed_meta", true),
        embedThumbnail = prefs.getBoolean("embed_thumb", true),
        subtitles = prefs.getBoolean("subs", false),
        sponsorBlock = prefs.getBoolean("sponsorblock", false),
        aria2 = prefs.getBoolean("aria2", true),
        speedBoost = prefs.getInt("boost", 8),
        autoRetry = prefs.getBoolean("auto_retry", true),
        parallel = prefs.getInt("parallel", 2),
        engineChannel = runCatching { EngineChannel.valueOf(prefs.getString("channel", null)!!) }.getOrDefault(EngineChannel.STABLE),
        autoUpdateEngine = prefs.getBoolean("auto_engine", true),
        autoCheckApp = prefs.getBoolean("auto_app", true),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_state.value)
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamic", s.dynamicColor)
            .putString("quality", s.defaultQuality.name)
            .putBoolean("wifi_only", s.wifiOnly)
            .putBoolean("embed_meta", s.embedMetadata)
            .putBoolean("embed_thumb", s.embedThumbnail)
            .putBoolean("subs", s.subtitles)
            .putBoolean("sponsorblock", s.sponsorBlock)
            .putBoolean("aria2", s.aria2)
            .putInt("boost", s.speedBoost)
            .putBoolean("auto_retry", s.autoRetry)
            .putInt("parallel", s.parallel)
            .putString("channel", s.engineChannel.name)
            .putBoolean("auto_engine", s.autoUpdateEngine)
            .putBoolean("auto_app", s.autoCheckApp)
            .apply()
        _state.value = s
    }

    // Small bits of non-UI state.
    var lastAppCheck: Long
        get() = prefs.getLong("last_app_check", 0)
        set(v) = prefs.edit().putLong("last_app_check", v).apply()
    var skippedVersion: String?
        get() = prefs.getString("skipped_version", null)
        set(v) = prefs.edit().putString("skipped_version", v).apply()
}

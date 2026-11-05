package com.anees.alldownloader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
enum class Accent(val label: String, val light: Long, val dark: Long) {
    BLUE("Blue", 0xFF007AFF, 0xFF0A84FF),
    INDIGO("Indigo", 0xFF5856D6, 0xFF5E5CE6),
    PURPLE("Purple", 0xFFAF52DE, 0xFFBF5AF2),
    PINK("Pink", 0xFFFF2D55, 0xFFFF375F),
    ORANGE("Orange", 0xFFFF9500, 0xFFFF9F0A),
    GREEN("Green", 0xFF34C759, 0xFF30D158),
    TEAL("Teal", 0xFF30B0C7, 0xFF40C8E0),
}
enum class ShareAction(val label: String) { ASK("Show quick sheet"), INSTANT("Download right away") }
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
    val accent: Accent = Accent.BLUE,
    val clipboardDetect: Boolean = true,
    val shareAction: ShareAction = ShareAction.ASK,
    val haptics: Boolean = true,
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
        accent = runCatching { Accent.valueOf(prefs.getString("accent", null)!!) }.getOrDefault(Accent.BLUE),
        clipboardDetect = prefs.getBoolean("clip_detect", true),
        shareAction = runCatching { ShareAction.valueOf(prefs.getString("share_action", null)!!) }.getOrDefault(ShareAction.ASK),
        haptics = prefs.getBoolean("haptics", true),
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
            .putString("accent", s.accent.name)
            .putBoolean("clip_detect", s.clipboardDetect)
            .putString("share_action", s.shareAction.name)
            .putBoolean("haptics", s.haptics)
            .apply()
        _state.value = s
    }

    // Small bits of non-UI state.
    var lastAppCheck: Long
        get() = prefs.getLong("last_app_check", 0)
        set(v) = prefs.edit().putLong("last_app_check", v).apply()
    /** Last clipboard link we offered, so the banner doesn't nag about the same one. */
    var lastClipOffered: String?
        get() = prefs.getString("last_clip", null)
        set(v) = prefs.edit().putString("last_clip", v).apply()
    var skippedVersion: String?
        get() = prefs.getString("skipped_version", null)
        set(v) = prefs.edit().putString("skipped_version", v).apply()
}

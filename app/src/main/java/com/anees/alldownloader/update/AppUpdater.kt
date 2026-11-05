package com.anees.alldownloader.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import com.anees.alldownloader.BuildConfig
import com.anees.alldownloader.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Self-update from GitHub Releases. Picks the APK matching this device's CPU. */
object AppUpdater {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    var downloadJob: Job? = null

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** [manual]=false respects the 12h throttle and the user's "skip this version". */
    suspend fun check(manual: Boolean) = withContext(Dispatchers.IO) {
        if (!manual && System.currentTimeMillis() - Settings.lastAppCheck < 12 * 3600_000L) return@withContext
        if (_state.value is UpdateState.Downloading) return@withContext
        _state.value = UpdateState.Checking
        _state.value = try {
            val rel = fetchLatest()
            Settings.lastAppCheck = System.currentTimeMillis()
            when {
                rel == null || compare(rel.version, currentVersion) <= 0 -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                !manual && Settings.skippedVersion == rel.version -> UpdateState.Idle
                else -> UpdateState.Available(rel)
            }
        } catch (t: Throwable) {
            if (manual) UpdateState.Failed("Couldn't reach GitHub: ${t.message ?: "network error"}") else UpdateState.Idle
        }
    }

    private fun fetchLatest(): Release? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AllDownloader/${currentVersion}")
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code == 404) return null
            if (!res.isSuccessful) error("HTTP ${res.code}")
            val o = JSONObject(res.body!!.string())
            val version = o.getString("tag_name").removePrefix("v")
            val assets = o.getJSONArray("assets")
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.getString("name").endsWith(".apk") }
            val abis = Build.SUPPORTED_ABIS.toList()
            val pick = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.getString("name").contains("-$abi.apk") } }
                ?: apks.firstOrNull { it.getString("name").contains("universal") }
                ?: return null
            return Release(
                version = version,
                notes = o.optString("body").trim(),
                apkUrl = pick.getString("browser_download_url"),
                apkName = pick.getString("name"),
                sizeBytes = pick.optLong("size"),
                pageUrl = o.optString("html_url"),
            )
        }
    }

    suspend fun download(ctx: Context, rel: Release) = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, rel.apkName)
        _state.value = UpdateState.Downloading(rel, 0f)
        try {
            http.newCall(Request.Builder().url(rel.apkUrl).build()).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
                val body = res.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: rel.sizeBytes
                var read = 0L; var lastEmit = 0L
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            if (!coroutineContext.isActive) error("Cancelled")
                            val n = input.read(buf); if (n < 0) break
                            output.write(buf, 0, n); read += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 150 && total > 0) {
                                lastEmit = now
                                _state.value = UpdateState.Downloading(rel, read.toFloat() / total)
                            }
                        }
                    }
                }
            }
            _state.value = UpdateState.Ready(rel, out)
        } catch (t: Throwable) {
            out.delete()
            _state.value = if (t.message == "Cancelled") UpdateState.Available(rel)
            else UpdateState.Failed("Download failed: ${t.message ?: "network error"}")
        }
    }

    /** Returns false when the user first has to allow installs from this app. */
    fun install(ctx: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    fun skip(rel: Release) { Settings.skippedVersion = rel.version; dismiss() }
    fun dismiss() { if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle }

    /** Semver-ish compare: 1.2.10 > 1.2.9; pre-release suffixes sort lower. */
    fun compare(a: String, b: String): Int {
        fun parts(v: String) = v.split('-', limit = 2).let { it[0].split('.').map { p -> p.toIntOrNull() ?: 0 } to it.getOrNull(1) }
        val (pa, sa) = parts(a); val (pb, sb) = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return when { sa == sb -> 0; sa == null -> 1; sb == null -> -1; else -> sa.compareTo(sb) }
    }
}

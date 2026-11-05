package com.anees.alldownloader.data

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EngineState {
    data object Starting : EngineState
    data class Ready(val version: String?) : EngineState
    data class Updating(val version: String?) : EngineState
    data class Failed(val message: String) : EngineState
}

/** Owns one-time init of the bundled yt-dlp / python / ffmpeg / aria2c runtime. */
object Engine {
    private const val TAG = "Engine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()
    private lateinit var app: Context
    private val _state = MutableStateFlow<EngineState>(EngineState.Starting)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    fun start(context: Context) {
        app = context.applicationContext
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                Aria2c.getInstance().init(app)
                ready.complete(Unit)
                _state.value = EngineState.Ready(version())
            } catch (t: Throwable) {
                Log.e(TAG, "init failed", t)
                ready.completeExceptionally(t)
                _state.value = EngineState.Failed(t.message ?: "Engine failed to start")
                return@launch
            }
            if (Settings.current.autoUpdateEngine) runCatching { update() }
        }
    }

    suspend fun await() = ready.await()

    private fun version(): String? = runCatching { YoutubeDL.getInstance().versionName(app) }.getOrNull()

    /** Returns a human message. Sites change often, so extractors need refreshing. */
    suspend fun update(): String = withContext(Dispatchers.IO) {
        await()
        _state.value = EngineState.Updating(version())
        try {
            val ch = when (Settings.current.engineChannel) {
                EngineChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                EngineChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
            }
            val status = YoutubeDL.getInstance().updateYoutubeDL(app, ch)
            Log.i(TAG, "yt-dlp update: $status")
            if (status == YoutubeDL.UpdateStatus.DONE) "Updated to ${version()}" else "Already up to date"
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp update failed", t)
            "Update failed: ${t.message?.lineSequence()?.firstOrNull() ?: "network error"}"
        } finally {
            _state.value = EngineState.Ready(version())
        }
    }
}

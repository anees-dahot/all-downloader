package com.anees.alldownloader.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.anees.alldownloader.data.DownloadRecord
import com.anees.alldownloader.data.Engine
import com.anees.alldownloader.data.HistoryStore
import com.anees.alldownloader.data.Kind
import com.anees.alldownloader.data.MediaInfo
import com.anees.alldownloader.data.Quality
import com.anees.alldownloader.data.Settings
import com.anees.alldownloader.data.Status
import com.anees.alldownloader.data.Storage
import com.anees.alldownloader.data.YtDlpRepository
import com.anees.alldownloader.update.AppUpdater
import com.anees.alldownloader.update.Release
import com.anees.alldownloader.work.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

data class HomeState(
    val url: String = "",
    val loading: Boolean = false,
    val info: MediaInfo? = null,
    val error: String? = null,
    val quality: Quality = Settings.current.defaultQuality,
    /** Selected playlist entry ids. */
    val selected: Set<String> = emptySet(),
)

data class ActiveItem(
    val rec: DownloadRecord,
    val progress: Float,     // -1 = indeterminate
    val status: String,
    val speed: String?,
    val total: String?,
    val waitingForNetwork: Boolean,
)

data class LibraryFilter(val query: String = "", val kind: Kind? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val wm = WorkManager.getInstance(app)
    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()
    private var fetchJob: Job? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val settings = Settings.state
    val engine = Engine.state
    val update = AppUpdater.state

    private val works = wm.getWorkInfosByTagFlow(DownloadWorker.TAG)

    /** Queued, running and failed jobs (failed stay until retried or dismissed). */
    val active: StateFlow<List<ActiveItem>> = combine(HistoryStore.items, works) { recs, infos ->
        val byRec = infos.associateBy { w -> w.tags.firstOrNull { it.startsWith(TAG_REC) }?.removePrefix(TAG_REC) }
        recs.filter { it.status != Status.DONE && it.status != Status.CANCELLED }.map { r ->
            val w = byRec[r.id]
            // Process may have died mid-download; trust WorkManager over our record.
            val st = when {
                r.status.isActive && w == null -> Status.FAILED
                r.status.isActive && w!!.state == WorkInfo.State.FAILED -> Status.FAILED
                r.status.isActive && w!!.state == WorkInfo.State.CANCELLED -> Status.CANCELLED
                else -> r.status
            }
            val rec = if (st != r.status) r.copy(status = st, error = r.error ?: "Interrupted") else r
            val running = w?.state == WorkInfo.State.RUNNING
            ActiveItem(
                rec = rec,
                progress = if (running) w!!.progress.getFloat(DownloadWorker.KEY_PROGRESS, -1f) else -1f,
                status = when {
                    rec.status == Status.FAILED -> rec.error ?: "Failed"
                    running -> w!!.progress.getString(DownloadWorker.KEY_STATUS) ?: "Starting…"
                    (w?.runAttemptCount ?: 0) > 0 -> rec.error?.substringBefore(" · ")?.let { "$it · retrying shortly" } ?: "Retrying shortly"
                    else -> "Waiting in queue"
                },
                speed = if (running) w!!.progress.getString(DownloadWorker.KEY_SPEED) else null,
                total = if (running) w!!.progress.getString(DownloadWorker.KEY_TOTAL) else null,
                waitingForNetwork = w?.state == WorkInfo.State.ENQUEUED && w.runAttemptCount == 0 && !running,
            )
        }.filter { it.rec.status != Status.CANCELLED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filter = MutableStateFlow(LibraryFilter())
    val filter = _filter.asStateFlow()
    private val _missing = MutableStateFlow<Set<String>>(emptySet())
    val missing = _missing.asStateFlow()

    val library: StateFlow<List<DownloadRecord>> = combine(HistoryStore.items, _filter) { recs, f ->
        recs.asSequence().filter { it.status == Status.DONE }
            .filter { f.kind == null || it.kind == f.kind }
            .filter { f.query.isBlank() || it.title.contains(f.query, true) || it.site.contains(f.query, true) || (it.uploader?.contains(f.query, true) == true) }
            .sortedByDescending { it.finishedAt }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { AppUpdater.takeIf { Settings.current.autoCheckApp }?.check(manual = false) }
    }

    // ---------- Home ----------
    fun setUrl(url: String) = _home.update { it.copy(url = url, error = null) }
    fun setQuality(q: Quality) = _home.update { it.copy(quality = q) }
    fun clearHome() { fetchJob?.cancel(); _home.value = HomeState() }

    /** Pull first URL out of shared or pasted text. */
    fun onShared(text: String, autoFetch: Boolean = true) {
        val url = URL_RE.find(text)?.value?.trimEnd('.', ',', ')', ']') ?: run {
            _home.update { it.copy(url = text.trim(), error = "No link found in that text") }; return
        }
        setUrl(url)
        if (autoFetch) fetch()
    }

    fun toggleEntry(id: String) = _home.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
    fun selectAll(all: Boolean) = _home.update { s -> s.copy(selected = if (all) s.info?.entries?.map { it.id }?.toSet().orEmpty() else emptySet()) }

    /** From a playlist link, grab only the video it points at (watch?v=…&list=…). */
    fun fetchSingle() {
        val u = _home.value.url
        if (!u.contains("v=")) return
        setUrl(u.replace(Regex("[?&](list|index|start_radio)=[^&]*"), "").let { if (!it.contains('?')) it.replaceFirst('&', '?') else it })
        fetch(allowPlaylist = false)
    }

    fun fetch(allowPlaylist: Boolean = true) {
        val url = _home.value.url.trim()
        if (!URL_RE.matches(url)) {
            _home.update { it.copy(error = "That doesn't look like a link. It should start with http:// or https://") }
            return
        }
        fetchJob?.cancel()
        _home.update { it.copy(loading = true, error = null, info = null) }
        fetchJob = viewModelScope.launch {
            runCatching { YtDlpRepository.fetchInfo(url, allowPlaylist) }
                .onSuccess { info ->
                    _home.update { s ->
                        val q = when {
                            s.quality.isAudio -> s.quality
                            s.quality.height == null -> Quality.BEST
                            info.heights.any { it == s.quality.height } -> s.quality
                            else -> Quality.BEST
                        }
                        s.copy(loading = false, info = info, quality = q, selected = info.entries.map { it.id }.toSet())
                    }
                }
                .onFailure { t ->
                    if (t is kotlinx.coroutines.CancellationException) return@onFailure
                    _home.update { it.copy(loading = false, error = YtDlpRepository.errorLine(t)) }
                }
        }
    }

    fun enqueue() {
        val s = _home.value
        val info = s.info ?: return
        val quality = if (info.isDirect) Quality.BEST else s.quality
        if (info.isPlaylist) {
            val picks = info.entries.filter { it.id in s.selected }
            if (picks.isEmpty()) { _messages.tryEmit("Pick at least one item"); return }
            val base = System.currentTimeMillis()
            picks.forEachIndexed { i, e ->
                start(DownloadRecord(
                    id = UUID.randomUUID().toString(), url = e.url, title = e.title, thumb = e.thumb,
                    site = info.site.removeSuffix("Tab").removeSuffix("Playlist").ifEmpty { "Playlist" }, uploader = e.uploader ?: info.uploader,
                    durationSec = e.durationSec, quality = quality, status = Status.QUEUED, createdAt = base + i,
                ), false)
            }
            _home.value = HomeState()
            _messages.tryEmit("${picks.size} items added · ${Settings.current.parallel} at a time")
            return
        }
        val rec = DownloadRecord(
            id = UUID.randomUUID().toString(), url = info.url, title = info.title, thumb = info.thumbnail,
            site = info.site, uploader = info.uploader, durationSec = info.durationSec, quality = quality,
            status = Status.QUEUED, createdAt = System.currentTimeMillis(),
        )
        start(rec, info.isDirect)
        _home.value = HomeState()
        _messages.tryEmit("Added to downloads")
    }

    private fun start(rec: DownloadRecord, isDirect: Boolean) {
        HistoryStore.upsert(rec)
        val net = if (Settings.current.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_REC_ID to rec.id,
                    DownloadWorker.KEY_URL to rec.url,
                    DownloadWorker.KEY_TITLE to rec.title,
                    DownloadWorker.KEY_QUALITY to rec.quality.name,
                    DownloadWorker.KEY_DIRECT to isDirect,
                )
            )
            .setConstraints(Constraints(requiredNetworkType = net))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(DownloadWorker.TAG)
            .addTag(TAG_REC + rec.id)
            .build()
        wm.enqueue(req)
    }

    // ---------- Downloads ----------
    fun cancel(rec: DownloadRecord) {
        wm.cancelAllWorkByTag(TAG_REC + rec.id)
        HistoryStore.modify(rec.id) { it.copy(status = Status.CANCELLED) }
    }

    fun retry(rec: DownloadRecord) {
        wm.cancelAllWorkByTag(TAG_REC + rec.id)
        start(rec.copy(status = Status.QUEUED, error = null, createdAt = System.currentTimeMillis()), rec.site.equals("Generic", true))
    }

    fun dismiss(rec: DownloadRecord) = HistoryStore.remove(listOf(rec.id))
    fun clearFailed() = HistoryStore.remove(active.value.filter { it.rec.status == Status.FAILED }.map { it.rec.id })

    fun redownload(rec: DownloadRecord) {
        start(rec.copy(id = UUID.randomUUID().toString(), status = Status.QUEUED, createdAt = System.currentTimeMillis(),
            outUri = null, outName = null, mime = null, sizeBytes = 0, finishedAt = 0, error = null), rec.site.equals("Generic", true))
        _messages.tryEmit("Downloading again")
    }

    // ---------- Library ----------
    fun setQuery(q: String) = _filter.update { it.copy(query = q) }
    fun setKind(k: Kind?) = _filter.update { it.copy(kind = k) }

    fun refreshMissing() = viewModelScope.launch(Dispatchers.IO) {
        _missing.value = HistoryStore.items.value.filter { it.status == Status.DONE && it.outUri != null }
            .filterNot { Storage.exists(ctx, it.outUri!!) }.map { it.id }.toSet()
    }

    fun open(rec: DownloadRecord) {
        val ref = rec.outUri ?: return
        if (rec.id in _missing.value) { _messages.tryEmit("File was moved or deleted"); return }
        try { ctx.startActivity(Storage.openIntent(ctx, ref, rec.mime)) }
        catch (_: ActivityNotFoundException) { _messages.tryEmit("No app installed that can open this file") }
        catch (_: Exception) { _messages.tryEmit("Couldn't open file") }
    }

    fun share(rec: DownloadRecord) {
        val ref = rec.outUri ?: return
        runCatching { ctx.startActivity(Storage.shareIntent(ctx, ref, rec.mime)) }
            .onFailure { _messages.tryEmit("Couldn't share file") }
    }

    fun removeFromLibrary(rec: DownloadRecord, deleteFile: Boolean) = viewModelScope.launch {
        if (deleteFile && rec.outUri != null) withContext(Dispatchers.IO) { Storage.delete(ctx, rec.outUri) }
        HistoryStore.remove(listOf(rec.id))
        _messages.tryEmit(if (deleteFile) "File deleted" else "Removed from library")
    }

    // ---------- Settings / engine / updates ----------
    fun updateEngine() = viewModelScope.launch { _messages.emit(Engine.update()) }
    fun checkAppUpdate() = viewModelScope.launch { AppUpdater.check(manual = true) }
    fun downloadUpdate(rel: Release) {
        AppUpdater.downloadJob?.cancel()
        AppUpdater.downloadJob = viewModelScope.launch { AppUpdater.download(ctx, rel) }
    }
    fun cancelUpdateDownload() { AppUpdater.downloadJob?.cancel() }
    fun install(file: java.io.File) {
        if (!AppUpdater.install(ctx, file)) _messages.tryEmit("Allow installs from All Downloader, then tap Install again")
    }
    fun skipUpdate(rel: Release) = AppUpdater.skip(rel)
    fun dismissUpdate() = AppUpdater.dismiss()

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG_REC = "rec:"
        val URL_RE = Regex("""https?://[^\s]+""")
    }
}

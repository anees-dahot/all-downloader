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
    /** More than one link pasted: offer to queue them all. */
    val batch: List<String> = emptyList(),
    /** Clip: download only [clipStart, clipEnd] seconds. */
    val clipOn: Boolean = false,
    val clipStart: Int = 0,
    val clipEnd: Int = 0,
)

data class ActiveItem(
    val rec: DownloadRecord,
    val progress: Float,     // -1 = indeterminate
    val status: String,
    val speed: String?,
    val total: String?,
    val waitingForNetwork: Boolean,
)

enum class LibrarySort(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), LARGEST("Largest"), NAME("Name") }
data class LibraryFilter(val query: String = "", val kind: Kind? = null, val sort: LibrarySort = LibrarySort.NEWEST)

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
                    rec.status == Status.PAUSED -> "Paused"
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
            .let { seq ->
                when (f.sort) {
                    LibrarySort.NEWEST -> seq.sortedByDescending { it.finishedAt }
                    LibrarySort.OLDEST -> seq.sortedBy { it.finishedAt }
                    LibrarySort.LARGEST -> seq.sortedByDescending { it.sizeBytes }
                    LibrarySort.NAME -> seq.sortedBy { it.title.lowercase() }
                }
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { AppUpdater.takeIf { Settings.current.autoCheckApp }?.check(manual = false) }
    }

    // ---------- Home ----------
    fun setUrl(url: String) = _home.update {
        val links = URL_RE.findAll(url).map { m -> m.value.trimEnd('.', ',', ')', ']') }.distinct().toList()
        it.copy(url = url, error = null, batch = if (links.size > 1) links else emptyList())
    }
    fun setClip(on: Boolean, start: Int, end: Int) = _home.update { it.copy(clipOn = on, clipStart = start, clipEnd = end) }
    fun setQuality(q: Quality) = _home.update { it.copy(quality = q) }
    fun clearHome() { fetchJob?.cancel(); _home.value = HomeState() }

    /** Pull first URL out of shared or pasted text. */
    fun onShared(text: String, autoFetch: Boolean = true) {
        val all = URL_RE.findAll(text).map { it.value.trimEnd('.', ',', ')', ']') }.distinct().toList()
        if (all.size > 1) { setUrl(text.trim()); return }
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
                        s.copy(loading = false, info = info, quality = q, selected = info.entries.map { it.id }.toSet(),
                            clipOn = false, clipStart = 0, clipEnd = info.durationSec)
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
        val section = if (s.clipOn && !info.isDirect && s.clipEnd > s.clipStart &&
            (s.clipStart > 0 || s.clipEnd < info.durationSec)) "${s.clipStart}-${s.clipEnd}" else null
        val rec = DownloadRecord(
            id = UUID.randomUUID().toString(), url = info.url, title = info.title, thumb = info.thumbnail,
            site = info.site, uploader = info.uploader,
            durationSec = if (section != null) s.clipEnd - s.clipStart else info.durationSec, quality = quality,
            status = Status.QUEUED, createdAt = System.currentTimeMillis(), section = section,
        )
        start(rec, info.isDirect)
        _home.value = HomeState()
        _messages.tryEmit("Added to downloads")
    }

    /** Queue every pasted link with the default quality; titles fill in when each finishes. */
    fun enqueueBatch() {
        val links = _home.value.batch.ifEmpty { return }
        val base = System.currentTimeMillis()
        links.forEachIndexed { i, u -> quickStart(u, Settings.current.defaultQuality, base + i) }
        _home.value = HomeState()
        _messages.tryEmit("${links.size} links added")
    }

    /** Start without reading info first (share → instant, batch). */
    fun quickStart(url: String, quality: Quality, at: Long = System.currentTimeMillis()) {
        val host = runCatching { java.net.URI(url).host.removePrefix("www.").removePrefix("m.") }.getOrNull().orEmpty()
        start(DownloadRecord(
            id = UUID.randomUUID().toString(), url = url, title = url, thumb = null,
            site = host.substringBeforeLast('.').replaceFirstChar { it.uppercase() }, uploader = null, durationSec = 0,
            quality = quality, status = Status.QUEUED, createdAt = at,
        ), FILE_RE.containsMatchIn(url))
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
                    DownloadWorker.KEY_SECTION to rec.section,
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

    fun pause(rec: DownloadRecord) {
        HistoryStore.modify(rec.id) { it.copy(status = Status.PAUSED) }
        wm.cancelAllWorkByTag(TAG_REC + rec.id)
    }

    /** Same record id → same temp dir → yt-dlp --continue picks up the partial file. */
    fun resume(rec: DownloadRecord) {
        wm.cancelAllWorkByTag(TAG_REC + rec.id)
        start(rec.copy(status = Status.QUEUED, error = null), isDirectRec(rec))
    }

    fun pauseAll() = active.value.filter { it.rec.status.isActive }.forEach { pause(it.rec) }
    fun resumeAll() = active.value.filter { it.rec.status == Status.PAUSED }.forEach { resume(it.rec) }

    private fun isDirectRec(rec: DownloadRecord) = rec.site.equals("Generic", true) || FILE_RE.containsMatchIn(rec.url)

    fun retry(rec: DownloadRecord) {
        wm.cancelAllWorkByTag(TAG_REC + rec.id)
        start(rec.copy(status = Status.QUEUED, error = null, createdAt = System.currentTimeMillis()), isDirectRec(rec))
    }

    fun dismiss(rec: DownloadRecord) = HistoryStore.remove(listOf(rec.id))
    fun clearFailed() = HistoryStore.remove(active.value.filter { it.rec.status == Status.FAILED }.map { it.rec.id })

    fun redownload(rec: DownloadRecord) {
        start(rec.copy(id = UUID.randomUUID().toString(), status = Status.QUEUED, createdAt = System.currentTimeMillis(),
            outUri = null, outName = null, mime = null, sizeBytes = 0, finishedAt = 0, error = null), isDirectRec(rec))
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

    fun shareMany(recs: List<DownloadRecord>) {
        if (recs.size == 1) return share(recs[0])
        val uris = recs.mapNotNull { r -> r.outUri?.let { runCatching { Storage.shareable(ctx, it) }.getOrNull() } }
        if (uris.isEmpty()) return
        val i = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { _messages.tryEmit("Couldn't share files") }
    }

    fun share(rec: DownloadRecord) {
        val ref = rec.outUri ?: return
        runCatching { ctx.startActivity(Storage.shareIntent(ctx, ref, rec.mime)) }
            .onFailure { _messages.tryEmit("Couldn't share file") }
    }

    fun removeFromLibrary(rec: DownloadRecord, deleteFile: Boolean) = removeMany(listOf(rec), deleteFile)

    fun removeMany(recs: List<DownloadRecord>, deleteFile: Boolean) = viewModelScope.launch {
        if (deleteFile) withContext(Dispatchers.IO) { recs.forEach { r -> r.outUri?.let { Storage.delete(ctx, it) } } }
        HistoryStore.remove(recs.map { it.id })
        val n = if (recs.size == 1) "" else "${recs.size} "
        _messages.tryEmit(if (deleteFile) "${n}File${if (recs.size == 1) "" else "s"} deleted" else "Removed from library")
    }

    fun setSort(s: LibrarySort) = _filter.update { it.copy(sort = s) }

    // ---------- Clipboard ----------
    private val _clipOffer = MutableStateFlow<String?>(null)
    val clipOffer = _clipOffer.asStateFlow()

    fun onClipboard(text: String?) {
        if (!Settings.current.clipboardDetect || text == null) return
        val url = URL_RE.find(text)?.value?.trimEnd('.', ',', ')', ']') ?: return
        if (url == Settings.lastClipOffered || url == _home.value.url.trim()) return
        _clipOffer.value = url
    }
    fun dismissClip() { Settings.lastClipOffered = _clipOffer.value; _clipOffer.value = null }
    fun useClip() { val u = _clipOffer.value ?: return; dismissClip(); onShared(u) }

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
        private val FILE_RE = Regex("""\.(zip|apk|pdf|rar|7z|exe|dmg|iso|tar|gz|mp4|mkv|mp3|m4a|flac|jpg|png)(\?|$)""", RegexOption.IGNORE_CASE)
    }
}

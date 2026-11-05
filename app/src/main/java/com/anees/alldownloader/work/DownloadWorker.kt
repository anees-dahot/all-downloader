package com.anees.alldownloader.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anees.alldownloader.App
import com.anees.alldownloader.MainActivity
import com.anees.alldownloader.R
import com.anees.alldownloader.data.Engine
import com.anees.alldownloader.data.HistoryStore
import com.anees.alldownloader.data.Quality
import com.anees.alldownloader.data.Settings
import com.anees.alldownloader.data.Status
import com.anees.alldownloader.data.Storage
import com.anees.alldownloader.data.YtDlpRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val recId = inputData.getString(KEY_REC_ID) ?: id.toString()
    private val notifId = recId.hashCode()
    private val title = inputData.getString(KEY_TITLE) ?: "Download"
    private var lastPushMs = 0L

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val quality = Quality.of(inputData.getString(KEY_QUALITY))
        val isDirect = inputData.getBoolean(KEY_DIRECT, false)
        val processId = id.toString()
        // Keyed by record, not by work id, so a retry resumes the .part files.
        val tmp = File(applicationContext.cacheDir, "dl/$recId").apply { mkdirs() }
        val order = HistoryStore.get(recId)?.createdAt ?: 0L
        if (HistoryStore.get(recId)?.status == Status.CANCELLED) return Result.success()

        setProgress(progressData(-1f, if (runAttemptCount > 0) "Retrying…" else "Waiting in queue", null, null))
        var acquired = false
        var keepTmp = false
        return try {
            DownloadGate.acquire(order, recId); acquired = true
            HistoryStore.modify(recId) { it.copy(status = Status.RUNNING, error = null) }
            runCatching { setForeground(foregroundInfo(-1f, "Starting…")) }
            setProgress(progressData(-1f, "Starting…", null, null))
            Engine.await()
            coroutineScope {
                // If WorkManager cancels us, kill the yt-dlp process so the blocking call returns.
                val killer = launch { try { awaitCancellation() } finally { YtDlpRepository.cancel(processId) } }
                withContext(Dispatchers.IO) {
                    YtDlpRepository.download(url, quality, isDirect, tmp, processId, ::onProgress)
                }
                killer.cancel()
            }
            setProgress(progressData(100f, "Saving…", null, null))
            val (name, saved) = withContext(Dispatchers.IO) {
                val main = tmp.listFiles().orEmpty().filter {
                    it.isFile && it.extension.lowercase() !in setOf("part", "ytdl", "vtt", "srt", "webp", "jpg", "png", "json")
                        && !it.name.contains(".part-Frag")
                }.maxByOrNull { it.length() } ?: error("No output file produced")
                main.name to Storage.publish(applicationContext, main)
            }
            HistoryStore.modify(recId) {
                it.copy(
                    status = Status.DONE, finishedAt = System.currentTimeMillis(), error = null,
                    outUri = saved.first, outName = name, mime = Storage.mimeOf(name), sizeBytes = saved.second,
                )
            }
            notifyDone(true, name)
            Result.success(workDataOf(KEY_REC_ID to recId))
        } catch (t: Throwable) {
            if (isStopped) {
                // User cancel -> VM already marked CANCELLED. System stop -> WorkManager reschedules; keep partials.
                keepTmp = HistoryStore.get(recId)?.status != Status.CANCELLED
                if (keepTmp) HistoryStore.modify(recId) { it.copy(status = Status.QUEUED) }
                return Result.failure()
            }
            android.util.Log.w("DownloadWorker", "download failed: ${t.message}")
            val msg = YtDlpRepository.errorLine(t).take(300)
            if (Settings.current.autoRetry && runAttemptCount < MAX_RETRIES && !YtDlpRepository.isPermanent(msg)) {
                keepTmp = true
                HistoryStore.modify(recId) { it.copy(status = Status.QUEUED, error = "Retry ${runAttemptCount + 1}/$MAX_RETRIES · $msg") }
                return Result.retry()
            }
            HistoryStore.modify(recId) { it.copy(status = Status.FAILED, error = msg, finishedAt = System.currentTimeMillis()) }
            notifyDone(false, msg)
            // Success so the record (not WorkManager) carries the failure; keeps UI logic in one place.
            Result.success(workDataOf(KEY_REC_ID to recId, KEY_ERROR to msg))
        } finally {
            if (acquired) DownloadGate.release()
            if (!keepTmp) tmp.deleteRecursively()
        }
    }

    private fun onProgress(p: Float, eta: Long, line: String) {
        val now = System.currentTimeMillis()
        if (now - lastPushMs < 400) return
        lastPushMs = now
        val m = PROGRESS_RE.find(line)
        val total = m?.groupValues?.get(1)
        val speed = m?.groupValues?.get(2)
        val status = when {
            line.contains("[Merger]") || line.contains("[ExtractAudio]") || line.contains("[FFmpeg") -> "Processing…"
            line.contains("[SponsorBlock]") || line.contains("[ModifyChapters]") -> "Removing sponsor segments…"
            p >= 0 -> buildString {
                append("${p.toInt()}%")
                if (eta > 0) append(" · ${fmtEta(eta)} left")
            }
            else -> "Preparing…"
        }
        setProgressAsync(progressData(p, status, speed, total))
        setForegroundAsync(foregroundInfo(p, listOfNotNull(status, speed).joinToString(" · ")))
    }

    private fun fmtEta(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0,
        Intent(applicationContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 1)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun foregroundInfo(p: Float, status: String): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, App.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download_glyph)
            .setContentTitle(title)
            .setContentText(status)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, p.coerceAtLeast(0f).toInt(), p < 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notifId, n)
    }

    private fun notifyDone(ok: Boolean, text: String) {
        val n = NotificationCompat.Builder(applicationContext, App.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_download_glyph)
            .setContentTitle(if (ok) "Saved · $title" else "Failed · $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(notifId + 1, n) }
    }

    private fun progressData(p: Float, status: String, speed: String?, total: String?): Data =
        workDataOf(KEY_REC_ID to recId, KEY_PROGRESS to p, KEY_STATUS to status, KEY_SPEED to speed, KEY_TOTAL to total)

    companion object {
        const val TAG = "download"
        const val MAX_RETRIES = 3
        const val KEY_REC_ID = "rec"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_QUALITY = "quality"
        const val KEY_DIRECT = "direct"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_SPEED = "speed"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private val PROGRESS_RE = Regex("""of\s+~?\s*([\d.]+\s*\w+)(?:\s+in\s+\S+)?\s+at\s+([\d.]+\s*\w+/s)""")
    }
}

package com.anees.alldownloader.data

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object YtDlpRepository {

    private val PLAYLIST_HINT = Regex("""[?&]list=|/playlist|/sets/|/album/|/@[^/]+/?(videos|shorts|streams)?/?$|/channel/|/c/""", RegexOption.IGNORE_CASE)

    suspend fun fetchInfo(url: String, allowPlaylist: Boolean = true): MediaInfo = withContext(Dispatchers.IO) {
        Engine.await()
        if (allowPlaylist && PLAYLIST_HINT.containsMatchIn(url)) {
            runCatching { fetchPlaylist(url) }.getOrNull()?.let { return@withContext it }
        }
        val info = withBotFallback { fallback ->
            YoutubeDL.getInstance().getInfo(YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                if (fallback) addOption("--extractor-args", FALLBACK_ARGS)
            })
        }
        val formats = info.formats.orEmpty()
        val heights = formats.mapNotNull { f -> f.height.takeIf { it > 0 } }.distinct().sortedDescending()

        fun size(f: com.yausername.youtubedl_android.mapper.VideoFormat) =
            f.fileSize.takeIf { it > 0 } ?: f.fileSizeApproximate.takeIf { it > 0 }
        val audioOnly = formats.filter { (it.vcodec == "none") && it.acodec != null && it.acodec != "none" }
        val bestAudio = audioOnly.mapNotNull(::size).maxOrNull()
        val sizeByHeight = heights.associateWith { h ->
            formats.filter { it.height == h }.mapNotNull(::size).maxOrNull()?.let { v ->
                val muxed = formats.any { it.height == h && it.acodec != null && it.acodec != "none" }
                if (muxed) v else v + (bestAudio ?: 0)
            }
        }.filterValues { it != null }.mapValues { it.value!! }

        val extractor = info.extractorKey ?: info.extractor ?: "Generic"
        MediaInfo(
            url = url,
            title = info.title ?: info.fulltitle ?: url.substringAfterLast('/'),
            thumbnail = info.thumbnail,
            uploader = info.uploader,
            durationSec = info.duration,
            site = extractor,
            heights = heights,
            sizeByHeight = sizeByHeight,
            audioSize = bestAudio,
            isDirect = extractor.equals("Generic", ignoreCase = true),
        )
    }

    /** Flat listing: fast, one request, no per-video extraction. */
    private fun fetchPlaylist(url: String): MediaInfo? {
        val req = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist"); addOption("--yes-playlist"); addOption("-J")
        }
        val o = org.json.JSONObject(YoutubeDL.getInstance().execute(req).out)
        if (o.optString("_type") != "playlist") return null
        val arr = o.optJSONArray("entries") ?: return null
        val entries = (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("id").ifEmpty { return@mapNotNull null }
            val link = e.optString("url").takeIf { it.startsWith("http") }
                ?: e.optString("webpage_url").takeIf { it.startsWith("http") }
                ?: if (e.optString("ie_key").equals("Youtube", true)) "https://www.youtube.com/watch?v=$id" else return@mapNotNull null
            val thumbs = e.optJSONArray("thumbnails")
            val thumb = thumbs?.let { t -> (t.length() - 1 downTo 0).firstNotNullOfOrNull { t.optJSONObject(it)?.optString("url")?.ifEmpty { null } } }
                ?: e.optString("thumbnail").ifEmpty { null }
            val title = e.optString("title").ifEmpty { id }
            if (title == "[Private video]" || title == "[Deleted video]") return@mapNotNull null
            PlaylistEntry(id, link, title, e.optDouble("duration", 0.0).toInt(), thumb, e.optString("uploader").ifEmpty { e.optString("channel").ifEmpty { null } })
        }
        if (entries.isEmpty()) return null
        return MediaInfo(
            url = url,
            title = o.optString("title").ifEmpty { "Playlist" },
            thumbnail = entries.firstNotNullOfOrNull { it.thumb },
            uploader = o.optString("uploader").ifEmpty { o.optString("channel").ifEmpty { null } },
            durationSec = entries.sumOf { it.durationSec },
            site = o.optString("extractor_key").ifEmpty { "Playlist" },
            heights = emptyList(), sizeByHeight = emptyMap(), audioSize = null, isDirect = false,
            entries = entries,
        )
    }

    /** Blocking. Call from IO thread. */
    fun download(
        url: String,
        quality: Quality,
        isDirect: Boolean,
        outDir: File,
        processId: String,
        onProgress: (percent: Float, etaSec: Long, line: String) -> Unit,
    ) {
        val s = Settings.current
        withBotFallback { fallback -> runDownload(url, quality, isDirect, outDir, processId, s, fallback, onProgress) }
    }

    private fun runDownload(
        url: String, quality: Quality, isDirect: Boolean, outDir: File, processId: String,
        s: AppSettings, fallback: Boolean, onProgress: (Float, Long, String) -> Unit,
    ) {
        val req = YoutubeDLRequest(url).apply {
            if (fallback) addOption("--extractor-args", FALLBACK_ARGS)
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--continue")
            // Network hiccups shouldn't kill a long download.
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--retry-sleep", "fragment:exp=1:20")
            addOption("--socket-timeout", "20")
            if (s.speedBoost > 1) {
                // Speed booster: fetch DASH/HLS fragments in parallel, and chunk plain HTTP
                // streams so sites that throttle long single requests stay fast.
                addOption("-N", s.speedBoost.toString())
                if (!isDirect) addOption("--http-chunk-size", "10M")
            }
            addOption("-o", File(outDir, "%(title).80B [%(id)s].%(ext)s").absolutePath)
            if (s.embedMetadata) addOption("--embed-metadata")
            if (quality.isAudio) {
                addOption("-f", "ba/b")
                addOption("-x")
                addOption("--audio-format", quality.audio!!)
                addOption("--audio-quality", "0")
                if (s.embedThumbnail) addOption("--embed-thumbnail")
            } else {
                addOption("-f", "bv*+ba/b")
                val sort = buildString {
                    quality.height?.let { append("res:$it,") }
                    append("ext:mp4:m4a")
                }
                addOption("-S", sort)
                addOption("--merge-output-format", "mp4")
                if (s.embedThumbnail) addOption("--embed-thumbnail")
                if (s.subtitles) {
                    addOption("--write-subs"); addOption("--embed-subs")
                    addOption("--sub-langs", "en.*,${java.util.Locale.getDefault().language}.*")
                }
            }
            if (s.sponsorBlock && !isDirect) addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
            if (isDirect && s.aria2) {
                addOption("--downloader", "aria2c")
                val n = s.speedBoost.coerceIn(1, 16)
                addOption("--external-downloader-args", "aria2c:-x $n -s $n -k 1M --file-allocation=none")
            }
        }
        YoutubeDL.getInstance().execute(req, processId) { progress, eta, line ->
            onProgress(progress, eta, line)
        }
    }

    /** YouTube sometimes bot-checks the default web client; the VR client usually still works. */
    private const val FALLBACK_ARGS = "youtube:player_client=android_vr,tv_simply"

    private inline fun <T> withBotFallback(block: (fallback: Boolean) -> T): T = try {
        block(false)
    } catch (t: Throwable) {
        val m = t.message.orEmpty()
        if (t is YoutubeDL.CanceledException || !(m.contains("Sign in to confirm", true) || m.contains("not a bot", true))) throw t
        block(true)
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /** yt-dlp errors are multi-line; keep the most useful line. */
    fun errorLine(t: Throwable): String {
        val lines = t.message.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val raw = lines.lastOrNull { it.startsWith("ERROR") }?.removePrefix("ERROR:")?.trim()
            ?: lines.lastOrNull() ?: t.javaClass.simpleName
        return friendly(raw)
    }

    /** Errors where trying again won't help. */
    fun isPermanent(msg: String) = listOf("isn't supported", "private", "404", "sign-in", "No output file", "copyright", "removed", "unavailable")
        .any { msg.contains(it, true) }

    private fun friendly(msg: String): String = when {
        msg.contains("Unsupported URL", true) -> "This site isn't supported, or the link doesn't point to media."
        msg.contains("Unable to resolve host", true) || msg.contains("Failed to resolve", true) ||
            msg.contains("getaddrinfo", true) -> "No internet connection."
        msg.contains("Private video", true) -> "This video is private."
        msg.contains("Sign in to confirm", true) -> "The site wants a sign-in to show this. Try again later or update the engine in Settings."
        msg.contains("HTTP Error 403", true) -> "The site refused the request (403). Updating the engine in Settings often fixes this."
        msg.contains("429", true) || msg.contains("Too Many Requests", true) ->
            "The site is rate-limiting this network (429). Wait a few minutes and retry."
        msg.contains("HTTP Error 404", true) -> "Nothing found at this link (404)."
        else -> msg.replace(Regex("^\\[[^]]+] [^:]+: "), "")
    }
}

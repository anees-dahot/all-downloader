package com.anees.alldownloader.data

data class MediaInfo(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Int,
    val site: String,
    val heights: List<Int>,
    /** Approx bytes per offered height (video+best audio), when the site reports sizes. */
    val sizeByHeight: Map<Int, Long>,
    val audioSize: Long?,
    val isDirect: Boolean,
    /** Non-empty when the link is a playlist / channel tab. */
    val entries: List<PlaylistEntry> = emptyList(),
) {
    val isPlaylist get() = entries.isNotEmpty()
}

data class PlaylistEntry(
    val id: String,
    val url: String,
    val title: String,
    val durationSec: Int,
    val thumb: String?,
    val uploader: String?,
)

/** What the user wants to save. */
enum class Quality(val label: String, val height: Int?, val audio: String? = null) {
    BEST("Best available", null),
    P2160("2160p · 4K", 2160),
    P1440("1440p", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
    MP3("MP3", null, "mp3"),
    M4A("M4A", null, "m4a"),
    OPUS("Opus", null, "opus"),
    FLAC("FLAC", null, "flac");

    val isAudio get() = audio != null

    companion object {
        val video = entries.filter { !it.isAudio }
        val audioOnly = entries.filter { it.isAudio }
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: BEST
    }
}

enum class Status { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED;
    val isActive get() = this == QUEUED || this == RUNNING
}

data class DownloadRecord(
    val id: String,
    val url: String,
    val title: String,
    val thumb: String?,
    val site: String,
    val uploader: String?,
    val durationSec: Int,
    val quality: Quality,
    val status: Status,
    val createdAt: Long,
    val finishedAt: Long = 0,
    val outUri: String? = null,
    val outName: String? = null,
    val mime: String? = null,
    val sizeBytes: Long = 0,
    val error: String? = null,
    /** Clip range in seconds, "start-end"; null = whole thing. */
    val section: String? = null,
) {
    /** Quick-started jobs are titled with their URL until yt-dlp names them; show "file.bin · host" meanwhile. */
    val displayTitle: String get() = if (title != url) title else run {
        val u = runCatching { java.net.URI(url) }.getOrNull()
        val host = u?.host?.removePrefix("www.").orEmpty()
        val last = u?.path?.trimEnd('/')?.substringAfterLast('/').orEmpty()
        listOf(last, host).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { url }
    }

    val kind: Kind get() = when {
        mime?.startsWith("video") == true -> Kind.VIDEO
        mime?.startsWith("audio") == true -> Kind.AUDIO
        quality.isAudio -> Kind.AUDIO
        else -> if (outName == null) Kind.VIDEO else Kind.OTHER
    }
}

enum class Kind(val label: String) { VIDEO("Video"), AUDIO("Audio"), OTHER("Files") }

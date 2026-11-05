package com.anees.alldownloader.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tiny JSON-file store for download records. Small data, so whole-file rewrite is fine. */
object HistoryStore {
    private lateinit var file: File
    private val lock = Any()
    private val _items = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val items: StateFlow<List<DownloadRecord>> = _items.asStateFlow()

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "history.json")
        _items.value = runCatching { parse(file.readText()) }.getOrElse { emptyList() }
    }

    fun get(id: String) = _items.value.firstOrNull { it.id == id }

    fun upsert(rec: DownloadRecord) = mutate { list ->
        val i = list.indexOfFirst { it.id == rec.id }
        if (i < 0) listOf(rec) + list else list.toMutableList().also { it[i] = rec }
    }

    fun modify(id: String, block: (DownloadRecord) -> DownloadRecord) = mutate { list ->
        list.map { if (it.id == id) block(it) else it }
    }

    fun remove(ids: Collection<String>) = mutate { list -> list.filterNot { it.id in ids } }

    private fun mutate(block: (List<DownloadRecord>) -> List<DownloadRecord>) {
        synchronized(lock) {
            _items.update(block)
            runCatching {
                val tmp = File(file.parentFile, "history.json.tmp")
                tmp.writeText(serialize(_items.value))
                tmp.renameTo(file)
            }.onFailure { Log.w("HistoryStore", "write failed", it) }
        }
    }

    private fun serialize(list: List<DownloadRecord>) = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id); put("url", r.url); put("title", r.title)
                put("thumb", r.thumb); put("site", r.site); put("uploader", r.uploader)
                put("dur", r.durationSec); put("q", r.quality.name); put("st", r.status.name)
                put("at", r.createdAt); put("fin", r.finishedAt); put("uri", r.outUri)
                put("name", r.outName); put("mime", r.mime); put("size", r.sizeBytes); put("err", r.error); put("sec", r.section)
            })
        }
    }.toString()

    private fun parse(text: String): List<DownloadRecord> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DownloadRecord(
                id = o.getString("id"),
                url = o.getString("url"),
                title = o.optString("title", "Download"),
                thumb = o.str("thumb"),
                site = o.optString("site", ""),
                uploader = o.str("uploader"),
                durationSec = o.optInt("dur"),
                quality = Quality.of(o.str("q")),
                status = runCatching { Status.valueOf(o.getString("st")) }.getOrDefault(Status.FAILED),
                createdAt = o.optLong("at"),
                finishedAt = o.optLong("fin"),
                outUri = o.str("uri"),
                outName = o.str("name"),
                mime = o.str("mime"),
                sizeBytes = o.optLong("size"),
                error = o.str("err"),
                section = o.str("sec"),
            )
        }
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }
}

package com.anees.alldownloader.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Sign-in cookies captured from the in-app browser, written as a Netscape cookies.txt that
 * yt-dlp reads with --cookies. Lets age-restricted / members-only / "sign in to confirm" media work.
 */
object Cookies {
    private const val HEADER = "# Netscape HTTP Cookie File"
    private lateinit var file: File
    private val lock = Any()
    private val _sites = MutableStateFlow<List<String>>(emptyList())
    /** Domains we hold cookies for, e.g. "youtube.com". */
    val sites: StateFlow<List<String>> = _sites.asStateFlow()

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "cookies.txt")
        _sites.value = readSites()
    }

    /** Path for yt-dlp, or null when there's nothing to send. */
    fun fileForYtDlp(): File? = file.takeIf { _sites.value.isNotEmpty() && it.exists() }

    /** "m.youtube.com" -> "youtube.com". Naive, but right for the big media sites. */
    fun baseDomain(host: String) = host.removePrefix("www.").split('.').takeLast(2).joinToString(".")

    /** Copy the WebView's cookies for [url]'s site into cookies.txt. Returns how many were saved. */
    fun capture(url: String): Int = synchronized(lock) {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return 0
        val base = baseDomain(host)
        val cm = CookieManager.getInstance().apply { flush() }
        // Cookies may sit on the bare, www. or m. host; merge them by name.
        val pairs = linkedMapOf<String, String>()
        listOf(base, "www.$base", "m.$base", host).distinct().forEach { h ->
            cm.getCookie("https://$h")?.split(';')?.forEach { kv ->
                val i = kv.indexOf('=')
                if (i > 0) pairs[kv.substring(0, i).trim()] = kv.substring(i + 1).trim()
            }
        }
        if (pairs.isEmpty()) return 0
        // WebView doesn't expose expiry; a year out is fine, yt-dlp refreshes as it goes.
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val keep = readLines().filterNot { it.split('\t').firstOrNull()?.trimStart('.') == base }
        val fresh = pairs.map { (k, v) -> listOf(".$base", "TRUE", "/", "TRUE", expiry.toString(), k, v).joinToString("\t") }
        file.writeText((listOf(HEADER, "") + keep + fresh).joinToString("\n") + "\n")
        _sites.value = readSites()
        pairs.size
    }

    fun forget(domain: String) = synchronized(lock) {
        file.writeText((listOf(HEADER, "") + readLines().filterNot { it.split('\t').firstOrNull()?.trimStart('.') == domain }).joinToString("\n") + "\n")
        _sites.value = readSites()
    }

    fun clearAll() = synchronized(lock) {
        file.delete()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        _sites.value = emptyList()
    }

    private fun readLines(): List<String> =
        runCatching { file.readLines() }.getOrDefault(emptyList()).filter { it.isNotBlank() && !it.startsWith("#") && it.count { c -> c == '\t' } >= 6 }

    private fun readSites() = readLines().map { it.substringBefore('\t').trimStart('.') }.distinct().sorted()
}

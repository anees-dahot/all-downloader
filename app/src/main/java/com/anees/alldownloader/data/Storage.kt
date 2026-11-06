package com.anees.alldownloader.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Saves into public Download/AllDownloader.
 * Android 10+: MediaStore (no permission). Android 7–9: plain file API + media scan.
 * Stored refs are either content:// (MediaStore) or file:// (legacy).
 */
object Storage {
    const val FOLDER = "AllDownloader"

    fun publish(ctx: Context, file: File, displayName: String = file.name): Pair<String, Long> {
        val mime = mimeOf(displayName)
        return if (Build.VERSION.SDK_INT >= 29) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create file in Downloads")
            try {
                resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null); throw t
            }
            uri.toString() to file.length()
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            dir.mkdirs()
            val dest = uniqueFile(dir, displayName)
            if (!file.renameTo(dest)) file.copyTo(dest, overwrite = false)
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime), null)
            Uri.fromFile(dest).toString() to dest.length()
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        while (f.exists()) f = File(dir, "$base ($n)${if (ext.isNotEmpty()) ".$ext" else ""}").also { n++ }
        return f
    }

    /** Uri safe to hand to other apps. */
    fun shareable(ctx: Context, ref: String): Uri {
        val uri = Uri.parse(ref)
        return if (uri.scheme == "file") FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(uri.path!!))
        else uri
    }

    fun exists(ctx: Context, ref: String): Boolean = runCatching {
        val uri = Uri.parse(ref)
        if (uri.scheme == "file") File(uri.path!!).exists()
        else ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    fun delete(ctx: Context, ref: String): Boolean = runCatching {
        val uri = Uri.parse(ref)
        if (uri.scheme == "file") File(uri.path!!).delete()
        else ctx.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)

    fun openIntent(ctx: Context, ref: String, mime: String?): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(shareable(ctx, ref), mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun shareIntent(ctx: Context, ref: String, mime: String?): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(mime ?: "*/*")
                .putExtra(Intent.EXTRA_STREAM, shareable(ctx, ref))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            null,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val folderLabel get() = "Download/$FOLDER"

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: when (name.substringAfterLast('.', "").lowercase()) {
                "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "opus" -> "audio/ogg"; "m4a" -> "audio/mp4"
                else -> "application/octet-stream"
            }
}

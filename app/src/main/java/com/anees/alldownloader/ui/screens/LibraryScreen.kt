package com.anees.alldownloader.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anees.alldownloader.data.DownloadRecord
import com.anees.alldownloader.data.Kind
import com.anees.alldownloader.ui.LibraryFilter
import com.anees.alldownloader.ui.components.EmptyState
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.Hairline
import com.anees.alldownloader.ui.components.MetaLine
import com.anees.alldownloader.ui.components.ScreenHeader
import com.anees.alldownloader.ui.components.Tag
import com.anees.alldownloader.ui.components.Thumb
import com.anees.alldownloader.ui.components.fmtBytes
import com.anees.alldownloader.ui.components.fmtDuration

@Composable
fun LibraryScreen(
    items: List<DownloadRecord>,
    totalCount: Int,
    filter: LibraryFilter,
    missing: Set<String>,
    onQuery: (String) -> Unit,
    onKind: (Kind?) -> Unit,
    onOpen: (DownloadRecord) -> Unit,
    onShare: (DownloadRecord) -> Unit,
    onRedownload: (DownloadRecord) -> Unit,
    onRemove: (DownloadRecord, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(totalCount) { onRefresh() }
    var confirm by remember { mutableStateOf<DownloadRecord?>(null) }
    val totalBytes = remember(items) { items.sumOf { it.sizeBytes } }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            ScreenHeader("Library", if (totalCount == 0) "Your saved files" else "$totalCount ${if (totalCount == 1) "file" else "files"} · ${fmtBytes(totalBytes)}")
        }
        if (totalCount > 0) item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Gap(6.dp)
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    HGap(10.dp)
                    Box(Modifier.weight(1f)) {
                        if (filter.query.isEmpty()) Text("Search title, site, channel", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        BasicTextField(filter.query, onQuery, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                    }
                }
                Gap(10.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip("All", filter.kind == null) { onKind(null) }
                    Kind.entries.forEach { k -> FilterChip(k.label, filter.kind == k) { onKind(k) } }
                }
                Gap(8.dp)
            }
        }
        if (totalCount == 0) item {
            EmptyState(Icons.Rounded.VideoLibrary, "No downloads yet", "Finished files appear here. They're also in Download/AllDownloader on your phone.")
        } else if (items.isEmpty()) item {
            EmptyState(Icons.Rounded.Search, "No matches", "Try a different word or filter.")
        }
        items(items, key = { it.id }) { rec ->
            LibraryRow(rec, rec.id in missing, onOpen, onShare, onRedownload, { confirm = rec }, onCopied)
            Hairline(Modifier.padding(start = 20.dp))
        }
        item { Gap(24.dp) }
    }

    confirm?.let { rec ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove download?") },
            text = { Text("“${rec.title}”\n\nRemove it from the library only, or delete the file from your phone too.") },
            confirmButton = { TextButton(onClick = { onRemove(rec, true); confirm = null }) { Text("Delete file", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { onRemove(rec, false); confirm = null }) { Text("Remove from list") } },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Text(
        label, style = MaterialTheme.typography.labelLarge,
        color = if (selected) s.onSecondary else s.onSurface,
        modifier = Modifier.clip(MaterialTheme.shapes.small)
            .then(if (selected) Modifier.background(s.secondary) else Modifier.border(1.dp, s.outline, MaterialTheme.shapes.small))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun LibraryRow(
    rec: DownloadRecord, missing: Boolean,
    onOpen: (DownloadRecord) -> Unit, onShare: (DownloadRecord) -> Unit,
    onRedownload: (DownloadRecord) -> Unit, onDelete: () -> Unit, onCopied: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current
    val icon = when (rec.kind) { Kind.AUDIO -> Icons.Rounded.MusicNote; Kind.VIDEO -> Icons.Rounded.Movie; Kind.OTHER -> Icons.Rounded.InsertDriveFile }
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(rec) }.padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(rec.thumb, fallback = icon, width = 96.dp, height = 54.dp)
        HGap(14.dp)
        Column(Modifier.weight(1f)) {
            Text(rec.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (missing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Gap(4.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (missing) { Tag("MISSING", MaterialTheme.colorScheme.error); HGap(6.dp) }
                MetaLine(listOf(
                    rec.outName?.substringAfterLast('.', "")?.uppercase().orEmpty(),
                    fmtBytes(rec.sizeBytes),
                    fmtDuration(rec.durationSec),
                    DateUtils.getRelativeTimeSpanString(rec.finishedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                ))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Share") }, { menu = false; onShare(rec) }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, enabled = !missing)
                DropdownMenuItem({ Text("Copy source link") }, { menu = false; clip.setText(AnnotatedString(rec.url)); onCopied() }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) })
                DropdownMenuItem({ Text("Download again") }, { menu = false; onRedownload(rec) }, leadingIcon = { Icon(Icons.Rounded.Download, null) })
                DropdownMenuItem({ Text("Remove…") }, { menu = false; onDelete() }, leadingIcon = { Icon(if (missing) Icons.Rounded.DeleteOutline else Icons.Rounded.Delete, null) })
            }
        }
    }
}

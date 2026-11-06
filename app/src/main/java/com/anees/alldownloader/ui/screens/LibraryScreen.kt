package com.anees.alldownloader.ui.screens

import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anees.alldownloader.data.DownloadRecord
import com.anees.alldownloader.data.Kind
import com.anees.alldownloader.ui.LibraryFilter
import com.anees.alldownloader.ui.LibrarySort
import com.anees.alldownloader.ui.components.EmptyState
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.LargeTitleList
import com.anees.alldownloader.ui.components.MetaLine
import com.anees.alldownloader.ui.components.Motion
import com.anees.alldownloader.ui.components.SearchField
import com.anees.alldownloader.ui.components.Segmented
import com.anees.alldownloader.ui.components.Separator
import com.anees.alldownloader.ui.components.Tag
import com.anees.alldownloader.ui.components.TextAction
import com.anees.alldownloader.ui.components.Thumb
import com.anees.alldownloader.ui.components.fmtBytes
import com.anees.alldownloader.ui.components.fmtDuration
import com.anees.alldownloader.ui.components.pressable
import com.anees.alldownloader.ui.components.rememberHaptics
import com.anees.alldownloader.ui.components.rowPress
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles

class LibraryActions(
    val onQuery: (String) -> Unit,
    val onKind: (Kind?) -> Unit,
    val onSort: (LibrarySort) -> Unit,
    val onOpen: (DownloadRecord) -> Unit,
    val onShare: (List<DownloadRecord>) -> Unit,
    val onRedownload: (DownloadRecord) -> Unit,
    val onRemove: (List<DownloadRecord>, Boolean) -> Unit,
    val onRefresh: () -> Unit,
    val onCopied: () -> Unit,
    val onRemoveMissing: () -> Unit,
    val onOpenSource: (DownloadRecord) -> Unit,
)

@Composable
fun LibraryScreen(
    items: List<DownloadRecord>,
    all: List<DownloadRecord>,
    filter: LibraryFilter,
    missing: Set<String>,
    a: LibraryActions,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.c
    val h = rememberHaptics()
    val totalCount = all.size
    LaunchedEffect(totalCount) { a.onRefresh() }
    var confirm by remember { mutableStateOf<List<DownloadRecord>?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sortOpen by remember { mutableStateOf(false) }
    val totalBytes = remember(all) { all.sumOf { it.sizeBytes } }
    BackHandler(selecting) { selecting = false; selected = emptySet() }

    Box(modifier.fillMaxSize()) {
        LargeTitleList(
            "Library",
            subtitle = if (totalCount == 0) "Your saved files" else "$totalCount ${if (totalCount == 1) "file" else "files"} · ${fmtBytes(totalBytes)}",
            actions = {
                if (totalCount > 0) {
                    if (!selecting) Box {
                        Box(Modifier.size(36.dp).clip(CircleShape).pressable(scale = 0.88f) { sortOpen = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SwapVert, "Sort", tint = c.accent, modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(sortOpen, { sortOpen = false }, shape = RoundedCornerShape(14.dp), containerColor = c.card) {
                            LibrarySort.entries.forEach { s ->
                                DropdownMenuItem(
                                    { Text(s.label, style = TextStyles.body, fontWeight = if (s == filter.sort) FontWeight.SemiBold else FontWeight.Normal) },
                                    { sortOpen = false; h.tap(); a.onSort(s) },
                                    trailingIcon = { if (s == filter.sort) Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, tint = c.accent) },
                                )
                            }
                        }
                    }
                    TextAction(if (selecting) "Done" else "Select", { h.tap(); selecting = !selecting; selected = emptySet() }, bold = selecting)
                }
            },
        ) {
            if (totalCount > 0) item(key = "tools") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SearchField(filter.query, a.onQuery, "Search title, site, channel")
                    Gap(10.dp)
                    val kinds = listOf<Kind?>(null) + Kind.entries
                    Segmented(listOf("All", "Video", "Audio", "Files"), kinds.indexOf(filter.kind), { a.onKind(kinds[it]) }, Modifier.fillMaxWidth())
                    Gap(16.dp)
                    StorageBar(all)
                    if (missing.isNotEmpty()) {
                        Gap(10.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.orange.copy(alpha = 0.12f))
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Warning, null, tint = c.orange, modifier = Modifier.size(18.dp))
                            HGap(10.dp)
                            Text(
                                "${missing.size} ${if (missing.size == 1) "file was" else "files were"} moved or deleted",
                                style = TextStyles.footnote, color = c.label, modifier = Modifier.weight(1f),
                            )
                            TextAction("Clean Up", { h.tap(); a.onRemoveMissing() })
                        }
                    }
                    Gap(18.dp)
                }
            }
            if (totalCount == 0) item(key = "empty") {
                EmptyState(Icons.Rounded.VideoLibrary, "No downloads yet", "Finished files land here, and in Download/AllDownloader on your phone.")
            } else if (items.isEmpty()) item(key = "nomatch") {
                EmptyState(Icons.Rounded.Search, "No Results", "Try another word or filter.")
            }
            itemsIndexed(items, key = { _, r -> r.id }) { i, rec ->
                val first = i == 0; val last = i == items.lastIndex
                val shape = RoundedCornerShape(topStart = if (first) 14.dp else 0.dp, topEnd = if (first) 14.dp else 0.dp, bottomStart = if (last) 14.dp else 0.dp, bottomEnd = if (last) 14.dp else 0.dp)
                Column(Modifier.animateItem(placementSpec = Motion.default()).padding(horizontal = 16.dp).clip(shape).background(c.card)) {
                    LibraryRow(
                        rec, rec.id in missing, selecting, rec.id in selected,
                        onTap = {
                            if (selecting) { h.tap(); selected = if (rec.id in selected) selected - rec.id else selected + rec.id }
                            else a.onOpen(rec)
                        },
                        onSelectStart = { h.confirm(); selecting = true; selected = setOf(rec.id) },
                        a = a, onDelete = { confirm = listOf(rec) },
                    )
                    if (!last) Separator(if (selecting) 140.dp else 104.dp)
                }
            }
        }

        // Selection toolbar floats above the tab bar, materialising from below.
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        AnimatedVisibility(
            selecting, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 84.dp),
            enter = slideInVertically(Motion.default()) { it } + fadeIn(Motion.default()),
            exit = slideOutVertically(Motion.default()) { it } + fadeOut(Motion.default()),
        ) {
            val picks = items.filter { it.id in selected }
            Row(
                Modifier.widthIn(max = 440.dp).padding(horizontal = 20.dp).fillMaxWidth().height(52.dp)
                    .graphicsLayer { shadowElevation = 16f; shape = CircleShape; clip = true }
                    .background(c.card).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(if (selected.size == items.size && items.isNotEmpty()) "None" else "All", {
                    h.tap(); selected = if (selected.size == items.size) emptySet() else items.map { it.id }.toSet()
                })
                Text(if (selected.isEmpty()) "Select items" else "${selected.size} selected", style = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = c.label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                val en = picks.isNotEmpty()
                Box(Modifier.size(40.dp).clip(CircleShape).pressable(enabled = en) { a.onShare(picks.filter { it.id !in missing }) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Share, "Share", tint = if (en) c.accent else c.tertiary)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).pressable(enabled = en) { confirm = picks }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Delete, "Delete", tint = if (en) c.red else c.tertiary)
                }
            }
        }
    }

    confirm?.let { recs ->
        val one = recs.size == 1
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = c.card, shape = RoundedCornerShape(22.dp),
            title = { Text(if (one) "Remove Download?" else "Remove ${recs.size} Downloads?", style = TextStyles.headline) },
            text = { Text(if (one) "“${recs[0].title}”\n\nRemove it from the library only, or delete the file too." else "Remove them from the library only, or delete the files too.", style = TextStyles.subhead) },
            confirmButton = {
                TextButton(onClick = { h.reject(); a.onRemove(recs, true); confirm = null; selecting = false; selected = emptySet() }) {
                    Text(if (one) "Delete File" else "Delete Files", color = c.red, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { a.onRemove(recs, false); confirm = null; selecting = false; selected = emptySet() }) { Text("Remove from List", color = c.accent) }
            },
        )
    }
}

/** Storage split by kind, like iPhone Storage. */
@Composable
private fun StorageBar(all: List<DownloadRecord>) {
    val c = AppTheme.c
    val total = all.sumOf { it.sizeBytes }.coerceAtLeast(1)
    val parts = listOf(Kind.VIDEO to c.accent, Kind.AUDIO to c.pink, Kind.OTHER to c.orange)
        .map { (k, col) -> Triple(k, col, all.filter { it.kind == k }.sumOf { it.sizeBytes }) }
        .filter { it.third > 0 }
    if (parts.isEmpty()) return
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(c.card).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.fill), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEach { (_, col, b) ->
                Box(Modifier.weight((b.toFloat() / total).coerceAtLeast(0.01f)).height(8.dp).background(col))
            }
        }
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            parts.forEach { (k, col, b) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                    HGap(5.dp)
                    Text("${k.label} ${fmtBytes(b)}", style = TextStyles.caption, color = c.secondary)
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    rec: DownloadRecord, missing: Boolean, selecting: Boolean, checked: Boolean,
    onTap: () -> Unit, onSelectStart: () -> Unit, a: LibraryActions, onDelete: () -> Unit,
) {
    val c = AppTheme.c
    var menu by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current
    val h = rememberHaptics()
    val icon = when (rec.kind) { Kind.AUDIO -> Icons.Rounded.MusicNote; Kind.VIDEO -> Icons.Rounded.Movie; Kind.OTHER -> Icons.Rounded.InsertDriveFile }
    Box {
        Row(
            Modifier.fillMaxWidth().rowPress(onLongClick = { if (!selecting) { h.confirm(); menu = true } }, onClick = onTap)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(selecting) { Row { CheckCircle(checked); HGap(12.dp) } }
            Box {
                Thumb(rec.thumb, fallback = icon, width = 78.dp, height = 52.dp)
                if (rec.kind == Kind.AUDIO && rec.thumb != null) Box(
                    Modifier.align(Alignment.BottomEnd).padding(3.dp).size(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(10.dp)) }
            }
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                Text(rec.title, style = TextStyles.subhead.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (missing) c.secondary else c.label)
                Gap(3.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (missing) { Icon(Icons.Rounded.Warning, null, tint = c.orange, modifier = Modifier.size(13.dp)); HGap(4.dp) }
                    if (rec.section != null) { Tag("CLIP", c.pink); HGap(5.dp) }
                    MetaLine(listOf(
                        rec.outName?.substringAfterLast('.', "")?.uppercase().orEmpty(),
                        fmtBytes(rec.sizeBytes),
                        fmtDuration(rec.durationSec),
                        if (System.currentTimeMillis() - rec.finishedAt < 60_000) "Just now"
                        else DateUtils.getRelativeTimeSpanString(rec.finishedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    ), style = TextStyles.caption)
                }
            }
        }
        // Context menu (long press), anchored to the row.
        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(14.dp), containerColor = c.card) {
            DropdownMenuItem({ Text("Share") }, { menu = false; a.onShare(listOf(rec)) }, trailingIcon = { Icon(Icons.Rounded.Share, null) }, enabled = !missing)
            DropdownMenuItem({ Text("Copy Source Link") }, { menu = false; clip.setText(AnnotatedString(rec.url)); a.onCopied() }, trailingIcon = { Icon(Icons.Rounded.ContentCopy, null) })
            DropdownMenuItem({ Text("Open Source Page") }, { menu = false; a.onOpenSource(rec) }, trailingIcon = { Icon(Icons.Rounded.Language, null) })
            DropdownMenuItem({ Text("Download Again") }, { menu = false; a.onRedownload(rec) }, trailingIcon = { Icon(Icons.Rounded.Download, null) })
            DropdownMenuItem({ Text("Select") }, { menu = false; onSelectStart() }, trailingIcon = { Icon(androidx.compose.material.icons.Icons.Rounded.CheckCircle, null) })
            DropdownMenuItem({ Text("Delete…", color = c.red) }, { menu = false; onDelete() }, trailingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.red) })
        }
    }
}

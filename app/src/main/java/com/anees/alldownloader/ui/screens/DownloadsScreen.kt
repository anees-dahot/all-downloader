package com.anees.alldownloader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anees.alldownloader.data.Status
import com.anees.alldownloader.ui.ActiveItem
import com.anees.alldownloader.ui.components.EmptyState
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.Hairline
import com.anees.alldownloader.ui.components.MetaLine
import com.anees.alldownloader.ui.components.ScreenHeader
import com.anees.alldownloader.ui.components.SectionLabel
import com.anees.alldownloader.ui.components.Tag
import com.anees.alldownloader.ui.components.Thumb
import com.anees.alldownloader.ui.theme.Mono

@Composable
fun DownloadsScreen(
    items: List<ActiveItem>,
    wifiOnly: Boolean,
    onCancel: (ActiveItem) -> Unit,
    onRetry: (ActiveItem) -> Unit,
    onDismiss: (ActiveItem) -> Unit,
    onClearFailed: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = items.filter { it.rec.status.isActive }
    val failed = items.filter { it.rec.status == Status.FAILED }
    LazyColumn(modifier.fillMaxSize()) {
        item {
            ScreenHeader(
                "Downloads",
                when {
                    running.isEmpty() && failed.isEmpty() -> "Nothing in progress"
                    running.isEmpty() -> "${failed.size} need attention"
                    else -> "${running.size} in progress" + if (wifiOnly) " · Wi-Fi only" else ""
                },
            )
        }
        if (items.isEmpty()) item {
            EmptyState(Icons.Rounded.CloudDownload, "Queue is empty", "Find something on the Grab tab and it'll show up here while it downloads.") {
                OutlinedButton(onClick = onGoHome) { Text("Grab a link") }
            }
        }
        if (running.isNotEmpty()) {
            item { SectionLabel("Active") }
            items(running, key = { it.rec.id }) { ActiveRow(it, onCancel); Hairline(Modifier.padding(start = 20.dp)) }
        }
        if (failed.isNotEmpty()) {
            item {
                SectionLabel("Failed", Modifier.padding(top = 12.dp)) {
                    TextButton(onClick = onClearFailed) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(16.dp)); HGap(6.dp); Text("Clear")
                    }
                }
            }
            items(failed, key = { it.rec.id }) { FailedRow(it, onRetry, onDismiss); Hairline(Modifier.padding(start = 20.dp)) }
        }
        item { Gap(24.dp) }
    }
}

@Composable
private fun ActiveRow(item: ActiveItem, onCancel: (ActiveItem) -> Unit) {
    val p by animateFloatAsState((item.progress.coerceIn(0f, 100f)) / 100f, label = "p")
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
        Thumb(item.rec.thumb, fallback = if (item.rec.quality.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Movie, width = 80.dp, height = 45.dp)
        HGap(14.dp)
        Column(Modifier.weight(1f)) {
            Text(item.rec.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Gap(8.dp)
            if (item.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), strokeCap = StrokeCap.Butt)
            else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp), strokeCap = StrokeCap.Butt, gapSize = 0.dp, drawStopIndicator = {})
            Gap(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.status, style = MaterialTheme.typography.labelSmall, fontFamily = Mono, modifier = Modifier.weight(1f), maxLines = 1)
                MetaLine(listOfNotNull(item.speed, item.total))
            }
        }
        IconButton(onClick = { onCancel(item) }) { Icon(Icons.Rounded.Close, "Cancel") }
    }
}

@Composable
private fun FailedRow(item: ActiveItem, onRetry: (ActiveItem) -> Unit, onDismiss: (ActiveItem) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
        Thumb(item.rec.thumb, fallback = Icons.Rounded.Movie, width = 80.dp, height = 45.dp)
        HGap(14.dp)
        Column(Modifier.weight(1f)) {
            Text(item.rec.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Gap(4.dp)
            Row { Tag("FAILED", MaterialTheme.colorScheme.error); HGap(6.dp); Tag(item.rec.quality.label.uppercase()) }
            Gap(6.dp)
            Text(item.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Column {
            IconButton(onClick = { onRetry(item) }) { Icon(Icons.Rounded.Refresh, "Retry") }
            IconButton(onClick = { onDismiss(item) }) { Icon(Icons.Rounded.Close, "Dismiss") }
        }
    }
}

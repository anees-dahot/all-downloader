package com.anees.alldownloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anees.alldownloader.data.Status
import com.anees.alldownloader.ui.ActiveItem
import com.anees.alldownloader.ui.components.CircleIcon
import com.anees.alldownloader.ui.components.EmptyState
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.LargeTitleList
import com.anees.alldownloader.ui.components.MetaLine
import com.anees.alldownloader.ui.components.Motion
import com.anees.alldownloader.ui.components.PillButton
import com.anees.alldownloader.ui.components.Separator
import com.anees.alldownloader.ui.components.Tag
import com.anees.alldownloader.ui.components.TextAction
import com.anees.alldownloader.ui.components.Thumb
import com.anees.alldownloader.ui.components.ProgressBar
import com.anees.alldownloader.ui.components.rememberHaptics
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles

class QueueActions(
    val onCancel: (ActiveItem) -> Unit,
    val onPause: (ActiveItem) -> Unit,
    val onResume: (ActiveItem) -> Unit,
    val onRetry: (ActiveItem) -> Unit,
    val onDismiss: (ActiveItem) -> Unit,
    val onClearFailed: () -> Unit,
    val onPauseAll: () -> Unit,
    val onResumeAll: () -> Unit,
    val onGoHome: () -> Unit,
)

@Composable
fun DownloadsScreen(items: List<ActiveItem>, wifiOnly: Boolean, a: QueueActions, modifier: Modifier = Modifier) {
    val c = AppTheme.c
    val h = rememberHaptics()
    val running = items.filter { it.rec.status.isActive }
    val paused = items.filter { it.rec.status == Status.PAUSED }
    val failed = items.filter { it.rec.status == Status.FAILED }
    LargeTitleList(
        "Queue", modifier,
        subtitle = when {
            items.isEmpty() -> "Nothing in progress"
            running.isEmpty() && paused.isNotEmpty() -> "${paused.size} paused"
            running.isEmpty() -> "${failed.size} need attention"
            else -> buildString {
                append("${running.size} downloading")
                // Average over jobs that report a percentage; queued ones would drag it to 0.
                val known = running.map { it.progress }.filter { it >= 0f }
                if (known.isNotEmpty()) append(" · ${(known.average()).toInt()}%")
                if (wifiOnly) append(" · Wi-Fi only")
            }
        },
        actions = {
            when {
                running.isNotEmpty() -> TextAction("Pause All", { h.tap(); a.onPauseAll() })
                paused.isNotEmpty() -> TextAction("Resume All", { h.tap(); a.onResumeAll() })
            }
        },
    ) {
        if (items.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Rounded.CloudDownload, "Nothing downloading", "Grab a link and it shows up here with live speed and progress.") {
                PillButton("Grab a Link", a.onGoHome, height = 44.dp)
            }
        }
        group("Downloading", running, swipe = "Cancel" to { it -> h.reject(); a.onCancel(it) }) { i, it, last -> ActiveRow(it, last, { h.tap(); a.onPause(it) }, { h.reject(); a.onCancel(it) }) }
        group("Paused", paused, swipe = "Cancel" to { it -> h.reject(); a.onCancel(it) }) { i, it, last -> ActiveRow(it, last, { h.tap(); a.onResume(it) }, { h.reject(); a.onCancel(it) }) }
        group("Failed", failed, swipe = "Dismiss" to { it -> h.tap(); a.onDismiss(it) }, trailing = { TextAction("Clear", { h.tap(); a.onClearFailed() }, color = c.red) }) { i, it, last ->
            FailedRow(it, last, { h.confirm(); a.onRetry(it) }, { a.onDismiss(it) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    title: String, list: List<ActiveItem>, swipe: Pair<String, (ActiveItem) -> Unit>, trailing: (@Composable () -> Unit)? = null,
    row: @Composable LazyItemScope.(Int, ActiveItem, Boolean) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") {
        Row(Modifier.animateItem(placementSpec = Motion.default()).fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = TextStyles.footnote, color = AppTheme.c.secondary, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
    itemsIndexed(list, key = { _, it -> it.rec.id }) { i, it ->
        val first = i == 0; val last = i == list.lastIndex
        val shape = RoundedCornerShape(topStart = if (first) 14.dp else 0.dp, topEnd = if (first) 14.dp else 0.dp, bottomStart = if (last) 14.dp else 0.dp, bottomEnd = if (last) 14.dp else 0.dp)
        Box(Modifier.animateItem(placementSpec = Motion.default()).padding(horizontal = 16.dp).clip(shape)) {
            SwipeRow(swipe.first, { swipe.second(it) }) {
                Box(Modifier.background(AppTheme.c.card)) { row(i, it, last) }
            }
        }
    }
}

@Composable
private fun ActiveRow(item: ActiveItem, last: Boolean, onToggle: () -> Unit, onCancel: () -> Unit) {
    val c = AppTheme.c
    val paused = item.rec.status == Status.PAUSED
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Thumb(item.rec.thumb, fallback = if (item.rec.quality.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Movie, width = 72.dp, height = 72.dp, shape = RoundedCornerShape(10.dp))
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                Text(item.rec.displayTitle, style = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Gap(3.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(item.rec.quality.short.uppercase())
                    if (item.rec.section != null) { HGap(5.dp); Tag("CLIP", c.pink) }
                    if (item.waitingForNetwork) { HGap(6.dp); Icon(Icons.Rounded.WifiOff, null, tint = c.orange, modifier = Modifier.size(14.dp)) }
                }
                Gap(9.dp)
                ProgressBar(if (item.progress < 0 && !paused) null else (item.progress.coerceAtLeast(0f) / 100f), color = if (paused) c.gray else c.accent)
                Gap(6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.waitingForNetwork) (if (item.status == "Waiting in queue") "Waiting for network" else item.status) else item.status,
                        style = TextStyles.numeric.copy(fontSize = TextStyles.caption.fontSize), color = c.secondary, modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    MetaLine(listOfNotNull(item.speed, item.total), style = TextStyles.caption)
                }
            }
            HGap(6.dp)
            Column {
                CircleIcon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) "Resume" else "Pause", onToggle)
                Gap(8.dp)
                CircleIcon(Icons.Rounded.Close, "Cancel", onCancel, tint = c.secondary)
            }
        }
        if (!last) Separator(98.dp)
    }
}

@Composable
private fun FailedRow(item: ActiveItem, last: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.c
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Thumb(item.rec.thumb, fallback = if (item.rec.section != null) Icons.Rounded.ContentCut else Icons.Rounded.Movie, width = 72.dp, height = 72.dp, shape = RoundedCornerShape(10.dp))
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                Text(item.rec.displayTitle, style = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Gap(3.dp)
                Text(item.status, style = TextStyles.footnote, color = c.red, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            HGap(6.dp)
            Column {
                CircleIcon(Icons.Rounded.Refresh, "Retry", onRetry)
                Gap(8.dp)
                CircleIcon(Icons.Rounded.Close, "Dismiss", onDismiss, tint = c.secondary)
            }
        }
        if (!last) Separator(98.dp)
    }
}

/** Swipe left to reveal a red action, Mail-style. Full swipe commits it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(label: String, onSwiped: () -> Unit, content: @Composable () -> Unit) {
    val c = AppTheme.c
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) { onSwiped(); true } else false },
        positionalThreshold = { it * 0.4f },
    )
    SwipeToDismissBox(
        state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(c.red).padding(end = 22.dp), contentAlignment = Alignment.CenterEnd) {
                Text(label, style = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }
        },
    ) { content() }
}

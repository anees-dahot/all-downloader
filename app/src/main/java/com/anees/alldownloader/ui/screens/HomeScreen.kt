package com.anees.alldownloader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import com.anees.alldownloader.ui.components.Thumb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.anees.alldownloader.data.EngineState
import com.anees.alldownloader.data.MediaInfo
import com.anees.alldownloader.data.Quality
import com.anees.alldownloader.ui.HomeState
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.Hairline
import com.anees.alldownloader.ui.components.SectionLabel
import com.anees.alldownloader.ui.components.SegmentRow
import com.anees.alldownloader.ui.components.Tag
import com.anees.alldownloader.ui.components.fmtBytes
import com.anees.alldownloader.ui.components.fmtDuration
import com.anees.alldownloader.ui.theme.Eyebrow
import com.anees.alldownloader.ui.theme.LocalExtra

@Composable
fun HomeScreen(
    state: HomeState,
    engine: EngineState,
    onUrl: (String) -> Unit,
    onPaste: (String) -> Unit,
    onFetch: () -> Unit,
    onClear: () -> Unit,
    onQuality: (Quality) -> Unit,
    onDownload: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSingle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val info = state.info
    LazyColumn(modifier.imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("All\nDownloader", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                EnginePill(engine)
            }
        }
        item(key = "input") {
            LinkInput(state, onUrl, { clipboard.getText()?.text?.let(onPaste) }, { focus.clearFocus(); onFetch() }, onClear)
        }
        when {
            info?.isPlaylist == true -> {
                item(key = "pl-head") { PlaylistHeader(info, state, onQuality, onSelectAll, onSingle) }
                items(info.entries, key = { "e-" + it.id }) { e ->
                    EntryRow(e, e.id in state.selected) { onToggle(e.id) }
                }
                item(key = "pl-go") {
                    Button(
                        onClick = onDownload, enabled = state.selected.isNotEmpty(),
                        shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(vertical = 16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)); HGap(8.dp)
                        Text("Download ${state.selected.size} · ${state.quality.label}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            info != null -> item(key = "preview") { PreviewPanel(info, state.quality, onQuality, onDownload) }
            state.loading -> item(key = "loading") { LoadingPanel() }
            else -> item(key = "tips") { Tips() }
        }
    }
}

@Composable
private fun LinkInput(state: HomeState, onUrl: (String) -> Unit, onPaste: () -> Unit, onFetch: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        val hasError = state.error != null
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (state.url.isEmpty()) Text(
                    "Paste a link: video, playlist or file",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                BasicTextField(
                    value = state.url, onValueChange = onUrl, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onFetch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.url.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Clear") }
            else TextButton(onClick = onPaste) { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); HGap(6.dp); Text("Paste") }
        }
        if (state.error != null) {
            Gap(10.dp)
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                HGap(8.dp)
                Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.info == null) {
            Gap(12.dp)
            Button(
                onClick = onFetch, enabled = state.url.isNotBlank() && !state.loading,
                shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    HGap(10.dp); Text("Reading link…")
                } else Text("Find media", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistHeader(info: MediaInfo, state: HomeState, onQuality: (Quality) -> Unit, onSelectAll: (Boolean) -> Unit, onSingle: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag("PLAYLIST", filled = true); HGap(8.dp); Tag("${info.entries.size} ITEMS")
            if (info.durationSec > 0) { HGap(8.dp); Tag(fmtDuration(info.durationSec)) }
        }
        Gap(10.dp)
        Text(info.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (info.uploader != null) Text(info.uploader, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.url.contains("v=")) {
            TextButton(onClick = onSingle, contentPadding = PaddingValues(0.dp)) { Text("Only download the video in this link") }
        } else Gap(12.dp)
        var mode by rememberSaveable { mutableIntStateOf(if (state.quality.isAudio) 1 else 0) }
        SegmentRow(listOf("Video", "Audio only"), mode, { mode = it; onQuality(if (it == 1) Quality.MP3 else Quality.BEST) }, Modifier.fillMaxWidth())
        Gap(12.dp)
        val opts = if (mode == 1) Quality.audioOnly else listOf(Quality.BEST, Quality.P1080, Quality.P720, Quality.P480, Quality.P360)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEach { q -> QualityChip(q, null, q == state.quality) { onQuality(q) } }
        }
        Text(
            "Heights are a ceiling — items without that quality get the next best.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Gap(16.dp)
        val all = state.selected.size == info.entries.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.selected.size} of ${info.entries.size} selected".uppercase(), style = Eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSelectAll(!all) }) { Text(if (all) "Select none" else "Select all") }
        }
        Hairline()
    }
}

@Composable
private fun EntryRow(e: com.anees.alldownloader.data.PlaylistEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, { onToggle() })
        Thumb(e.thumb, fallback = Icons.Rounded.Movie, width = 80.dp, height = 45.dp,
            modifier = Modifier.alpha(if (checked) 1f else 0.45f))
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.durationSec > 0) Text(fmtDuration(e.durationSec), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EnginePill(state: EngineState) {
    val (label, color) = when (state) {
        EngineState.Starting -> "WARMING UP" to MaterialTheme.colorScheme.onSurfaceVariant
        is EngineState.Updating -> "UPDATING" to LocalExtra.current.warn
        is EngineState.Ready -> "READY" to LocalExtra.current.success
        is EngineState.Failed -> "ENGINE ERROR" to MaterialTheme.colorScheme.error
    }
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a",
    )
    Row(
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(7.dp).alpha(if (state is EngineState.Ready || state is EngineState.Failed) 1f else pulse.value)
                .clip(CircleShape).background(color)
        )
        HGap(7.dp)
        Text(label, style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingPanel() {
    val a = rememberInfiniteTransition(label = "sk").animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Column(Modifier.padding(16.dp).alpha(a.value)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Gap(14.dp)
        Box(Modifier.fillMaxWidth(0.8f).height(18.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Gap(8.dp)
        Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewPanel(info: MediaInfo, quality: Quality, onQuality: (Quality) -> Unit, onDownload: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (info.isDirect) Icons.Rounded.InsertDriveFile else Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (info.thumbnail != null) AsyncImage(info.thumbnail, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            if (info.durationSec > 0) Text(
                fmtDuration(info.durationSec),
                style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    .clip(MaterialTheme.shapes.extraSmall).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Gap(14.dp)
        Text(info.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Gap(6.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(if (info.isDirect) "DIRECT FILE" else info.site.uppercase())
            if (info.uploader != null) {
                HGap(8.dp)
                Text(info.uploader, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Gap(20.dp)

        if (!info.isDirect) {
            var mode by rememberSaveable { mutableIntStateOf(if (quality.isAudio) 1 else 0) }
            SegmentRow(listOf("Video", "Audio only"), mode, {
                mode = it
                onQuality(if (it == 1) Quality.MP3 else Quality.BEST)
            }, Modifier.fillMaxWidth())
            Gap(14.dp)
            val options = remember(info, mode) {
                if (mode == 1) Quality.audioOnly
                else Quality.video.filter { it.height == null || info.heights.contains(it.height) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { q ->
                    val size = when {
                        q.isAudio -> info.audioSize
                        q.height != null -> info.sizeByHeight[q.height]
                        else -> info.heights.firstOrNull()?.let { info.sizeByHeight[it] }
                    }
                    QualityChip(q, size, q == quality) { onQuality(q) }
                }
            }
        } else {
            Text("This link points straight to a file. It will be fetched with 16 parallel connections.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Gap(22.dp)
        Button(
            onClick = onDownload,
            shape = MaterialTheme.shapes.large,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)); HGap(8.dp)
            Text(if (info.isDirect) "Download file" else "Download ${quality.label}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QualityChip(q: Quality, size: Long?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.widthIn(min = 96.dp).clip(MaterialTheme.shapes.medium)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (q.isAudio) { Icon(Icons.Rounded.MusicNote, null, Modifier.size(14.dp)); HGap(4.dp) }
            Text(if (q == Quality.BEST) "Best" else q.label, style = MaterialTheme.typography.titleSmall,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
        }
        Text(
            size?.let { "≈ ${fmtBytes(it)}" } ?: when { q == Quality.BEST -> "highest"; q.isAudio -> "audio"; else -> "max" },
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Tips() {
    Column(Modifier.padding(top = 28.dp)) {
        SectionLabel("Three ways in")
        Hairline(Modifier.padding(horizontal = 20.dp))
        Tip("01", "Share from any app", "Tap Share in YouTube, Instagram, a browser… then pick All Downloader.", Icons.Rounded.Share)
        Hairline(Modifier.padding(horizontal = 20.dp))
        Tip("02", "Paste a link", "Copy a link, tap Paste above, then Find media.", Icons.Rounded.ContentPaste)
        Hairline(Modifier.padding(horizontal = 20.dp))
        Tip("03", "Direct file links", "Links to .zip, .apk, .pdf or any file download with up to 16 connections.", Icons.Rounded.InsertDriveFile)
        Hairline(Modifier.padding(horizontal = 20.dp))
        Gap(18.dp)
        Text(
            "Powered by yt-dlp — over 1,000 sites. Files land in Download/AllDownloader.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun Tip(n: String, title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.Top) {
        Text(n, style = Eyebrow, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 3.dp))
        HGap(16.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Gap(2.dp)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HGap(12.dp)
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

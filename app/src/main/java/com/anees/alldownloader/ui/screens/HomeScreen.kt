package com.anees.alldownloader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.anees.alldownloader.data.EngineState
import com.anees.alldownloader.data.MediaInfo
import com.anees.alldownloader.data.PlaylistEntry
import com.anees.alldownloader.data.Quality
import com.anees.alldownloader.ui.HomeState
import com.anees.alldownloader.ui.components.Cell
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.AppleSwitch
import com.anees.alldownloader.ui.components.LargeTitleList
import com.anees.alldownloader.ui.components.MetaLine
import com.anees.alldownloader.ui.components.Motion
import com.anees.alldownloader.ui.components.PillButton
import com.anees.alldownloader.ui.components.Section
import com.anees.alldownloader.ui.components.Segmented
import com.anees.alldownloader.ui.components.Separator
import com.anees.alldownloader.ui.components.Spinner
import com.anees.alldownloader.ui.components.Tag
import com.anees.alldownloader.ui.components.TextAction
import com.anees.alldownloader.ui.components.Thumb
import com.anees.alldownloader.ui.components.TintButton
import com.anees.alldownloader.ui.components.fmtBytes
import com.anees.alldownloader.ui.components.fmtDuration
import com.anees.alldownloader.ui.components.pressable
import com.anees.alldownloader.ui.components.rememberHaptics
import com.anees.alldownloader.ui.components.rowPress
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles

class HomeActions(
    val onUrl: (String) -> Unit,
    val onPaste: (String) -> Unit,
    val onFetch: () -> Unit,
    val onClear: () -> Unit,
    val onQuality: (Quality) -> Unit,
    val onDownload: () -> Unit,
    val onToggle: (String) -> Unit,
    val onSelectAll: (Boolean) -> Unit,
    val onSingle: () -> Unit,
    val onClip: (Boolean, Int, Int) -> Unit,
    val onBatch: () -> Unit,
    val onUseClip: () -> Unit,
    val onDismissClip: () -> Unit,
)

@Composable
fun HomeScreen(state: HomeState, engine: EngineState, clipOffer: String?, a: HomeActions, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val h = rememberHaptics()
    val info = state.info
    LargeTitleList("Grab", modifier.imePadding(), actions = { EnginePill(engine) }) {
        item(key = "clip") {
            AnimatedVisibility(
                clipOffer != null && state.info == null && !state.loading,
                enter = expandVertically(Motion.default()) + fadeIn(Motion.default()),
                exit = shrinkVertically(Motion.default()) + fadeOut(Motion.default()),
            ) { ClipBanner(clipOffer.orEmpty(), { h.confirm(); a.onUseClip() }, a.onDismissClip) }
        }
        item(key = "input") {
            LinkInput(state, a.onUrl, { h.tap(); clipboard.getText()?.text?.let(a.onPaste) }, { focus.clearFocus(); a.onFetch() }, a.onClear)
        }
        if (state.batch.isNotEmpty()) item(key = "batch") { BatchCard(state.batch) { h.confirm(); a.onBatch() } }
        when {
            info?.isPlaylist == true -> {
                item(key = "pl-head") { PlaylistHeader(info, state, a) }
                itemsIndexed(info.entries, key = { _, e -> "e-" + e.id }) { i, e ->
                    EntryRow(e, e.id in state.selected, first = i == 0, last = i == info.entries.lastIndex) { h.tap(); a.onToggle(e.id) }
                }
                item(key = "pl-go") {
                    PillButton(
                        "Download ${state.selected.size} · ${state.quality.short}", { h.confirm(); a.onDownload() },
                        Modifier.fillMaxWidth().padding(16.dp), icon = Icons.Rounded.ArrowDownward, enabled = state.selected.isNotEmpty(),
                    )
                }
            }
            info != null -> item(key = "preview") { PreviewPanel(info, state, a) { h.confirm(); a.onDownload() } }
            state.loading -> item(key = "loading") { LoadingPanel() }
            state.batch.isEmpty() -> item(key = "tips") { Tips() }
        }
    }
}

val Quality.short get() = if (this == Quality.BEST) "Best" else label.substringBefore(" ·")

@Composable
private fun ClipBanner(url: String, onUse: () -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.c
    val host = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(c.accent.copy(alpha = 0.12f)).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ContentPaste, null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            Text("Link on clipboard", style = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label)
            Text(host, style = TextStyles.footnote, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TintButton("Grab", onUse, small = true)
        Box(Modifier.size(32.dp).clip(CircleShape).pressable(scale = 0.85f, onClick = onDismiss), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Dismiss", tint = c.secondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LinkInput(state: HomeState, onUrl: (String) -> Unit, onPaste: () -> Unit, onFetch: () -> Unit, onClear: () -> Unit) {
    val c = AppTheme.c
    val err = state.error != null
    val border by animateColorAsState(if (err) c.red else Color.Transparent, Motion.default(), label = "b")
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Link, null, tint = c.secondary, modifier = Modifier.size(20.dp))
            HGap(10.dp)
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (state.url.isEmpty()) Text("Paste a link — or several", style = TextStyles.body, color = c.secondary, maxLines = 1)
                BasicTextField(
                    value = state.url, onValueChange = onUrl, singleLine = state.batch.isEmpty(), maxLines = 4,
                    textStyle = TextStyles.body.copy(color = c.label),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onFetch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.url.isNotEmpty()) {
                Box(Modifier.size(36.dp).clip(CircleShape).pressable(scale = 0.85f, onClick = onClear), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(c.tertiary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Clear", tint = c.card, modifier = Modifier.size(13.dp))
                    }
                }
            } else TintButton("Paste", onPaste, icon = Icons.Rounded.ContentPaste, small = true, modifier = Modifier.padding(end = 4.dp))
        }
        AnimatedVisibility(err, enter = expandVertically(Motion.default()) + fadeIn(), exit = shrinkVertically(Motion.default()) + fadeOut()) {
            Row(Modifier.padding(start = 6.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = c.red, modifier = Modifier.size(17.dp).padding(top = 1.dp))
                HGap(6.dp)
                Text(state.error.orEmpty(), style = TextStyles.footnote, color = c.red)
            }
        }
        if (state.info == null && state.batch.isEmpty()) {
            Gap(12.dp)
            PillButton(
                if (state.loading) "Reading link…" else "Find Media", onFetch, Modifier.fillMaxWidth(),
                enabled = state.url.isNotBlank() && !state.loading, loading = state.loading,
            )
        }
        Gap(20.dp)
    }
}

@Composable
private fun BatchCard(links: List<String>, onGo: () -> Unit) {
    val c = AppTheme.c
    Section("${links.size} links found", footer = "Each one downloads at your default quality. Titles fill in as they start.") {
        links.take(6).forEachIndexed { i, u ->
            if (i > 0) Separator(52.dp)
            val host = runCatching { java.net.URI(u).host?.removePrefix("www.") }.getOrNull() ?: u
            Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = TextStyles.numeric, color = c.secondary, modifier = Modifier.padding(end = 14.dp).size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(host, style = TextStyles.subhead.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1)
                    Text(u, style = TextStyles.caption, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (links.size > 6) { Separator(52.dp); Text("+ ${links.size - 6} more", style = TextStyles.footnote, color = c.secondary, modifier = Modifier.padding(start = 52.dp, top = 10.dp, bottom = 10.dp)) }
    }
    PillButton("Download all ${links.size}", onGo, Modifier.fillMaxWidth().padding(16.dp), icon = Icons.Rounded.PlaylistAdd)
}

@Composable
private fun PlaylistHeader(info: MediaInfo, state: HomeState, a: HomeActions) {
    val c = AppTheme.c
    Column {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                // Stacked-cards hint that this is a collection.
                Box(Modifier.padding(start = 8.dp).size(104.dp, 58.dp).graphicsLayer { translationY = -6.dp.toPx(); alpha = 0.4f }.clip(RoundedCornerShape(10.dp)).background(c.tertiary))
                Thumb(info.thumbnail, fallback = Icons.Rounded.Movie, width = 120.dp, height = 68.dp, shape = RoundedCornerShape(10.dp))
            }
            HGap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(info.title, style = TextStyles.headline, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MetaLine(listOfNotNull("${info.entries.size} items", fmtDuration(info.durationSec), info.uploader))
            }
        }
        if (state.url.contains("v=")) TextAction("Only the video in this link", a.onSingle, Modifier.padding(start = 8.dp, top = 6.dp))
        Gap(16.dp)
        FormatPicker(state.quality, a.onQuality, Modifier.padding(horizontal = 16.dp), playlist = true, info = null)
        Gap(22.dp)
        val all = state.selected.size == info.entries.size
        Row(Modifier.padding(start = 32.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${state.selected.size} OF ${info.entries.size} SELECTED", style = TextStyles.footnote, color = c.secondary, modifier = Modifier.weight(1f))
            TextAction(if (all) "Deselect All" else "Select All", { a.onSelectAll(!all) })
        }
    }
}

@Composable
internal fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier) {
    val c = AppTheme.c
    val s by animateFloatAsState(if (checked) 1f else 0f, Motion.bouncy(), label = "chk")
    Box(modifier.size(24.dp).clip(CircleShape).border(1.5.dp, if (checked) Color.Transparent else c.tertiary, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EntryRow(e: PlaylistEntry, checked: Boolean, first: Boolean, last: Boolean, onToggle: () -> Unit) {
    val c = AppTheme.c
    val shape = RoundedCornerShape(topStart = if (first) 14.dp else 0.dp, topEnd = if (first) 14.dp else 0.dp, bottomStart = if (last) 14.dp else 0.dp, bottomEnd = if (last) 14.dp else 0.dp)
    Column(Modifier.padding(horizontal = 16.dp).clip(shape).background(c.card)) {
        Row(Modifier.fillMaxWidth().rowPress(onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(checked)
            HGap(12.dp)
            Thumb(e.thumb, fallback = Icons.Rounded.Movie, width = 80.dp, height = 45.dp, modifier = Modifier.alpha(if (checked) 1f else 0.5f))
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                Text(e.title, style = TextStyles.subhead, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (checked) c.label else c.secondary)
                if (e.durationSec > 0) Text(fmtDuration(e.durationSec), style = TextStyles.numeric.copy(fontSize = TextStyles.caption.fontSize), color = c.secondary)
            }
        }
        if (!last) Separator(50.dp)
    }
}

@Composable
private fun EnginePill(state: EngineState) {
    val c = AppTheme.c
    val (label, color) = when (state) {
        EngineState.Starting -> "Starting" to c.gray
        is EngineState.Updating -> "Updating" to c.orange
        is EngineState.Ready -> "Ready" to c.green
        is EngineState.Failed -> "Engine error" to c.red
    }
    val busy = state !is EngineState.Ready && state !is EngineState.Failed
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.padding(end = 8.dp).clip(CircleShape).background(c.fill).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = if (busy) pulse.value else 1f }.clip(CircleShape).background(color))
        HGap(6.dp)
        Text(label, style = TextStyles.caption.copy(fontWeight = FontWeight.Medium), color = c.secondary)
    }
}

@Composable
private fun LoadingPanel() {
    val c = AppTheme.c
    val a = rememberInfiniteTransition(label = "sk").animateFloat(0.5f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "s")
    Column(Modifier.padding(horizontal = 16.dp).graphicsLayer { alpha = a.value }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(18.dp)).background(c.fill))
        Gap(14.dp)
        Box(Modifier.fillMaxWidth(0.8f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(c.fill))
        Gap(8.dp)
        Box(Modifier.fillMaxWidth(0.45f).height(13.dp).clip(RoundedCornerShape(6.dp)).background(c.fill))
    }
}

@Composable
private fun PreviewPanel(info: MediaInfo, state: HomeState, a: HomeActions, onDownload: () -> Unit) {
    val c = AppTheme.c
    Column {
        // Media card: artwork rises in with a soft spring.
        var shown by rememberSaveable(info.url) { mutableIntStateOf(0) }
        val s by animateFloatAsState(if (shown == 1) 1f else 0.94f, Motion.default(), label = "in")
        androidx.compose.runtime.LaunchedEffect(info.url) { shown = 1 }
        Column(
            Modifier.padding(horizontal = 16.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = (s - 0.94f) / 0.06f }
                .clip(RoundedCornerShape(18.dp)).background(c.card),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(c.fill), contentAlignment = Alignment.Center) {
                Icon(if (info.isDirect) Icons.Rounded.InsertDriveFile else Icons.Rounded.Movie, null, tint = c.secondary, modifier = Modifier.size(36.dp))
                if (info.thumbnail != null) AsyncImage(info.thumbnail, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                if (info.durationSec > 0) Text(
                    fmtDuration(info.durationSec), style = TextStyles.numeric.copy(fontSize = TextStyles.caption.fontSize), color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(Modifier.padding(14.dp)) {
                Text(info.title, style = TextStyles.headline, color = c.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Gap(6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(if (info.isDirect) "FILE" else info.site.uppercase(), c.accent)
                    if (info.uploader != null) { HGap(8.dp); Text(info.uploader, style = TextStyles.footnote, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Gap(24.dp)
        if (!info.isDirect) {
            FormatPicker(state.quality, a.onQuality, Modifier.padding(horizontal = 16.dp), playlist = false, info = info)
            if (info.durationSec > 0) { Gap(24.dp); ClipSection(info.durationSec, state, a.onClip) }
        } else {
            Section(footer = "Direct file — fetched with parallel connections by aria2c.") {
                Cell("Download file", icon = Icons.Rounded.InsertDriveFile, iconColor = c.blue)
            }
        }
        Gap(20.dp)
        val clip = state.clipOn && state.clipEnd > state.clipStart && (state.clipStart > 0 || state.clipEnd < info.durationSec)
        PillButton(
            when {
                info.isDirect -> "Download File"
                clip -> "Download Clip · ${state.quality.short}"
                else -> "Download · ${state.quality.short}"
            },
            onDownload, Modifier.fillMaxWidth().padding(horizontal = 16.dp), icon = Icons.Rounded.ArrowDownward,
        )
    }
}

/** Video/Audio switch plus quality list with sizes and a checkmark, settings-style. */
@Composable
private fun FormatPicker(quality: Quality, onQuality: (Quality) -> Unit, modifier: Modifier, playlist: Boolean, info: MediaInfo?) {
    val c = AppTheme.c
    val h = rememberHaptics()
    var mode by rememberSaveable { mutableIntStateOf(if (quality.isAudio) 1 else 0) }
    Column(modifier) {
        Segmented(listOf("Video", "Audio"), mode, { mode = it; onQuality(if (it == 1) Quality.MP3 else Quality.BEST) }, Modifier.fillMaxWidth())
        Gap(12.dp)
        val options = when {
            mode == 1 -> Quality.audioOnly
            playlist -> listOf(Quality.BEST, Quality.P1080, Quality.P720, Quality.P480, Quality.P360)
            else -> Quality.video.filter { it.height == null || info!!.heights.contains(it.height) }
        }
        if (playlist) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { q ->
                    val on = q == quality
                    val bg by animateColorAsState(if (on) c.accent else c.card, Motion.default(), label = "q")
                    Text(
                        q.short, style = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (on) Color.White else c.label,
                        modifier = Modifier.clip(CircleShape).background(bg).pressable { h.tap(); onQuality(q) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (mode == 0) Text("Heights are a ceiling — items without it get the next best.", style = TextStyles.footnote, color = c.secondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        } else {
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(c.card)) {
                options.forEachIndexed { i, q ->
                    if (i > 0) Separator()
                    val size = when {
                        q.isAudio -> info?.audioSize
                        q.height != null -> info?.sizeByHeight?.get(q.height)
                        else -> info?.heights?.firstOrNull()?.let { info.sizeByHeight[it] }
                    }
                    val sub = when {
                        q == Quality.BEST -> info?.heights?.firstOrNull()?.let { "${it}p" } ?: "Highest available"
                        q.isAudio -> when (q) { Quality.MP3 -> "Plays everywhere"; Quality.M4A -> "AAC, smaller"; Quality.OPUS -> "Best quality per MB"; else -> "Lossless container" }
                        else -> null
                    }
                    Row(Modifier.fillMaxWidth().rowPress { h.tap(); onQuality(q) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (q == Quality.BEST) "Best" else q.label, style = TextStyles.body, color = c.label)
                            if (sub != null) Text(sub, style = TextStyles.footnote, color = c.secondary)
                        }
                        if (size != null) Text("≈ ${fmtBytes(size)}", style = TextStyles.numeric, color = c.secondary)
                        HGap(12.dp)
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(q == quality, enter = scaleIn(Motion.bouncy()) + fadeIn(), exit = fadeOut(tween(100))) {
                                Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipSection(duration: Int, state: HomeState, onClip: (Boolean, Int, Int) -> Unit) {
    val c = AppTheme.c
    val end = if (state.clipEnd <= 0) duration else state.clipEnd
    Section(footer = if (state.clipOn) "Cuts snap to the nearest keyframe, so edges can shift by a second or two." else null) {
        Cell("Download a part", icon = Icons.Rounded.ContentCut, iconColor = c.pink,
            subtitle = if (state.clipOn) "${fmtDuration(state.clipStart).ifEmpty { "0:00" }} – ${fmtDuration(end)} · ${fmtDuration(end - state.clipStart).ifEmpty { "0:00" }} long" else null) {
            AppleSwitch(state.clipOn, { onClip(it, state.clipStart, end) })
        }
        AnimatedVisibility(state.clipOn, enter = expandVertically(Motion.default()) + fadeIn(), exit = shrinkVertically(Motion.default()) + fadeOut()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                RangeSlider(
                    value = state.clipStart.toFloat()..end.toFloat(),
                    onValueChange = { r -> onClip(true, r.start.toInt(), r.endInclusive.toInt()) },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.fill),
                )
                Row {
                    Text(fmtDuration(state.clipStart).ifEmpty { "0:00" }, style = TextStyles.numeric, color = c.secondary, modifier = Modifier.weight(1f))
                    Text(fmtDuration(end), style = TextStyles.numeric, color = c.secondary)
                }
            }
        }
    }
}

@Composable
private fun Tips() {
    val c = AppTheme.c
    Column {
        Section("Ways to grab", footer = "Powered by yt-dlp · 1,000+ sites · Saved to Download/AllDownloader") {
            Cell("Share from any app", subtitle = "Tap Share → All Downloader. A quick sheet opens right there.", icon = Icons.Rounded.IosShare, iconColor = c.blue)
            Separator(59.dp)
            Cell("Just copy a link", subtitle = "Open the app and it offers the copied link.", icon = Icons.Rounded.ContentPaste, iconColor = c.orange)
            Separator(59.dp)
            Cell("Paste many at once", subtitle = "Drop a block of links to queue them together.", icon = Icons.Rounded.PlaylistAdd, iconColor = c.indigo)
            Separator(59.dp)
            Cell("Keep only a part", subtitle = "Trim to a clip before it downloads.", icon = Icons.Rounded.ContentCut, iconColor = c.pink)
            Separator(59.dp)
            Cell("Music & podcasts", subtitle = "MP3, M4A, Opus or FLAC with cover art.", icon = Icons.Rounded.MusicNote, iconColor = c.green)
        }
    }
}

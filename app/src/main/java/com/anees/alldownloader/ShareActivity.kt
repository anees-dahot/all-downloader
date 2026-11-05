package com.anees.alldownloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anees.alldownloader.data.MediaInfo
import com.anees.alldownloader.data.Quality
import com.anees.alldownloader.data.Settings
import com.anees.alldownloader.data.ShareAction
import com.anees.alldownloader.ui.AppViewModel
import com.anees.alldownloader.ui.HomeState
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.MetaLine
import com.anees.alldownloader.ui.components.Motion
import com.anees.alldownloader.ui.components.PillButton
import com.anees.alldownloader.ui.components.Spinner
import com.anees.alldownloader.ui.components.TextAction
import com.anees.alldownloader.ui.components.Thumb
import com.anees.alldownloader.ui.components.fmtBytes
import com.anees.alldownloader.ui.components.fmtDuration
import com.anees.alldownloader.ui.components.pressable
import com.anees.alldownloader.ui.components.rememberHaptics
import com.anees.alldownloader.ui.screens.short
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles
import kotlinx.coroutines.launch

/**
 * Opened from the system share sheet / text selection menu. Shows a compact sheet over the
 * app the user is in, so a link can be grabbed without leaving it.
 */
class ShareActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (text.isNullOrBlank()) { finish(); return }

        if (savedInstanceState == null) {
            val links = AppViewModel.URL_RE.findAll(text).map { it.value.trimEnd('.', ',', ')', ']') }.distinct().toList()
            if (links.size == 1 && Settings.current.shareAction == ShareAction.INSTANT) {
                vm.quickStart(links[0], Settings.current.defaultQuality)
                Toast.makeText(this, "Downloading · ${Settings.current.defaultQuality.short}", Toast.LENGTH_SHORT).show()
                finish(); return
            }
            vm.onShared(text)
        }

        setContent {
            val s by vm.settings.collectAsStateWithLifecycle()
            AppTheme(s.theme, s.dynamicColor, s.accent) { Sheet() }
        }
    }

    private fun openApp(url: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_URL, url)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Sheet() {
        val c = AppTheme.c
        val h = rememberHaptics()
        val state by vm.home.collectAsStateWithLifecycle()
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(this@ShareActivity, it, Toast.LENGTH_SHORT).show() } }
        fun close(after: () -> Unit = {}) = scope.launch { sheet.hide() }.invokeOnCompletion { after(); finish() }

        ModalBottomSheet(
            onDismissRequest = { finish() }, sheetState = sheet,
            containerColor = c.card, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = { Box(Modifier.padding(top = 8.dp).size(36.dp, 5.dp).clip(CircleShape).background(c.tertiary)) },
        ) {
            Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grab", style = TextStyles.title3, color = c.label, modifier = Modifier.weight(1f))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(c.fill).pressable(scale = 0.85f) { close() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Close", tint = c.secondary, modifier = Modifier.size(17.dp))
                    }
                }
                Gap(14.dp)
                AnimatedContent(
                    when {
                        state.batch.isNotEmpty() -> 4
                        state.info != null -> 3
                        state.error != null -> 2
                        else -> 1
                    },
                    transitionSpec = { fadeIn(Motion.default()) togetherWith fadeOut(Motion.snappy()) },
                    label = "sheet",
                ) { step ->
                    when (step) {
                        1 -> Loading(state.url)
                        2 -> Failure(state.error.orEmpty()) { openApp(state.url) }
                        4 -> Column {
                            Text("${state.batch.size} links", style = TextStyles.headline, color = c.label)
                            Text("Each downloads at ${Settings.current.defaultQuality.short}.", style = TextStyles.subhead, color = c.secondary)
                            Gap(18.dp)
                            PillButton("Download All ${state.batch.size}", { h.confirm(); vm.enqueueBatch(); close() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.ArrowDownward)
                        }
                        else -> state.info?.let { Ready(it, state, h::confirm) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Loading(url: String) {
        val c = AppTheme.c
        Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(10.dp)).background(c.fill), contentAlignment = Alignment.Center) { Spinner() }
            HGap(14.dp)
            Column {
                Text("Reading link…", style = TextStyles.headline, color = c.label)
                Text(url, style = TextStyles.footnote, color = c.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun Failure(msg: String, onOpen: () -> Unit) {
        val c = AppTheme.c
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = c.red, modifier = Modifier.size(20.dp))
                HGap(8.dp)
                Text(msg, style = TextStyles.subhead, color = c.label)
            }
            Gap(18.dp)
            PillButton("Open in App", onOpen, Modifier.fillMaxWidth(), container = c.fill, content = c.accent)
        }
    }

    @Composable
    private fun Ready(info: MediaInfo, state: HomeState, confirm: () -> Unit) {
        val c = AppTheme.c
        val h = rememberHaptics()
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(info.thumbnail, fallback = if (info.isDirect) Icons.Rounded.InsertDriveFile else Icons.Rounded.Movie, width = 120.dp, height = 68.dp, shape = RoundedCornerShape(10.dp))
                HGap(14.dp)
                Column(Modifier.weight(1f)) {
                    Text(info.title, style = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Gap(3.dp)
                    MetaLine(listOfNotNull(
                        if (info.isPlaylist) "${info.entries.size} items" else null,
                        if (info.isDirect) "File" else info.site, fmtDuration(info.durationSec), info.uploader,
                    ), style = TextStyles.caption)
                }
            }
            Gap(18.dp)
            if (!info.isDirect) {
                val opts = if (info.isPlaylist) listOf(Quality.BEST, Quality.P1080, Quality.P720, Quality.MP3, Quality.M4A)
                else Quality.video.filter { it.height == null || info.heights.contains(it.height) }.take(5) + listOf(Quality.MP3, Quality.M4A)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    opts.forEach { q ->
                        val on = q == state.quality
                        val bg by animateColorAsState(if (on) c.accent else c.fill, Motion.default(), label = "q")
                        val size = when { q.isAudio -> info.audioSize; q.height != null -> info.sizeByHeight[q.height]; else -> null }
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(bg).pressable { h.tap(); vm.setQuality(q) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (q.isAudio) { Icon(Icons.Rounded.MusicNote, null, tint = if (on) Color.White else c.label, modifier = Modifier.size(13.dp)); HGap(3.dp) }
                                Text(q.short, style = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (on) Color.White else c.label)
                            }
                            if (size != null) Text(fmtBytes(size), style = TextStyles.caption, color = if (on) Color.White.copy(alpha = 0.8f) else c.secondary)
                        }
                    }
                }
                Gap(18.dp)
            }
            PillButton(
                if (info.isPlaylist) "Download ${info.entries.size} · ${state.quality.short}" else if (info.isDirect) "Download File" else "Download · ${state.quality.short}",
                { confirm(); vm.enqueue(); Toast.makeText(this@ShareActivity, "Added to queue", Toast.LENGTH_SHORT).show(); finish() },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.ArrowDownward,
            )
            Gap(6.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction(if (info.isPlaylist) "Choose Items in App" else "More Options in App", { openApp(state.url) })
            }
        }
    }
}

package com.anees.alldownloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anees.alldownloader.BuildConfig
import com.anees.alldownloader.data.AppSettings
import com.anees.alldownloader.data.EngineChannel
import com.anees.alldownloader.data.EngineState
import com.anees.alldownloader.data.Quality
import com.anees.alldownloader.data.Storage
import com.anees.alldownloader.data.ThemeMode
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.Hairline
import com.anees.alldownloader.ui.components.ScreenHeader
import com.anees.alldownloader.ui.components.SectionLabel
import com.anees.alldownloader.ui.components.SegmentRow
import com.anees.alldownloader.update.UpdateState

@Composable
fun SettingsScreen(
    s: AppSettings,
    engine: EngineState,
    update: UpdateState,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onUpdateEngine: () -> Unit,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        ScreenHeader("Settings")

        SectionLabel("Appearance")
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            SegmentRow(ThemeMode.entries.map { it.label }, s.theme.ordinal, { i -> onChange { it.copy(theme = ThemeMode.entries[i]) } }, Modifier.fillMaxWidth())
        }
        if (Build.VERSION.SDK_INT >= 31) Toggle("Wallpaper colours", "Use Material You colours instead of the app's palette", s.dynamicColor) { v -> onChange { it.copy(dynamicColor = v) } }
        Gap(12.dp)

        SectionLabel("Downloads")
        Choice("Default quality", s.defaultQuality.label, Quality.entries.map { it.label }) { i -> onChange { it.copy(defaultQuality = Quality.entries[i]) } }
        Hairline(Modifier.padding(start = 20.dp))
        Info("Save location", Storage.folderLabel)
        Hairline(Modifier.padding(start = 20.dp))
        Toggle("Wi-Fi only", "Wait for an unmetered network before downloading", s.wifiOnly) { v -> onChange { it.copy(wifiOnly = v) } }
        Hairline(Modifier.padding(start = 20.dp))
        Toggle("Embed metadata", "Title, artist and chapters written into the file", s.embedMetadata) { v -> onChange { it.copy(embedMetadata = v) } }
        Hairline(Modifier.padding(start = 20.dp))
        Toggle("Embed thumbnail", "Cover art inside MP3/MP4 files", s.embedThumbnail) { v -> onChange { it.copy(embedThumbnail = v) } }
        Hairline(Modifier.padding(start = 20.dp))
        Toggle("Subtitles", "Embed English + your language subtitles when available", s.subtitles) { v -> onChange { it.copy(subtitles = v) } }
        Hairline(Modifier.padding(start = 20.dp))
        Toggle("Skip sponsor segments", "Cut sponsor, self-promo and subscribe reminders (YouTube, via SponsorBlock)", s.sponsorBlock) { v -> onChange { it.copy(sponsorBlock = v) } }
        Hairline(Modifier.padding(start = 20.dp))
        Choice("Speed booster", if (s.speedBoost <= 1) "Off" else "${s.speedBoost} connections", listOf("Off", "4 connections", "8 connections", "16 connections")) { i ->
            onChange { it.copy(speedBoost = listOf(1, 4, 8, 16)[i]) }
        }
        Hairline(Modifier.padding(start = 20.dp))
        Choice("Simultaneous downloads", "${s.parallel} at a time", listOf("1 at a time", "2 at a time", "3 at a time", "4 at a time")) { i ->
            onChange { it.copy(parallel = i + 1) }
        }
        Hairline(Modifier.padding(start = 20.dp))
        Toggle("Auto-retry", "Retry failed downloads up to 3 times and resume where they stopped", s.autoRetry) { v -> onChange { it.copy(autoRetry = v) } }
        Hairline(Modifier.padding(start = 20.dp))
        Toggle("Use aria2c for direct files", "Split direct file links across the booster's connections", s.aria2) { v -> onChange { it.copy(aria2 = v) } }
        Gap(12.dp)

        SectionLabel("Engine")
        val ver = when (engine) { is EngineState.Ready -> engine.version; is EngineState.Updating -> engine.version; else -> null }
        Info("yt-dlp version", ver ?: if (engine is EngineState.Failed) "failed to start" else "starting…")
        Hairline(Modifier.padding(start = 20.dp))
        Choice("Update channel", s.engineChannel.label, EngineChannel.entries.map { it.label }) { i -> onChange { it.copy(engineChannel = EngineChannel.entries[i]) } }
        Hairline(Modifier.padding(start = 20.dp))
        Toggle("Update engine on launch", "Sites change often; fresh extractors avoid most errors", s.autoUpdateEngine) { v -> onChange { it.copy(autoUpdateEngine = v) } }
        Hairline(Modifier.padding(start = 20.dp))
        Action("Update engine now", if (engine is EngineState.Updating) "Updating…" else "Fetch the latest yt-dlp", busy = engine is EngineState.Updating, onClick = onUpdateEngine)
        Gap(12.dp)

        SectionLabel("App")
        Toggle("Check for app updates", "Look for new versions on GitHub at most twice a day", s.autoCheckApp) { v -> onChange { it.copy(autoCheckApp = v) } }
        Hairline(Modifier.padding(start = 20.dp))
        Action(
            "Check for updates now",
            when (update) {
                UpdateState.Checking -> "Checking…"
                UpdateState.UpToDate -> "You're on the latest version"
                is UpdateState.Failed -> update.message
                is UpdateState.Available -> "Version ${update.release.version} available"
                is UpdateState.Downloading -> "Downloading ${(update.progress * 100).toInt()}%"
                is UpdateState.Ready -> "Ready to install"
                UpdateState.Idle -> "Installed: ${BuildConfig.VERSION_NAME}"
            },
            busy = update is UpdateState.Checking,
            onClick = onCheckUpdate,
        )
        Hairline(Modifier.padding(start = 20.dp))
        Action("Source code & releases", "github.com/${BuildConfig.GITHUB_REPO}") {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}"))) }
        }
        Hairline(Modifier.padding(start = 20.dp))
        Info("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.firstOrNull()}")
        Gap(20.dp)
        Text(
            "Only download content you have the right to. Respect creators and each site's terms.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun Row2(title: String, sub: String?, onClick: (() -> Unit)?, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { HGap(16.dp); trailing() }
    }
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) =
    Row2(title, sub, { onChange(!value) }) {
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
    }

@Composable
private fun Info(title: String, value: String) = Row2(title, value, null)

@Composable
private fun Action(title: String, sub: String, busy: Boolean = false, onClick: () -> Unit) =
    Row2(title, sub, if (busy) null else onClick) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }

@Composable
private fun Choice(title: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row2(title, null, { open = true }) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(open, { open = false }, Modifier.align(Alignment.CenterEnd)) {
            options.forEachIndexed { i, o -> DropdownMenuItem({ Text(o) }, { open = false; onPick(i) }) }
        }
    }
}

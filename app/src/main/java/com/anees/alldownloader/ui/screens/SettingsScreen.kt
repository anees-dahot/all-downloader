package com.anees.alldownloader.ui.screens

import androidx.compose.material.icons.rounded.UnfoldMore
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Login
import com.anees.alldownloader.ui.components.fmtBytes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anees.alldownloader.BuildConfig
import com.anees.alldownloader.data.Accent
import com.anees.alldownloader.data.AppSettings
import com.anees.alldownloader.data.EngineChannel
import com.anees.alldownloader.data.EngineState
import com.anees.alldownloader.data.Quality
import com.anees.alldownloader.data.ShareAction
import com.anees.alldownloader.data.Storage
import com.anees.alldownloader.data.ThemeMode
import com.anees.alldownloader.ui.components.AppleSwitch
import com.anees.alldownloader.ui.components.Cell
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.LargeTitleList
import com.anees.alldownloader.ui.components.Motion
import com.anees.alldownloader.ui.components.Section
import com.anees.alldownloader.ui.components.Segmented
import com.anees.alldownloader.ui.components.Separator
import com.anees.alldownloader.ui.components.Spinner
import com.anees.alldownloader.ui.components.pressable
import com.anees.alldownloader.ui.components.rememberHaptics
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles
import com.anees.alldownloader.update.UpdateState

private val SEP = 59.dp

@Composable
fun SettingsScreen(
    s: AppSettings,
    engine: EngineState,
    update: UpdateState,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onUpdateEngine: () -> Unit,
    onCheckUpdate: () -> Unit,
    cacheBytes: () -> Long,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.c
    val ctx = LocalContext.current
    LargeTitleList("Settings", modifier) {
        item(key = "look") {
            Section("Appearance") {
                Column(Modifier.padding(16.dp)) {
                    Segmented(ThemeMode.entries.map { it.label }, s.theme.ordinal, { i -> onChange { it.copy(theme = ThemeMode.entries[i]) } }, Modifier.fillMaxWidth())
                }
                Separator()
                AccentRow(s.accent, enabled = !s.dynamicColor) { a -> onChange { it.copy(accent = a) } }
                if (Build.VERSION.SDK_INT >= 31) {
                    Separator(SEP)
                    Toggle("Wallpaper Colour", Icons.Rounded.Palette, c.indigo, s.dynamicColor) { v -> onChange { it.copy(dynamicColor = v) } }
                }
                Separator(SEP)
                Toggle("Haptics", Icons.Rounded.Vibration, c.pink, s.haptics) { v -> onChange { it.copy(haptics = v) } }
            }
            Gap(28.dp)
        }
        item(key = "grab") {
            Section("Grabbing", footer = "The quick sheet lets you pick quality without leaving the app you shared from.") {
                Choice("When Shared", Icons.Rounded.IosShare, c.blue, s.shareAction.label, ShareAction.entries.map { it.label }) { i -> onChange { it.copy(shareAction = ShareAction.entries[i]) } }
                Separator(SEP)
                Toggle("Detect Copied Links", Icons.Rounded.ContentPaste, c.orange, s.clipboardDetect) { v -> onChange { it.copy(clipboardDetect = v) } }
                Separator(SEP)
                Choice("Default Quality", Icons.Rounded.HighQuality, c.accent, s.defaultQuality.short, Quality.entries.map { it.label }) { i -> onChange { it.copy(defaultQuality = Quality.entries[i]) } }
            }
            Gap(28.dp)
        }
        item(key = "dl") {
            Section("Downloads") {
                Cell("Save Location", icon = Icons.Rounded.Folder, iconColor = c.blue, value = Storage.folderLabel.substringAfterLast('/'))
                Separator(SEP)
                Toggle("Wi-Fi Only", Icons.Rounded.Wifi, c.blue, s.wifiOnly) { v -> onChange { it.copy(wifiOnly = v) } }
                Separator(SEP)
                Choice("Speed Booster", Icons.Rounded.Bolt, c.orange, if (s.speedBoost <= 1) "Off" else "${s.speedBoost}×", listOf("Off", "4 connections", "8 connections", "16 connections")) { i ->
                    onChange { it.copy(speedBoost = listOf(1, 4, 8, 16)[i]) }
                }
                Separator(SEP)
                Choice("At Once", Icons.Rounded.Layers, c.teal, "${s.parallel}", listOf("1 at a time", "2 at a time", "3 at a time", "4 at a time")) { i -> onChange { it.copy(parallel = i + 1) } }
                Separator(SEP)
                Toggle("Auto-Retry", Icons.Rounded.Replay, c.green, s.autoRetry) { v -> onChange { it.copy(autoRetry = v) } }
                Separator(SEP)
                // Re-measured whenever the tab is shown or after clearing.
                var cache by remember { mutableStateOf<Long?>(null) }
                var tick by remember { mutableStateOf(0) }
                LaunchedEffect(tick) { if (tick > 0) kotlinx.coroutines.delay(500); cache = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { cacheBytes() } }
                Cell(
                    "Clear Partial Files", icon = Icons.Rounded.Delete, iconColor = c.red,
                    value = cache?.let { if (it == 0L) "Empty" else fmtBytes(it) },
                    onClick = if ((cache ?: 0L) > 0L) ({ onClearCache(); tick++ }) else null,
                )
                Separator(SEP)
                Toggle("aria2c for Files", Icons.Rounded.Memory, c.gray, s.aria2) { v -> onChange { it.copy(aria2 = v) } }
            }
            Gap(28.dp)
        }
        item(key = "files") {
            Section("Files", footer = "SponsorBlock cuts sponsor reads, self-promo and subscribe reminders on YouTube.") {
                Toggle("Embed Metadata", Icons.Rounded.Sell, c.indigo, s.embedMetadata) { v -> onChange { it.copy(embedMetadata = v) } }
                Separator(SEP)
                Toggle("Embed Cover Art", Icons.Rounded.Image, c.pink, s.embedThumbnail) { v -> onChange { it.copy(embedThumbnail = v) } }
                Separator(SEP)
                Toggle("Subtitles", Icons.Rounded.Subtitles, c.gray, s.subtitles) { v -> onChange { it.copy(subtitles = v) } }
                Separator(SEP)
                Toggle("Skip Sponsors", Icons.Rounded.ContentCut, c.red, s.sponsorBlock) { v -> onChange { it.copy(sponsorBlock = v) } }
            }
            Gap(28.dp)
        }
        item(key = "accounts") {
            val sites by com.anees.alldownloader.data.Cookies.sites.collectAsStateWithLifecycle()
            var confirmForget by remember { mutableStateOf(false) }
            Section("Accounts", footer = "Sign in to a site so age-restricted, members-only or \"confirm you're not a bot\" videos download. Cookies stay on this phone.") {
                Cell(
                    "Site Sign-In", icon = Icons.AutoMirrored.Rounded.Login, iconColor = c.green, chevron = true,
                    value = when (sites.size) { 0 -> "None"; 1 -> sites[0]; else -> "${sites.size} sites" },
                    onClick = { ctx.startActivity(Intent(ctx, com.anees.alldownloader.SignInActivity::class.java)) },
                )
                if (sites.isNotEmpty()) {
                    Separator(SEP)
                    Cell("Forget All Sign-Ins", icon = Icons.AutoMirrored.Rounded.Logout, iconColor = c.red, titleColor = c.red, onClick = { confirmForget = true })
                }
            }
            if (confirmForget) androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmForget = false }, containerColor = c.card,
                shape = RoundedCornerShape(22.dp),
                title = { Text("Forget All Sign-Ins?", style = TextStyles.headline) },
                text = { Text("Removes saved cookies for ${sites.joinToString()}. You can sign in again any time.", style = TextStyles.subhead) },
                confirmButton = { androidx.compose.material3.TextButton({ com.anees.alldownloader.data.Cookies.clearAll(); confirmForget = false }) { Text("Forget", color = c.red) } },
                dismissButton = { androidx.compose.material3.TextButton({ confirmForget = false }) { Text("Cancel", color = c.accent) } },
            )
            Gap(28.dp)
        }
        item(key = "engine") {
            Section("Engine", footer = "Sites change often. A fresh engine fixes most errors.") {
                val ver = when (engine) { is EngineState.Ready -> engine.version; is EngineState.Updating -> engine.version; else -> null }
                Cell("yt-dlp", icon = Icons.Rounded.Code, iconColor = c.gray, value = ver ?: if (engine is EngineState.Failed) "Failed" else "Starting…")
                Separator(SEP)
                Choice("Channel", Icons.Rounded.Layers, c.indigo, s.engineChannel.label, EngineChannel.entries.map { it.label }) { i -> onChange { it.copy(engineChannel = EngineChannel.entries[i]) } }
                Separator(SEP)
                Toggle("Update on Launch", Icons.Rounded.Refresh, c.green, s.autoUpdateEngine) { v -> onChange { it.copy(autoUpdateEngine = v) } }
                Separator(SEP)
                val busy = engine is EngineState.Updating
                Cell("Update Engine Now", icon = Icons.Rounded.SystemUpdate, iconColor = c.accent, titleColor = c.accent, onClick = if (busy) null else onUpdateEngine) {
                    if (busy) Spinner(size = 18.dp)
                }
            }
            Gap(28.dp)
        }
        item(key = "app") {
            Section("About", footer = "Only download content you have the right to. Respect creators and each site's terms.") {
                Toggle("Check for Updates", Icons.Rounded.SystemUpdate, c.blue, s.autoCheckApp) { v -> onChange { it.copy(autoCheckApp = v) } }
                Separator(SEP)
                val status = when (update) {
                    UpdateState.Checking -> "Checking…"
                    UpdateState.UpToDate -> "Up to date"
                    is UpdateState.Failed -> "Failed"
                    is UpdateState.Available -> "${update.release.version} available"
                    is UpdateState.Downloading -> "${(update.progress * 100).toInt()}%"
                    is UpdateState.Ready -> "Ready"
                    UpdateState.Idle -> null
                }
                Cell("Check Now", icon = Icons.Rounded.Refresh, iconColor = c.green, value = status, onClick = if (update is UpdateState.Checking) null else onCheckUpdate)
                Separator(SEP)
                Cell("Source Code", icon = Icons.Rounded.Code, iconColor = Color(0xFF24292F), chevron = true, onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}"))) }
                })
                Separator(SEP)
                Cell("Version", icon = Icons.Rounded.Info, iconColor = c.gray, value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
        }
    }
}

@Composable
private fun Toggle(title: String, icon: ImageVector, color: Color, value: Boolean, onChange: (Boolean) -> Unit) =
    Cell(title, icon = icon, iconColor = color) { AppleSwitch(value, onChange) }

@Composable
private fun Choice(title: String, icon: ImageVector, color: Color, value: String, options: List<String>, onPick: (Int) -> Unit) {
    val c = AppTheme.c
    val h = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    Box {
        Cell(title, icon = icon, iconColor = color, value = value, onClick = { open = true }) {
            Icon(androidx.compose.material.icons.Icons.Rounded.UnfoldMore, null, tint = c.tertiary, modifier = Modifier.size(18.dp))
        }
        Box(Modifier.align(Alignment.CenterEnd)) {
            DropdownMenu(open, { open = false }, shape = RoundedCornerShape(14.dp), containerColor = c.card) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem({ Text(o, style = TextStyles.body) }, { open = false; h.tap(); onPick(i) })
                }
            }
        }
    }
}

@Composable
private fun AccentRow(current: Accent, enabled: Boolean, onPick: (Accent) -> Unit) {
    val c = AppTheme.c
    val h = rememberHaptics()
    Column(Modifier.padding(16.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }) {
        Text("Accent", style = TextStyles.body, color = c.label)
        Gap(12.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Accent.entries.forEach { a ->
                val on = a == current
                val ring by animateFloatAsState(if (on) 1f else 0f, Motion.bouncy(), label = "ring")
                val col = Color(if (c.dark) a.dark else a.light)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).border((2.5f * ring).dp, col, CircleShape)
                        .pressable(enabled = enabled, scale = 0.85f) { h.tap(); onPick(a) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(34.dp - (8f * ring).dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                        if (on) Icon(Icons.Rounded.Check, a.label, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

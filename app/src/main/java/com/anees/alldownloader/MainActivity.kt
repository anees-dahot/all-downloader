package com.anees.alldownloader

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anees.alldownloader.data.Settings
import com.anees.alldownloader.data.Status
import com.anees.alldownloader.ui.AppViewModel
import com.anees.alldownloader.ui.UpdateSheet
import com.anees.alldownloader.ui.screens.DownloadsScreen
import com.anees.alldownloader.ui.screens.HomeScreen
import com.anees.alldownloader.ui.screens.LibraryScreen
import com.anees.alldownloader.ui.screens.SettingsScreen
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.data.HistoryStore

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    private var tab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestPermissions()
        if (savedInstanceState == null) handleIntent(intent) else tab = savedInstanceState.getInt("tab")

        setContent {
            val s by vm.settings.collectAsStateWithLifecycle()
            AppTheme(s.theme, s.dynamicColor) { Root() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); outState.putInt("tab", tab)
    }

    private fun requestPermissions() {
        val need = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) perms.launch(need.toTypedArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.onShared(it); tab = 0 }
        } else if (intent.hasExtra(EXTRA_TAB)) {
            tab = intent.getIntExtra(EXTRA_TAB, 0)
        }
    }

    private data class Dest(val label: String, val icon: ImageVector, val iconOn: ImageVector)
    private val dests = listOf(
        Dest("Grab", Icons.Rounded.Link, Icons.Rounded.Link),
        Dest("Downloads", Icons.Rounded.ArrowDownward, Icons.Rounded.ArrowDownward),
        Dest("Library", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
        Dest("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
    )

    @Composable
    private fun Root() {
        val home by vm.home.collectAsStateWithLifecycle()
        val active by vm.active.collectAsStateWithLifecycle()
        val library by vm.library.collectAsStateWithLifecycle()
        val filter by vm.filter.collectAsStateWithLifecycle()
        val missing by vm.missing.collectAsStateWithLifecycle()
        val engine by vm.engine.collectAsStateWithLifecycle()
        val update by vm.update.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val all by HistoryStore.items.collectAsStateWithLifecycle()
        val doneCount = all.count { it.status == Status.DONE }
        val running = active.count { it.rec.status.isActive }
        val failed = active.count { it.rec.status == Status.FAILED }

        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

        val wide = LocalConfiguration.current.screenWidthDp >= 600
        val badge: @Composable (Int) -> Unit = { i ->
            if (i == 1 && (running > 0 || failed > 0)) Badge(containerColor = if (running > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) {
                Text("${if (running > 0) running else failed}")
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    dests.forEachIndexed { i, d ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { BadgedBox(badge = { badge(i) }) { Icon(if (tab == i) d.iconOn else d.icon, null) } },
                            label = { Text(d.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            },
            contentWindowInsets = if (wide) WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End) else WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ) { pad ->
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)),
                    ) {
                        dests.forEachIndexed { i, d ->
                            NavigationRailItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { BadgedBox(badge = { badge(i) }) { Icon(if (tab == i) d.iconOn else d.icon, null) } },
                                label = { Text(d.label) },
                                colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Box(Modifier.weight(1f).padding(pad)) {
                    AnimatedContent(tab, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) }, label = "tab") { t ->
                        val m = Modifier.fillMaxSize()
                        when (t) {
                            0 -> HomeScreen(
                                home, engine, vm::setUrl, { vm.onShared(it, autoFetch = true) }, vm::fetch, vm::clearHome,
                                vm::setQuality, { vm.enqueue(); tab = 1 }, vm::toggleEntry, vm::selectAll, vm::fetchSingle, m,
                            )
                            1 -> DownloadsScreen(
                                active, settings.wifiOnly,
                                { vm.cancel(it.rec) }, { vm.retry(it.rec) }, { vm.dismiss(it.rec) }, vm::clearFailed, { tab = 0 }, m,
                            )
                            2 -> LibraryScreen(
                                library, doneCount, filter, missing, vm::setQuery, vm::setKind, vm::open, vm::share,
                                { vm.redownload(it); tab = 1 }, { r, del -> vm.removeFromLibrary(r, del) }, { vm.refreshMissing() },
                                { vm.toast("Link copied") }, m,
                            )
                            else -> SettingsScreen(settings, engine, update, Settings::update, { vm.updateEngine() }, { vm.checkAppUpdate() }, m)
                        }
                    }
                }
            }
        }

        UpdateSheet(
            update,
            onDownload = vm::downloadUpdate, onCancel = vm::cancelUpdateDownload,
            onInstall = vm::install, onSkip = vm::skipUpdate, onDismiss = vm::dismissUpdate,
        )
    }

    companion object { const val EXTRA_TAB = "tab" }
}

package com.anees.alldownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anees.alldownloader.data.HistoryStore
import com.anees.alldownloader.data.Settings
import com.anees.alldownloader.data.Status
import com.anees.alldownloader.ui.AppViewModel
import com.anees.alldownloader.ui.UpdateSheet
import com.anees.alldownloader.ui.components.FloatingTabBar
import com.anees.alldownloader.ui.components.Motion
import com.anees.alldownloader.ui.components.TabItem
import com.anees.alldownloader.ui.screens.DownloadsScreen
import com.anees.alldownloader.ui.screens.HomeActions
import com.anees.alldownloader.ui.screens.HomeScreen
import com.anees.alldownloader.ui.screens.LibraryActions
import com.anees.alldownloader.ui.screens.LibraryScreen
import com.anees.alldownloader.ui.screens.QueueActions
import com.anees.alldownloader.ui.screens.SettingsScreen
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    private var tab by mutableIntStateOf(0)
    private var lastClipStamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestPermissions()
        if (savedInstanceState == null) handleIntent(intent) else tab = savedInstanceState.getInt("tab")

        setContent {
            val s by vm.settings.collectAsStateWithLifecycle()
            AppTheme(s.theme, s.dynamicColor, s.accent) {
                // Bar icons follow the app's theme, not the system's.
                val dark = AppTheme.c.dark
                LaunchedEffect(dark) {
                    val t = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                        if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                    )
                }
                Root()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); outState.putInt("tab", tab)
    }

    /** Android 10+ only lets the focused app read the clipboard. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !Settings.current.clipboardDetect) return
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val desc = cm.primaryClipDescription ?: return
        if (!desc.hasMimeType("text/*")) return
        // Only read new clips: each read shows Android's "pasted" toast.
        val stamp = if (Build.VERSION.SDK_INT >= 26) desc.timestamp else 0L
        if (stamp != 0L && stamp == lastClipStamp) return
        lastClipStamp = stamp
        vm.onClipboard(runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull())
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
        when {
            intent.action == Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.onShared(it); tab = 0 }
            intent.hasExtra(EXTRA_URL) -> { vm.onShared(intent.getStringExtra(EXTRA_URL)!!); tab = 0 }
            intent.hasExtra(EXTRA_RETRY) -> {
                val id = intent.getStringExtra(EXTRA_RETRY)!!
                vm.retryById(id); tab = 1
                // The failure notification used id.hashCode() + 1 (see DownloadWorker.notifyDone).
                getSystemService(android.app.NotificationManager::class.java)?.cancel(id.hashCode() + 1)
            }
            intent.hasExtra(EXTRA_TAB) -> tab = intent.getIntExtra(EXTRA_TAB, 0)
        }
    }

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
        val clip by vm.clipOffer.collectAsStateWithLifecycle()
        val all by HistoryStore.items.collectAsStateWithLifecycle()
        val done = remember(all) { all.filter { it.status == Status.DONE } }
        val running = active.count { it.rec.status.isActive }
        val failed = active.count { it.rec.status == Status.FAILED }
        val c = AppTheme.c

        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
        val haze = rememberHazeState()

        val tabs = listOf(
            TabItem("Grab", Icons.Rounded.Link),
            TabItem("Queue", Icons.Rounded.ArrowCircleDown, badge = if (running > 0) running else failed, badgeColor = if (running > 0) c.accent else c.red),
            TabItem("Library", Icons.Rounded.PhotoLibrary),
            TabItem("Settings", Icons.Rounded.Settings),
        )

        Box(Modifier.fillMaxSize().background(c.groupedBg)) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                // Tabs slide a touch in the direction of travel; enter and exit share the same path.
                AnimatedContent(
                    tab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(Motion.default()) { it / 14 * dir } + fadeIn(Motion.default())) togetherWith
                            (slideOutHorizontally(Motion.default()) { -it / 14 * dir } + fadeOut(Motion.snappy()))
                    },
                    label = "tab",
                ) { t ->
                    val m = Modifier.fillMaxSize()
                    when (t) {
                        0 -> HomeScreen(
                            home, engine, clip,
                            HomeActions(
                                vm::setUrl, { vm.onShared(it, autoFetch = true) }, { vm.fetch() }, vm::clearHome, vm::setQuality,
                                { vm.enqueue(); tab = 1 }, vm::toggleEntry, vm::selectAll, vm::fetchSingle, vm::setClip,
                                { vm.enqueueBatch(); tab = 1 }, vm::useClip, vm::dismissClip,
                            ), m,
                        )
                        1 -> DownloadsScreen(
                            active, settings.wifiOnly,
                            QueueActions(
                                { vm.cancel(it.rec) }, { vm.pause(it.rec) }, { vm.resume(it.rec) }, { vm.retry(it.rec) }, { vm.dismiss(it.rec) },
                                vm::clearFailed, vm::pauseAll, vm::resumeAll, { tab = 0 },
                            ), m,
                        )
                        2 -> LibraryScreen(
                            library, done, filter, missing,
                            LibraryActions(
                                vm::setQuery, vm::setKind, vm::setSort, vm::open, vm::shareMany,
                                { vm.redownload(it); tab = 1 }, { r, del -> vm.removeMany(r, del) }, { vm.refreshMissing() }, { vm.toast("Link copied") },
                                vm::removeMissing, vm::openSource,
                            ), m,
                        )
                        else -> SettingsScreen(settings, engine, update, Settings::update, { vm.updateEngine() }, { vm.checkAppUpdate() },
                            vm::cacheBytes, { vm.clearCache() }, m)
                    }
                }
            }

            FloatingTabBar(tabs, tab, { tab = it }, haze, Modifier.align(Alignment.BottomCenter))

            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 84.dp, start = 16.dp, end = 16.dp)) { d ->
                Snackbar(shape = RoundedCornerShape(16.dp), containerColor = if (c.dark) androidx.compose.ui.graphics.Color(0xFF3A3A3C) else androidx.compose.ui.graphics.Color(0xFF1C1C1E),
                    contentColor = androidx.compose.ui.graphics.Color.White) {
                    Text(d.visuals.message, style = TextStyles.subhead)
                }
            }
        }

        UpdateSheet(
            update,
            onDownload = vm::downloadUpdate, onCancel = vm::cancelUpdateDownload,
            onInstall = vm::install, onSkip = vm::skipUpdate, onDismiss = vm::dismissUpdate,
        )
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_URL = "url"
        const val EXTRA_RETRY = "retry"
    }
}

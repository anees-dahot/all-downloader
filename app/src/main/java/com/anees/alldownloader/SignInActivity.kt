package com.anees.alldownloader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anees.alldownloader.data.Cookies
import com.anees.alldownloader.data.Settings
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.ProgressBar
import com.anees.alldownloader.ui.components.TextAction
import com.anees.alldownloader.ui.components.pressable
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles

/**
 * Small in-app browser: the user signs in to a site, taps Done, and its cookies are handed to
 * yt-dlp. The user types their own credentials into the site; the app never sees them.
 */
class SignInActivity : ComponentActivity() {

    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra(EXTRA_URL) ?: SITES.first().second
        setContent {
            val s by Settings.state.collectAsStateWithLifecycle()
            AppTheme(s.theme, s.dynamicColor, s.accent) { Screen(start) }
        }
    }

    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }

    private fun done() {
        val url = web?.url ?: return finish()
        val n = Cookies.capture(url)
        val site = Cookies.baseDomain(runCatching { java.net.URI(url).host }.getOrNull().orEmpty())
        Toast.makeText(this, if (n > 0) "Saved sign-in for $site" else "No sign-in found for $site yet", Toast.LENGTH_SHORT).show()
        if (n > 0) finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun Screen(start: String) {
        val c = AppTheme.c
        var url by remember { mutableStateOf(start) }
        var progress by remember { mutableStateOf(0f) }
        var canBack by remember { mutableStateOf(false) }
        BackHandler(canBack) { web?.goBack() }

        Column(Modifier.fillMaxSize().background(c.groupedBg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).pressable(scale = 0.85f) { finish() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Close", tint = c.secondary)
                }
                Row(
                    Modifier.weight(1f).clip(CircleShape).background(c.fill).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = c.secondary, modifier = Modifier.size(14.dp))
                    HGap(6.dp)
                    Text(runCatching { java.net.URI(url).host }.getOrNull() ?: url, style = TextStyles.subhead, color = c.label,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HGap(4.dp)
                TextAction("Done", { done() }, bold = true)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SITES.forEach { (name, link) ->
                    val on = Cookies.baseDomain(runCatching { java.net.URI(url).host }.getOrNull().orEmpty()) == Cookies.baseDomain(java.net.URI(link).host)
                    Text(
                        name, style = TextStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = if (on) androidx.compose.ui.graphics.Color.White else c.label,
                        modifier = Modifier.clip(CircleShape).background(if (on) c.accent else c.card)
                            .pressable { web?.loadUrl(link) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            if (progress in 0.01f..0.99f) ProgressBar(progress, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Text(
                "Sign in normally, then tap Done. Only cookies are kept — on this phone, for downloads.",
                style = TextStyles.caption, color = c.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Some sign-in pages refuse anything that says "wv" (embedded WebView).
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) { url = u; canBack = view.canGoBack() }
                            override fun onPageFinished(view: WebView, u: String) { url = u; canBack = view.canGoBack(); progress = 1f }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, p: Int) { progress = p / 100f }
                        }
                        loadUrl(start)
                        web = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        val SITES = listOf(
            "YouTube" to "https://m.youtube.com/",
            "Instagram" to "https://www.instagram.com/accounts/login/",
            "X" to "https://x.com/login",
            "Facebook" to "https://m.facebook.com/",
            "TikTok" to "https://www.tiktok.com/login",
            "Reddit" to "https://www.reddit.com/login/",
        )
    }
}

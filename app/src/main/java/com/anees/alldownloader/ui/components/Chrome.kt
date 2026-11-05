package com.anees.alldownloader.ui.components

import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Frosted material: content blurs underneath, tinted for legibility. */
@Composable
fun chromeStyle(): HazeStyle {
    val c = AppTheme.c
    return HazeStyle(
        backgroundColor = c.groupedBg,
        tint = HazeTint(c.chrome.copy(alpha = if (c.dark) 0.72f else 0.78f)),
        blurRadius = 24.dp,
        noiseFactor = 0f,
    )
}

/**
 * Large-title list. The title scrolls with content; once it passes under the bar, a small
 * centred title fades in and the bar turns into frosted glass with a hairline edge.
 */
@Composable
fun LargeTitleList(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    leading: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val c = AppTheme.c
    val haze = rememberHazeState()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 40.dp.toPx() }
    val collapse by remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
        }
    }
    val smallTitle by animateFloatAsState(if (collapse >= 1f) 1f else 0f, Motion.default(), label = "st")
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxSize().background(c.groupedBg)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = top + 44.dp, bottom = 120.dp),
        ) {
            item(key = "__title") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).graphicsLayer { alpha = 1f - smallTitle * 0.9f }) {
                    Text(title, style = TextStyles.largeTitle, color = c.label)
                    if (subtitle != null) Text(subtitle, style = TextStyles.subhead, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            content()
        }
        // Nav bar
        Box(
            Modifier.fillMaxWidth()
                .hazeEffect(haze, chromeStyle()) { alpha = collapse }
                .windowInsetsPadding(WindowInsets.statusBars).height(44.dp),
        ) {
            Row(Modifier.align(Alignment.CenterStart).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, content = leading)
            Text(
                title, style = TextStyles.headline, color = c.label,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = smallTitle; translationY = (1f - smallTitle) * 8.dp.toPx() },
            )
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(0.5.dp).graphicsLayer { alpha = collapse }.background(c.separator))
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector, val badge: Int = 0, val badgeColor: Color? = null)

/**
 * Floating frosted tab bar. The selection pill springs to the tapped tab;
 * tabs give instant press feedback.
 */
@Composable
fun FloatingTabBar(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, haze: HazeState, modifier: Modifier = Modifier) {
    val c = AppTheme.c
    val h = rememberHaptics()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxWidth().padding(bottom = bottom + 10.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().height(62.dp)
                .graphicsLayer {
                    shadowElevation = 18f; shape = CircleShape; clip = true
                    ambientShadowColor = Color.Black.copy(alpha = 0.25f); spotShadowColor = Color.Black.copy(alpha = 0.25f)
                }
                .hazeEffect(haze, chromeStyle())
                .padding(5.dp),
        ) {
            val w = maxWidth / tabs.size
            val x by animateDpAsState(w * selected, Motion.default(), label = "tab")
            Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(c.label.copy(alpha = if (c.dark) 0.12f else 0.07f)))
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, t ->
                    val on = i == selected
                    val tint = if (on) c.accent else c.label.copy(alpha = 0.75f)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().pressable(scale = 0.9f, dim = 0.8f, role = Role.Tab) {
                            if (!on) h.tap(); onSelect(i)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            Icon(t.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                            if (t.badge > 0) Text(
                                "${t.badge}", style = TextStyles.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-5).dp)
                                    .clip(CircleShape).background(t.badgeColor ?: c.red).padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                        Text(t.label, style = TextStyles.caption2.copy(fontSize = 10.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = tint)
                    }
                }
            }
        }
    }
}

/** Search field in the iOS style. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = AppTheme.c
    Row(
        modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(11.dp)).background(c.fill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = c.secondary, modifier = Modifier.size(18.dp))
        HGap(6.dp)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = TextStyles.body, color = c.secondary, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                value, onChange, singleLine = true, textStyle = TextStyles.body.copy(color = c.label),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(c.secondary.copy(alpha = 0.6f)).pressable { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Close, "Clear", tint = c.card, modifier = Modifier.size(12.dp))
            }
        }
    }
}

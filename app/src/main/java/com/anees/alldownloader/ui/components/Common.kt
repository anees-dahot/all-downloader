package com.anees.alldownloader.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.anees.alldownloader.data.Settings
import com.anees.alldownloader.ui.theme.AppTheme
import com.anees.alldownloader.ui.theme.TextStyles

// ---------------------------------------------------------------------------------------------
// Motion. Springs described the Apple way (damping ratio + response) and converted to stiffness:
// stiffness = (2π / response)². Critically damped by default; bounce only after momentum.
// ---------------------------------------------------------------------------------------------
object Motion {
    private fun k(response: Float) = (2 * Math.PI / response).let { (it * it).toFloat() }
    fun <T> default() = spring<T>(dampingRatio = 1f, stiffness = k(0.35f))
    fun <T> snappy() = spring<T>(dampingRatio = 1f, stiffness = k(0.25f))
    fun <T> bouncy() = spring<T>(dampingRatio = 0.8f, stiffness = k(0.4f))
    fun <T> press() = spring<T>(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
}

/** Haptic tick that honours the in-app setting. */
class Haptics(private val view: android.view.View) {
    private fun go(c: Int) { if (Settings.current.haptics) view.performHapticFeedback(c) }
    fun tap() = go(HapticFeedbackConstants.CLOCK_TICK)
    fun confirm() = go(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun reject() = go(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun toggle() = go(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
fun rememberHaptics(): Haptics { val v = LocalView.current; return remember(v) { Haptics(v) } }

/**
 * Tap target with instant press feedback (on touch-down): shrinks and dims under the finger,
 * springs back on release, and can be grabbed again mid-animation. No ripple.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    scale: Float = 0.97f,
    dim: Float = 0.75f,
    onLongClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scale else 1f, Motion.press(), label = "press")
    val a by animateFloatAsState(if (pressed && enabled) dim else 1f, Motion.press(), label = "dim")
    this
        .graphicsLayer { scaleX = s; scaleY = s; alpha = a }
        .combinedClickable(src, null, enabled = enabled, role = role, onLongClick = onLongClick, onClick = onClick)
}

/** Row highlight on press (table-cell style) instead of a scale. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rowPress(enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val c = AppTheme.c
    val bg by animateColorAsState(if (pressed && enabled) c.fill else Color.Transparent, Motion.press(), label = "row")
    this.background(bg).combinedClickable(src, null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)
}

// ---------------------------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------------------------

/** Inset grouped section: optional header + footer around a rounded card. */
@Composable
fun Section(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    headerTrailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.c
    Column(modifier.padding(horizontal = 16.dp)) {
        if (header != null || headerTrailing != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 7.dp), verticalAlignment = Alignment.Bottom) {
                Text(header.orEmpty().uppercase(), style = TextStyles.footnote, color = c.secondary, modifier = Modifier.weight(1f))
                headerTrailing?.invoke(this)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card), content = content)
        if (footer != null) {
            Text(footer, style = TextStyles.footnote, color = c.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** Separator inset from the leading edge, like table cells. */
@Composable
fun Separator(start: Dp = 16.dp) {
    Box(Modifier.padding(start = start).fillMaxWidth().height(0.5.dp).background(AppTheme.c.separator))
}

/** Rounded square glyph tile, as in iOS Settings. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 29.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/** Standard list cell. */
@Composable
fun Cell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = AppTheme.c.gray,
    value: String? = null,
    chevron: Boolean = false,
    titleColor: Color = AppTheme.c.label,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = AppTheme.c
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.rowPress(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp).padding(start = 16.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconTile(icon, iconColor); HGap(14.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.body, color = titleColor)
            if (subtitle != null) Text(subtitle, style = TextStyles.footnote, color = c.secondary)
        }
        if (value != null) { HGap(8.dp); Text(value, style = TextStyles.body, color = c.secondary, maxLines = 1) }
        if (trailing != null) { HGap(10.dp); trailing() }
        if (chevron) { HGap(4.dp); Icon(Icons.Rounded.ChevronRight, null, tint = c.tertiary, modifier = Modifier.size(22.dp)) }
    }
}

/** iOS-style switch: green track, white knob that springs across. */
@Composable
fun AppleSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = AppTheme.c
    val h = rememberHaptics()
    val track by animateColorAsState(if (checked) c.green else c.fill, Motion.default(), label = "track")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, Motion.bouncy(), label = "knob")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val stretch by animateDpAsState(if (pressed) 6.dp else 0.dp, Motion.press(), label = "stretch")
    Box(
        Modifier.size(51.dp, 31.dp).clip(CircleShape).background(track)
            .combinedClickable(src, null, enabled = enabled, role = Role.Switch) { h.toggle(); onChange(!checked) }
            .padding(2.dp),
    ) {
        // Knob widens while held, then slides — the "grow toward the finger" hint.
        Box(
            Modifier.offset(x = if (checked) x - stretch else x).size(27.dp + stretch, 27.dp)
                .graphicsLayer { shadowElevation = 4f; shape = CircleShape; clip = true }
                .background(Color.White)
        )
    }
}

/** Segmented control with a thumb that springs between options. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.c
    val h = rememberHaptics()
    BoxWithConstraints(modifier.height(34.dp).clip(RoundedCornerShape(9.dp)).background(c.fill).padding(2.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, Motion.default(), label = "seg")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .graphicsLayer { shadowElevation = if (c.dark) 0f else 3f; shape = RoundedCornerShape(7.dp); clip = true }
                .background(if (c.dark) Color(0xFF636366) else Color.White)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().pressable(scale = 0.95f, dim = 0.6f, role = Role.Tab) {
                        if (i != selected) { h.tap(); onSelect(i) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = TextStyles.footnote.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = c.label, maxLines = 1)
                }
            }
        }
    }
}

/** Filled capsule button (primary action). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = AppTheme.c.accent,
    content: Color = Color.White,
    height: Dp = 52.dp,
    loading: Boolean = false,
) {
    val c = AppTheme.c
    val bg by animateColorAsState(if (enabled) container else c.fill, Motion.default(), label = "pb")
    val fg = if (enabled) content else c.secondary
    Row(
        modifier.height(height).clip(CircleShape).background(bg).pressable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) { Spinner(fg, 18.dp); HGap(10.dp) }
        else if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp)); HGap(8.dp) }
        Text(text, style = TextStyles.headline, color = fg, maxLines = 1)
    }
}

/** Tinted capsule (secondary action). */
@Composable
fun TintButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = AppTheme.c.accent, small: Boolean = false) {
    Row(
        modifier.height(if (small) 30.dp else 40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f))
            .pressable(onClick = onClick).padding(horizontal = if (small) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(if (small) 15.dp else 17.dp)); HGap(6.dp) }
        Text(text, style = (if (small) TextStyles.footnote else TextStyles.subhead).copy(fontWeight = FontWeight.SemiBold), color = tint, maxLines = 1)
    }
}

/** Plain text button in the tint colour (nav-bar style). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppTheme.c.accent, bold: Boolean = false) {
    Text(
        text, style = TextStyles.body.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal), color = color,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).pressable(scale = 1f, dim = 0.4f, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Round icon button on a soft fill. */
@Composable
fun CircleIcon(icon: ImageVector, desc: String?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = AppTheme.c.accent, bg: Color = AppTheme.c.fill, size: Dp = 32.dp) {
    Box(modifier.size(size).clip(CircleShape).background(bg).pressable(scale = 0.9f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Spinner(color: Color = AppTheme.c.secondary, size: Dp = 20.dp) =
    androidx.compose.material3.CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = 2.dp, trackColor = Color.Transparent)

@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier, fallback: ImageVector, width: Dp = 96.dp, height: Dp = 54.dp, shape: Shape = RoundedCornerShape(8.dp)) {
    val c = AppTheme.c
    Box(modifier.size(width, height).clip(shape).background(c.fill), contentAlignment = Alignment.Center) {
        Icon(fallback, null, tint = c.secondary, modifier = Modifier.size(20.dp))
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Small rounded tag, e.g. "1080p". */
@Composable
fun Tag(text: String, color: Color = AppTheme.c.secondary, filled: Boolean = false) {
    Text(
        text, style = TextStyles.caption2, color = if (filled) Color.White else color, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val c = AppTheme.c
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.tertiary, modifier = Modifier.size(56.dp))
        Gap(14.dp)
        Text(title, style = TextStyles.title3, color = c.label, textAlign = TextAlign.Center)
        Gap(6.dp)
        Text(body, style = TextStyles.subhead, color = c.secondary, textAlign = TextAlign.Center)
        if (action != null) { Gap(20.dp); action() }
    }
}

@Composable
fun MetaLine(parts: List<String>, modifier: Modifier = Modifier, style: TextStyle = TextStyles.footnote) {
    Text(
        parts.filter { it.isNotBlank() }.joinToString(" · "), style = style.copy(fontFeatureSettings = "tnum"),
        color = AppTheme.c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

/** Thin rounded progress bar; indeterminate shows a sliding segment. */
@Composable
fun ProgressBar(progress: Float?, modifier: Modifier = Modifier, color: Color = AppTheme.c.accent) {
    val c = AppTheme.c
    if (progress == null) {
        androidx.compose.material3.LinearProgressIndicator(
            modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = color, trackColor = c.fill,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp,
        )
    } else {
        val p by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.default(), label = "pb")
        Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.fill)) {
            Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

val ContentPadding = PaddingValues(bottom = 120.dp)

fun fmtBytes(b: Long): String = when {
    b <= 0 -> ""
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.0f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}

fun fmtDuration(s: Int): String = when {
    s <= 0 -> ""
    s >= 3600 -> "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    else -> "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))
@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

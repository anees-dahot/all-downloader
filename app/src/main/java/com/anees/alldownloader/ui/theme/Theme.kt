package com.anees.alldownloader.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anees.alldownloader.data.Accent
import com.anees.alldownloader.data.ThemeMode

/**
 * Apple-style system palette: grouped background, raised cards, label hierarchy,
 * hairline separators and a single tint colour the user picks.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val accent: Color,
    val onAccent: Color,
    /** Grouped background behind cards. */
    val groupedBg: Color,
    /** Cards / rows. */
    val card: Color,
    /** Cards drawn on cards (search fields, segmented track). */
    val fill: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    /** Translucent chrome (tab bar / nav bar) tint. */
    val chrome: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val blue: Color,
    val indigo: Color,
    val pink: Color,
    val teal: Color,
    val gray: Color,
)

private fun lightPalette(accent: Color) = Palette(
    dark = false, accent = accent, onAccent = Color.White,
    groupedBg = Color(0xFFF2F2F7), card = Color.White, fill = Color(0xFFE9E9EE),
    label = Color(0xFF000000), secondary = Color(0xFF8A8A8E), tertiary = Color(0xFFC4C4C7),
    separator = Color(0xFFDCDCE0), chrome = Color(0xFFF9F9FB),
    green = Color(0xFF34C759), red = Color(0xFFFF3B30), orange = Color(0xFFFF9500), blue = Color(0xFF007AFF),
    indigo = Color(0xFF5856D6), pink = Color(0xFFFF2D55), teal = Color(0xFF30B0C7), gray = Color(0xFF8E8E93),
)

private fun darkPalette(accent: Color) = Palette(
    dark = true, accent = accent, onAccent = Color.White,
    groupedBg = Color(0xFF000000), card = Color(0xFF1C1C1E), fill = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF), secondary = Color(0xFF8D8D93), tertiary = Color(0xFF48484A),
    separator = Color(0xFF38383A), chrome = Color(0xFF161618),
    green = Color(0xFF30D158), red = Color(0xFFFF453A), orange = Color(0xFFFF9F0A), blue = Color(0xFF0A84FF),
    indigo = Color(0xFF5E5CE6), pink = Color(0xFFFF375F), teal = Color(0xFF40C8E0), gray = Color(0xFF8E8E93),
)

val LocalPalette = staticCompositionLocalOf { lightPalette(Color(0xFF007AFF)) }

object AppTheme {
    val c: Palette @Composable get() = LocalPalette.current
}

val Mono = FontFamily.Monospace

/**
 * Type ramp after the iOS text styles. Tracking tightens as size grows;
 * leading tightens on display sizes and opens up on body text.
 */
object TextStyles {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp)
    val subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.15).sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.05).sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp)
    /** Tabular numbers for progress, sizes and times so digits don't jitter. */
    val numeric = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
}

private val AppType = Typography(
    displaySmall = TextStyles.largeTitle,
    headlineSmall = TextStyles.title2,
    titleLarge = TextStyles.title3,
    titleMedium = TextStyles.headline,
    titleSmall = TextStyles.headline.copy(fontSize = 15.sp),
    bodyLarge = TextStyles.body,
    bodyMedium = TextStyles.subhead,
    bodySmall = TextStyles.footnote,
    labelLarge = TextStyles.subhead.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyles.footnote.copy(fontWeight = FontWeight.Medium),
    labelSmall = TextStyles.caption2,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(26.dp),
)

private fun Palette.toScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.16f).compositeOver(card), onPrimaryContainer = accent,
        secondary = label, onSecondary = groupedBg,
        secondaryContainer = fill, onSecondaryContainer = label,
        background = groupedBg, onBackground = label,
        surface = groupedBg, onSurface = label,
        surfaceVariant = fill, onSurfaceVariant = secondary,
        surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = if (dark) Color(0xFF2C2C2E) else Color.White,
        surfaceContainerHighest = fill,
        outline = tertiary, outlineVariant = separator,
        error = red, onError = Color.White,
        errorContainer = red.copy(alpha = 0.15f).compositeOver(card), onErrorContainer = red,
        inverseSurface = if (dark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E),
        inverseOnSurface = if (dark) Color.Black else Color.White,
        inversePrimary = accent,
    )
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

@Composable
fun AppTheme(mode: ThemeMode, dynamic: Boolean, accent: Accent, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val dyn = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)).primary
    } else null
    // Ease accent swaps instead of snapping.
    val tint by animateColorAsState(dyn ?: Color(if (dark) accent.dark else accent.light), tween(250), label = "accent")
    val palette = if (dark) darkPalette(tint) else lightPalette(tint)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.toScheme(), typography = AppType, shapes = AppShapes, content = content)
    }
}

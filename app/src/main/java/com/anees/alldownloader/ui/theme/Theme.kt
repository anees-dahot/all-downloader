package com.anees.alldownloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anees.alldownloader.data.ThemeMode

// Paper & ink, one signal colour. No gradients.
private val Vermillion = Color(0xFFE8501C)
private val VermillionDark = Color(0xFFFF6A33)

private val Light = lightColorScheme(
    primary = Vermillion, onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE3D9), onPrimaryContainer = Color(0xFF5A1A04),
    secondary = Color(0xFF16161A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E6DF), onSecondaryContainer = Color(0xFF16161A),
    background = Color(0xFFF4F2EE), onBackground = Color(0xFF16161A),
    surface = Color(0xFFF4F2EE), onSurface = Color(0xFF16161A),
    surfaceVariant = Color(0xFFE9E6DF), onSurfaceVariant = Color(0xFF6B6A66),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAF9F6),
    surfaceContainer = Color(0xFFEFECE6), surfaceContainerHigh = Color(0xFFE9E6DF),
    surfaceContainerHighest = Color(0xFFE2DFD8),
    outline = Color(0xFFCFCBC2), outlineVariant = Color(0xFFE2DFD8),
    error = Color(0xFFC62337), onError = Color.White,
    errorContainer = Color(0xFFFADBDF), onErrorContainer = Color(0xFF5C0812),
)

private val Dark = darkColorScheme(
    primary = VermillionDark, onPrimary = Color(0xFF1A0700),
    primaryContainer = Color(0xFF3A1A0D), onPrimaryContainer = Color(0xFFFFD2C0),
    secondary = Color(0xFFEDEBE6), onSecondary = Color(0xFF0E0E10),
    secondaryContainer = Color(0xFF25252A), onSecondaryContainer = Color(0xFFEDEBE6),
    background = Color(0xFF0E0E10), onBackground = Color(0xFFEDEBE6),
    surface = Color(0xFF0E0E10), onSurface = Color(0xFFEDEBE6),
    surfaceVariant = Color(0xFF1D1D21), onSurfaceVariant = Color(0xFF8E8D87),
    surfaceContainerLowest = Color(0xFF09090A), surfaceContainerLow = Color(0xFF141417),
    surfaceContainer = Color(0xFF17171A), surfaceContainerHigh = Color(0xFF1D1D21),
    surfaceContainerHighest = Color(0xFF25252A),
    outline = Color(0xFF3A3A40), outlineVariant = Color(0xFF26262B),
    error = Color(0xFFFF5C6E), onError = Color(0xFF2A0006),
    errorContainer = Color(0xFF3B0E15), onErrorContainer = Color(0xFFFFD9DD),
)

@Immutable
data class ExtraColors(val success: Color, val warn: Color)
val LocalExtra = staticCompositionLocalOf { ExtraColors(Color(0xFF2F9E62), Color(0xFFC98A12)) }

val Mono = FontFamily.Monospace

private val base = Typography()
private val AppType = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Mono, letterSpacing = 0.6.sp, fontSize = 11.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(20.dp),
)

/** Small uppercase mono label used as section eyebrow. */
val Eyebrow = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)

@Composable
fun AppTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val scheme: ColorScheme = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) Dark else Light
    val extra = if (dark) ExtraColors(Color(0xFF4CC38A), Color(0xFFE5A93A)) else ExtraColors(Color(0xFF2F9E62), Color(0xFFB27908))
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AppType, shapes = AppShapes, content = content)
    }
}

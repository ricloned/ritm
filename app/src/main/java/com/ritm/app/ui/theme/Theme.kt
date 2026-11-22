package com.ritm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.ritm.app.data.Store
import com.ritm.app.ui.UiState

/** Мягкие пастельные темы. Цвета считаются от одного оттенка (hue). */
data class AppTheme(val name: String, val emoji: String, val hue: Float, val sat: Float = 1f)

val THEMES = listOf(
    AppTheme("Лаванда", "💜", 256f),
    AppTheme("Сирень", "🪻", 285f),
    AppTheme("Роза", "🌸", 335f),
    AppTheme("Персик", "🍑", 18f, 0.9f),
    AppTheme("Мята", "🌿", 160f, 0.75f),
    AppTheme("Небо", "☁️", 205f, 0.85f),
    AppTheme("Индиго", "🌙", 232f),
    AppTheme("Графит", "●", 82f),
)

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

private val GraphiteLight = lightColorScheme(
    primary = Color(0xFF3B590E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EDCC),
    onPrimaryContainer = Color(0xFF23340C),
    background = Color(0xFFF8F8F3),
    onBackground = Color(0xFF20221D),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEFE6),
    onSurfaceVariant = Color(0xFF4C5047),
    secondary = Color(0xFF54613F),
    secondaryContainer = Color(0xFFE3EAD7),
    onSecondaryContainer = Color(0xFF202914),
    tertiary = Color(0xFF3C6255),
    tertiaryContainer = Color(0xFFDCECE5),
    onTertiaryContainer = Color(0xFF19382D),
    outline = Color(0xFF787D70),
    outlineVariant = Color(0xFFD9DDD1),
)

private val GraphiteDark = darkColorScheme(
    primary = Color(0xFFD7F885),
    onPrimary = Color(0xFF203000),
    primaryContainer = Color(0xFF2B381D),
    onPrimaryContainer = Color(0xFFE5F7C5),
    background = Color(0xFF141613),
    onBackground = Color(0xFFF0F2EA),
    surface = Color(0xFF1E211C),
    onSurface = Color(0xFFF0F2EA),
    surfaceVariant = Color(0xFF2A2E26),
    onSurfaceVariant = Color(0xFFBFC6B6),
    secondary = Color(0xFFBFCEA8),
    secondaryContainer = Color(0xFF343E2D),
    onSecondaryContainer = Color(0xFFE0EAD7),
    tertiary = Color(0xFFA8D0BF),
    tertiaryContainer = Color(0xFF2A4238),
    onTertiaryContainer = Color(0xFFDBEFE5),
    outline = Color(0xFF878E81),
    outlineVariant = Color(0xFF393F36),
)

fun previewColor(t: AppTheme): Color =
    if (t == THEMES.last()) GraphiteDark.primary else hsl(t.hue, 0.48f * t.sat, 0.6f)

fun lightSchemeFor(t: AppTheme): ColorScheme {
    if (t == THEMES.last()) return GraphiteLight
    val h = t.hue
    val k = t.sat
    val h2 = h + 35f
    return lightColorScheme(
        primary = hsl(h, 0.48f * k, 0.57f),
        onPrimary = Color.White,
        primaryContainer = hsl(h, 0.9f * k, 0.93f),
        onPrimaryContainer = hsl(h, 0.55f * k, 0.22f),
        secondary = hsl(h, 0.40f * k, 0.66f),
        onSecondary = Color.White,
        secondaryContainer = hsl(h, 0.8f * k, 0.95f),
        onSecondaryContainer = hsl(h, 0.40f * k, 0.25f),
        tertiary = hsl(h2, 0.40f * k, 0.60f),
        onTertiary = Color.White,
        tertiaryContainer = hsl(h2, 0.70f * k, 0.92f),
        onTertiaryContainer = hsl(h2, 0.45f * k, 0.25f),
        background = hsl(h, 0.6f * k, 0.975f),
        onBackground = hsl(h, 0.22f, 0.13f),
        surface = Color.White,
        onSurface = hsl(h, 0.22f, 0.13f),
        surfaceVariant = hsl(h, 0.45f * k, 0.94f),
        onSurfaceVariant = hsl(h, 0.12f, 0.45f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(h, 0.6f * k, 0.985f),
        surfaceContainer = hsl(h, 0.5f * k, 0.96f),
        surfaceContainerHigh = hsl(h, 0.45f * k, 0.94f),
        surfaceContainerHighest = hsl(h, 0.4f * k, 0.92f),
        outline = hsl(h, 0.25f * k, 0.75f),
        outlineVariant = hsl(h, 0.35f * k, 0.90f),
        error = Color(0xFFC2416B),
        onError = Color.White,
        errorContainer = Color(0xFFFFE1EA),
        onErrorContainer = Color(0xFF5C1027),
    )
}

fun darkSchemeFor(t: AppTheme): ColorScheme {
    if (t == THEMES.last()) return GraphiteDark
    val h = t.hue
    val k = t.sat
    val h2 = h + 35f
    return darkColorScheme(
        primary = hsl(h, 0.9f * k, 0.83f),
        onPrimary = hsl(h, 0.5f * k, 0.22f),
        primaryContainer = hsl(h, 0.38f * k, 0.38f),
        onPrimaryContainer = hsl(h, 0.9f * k, 0.93f),
        secondary = hsl(h, 0.6f * k, 0.85f),
        onSecondary = hsl(h, 0.35f * k, 0.25f),
        secondaryContainer = hsl(h, 0.28f * k, 0.27f),
        onSecondaryContainer = hsl(h, 0.8f * k, 0.92f),
        tertiary = hsl(h2, 0.6f * k, 0.82f),
        onTertiary = hsl(h2, 0.45f * k, 0.22f),
        tertiaryContainer = hsl(h2, 0.30f * k, 0.32f),
        onTertiaryContainer = hsl(h2, 0.8f * k, 0.93f),
        background = hsl(h, 0.22f * k, 0.10f),
        onBackground = hsl(h, 0.25f, 0.91f),
        surface = hsl(h, 0.18f * k, 0.14f),
        onSurface = hsl(h, 0.25f, 0.91f),
        surfaceVariant = hsl(h, 0.16f * k, 0.19f),
        onSurfaceVariant = hsl(h, 0.15f, 0.74f),
        surfaceContainerLowest = hsl(h, 0.22f * k, 0.08f),
        surfaceContainerLow = hsl(h, 0.18f * k, 0.12f),
        surfaceContainer = hsl(h, 0.18f * k, 0.15f),
        surfaceContainerHigh = hsl(h, 0.16f * k, 0.19f),
        surfaceContainerHighest = hsl(h, 0.15f * k, 0.23f),
        outline = hsl(h, 0.12f, 0.51f),
        outlineVariant = hsl(h, 0.14f * k, 0.25f),
        error = Color(0xFFFFB0C6),
        onError = Color(0xFF5C1027),
        errorContainer = Color(0xFF7A2941),
        onErrorContainer = Color(0xFFFFE1EA),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val BaseTypography = Typography()
private val RitmTypography = Typography(
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun RitmTheme(content: @Composable () -> Unit) {
    val theme = THEMES[Store.themeIndexFor(UiState.today).coerceIn(0, THEMES.size - 1)]
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkSchemeFor(theme) else lightSchemeFor(theme),
        shapes = AppShapes,
        typography = RitmTypography,
        content = content,
    )
}

package com.ritm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF7B61C9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9E1FF),
    onPrimaryContainer = Color(0xFF2B1A63),
    secondary = Color(0xFF9883D4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE9FF),
    onSecondaryContainer = Color(0xFF34295A),
    tertiary = Color(0xFFB879C2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8E4F7),
    onTertiaryContainer = Color(0xFF4A1E4E),
    background = Color(0xFFF8F5FD),
    onBackground = Color(0xFF1E1A29),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1A29),
    surfaceVariant = Color(0xFFEFEAF8),
    onSurfaceVariant = Color(0xFF6B6480),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF9FE),
    surfaceContainer = Color(0xFFF4F0FB),
    surfaceContainerHigh = Color(0xFFEFEAF8),
    surfaceContainerHighest = Color(0xFFE9E3F5),
    outline = Color(0xFFBDB3D6),
    outlineVariant = Color(0xFFE3DCF0),
    error = Color(0xFFC2416B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1EA),
    onErrorContainer = Color(0xFF5C1027),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9B8FF),
    onPrimary = Color(0xFF33206E),
    primaryContainer = Color(0xFF4A3A85),
    onPrimaryContainer = Color(0xFFEDE5FF),
    secondary = Color(0xFFCDBFF2),
    onSecondary = Color(0xFF352B57),
    secondaryContainer = Color(0xFF3A3156),
    onSecondaryContainer = Color(0xFFEAE2FF),
    tertiary = Color(0xFFE8B6EE),
    onTertiary = Color(0xFF4A1E4E),
    tertiaryContainer = Color(0xFF5C3463),
    onTertiaryContainer = Color(0xFFFBE4FD),
    background = Color(0xFF16131E),
    onBackground = Color(0xFFE8E2F2),
    surface = Color(0xFF201C2A),
    onSurface = Color(0xFFE8E2F2),
    surfaceVariant = Color(0xFF2C2738),
    onSurfaceVariant = Color(0xFFB5ACC9),
    surfaceContainerLowest = Color(0xFF110E18),
    surfaceContainerLow = Color(0xFF1C1825),
    surfaceContainer = Color(0xFF221E2D),
    surfaceContainerHigh = Color(0xFF2C2738),
    surfaceContainerHighest = Color(0xFF363042),
    outline = Color(0xFF7C7391),
    outlineVariant = Color(0xFF3B3548),
    error = Color(0xFFFFB0C6),
    onError = Color(0xFF5C1027),
    errorContainer = Color(0xFF7A2941),
    onErrorContainer = Color(0xFFFFE1EA),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RitmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = Typography(),
        content = content,
    )
}

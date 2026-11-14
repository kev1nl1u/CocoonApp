package com.cocoonapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val Dark = darkColorScheme(
    primary = Color(0xFF7FE3C0),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF12483A),
    onPrimaryContainer = Color(0xFFA6F5D8),
    secondary = Color(0xFFB4CCC1),
    onSecondary = Color(0xFF20352D),
    secondaryContainer = Color(0xFF263A33),
    onSecondaryContainer = Color(0xFFD0E8DC),
    tertiary = Color(0xFFFFB68A),
    onTertiary = Color(0xFF4F2500),
    tertiaryContainer = Color(0xFF5A3418),
    onTertiaryContainer = Color(0xFFFFDCC7),
    error = Color(0xFFFF8A6B),
    onError = Color(0xFF561F0C),
    background = Color(0xFF0E1113),
    onBackground = Color(0xFFE2E6E4),
    surface = Color(0xFF0E1113),
    onSurface = Color(0xFFE2E6E4),
    surfaceVariant = Color(0xFF252B2E),
    onSurfaceVariant = Color(0xFF9BA5A1),
    surfaceContainerLowest = Color(0xFF090B0D),
    surfaceContainerLow = Color(0xFF14181A),
    surfaceContainer = Color(0xFF181C1F),
    surfaceContainerHigh = Color(0xFF1E2326),
    surfaceContainerHighest = Color(0xFF1E2326),
    outline = Color(0xFF6B7672),
    outlineVariant = Color(0xFF2E3538),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0C6B52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDF2DD),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4B635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDEEE6),
    onSecondaryContainer = Color(0xFF082018),
    tertiary = Color(0xFF8F4E1F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC7),
    onTertiaryContainer = Color(0xFF331200),
    error = Color(0xFFB3401F),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF171C1A),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF171C1A),
    surfaceVariant = Color(0xFFDFE5E2),
    onSurfaceVariant = Color(0xFF5A6561),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F1),
    surfaceContainer = Color(0xFFEAEEEC),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF7A8581),
    outlineVariant = Color(0xFFD3DAD7),
)

@Composable
fun CocoonTheme(content: @Composable () -> Unit) {
    val base = Typography()
    // Tabular figures keep countdowns from jittering as digits change.
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodySmall = base.bodySmall.copy(fontFeatureSettings = "tnum"),
    )
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = typography,
        content = content,
    )
}

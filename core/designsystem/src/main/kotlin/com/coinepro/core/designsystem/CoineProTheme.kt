package com.coinepro.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CoineProDarkColorScheme = darkColorScheme(
    primary = CoineProColors.Lapis,
    onPrimary = CoineProColors.TextPrimary,
    background = CoineProColors.Stage,
    onBackground = CoineProColors.TextPrimary,
    surface = CoineProColors.Surface,
    onSurface = CoineProColors.TextPrimary,
    surfaceVariant = CoineProColors.SurfaceElevated,
    onSurfaceVariant = CoineProColors.TextSecondary,
    outline = CoineProColors.Border,
    error = CoineProColors.Sell,
)

@Composable
fun CoineProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoineProDarkColorScheme,
        shapes = CoineProShapes,
        content = content,
    )
}

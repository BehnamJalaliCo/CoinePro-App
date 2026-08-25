package com.coinepro.core.designsystem

import android.text.TextUtils
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Bridges a [CoineProPalette] into the Material colour scheme, so Material's own components pick
 * the same colours the product's components do.
 *
 * `primary` is the brand gold in both themes and `onPrimary` is near-black in both: the gold is a
 * mid-tone, so a near-white label on a filled gold button measures 2.0:1 and fails contrast, while
 * the dark label measures 9.0:1.
 */
private fun CoineProPalette.toColorScheme() = if (isDark) {
    darkColorScheme(
        primary = CoineProColors.Gold,
        onPrimary = onAccent,
        background = stage,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        outline = border,
        error = sell,
    )
} else {
    lightColorScheme(
        primary = CoineProColors.Gold,
        onPrimary = onAccent,
        background = stage,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        outline = border,
        error = sell,
    )
}

/**
 * @param darkTheme follows the system setting by default, so the app changes with the phone rather
 *   than needing its own switch. Pass it explicitly to pin a theme — screenshot renders do.
 */
@Composable
fun CoineProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Direction follows the language the app is running in, not the device locale. The two differ
    // whenever a reader keeps their phone in English but the app in Persian, and taking the device
    // value there would render Persian copy in a left-to-right layout.
    val configuration = LocalConfiguration.current
    val layoutDirection = remember(configuration) {
        val locale = configuration.locales[0]
        if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }
    val palette = if (darkTheme) CoineProDarkPalette else CoineProLightPalette

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalCoineProPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            shapes = CoineProShapes,
            typography = CoineProTypography,
            content = content,
        )
    }
}

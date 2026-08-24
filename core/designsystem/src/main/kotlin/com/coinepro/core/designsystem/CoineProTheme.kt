package com.coinepro.core.designsystem

import android.text.TextUtils
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val CoineProDarkColorScheme = darkColorScheme(
    primary = CoineProColors.Gold,
    // Dark on gold, not light: the brand gold is a mid-tone, so near-white label text on a filled
    // gold button measures 2.0:1 and fails contrast. Against the stage colour it measures 9.0:1.
    onPrimary = CoineProColors.Stage,
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

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = CoineProDarkColorScheme,
            shapes = CoineProShapes,
            typography = CoineProTypography,
            content = content,
        )
    }
}

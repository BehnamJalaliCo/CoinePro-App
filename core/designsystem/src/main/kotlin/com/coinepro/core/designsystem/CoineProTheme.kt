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
    /**
     * Whether a rise is drawn in the palette's green or its red.
     *
     * Swapped here, at the palette, rather than at any call site — which is the whole reason this
     * is one line of change instead of a hundred. Every direction colour in the product reads
     * `CoineProColors.Buy` or `CoineProColors.Sell`, and both resolve through
     * [LocalCoineProPalette]; exchanging the two fields exchanges the meaning everywhere at once,
     * including inside the chart's canvas, which never sees a composable colour at all.
     *
     * See `MarketColorScheme` in `core:datastore` for why this switch exists.
     */
    risingIsGreen: Boolean = true,
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
    val base = if (darkTheme) CoineProDarkPalette else CoineProLightPalette
    // The movement pair flips with the execution pair. A reader who has asked for red-up gets it
    // everywhere a price is drawn, not only on the two controls that commit an order.
    val palette = if (risingIsGreen) {
        base
    } else {
        base.copy(
            buy = base.sell,
            sell = base.buy,
            marketUp = base.marketDown,
            marketDown = base.marketUp,
        )
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalCoineProPalette provides palette,
        // Provided here so that every screen, preview and screenshot render inherits one answer to
        // "how much room is there". A screen that computed its own would disagree with the shell
        // the day the app runs in a window smaller than the display, and the two would then draw a
        // navigation rail and a bottom bar at the same time.
        LocalCoineProWindowClass provides configurationWindowClass(),
    ) {
        // Not `MaterialExpressiveTheme`: in the Material 3 this app builds against (1.4.0) the
        // expressive motion scheme and its opt-in annotation are still `internal`, so the springs
        // live in `CoineProMotionSpecs` and the navigation and sheets take them from there. Flip
        // this to the expressive theme when the library makes it public.
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            shapes = CoineProShapes,
            typography = CoineProTypography,
            content = content,
        )
    }
}

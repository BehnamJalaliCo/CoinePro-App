package com.coinepro.core.designsystem

import android.text.TextUtils
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
 *
 * **Every slot is set (DIALOGS-07).** Material 3 reads its menus, tooltips, sheets and dialogs from
 * the `surfaceContainer*` family, its segmented buttons and navigation indicators from
 * `secondaryContainer`, and tints anything with tonal elevation by `surfaceTint`. A slot left out
 * falls back to the baseline scheme — a lavender `#F3EDF7` menu on a white page, a `#211F26` one on
 * the navy — so a slot left out is a purple object waiting for a component to reach for it.
 */
private fun CoineProPalette.toColorScheme(): ColorScheme {
    // Soft washes of the two hues Material fills containers with, laid over the card so they are
    // solid values: a container colour with alpha would change with whatever it sits on.
    val goldWash = accentFill.copy(alpha = CONTAINER_WASH).compositeOver(surface)
    val blueWash = analysis.copy(alpha = CONTAINER_WASH).compositeOver(surface)
    val redWash = sell.copy(alpha = CONTAINER_WASH).compositeOver(surface)
    return if (isDark) {
        darkColorScheme(
            primary = CoineProColors.Gold,
            onPrimary = onAccent,
            primaryContainer = goldWash,
            onPrimaryContainer = accent,
            inversePrimary = accent,
            // Selection that is a view rather than an action — a segmented button, a navigation
            // indicator — takes the raised neutral the app already uses for «this one is in force».
            secondary = textSecondary,
            onSecondary = stage,
            secondaryContainer = surfaceRaised,
            onSecondaryContainer = textPrimary,
            tertiary = analysis,
            onTertiary = Color.White,
            tertiaryContainer = blueWash,
            onTertiaryContainer = textPrimary,
            background = stage,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textSecondary,
            // No tonal tint: the ladder already says which rung a surface is on.
            surfaceTint = Color.Transparent,
            // A tooltip or a snackbar: a lifted grey plate, as TradingView's dark tooltips are,
            // rather than Material's white slab on a dark chart.
            inverseSurface = surfacePressed,
            inverseOnSurface = textPrimary,
            error = sell,
            onError = Color.White,
            errorContainer = redWash,
            onErrorContainer = sell,
            outline = border,
            outlineVariant = border,
            scrim = Color.Black,
            // The container ladder, darkest to lightest. A menu is `surfaceContainer`: the elevated
            // rung, one clear step off the page and off a dialog's card, with Material's shadow.
            surfaceDim = stage,
            surfaceBright = surfaceRaised,
            surfaceContainerLowest = stage,
            surfaceContainerLow = surface,
            surfaceContainer = surfaceElevated,
            surfaceContainerHigh = surfaceOverlay,
            surfaceContainerHighest = surfaceRaised,
        )
    } else {
        lightColorScheme(
            primary = CoineProColors.Gold,
            onPrimary = onAccent,
            primaryContainer = goldWash,
            onPrimaryContainer = accent,
            inversePrimary = CoineProColors.Gold,
            secondary = textSecondary,
            onSecondary = surface,
            secondaryContainer = surfaceRaised,
            onSecondaryContainer = textPrimary,
            tertiary = analysis,
            onTertiary = Color.White,
            tertiaryContainer = blueWash,
            onTertiaryContainer = textPrimary,
            background = stage,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = textPrimary,
            inverseOnSurface = surface,
            error = sell,
            onError = Color.White,
            errorContainer = redWash,
            onErrorContainer = sell,
            outline = border,
            outlineVariant = border,
            scrim = Color.Black,
            // Light climbs *down* into grey. A menu is white, like TradingView's, and lifted by its
            // shadow; the higher containers are the pale rungs a card's tiles sit on.
            surfaceDim = surfaceOverlay,
            surfaceBright = surface,
            surfaceContainerLowest = surface,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceElevated,
            surfaceContainerHighest = surfaceRaised,
        )
    }
}

/** How strongly a Material container is washed with its hue: pastel, not a fill. */
private const val CONTAINER_WASH = 0.16f

/**
 * The state layer a pointer draws over a row it rests on (LISTS-17).
 *
 * Material's hover is eight per cent of the ink. On the light page that is `#E5E6E8` over `#F7F8FA`,
 * which reads; on the navy it is `#1D2023` over `#0B0E11`, a ΔL* of five, and a desktop list gave no
 * sign of where the pointer was. Fourteen per cent lands near TradingView's own dark hover
 * (`#2E2E2E` on black) without turning a pressed row into a flash.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun rippleFor(dark: Boolean): RippleConfiguration = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = if (dark) 0.14f else 0.10f,
        hoveredAlpha = if (dark) 0.14f else 0.08f,
        pressedAlpha = if (dark) 0.14f else 0.10f,
    ),
)

/**
 * @param darkTheme follows the system setting by default, so the app changes with the phone rather
 *   than needing its own switch. Pass it explicitly to pin a theme — screenshot renders do.
 */
@Composable
fun CoineProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Whether the dark palette is drawn on true black — «نیمه‌شب» (run Ω2).
     *
     * Ignored when [darkTheme] is false, because there is no light theme on black and a flag that
     * silently did nothing would be worse than one that says so. See [CoineProMidnightPalette].
     */
    midnight: Boolean = false,
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
    /**
     * The window, when the caller has a real one. The app passes
     * `CoineProWindowClass.of(currentWindowDpSize())` — the window's own metrics, which differ from
     * the configuration's in split screen and free-form. Null reads the configuration, which is
     * what a preview and a screenshot render have.
     */
    windowClass: CoineProWindowClass? = null,
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
    val base = when {
        darkTheme && midnight -> CoineProMidnightPalette
        darkTheme -> CoineProDarkPalette
        else -> CoineProLightPalette
    }
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
        LocalCoineProWindowClass provides (windowClass ?: configurationWindowClass()),
    ) {
        // Not `MaterialExpressiveTheme`: in the Material 3 this app builds against (1.4.0) the
        // expressive motion scheme and its opt-in annotation are still `internal`, so the springs
        // live in `CoineProMotionSpecs` and the navigation and sheets take them from there. Flip
        // this to the expressive theme when the library makes it public.
        MaterialTheme(
            colorScheme = remember(palette) { palette.toColorScheme() },
            shapes = CoineProShapes,
            typography = CoineProTypography,
        ) {
            @OptIn(ExperimentalMaterial3Api::class)
            CompositionLocalProvider(
                LocalRippleConfiguration provides remember(palette.isDark) { rippleFor(palette.isDark) },
                content = content,
            )
        }
    }
}

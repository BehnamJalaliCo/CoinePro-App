package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Which colour a screen's primary action, selected chip and selected border take.
 *
 * One button component, four identities. The alternative — a variant parameter on every control —
 * means every call site has to know which domain it is in, and the one that forgets is the one
 * that ships a gold "execute" button on an analysis screen.
 *
 * The rule this enforces is the one worth stating plainly: **a domain colour is never decorative.**
 * Blue on a chart screen does not mean "we liked blue here", it means "this is analysis".
 *
 * There are three, not four. The web terminal has a fourth — a premium gold, `#D4AF37`, distinct
 * from its brand yellow `#F0B90B` — and that distinction does not survive here, because this app's
 * brand gold *is* `#D8A848`, which is the same metal. Shipping two golds a reader cannot tell
 * apart, under a rule claiming they mean different things, would be a rule with no teeth. Premium
 * is marked by treatment instead: `CoineProColors.Premium` still exists for a subscription card's
 * tint and its label, and the accent under it stays [BRAND].
 */
enum class PageAccent {
    /** Markets, chart, AI, search — anything that reads the market rather than acting on it. */
    ANALYSIS,

    /** Trade, orders, execution, subscription — the app acting on the reader's account. */
    BRAND,

    /** Copy trading and anything social. */
    SOCIAL,
}

/**
 * Defaults to [PageAccent.BRAND], which is what a screen that has not declared a domain gets.
 *
 * Dynamic rather than static: a navigation change swaps this while the tree around it stays, so
 * readers of it do need to recompose.
 */
val LocalPageAccent = compositionLocalOf { PageAccent.BRAND }

/** Sets the accent for everything inside. One call per navigation destination. */
@Composable
fun ProvidePageAccent(accent: PageAccent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPageAccent provides accent, content = content)
}

/**
 * The current accent as a **fill** — a button, a selected chip.
 *
 * Distinct from [pageAccentInk] and it has to be: gold as a fill is the brand mid-tone in both
 * themes, while gold as ink is darkened in the light theme so it can be read on white.
 */
val CoineProColors.pageAccent: Color
    @Composable @ReadOnlyComposable get() = accentColour(LocalPageAccent.current, fill = true)

/** The current accent as **ink** — a label, an icon, a value on a neutral surface. */
val CoineProColors.pageAccentInk: Color
    @Composable @ReadOnlyComposable get() = accentColour(LocalPageAccent.current, fill = false)

/** The ink that reads on a fill of [pageAccent]. */
val CoineProColors.onPageAccent: Color
    @Composable @ReadOnlyComposable get() = when (LocalPageAccent.current) {
        // White on blue and on green; near-black on gold, which is a mid-tone and fails contrast
        // under white in either theme.
        PageAccent.ANALYSIS, PageAccent.SOCIAL -> Color.White
        PageAccent.BRAND -> LocalCoineProPalette.current.onAccent
    }

@Composable
@ReadOnlyComposable
private fun accentColour(accent: PageAccent, fill: Boolean): Color {
    val palette = LocalCoineProPalette.current
    return when (accent) {
        // Blue and green are already dark enough to carry white text in the light theme, so they
        // need no fill/ink split. Only gold does.
        PageAccent.ANALYSIS -> palette.analysis
        PageAccent.SOCIAL -> palette.social
        PageAccent.BRAND -> if (fill) palette.accentFill else palette.accent
    }
}

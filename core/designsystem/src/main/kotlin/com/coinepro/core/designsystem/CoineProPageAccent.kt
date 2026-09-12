package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Which colour a screen's primary action, selected chip and selected border take.
 *
 * One button component, one accent, and that is the run Ω answer to a question this file used to
 * answer the other way.
 *
 * ### Why the domain colours went (run Ω2)
 *
 * Until 4.75.0 this resolved three hues: blue for analysis, green for social, gold for the brand.
 * The reasoning was that a domain colour is never decorative — blue on a chart screen means «this
 * is analysis». That reasoning was sound about *buttons* and wrong about *charts*. On a surface
 * where green means «the price went up» and red means «it went down», a green «follow» button and a
 * blue «add indicator» chip are two more colours competing with the only two that carry a fact, and
 * a reader scanning for the one thing they can act on has four candidates instead of one.
 *
 * So: **green and red belong to the market, and one warm gold belongs to action.** A screen still
 * declares its domain — the enum is unchanged and every call site still reads truthfully — but
 * [ANALYSIS] and [SOCIAL] now resolve to the same gold [BRAND] does. `CoineProColors.Analysis` and
 * `CoineProColors.Social` are still real colours and still used where a *hue* is the content: an
 * indicator's line on the chart, an avatar's ring, a badge. What they no longer do is paint a
 * control.
 *
 * The one survivor is [DESTRUCTIVE], and it survives on its own rule: it is not a domain and not
 * decoration, it means «this cannot be undone». A gold "delete my account" button would be the one
 * place in the app where the accent that means «press me» sits on the press a reader must not make
 * by accident.
 *
 * The web terminal has another — a premium gold, `#D4AF37`, distinct
 * from its brand yellow `#F0B90B` — and that distinction does not survive here, because this app's
 * brand gold *is* `#D8A848`, which is the same metal. Shipping two golds a reader cannot tell
 * apart, under a rule claiming they mean different things, would be a rule with no teeth. Premium
 * is marked by treatment instead: `CoineProColors.Premium` still exists for a subscription card's
 * tint and its label, and the accent under it stays [BRAND].
 */
enum class PageAccent {
    /**
     * Markets, chart, AI, search — anything that reads the market rather than acting on it.
     *
     * Resolves to the brand gold since run Ω2. Kept as a distinct name because a screen declaring
     * what it is remains true and useful, and because collapsing ninety call sites onto [BRAND]
     * would lose that declaration to save nothing.
     */
    ANALYSIS,

    /** Trade, orders, execution, subscription — the app acting on the reader's account. */
    BRAND,

    /** Copy trading and anything social. Gold, like the rest, since run Ω2. */
    SOCIAL,

    /**
     * Irreversible destruction — deleting an account, and nothing lighter.
     *
     * Deliberately not available for a cancel, a close or a sign-out. If every action that ends
     * something were red, red would stop meaning anything, and the one screen that needs a reader
     * to stop and read would look like the rest.
     */
    DESTRUCTIVE,
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
    @Composable @ReadOnlyComposable get() =
        LocalCoineProPalette.current.accentFor(LocalPageAccent.current, fill = true)

/** The current accent as **ink** — a label, an icon, a value on a neutral surface. */
val CoineProColors.pageAccentInk: Color
    @Composable @ReadOnlyComposable get() =
        LocalCoineProPalette.current.accentFor(LocalPageAccent.current, fill = false)

/** The ink that reads on a fill of [pageAccent]. */
val CoineProColors.onPageAccent: Color
    @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.inkOn(LocalPageAccent.current)

/**
 * The accent one domain resolves to, as a pure function of the palette.
 *
 * Not a composable, deliberately: the one-accent rule is the kind of claim that is worth a test
 * rather than a screenshot, and a `@Composable` private function can only be checked by rendering
 * something. See `PageAccentTest`.
 */
internal fun CoineProPalette.accentFor(accent: PageAccent, fill: Boolean): Color = when (accent) {
    // One accent. The fill/ink split is gold's alone and is the reason this cannot simply be a
    // constant: gold as a fill is the brand mid-tone, gold as ink is darkened for white.
    PageAccent.ANALYSIS, PageAccent.SOCIAL, PageAccent.BRAND -> if (fill) accentFill else this.accent
    // The same red the app already uses for a losing position and a refusal. A second red would be
    // a second meaning nobody asked for — and red here is not «down», it is «gone».
    PageAccent.DESTRUCTIVE -> sell
}

/** The ink that reads on a fill of [accentFor]. */
internal fun CoineProPalette.inkOn(accent: PageAccent): Color = when (accent) {
    // White on the one fill that is still a hue; near-black on gold, which is a mid-tone and fails
    // contrast under white in either theme.
    PageAccent.DESTRUCTIVE -> Color.White
    PageAccent.ANALYSIS, PageAccent.SOCIAL, PageAccent.BRAND -> onAccent
}

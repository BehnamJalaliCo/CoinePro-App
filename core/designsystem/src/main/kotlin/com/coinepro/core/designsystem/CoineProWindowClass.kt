package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/**
 * How much room there is, in the three sizes the product actually makes a decision at.
 *
 * The names are Material 3's window size classes and so are the numbers, because the alternative —
 * inventing a scheme — means every future reader has to be told what 620 meant to whoever typed it.
 * These are published, they are what the platform's own guidance and every Android tablet review
 * measures against, and they land where real hardware lands: no phone in portrait reaches 600dp, no
 * tablet in landscape falls below 840dp, and the band between the two is where a large phone
 * sideways and a small tablet upright both sit.
 *
 * There is deliberately no fourth size for "very large". The product has one decision that cares
 * about widths past 840dp — whether the navigation rail carries its labels beside the glyphs — and
 * that is a single threshold on [CoineProWindowClass.widthDp], not a class the whole app has to
 * learn.
 */
enum class CoineProWindowSize {
    /** A phone, in either orientation, and any window narrower than one. */
    COMPACT,

    /** A large phone turned sideways, a small tablet upright, a half-screen split on a tablet. */
    MEDIUM,

    /** A tablet with the whole screen. */
    EXPANDED,
}

/**
 * The size class the whole app reads, and the layout decisions taken from it.
 *
 * ### Why this is derived rather than taken from a library
 *
 * `androidx.compose.material3.adaptive` and `material3-window-size-class` both answer this question
 * and neither is in `gradle/libs.versions.toml`. They were considered and not added, for two
 * reasons that are about this repository rather than about the libraries:
 *
 * 1. **Both need a real window.** `currentWindowAdaptiveInfo()` measures through
 *    `WindowMetricsCalculator`, which wants an `Activity`. The design gate in this repository is
 *    `ScreenshotRenderTest`, which renders production composables off-device at a configured
 *    qualifier, and previews render with no window at all. A size class that reads the
 *    *configuration* is correct in all three places; one that reads the window is correct in one of
 *    them and silently reports a phone in the other two — which would mean the tablet renders this
 *    work exists to add would capture phone layouts and pass.
 * 2. **The decisions have to be unit-testable without Robolectric.** `core:designsystem` tests are
 *    plain JUnit on the JVM. [of] is a pure function of two integers, so every breakpoint below is
 *    asserted in `WindowClassTest` at the exact dp either side of it, which is the only way a
 *    threshold stays where somebody put it.
 *
 * Nothing about the *numbers* is invented — see [CoineProWindowSize]. What is local is only where
 * they are read from.
 *
 * ### Two ways to ask, and they are not the same question
 *
 * [CoineProWindowClass] as read from [LocalCoineProWindowClass] describes **the window**, which is
 * what a navigation rail needs: the rail is a property of the app's shell and must not change
 * because some inner column happens to be narrow.
 *
 * [CoineProListDetail] and `ChartWorkbench` instead measure **the space they were actually given**,
 * with `BoxWithConstraints`, and that is what any layout inside the shell must do. The two rulers
 * differ by exactly the width of the rail, and on an 840dp tablet that difference decides whether
 * the second pane fits — so a list-detail layout that asked the window would put a 360dp list
 * beside a 400dp detail and call it two panes.
 */
@Immutable
data class CoineProWindowClass(
    val widthDp: Int,
    val heightDp: Int,
) {
    /** Which of the three widths this is. Almost every decision in the product reads this one. */
    val width: CoineProWindowSize
        get() = when {
            widthDp < MEDIUM_WIDTH_DP -> CoineProWindowSize.COMPACT
            widthDp < EXPANDED_WIDTH_DP -> CoineProWindowSize.MEDIUM
            else -> CoineProWindowSize.EXPANDED
        }

    /**
     * Which of the three heights this is.
     *
     * Read by far less than [width], and only where something stacks: the chart's pane grid asks
     * it, because eight panes in one column on a short window is eight bands too thin to read.
     */
    val height: CoineProWindowSize
        get() = when {
            heightDp < MEDIUM_HEIGHT_DP -> CoineProWindowSize.COMPACT
            heightDp < EXPANDED_HEIGHT_DP -> CoineProWindowSize.MEDIUM
            else -> CoineProWindowSize.EXPANDED
        }

    /**
     * Whether the shell draws a navigation rail instead of the bottom bar.
     *
     * At [MEDIUM_WIDTH_DP] and above, and the reason is reach rather than room. A bottom bar is
     * where it is because a thumb holding a phone is already there; a tablet is held at the edges
     * or not held at all, and its bottom edge is the furthest point on the device from either hand.
     * The rail also gives the page back the 80dp the bar was taking off the bottom, which on a
     * landscape tablet is a tenth of the height.
     */
    val showsNavigationRail: Boolean get() = width != CoineProWindowSize.COMPACT

    /**
     * Whether the rail carries its labels beside the glyphs rather than beneath them.
     *
     * Not at [EXPANDED_WIDTH_DP], which is the tempting place to put it. A labelled rail is
     * [CoineProRailWidth.LABELLED] wide, and taking that off an 840dp window leaves 600dp of
     * content — well under the 840 a list and a detail need between them. So labelling the rail
     * there would buy a word per tab and pay for it with the second pane, which is the trade
     * [LABELLED_RAIL_WIDTH_DP] exists to refuse.
     */
    val prefersLabelledRail: Boolean get() = widthDp >= LABELLED_RAIL_WIDTH_DP

    /**
     * Whether a list-to-detail screen can show both at once **given the whole window**.
     *
     * A screen inside the shell should measure instead — see [CoineProListDetail], which does. This
     * exists for the shell itself and for code with no layout node to measure: a route deciding
     * whether a detail destination is still a page of its own.
     */
    val showsTwoPanes: Boolean get() = width == CoineProWindowSize.EXPANDED

    /**
     * How many chart panes this window will carry.
     *
     * Two on a phone and eight on a tablet, and the argument for each is in `ChartPanesScreen`.
     * The number lives here rather than there because it is a fact about the glass, and the chart
     * screen is not the only thing that has to agree with it — the layout store caps what it will
     * restore against the same value.
     */
    val maxChartPanes: Int
        get() = if (width == CoineProWindowSize.COMPACT) PHONE_MAX_PANES else TABLET_MAX_PANES

    companion object {
        /**
         * Where a phone stops. No Android phone reports 600dp of width in portrait, and a large one
         * in landscape reports a little over it — which is the point: the class changes exactly
         * when there is genuinely room for a second column.
         */
        const val MEDIUM_WIDTH_DP = 600

        /**
         * Where a tablet with its whole screen starts. Below it a "tablet layout" is a tablet
         * layout on a device that is not one, which is how a two-pane list ends up with a 260dp
         * detail pane.
         */
        const val EXPANDED_WIDTH_DP = 840

        /**
         * Where the rail can afford its labels. See [prefersLabelledRail] for why this is not 840.
         *
         * 1080 = [EXPANDED_WIDTH_DP], which is what a list and a detail need between them, plus the
         * 240 a labelled rail takes off the front of the window. It is the first width at which a
         * reader gets the labels *and* keeps the second pane, and `WindowClassTest` asserts that
         * arithmetic rather than the number — widen the rail and the threshold has to move with it.
         *
         * In hardware terms it is a twelve-inch tablet held sideways. A ten-inch one keeps the
         * glyph rail, which is the right answer: it is the device with the least width to spare.
         */
        const val LABELLED_RAIL_WIDTH_DP = 1080

        /** Material 3's height breakpoints. Only the chart's pane grid reads them. */
        const val MEDIUM_HEIGHT_DP = 480
        const val EXPANDED_HEIGHT_DP = 900

        /** See `ChartPanesScreen` for the argument. Named here so the cap is one number. */
        const val PHONE_MAX_PANES = 2
        const val TABLET_MAX_PANES = 8

        /**
         * The class of a window that is [widthDp] by [heightDp].
         *
         * A pure function on purpose: this is what `WindowClassTest` asserts against, one case per
         * dp either side of every threshold above.
         */
        fun of(widthDp: Int, heightDp: Int): CoineProWindowClass =
            CoineProWindowClass(widthDp = widthDp, heightDp = heightDp)

        /**
         * What a composition with no theme around it reports.
         *
         * A phone, because that is the layout that works everywhere: a missing provider producing
         * "tablet" would give a preview a navigation rail and two panes inside 411dp, and the
         * failure would look like a layout bug rather than a missing provider.
         */
        val Phone = CoineProWindowClass(widthDp = 411, heightDp = 914)
    }
}

/**
 * The window's size class, provided by [CoineProTheme] and read by anything that lays out.
 *
 * Static rather than dynamic: it changes on a rotation and a multi-window resize and at no other
 * time, so the recomposition scoping a dynamic local buys is scoping for an event that happens
 * twice a session and already recomposes everything underneath it anyway.
 */
val LocalCoineProWindowClass = staticCompositionLocalOf { CoineProWindowClass.Phone }

/**
 * The window's size class, for a caller that would otherwise import the local by name.
 *
 * Exists so that call sites read `coineProWindowClass().showsNavigationRail` rather than
 * `LocalCoineProWindowClass.current.showsNavigationRail`, which is the same sentence with the
 * plumbing left in.
 */
@Composable
@ReadOnlyComposable
fun coineProWindowClass(): CoineProWindowClass = LocalCoineProWindowClass.current

/**
 * The size class of the *current configuration*, for [CoineProTheme] to provide.
 *
 * Internal because everything else should read [LocalCoineProWindowClass]: a screen that recomputes
 * this for itself is a screen that will disagree with the shell the day the app runs in a window
 * that is not the whole display, and the two would then draw a rail and a bottom bar at once.
 */
@Composable
@ReadOnlyComposable
internal fun configurationWindowClass(): CoineProWindowClass {
    val configuration = LocalConfiguration.current
    return CoineProWindowClass.of(configuration.screenWidthDp, configuration.screenHeightDp)
}

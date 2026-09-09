package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.app.GoldenScreenshot.assertMatchesGolden
import com.coinepro.app.ideas.IdeasFace
import com.coinepro.app.ideas.IdeasScreen
import com.coinepro.core.community.CommunityController
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.designsystem.TeachingDismissals
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.community.CommunityScreen
import com.coinepro.feature.explore.ExploreScreen
import com.coinepro.feature.menu.MenuAccess
import com.coinepro.feature.menu.MenuScreen
import com.coinepro.feature.search.WatchlistScreen
import com.coinepro.feature.signals.SignalsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The surfaces whose pixels are committed, and the widths they are committed at.
 *
 * ### Why this matrix and not another
 *
 * Every case here is a screen where **a point of chrome is the product**. The watchlist's fold
 * decides how many markets a reader sees; the bar's height is a ninth of the phone; Explore's fold
 * decides whether the page opens on market content or on the way out of itself; Ideas is the one
 * place two screens are composed inside a third, which is exactly where a duplicated header hides.
 * A regression on any of them is invisible in a unit test and obvious in a diff.
 *
 * ### Two widths, and 393 is the important one
 *
 * 411 is this design system's reference width. It is **wider than the phone most readers hold**, and
 * that gap has already shipped one visible fault — the watchlist's move column ran off the row at
 * 393 while every measurement said it fitted. So the surfaces where width is tightest are pinned at
 * both, and the narrow one is not optional.
 *
 * ### Both themes, and both directions
 *
 * Dark and light for the three list surfaces, because this palette is not an inversion and several
 * of its tokens genuinely swap roles. And one English case, because the whole layout mirrors: a
 * screen that is right in Persian can be wrong in English in ways only a picture shows.
 *
 * See [GoldenScreenshot] for what a failure prints and how to re-record.
 */
@RunWith(RobolectricTestRunner::class)
// Real pixels. Robolectric's legacy graphics mode records draw calls and returns a bitmap of the
// fill colour, which for a golden comparison is a test that compares two blank frames and always
// passes. Native runs the same Skia the device does.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoldenScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /* ------------------------------------------------------------------ watchlist */

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun watchlistDark() = composeRule.assertMatchesGolden("watchlist-fa-411-dark") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun watchlistLight() =
        composeRule.assertMatchesGolden("watchlist-fa-411-light", darkTheme = false) { Watchlist() }

    /** The narrow phone, which is the one the column arithmetic is actually measured against. */
    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun watchlistNarrow() = composeRule.assertMatchesGolden("watchlist-fa-393") { Watchlist() }

    /** The same screen with the layout mirrored, which is a different layout. */
    @Test
    @Config(sdk = [34], qualifiers = EN_411)
    fun watchlistEnglish() = composeRule.assertMatchesGolden("watchlist-en-411") { Watchlist() }

    /* ------------------------------------------------------------------ explore */

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun exploreDark() = composeRule.assertMatchesGolden("explore-fa-411-dark") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun exploreLight() =
        composeRule.assertMatchesGolden("explore-fa-411-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun exploreNarrow() = composeRule.assertMatchesGolden("explore-fa-393") { Explore() }

    /* ------------------------------------------------------------------ ideas */

    /**
     * Both faces, with the content the screens actually draw.
     *
     * A stub would render the switch and prove nothing: the fault this case exists for is a *child*
     * screen drawing its own heading under the switch that already named the page, and a stub has
     * no heading to draw.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun ideasSignals() = composeRule.assertMatchesGolden("ideas-signals-fa-411") {
        Ideas(IdeasFace.SIGNALS)
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun ideasCommunity() = composeRule.assertMatchesGolden("ideas-community-fa-411") {
        Ideas(IdeasFace.COMMUNITY)
    }

    /**
     * The light theme, because that is the theme the switch was wrong in.
     *
     * «روی سیگنال می‌زنم، انجمن باز می‌شود.» The panes were right and the *mark* was inverted: the
     * chosen key was `SurfaceRaised`, which on this theme is the page's own white, so it disappeared
     * and the unchosen grey key was the only marked thing on the row. A dark golden cannot see that
     * — the two tokens differ there — which is exactly why this case is here.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun ideasSignalsLight() =
        composeRule.assertMatchesGolden("ideas-signals-fa-411-light", darkTheme = false) {
            Ideas(IdeasFace.SIGNALS)
        }

    /* ------------------------------------------------------------------ menu */

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun menuDark() = composeRule.assertMatchesGolden("menu-fa-411-dark") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun menuLight() =
        composeRule.assertMatchesGolden("menu-fa-411-light", darkTheme = false) { Menu() }

    /* ------------------------------------------------------------------ the wider matrix */

    /*
     * Phase 6 of the audit: the same screens in the other locale, on a tablet, and at the largest
     * font scale a reader is likely to set. Not every screen at every point — twelve screens times
     * sixteen combinations is a diff nobody reads — but each axis on the screen where it bites:
     * the menu for the mirrored layout and the large font (the longest strings), the watchlist for
     * the tablet (the two-pane threshold).
     */

    @Test
    @Config(sdk = [34], qualifiers = EN_411)
    fun menuEnglishDark() = composeRule.assertMatchesGolden("menu-en-411-dark") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun watchlistTablet() = composeRule.assertMatchesGolden("watchlist-fa-840") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun menuTablet() = composeRule.assertMatchesGolden("menu-fa-840") { Menu() }

    /* ------------------------------------------------------------------ the tablet, both ways */

    /*
     * §4 of the plan: every top-level screen on the tablet in both orientations, so the parity
     * matrix in `docs/qa/PARITY_MATRIX.md` (generated from these qualifiers) has a picture behind
     * every cell rather than a tick. Portrait is [FA_840], the two-pane threshold; landscape is
     * [FA_1280], a ten-inch tablet on its side, where the rail is labelled and the chart's plot
     * is wider than it is tall. A foldable open flat is the portrait case by window class; the
     * hinge itself is a JVM matter (`ChartFoldTest`), because Robolectric has no hinge to render.
     */

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun exploreTablet() = composeRule.assertMatchesGolden("explore-fa-840") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun ideasTablet() = composeRule.assertMatchesGolden("ideas-signals-fa-840") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun chartTablet() = composeRule.assertMatchesGolden("chart-fa-840") {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope))
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun watchlistTabletLandscape() = composeRule.assertMatchesGolden("watchlist-fa-1280") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun exploreTabletLandscape() = composeRule.assertMatchesGolden("explore-fa-1280") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun ideasTabletLandscape() = composeRule.assertMatchesGolden("ideas-signals-fa-1280") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun menuTabletLandscape() = composeRule.assertMatchesGolden("menu-fa-1280") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun chartTabletLandscape() = composeRule.assertMatchesGolden("chart-fa-1280") {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope))
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_411, fontScale = 1.3f)
    fun menuLargeType() = composeRule.assertMatchesGolden("menu-fa-411-font130") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_393, fontScale = 1.3f)
    fun watchlistLargeType() = composeRule.assertMatchesGolden("watchlist-fa-393-font130") { Watchlist() }

    /* ------------------------------------------------------------------ the device matrix */
    /*
     * Item 2 of the 4.52 run: the five top-level screens on the plan's three devices, dark and
     * light, Persian and English. The qualifiers are the panels' own dp (see `FA_S9U` and the
     * fold constants). `docs/qa/PARITY_MATRIX.md` is generated from these.
     */
    // Pixel Tablet, landscape
    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun watchlistFa1280Light() = composeRule.assertMatchesGolden("watchlist-fa-1280-light", darkTheme = false) { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun watchlistEn1280Dark() = composeRule.assertMatchesGolden("watchlist-en-1280") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun watchlistEn1280Light() = composeRule.assertMatchesGolden("watchlist-en-1280-light", darkTheme = false) { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun exploreFa1280Light() = composeRule.assertMatchesGolden("explore-fa-1280-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun exploreEn1280Dark() = composeRule.assertMatchesGolden("explore-en-1280") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun exploreEn1280Light() = composeRule.assertMatchesGolden("explore-en-1280-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun ideasSignalsFa1280Light() = composeRule.assertMatchesGolden("ideas-signals-fa-1280-light", darkTheme = false) { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun ideasSignalsEn1280Dark() = composeRule.assertMatchesGolden("ideas-signals-en-1280") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun ideasSignalsEn1280Light() = composeRule.assertMatchesGolden("ideas-signals-en-1280-light", darkTheme = false) { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun menuFa1280Light() = composeRule.assertMatchesGolden("menu-fa-1280-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun menuEn1280Dark() = composeRule.assertMatchesGolden("menu-en-1280") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun menuEn1280Light() = composeRule.assertMatchesGolden("menu-en-1280-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun chartFa1280Light() = composeRule.assertMatchesGolden("chart-fa-1280-light", darkTheme = false) { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun chartEn1280Dark() = composeRule.assertMatchesGolden("chart-en-1280") { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun chartEn1280Light() = composeRule.assertMatchesGolden("chart-en-1280-light", darkTheme = false) { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    // Galaxy Tab S9 Ultra, landscape
    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun watchlistFaS9UDark() = composeRule.assertMatchesGolden("watchlist-fa-s9u") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun watchlistFaS9ULight() = composeRule.assertMatchesGolden("watchlist-fa-s9u-light", darkTheme = false) { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun watchlistEnS9UDark() = composeRule.assertMatchesGolden("watchlist-en-s9u") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun watchlistEnS9ULight() = composeRule.assertMatchesGolden("watchlist-en-s9u-light", darkTheme = false) { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun exploreFaS9UDark() = composeRule.assertMatchesGolden("explore-fa-s9u") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun exploreFaS9ULight() = composeRule.assertMatchesGolden("explore-fa-s9u-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun exploreEnS9UDark() = composeRule.assertMatchesGolden("explore-en-s9u") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun exploreEnS9ULight() = composeRule.assertMatchesGolden("explore-en-s9u-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun ideasSignalsFaS9UDark() = composeRule.assertMatchesGolden("ideas-signals-fa-s9u") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun ideasSignalsFaS9ULight() = composeRule.assertMatchesGolden("ideas-signals-fa-s9u-light", darkTheme = false) { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun ideasSignalsEnS9UDark() = composeRule.assertMatchesGolden("ideas-signals-en-s9u") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun ideasSignalsEnS9ULight() = composeRule.assertMatchesGolden("ideas-signals-en-s9u-light", darkTheme = false) { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun menuFaS9UDark() = composeRule.assertMatchesGolden("menu-fa-s9u") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun menuFaS9ULight() = composeRule.assertMatchesGolden("menu-fa-s9u-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun menuEnS9UDark() = composeRule.assertMatchesGolden("menu-en-s9u") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun menuEnS9ULight() = composeRule.assertMatchesGolden("menu-en-s9u-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun chartFaS9UDark() = composeRule.assertMatchesGolden("chart-fa-s9u") { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun chartFaS9ULight() = composeRule.assertMatchesGolden("chart-fa-s9u-light", darkTheme = false) { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun chartEnS9UDark() = composeRule.assertMatchesGolden("chart-en-s9u") { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun chartEnS9ULight() = composeRule.assertMatchesGolden("chart-en-s9u-light", darkTheme = false) { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    // Pixel Fold, inner display
    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun watchlistFaFoldopenDark() = composeRule.assertMatchesGolden("watchlist-fa-foldopen") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun watchlistFaFoldopenLight() = composeRule.assertMatchesGolden("watchlist-fa-foldopen-light", darkTheme = false) { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun watchlistEnFoldopenDark() = composeRule.assertMatchesGolden("watchlist-en-foldopen") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun watchlistEnFoldopenLight() = composeRule.assertMatchesGolden("watchlist-en-foldopen-light", darkTheme = false) { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun exploreFaFoldopenDark() = composeRule.assertMatchesGolden("explore-fa-foldopen") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun exploreFaFoldopenLight() = composeRule.assertMatchesGolden("explore-fa-foldopen-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun exploreEnFoldopenDark() = composeRule.assertMatchesGolden("explore-en-foldopen") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun exploreEnFoldopenLight() = composeRule.assertMatchesGolden("explore-en-foldopen-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun ideasSignalsFaFoldopenDark() = composeRule.assertMatchesGolden("ideas-signals-fa-foldopen") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun ideasSignalsFaFoldopenLight() = composeRule.assertMatchesGolden("ideas-signals-fa-foldopen-light", darkTheme = false) { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun ideasSignalsEnFoldopenDark() = composeRule.assertMatchesGolden("ideas-signals-en-foldopen") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun ideasSignalsEnFoldopenLight() = composeRule.assertMatchesGolden("ideas-signals-en-foldopen-light", darkTheme = false) { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun menuFaFoldopenDark() = composeRule.assertMatchesGolden("menu-fa-foldopen") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun menuFaFoldopenLight() = composeRule.assertMatchesGolden("menu-fa-foldopen-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun menuEnFoldopenDark() = composeRule.assertMatchesGolden("menu-en-foldopen") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun menuEnFoldopenLight() = composeRule.assertMatchesGolden("menu-en-foldopen-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun chartFaFoldopenDark() = composeRule.assertMatchesGolden("chart-fa-foldopen") { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun chartFaFoldopenLight() = composeRule.assertMatchesGolden("chart-fa-foldopen-light", darkTheme = false) { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun chartEnFoldopenDark() = composeRule.assertMatchesGolden("chart-en-foldopen") { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_OPEN)
    fun chartEnFoldopenLight() = composeRule.assertMatchesGolden("chart-en-foldopen-light", darkTheme = false) { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    // Pixel Fold, cover display
    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun watchlistFaFoldclosedDark() = composeRule.assertMatchesGolden("watchlist-fa-foldclosed") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun watchlistFaFoldclosedLight() = composeRule.assertMatchesGolden("watchlist-fa-foldclosed-light", darkTheme = false) { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun watchlistEnFoldclosedDark() = composeRule.assertMatchesGolden("watchlist-en-foldclosed") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun watchlistEnFoldclosedLight() = composeRule.assertMatchesGolden("watchlist-en-foldclosed-light", darkTheme = false) { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun exploreFaFoldclosedDark() = composeRule.assertMatchesGolden("explore-fa-foldclosed") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun exploreFaFoldclosedLight() = composeRule.assertMatchesGolden("explore-fa-foldclosed-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun exploreEnFoldclosedDark() = composeRule.assertMatchesGolden("explore-en-foldclosed") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun exploreEnFoldclosedLight() = composeRule.assertMatchesGolden("explore-en-foldclosed-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun ideasSignalsFaFoldclosedDark() = composeRule.assertMatchesGolden("ideas-signals-fa-foldclosed") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun ideasSignalsFaFoldclosedLight() = composeRule.assertMatchesGolden("ideas-signals-fa-foldclosed-light", darkTheme = false) { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun ideasSignalsEnFoldclosedDark() = composeRule.assertMatchesGolden("ideas-signals-en-foldclosed") { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun ideasSignalsEnFoldclosedLight() = composeRule.assertMatchesGolden("ideas-signals-en-foldclosed-light", darkTheme = false) { Ideas(IdeasFace.SIGNALS) }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun menuFaFoldclosedDark() = composeRule.assertMatchesGolden("menu-fa-foldclosed") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun menuFaFoldclosedLight() = composeRule.assertMatchesGolden("menu-fa-foldclosed-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun menuEnFoldclosedDark() = composeRule.assertMatchesGolden("menu-en-foldclosed") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun menuEnFoldclosedLight() = composeRule.assertMatchesGolden("menu-en-foldclosed-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun chartFaFoldclosedDark() = composeRule.assertMatchesGolden("chart-fa-foldclosed") { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun chartFaFoldclosedLight() = composeRule.assertMatchesGolden("chart-fa-foldclosed-light", darkTheme = false) { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun chartEnFoldclosedDark() = composeRule.assertMatchesGolden("chart-en-foldclosed") { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    @Test
    @Config(sdk = [34], qualifiers = EN_FOLD_CLOSED)
    fun chartEnFoldclosedLight() = composeRule.assertMatchesGolden("chart-en-foldclosed-light", darkTheme = false) { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }

    /* ------------------------------------------------------------------ the bar */

    /**
     * Every state of the bar at once, which is the only way to see the selection treatment.
     *
     * One capture per tab would be five nearly identical frames; stacked, the difference between
     * the plate under «دیده‌بان» and the plate under «منو» is on one screen and a diff points
     * straight at it.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun bottomBarStates() = composeRule.assertMatchesGolden("bottom-bar-fa-411") { BarStates() }

    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun bottomBarNarrow() = composeRule.assertMatchesGolden("bottom-bar-fa-393") { BarStates() }

    /* ------------------------------------------------------------------ the chart */

    /**
     * A sanity frame, and deliberately only one.
     *
     * The chart's geometry and palette are settled and out of scope for a chrome pass; what this
     * guards is that a change to a shared token — a colour, a spacing step, the row that the
     * watchlist strip shares — did not reach into the plot.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun chartSanity() = composeRule.assertMatchesGolden("chart-fa-411") {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope))
    }

    /* ------------------------------------------------------------------ the fixtures */

    /**
     * The watchlist in its **steady state**, which is the one worth committing pixels for.
     *
     * The teaching banner is marked dismissed. It is a one-visit object — read once, closed, never
     * seen again — and a golden that carries it would be guarding ninety points of chrome that no
     * reader past their first day has on screen, while guarding nothing about the fold that
     * everybody actually sees.
     */
    @Composable
    private fun Watchlist() {
        val store = WatchlistStore(FakeScreenshotPreferences())
        runBlocking {
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD").forEach {
                store.add(Watchlist.DEFAULT_LIST_ID, it)
            }
        }
        Taught {
            WatchlistScreen(
                controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
                    .also { it.start() },
                store = store,
                sparklines = ScreenshotFixtures.sparklineStore(scope),
                onOpenSymbol = {},
                onOpenSearch = {},
            )
        }
    }

    @Composable
    private fun Explore() {

        val intel = MarketIntelController(FakeMarketIntelGateway(), scope).also { it.refresh() }
        ExploreScreen(
            controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
                .also { it.start() },
            intel = intel,
            sparklines = ScreenshotFixtures.sparklineStore(scope),
            onOpenSymbol = {},
            onOpenNews = {},
            onOpenCalendar = {},
            onOpenHeatmap = {},
            onOpenSearch = {},
            onOpenMarkets = {},
        )
    }

    @Composable
    private fun Ideas(initial: IdeasFace) {
        val signals = SignalController(FakeSignalGateway(), scope)
        val community = CommunityController(FakeCommunityGateway(), FakeCommunityIdentity(), scope)
        Taught {
        IdeasScreen(
            initial = initial,
            signals = {
                SignalsScreen(
                    controller = signals,
                    onOpenSignal = {},
                    platform = MarketPlatform.TRADEYAR,
                    embedded = true,
                )
            },
            community = {
                CommunityScreen(controller = community, onOpenThread = {}, embedded = true)
            },
        )
        }
    }

    @Composable
    private fun Menu() {
        MenuScreen(
            access = MenuAccess(platform = MarketPlatform.TRADEYAR, signedIn = true),
            onOpen = {},
            name = "بهنام",
            email = "trader@example.com",
            planLabel = "حرفه‌ای",
            platformLabel = "کریپتو",
            watchlistCount = 12,
        )
    }

    /** Every teaching banner already read, which is where a reader spends their life. */
    @Composable
    private fun Taught(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalTeachingDismissals provides AllTeachingDismissed,
            content = content,
        )
    }

    @Composable
    private fun BarStates() {
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            AppDestination.entries.forEach { destination ->
                Text(
                    text = destination.route,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
                CoineProBottomBar(
                    currentRoute = destination.route,
                    onSelect = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    private companion object {
        /** The design system's reference width, in the app's own language and direction. */
        const val FA_411 = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"

        /** A Pixel, which is narrower than the reference and is what most readers hold. */
        const val FA_393 = "fa-rIR-ldrtl-w393dp-h914dp-xxhdpi"

        /** The mirrored layout. */
        const val EN_411 = "en-rUS-ldltr-w411dp-h914dp-xxhdpi"

        /** A tablet in portrait — past the width where lists and charts go two-pane. */
        const val FA_840 = "fa-rIR-ldrtl-w840dp-h1280dp-xhdpi"

        /** The same tablet on its side — the labelled rail, and a plot wider than it is tall. */
        const val FA_1280 = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi"
        const val EN_1280 = "en-rUS-ldltr-sw800dp-w1280dp-h800dp-xhdpi"

        /** Galaxy Tab S9 Ultra: 2960×1848 at hdpi is 1973×1232 dp, the widest window the app meets. */
        const val FA_S9U = "fa-rIR-ldrtl-sw1232dp-w1973dp-h1232dp-hdpi"
        const val EN_S9U = "en-rUS-ldltr-sw1232dp-w1973dp-h1232dp-hdpi"

        /** Pixel Fold open: 2208×1840 at 380 dpi is 930×775 dp — expanded, but not by much. */
        const val FA_FOLD_OPEN = "fa-rIR-ldrtl-sw775dp-w930dp-h775dp-xhdpi"
        const val EN_FOLD_OPEN = "en-rUS-ldltr-sw775dp-w930dp-h775dp-xhdpi"

        /** Pixel Fold closed: the cover is a 411×797 dp phone. */
        const val FA_FOLD_CLOSED = "fa-rIR-ldrtl-w411dp-h797dp-xxhdpi"
        const val EN_FOLD_CLOSED = "en-rUS-ldltr-w411dp-h797dp-xxhdpi"
    }
}


/** Every banner read and closed — the state a screen spends almost all of its life in. */
internal object AllTeachingDismissed : TeachingDismissals {
    override val dismissed: Set<String> = ALL_TEACHING_KEYS
    override fun dismiss(key: String) = Unit
    override fun restore(key: String) = Unit
}

private val ALL_TEACHING_KEYS: Set<String> =
    com.coinepro.core.designsystem.TeachingSurface.entries.map { it.key }.toSet()

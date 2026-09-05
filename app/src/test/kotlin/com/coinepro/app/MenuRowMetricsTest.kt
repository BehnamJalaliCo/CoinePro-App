package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.model.MarketPlatform
import com.coinepro.feature.menu.MenuAccess
import com.coinepro.feature.menu.MenuCatalogue
import com.coinepro.feature.menu.MenuScreen
import com.coinepro.feature.menu.MenuTestTags
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every row of the menu, measured — because «۵۰ نقطه» was a claim and not a fact.
 *
 * ### What was actually in the code
 *
 * `heightIn(min = 50.dp)`. That is a *floor*, and a floor is not a rhythm: it says a row will be
 * fifty **or more**, and twenty-four of the thirty-two rows carried a second line, so what the page
 * really had was two heights interleaved in whatever order the catalogue happened to be written in.
 * The comment beside the modifier said the list had one rhythm. It did not, and nothing checked.
 *
 * ### So this measures rather than trusts
 *
 * Every row is tagged with its own id and every one of them is scrolled to and read, in both
 * account states and at both widths and in both directions. That covers the five shapes a row can
 * take, which is the whole reason a single number is hard to hold:
 *
 * * an **ordinary** row — a mark, a name, a chevron;
 * * a **descriptive** row, with the second line (`screener` and the six others);
 * * a **locked** row, which suppresses its second line and gains a badge — so the same entry is a
 *   different shape depending on who is reading;
 * * a **trailing-value** row, `watchlist`, which carries a count;
 * * the **destructive** row, `delete`, which is drawn in the refusal colour and is last.
 *
 * ### The two assertions
 *
 * Ordinary rows are held to **exactly** fifty, with half a point for rounding. Descriptive rows are
 * allowed their second line and held to a ceiling instead — and the ceiling is low enough that
 * adding a third line, or letting a subtitle wrap, fails here rather than in somebody's eye three
 * releases later.
 *
 * A row that is not on [MenuCatalogue.DESCRIPTIVE_ROWS] and is not fifty is a bug in the layout. A
 * row that *is* on that list and did not need to be is a bug in the catalogue, and the list's own
 * documentation is where the argument for each one has to be written down.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuRowMetricsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** A member, on the backend that serves every row this catalogue has. */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun signedInRowsAt411() = assertRowRhythm("fa 411 member", signedIn = true)

    /**
     * And a guest, which is a **different set of shapes**: eleven rows gain a badge and lose their
     * second line, so the row that was descriptive is now ordinary and has to measure like one.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun signedOutRowsAt411() = assertRowRhythm("fa 411 guest", signedIn = false)

    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun signedInRowsAt393() = assertRowRhythm("fa 393 member", signedIn = true)

    /** The mirrored layout, with the longer words. */
    @Test
    @Config(sdk = [34], qualifiers = EN_411)
    fun signedInRowsInEnglish() = assertRowRhythm("en 411 member", signedIn = true)

    /**
     * The other backend, because two rows only exist there.
     *
     * `copy-trade` and `academy` are CoinePro-FX's, so a measurement taken on TradeYar has never
     * seen them — and `copy-trade` is one of the seven that carries a second line.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun signedInRowsOnForex() =
        assertRowRhythm("fa 411 member fx", signedIn = true, platform = MarketPlatform.COINEPRO_FX)

    /**
     * The whitelist is short, and stays short by being asserted.
     *
     * Without this, "only where the name alone is not enough" is a sentence in a doc comment that
     * the next person adding a row will not read. Seven is the number the audit arrived at; making
     * it eight should be a decision somebody takes on purpose, in this file, with a reason.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun onlyTheAuditedRowsCarryASecondLine() {
        assertEquals(
            "The set of rows with a second line has changed. Every one of these has to survive the " +
                "same question — would a reader guess wrong about where this row goes from the " +
                "title alone? — and the argument belongs in MenuCatalogue.DESCRIPTIVE_ROWS.",
            AUDITED_DESCRIPTIVE_ROWS,
            MenuCatalogue.DESCRIPTIVE_ROWS,
        )
    }

    private fun assertRowRhythm(
        label: String,
        signedIn: Boolean,
        platform: MarketPlatform = MarketPlatform.TRADEYAR,
    ) {
        val access = MenuAccess(platform = platform, signedIn = signedIn)
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                MenuScreen(
                    access = access,
                    onOpen = {},
                    modifier = Modifier.testTag(MENU),
                    name = if (signedIn) "بهنام" else null,
                    email = if (signedIn) "trader@example.com" else null,
                    planLabel = if (signedIn) "حرفه‌ای" else null,
                    platformLabel = "کریپتو",
                    // The trailing-value shape: `watchlist` is the one row that carries a figure,
                    // and a figure is another thing that can push a row taller.
                    watchlistCount = 12,
                    onSignIn = if (signedIn) null else ({}),
                )
            }
        }
        composeRule.waitForIdle()

        val items = MenuCatalogue.sections(access).flatMap { it.items }
        val tall = mutableListOf<String>()
        val offRhythm = mutableListOf<String>()

        items.forEach { item ->
            val id = item.entry.id
            val tag = MenuTestTags.row(id)
            composeRule.onNodeWithTag(MENU).performScrollToNode(hasTestTag(tag))
            val height = with(composeRule.density) {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode().size.height.toDp()
            }
            // A locked row does not draw its second line, so it is an ordinary row whoever wrote
            // the catalogue entry. That is the whole reason both account states are measured.
            val descriptive = item.entry.bodyRes != null && !item.locked
            val shape = buildString {
                append(if (descriptive) "descriptive" else "ordinary")
                if (item.locked) append("+locked")
                if (item.entry.destructive) append("+destructive")
                if (id == "watchlist") append("+value")
            }
            println("menu[$label] $id: $height ($shape)")

            if (!descriptive && abs((height - ROW_HEIGHT).value) > ROW_DRIFT_DP) {
                offRhythm += "$id=$height"
            }
            if (height > MAX_ROW_HEIGHT) {
                tall += "$id=$height"
            }
        }

        assertTrue(
            "[$label] these rows carry no second line and so should be exactly $ROW_HEIGHT, and " +
                "are not: $offRhythm. A directory's whole claim is one row height.",
            offRhythm.isEmpty(),
        )
        assertTrue(
            "[$label] these rows are taller than $MAX_ROW_HEIGHT: $tall. A descriptive row is one " +
                "extra line and no more — anything past this is a subtitle that wrapped or a " +
                "third line that nobody decided to add.",
            tall.isEmpty(),
        )
    }

    private companion object {
        const val MENU = "menu-page"

        const val FA_411 = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val FA_393 = "fa-rIR-ldrtl-w393dp-h914dp-xxhdpi"
        const val EN_411 = "en-rUS-ldltr-w411dp-h914dp-xxhdpi"

        /** The directory row, and half a point of rounding. */
        val ROW_HEIGHT: Dp = 50.dp
        const val ROW_DRIFT_DP = 0.5f

        /**
         * The ceiling for a row that is allowed its second line.
         *
         * Fifty is the title, its leading and the eight points of air at each end. One more line of
         * `labelSmall` is what a descriptive row buys, and this is that — so a subtitle that wraps
         * to two lines, or a third line, fails here. Sixty is the top of the master prompt's
         * «56–60 dp» band for a two-line row; the second line got a point of leading in Sprint A2
         * (`labelSmall` 11/15) and a descriptive row now measures fifty-three.
         */
        val MAX_ROW_HEIGHT: Dp = 60.dp

        /** The seven the audit kept. See [MenuCatalogue.DESCRIPTIVE_ROWS] for the argument. */
        val AUDITED_DESCRIPTIVE_ROWS = setOf(
            "screener",
            "connections",
            "copy-trade",
            "terminal",
            "ai",
            "ai-assistant",
            "safety",
        )
    }
}

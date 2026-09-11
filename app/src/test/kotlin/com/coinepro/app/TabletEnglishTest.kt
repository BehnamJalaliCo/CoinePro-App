package com.coinepro.app

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.AnnotatedString
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.search.WatchlistScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The English tablet is in English — every word of it (run H item 5).
 *
 * ### The defect
 *
 * Three things on the tablet chart were Persian whatever language the app was in, because none of
 * them is a string resource: the **drawing tools** and their groups (`DrawingTools`, a catalogue in
 * `chart-core`, which has no resources), the **market reading's values** («متوسط · کم · خنثی», from
 * `ChartReading`), and the **column headings** of the watchlist table (fixed in 4.71.0). A leak of
 * this kind cannot be found by the resource gates — there is no resource to check — so it is found
 * here, by rendering the screens and reading what they actually drew.
 *
 * ### What is allowed to be Persian
 *
 * Nothing, on these screens, in this locale. An instrument's *own* Persian name is the one thing
 * that legitimately appears in either language — «طلا / دلار آمریکا» — and it is bilingual since
 * 4.71.0, so in `en` it does not appear either. That makes the assertion a flat one: no Arabic
 * script anywhere. A screen that later has to show user-entered Persian — a note, a list somebody
 * named — belongs on the allow-list below with the reason, not in a weakened rule.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-rUS-w1280dp-h800dp-xhdpi")
class TabletEnglishTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun theChartOnATabletIsEnglishThroughout() {
        render { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }
        assertNoPersian("the tablet chart")
    }

    @Test
    fun theWatchlistOnATabletIsEnglishThroughout() {
        render {
            val store = remember { WatchlistStore(FakeScreenshotPreferences()) }
            runBlocking {
                listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD").forEach {
                    store.add(Watchlist.DEFAULT_LIST_ID, it)
                }
            }
            WatchlistScreen(
                controller = remember {
                    MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() }
                },
                store = store,
                sparklines = remember { ScreenshotFixtures.sparklineStore(scope) },
                onOpenSymbol = {},
                onOpenSearch = {},
            )
        }
        assertNoPersian("the tablet watchlist")
    }

    /**
     * Every drawing tool and every group, in English, straight from the catalogue.
     *
     * Not a render: the rail draws one glyph per group and puts the names in flyouts and in content
     * descriptions, so a screen walk would only reach the twelve that happen to be open. Ninety-one
     * tools is the number that has to be complete, and the catalogue is where completeness lives.
     */
    @Test
    fun everyToolAndGroupHasAnEnglishName() {
        val tools = com.coinepro.core.chart.DrawingTools.ALL
        val untranslated = tools.filter { PERSIAN.containsMatchIn(it.englishLabel) || it.englishLabel.isBlank() }
        assertTrue("every tool needs an English name: ${untranslated.map { it.id }}", untranslated.isEmpty())
        val groups = com.coinepro.core.chart.ToolGroup.entries
            .filter { PERSIAN.containsMatchIn(it.englishLabel) || it.englishLabel.isBlank() }
        assertTrue("every tool group needs an English name: $groups", groups.isEmpty())
        // And the two languages are actually different words, which is what catches a row copied
        // from the column beside it.
        val copied = tools.filter { it.englishLabel == it.label }
        assertTrue("a tool's English name is not its Persian one: ${copied.map { it.id }}", copied.isEmpty())
    }

    private fun render(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun assertNoPersian(what: String) {
        val offenders = texts().filter { PERSIAN.containsMatchIn(it) }
        assertTrue("$what drew Persian text in the English locale: $offenders", offenders.isEmpty())
    }

    /** Every string the frame is drawing, including the content descriptions the rail carries. */
    private fun texts(): List<String> {
        val found = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.find { it.key == SemanticsProperties.Text }?.let { entry ->
                @Suppress("UNCHECKED_CAST")
                (entry.value as? List<AnnotatedString>)?.forEach { found += it.text }
            }
            node.config.find { it.key == SemanticsProperties.ContentDescription }?.let { entry ->
                @Suppress("UNCHECKED_CAST")
                (entry.value as? List<String>)?.forEach { found += it }
            }
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot().fetchSemanticsNode())
        return found
    }

    private companion object {
        /** The Arabic block, which is what Persian is written in. */
        val PERSIAN = Regex("[\\u0600-\\u06FF]")
    }
}

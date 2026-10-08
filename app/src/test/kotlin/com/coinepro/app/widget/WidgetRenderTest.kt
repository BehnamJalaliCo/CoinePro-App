package com.coinepro.app.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.WidgetMarket
import com.coinepro.core.datastore.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 5.27.0: the three widgets, inflated the way a launcher inflates them — TradingView's set: the
 * watchlist with its header, the one-market tile with its mark, and the news with its paging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl")
class WidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val snapshot = WidgetSnapshot(
        markets = listOf(
            WidgetMarket("BTCUSDT", "Bitcoin", "64,210.50", "+3.24%", 1, changeAmountText = "+2,015.10", wire = "BTCUSDT"),
            WidgetMarket("XAUUSD", "Gold", "2,592.60", "−0.42%", -1, changeAmountText = "−10.90", wire = "XAUUSD"),
            WidgetMarket("EURUSD", "Euro", "1.0842", "+0.10%", 1, wire = "EURUSD"),
        ),
        capturedAtEpochMillis = 1_700_000_000_000L,
        title = "",
    )

    private fun inflate(views: android.widget.RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun images(view: View): List<ImageView> = when (view) {
        is ImageView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { images(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun `the watchlist widget carries its header, every row and each row's mark`() {
        val view = inflate(WidgetRenderer.render(context, snapshot, WidgetLayout.of(250, 150), MarketColorScheme.GREEN_UP, 7))
        val shown = texts(view)
        assertTrue(shown.any { it.startsWith("به‌روزرسانی") })
        assertTrue("+2,015.10  +3.24%" in shown)
        assertTrue("XAUUSD" in shown)
        assertNotNull(view.findViewById<View>(com.coinepro.app.R.id.widget_settings))
        val rowLogos = images(view).filter { it.id == com.coinepro.app.R.id.row_logo && it.drawable != null }
        assertEquals("a mark on every row shown", WidgetLayout.of(250, 150).rows.coerceAtMost(3), rowLogos.size)
    }

    @Test
    fun `the one-market tile carries the mark, the price and the change in price and percent`() {
        val market = snapshot.markets.first()
        val view = inflate(
            SymbolWidgetRenderer.render(
                context = context,
                market = market,
                symbol = "BTCUSDT",
                freshness = "12:00",
                layout = SymbolWidgetPick.layoutFor(160, 150),
                colours = MarketColorScheme.GREEN_UP,
            ),
        )
        val shown = texts(view)
        assertTrue("64,210.50" in shown)
        assertTrue("+2,015.10  +3.24%" in shown)
        assertNotNull(view.findViewById<ImageView>(com.coinepro.app.R.id.symbol_logo).drawable)
    }

    @Test
    fun `the news widget shows one story and pages through the rest`() {
        val store = NewsWidgetStore(context)
        store.setFeed(9, NewsFeed.CRYPTO)
        store.write(
            NewsFeed.CRYPTO,
            listOf(
                WidgetStory("First headline", "CoinDesk", 1_700_000_000_000L, null),
                WidgetStory("Second headline", "Reuters", 1_699_999_000_000L, null),
            ),
            capturedAt = 1_700_000_100_000L,
        )
        assertTrue("First headline" in texts(inflate(NewsWidgetRenderer.render(context, store, 9))))
        store.turn(9, 1)
        assertTrue("Second headline" in texts(inflate(NewsWidgetRenderer.render(context, store, 9))))
        store.turn(9, 1)
        assertTrue("it wraps round", "First headline" in texts(inflate(NewsWidgetRenderer.render(context, store, 9))))
    }

    @Test
    fun `the picker previews inflate with content rather than a blank card`() {
        listOf(
            com.coinepro.app.R.layout.widget_symbol_preview,
            com.coinepro.app.R.layout.widget_markets_preview,
            com.coinepro.app.R.layout.widget_news_preview,
        ).forEach { layout ->
            val view = inflate(android.widget.RemoteViews(context.packageName, layout))
            assertTrue("preview $layout is blank", texts(view).count(String::isNotBlank) >= 3)
        }
    }
}

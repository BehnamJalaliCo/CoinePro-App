package com.coinepro.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.coinepro.app.MainActivity
import com.coinepro.app.R
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.WidgetMarket
import com.coinepro.core.datastore.WidgetSnapshot

/**
 * The markets widget.
 *
 * ### What it is for
 *
 * The prices a reader watches, on their home screen, without opening anything. In a corpus of
 * reviews of this category of app it is one of the most-requested things that no Persian-market
 * trading app ships — and the reason it is worth building rather than a nice extra is that it is
 * the only surface of this product somebody sees *without deciding to*. A widget that is right
 * every time they unlock their phone is worth more attention than a screen they visit weekly.
 *
 * ### It follows the watchlist rather than being configured
 *
 * There is no configuration activity, deliberately. A reader who has already starred the markets
 * they care about has answered this question, and asking it again — in a different screen, with a
 * different list that can drift out of step — is the sort of duplication that ends with two
 * watchlists nobody trusts. Star a market in the app and it is on the home screen.
 *
 * ### The three processes
 *
 * A widget is drawn by the *launcher's* process from a [RemoteViews] tree. This provider runs in a
 * broadcast receiver with about five seconds and no scope worth the name: it cannot open a socket,
 * wait for a quote and build a view. So the work is split — [WidgetRefreshWorker] fetches and
 * writes a [WidgetSnapshot], this provider renders whatever is there, and the launcher draws it.
 * That is how every reliable widget on Android is built and the split is why this one does not
 * show a spinner for ever on a slow network.
 */
class MarketsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // The provider does not fetch. It asks for a fetch and renders what is already stored, so
        // the widget has something on it within the receiver's few seconds rather than after the
        // network answers.
        WidgetRefreshWorker.requestNow(context)
        WidgetRenderer.renderAll(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        // A resize. `WidgetLayout` decides everything from the new size, so re-rendering is the
        // whole of the response — no refetch, because the prices did not change when the reader
        // dragged a corner.
        WidgetRenderer.renderAll(context, manager, intArrayOf(id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            WidgetRefreshWorker.requestNow(context)
            val manager = AppWidgetManager.getInstance(context)
            WidgetRenderer.renderAll(context, manager, manager.widgetIds(context))
        }
    }

    override fun onEnabled(context: Context) {
        // The first widget placed. Start the periodic refresh; it is cancelled again in `onDisabled`
        // when the last one is removed, so a reader with no widget pays nothing for this feature.
        WidgetRefreshWorker.schedule(context)
        WidgetRefreshWorker.requestNow(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshWorker.cancel(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.coinepro.app.widget.REFRESH"

        /** Every placed instance of this widget. */
        fun AppWidgetManager.widgetIds(context: Context): IntArray =
            getAppWidgetIds(ComponentName(context, MarketsWidget::class.java))

        /**
         * Redraw every placed widget.
         *
         * Called from the worker after a fetch, and from the app when the watchlist changes — a
         * reader who stars a market should not have to wait a refresh cycle to see it appear.
         */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            WidgetRenderer.renderAll(context, manager, manager.widgetIds(context))
        }
    }
}

/**
 * Turns a [WidgetSnapshot] and a size into a [RemoteViews] tree.
 *
 * Separated from the provider because the provider is a broadcast receiver — an awkward thing to
 * reason about — and this is a pure-ish function from state to view. Everything it needs is passed
 * in.
 */
object WidgetRenderer {

    fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        // One read for every widget on the screen rather than one each: a reader with the same
        // widget in two sizes is common, and the snapshot is the same for both.
        val snapshot = WidgetSnapshotBridge.read(context)
        val colours = WidgetSnapshotBridge.colours(context)
        ids.forEach { id ->
            val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val layout = WidgetLayout.of(
                widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: DEFAULT_WIDTH_DP,
                heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: DEFAULT_HEIGHT_DP,
            )
            runCatching {
                manager.updateAppWidget(id, render(context, snapshot, layout, colours))
            }
        }
    }

    fun render(
        context: Context,
        snapshot: WidgetSnapshot,
        layout: WidgetLayout,
        colours: MarketColorScheme,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_markets)

        views.setViewVisibility(R.id.widget_header, layout.header.visibility())
        views.setViewVisibility(R.id.widget_footer, layout.footer.visibility())

        val freshness = WidgetFreshness.describe(
            context = context,
            capturedAtEpochMillis = snapshot.capturedAtEpochMillis,
            nowEpochMillis = System.currentTimeMillis(),
            stale = snapshot.stale,
        )
        views.setTextViewText(R.id.widget_freshness, freshness)
        views.setTextViewText(R.id.widget_footer, freshness)

        // The whole plate opens the app; the refresh glyph refetches. Two targets, and the larger
        // one is the one a thumb finds by accident — which should be the harmless one.
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh(context))

        val shown = snapshot.markets.take(layout.rows)
        if (shown.isEmpty()) {
            views.setViewVisibility(R.id.widget_rows, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_empty))
            return views
        }
        views.setViewVisibility(R.id.widget_rows, View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, View.GONE)

        ROW_IDS.forEachIndexed { index, rowId ->
            val market = shown.getOrNull(index)
            if (market == null) {
                views.setViewVisibility(rowId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            bindRow(context, views, rowId, market, layout, colours)
        }
        return views
    }

    private fun bindRow(
        context: Context,
        views: RemoteViews,
        rowId: Int,
        market: WidgetMarket,
        layout: WidgetLayout,
        colours: MarketColorScheme,
    ) {
        views.setTextViewText(R.id.row_symbol, market.symbol)
        views.setTextViewText(R.id.row_name, market.name)
        views.setViewVisibility(R.id.row_name, layout.names.visibility())
        views.setTextViewText(R.id.row_price, market.priceText)
        views.setTextViewText(R.id.row_change, market.changeText)
        views.setTextColor(R.id.row_change, context.getColor(market.direction.colourFor(colours)))
        // The row opens that market's chart. `PendingIntent` needs a distinct request code *and* a
        // distinct data URI per row, or Android reuses one intent for all of them and every row
        // opens whichever was created first.
        views.setOnClickPendingIntent(rowId, openSymbol(context, market.symbol))
    }

    /**
     * Which colour a direction takes, under the reader's own convention.
     *
     * Read at render time rather than stored with the price, because it is a *display* choice and
     * the snapshot is data. A reader who flips the setting sees every widget change on the next
     * draw without a refetch.
     */
    private fun Int.colourFor(colours: MarketColorScheme): Int {
        val risingIsGreen = colours == MarketColorScheme.GREEN_UP
        return when {
            this > 0 -> if (risingIsGreen) R.color.widget_buy else R.color.widget_sell
            this < 0 -> if (risingIsGreen) R.color.widget_sell else R.color.widget_buy
            else -> R.color.widget_text_muted
        }
    }

    private fun Boolean.visibility(): Int = if (this) View.VISIBLE else View.GONE

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openSymbol(context: Context, symbol: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            // The data is what makes this intent distinct. `PendingIntent` compares intents by
            // everything *except* their extras, so rows carrying only a different extra would all
            // resolve to the same pending intent — the classic widget bug where every row opens
            // the first market.
            .setData(Uri.parse("coinepro://market/${Uri.encode(symbol)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            REQUEST_SYMBOL_BASE + symbol.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refresh(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_REFRESH,
        Intent(context, MarketsWidget::class.java).setAction(MarketsWidget.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The eight rows the layout declares. See `widget_markets.xml` for why they are fixed. */
    private val ROW_IDS = intArrayOf(
        R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
        R.id.widget_row_4, R.id.widget_row_5, R.id.widget_row_6, R.id.widget_row_7,
    )

    /** What a launcher that reports no options is assumed to have given us: the target size. */
    private const val DEFAULT_WIDTH_DP = 250
    private const val DEFAULT_HEIGHT_DP = 180

    private const val REQUEST_OPEN = 1
    private const val REQUEST_REFRESH = 2
    private const val REQUEST_SYMBOL_BASE = 1_000
}

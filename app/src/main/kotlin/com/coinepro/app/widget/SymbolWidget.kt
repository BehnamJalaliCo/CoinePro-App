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
import com.coinepro.core.common.BrandConfig
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.WidgetMarket

/**
 * One market, as a tile — run Τ2, B7.
 *
 * ### Why a second widget rather than a smaller first one
 *
 * `MarketsWidget` answers «how is my list» and is worth twelve cells. This answers «where is
 * gold», which is a different question: one instrument, large enough to read across a room,
 * placed next to a clock. Reviews of this category ask for both and ask for them separately — and
 * on a home screen the two do not substitute, because a reader who wants one number does not want
 * to scan a list to find it.
 *
 * ### It is configured, and the first one is not
 *
 * `MarketsWidget` follows the watchlist deliberately: the reader has already answered «which
 * markets» by starring them, and asking again would create a second list that drifts. A
 * single-symbol tile has no such answer to inherit — «which *one*» is a question only this widget
 * can ask — so it has a configuration activity, and the ticker it is given is stored per widget id.
 *
 * ### Everything else is shared
 *
 * The same `WidgetSnapshot`, the same `WidgetRefreshWorker`, the same freshness sentence, the same
 * colour convention, the same deep link. Two widgets, one refresh: a second fetch schedule for the
 * same prices would be a second wake-up on somebody's battery for nothing.
 *
 * ### And it is RemoteViews, like the first
 *
 * Not Glance. See `docs/runs/RUN_T2/REPORT.md` §B7 for the argument, which is short: the existing
 * widget works, a Glance rewrite of a working widget is risk with no reader-visible gain, and
 * mixing the two toolkits would mean two ways of doing the same thing in one directory.
 */
class SymbolWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // As in `MarketsWidget`: ask for a fetch, render what is already stored. A broadcast
        // receiver has about five seconds and cannot wait for a network.
        WidgetRefreshWorker.requestNow(context)
        SymbolWidgetRenderer.renderAll(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        SymbolWidgetRenderer.renderAll(context, manager, intArrayOf(id))
    }

    /**
     * Forgets the ticker a removed tile was configured with.
     *
     * Without this the store grows by one entry per widget the reader ever placed, in a file read
     * whole on every launch — and Android reuses widget ids, so a new tile could inherit a
     * deleted one's market and open already showing something nobody chose.
     */
    override fun onDeleted(context: Context, ids: IntArray) {
        SymbolWidgetBridge.forget(context, ids)
    }

    override fun onDisabled(context: Context) {
        // Only when the *markets* widget is also gone. Two widgets share one schedule, and
        // cancelling it because one of them was removed would leave the other frozen.
        if (AppWidgetManager.getInstance(context).marketWidgetIds(context).isEmpty()) {
            WidgetRefreshWorker.cancel(context)
        }
    }

    companion object {

        /** Every placed instance of this widget. */
        fun AppWidgetManager.symbolWidgetIds(context: Context): IntArray =
            getAppWidgetIds(ComponentName(context, SymbolWidget::class.java))

        private fun AppWidgetManager.marketWidgetIds(context: Context): IntArray =
            getAppWidgetIds(ComponentName(context, MarketsWidget::class.java))

        /** Redraw every placed tile. Called from the worker after a fetch. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            SymbolWidgetRenderer.renderAll(context, manager, manager.symbolWidgetIds(context))
        }
    }
}

/**
 * Turns a snapshot, a configured ticker and a size into a [RemoteViews] tree.
 *
 * Separated from the provider for `WidgetRenderer`'s reason: a broadcast receiver is an awkward
 * thing to reason about, and this is a function from state to view with everything passed in.
 */
object SymbolWidgetRenderer {

    fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val snapshot = WidgetSnapshotBridge.read(context)
        val colours = WidgetSnapshotBridge.colours(context)
        val symbols = SymbolWidgetBridge.symbols(context)
        ids.forEach { id ->
            val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val layout = SymbolWidgetPick.layoutFor(
                widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: DEFAULT_WIDTH_DP,
                heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: DEFAULT_HEIGHT_DP,
            )
            val symbol = symbols[id]
            runCatching {
                manager.updateAppWidget(
                    id,
                    render(
                        context = context,
                        market = SymbolWidgetPick.marketFor(snapshot, symbol),
                        symbol = symbol,
                        freshness = WidgetFreshness.describe(
                            context = context,
                            capturedAtEpochMillis = snapshot.capturedAtEpochMillis,
                            nowEpochMillis = System.currentTimeMillis(),
                            stale = snapshot.stale,
                        ),
                        layout = layout,
                        colours = colours,
                    ),
                )
            }
        }
    }

    fun render(
        context: Context,
        market: WidgetMarket?,
        symbol: String?,
        freshness: CharSequence,
        layout: SymbolWidgetLayout,
        colours: MarketColorScheme,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_symbol)
        // The tile opens this market's chart, or the app where there is no market to open. The
        // data URI is what makes the intent distinct — see `WidgetRenderer.openSymbol` for the
        // classic bug it avoids.
        views.setOnClickPendingIntent(R.id.symbol_root, open(context, market?.symbol ?: symbol))

        if (market == null) {
            // The configured market is not in the snapshot: unstarred, dropped from the catalogue,
            // or never fetched. Saying so, rather than drawing whatever is first — a tile that
            // quietly starts showing a different instrument is worse than one that admits it
            // cannot find this one. See `SymbolWidgetPick`.
            views.setViewVisibility(R.id.symbol_ticker, View.GONE)
            views.setViewVisibility(R.id.symbol_name, View.GONE)
            views.setViewVisibility(R.id.symbol_price, View.GONE)
            views.setViewVisibility(R.id.symbol_change, View.GONE)
            views.setViewVisibility(R.id.symbol_freshness, View.GONE)
            views.setViewVisibility(R.id.symbol_missing, View.VISIBLE)
            views.setTextViewText(
                R.id.symbol_missing,
                context.getString(R.string.widget_symbol_missing, symbol.orEmpty()),
            )
            return views
        }

        views.setViewVisibility(R.id.symbol_missing, View.GONE)
        views.setViewVisibility(R.id.symbol_ticker, View.VISIBLE)
        views.setViewVisibility(R.id.symbol_price, View.VISIBLE)
        views.setTextViewText(R.id.symbol_ticker, market.symbol)
        views.setTextViewText(R.id.symbol_price, market.priceText)

        views.setViewVisibility(R.id.symbol_name, layout.name.visibility())
        views.setTextViewText(R.id.symbol_name, market.name)

        views.setViewVisibility(R.id.symbol_change, layout.change.visibility())
        views.setTextViewText(R.id.symbol_change, market.changeText)
        views.setTextColor(R.id.symbol_change, context.getColor(market.direction.colourFor(colours)))

        views.setViewVisibility(R.id.symbol_freshness, layout.freshness.visibility())
        views.setTextViewText(R.id.symbol_freshness, freshness)
        return views
    }

    /** The reader's own convention, read at render time. `WidgetRenderer`'s rule, unchanged. */
    private fun Int.colourFor(colours: MarketColorScheme): Int {
        val risingIsGreen = colours == MarketColorScheme.GREEN_UP
        return when {
            this > 0 -> if (risingIsGreen) R.color.widget_buy else R.color.widget_sell
            this < 0 -> if (risingIsGreen) R.color.widget_sell else R.color.widget_buy
            else -> R.color.widget_text_muted
        }
    }

    private fun Boolean.visibility(): Int = if (this) View.VISIBLE else View.GONE

    private fun open(context: Context, symbol: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!symbol.isNullOrBlank()) {
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse("${BrandConfig.SCHEME_PREFIX}market/${Uri.encode(symbol)}")
        }
        return PendingIntent.getActivity(
            context,
            // Hashed on the ticker so two tiles for two markets do not share one pending intent —
            // `PendingIntent` compares intents by everything except their extras.
            symbol.orEmpty().hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** What a launcher that reports no size is assumed to have given: the declared target. */
    private const val DEFAULT_WIDTH_DP = 160
    private const val DEFAULT_HEIGHT_DP = 110
}

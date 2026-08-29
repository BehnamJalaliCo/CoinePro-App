package com.coinepro.feature.screener

import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.MarketTickerTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Where the day's figures for the whole catalogue come from.
 *
 * ### A stream rather than a fetch, and a batch rather than a lookup
 *
 * Both halves of that shape are the point. A batch, because the route answers every market in one
 * request and an interface that asked symbol by symbol would quietly reintroduce the per-market cost
 * this exists to remove — the mistake would be invisible, since it would still work. A stream,
 * because the figures keep moving: the day's change is what the screener sorts by out of the box,
 * and a table read once at open would freeze that column while the price column beside it went on
 * ticking. The store behind it polls at the server's own cache interval, so following it costs
 * nothing beyond what is already being fetched for every other screen.
 *
 * ### The first emission is the platform's answer, whatever it is
 *
 * [ScreenerController] holds its candle pass until this flow has spoken once — see
 * `scheduleResolution` — so a source that stayed silent on a platform with no route would leave the
 * screener showing prices and nothing else, for ever. Every implementation therefore emits
 * something on every platform: the table, an empty map where there is no such route, and an empty
 * map again where the request failed.
 */
fun interface ScreenerTickerSource {
    /**
     * The day's figures by symbol, as they arrive.
     *
     * Never fails and never ends of its own accord; it ends when the collector is cancelled, which
     * is [ScreenerController.stop] and therefore the screen leaving the composition.
     */
    fun tickers(): Flow<Map<String, MarketTicker>>
}

/**
 * [ScreenerTickerSource] over the shared [MarketTickerStore].
 *
 * The store is what six screens read, so following it here means the screener's «تغییر روزانه»
 * column and the market list's ordering are the same numbers rather than two fetches that can
 * disagree by a poll.
 *
 * ### What the dropped values are
 *
 * The store's state exists before the store has ever loaded anything, and its table is then the
 * `Empty` singleton. Passing that on would tell the controller "the platform has answered, and the
 * answer is that it knows nothing", which is the one thing it must not hear before a request has
 * even been made: it would release the candle pass a moment before the table that makes it
 * unnecessary. So the identity of that singleton is the test for "nothing has completed yet", and
 * the drop ends the moment a load finishes — including a load that failed, and including one that
 * succeeded with no rows in it, because both of those are answers.
 *
 * ### Reference counting
 *
 * [MarketTickerStore.start] and [MarketTickerStore.stop] are attached to the collection rather than
 * called by the controller, so the poll runs for exactly as long as somebody is reading it. A raised
 * count that never comes down is a five-second request against the whole catalogue for the rest of
 * the process; `onCompletion` runs on cancellation as well as on a normal end, which is what makes
 * closing the screen enough to lower it.
 */
class MarketTickerScreenerSource(private val store: MarketTickerStore) : ScreenerTickerSource {

    override fun tickers(): Flow<Map<String, MarketTicker>> {
        // One emission and then nothing, because there is nothing: CoinePro-FX has no such route,
        // and the honest answer is the one that lets the screener get on with reading candles.
        if (!store.supported) return flowOf(emptyMap())
        return store.state
            .dropWhile { it.table === MarketTickerTable.Empty && !it.failed }
            .map { it.table.tickers }
            // The store republishes its state to flag a load starting as well as one finishing, and
            // a poll that returns the same figures is the common case at five-second intervals.
            // Without this the whole table would be rebuilt and re-sorted for a table that had not
            // changed. Identical instances compare in constant time, so this costs nothing in the
            // case it fires most often.
            .distinctUntilChanged()
            .onStart { store.start() }
            .onCompletion { store.stop() }
    }
}

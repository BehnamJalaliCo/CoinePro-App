package com.coinepro.core.marketdata

import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import java.time.ZoneId

/**
 * Candles from whichever platform carries the symbol, not whichever tab is open (5.24.3).
 *
 * One watchlist spans both backends, so on the crypto tab it holds EURUSD and gold too — and every
 * one of them was asked of TradeYar, which answers a forex symbol with `422 TYR-021`. Seven rows of
 * the watchlist and eleven of the market list drew no spark line and no change, and EURUSD's chart
 * read «چارت بارگیری نشد». Crypto goes to [crypto], everything else to [forex]; the venue name and
 * limits a caller reads without a symbol are [crypto]'s or [forex]'s by whichever is [primary].
 */
class SymbolRoutedCandleGateway(
    private val crypto: CandleGateway,
    private val forex: CandleGateway,
    private val primary: CandleGateway = crypto,
) : CandleGateway {

    override val sourceName: String get() = primary.sourceName
    override val nativeTimeframes: List<Timeframe> get() = primary.nativeTimeframes
    override val sourceLimitMax: Int get() = primary.sourceLimitMax

    /** The gateway that carries [symbol]. */
    fun forSymbol(symbol: String): CandleGateway =
        if (SymbolClassifier.classify(symbol).category == SymbolCategory.CRYPTO) crypto else forex

    override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage =
        forSymbol(symbol).load(symbol, timeframe, limit, before)

    override suspend fun load(
        symbol: String,
        interval: ChartInterval,
        limit: Int,
        before: Long?,
        zone: ZoneId,
    ): CandlePage = forSymbol(symbol).load(symbol, interval, limit, before, zone)
}

/**
 * CoinePro-FX's chart token, falling back to the guest credential (5.24.3).
 *
 * The academy token is minted from a forex session. A guest has none, and neither does somebody
 * signed in to the crypto platform only — so their gold and majors had no candles at all. The
 * guest scope opens exactly the chart routes (see [GuestTokenStore]), which is all candles need.
 * After a refused mint the guest token is used for [RETRY_MS] before the session is asked again,
 * so a reader with no forex account costs one failed request every few minutes, not one a chart.
 */
class FallbackAcademyTokens(
    private val session: AcademyTokenStore,
    private val guest: GuestTokenStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /**
     * Whether there is a forex session to ask with (5.27.0). Without one the session store's mint is
     * a 401 on every opening — the pro-chart.com check's `academy-token` 401 for a guest — so the
     * guest credential is used straight away.
     */
    private val hasSession: () -> Boolean = { true },
) : AcademyTokenStore {

    @Volatile
    private var guestUntil: Long = 0

    override suspend fun token(): String {
        if (!hasSession() || nowMillis() < guestUntil) return guest.token()
        return try {
            session.token()
        } catch (error: AcademyDisabledException) {
            throw error
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            guestUntil = nowMillis() + RETRY_MS
            guest.token()
        }
    }

    override fun clear() {
        guestUntil = 0
        session.clear()
        guest.clear()
    }

    private companion object {
        // A minute, not five (5.26.0): the shell clears the session store on a forex sign-in, not
        // this wrapper, so a reader who signs in during the window was on guest candles that long.
        const val RETRY_MS = 60 * 1_000L
    }
}

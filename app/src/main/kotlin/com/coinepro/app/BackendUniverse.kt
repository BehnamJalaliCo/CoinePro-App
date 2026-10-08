package com.coinepro.app

import com.coinepro.core.auth.PlatformCapabilities
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CoineProFxCandleGateway
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.feature.heatmap.HeatmapUniverse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The markets each backend has bars for, asked once each (5.25.3).
 *
 * Crypto is held to TradeYar's own chart list — the one that keeps the guest screener to the
 * markets it can draw — and everything else to CoinePro-FX's `academy/chart/symbols?with_data=1`.
 * Each side only ever judges its own symbols: a backend that sent no list rules nothing out, and
 * its silence never empties the other's half of the map.
 */
internal class BackendUniverse(
    private val capabilities: PlatformCapabilities,
    private val forex: CandleGateway?,
) : HeatmapUniverse {

    private val lock = Mutex()
    private var crypto: Set<String>? = null
    private var forexSymbols: Set<String>? = null

    override suspend fun allows(symbol: String): Boolean {
        load()
        val list = if (SymbolClassifier.classify(symbol).category == SymbolCategory.CRYPTO) crypto else forexSymbols
        return list.isNullOrEmpty() || symbol.uppercase() in list
    }

    /**
     * Each list is kept once it has arrived, and asked for again while it has not (5.26.0). The
     * first version marked both as asked before either loaded, so a cancelled or failed first read
     * left the filter open — and the 404s back — until the app restarted.
     */
    private suspend fun load() = lock.withLock {
        if (crypto == null) {
            crypto = capabilities.chartableSymbols(MarketPlatform.TRADEYAR)?.map { it.uppercase() }?.toSet()
        }
        if (forexSymbols == null) {
            forexSymbols = try {
                (forex as? CoineProFxCandleGateway)?.symbolsWithBars()?.toSet()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            }
        }
    }
}

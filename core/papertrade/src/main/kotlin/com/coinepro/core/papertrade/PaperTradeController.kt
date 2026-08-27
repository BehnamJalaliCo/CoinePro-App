package com.coinepro.core.papertrade

import com.coinepro.core.database.PaperTradeDao
import com.coinepro.core.database.PaperTradeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaperTradeUiState(
    val open: List<PaperTradeEntity> = emptyList(),
    val closed: List<PaperTradeEntity> = emptyList(),
    val record: PaperTrading.Record = PaperTrading.Record(0, 0, 0.0),
)

class PaperTradeController(
    private val dao: PaperTradeDao,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val state: StateFlow<PaperTradeUiState> = dao.trades().map { trades ->
        PaperTradeUiState(
            open = trades.filter { it.exit == null },
            closed = trades.filter { it.exit != null },
            record = PaperTrading.record(trades),
        )
    }.stateIn(scope, SharingStarted.Eagerly, PaperTradeUiState())

    /**
     * Opens a position at the price the reader is looking at.
     *
     * The price is passed in rather than fetched here, and that is the honest version: the entry
     * is whatever the screen was showing at the moment of the tap, which is the same thing a real
     * market order gets. Fetching a fresh price inside would fill at a number the reader never saw.
     */
    fun open(symbol: String, buy: Boolean, price: Double, size: Double) {
        val ticker = symbol.trim().uppercase()
        if (ticker.isEmpty() || !price.isFinite() || price <= 0 || !size.isFinite() || size <= 0) return
        scope.launch {
            dao.insert(
                PaperTradeEntity(
                    symbol = ticker,
                    buy = buy,
                    entry = price,
                    size = size,
                    openedAtEpochMillis = now(),
                ),
            )
        }
    }

    /** Closes at the price on screen. Refused on an already-closed trade rather than overwriting. */
    fun close(trade: PaperTradeEntity, price: Double) {
        if (trade.exit != null || !price.isFinite()) return
        scope.launch {
            dao.update(trade.copy(exit = price, closedAtEpochMillis = now()))
        }
    }

    fun clear() {
        scope.launch { dao.clear() }
    }
}

package com.coinepro.core.signals

import java.time.Instant

private const val HISTORY_PAGE_SIZE = 50
private const val MAX_HISTORY_RECORDS = 1000

class SignalHistoryLoader(
    private val gateway: SignalGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun load(): CachedSignalHistory {
        val forex = loadMarket(SignalMarketFilter.FOREX)
        val crypto = loadMarket(SignalMarketFilter.CRYPTO)
        val combined = (forex.items + crypto.items)
            .distinctBy(TradingSignal::id)
            .sortedWith(compareByDescending<TradingSignal> { historyInstant(it) }.thenByDescending(TradingSignal::id))
        val expected = forex.expectedTotal + crypto.expectedTotal
        return CachedSignalHistory(
            items = combined,
            expectedTotal = expected,
            coverageComplete = forex.complete && crypto.complete && combined.size >= expected,
            cachedAtEpochMillis = nowMillis(),
        )
    }

    private suspend fun loadMarket(market: SignalMarketFilter): HistoryLoad {
        val items = mutableListOf<TradingSignal>()
        var offset = 0
        var expectedTotal = 0
        var complete = true

        do {
            val page = gateway.list(
                market = market,
                status = SignalStatusFilter.CLOSED,
                limit = HISTORY_PAGE_SIZE,
                offset = offset,
            )
            expectedTotal = page.total.coerceAtLeast(0)
            items += page.items

            if (page.items.isEmpty()) {
                complete = offset >= expectedTotal
                break
            }

            offset += HISTORY_PAGE_SIZE
            if (items.size >= MAX_HISTORY_RECORDS && offset < expectedTotal) {
                complete = false
                break
            }
        } while (offset < expectedTotal)

        return HistoryLoad(
            items = items.take(MAX_HISTORY_RECORDS),
            expectedTotal = expectedTotal,
            complete = complete && offset >= expectedTotal,
        )
    }

    private data class HistoryLoad(
        val items: List<TradingSignal>,
        val expectedTotal: Int,
        val complete: Boolean,
    )
}

private fun historyInstant(signal: TradingSignal): Instant? {
    val raw = signal.closedAt ?: signal.createdAt ?: return null
    return runCatching { Instant.parse(raw) }.getOrNull()
}

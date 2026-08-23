package com.coinepro.core.database

import com.coinepro.core.marketdata.CachedMarketSnapshot
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.CachedSignalHistory
import com.coinepro.core.signals.SignalEntryZone
import com.coinepro.core.signals.SignalHistoryCache
import com.coinepro.core.signals.SignalResult
import com.coinepro.core.signals.SignalScoreBreakdown
import com.coinepro.core.signals.SignalTarget
import com.coinepro.core.signals.TradingSignal

class RoomMarketDataCache(
    private val dao: CoineProCacheDao,
) : MarketDataCache {
    override suspend fun read(): CachedMarketSnapshot? {
        val metadata = dao.metadata(MARKET_METADATA_KEY) ?: return null
        if (metadata.cachedAtEpochMillis <= 0L) return null
        val quotes = dao.marketQuotes().mapNotNull(CachedMarketQuoteEntity::toDomain)
        if (quotes.isEmpty()) return null
        return CachedMarketSnapshot(quotes, metadata.cachedAtEpochMillis)
    }

    override suspend fun replace(quotes: List<MarketQuote>, cachedAtEpochMillis: Long) {
        if (cachedAtEpochMillis <= 0L) return
        val safe = quotes.mapNotNull(MarketQuote::toEntity)
        if (safe.isEmpty()) return
        dao.replaceMarketQuotes(
            items = safe,
            metadata = CacheMetadataEntity(
                key = MARKET_METADATA_KEY,
                expectedTotal = null,
                coverageComplete = null,
                cachedAtEpochMillis = cachedAtEpochMillis,
            ),
        )
    }

    override suspend fun clear() = dao.clearMarketCache()
}

class RoomSignalHistoryCache(
    private val dao: CoineProCacheDao,
) : SignalHistoryCache {
    override suspend fun read(): CachedSignalHistory? {
        val metadata = dao.metadata(SIGNAL_HISTORY_METADATA_KEY) ?: return null
        if (metadata.cachedAtEpochMillis <= 0L) return null
        val targetMap = dao.signalTargets().groupBy(CachedSignalTargetEntity::signalId)
        val signals = dao.signalHistory().mapNotNull { entity ->
            entity.toDomain(targetMap[entity.id].orEmpty())
        }
        if (signals.isEmpty() && (metadata.expectedTotal ?: 0) > 0) return null
        return CachedSignalHistory(
            items = signals,
            expectedTotal = metadata.expectedTotal?.coerceAtLeast(signals.size) ?: signals.size,
            coverageComplete = metadata.coverageComplete == true,
            cachedAtEpochMillis = metadata.cachedAtEpochMillis,
        )
    }

    override suspend fun replace(snapshot: CachedSignalHistory) {
        if (snapshot.cachedAtEpochMillis <= 0L) return
        val signals = snapshot.items.mapNotNull(TradingSignal::toEntity)
        val validIds = signals.map(CachedSignalEntity::id).toSet()
        val targets = snapshot.items
            .filter { it.id in validIds }
            .flatMap { signal -> signal.targets.mapNotNull { it.toEntity(signal.id) } }
        dao.replaceSignalHistory(
            signals = signals,
            targets = targets,
            metadata = CacheMetadataEntity(
                key = SIGNAL_HISTORY_METADATA_KEY,
                expectedTotal = snapshot.expectedTotal.coerceAtLeast(signals.size),
                coverageComplete = snapshot.coverageComplete && signals.size >= snapshot.expectedTotal,
                cachedAtEpochMillis = snapshot.cachedAtEpochMillis,
            ),
        )
    }

    override suspend fun clear() = dao.clearSignalHistoryCache()
}

internal fun MarketQuote.toEntity(): CachedMarketQuoteEntity? {
    if (!price.isFinite() || price <= 0.0 || timestampEpochMillis <= 0L) return null
    val safeBid = bid?.takeIf { it.isFinite() && it > 0.0 }
    val safeAsk = ask?.takeIf { it.isFinite() && it > 0.0 }
    val safeChange = changePercent?.takeIf(Double::isFinite)
    return CachedMarketQuoteEntity(
        symbol = instrument.symbol.trim().uppercase(),
        displayName = instrument.displayName,
        marketType = instrument.marketType.name,
        price = price,
        bid = safeBid,
        ask = safeAsk,
        changePercent = safeChange,
        source = source.name,
        sourceTimestampEpochMillis = timestampEpochMillis,
    )
}

internal fun CachedMarketQuoteEntity.toDomain(): MarketQuote? {
    val market = enumValueOrNull<MarketType>(marketType) ?: return null
    val sourceValue = enumValueOrNull<QuoteSource>(source) ?: QuoteSource.UNKNOWN
    val safeSymbol = symbol.trim().uppercase().takeIf { it.isNotEmpty() } ?: return null
    if (!price.isFinite() || price <= 0.0 || sourceTimestampEpochMillis <= 0L) return null
    return MarketQuote(
        instrument = Instrument(safeSymbol, displayName.ifBlank { safeSymbol }, market),
        price = price,
        bid = bid?.takeIf { it.isFinite() && it > 0.0 },
        ask = ask?.takeIf { it.isFinite() && it > 0.0 },
        changePercent = changePercent?.takeIf(Double::isFinite),
        timestampEpochMillis = sourceTimestampEpochMillis,
        source = sourceValue,
        isStale = true,
    )
}

internal fun TradingSignal.toEntity(): CachedSignalEntity? {
    if (id <= 0L || !isSupportedCachedSignal(market, symbol)) return null
    return CachedSignalEntity(
        id = id,
        market = market.name,
        symbol = symbol.trim().uppercase(),
        direction = direction.name,
        status = status,
        timeframe = timeframe,
        strategy = strategy,
        confidence = confidence?.coerceIn(0, 100),
        entry = entry.finiteOrNull(),
        entryZoneLow = entryZone?.low.finiteOrNull(),
        entryZoneHigh = entryZone?.high.finiteOrNull(),
        stopLoss = stopLoss.finiteOrNull(),
        riskRewardTp1 = riskRewardTp1?.takeIf { it.isFinite() && it > 0.0 },
        rationale = rationale,
        scoreTechnical = scoreBreakdown?.technical.finiteOrNull(),
        scorePattern = scoreBreakdown?.pattern.finiteOrNull(),
        scoreMl = scoreBreakdown?.ml.finiteOrNull(),
        closeReason = closeReason,
        resultPnlUsd = result?.pnlUsd.finiteOrNull(),
        resultSource = result?.source,
        createdAt = createdAt,
        closedAt = closedAt,
    )
}

internal fun SignalTarget.toEntity(signalId: Long): CachedSignalTargetEntity? {
    if (signalId <= 0L || level <= 0) return null
    return CachedSignalTargetEntity(
        signalId = signalId,
        level = level,
        price = price.finiteOrNull(),
        hit = hit,
    )
}

internal fun CachedSignalEntity.toDomain(targets: List<CachedSignalTargetEntity>): TradingSignal? {
    val marketValue = enumValueOrNull<MarketType>(market) ?: return null
    val directionValue = enumValueOrNull<SignalDirection>(direction) ?: return null
    val safeSymbol = symbol.trim().uppercase()
    if (id <= 0L || !isSupportedCachedSignal(marketValue, safeSymbol)) return null
    return TradingSignal(
        id = id,
        market = marketValue,
        symbol = safeSymbol,
        direction = directionValue,
        status = status,
        timeframe = timeframe,
        strategy = strategy,
        confidence = confidence?.coerceIn(0, 100),
        entry = entry.finiteOrNull(),
        entryZone = if (entryZoneLow != null || entryZoneHigh != null) {
            SignalEntryZone(entryZoneLow.finiteOrNull(), entryZoneHigh.finiteOrNull())
        } else {
            null
        },
        stopLoss = stopLoss.finiteOrNull(),
        targets = targets.mapNotNull(CachedSignalTargetEntity::toDomain).sortedBy(SignalTarget::level),
        riskRewardTp1 = riskRewardTp1?.takeIf { it.isFinite() && it > 0.0 },
        currentQuote = null,
        livePnlPercent = null,
        hitTarget = null,
        rationale = rationale,
        scoreBreakdown = if (scoreTechnical != null || scorePattern != null || scoreMl != null) {
            SignalScoreBreakdown(
                scoreTechnical.finiteOrNull(),
                scorePattern.finiteOrNull(),
                scoreMl.finiteOrNull(),
            )
        } else {
            null
        },
        closeReason = closeReason,
        result = if (resultPnlUsd != null || !resultSource.isNullOrBlank()) {
            SignalResult(resultPnlUsd.finiteOrNull(), resultSource)
        } else {
            null
        },
        createdAt = createdAt,
        closedAt = closedAt,
    )
}

internal fun CachedSignalTargetEntity.toDomain(): SignalTarget? {
    if (level <= 0) return null
    return SignalTarget(level = level, price = price.finiteOrNull(), hit = hit)
}

private fun isSupportedCachedSignal(market: MarketType, symbol: String): Boolean {
    val normalized = symbol.trim().uppercase()
    return when (market) {
        MarketType.FOREX -> normalized == "XAUUSD" || normalized == "XAGUSD"
        MarketType.CRYPTO -> normalized.endsWith("USDT") && normalized.length > 4
    }
}

private fun Double?.finiteOrNull(): Double? = this?.takeIf(Double::isFinite)

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    runCatching { enumValueOf<T>(value) }.getOrNull()

package com.coinepro.app

import com.coinepro.core.aisignal.AiCandle
import com.coinepro.core.aisignal.AiDirectionBias
import com.coinepro.core.aisignal.AiGeneratedSignal
import com.coinepro.core.aisignal.AiRiskAppetite
import com.coinepro.core.aisignal.AiSignalGateway
import com.coinepro.core.aisignal.AiSignalJobStatus
import com.coinepro.core.aisignal.AiSignalTarget
import com.coinepro.core.aisignal.AiSignalTimeframe
import com.coinepro.core.aisignal.AiTechnicalSnapshot
import com.coinepro.core.aisignal.AiTradeStyle
import com.coinepro.core.aisignal.AiSignalJob
import com.coinepro.core.aisignal.AiSignalQuota
import com.coinepro.core.aisignal.AiSignalRisk
import com.coinepro.core.aisignal.AiSignalRequest
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.execution.ExecutionGateway
import com.coinepro.core.execution.ExecutionStatus
import com.coinepro.core.execution.ExecutionVenue
import com.coinepro.core.execution.LbankPermission
import com.coinepro.core.execution.SignalExecution
import com.coinepro.core.execution.VenueConnection
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataOrigin
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelGateway
import com.coinepro.core.marketintel.MarketIntelSnapshot
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.model.SignalDirection
import com.coinepro.feature.home.HomeBriefing
import com.coinepro.feature.home.HomeHolding
import com.coinepro.feature.home.HomePortfolio
import com.coinepro.feature.home.HomeSignal
import com.coinepro.core.notifications.AppNotification
import com.coinepro.core.notifications.NotificationGateway
import com.coinepro.core.notifications.NotificationPage
import com.coinepro.core.notifications.PriceAlert
import com.coinepro.core.notifications.PriceAlertCondition
import com.coinepro.core.notifications.PriceAlertTrigger
import com.coinepro.core.notifications.PushPreferences
import com.coinepro.core.signals.SignalEntryZone
import com.coinepro.core.signals.SignalGateway
import com.coinepro.core.signals.SignalLiveQuote
import com.coinepro.core.signals.SignalMarketFilter
import com.coinepro.core.signals.SignalPage
import com.coinepro.core.signals.SignalResult
import com.coinepro.core.signals.SignalScoreBreakdown
import com.coinepro.core.signals.SignalStatusFilter
import com.coinepro.core.signals.SignalTarget
import com.coinepro.core.signals.TradingSignal
import java.time.Instant

/**
 * Deterministic sample data for the screenshot renders. The values are shaped like real server
 * payloads so the captures show populated screens instead of empty states, but nothing here reaches
 * production code.
 */
object ScreenshotFixtures {
    const val NOW_MILLIS: Long = 1_772_000_000_000L
    private val NOW: Instant = Instant.ofEpochMilli(NOW_MILLIS)

    /**
     * [platform] is required, not defaulted: a fixture that quietly spans both platforms would
     * render a screen the app can no longer produce, and the mixed watchlist is exactly the bug the
     * platform split exists to prevent.
     */
    fun marketState(platform: MarketPlatform = MarketPlatform.TRADEYAR): MarketDataState = MarketDataState(
        connection = MarketConnectionState.LIVE,
        origin = MarketDataOrigin.NETWORK,
        lastServerTimeEpochMillis = NOW_MILLIS,
        quotes = listOf(
            quote("XAUUSD", "Gold", MarketType.FOREX, 2_412.85, QuoteSource.FINNHUB, 0.62),
            quote("XAGUSD", "Silver", MarketType.FOREX, 30.42, QuoteSource.FINNHUB, -0.31),
            quote("BTCUSDT", "BTC", MarketType.CRYPTO, 91_248.30, QuoteSource.LBANK, 1.84),
            quote("ETHUSDT", "ETH", MarketType.CRYPTO, 3_104.77, QuoteSource.LBANK, -0.94),
            quote("BNBUSDT", "BNB", MarketType.CRYPTO, 612.05, QuoteSource.LBANK, 0.47),
            quote("SOLUSDT", "SOL", MarketType.CRYPTO, 184.62, QuoteSource.LBANK, 3.21),
            quote("XRPUSDT", "XRP", MarketType.CRYPTO, 0.6218, QuoteSource.LBANK, -1.12),
            quote("ADAUSDT", "ADA", MarketType.CRYPTO, 0.4471, QuoteSource.LBANK, 0.88),
        ).filter { it.instrument.marketType == platform.marketType }
            .associateBy { it.instrument.symbol },
    )

    /**
     * A briefing in the shape the server is being asked to return: one short paragraph of plain
     * text with the figures inline. The screen marks the figures up; it does not compose the
     * sentence.
     */
    val homeBriefing = HomeBriefing.Ready(
        body = "بیت‌کوین در شش ساعت گذشته 1.84% بالا رفت و از مقاومت 90,400 رد شد. " +
            "حجم خرید بالاتر از میانگین هفته است. سولانا با 3.21% رشد، بهترین دارایی پرتفوی توست.",
        ageLabel = "۴ دقیقه پیش",
    )

    val homePortfolio = HomePortfolio(
        totalLabel = MarketNumberFormatter.money(12_480.35),
        changeLabel = MarketNumberFormatter.money(261.40, signed = true) + " · " +
            MarketNumberFormatter.signedPercent(2.14) + " امروز",
        isUp = true,
        holdings = listOf(
            HomeHolding(
                symbol = "BTCUSDT",
                displayName = "بیت‌کوین",
                quantityLabel = MarketNumberFormatter.quantity(0.1482, "BTC"),
                valueLabel = MarketNumberFormatter.money(9_516.00),
                changeLabel = MarketNumberFormatter.signedPercent(1.82),
                isUp = true,
            ),
            HomeHolding(
                symbol = "ETHUSDT",
                displayName = "اتریوم",
                quantityLabel = MarketNumberFormatter.quantity(0.7400, "ETH"),
                valueLabel = MarketNumberFormatter.money(2_329.67),
                changeLabel = MarketNumberFormatter.signedPercent(-0.64),
                isUp = false,
            ),
            HomeHolding(
                symbol = "SOLUSDT",
                displayName = "سولانا",
                quantityLabel = MarketNumberFormatter.quantity(3.6800, "SOL"),
                valueLabel = MarketNumberFormatter.money(634.58),
                changeLabel = MarketNumberFormatter.signedPercent(4.10),
                isUp = true,
            ),
        ),
    )

    /** Crypto only, to match the crypto renders — a forex setup here would be the mixed screen. */
    val homeSignals: List<HomeSignal> = listOf(
        HomeSignal(
            id = 4821,
            title = "خرید BTCUSDT",
            entryLabel = MarketNumberFormatter.price(90_400.00),
            stopLabel = MarketNumberFormatter.price(88_900.00),
            targetLabel = MarketNumberFormatter.price(94_200.00),
            progressLabel = MarketNumberFormatter.signedPercent(0.18),
            isUp = true,
        ),
        HomeSignal(
            id = 4822,
            title = "فروش ETHUSDT",
            entryLabel = MarketNumberFormatter.price(3_140.00),
            stopLabel = MarketNumberFormatter.price(3_205.00),
            targetLabel = MarketNumberFormatter.price(3_010.00),
            progressLabel = MarketNumberFormatter.signedPercent(-0.42),
            isUp = false,
        ),
    )

    private fun quote(
        symbol: String,
        name: String,
        type: MarketType,
        price: Double,
        source: QuoteSource,
        changePercent: Double,
    ) = MarketQuote(
        instrument = Instrument(symbol, name, type),
        price = price,
        bid = price * 0.9997,
        ask = price * 1.0003,
        changePercent = changePercent,
        timestampEpochMillis = NOW_MILLIS - 1_500L,
        source = source,
        isStale = false,
    )

    val activeSignals: List<TradingSignal> = listOf(
        signal(
            id = 4821,
            market = MarketType.FOREX,
            symbol = "XAUUSD",
            direction = SignalDirection.BUY,
            status = "active",
            entry = 2_408.50,
            stop = 2_396.00,
            targets = listOf(2_421.0, 2_434.0, 2_452.0),
            confidence = 78,
            riskReward = 2.4,
            livePnl = 0.61,
            price = 2_412.85,
        ),
        signal(
            id = 4818,
            market = MarketType.CRYPTO,
            symbol = "BTCUSDT",
            direction = SignalDirection.SELL,
            status = "active",
            entry = 92_100.0,
            stop = 93_450.0,
            targets = listOf(90_600.0, 89_200.0),
            confidence = 64,
            riskReward = 1.8,
            livePnl = -0.42,
            price = 91_248.30,
        ),
        signal(
            id = 4809,
            market = MarketType.CRYPTO,
            symbol = "SOLUSDT",
            direction = SignalDirection.BUY,
            status = "active",
            entry = 179.40,
            stop = 172.80,
            targets = listOf(188.0, 196.5, 210.0),
            confidence = 71,
            riskReward = 3.1,
            livePnl = 2.91,
            price = 184.62,
        ),
    )

    val closedSignals: List<TradingSignal> = listOf(
        signal(
            id = 4790,
            market = MarketType.FOREX,
            symbol = "XAUUSD",
            direction = SignalDirection.BUY,
            status = "closed",
            entry = 2_381.0,
            stop = 2_370.0,
            targets = listOf(2_394.0, 2_408.0),
            confidence = 74,
            riskReward = 2.2,
            livePnl = null,
            price = null,
            closeReason = "TP1",
            pnlUsd = 148.20,
            targetsHit = 1,
        ),
        signal(
            id = 4776,
            market = MarketType.CRYPTO,
            symbol = "ETHUSDT",
            direction = SignalDirection.SELL,
            status = "closed",
            entry = 3_212.0,
            stop = 3_268.0,
            targets = listOf(3_150.0, 3_090.0),
            confidence = 58,
            riskReward = 1.6,
            livePnl = null,
            price = null,
            closeReason = "SL",
            pnlUsd = -62.40,
            targetsHit = 0,
        ),
        signal(
            id = 4761,
            market = MarketType.CRYPTO,
            symbol = "BTCUSDT",
            direction = SignalDirection.BUY,
            status = "closed",
            entry = 88_400.0,
            stop = 86_900.0,
            targets = listOf(90_200.0, 92_800.0),
            confidence = 81,
            riskReward = 2.9,
            livePnl = null,
            price = null,
            closeReason = "TP2",
            pnlUsd = 412.75,
            targetsHit = 2,
        ),
    )

    private fun signal(
        id: Long,
        market: MarketType,
        symbol: String,
        direction: SignalDirection,
        status: String,
        entry: Double,
        stop: Double,
        targets: List<Double>,
        confidence: Int,
        riskReward: Double,
        livePnl: Double?,
        price: Double?,
        closeReason: String? = null,
        pnlUsd: Double? = null,
        targetsHit: Int = 0,
    ) = TradingSignal(
        id = id,
        market = market,
        symbol = symbol,
        direction = direction,
        status = status,
        timeframe = if (market == MarketType.FOREX) "H4" else "H1",
        strategy = if (direction == SignalDirection.BUY) "Trend continuation" else "Range rejection",
        confidence = confidence,
        entry = entry,
        entryZone = SignalEntryZone(entry * 0.999, entry * 1.001),
        stopLoss = stop,
        targets = targets.mapIndexed { index, value ->
            SignalTarget(level = index + 1, price = value, hit = index < targetsHit)
        },
        riskRewardTp1 = riskReward,
        currentQuote = price?.let {
            SignalLiveQuote(
                price = it,
                bid = it * 0.9997,
                ask = it * 1.0003,
                timestampEpochMillis = NOW_MILLIS - 1_200L,
                source = if (market == MarketType.FOREX) QuoteSource.FINNHUB else QuoteSource.LBANK,
                isStale = false,
            )
        },
        livePnlPercent = livePnl,
        hitTarget = if (targetsHit > 0) "TP$targetsHit" else null,
        rationale = "Structure held above the prior demand block and momentum confirmed on the " +
            "execution timeframe. Invalidation stays below the swing low.",
        scoreBreakdown = SignalScoreBreakdown(technical = 0.72, pattern = 0.64, ml = 0.81),
        closeReason = closeReason,
        result = pnlUsd?.let { SignalResult(pnlUsd = it, source = "provider") },
        createdAt = NOW.minusSeconds(7_200).toString(),
        closedAt = closeReason?.let { NOW.minusSeconds(1_800).toString() },
    )

    val executions: List<SignalExecution> = listOf(
        SignalExecution(
            id = "exec_9f31",
            signalId = 4821,
            venue = ExecutionVenue.MT5,
            product = "XAUUSD",
            status = ExecutionStatus.OPEN,
            side = "buy",
            quantity = "0.20",
            providerOrderId = "MT5-8842119",
            errorCode = null,
            errorMessage = null,
            signal = null,
            createdAt = NOW.minusSeconds(5_400).toString(),
            updatedAt = NOW.minusSeconds(120).toString(),
            closedAt = null,
        ),
        SignalExecution(
            id = "exec_7c02",
            signalId = 4809,
            venue = ExecutionVenue.LBANK,
            product = "SOLUSDT",
            status = ExecutionStatus.QUEUED,
            side = "buy",
            quantity = "12.5",
            providerOrderId = null,
            errorCode = null,
            errorMessage = null,
            signal = null,
            createdAt = NOW.minusSeconds(240).toString(),
            updatedAt = NOW.minusSeconds(240).toString(),
            closedAt = null,
        ),
        SignalExecution(
            id = "exec_5ab8",
            signalId = 4761,
            venue = ExecutionVenue.MT5,
            product = "BTCUSDT",
            status = ExecutionStatus.CLOSED,
            side = "buy",
            quantity = "0.05",
            providerOrderId = "MT5-8839004",
            errorCode = null,
            errorMessage = null,
            signal = null,
            createdAt = NOW.minusSeconds(86_400).toString(),
            updatedAt = NOW.minusSeconds(3_600).toString(),
            closedAt = NOW.minusSeconds(3_600).toString(),
        ),
    )

    val connections: Pair<VenueConnection, VenueConnection> = VenueConnection(
        venue = ExecutionVenue.MT5,
        configured = true,
        connected = true,
        status = "connected",
        broker = "ICMarkets",
        server = "ICMarketsSC-Live12",
        loginMasked = "••••4471",
        lbankPermission = null,
        keyHint = null,
    ) to VenueConnection(
        venue = ExecutionVenue.LBANK,
        configured = true,
        connected = false,
        status = "awaiting_provider_confirmation",
        broker = null,
        server = null,
        loginMasked = null,
        lbankPermission = LbankPermission.SPOT,
        keyHint = "a91f…c4",
    )

    val notifications: List<AppNotification> = listOf(
        AppNotification(
            kind = "signal_new",
            title = "New XAUUSD signal",
            body = "BUY 2408.50 · SL 2396.00 · TP1 2421.00 · confidence 78%",
            data = mapOf("signal_id" to "4821"),
            timestampEpochMillis = NOW_MILLIS - 300_000L,
            read = false,
        ),
        AppNotification(
            kind = "signal_target",
            title = "SOLUSDT reached TP1",
            body = "Target 1 at 188.00 was hit. Position remains open toward TP2.",
            data = mapOf("signal_id" to "4809"),
            timestampEpochMillis = NOW_MILLIS - 1_800_000L,
            read = false,
        ),
        AppNotification(
            kind = "price_alert",
            title = "Gold crossed 2,400",
            body = "XAUUSD crossed above your 2400.00 alert level.",
            data = emptyMap(),
            timestampEpochMillis = NOW_MILLIS - 5_400_000L,
            read = true,
        ),
    )

    val alerts: List<PriceAlert> = listOf(
        PriceAlert(
            id = "alert_01",
            market = "forex",
            symbol = "XAUUSD",
            condition = PriceAlertCondition.CROSS,
            value = 2_400.0,
            trigger = PriceAlertTrigger.RECURRING,
            expiresAt = null,
            active = true,
            createdAtEpochMillis = NOW_MILLIS - 86_400_000L,
            lastTriggeredAtEpochMillis = NOW_MILLIS - 5_400_000L,
        ),
        PriceAlert(
            id = "alert_02",
            market = "crypto",
            symbol = "BTCUSDT",
            condition = PriceAlertCondition.BELOW,
            value = 88_000.0,
            trigger = PriceAlertTrigger.ONCE,
            expiresAt = null,
            active = true,
            createdAtEpochMillis = NOW_MILLIS - 172_800_000L,
            lastTriggeredAtEpochMillis = null,
        ),
    )

    fun marketIntel(): MarketIntelSnapshot = MarketIntelSnapshot(
        serverTime = NOW,
        news = listOf(
            MarketNewsItem(
                id = "news_1",
                title = "Dollar softens as traders price a slower tightening path",
                summary = "Treasury yields eased after the latest labour print, lifting precious metals.",
                source = "Reuters",
                url = "https://example.invalid/news/1",
                publishedAt = NOW.minusSeconds(1_800),
                sentiment = NewsSentiment.BULLISH,
                impact = MarketImpact.HIGH,
                relevance = setOf(MarketRelevance.GOLD, MarketRelevance.SILVER),
                isStale = false,
            ),
            MarketNewsItem(
                id = "news_2",
                title = "Spot Bitcoin ETFs post a fourth straight session of inflows",
                summary = "Net creations reached 412M USD, concentrated in two issuers.",
                source = "Bloomberg",
                url = "https://example.invalid/news/2",
                publishedAt = NOW.minusSeconds(5_400),
                sentiment = NewsSentiment.BULLISH,
                impact = MarketImpact.MEDIUM,
                relevance = setOf(MarketRelevance.CRYPTO),
                isStale = false,
            ),
            MarketNewsItem(
                id = "news_3",
                title = "Silver industrial demand forecast trimmed for the coming quarter",
                summary = null,
                source = "Kitco",
                url = "https://example.invalid/news/3",
                publishedAt = NOW.minusSeconds(21_600),
                sentiment = NewsSentiment.BEARISH,
                impact = MarketImpact.LOW,
                relevance = setOf(MarketRelevance.SILVER),
                isStale = true,
            ),
        ),
        calendar = listOf(
            EconomicEvent(
                id = "cal_1",
                title = "US Core CPI (MoM)",
                country = "United States",
                currency = "USD",
                scheduledAt = NOW.plusSeconds(9_000),
                impact = MarketImpact.HIGH,
                actual = null,
                forecast = "0.3%",
                previous = "0.2%",
                relevance = setOf(MarketRelevance.GOLD, MarketRelevance.CRYPTO),
                isStale = false,
            ),
            EconomicEvent(
                id = "cal_2",
                title = "FOMC Member Speech",
                country = "United States",
                currency = "USD",
                scheduledAt = NOW.plusSeconds(19_800),
                impact = MarketImpact.MEDIUM,
                actual = null,
                forecast = null,
                previous = null,
                relevance = setOf(MarketRelevance.GOLD),
                isStale = false,
            ),
            EconomicEvent(
                id = "cal_3",
                title = "Initial Jobless Claims",
                country = "United States",
                currency = "USD",
                scheduledAt = NOW.minusSeconds(3_600),
                impact = MarketImpact.MEDIUM,
                actual = "214K",
                forecast = "220K",
                previous = "219K",
                relevance = setOf(MarketRelevance.GOLD, MarketRelevance.SILVER),
                isStale = false,
            ),
        ),
    )

    val aiQuota = AiSignalQuota(remaining = 7, limit = 10, resetAt = NOW.plusSeconds(43_200).toString())

    /** A walk that trends up so the rendered candles look like real price action. */
    private val candles: List<AiCandle> = buildList {
        var price = 2_386.0
        repeat(28) { index ->
            val drift = 1.6 + (index % 5) * 0.4
            val open = price
            val close = open + if (index % 3 == 0) -drift else drift
            add(
                AiCandle(
                    open = open,
                    high = maxOf(open, close) + 1.9,
                    low = minOf(open, close) - 1.7,
                    close = close,
                ),
            )
            price = close
        }
    }

    val aiRequest = AiSignalRequest(
        symbol = "XAUUSD",
        timeframe = AiSignalTimeframe.H1,
        risk = AiSignalRisk.MEDIUM,
        tradeStyle = AiTradeStyle.INTRADAY,
        riskAppetite = AiRiskAppetite.BALANCED,
        directionBias = AiDirectionBias.AUTO,
    )

    val aiResult = AiGeneratedSignal(
        signalId = 5120L,
        symbol = "XAUUSD",
        direction = SignalDirection.BUY,
        timeframe = "H1",
        entry = 2_408.50,
        entryZone = null,
        stopLoss = 2_396.00,
        targets = listOf(
            AiSignalTarget(level = 1, price = 2_421.00),
            AiSignalTarget(level = 2, price = 2_434.00),
        ),
        confidence = 74,
        riskRewardTp1 = 2.4,
        rationale = "Price reclaimed the prior demand block and momentum confirmed on the execution " +
            "timeframe. Invalidation sits below the swing low.",
        validatedAt = NOW.toString(),
        lot = 0.18,
        strategy = "Trend continuation",
        warnings = listOf("Spread widens around the New York open; size accordingly."),
        snapshot = AiTechnicalSnapshot(
            ema20 = 2_404.10,
            ema50 = 2_397.60,
            ema200 = 2_371.25,
            rsi14 = 61.4,
            atr14 = 7.85,
            macd = 0.0142,
            bollingerUpper = 2_419.0,
            bollingerLower = 2_389.0,
            swingHigh20 = 2_421.4,
            swingLow20 = 2_384.2,
            changePercent20 = 1.12,
            priceNow = 2_412.85,
        ),
        recentCandles = candles,
    )

    val aiJob = AiSignalJob(
        id = "job_a91f",
        status = AiSignalJobStatus.DONE,
        request = aiRequest,
        result = aiResult,
        errorCode = null,
        errorMessage = null,
        quota = aiQuota,
        createdAt = NOW.minusSeconds(20).toString(),
        expiresAt = NOW.plusSeconds(600).toString(),
    )
}

class FakeSignalGateway : SignalGateway {
    override suspend fun list(
        market: SignalMarketFilter,
        status: SignalStatusFilter,
        limit: Int,
        offset: Int,
    ): SignalPage {
        val pool = when (status) {
            SignalStatusFilter.CLOSED -> ScreenshotFixtures.closedSignals
            else -> ScreenshotFixtures.activeSignals
        }
        val wanted = when (market) {
            SignalMarketFilter.FOREX -> MarketType.FOREX
            SignalMarketFilter.CRYPTO -> MarketType.CRYPTO
        }
        val items = if (offset > 0) emptyList() else pool.filter { it.market == wanted }
        return SignalPage(items, items.size, ScreenshotFixtures.NOW_MILLIS)
    }

    override suspend fun detail(signalId: Long): TradingSignal =
        (ScreenshotFixtures.activeSignals + ScreenshotFixtures.closedSignals)
            .first { it.id == signalId }
}

class FakeExecutionGateway : ExecutionGateway {
    override suspend fun connections(): Pair<VenueConnection?, VenueConnection?> =
        ScreenshotFixtures.connections.first to ScreenshotFixtures.connections.second

    override suspend fun connectMt5(broker: String, server: String, login: String, password: String) = Unit
    override suspend fun disconnectMt5() = Unit
    override suspend fun connectLbank(apiKey: String, apiSecret: String, permission: LbankPermission) = Unit
    override suspend fun disconnectLbank() = Unit

    override suspend fun executeSignal(
        signalId: Long,
        venue: ExecutionVenue,
        quantity: Double,
        clientRequestId: String,
    ): SignalExecution = ScreenshotFixtures.executions.first()

    override suspend fun executions(limit: Int): List<SignalExecution> = ScreenshotFixtures.executions
    override suspend fun execution(executionId: String): SignalExecution =
        ScreenshotFixtures.executions.first { it.id == executionId }

    override suspend fun requestClose(executionId: String): SignalExecution =
        ScreenshotFixtures.executions.first { it.id == executionId }
}

class FakeNotificationGateway : NotificationGateway {
    override suspend fun registerDevice(token: String, appVersion: String?, locale: String?) = true
    override suspend fun unregisterDevice(token: String) = true
    override suspend fun preferences() = PushPreferences()
    override suspend fun updatePreferences(preferences: PushPreferences) = preferences
    override suspend fun notifications(limit: Int) =
        NotificationPage(ScreenshotFixtures.notifications, unread = 2)

    override suspend fun markNotificationsRead() = Unit
    override suspend fun alerts(): List<PriceAlert> = ScreenshotFixtures.alerts

    override suspend fun createAlert(
        symbol: String,
        condition: PriceAlertCondition,
        value: Double,
        trigger: PriceAlertTrigger,
    ): PriceAlert = ScreenshotFixtures.alerts.first()

    override suspend fun setAlertActive(alertId: String, active: Boolean): PriceAlert =
        ScreenshotFixtures.alerts.first { it.id == alertId }.copy(active = active)

    override suspend fun deleteAlert(alertId: String) = true
}

class FakeMarketIntelGateway : MarketIntelGateway {
    override suspend fun snapshot(): MarketIntelSnapshot = ScreenshotFixtures.marketIntel()
}

class FakeAiSignalGateway(
    private val job: AiSignalJob? = null,
) : AiSignalGateway {
    override suspend fun quota(): AiSignalQuota = ScreenshotFixtures.aiQuota
    override suspend fun createJob(request: AiSignalRequest): AiSignalJob =
        job ?: throw IllegalStateException("not used by the screenshot render")

    override suspend fun job(jobId: String): AiSignalJob =
        job ?: throw IllegalStateException("not used by the screenshot render")
}

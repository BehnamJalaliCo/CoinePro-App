package com.coinepro.app

import com.coinepro.core.diagnostics.Appearance
import com.coinepro.core.diagnostics.ControlHub
import com.coinepro.core.diagnostics.FeedStatus
import com.coinepro.core.diagnostics.HubTone
import com.coinepro.core.diagnostics.PushPermission
import com.coinepro.core.diagnostics.PushStatus
import com.coinepro.core.diagnostics.ServerCapabilities
import com.coinepro.core.diagnostics.SessionRow
import com.coinepro.core.diagnostics.VenueStatus
import com.coinepro.core.diagnostics.AdminBuildInfo
import com.coinepro.core.diagnostics.AdminUiState
import com.coinepro.core.diagnostics.CatalogedEndpoint
import com.coinepro.core.diagnostics.EndpointProbe
import com.coinepro.core.diagnostics.PlatformBuildInfo
import com.coinepro.core.diagnostics.PlatformPanel
import com.coinepro.core.diagnostics.ProbeOutcome
import com.coinepro.core.diagnostics.RecordedRequest
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
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
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
import com.coinepro.core.copytrade.CopyAccount
import com.coinepro.core.copytrade.CopyBook
import com.coinepro.core.copytrade.CopyExecutionEvent
import com.coinepro.core.copytrade.CopyPosition
import com.coinepro.core.copytrade.CopyPreferences
import com.coinepro.core.copytrade.CopyTradeGateway
import com.coinepro.core.copytrade.CopyTradeStatus
import com.coinepro.core.designsystem.R as DesignR
import java.time.Instant
import com.coinepro.core.account.AccountBriefing
import com.coinepro.core.account.AccountGateway
import com.coinepro.core.account.AccountPortfolio
import com.coinepro.core.account.KycState
import com.coinepro.core.account.KycStatus
import com.coinepro.core.common.AppResult

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
            expiresAtEpochMillis = null,
            active = true,
            createdAtEpochMillis = NOW_MILLIS - 86_400_000L,
            lastTriggeredAtEpochMillis = NOW_MILLIS - 5_400_000L,
        ),
        PriceAlert(
            id = "alert_02",
            // Both alerts belong to the forex platform, because the notification surface is bound
            // to one backend at a time. A crypto pair beside gold here would be the render that
            // made the original mixing bug look intentional.
            market = "forex",
            symbol = "XAGUSD",
            condition = PriceAlertCondition.BELOW,
            value = 66.5,
            trigger = PriceAlertTrigger.ONCE,
            expiresAtEpochMillis = null,
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
    /**
     * A panel mid-diagnosis: one route answering, one alive but unauthenticated, and one that is
     * simply not there. The last is the state the whole panel exists to make visible.
     */
    val adminState: AdminUiState
        get() {
            val forexProbes = listOf(
                probe("GET", "user/auth/methods", "auth", ProbeOutcome.REACHED, 200),
                probe("GET", "user/mobile/portfolio", "home", ProbeOutcome.UNAUTHORIZED, 401),
                probe("GET", "user/mobile/alerts", "alerts", ProbeOutcome.UNAUTHORIZED, 401),
                probe("GET", "user/ai/assistant/messages", "ai", ProbeOutcome.NOT_FOUND, 404),
                probe("POST", "user/mobile/kyc/level1", "account", ProbeOutcome.SKIPPED, null),
            )
            return AdminUiState(
                build = AdminBuildInfo(
                    versionName = "1.0.0",
                    versionCode = "1",
                    environment = "staging",
                    applicationId = "com.coinepro.app.staging",
                    debuggable = false,
                    firebaseConfigured = true,
                ),
                selected = MarketPlatform.COINEPRO_FX,
                panels = mapOf(
                    MarketPlatform.COINEPRO_FX to PlatformPanel(
                        platform = MarketPlatform.COINEPRO_FX,
                        build = PlatformBuildInfo(MarketPlatform.COINEPRO_FX, "https://api.example.invalid/"),
                        probes = forexProbes,
                        installId = "…3f7a",
                    ),
                    MarketPlatform.TRADEYAR to PlatformPanel(
                        platform = MarketPlatform.TRADEYAR,
                        build = PlatformBuildInfo(MarketPlatform.TRADEYAR, "https://crypto.example.invalid/"),
                        installId = "…b210",
                    ),
                ),
                requests = listOf(
                    recorded(9, "GET", "user/mobile/portfolio", 200, 143),
                    recorded(8, "GET", "user/ai/assistant/messages", 404, 88),
                    recorded(7, "GET", "user/auth/methods", 200, 96),
                ),
            )
        }

    private fun probe(
        method: String,
        path: String,
        area: String,
        outcome: ProbeOutcome,
        status: Int?,
    ) = EndpointProbe(
        endpoint = CatalogedEndpoint(method, path, area, safeToProbe = method == "GET"),
        outcome = outcome,
        status = status,
        durationMillis = 120,
    )

    private fun recorded(
        sequence: Long,
        method: String,
        path: String,
        status: Int,
        duration: Long,
    ) = RecordedRequest(
        sequence = sequence,
        platform = MarketPlatform.COINEPRO_FX,
        method = method,
        path = path,
        status = status,
        durationMillis = duration,
        elapsedRealtimeMillis = 0,
    )


    /** A hub mid-life: signed in on one platform, feed live, push blocked by the reader. */
    val controlHub: ControlHub
        get() = ControlHub(
            sessions = listOf(
                SessionRow(MarketPlatform.COINEPRO_FX, signedIn = true),
                SessionRow(MarketPlatform.TRADEYAR, signedIn = false),
            ),
            feed = FeedStatus(
                tone = HubTone.GOOD,
                label = "لحظه‌ای",
                subscribedSymbols = 8,
                cacheAgeLabel = "۲ دقیقه پیش",
            ),
            push = PushStatus(
                permission = PushPermission.DENIED,
                serverEnabled = true,
                tokenHint = "…9c4d",
                priceAlerts = false,
            ),
            venue = VenueStatus(name = "MetaTrader 5", configured = true, connected = false),
            capabilities = mapOf(
                MarketPlatform.COINEPRO_FX to ServerCapabilities(
                    emailPassword = true,
                    google = true,
                    telegram = true,
                    push = true,
                    chartVision = true,
                    symbolCount = 2,
                ),
            ),
            appearance = Appearance(languageTag = "fa"),
        )


    /* -------------------------------------------------------------- copy trading */

    /**
     * A live CoinePro-FX copy account, as its `/user/copy-status` reports one.
     *
     * The execution event is real in shape and wording: the server assembles the Persian sentence,
     * the technical cause and the broker's return code into one string, and the screen prints it
     * exactly as sent.
     */
    val copyTradeLive = CopyTradeStatus(
        account = CopyAccount(
            broker = "OneRoyal",
            server = "OneRoyal-Live",
            loginMasked = "1••••89",
            status = "connected",
            lastError = null,
            alive = true,
            balance = 4821.5,
            equity = 4903.1,
            marginLevel = 312.0,
            floatingPnl = 81.6,
            openCount = 1,
            currency = "USD",
            lastSeen = Instant.parse("2026-08-25T09:12:04Z"),
        ),
        preferences = CopyPreferences(
            enabled = true,
            riskMode = "risk_percent",
            riskValue = 1.0,
            maxLot = 0.5,
            maxOpenTrades = 5,
            copyStopAndTargets = true,
            maxDailyLossPercent = 10.0,
            symbols = listOf("XAUUSD"),
        ),
        master = CopyBook(
            open = 2,
            positions = listOf(
                CopyPosition(symbol = "XAUUSD", direction = "buy", lots = 0.5, profit = 214.0),
            ),
        ),
        mirrored = listOf(
            CopyPosition(
                symbol = "XAUUSD",
                direction = "buy",
                lots = 0.05,
                profit = 21.4,
                stopLoss = 3312.4,
                signalId = 9114,
            ),
        ),
        mode = "live",
        accountMismatch = false,
        liveAccount = "1234589",
        events = listOf(
            CopyExecutionEvent(
                at = Instant.parse("2026-08-25T08:40:00Z"),
                signalId = 9114,
                code = "open_failed",
                outcome = "failed",
                symbol = "XAGUSD",
                message = "این سیگنال روی حسابِ شما اجرا نشد. " +
                    "(علتِ فنی: حجمِ درخواستی از حداقلِ بروکر کمتر است) [کد بروکر: 10014]",
            ),
        ),
        slotState = null,
    )

    /** Nothing linked yet: the form, and the warning that sits above it. */
    val copyTradeUnlinked = CopyTradeStatus(
        account = null,
        preferences = CopyPreferences(),
        master = CopyBook(),
        mirrored = emptyList(),
        mode = null,
        accountMismatch = false,
        liveAccount = null,
        events = emptyList(),
        slotState = null,
    )

    /**
     * Two hundred bars of a plausible market, deterministic so the screenshot never flickers.
     *
     * Not a smooth sine wave: a chart fixture has to have gaps, a run of doji, one violent bar and
     * a stretch of chop, because those are the shapes that expose a renderer. A clean wave would
     * make a broken wick or a collapsed body look fine.
     */
    fun chartSeries(bars: Int = 200): CandleSeries {
        var seed = 20_260_826L
        fun random(): Double {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return seed.toDouble() / 0x7FFFFFFF
        }
        val out = ArrayList<Candle>(bars)
        var price = 2_600.0
        for (index in 0 until bars) {
            // A trending stretch, then chop, then a shock — the three regimes a chart has to draw.
            val drift = when {
                index < bars / 3 -> 0.55
                index < bars * 2 / 3 -> 0.5
                else -> 0.44
            }
            val shock = if (index == bars * 3 / 4) -18.0 else 0.0
            val open = price
            val close = open + (random() - drift) * 9.0 + shock
            val wick = random() * 4.0
            out += Candle(
                t = 1_760_000_000L + index * 3600L,
                o = open,
                h = maxOf(open, close) + wick,
                l = minOf(open, close) - random() * 4.0,
                c = close,
                v = 800.0 + random() * 5_000.0,
            )
            price = close
        }
        return CandleSeries(out)
    }

    /** A setup on the fixture above: long, stop under the shock, two targets above. */
    fun chartSignal(series: CandleSeries): SignalOverlay {
        val entry = series.close.last()
        return SignalOverlay(
            entry = entry,
            stopLoss = entry - 26.0,
            takeProfits = listOf(entry + 42.0, entry + 68.0),
            isLong = true,
        )
    }

    /**
     * A catalogue the shape of a real one: majors, small caps, both asset classes, and the noise.
     *
     * Deliberately not a tidy list. Half the point of the ranking is that it copes with a feed that
     * arrives alphabetically and full of listings nobody has heard of, so a fixture sorted the way
     * the screen should end up would prove nothing.
     */
    fun searchCatalog(): MarketCatalogGateway {
        val symbols = listOf(
            "AAVEUSDT", "ADAUSDT", "ALGOUSDT", "APTUSDT", "ARBUSDT", "ATOMUSDT", "AVAXUSDT",
            "BCHUSDT", "BONKUSDT", "BTCUSDT", "DOGEUSDT", "DOTUSDT", "ETHUSDT", "FILUSDT",
            "GRTUSDT", "INJUSDT", "LINKUSDT", "LTCUSDT", "NEARUSDT", "ONDOUSDT", "OPUSDT",
            "PEPEUSDT", "QUACKUSDT", "RENDERUSDT", "SEIUSDT", "SHIBUSDT", "SOLUSDT", "SUIUSDT",
            "TIAUSDT", "TONUSDT", "TRXUSDT", "UNIUSDT", "WBTCUSDT", "WIFUSDT", "XLMUSDT",
            "XRPUSDT",
            "AUDUSD", "CADJPY", "CHFJPY", "EURAUD", "EURGBP", "EURJPY", "EURNZD", "EURUSD",
            "GBPJPY", "GBPUSD", "NZDUSD", "USDCAD", "USDCHF", "USDJPY", "USDTRY", "USDZAR",
            "XAGUSD", "XAUUSD", "XAUEUR", "XPTUSD",
            "US30", "US100", "US500", "GER40", "UK100", "JPN225",
            "USOIL", "UKOIL", "NATGAS",
        )
        val prices = mapOf(
            "BTCUSDT" to 91_248.30, "ETHUSDT" to 3_147.62, "SOLUSDT" to 172.45,
            "XAUUSD" to 2_643.18, "XAGUSD" to 30.94, "EURUSD" to 1.0842,
            "GBPUSD" to 1.2731, "USDJPY" to 156.28, "US500" to 5_918.40,
            "PEPEUSDT" to 0.000018, "DOGEUSDT" to 0.3914, "USOIL" to 71.62,
        )
        val changes = mapOf(
            "BTCUSDT" to 1.84, "ETHUSDT" to -0.64, "SOLUSDT" to 4.10,
            "XAUUSD" to 0.42, "EURUSD" to -0.18, "PEPEUSDT" to 7.31,
        )
        return object : MarketCatalogGateway {
            override suspend fun load(): MarketCatalog {
                val metas = symbols.map(SymbolClassifier::classify)
                return MarketCatalog(
                    markets = metas,
                    quotes = metas.mapNotNull { meta ->
                        val price = prices[meta.symbol] ?: return@mapNotNull null
                        meta.symbol to MarketQuote(
                            instrument = Instrument(
                                symbol = meta.symbol,
                                displayName = meta.short,
                                marketType = if (meta.category == SymbolCategory.CRYPTO) {
                                    MarketType.CRYPTO
                                } else {
                                    MarketType.FOREX
                                },
                            ),
                            price = price,
                            changePercent = changes[meta.symbol],
                            timestampEpochMillis = 1_787_670_872_000L,
                            source = QuoteSource.LBANK,
                            isStale = false,
                        )
                    }.toMap(),
                    serverTimeEpochMillis = 1_787_670_872_913L,
                )
            }
        }
    }

    /**
     * Every `tv_*` drawable, found by name rather than listed.
     *
     * Listed by hand it would drift the moment anyone added one, and the point of the render is to
     * catch the artwork nobody thought to look at.
     */
    fun tradingViewIconIds(context: android.content.Context): List<Int> {
        val fields = DesignR.drawable::class.java.fields
        return fields
            .filter { it.name.startsWith("tv_") }
            .sortedBy { it.name }
            .map { it.getInt(null) }
    }

    /**
     * The brand set paired with the Phosphor glyph it would replace, outline and fill each.
     *
     * Zero means the family has no counterpart for that meaning — the brand set covers sections
     * Phosphor answers with a generic shape, and Phosphor covers plenty the brand set never drew.
     */
    fun navIconComparison(): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> = listOf(
        (DesignR.drawable.nav_home to DesignR.drawable.nav_home_fill) to
            (DesignR.drawable.icon_house to DesignR.drawable.icon_filled_house),
        (DesignR.drawable.nav_signals to DesignR.drawable.nav_signals_fill) to
            (DesignR.drawable.icon_chart_line_up to DesignR.drawable.icon_filled_chart_line_up),
        (DesignR.drawable.nav_ai to DesignR.drawable.nav_ai_fill) to
            (DesignR.drawable.icon_sparkle to DesignR.drawable.icon_filled_sparkle),
        (DesignR.drawable.nav_tools to DesignR.drawable.nav_tools_fill) to
            (DesignR.drawable.icon_sliders_horizontal to DesignR.drawable.icon_filled_sliders_horizontal),
        (DesignR.drawable.nav_activity to DesignR.drawable.nav_activity_fill) to
            (DesignR.drawable.icon_bell to DesignR.drawable.icon_filled_bell),
    )

    fun brandIconComparison(): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
        return listOf(
            (DesignR.drawable.brand_home to DesignR.drawable.brand_home_fill) to (DesignR.drawable.icon_house to DesignR.drawable.icon_filled_house),
            (DesignR.drawable.brand_markets to DesignR.drawable.brand_markets_fill) to
                (DesignR.drawable.icon_chart_line_up to DesignR.drawable.icon_filled_chart_line_up),
            (DesignR.drawable.brand_signal to DesignR.drawable.brand_signal_fill) to (DesignR.drawable.icon_sparkle to DesignR.drawable.icon_filled_sparkle),
            (DesignR.drawable.brand_alert to DesignR.drawable.brand_alert_fill) to (DesignR.drawable.icon_bell to DesignR.drawable.icon_filled_bell),
            (DesignR.drawable.brand_grid to DesignR.drawable.brand_grid_fill) to
                (DesignR.drawable.icon_sliders_horizontal to DesignR.drawable.icon_filled_sliders_horizontal),
            (DesignR.drawable.brand_copy_trade to DesignR.drawable.brand_copy_trade_fill) to (DesignR.drawable.icon_link_simple to 0),
            (DesignR.drawable.brand_watchlist to DesignR.drawable.brand_watchlist_fill) to (DesignR.drawable.icon_eye to 0),
            (DesignR.drawable.brand_news to 0) to (DesignR.drawable.icon_newspaper to 0),
            (DesignR.drawable.brand_academy to DesignR.drawable.brand_academy_fill) to (DesignR.drawable.icon_info to 0),
            (DesignR.drawable.brand_wallet to 0) to (DesignR.drawable.icon_wallet to 0),
            (DesignR.drawable.brand_user to DesignR.drawable.brand_user_fill) to (DesignR.drawable.icon_gear_six to 0),
        )
    }
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

    override suspend fun markNotificationsRead() = 2
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

    override suspend fun job(jobId: String, request: AiSignalRequest): AiSignalJob =
        job ?: throw IllegalStateException("not used by the screenshot render")
}

/**
 * The account surface for the render fixtures.
 *
 * Verification defaults to not-started because that is the state every reader meets first, and the
 * one the screen has to be legible in before any other.
 */
internal class FakeCopyTradeGateway(
    private val status: CopyTradeStatus = ScreenshotFixtures.copyTradeLive,
) : CopyTradeGateway {
    override suspend fun status(): CopyTradeStatus = status
    override suspend fun setEnabled(enabled: Boolean): CopyPreferences =
        status.preferences.copy(enabled = enabled)
    override suspend fun linkAccount(broker: String, server: String, login: String, password: String) = Unit
    override suspend fun unlinkAccount() = Unit
}

internal class FakeAccountGateway(
    private val submitResult: AppResult<KycStatus>? = null,
    private val status: KycStatus = KycStatus(level = 1, state = KycState.NOT_STARTED),
) : AccountGateway {
    override suspend fun briefing(): AppResult<AccountBriefing?> = AppResult.Success(null)

    override suspend fun portfolio(): AppResult<AccountPortfolio> = AppResult.Success(AccountPortfolio())

    override suspend fun kyc(): AppResult<KycStatus> = AppResult.Success(status)

    override suspend fun submitKycLevel1(
        fullName: String,
        nationalId: String,
        birthDate: String,
        phone: String,
    ): AppResult<KycStatus> = submitResult ?: AppResult.Success(status)
}

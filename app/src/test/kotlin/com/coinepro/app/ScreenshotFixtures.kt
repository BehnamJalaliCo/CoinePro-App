package com.coinepro.app

import com.coinepro.core.account.AccountBriefing
import com.coinepro.core.account.AccountGateway
import com.coinepro.core.account.AccountPortfolio
import com.coinepro.core.account.DeletionOutcome
import com.coinepro.core.account.KycIdentity
import com.coinepro.core.account.KycState
import com.coinepro.core.account.KycStatus
import com.coinepro.core.aisignal.AiCandle
import com.coinepro.core.aisignal.AiDirectionBias
import com.coinepro.core.aisignal.AiGeneratedSignal
import com.coinepro.core.aisignal.AiRiskAppetite
import com.coinepro.core.aisignal.AiSignalGateway
import com.coinepro.core.aisignal.AiSignalJob
import com.coinepro.core.aisignal.AiSignalJobStatus
import com.coinepro.core.aisignal.AiSignalQuota
import com.coinepro.core.aisignal.AiSignalRequest
import com.coinepro.core.aisignal.AiSignalRisk
import com.coinepro.core.aisignal.AiSignalTarget
import com.coinepro.core.aisignal.AiSignalTimeframe
import com.coinepro.core.aisignal.AiTechnicalSnapshot
import com.coinepro.core.aisignal.AiTradeStyle
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.common.AppResult
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.copytrade.CopyAccount
import com.coinepro.core.copytrade.CopyBook
import com.coinepro.core.copytrade.CopyExecutionEvent
import com.coinepro.core.copytrade.CopyPosition
import com.coinepro.core.copytrade.CopyPreferences
import com.coinepro.core.copytrade.CopyTradeGateway
import com.coinepro.core.copytrade.CopyTradeStatus
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.diagnostics.AdminBuildInfo
import com.coinepro.core.diagnostics.AdminGateState
import com.coinepro.core.diagnostics.AdminUiState
import com.coinepro.core.diagnostics.CatalogedEndpoint
import com.coinepro.core.diagnostics.ControlHub
import com.coinepro.core.diagnostics.EndpointProbe
import com.coinepro.core.diagnostics.FeedStatus
import com.coinepro.core.diagnostics.HubTone
import com.coinepro.core.diagnostics.PlatformBuildInfo
import com.coinepro.core.diagnostics.PlatformPanel
import com.coinepro.core.diagnostics.ProbeOutcome
import com.coinepro.core.diagnostics.PushPermission
import com.coinepro.core.diagnostics.PushStatus
import com.coinepro.core.diagnostics.RecordedRequest
import com.coinepro.core.diagnostics.ServerCapabilities
import com.coinepro.core.diagnostics.SessionRow
import com.coinepro.core.diagnostics.VenueStatus
import com.coinepro.core.execution.ExecutionGateway
import com.coinepro.core.execution.ExecutionStatus
import com.coinepro.core.execution.ExecutionVenue
import com.coinepro.core.execution.LbankPermission
import com.coinepro.core.execution.SignalExecution
import com.coinepro.core.execution.VenueConnection
import com.coinepro.core.guest.CommunityChannel
import com.coinepro.core.guest.GuestCandles
import com.coinepro.core.guest.GuestCommunity
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.guest.GuestPrices
import com.coinepro.core.guest.GuestQuote
import com.coinepro.core.guest.GuestTrackRecord
import com.coinepro.core.guest.MemberCount
import com.coinepro.core.guest.MembershipTerms
import com.coinepro.core.guest.TrackRecordEntry
import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.MarketDataOrigin
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.feature.news.NewsStory
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
import com.coinepro.core.symbols.SymbolArtwork
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.feature.heatmap.HeatmapBarSource
import com.coinepro.feature.home.HomeBriefing
import com.coinepro.feature.home.HomeHolding
import com.coinepro.feature.home.HomePortfolio
import com.coinepro.feature.home.HomeSignal
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
        // A real-looking equity history — a rise with a drawdown in it, because a monotonic line
        // renders as a diagonal and proves nothing about how the sparkline handles a shape.
        equity = listOf(
            10_940.0, 11_120.0, 11_060.0, 11_390.0, 11_720.0, 11_450.0, 11_280.0,
            11_610.0, 11_980.0, 12_140.0, 11_890.0, 12_050.0, 12_310.0, 12_480.35,
        ),
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

    /**
     * The three live calls, with a P&L that agrees with their own prices.
     *
     * It did not, until the row started drawing it. XAUUSD said 0.61% where its entry and its quote
     * make 0.18%, and the BTC *short* said −0.42% where a fall from 92,100 to 91,248 is +0.92% —
     * a losing figure on a winning trade. Nobody caught it because no screen showed the number:
     * it arrived on the wire, sat in the model, and was drawn for the first time in this release,
     * directly above a progress bar computed from the prices, so the render now contradicted
     * itself in two places at once. Fixture figures that no screen reads are figures nobody checks.
     */
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
            livePnl = 0.18,
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
            // A short that has fallen is winning. Positive.
            livePnl = 0.92,
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

    /**
     * A story's own text, at the length one really arrives at.
     *
     * Taken from the shape `news_posts.body_fa` actually holds — four or five paragraphs of Persian
     * running to fifteen hundred characters — because the reading page's whole layout question is
     * how it sets *that*, not how it sets two lines. A one-paragraph stand-in would have made the
     * render agree with the page's easiest case and say nothing about its real one.
     */
    private val NEWS_BODY = """
        بازدهی اوراق خزانه‌ی آمریکا پس از انتشار تازه‌ترین گزارش بازار کار کاهش یافت و شاخص دلار در
        برابر سبد ارزهای اصلی عقب نشست. معامله‌گران با استناد به کندشدن رشد اشتغال، احتمال مسیر
        ملایم‌تری برای نرخ بهره در ماه‌های پیش‌رو را قیمت‌گذاری کردند.

        این تغییر انتظارات مستقیماً به سود فلزات گران‌بها تمام شد. طلا در معاملات روز جاری تا محدوده‌ی
        ۲٬۵۷۵ دلار بالا رفت و نقره هم مسیر مشابهی را دنبال کرد. دلیل این هم‌بستگی ساده است: هر دو فلز
        به دلار قیمت‌گذاری می‌شوند، پس ضعیف‌شدن دلار قیمتشان را برای خریدار غیرآمریکایی ارزان‌تر
        می‌کند و تقاضا را بالا می‌برد.

        نکته‌ای که تحلیل‌گران روی آن تأکید دارند این است که گزارش اشتغال به‌تنهایی تصمیم بانک مرکزی را
        تعیین نمی‌کند. داده‌های تورم هفته‌ی آینده و لحن اعضای فدرال‌رزرو در سخنرانی‌های پیشِ رو
        می‌تواند همین انتظارات را به‌سرعت برگرداند، و بازاری که امروز مسیر ملایم را قیمت کرده است
        بیشترین آسیب را از یک عدد تورمی بالاتر از انتظار می‌بیند.

        برای معامله‌گر ایرانی، چیزی که در این خبر بیش از خودِ عدد اهمیت دارد، ساعت انتشار داده‌ی بعدی
        است. تقویم اقتصادی همین برنامه رویدادهای پرتأثیر هفته را با ساعت دقیق نشان می‌دهد و می‌توان
        از هر ردیف مستقیم به نمودار همان لحظه رفت.
    """.trimIndent()

    /**
     * The same stories as [marketIntel], as the type the reading page takes.
     *
     * Built here rather than through `feature:news`'s own `asStory`, which is internal to that
     * module — deliberately, since nothing outside it should be converting a wire item into a
     * screen's model. This mapping is one for one and exists only so the render can reach the page.
     */
    fun newsStories(): List<NewsStory> = marketIntel().news.map { item ->
        NewsStory(
            id = item.id,
            title = item.title,
            summary = item.summary,
            body = item.body,
            source = item.source,
            url = item.url,
            imageUrl = item.imageUrl,
            publishedAt = item.publishedAt,
            sentiment = item.sentiment,
            impact = item.impact,
            relevance = item.relevance,
            isStale = item.isStale,
        )
    }

    fun marketIntel(): MarketIntelSnapshot = MarketIntelSnapshot(
        serverTime = NOW,
        // **Persian, with a body on the first one**, for the same reason the calendar below is
        // Persian: this render is the only view of the news screen anybody has, and until now it
        // showed three English headlines under a Persian heading — a picture of a product that does
        // not exist. Both feeds publish Persian. TradeYar's rows are `title_fa` and `summary_fa`
        // straight out of `news_posts`, and the forex side's are its own newsroom's.
        //
        // The first story carries a `body` because that is now an ordinary shape for a story rather
        // than the exceptional one: `NewsBodySource` fetches `body_fa` when a reader opens an
        // article, so the reading page has a page of prose on it and the render has to show that
        // rather than the two-line summary it used to be stuck with. The third deliberately carries
        // neither a summary nor a body, because a wire row with nothing but a headline is real and
        // the card has to be right for it.
        news = listOf(
            MarketNewsItem(
                id = "news_1",
                title = "دلار عقب نشست؛ بازار مسیر ملایم‌تری برای نرخ بهره قیمت‌گذاری می‌کند",
                summary = "بازدهی اوراق خزانه پس از تازه‌ترین گزارش اشتغال کاهش یافت و فلزات گران‌بها را بالا کشید.",
                source = "Reuters",
                url = "https://example.invalid/news/1",
                publishedAt = NOW.minusSeconds(1_800),
                sentiment = NewsSentiment.BULLISH,
                impact = MarketImpact.HIGH,
                relevance = setOf(MarketRelevance.GOLD, MarketRelevance.SILVER),
                isStale = false,
                body = NEWS_BODY,
            ),
            MarketNewsItem(
                id = "news_2",
                title = "ورود سرمایه به ETFهای نقدی بیت‌کوین برای چهارمین روز پیاپی ادامه یافت",
                summary = "خالص خرید تازه به ۴۱۲ میلیون دلار رسید و بیشترش در دو صندوق متمرکز بود.",
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
                title = "پیش‌بینی تقاضای صنعتی نقره برای فصل پیش‌رو کاهش یافت",
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
        // Persian, because that is what a reader is handed: `CalendarPersian` runs at the gateway
        // on **every** source, so an English title never survives as far as a screen. Writing the
        // wire's English here would have made this render lie about the app — which is exactly what
        // it did until the translation was extended past the published-file path.
        calendar = listOf(
            EconomicEvent(
                id = "cal_1",
                title = "شاخص قیمت مصرف‌کننده هسته‌ی آمریکا ماه‌به‌ماه",
                country = "آمریکا",
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
                title = "سخنرانی عضو فدرال‌رزرو",
                country = "آمریکا",
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
                title = "درخواست‌های اولیه‌ی بیمه‌ی بیکاری",
                country = "آمریکا",
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
                // Unlocked, because the render is of the panel. The lock screen has its own case
                // below — a fixture that left the gate closed would capture the door on every run
                // and quietly stop covering everything behind it.
                gate = AdminGateState(unlocked = true, provisioned = true),
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
    /**
     * A chart controller already holding bars, for rendering the screen without a network.
     *
     * The gateway hands back the same walk `chartSeries` draws, so a reader comparing the screen
     * render with the engine renders is looking at the same market.
     */
    /**
     * A script controller with an in-memory store.
     *
     * No Room, because the render only needs the editor's own state and standing up a database in
     * a screenshot test would make every capture wait on a schema migration.
     */
    fun scriptController(
        scope: kotlinx.coroutines.CoroutineScope,
    ): com.coinepro.core.script.ScriptController {
        val dao = object : com.coinepro.core.database.SavedScriptDao {
            private val rows =
                kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.coinepro.core.database.SavedScriptEntity>())

            override fun scripts() = rows
            override suspend fun byId(id: Long) = rows.value.firstOrNull { it.id == id }
            override suspend fun count() = rows.value.size
            override suspend fun insert(script: com.coinepro.core.database.SavedScriptEntity): Long {
                val id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1
                rows.value = rows.value + script.copy(id = id)
                return id
            }
            override suspend fun update(script: com.coinepro.core.database.SavedScriptEntity) {
                rows.value = rows.value.map { if (it.id == script.id) script else it }
            }
            override suspend fun delete(id: Long) {
                rows.value = rows.value.filterNot { it.id == id }
            }
        }
        return com.coinepro.core.script.ScriptController(dao, scope)
    }

    /**
     * A sparkline store served by a gateway that answers instantly from generated bars.
     *
     * The real store fetches, and a render test that waited on a network would capture the state
     * before the lines arrived — which is the one thing this screenshot exists to check.
     */
    fun sparklineStore(
        scope: kotlinx.coroutines.CoroutineScope,
    ): com.coinepro.core.marketdata.SparklineStore {
        val gateway = object : com.coinepro.core.marketdata.CandleGateway {
            override suspend fun load(
                symbol: String,
                timeframe: com.coinepro.core.marketdata.Timeframe,
                limit: Int,
                before: Long?,
            ): com.coinepro.core.marketdata.CandlePage {
                // Seeded off the ticker, so two rows never draw the same line.
                val seed = symbol.sumOf { it.code }
                val bars = (0 until limit).map { index ->
                    val wave = kotlin.math.sin((index + seed) / 5.0) * 3 +
                        kotlin.math.sin((index + seed) / 17.0) * 6 + index * 0.05
                    val close = 100.0 + wave
                    com.coinepro.core.marketdata.OhlcBar(
                        t = 1_700_000_000L + index * 3_600L,
                        o = close - 0.2,
                        h = close + 0.5,
                        l = close - 0.6,
                        c = close,
                        v = 1_000.0,
                    )
                }
                return com.coinepro.core.marketdata.CandlePage(
                    symbol = symbol,
                    timeframe = timeframe,
                    candles = bars,
                    hasMore = false,
                )
            }
        }
        return com.coinepro.core.marketdata.SparklineStore(gateway, scope)
    }

    /** A journal DAO holding three finished entries, so the screen renders with real rows. */
    fun journalController(
        scope: kotlinx.coroutines.CoroutineScope,
    ): com.coinepro.core.journal.JournalController {
        val rows = kotlinx.coroutines.flow.MutableStateFlow(
            listOf(
                com.coinepro.core.database.JournalEntryEntity(
                    id = 1, symbol = "XAUUSD", buy = true, entry = 2_640.0, exit = 2_662.5,
                    size = 0.2, pnl = 450.0, emotion = "صبور", note = "شکست سقف روزانه با حجم.",
                    lesson = "زودتر از تأیید وارد نشدم.", tags = "شکست",
                    createdAtEpochMillis = 1_756_000_000_000L,
                ),
                com.coinepro.core.database.JournalEntryEntity(
                    id = 2, symbol = "BTCUSDT", buy = false, entry = 92_400.0, exit = 93_180.0,
                    size = 0.05, pnl = -39.0, emotion = "عجول", note = "خلاف روند وارد شدم.",
                    lesson = "فیلتر روند را نادیده گرفتم.", tags = "خلاف‌روند",
                    createdAtEpochMillis = 1_755_900_000_000L,
                ),
                com.coinepro.core.database.JournalEntryEntity(
                    id = 3, symbol = "ETHUSDT", buy = true, entry = 3_080.0, exit = 3_142.0,
                    size = 1.0, pnl = 62.0, emotion = "آرام", note = "برگشت از حمایت هفتگی.",
                    lesson = "حد ضرر را جابه‌جا نکردم.", tags = "برگشت",
                    createdAtEpochMillis = 1_755_800_000_000L,
                ),
            ),
        )
        val dao = object : com.coinepro.core.database.JournalDao {
            override fun entries() = rows
            override suspend fun insert(entry: com.coinepro.core.database.JournalEntryEntity): Long {
                rows.value = rows.value + entry
                return entry.id
            }
            override suspend fun delete(entry: com.coinepro.core.database.JournalEntryEntity) {
                rows.value = rows.value.filterNot { it.id == entry.id }
            }
            override suspend fun clear() {
                rows.value = emptyList()
            }
        }
        return com.coinepro.core.journal.JournalController(dao, scope)
    }

    /** A paper-trade DAO with one open position and one closed, which is what the screen is for. */
    fun paperTradeController(
        scope: kotlinx.coroutines.CoroutineScope,
    ): com.coinepro.core.papertrade.PaperTradeController {
        val rows = kotlinx.coroutines.flow.MutableStateFlow(
            listOf(
                com.coinepro.core.database.PaperTradeEntity(
                    id = 1, symbol = "XAUUSD", buy = true, entry = 2_648.0, size = 0.2,
                    openedAtEpochMillis = 1_756_000_000_000L,
                ),
                com.coinepro.core.database.PaperTradeEntity(
                    id = 2, symbol = "BTCUSDT", buy = false, entry = 92_800.0, size = 0.05,
                    openedAtEpochMillis = 1_755_900_000_000L,
                    exit = 91_960.0, closedAtEpochMillis = 1_755_950_000_000L,
                ),
            ),
        )
        val dao = object : com.coinepro.core.database.PaperTradeDao {
            override fun trades() = rows
            override suspend fun insert(trade: com.coinepro.core.database.PaperTradeEntity): Long {
                rows.value = rows.value + trade
                return trade.id
            }
            override suspend fun update(trade: com.coinepro.core.database.PaperTradeEntity) {
                rows.value = rows.value.map { if (it.id == trade.id) trade else it }
            }
            override suspend fun clear() {
                rows.value = emptyList()
            }
        }
        return com.coinepro.core.papertrade.PaperTradeController(dao, scope)
    }

    fun chartController(
        scope: kotlinx.coroutines.CoroutineScope,
        symbol: String = "XAUUSD",
    ): com.coinepro.feature.chart.ChartController {
        val series = chartSeries()
        val gateway = object : com.coinepro.core.marketdata.CandleGateway {
            override suspend fun load(
                symbol: String,
                timeframe: com.coinepro.core.marketdata.Timeframe,
                limit: Int,
                before: Long?,
            ) = com.coinepro.core.marketdata.CandlePage(
                symbol = symbol,
                timeframe = timeframe,
                candles = series.bars.map {
                    com.coinepro.core.marketdata.OhlcBar(it.t, it.o, it.h, it.l, it.c, it.v ?: 0.0)
                },
                hasMore = true,
            )
        }
        return com.coinepro.feature.chart.ChartController(symbol, gateway, scope).also { it.start() }
    }

    fun chartSeries(bars: Int = 200, start: Double = 2_600.0): CandleSeries {
        var seed = 20_260_826L
        fun random(): Double {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return seed.toDouble() / 0x7FFFFFFF
        }
        val out = ArrayList<Candle>(bars)
        var price = start
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

    /**
     * The preview loader behind the signal screen, already holding bars.
     *
     * The walk starts where signal 4821's entry sits rather than at the chart screen's price, so
     * the setup band lands inside the bars instead of squashing two hundred candles into a strip
     * at the top of the card — which is what a fixture reused across two different markets does.
     */
    fun signalChartController(
        scope: kotlinx.coroutines.CoroutineScope,
    ): com.coinepro.feature.signaldetail.SignalChartController {
        val series = chartSeries(bars = 120, start = 2_386.0)
        val gateway = object : com.coinepro.core.marketdata.CandleGateway {
            override suspend fun load(
                symbol: String,
                timeframe: com.coinepro.core.marketdata.Timeframe,
                limit: Int,
                before: Long?,
            ) = com.coinepro.core.marketdata.CandlePage(
                symbol = symbol,
                timeframe = timeframe,
                candles = series.bars.map {
                    com.coinepro.core.marketdata.OhlcBar(it.t, it.o, it.h, it.l, it.c, it.v ?: 0.0)
                },
                hasMore = false,
            )
        }
        return com.coinepro.feature.signaldetail.SignalChartController(gateway, scope)
    }

    /**
     * A portfolio controller holding a month of closed trades.
     *
     * Two things are deliberate in the fixture. Every trade carries a balance, so the curve is the
     * real-account one and the drawdown percentage is offered — the branch that only exists on the
     * forex side. And the run is not monotone: it climbs, gives back a chunk, and recovers, because
     * a fixture that only goes up renders a straight line and tests nothing about the drawdown.
     */
    fun portfolioController(
        scope: kotlinx.coroutines.CoroutineScope,
    ): com.coinepro.core.portfolio.PortfolioController {
        var seed = 20_260_826L
        fun random(): Double {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return seed.toDouble() / 0x7FFFFFFF
        }
        val symbols = listOf("XAUUSD", "XAGUSD", "EURUSD", "GBPJPY")
        var balance = 42_000.0
        val closedAt = 1_787_751_459L
        val trades = (0 until 34).map { index ->
            // A losing stretch in the middle, so the curve has a real peak to fall from.
            val bias = if (index in 12..19) -0.62 else 0.34
            val profit = ((random() - 0.5 + bias) * 900.0)
            balance += profit
            com.coinepro.core.portfolio.ClosedTrade(
                id = "t$index",
                symbol = symbols[index % symbols.size],
                direction = if (index % 3 == 0) {
                    com.coinepro.core.portfolio.TradeDirection.SELL
                } else {
                    com.coinepro.core.portfolio.TradeDirection.BUY
                },
                volume = 0.08,
                entry = 2_380.0 + index * 1.4,
                // **The exit moves the way the trade's own direction requires.**
                //
                // It used to be `entry + profit / 80`, whatever the direction, so a fixture row
                // could read «فروش · 2,380.00 ← 2,386.85 · +$547.77» — a sell that made money on a
                // rising market. `ClosedTrade.netProfit` is the server's own figure and is
                // deliberately never derived from these two prices, so nothing in the app catches
                // it; it is only ever caught by somebody looking at the render, and what they
                // conclude is that the app has a sign error. A picture that invents a bug costs as
                // much time as one that hides a real one.
                exit = 2_380.0 + index * 1.4 +
                    (if (index % 3 == 0) -profit / 80.0 else profit / 80.0),
                // Spread across about three months rather than a week: the monthly card only
                // appears with more than one month in the window, and a fixture confined to one
                // would leave that branch unrendered in every screenshot.
                openedAt = closedAt - (34 - index) * 216_000L - 7_200L,
                closedAt = closedAt - (34 - index) * 216_000L,
                grossProfit = profit + 0.9,
                commission = -0.62,
                swap = -0.28,
                netProfit = profit,
                pips = profit / 8.0,
                closeReason = if (profit < 0) "sl" else "manual",
                balanceAfter = balance,
                currency = "USD",
            )
        }
        val gateway = object : com.coinepro.core.portfolio.PortfolioGateway {
            override suspend fun history(
                page: Int,
                perPage: Int,
                from: Long?,
                to: Long?,
            ) = com.coinepro.core.portfolio.TradeHistoryPage(
                trades = trades,
                page = 1,
                total = trades.size,
                hasMore = false,
            )
        }
        return com.coinepro.core.portfolio.PortfolioController(
            gateway = gateway,
            scope = scope,
            zone = java.time.ZoneOffset.UTC,
            nowSeconds = { closedAt },
        ).also { it.start() }
    }

    /**
     * An academy controller mid-course, with a locked level below the reader's tier.
     *
     * The fixture is deliberately not tidy: one level finished, one part-way, one locked behind a
     * subscription and one behind a missing phone number. All four states appear on the roadmap and
     * three of them look identical if the node styling is wrong.
     */
    fun academyController(
        scope: kotlinx.coroutines.CoroutineScope,
    ): com.coinepro.core.academy.AcademyController {
        fun lesson(
            slug: String,
            title: String,
            order: Int,
            completed: Boolean = false,
            locked: Boolean = false,
            reason: com.coinepro.core.academy.LockReason? = null,
            video: Boolean = false,
        ) = com.coinepro.core.academy.LessonSummary(
            slug = slug,
            title = title,
            order = order,
            tier = if (locked) "vip" else "free",
            locked = locked,
            lockReason = reason,
            completed = completed,
            hasVideo = video,
        )

        val catalog = com.coinepro.core.academy.AcademyCatalog(
            tier = "vip",
            levels = listOf(
                com.coinepro.core.academy.AcademyLevel(
                    key = "beginner",
                    name = "مقدماتی",
                    lessons = listOf(
                        lesson("what-is-forex", "بازار فارکس چیست", 1, completed = true),
                        lesson("pips", "پیپ و پیپت", 2, completed = true, video = true),
                        lesson("lots", "لات و اندازه‌ی معامله", 3, completed = true),
                        lesson("leverage", "اهرم و مارجین", 4),
                        lesson("spread", "اسپرد و کارمزد", 5, video = true),
                    ),
                ),
                com.coinepro.core.academy.AcademyLevel(
                    key = "intermediate",
                    name = "متوسط",
                    lessons = listOf(
                        lesson("candles", "الگوهای شمعی", 1),
                        lesson("rsi", "شاخص قدرت نسبی", 2),
                        lesson(
                            "fibonacci", "فیبوناچی اصلاحی", 3,
                            locked = true,
                            reason = com.coinepro.core.academy.LockReason.PHONE,
                        ),
                    ),
                ),
                com.coinepro.core.academy.AcademyLevel(
                    key = "advanced",
                    name = "پیشرفته",
                    lessons = listOf(
                        lesson(
                            "orderflow", "جریان سفارش", 1,
                            locked = true,
                            reason = com.coinepro.core.academy.LockReason.TIER,
                        ),
                        lesson(
                            "smc", "ساختار بازار", 2,
                            locked = true,
                            reason = com.coinepro.core.academy.LockReason.TIER,
                        ),
                    ),
                ),
            ),
        )
        val profile = com.coinepro.core.academy.AcademyProfile(
            username = "reza",
            fullName = "رضا کریمی",
            tier = "vip",
            phoneRequired = true,
            completed = 3,
            totalLessons = 10,
            progressPercent = 30.0,
            xp = 210,
            badges = listOf("first_lesson"),
            byLevel = listOf(
                com.coinepro.core.academy.LevelProgress("beginner", "مقدماتی", 3, 5, false),
                com.coinepro.core.academy.LevelProgress("intermediate", "متوسط", 0, 3, false),
                com.coinepro.core.academy.LevelProgress("advanced", "پیشرفته", 0, 2, false),
            ),
            quizzesTaken = 3,
            averageQuiz = 80,
            streak = com.coinepro.core.academy.Streak(6, 11, todayDone = true),
            achievementsCount = 1,
        )
        val body = com.coinepro.core.academy.Lesson(
            slug = "leverage",
            level = "beginner",
            title = "اهرم و مارجین",
            summary = "اهرم اندازه‌ی معامله را بزرگ می‌کند، نه احتمال درست‌بودن آن را.",
            content = "<p>اهرم به شما اجازه می‌دهد با سرمایه‌ی کم، معامله‌ای <b>بزرگ‌تر</b> باز کنید. " +
                "با اهرم ۱:۱۰۰، با ۱۰۰ دلار می‌توانید ۱۰٬۰۰۰ دلار معامله کنید.</p>" +
                "<p>مارجین آن بخشی از موجودی است که بروکر تا بسته‌شدن معامله نگه می‌دارد. " +
                "وقتی ضرر به مارجین برسد، <i>کال مارجین</i> می‌گیرید.</p>" +
                "<ul><li>اهرم سود را بزرگ می‌کند.</li><li>اهرم ضرر را هم به همان نسبت بزرگ می‌کند.</li>" +
                "<li>اندازه‌ی معامله را از ریسک بگیرید، نه از اهرم.</li></ul>",
            diagramImage = null,
            tier = "free",
            videoPath = null,
            videoDurationSeconds = null,
            readingTimeMinutes = 4,
            watermark = "reza",
        )
        val quiz = com.coinepro.core.academy.Quiz(
            slug = "leverage",
            questions = listOf(
                com.coinepro.core.academy.QuizQuestion(
                    id = 1,
                    question = "با اهرم ۱:۱۰۰ و ۲۰۰ دلار موجودی، بیشترین حجم قابل معامله چقدر است؟",
                    options = listOf("۲٬۰۰۰ دلار", "۲۰٬۰۰۰ دلار", "۲۰۰٬۰۰۰ دلار"),
                ),
                com.coinepro.core.academy.QuizQuestion(
                    id = 2,
                    question = "اهرم کدام‌یک را بزرگ می‌کند؟",
                    options = listOf("فقط سود", "سود و ضرر، هر دو", "احتمال درست‌بودن تحلیل"),
                ),
            ),
            lastScore = null,
        )
        val gateway = object : com.coinepro.core.academy.AcademyGateway {
            override suspend fun profile() = profile
            override suspend fun catalog() = catalog
            override suspend fun roadmap(level: String) =
                com.coinepro.core.academy.LevelRoadmap(level, null, emptyList())
            override suspend fun lesson(slug: String) = body
            override suspend fun complete(slug: String, quizScore: Int?) =
                com.coinepro.core.academy.ProgressResult(emptyList(), profile.streak)
            override suspend fun quiz(slug: String) = quiz
            override suspend fun submitQuiz(slug: String, answers: Map<Long, Int>) =
                com.coinepro.core.academy.QuizResult(
                    score = 50,
                    correct = 1,
                    total = 2,
                    passed = false,
                    answers = listOf(
                        com.coinepro.core.academy.QuizAnswer(
                            id = 1,
                            correctIndex = 1,
                            yourIndex = 1,
                            isCorrect = true,
                            explanation = "۲۰۰ × ۱۰۰ = ۲۰٬۰۰۰ دلار.",
                        ),
                        com.coinepro.core.academy.QuizAnswer(
                            id = 2,
                            correctIndex = 1,
                            yourIndex = 0,
                            isCorrect = false,
                            explanation = "اهرم به هر دو سمت اثر می‌گذارد؛ ضرر هم به همان نسبت بزرگ می‌شود.",
                        ),
                    ),
                )
            override suspend fun streak() = profile.streak
            override suspend fun achievements() =
                com.coinepro.core.academy.Achievements(emptyList(), 1, 11)
            override suspend fun leaderboard() =
                com.coinepro.core.academy.Leaderboard(emptyList(), 14, 312)
            override suspend fun glossary() = emptyList<com.coinepro.core.academy.GlossaryTerm>()
        }
        return com.coinepro.core.academy.AcademyController(gateway, scope)
    }

    /**
     * The academy's three side lists, all loaded.
     *
     * Badges deliberately mixed earned and unearned: a list showing only what has been achieved is
     * a list with nothing to aim at, and the dimmed rows are the half of the design that matters.
     * The leaderboard puts the reader mid-table rather than first, because first place is the one
     * arrangement where "your rank" and "the top row" cannot be told apart.
     */
    fun academyExtras(): com.coinepro.core.academy.AcademyExtrasState {
        fun badge(key: String, title: String, desc: String, icon: String, earned: Boolean) =
            com.coinepro.core.academy.Achievement(key, title, desc, icon, earned, null)

        return com.coinepro.core.academy.AcademyExtrasState(
            achievements = com.coinepro.core.academy.Achievements(
                items = listOf(
                    badge("first_lesson", "اولین قدم", "اولین درست را کامل کردی.", "🎯", true),
                    badge("lessons_10", "ده‌تایی", "۱۰ درس را کامل کردی.", "🔟", true),
                    badge("streak_7", "هفته‌ی آتشین", "۷ روزِ پیاپی فعال بودی.", "🔥", true),
                    badge("perfect_quiz", "نمره‌ی کامل", "در یک آزمون نمره‌ی ۱۰۰ گرفتی.", "⭐", false),
                    badge("lessons_50", "نیمه‌راه", "۵۰ درس را کامل کردی.", "🏃", false),
                    badge("streak_30", "ماراتن", "۳۰ روزِ پیاپی فعال بودی.", "🏅", false),
                    badge("level_master_beginner", "استادِ مقدماتی", "سطحِ مقدماتی را کامل کردی.", "🥉", false),
                ),
                earnedCount = 3,
                total = 11,
            ),
            leaderboard = com.coinepro.core.academy.Leaderboard(
                items = listOf(
                    com.coinepro.core.academy.LeaderboardRow(1, "sara_t", 1_420, 128, false),
                    com.coinepro.core.academy.LeaderboardRow(2, "amir", 1_180, 104, false),
                    com.coinepro.core.academy.LeaderboardRow(3, "n_moradi", 960, 88, false),
                    com.coinepro.core.academy.LeaderboardRow(4, "reza", 730, 61, true),
                    com.coinepro.core.academy.LeaderboardRow(5, "hesam", 640, 55, false),
                    com.coinepro.core.academy.LeaderboardRow(6, "—", 480, 41, false),
                ),
                myRank = 4,
                totalStudents = 312,
            ),
            glossary = listOf(
                com.coinepro.core.academy.GlossaryTerm(
                    "پیپ",
                    "کوچک‌ترین واحد استاندارد تغییر قیمت در جفت‌ارز، معمولاً چهارمین رقم اعشار.",
                    "Pip",
                ),
                com.coinepro.core.academy.GlossaryTerm(
                    "اسپرد",
                    "تفاوت بین بهترین قیمت خرید و بهترین قیمت فروش؛ هزینه‌ای که در لحظه‌ی ورود می‌دهی.",
                    "Spread",
                ),
                com.coinepro.core.academy.GlossaryTerm(
                    "مارجین",
                    "بخشی از موجودی که بروکر تا بسته‌شدن معامله نگه می‌دارد.",
                    "Margin",
                ),
                com.coinepro.core.academy.GlossaryTerm(
                    "دراودان",
                    "بیشترین افت سرمایه از یک قله تا کف بعدی آن.",
                    "Drawdown",
                ),
            ),
        )
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
    /**
     * The tickers the alert editor's symbol picker may offer.
     *
     * A short list rather than the whole catalogue: the render is checking the row and the sheet,
     * and a picker over four hundred symbols renders the same first screen as one over eight.
     */
    fun alertSymbols(): List<String> =
        listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD", "EURUSD", "GBPUSD", "XAGUSD", "USDJPY")

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
        val prices = SEARCH_PRICES
        val changes = mapOf(
            "BTCUSDT" to 1.84, "ETHUSDT" to -0.64, "SOLUSDT" to 4.10,
            "XAUUSD" to 0.42, "EURUSD" to -0.18, "PEPEUSDT" to 7.31,
        )
        return object : MarketCatalogGateway {
            override suspend fun load(): MarketCatalog {
                // The same filter the real gateway applies, and it is here rather than assumed
                // because the render is what the visual review looks at. Without it the sheet
                // showed US30, GER40, UK100 and JPN225 as lettered grey discs — a state the app
                // cannot produce, since `MarketCatalogGateway` drops anything without artwork —
                // and a screenshot that shows something the app cannot do is not a gate.
                val metas = symbols.map(SymbolClassifier::classify).filter(SymbolArtwork::covers)
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
     * Daily bars for the heat map, so the review sees a heat map rather than a wall of hatching.
     *
     * The map takes its colour from candles, and the render case passed none — so every tile came
     * back hatched with «کندلی در دسترس این صفحه نیست» above it. That is an honest state and the
     * app really does show it on a backend with no candle route, but it is the *one* state in which
     * the two things this render exists to check — whether the ramp reads as a scale and whether a
     * tile at phone width still carries its ticker — are both invisible.
     *
     * A deterministic walk per symbol: the drift comes from the symbol's own hash, so the same
     * ticker gets the same return on every run and the map spreads across the ramp instead of
     * landing in one colour.
     *
     * Every symbol answers. An earlier version had two return nothing, to keep the legend's
     * «بدون داده» hatch in the picture — and it does not work, because a market with no bars *and*
     * no catalogue quote has no price at all and so has no tile to hatch. It is simply absent from
     * the map, which is the app's real behaviour and is why the hatch belongs to a different case.
     */
    fun heatmapBars(): HeatmapBarSource = HeatmapBarSource { symbol ->
        // The walk **ends** at the catalogue's own price, and that is the whole point rather than a
        // detail. The map takes its price from the quote and its reference close from these bars,
        // so bars on their own scale made BTC read «+75384%»: a live 91,248 against a synthetic
        // 100. On a real backend the two come from the same venue and cannot disagree; a fixture
        // that lets them disagree is not the app.
        val price = SEARCH_PRICES[symbol] ?: syntheticPrice(symbol)
        // −4%…+4% over the window, stepped by the symbol's own hash so the same ticker gets the
        // same return on every run and the map spreads across the ramp instead of clustering.
        val step = 1.0 + ((symbol.hashCode().mod(81)) - 40) / 1_000.0 / 8.0
        val opens = DoubleArray(HEATMAP_BARS)
        opens[HEATMAP_BARS - 1] = price / step
        for (index in HEATMAP_BARS - 2 downTo 0) opens[index] = opens[index + 1] / step
        (0 until HEATMAP_BARS).map { index ->
            val open = opens[index]
            val close = open * step
            OhlcBar(
                t = 1_787_670_872L - (HEATMAP_BARS - 1L - index) * 86_400L,
                o = open,
                h = maxOf(open, close) * 1.004,
                l = minOf(open, close) * 0.996,
                c = close,
                v = 1_000.0,
            )
        }
    }

    /** A stable level for a market the catalogue quotes no price for, so the bars are plausible. */
    private fun syntheticPrice(symbol: String): Double = 1.0 + symbol.hashCode().mod(400) / 4.0

    private const val HEATMAP_BARS = 40

    /**
     * What the fixture catalogue quotes, and what [heatmapBars] walks towards.
     *
     * One map rather than two, because the heat map reads a price from one source and a reference
     * close from the other and computes a percentage between them.
     */
    private val SEARCH_PRICES = mapOf(
        "BTCUSDT" to 91_248.30, "ETHUSDT" to 3_147.62, "SOLUSDT" to 172.45,
        "XAUUSD" to 2_643.18, "XAGUSD" to 30.94, "EURUSD" to 1.0842,
        "GBPUSD" to 1.2731, "USDJPY" to 156.28, "US500" to 5_918.40,
        "PEPEUSDT" to 0.000018, "DOGEUSDT" to 0.3914, "USOIL" to 71.62,
    )

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

        /** The membership terms the guest gateway serves, for the card's own render. */
    val membershipTerms = MembershipTerms(
        lbankReferralUrl = "https://lbank.example/ref/CoinePro",
        ourbitReferralUrl = "https://ourbit.example/register?inviteCode=CoinePro",
        minDepositUsdt = 50.0,
        copyTradeExchanges = listOf("lbank"),
        uidExchanges = listOf("lbank", "ourbit"),
        noticeFa = "برای فعال‌سازی عضویت، حساب صرافی باید از طریق همین لینک ساخته شود.",
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
    /**
     * Sorted the way the real reader sorts, rather than in the order the fixture happens to list.
     *
     * `MarketIntelGateway.readSnapshot` orders the calendar by its scheduled moment and the news
     * newest-first, and every fallback source does the same before handing anything back — there is
     * no path on which the app shows an unsorted list. The fixture returns a canned snapshot, so it
     * skipped all of that and the calendar render came back 08:43, 11:43, 05:13: a state the app
     * cannot produce, in a review whose whole job is to say what the app looks like.
     */
    override suspend fun snapshot(): MarketIntelSnapshot {
        val raw = ScreenshotFixtures.marketIntel()
        return raw.copy(
            news = raw.news.sortedByDescending(MarketNewsItem::publishedAt),
            calendar = raw.calendar.sortedBy(EconomicEvent::scheduledAt),
        )
    }
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


/**
 * A public feed with the shape the route really returns — including the row where the server
 * omitted the 24-hour change, which is the case that must draw nothing rather than a flat zero.
 */
internal class FakeGuestGateway : GuestGateway {
    override suspend fun prices(symbols: List<String>) = AppResult.Success(
        GuestPrices(
            quotes = listOf(
                GuestQuote("BTCUSDT", 64_182.40, 2.14, 64_900.0, 62_800.0, 1.2e9),
                GuestQuote("ETHUSDT", 3_142.77, -1.08, 3_220.0, 3_090.0, 6.1e8),
                GuestQuote("SOLUSDT", 148.92, 5.63, 151.0, 139.4, 2.2e8),
                GuestQuote("XRPUSDT", 0.5241, -0.42, 0.5390, 0.5180, 9.4e7),
                GuestQuote("TONUSDT", 5.118, null, null, null, null),
            ),
            stale = false,
            ageMillis = 340,
        ),
    )

    override suspend fun trackRecord(limit: Int) = AppResult.Success(
        GuestTrackRecord(
            entries = listOf(
                TrackRecordEntry("BTCUSDT", "15m", buy = true, win = true, percentGain = 4.82, riskReward = 2.1),
                TrackRecordEntry("ETHUSDT", "1h", buy = false, win = true, percentGain = 3.14, riskReward = 1.8),
                TrackRecordEntry("SOLUSDT", "15m", buy = true, win = false, percentGain = -1.96, riskReward = 2.4),
            ),
            available = true,
        ),
    )

    override suspend fun news(limit: Int) = AppResult.Success(
        listOf(
            GuestHeadline(
                slug = "btc-etf-inflow",
                title = "ورودی صندوق‌های بیت‌کوین به بالاترین رقم دو ماه اخیر رسید",
                summary = "جریان خالص ورودی روز گذشته ۴۳۸ میلیون دلار ثبت شد.",
                source = "TradeYar",
                publishedAt = null,
            ),
            GuestHeadline(
                slug = "fed-minutes",
                title = "صورت‌جلسه‌ی فدرال رزرو: نگرانی از چسبندگی تورم خدمات",
                summary = null,
                source = "TradeYar",
                publishedAt = null,
            ),
        ),
    )

    override suspend fun membership() = AppResult.Success(
        MembershipTerms(
            lbankReferralUrl = "https://lbank.example/ref/CoinePro",
            ourbitReferralUrl = "https://ourbit.example/register?inviteCode=CoinePro",
            minDepositUsdt = 50.0,
            copyTradeExchanges = listOf("lbank"),
            uidExchanges = listOf("lbank", "ourbit"),
            noticeFa = "برای فعال‌سازی عضویت، حساب صرافی باید از طریق همین لینک ساخته شود.",
        ),
    )

    override suspend fun candles(symbol: String, timeframe: String, limit: Int) =
        AppResult.Success(GuestCandles(symbol, "H1", emptyList()))

    /**
     * One channel whose count the server could read and one it could not — the mixed case is the
     * one worth capturing, because the whole rule beside that route is what the second row does.
     */
    override suspend fun community() = AppResult.Success(
        GuestCommunity(
            channels = listOf(
                CommunityChannel("signals", "کانال سیگنال", "https://t.me/example", MemberCount.Known(18_420)),
                CommunityChannel("chat", "گروه گفت‌وگو", "https://t.me/example_chat", MemberCount.Unavailable),
            ),
            total = MemberCount.Known(52_340),
            botUsers = MemberCount.Known(7_915),
            note = null,
        ),
    )
}

internal class FakeAccountGateway(
    private val submitResult: AppResult<KycStatus>? = null,
    private val status: KycStatus = KycStatus(level = 1, state = KycState.NOT_STARTED),
    private val deletionResult: AppResult<DeletionOutcome> = AppResult.Success(DeletionOutcome.UNSUPPORTED),
) : AccountGateway {
    override suspend fun briefing(): AppResult<AccountBriefing?> = AppResult.Success(null)

    override suspend fun portfolio(): AppResult<AccountPortfolio> = AppResult.Success(AccountPortfolio())

    override suspend fun kyc(): AppResult<KycStatus> = AppResult.Success(status)

    override suspend fun submitKycLevel1(identity: KycIdentity): AppResult<KycStatus> = submitResult ?: AppResult.Success(status)

    override suspend fun deleteAccount(): AppResult<DeletionOutcome> = deletionResult
}

/** A feed that is up and has nothing to report, which is not the same as a feed that is down. */
class EmptySignalGateway : SignalGateway {
    override suspend fun list(
        market: SignalMarketFilter,
        status: SignalStatusFilter,
        limit: Int,
        offset: Int,
    ): SignalPage = SignalPage(emptyList(), 0, ScreenshotFixtures.NOW_MILLIS)

    override suspend fun detail(signalId: Long): TradingSignal =
        error("an empty feed has no signal to open")
}

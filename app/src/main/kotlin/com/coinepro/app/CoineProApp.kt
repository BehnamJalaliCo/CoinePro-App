package com.coinepro.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coinepro.app.alerts.InAppAlertBus
import com.coinepro.app.alerts.LocalAlertScheduler
import com.coinepro.app.auth.GoogleSignInClient
import com.coinepro.app.auth.GoogleSignInOutcome
import com.coinepro.app.chart.rememberChartControllers
import com.coinepro.app.notifications.PushCoordinator
import com.coinepro.app.notifications.channelDescriptionRes
import com.coinepro.app.notifications.channelNameRes
import com.coinepro.app.sync.BackgroundSyncScheduler
import com.coinepro.core.academy.AcademyController
import com.coinepro.core.account.AccountController
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.announcements.AnnouncementsController
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.auth.EmailAuthController
import com.coinepro.core.auth.EmailAuthStep
import com.coinepro.core.auth.PlatformCapabilities
import com.coinepro.core.auth.PlatformSessions
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionState
import com.coinepro.core.auth.sessionForShell
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.chartevents.ChartEventController
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.datastore.ActivePlatformStore
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.SymbolChartState
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.datastore.DrawingTemplateStore
import com.coinepro.core.datastore.IndicatorTemplateStore
import com.coinepro.core.datastore.DrawingImageStore
import com.coinepro.core.datastore.DrawingSyncStore
import com.coinepro.core.datastore.TimeZonePrefStore
import com.coinepro.core.datastore.IntervalFavouritesStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.ProfileStore
import com.coinepro.core.designsystem.CoineProNavigationRail
import com.coinepro.core.designsystem.CoineProRailHeader
import com.coinepro.core.designsystem.ProChartWordmark
import com.coinepro.core.designsystem.CoineProListDetail
import com.coinepro.core.designsystem.coineProWindowClass
import com.coinepro.core.marketdata.CandleArchive
import com.coinepro.core.marketdata.CandleCache
import com.coinepro.core.network.NetworkStatus
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.ThemeMode
import com.coinepro.core.datastore.UserPreferencesStore
import com.coinepro.core.datastore.StoredProfile
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.watchlistsync.WatchlistSyncController
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProReading
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import com.coinepro.app.security.AppLockSheet
import com.coinepro.app.security.BiometricGate
import com.coinepro.app.security.LockScreen
import com.coinepro.app.security.rememberLockCapability
import com.coinepro.core.designsystem.CoineProConfirmDialog
import com.coinepro.core.designsystem.CoineProOfflineBar
import com.coinepro.core.designsystem.CoineProPriceFeedBar
import com.coinepro.core.designsystem.PriceFeedReading
import com.coinepro.core.designsystem.CoineProToast
import com.coinepro.core.designsystem.LocalToaster
import com.coinepro.core.designsystem.ToastTone
import com.coinepro.core.designsystem.CoineProToastHost
import com.coinepro.feature.profile.AppearanceSheet
import com.coinepro.feature.profile.AppearanceTitle
import com.coinepro.feature.profile.labelRes
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.ProvideToaster
import com.coinepro.core.designsystem.PageAccent
import com.coinepro.core.designsystem.ProvidePageAccent
import com.coinepro.core.diagnostics.AdminController
import com.coinepro.core.diagnostics.DiagnosticExport
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.diagnostics.ControlHub
import com.coinepro.core.diagnostics.CrashReport
import com.coinepro.core.diagnostics.FeedStatus
import com.coinepro.core.diagnostics.HubActions
import com.coinepro.core.diagnostics.HubTone
import com.coinepro.core.diagnostics.PushPermission
import com.coinepro.core.diagnostics.PushPreferenceKey
import com.coinepro.core.diagnostics.PushStatus
import com.coinepro.core.diagnostics.RelayStatus
import com.coinepro.core.diagnostics.ServerCapabilities
import com.coinepro.core.diagnostics.SessionRow
import com.coinepro.core.diagnostics.VenueStatus
import com.coinepro.core.execution.ConnectionsState
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.guest.GuestCandleGateway
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.GuestMarketCatalogGateway
import com.coinepro.core.guest.GuestMembershipState
import com.coinepro.core.journal.JournalController
import com.coinepro.core.marketdata.AcademyTokenStore
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.marketdata.MarketDataSymbols
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.PriceFeedStatus
import com.coinepro.core.marketdata.PriceFeedTier
import com.coinepro.core.marketdata.SparklineStore
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.membership.MembershipController
import com.coinepro.core.model.AvatarSpec
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.NotificationSettings
import com.coinepro.core.orderbook.OrderBookController
import com.coinepro.core.orderbook.OrderBookGateway
import com.coinepro.core.papertrade.PaperOrderRequest
import com.coinepro.core.papertrade.PaperOrderType
import com.coinepro.core.papertrade.PaperSide
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.papertrade.asPaperQuote
import com.coinepro.core.portfolio.PortfolioController
import com.coinepro.core.script.ScriptController
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.academy.AcademyScreen
import com.coinepro.feature.academy.LessonScreen
import com.coinepro.feature.account.DeleteAccountScreen
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.feature.admin.AdminScreen
import com.coinepro.feature.ai.AiStudioScreen
import com.coinepro.feature.aiassistant.AiAssistantScreen
import com.coinepro.feature.aivision.AiVisionScreen
import com.coinepro.feature.alerts.AlertCenterScreen
import com.coinepro.feature.alerts.AlertsController
import com.coinepro.feature.auth.AuthScreen
import com.coinepro.feature.auth.EmailAuthScreen
import com.coinepro.feature.calendar.EconomicCalendarScreen
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartPanesScreen
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.ChartStudioScreen
import com.coinepro.feature.chart.ChartWorkspaceStore
import com.coinepro.feature.chart.WatchlistQuote
import com.coinepro.feature.connections.ConnectionsScreen
import com.coinepro.feature.copytrade.CopyTradeScreen
import com.coinepro.feature.dom.DepthLadderPreference
import com.coinepro.feature.dom.DepthLadderPreferences
import com.coinepro.feature.dom.LadderFigure
import com.coinepro.feature.dom.DepthOfMarketScreen
import com.coinepro.feature.dom.R as DomR
import com.coinepro.feature.execution.ExecutionScreen
import com.coinepro.feature.guest.GuestGate
import com.coinepro.feature.guest.GuestGateScreen
import com.coinepro.feature.guest.GuestScreen
import com.coinepro.feature.home.HomeBriefing
import com.coinepro.feature.home.HomePortfolio
import com.coinepro.feature.heatmap.CandleHeatmapBarSource
import com.coinepro.feature.heatmap.MarketTickerHeatmapSource
import com.coinepro.feature.heatmap.HeatmapScreen
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.portfolio.PortfolioReportScreen
import com.coinepro.feature.screener.CandleScreenerBarSource
import com.coinepro.feature.screener.ScreenerController
import com.coinepro.feature.screener.ScreenerScreen
import com.coinepro.feature.screener.ScreenerStore
import com.coinepro.feature.home.HomeSubscription
import com.coinepro.feature.home.toHomeBriefing
import com.coinepro.feature.home.toHomePortfolio
import com.coinepro.feature.home.toHomeSubscription
import com.coinepro.feature.journal.JournalScreen
import com.coinepro.feature.kyc.KycScreen
import com.coinepro.feature.membership.MembershipScreen
import com.coinepro.feature.news.NewsScreen
import com.coinepro.feature.news.PublicNewsScreen
import com.coinepro.feature.notifications.AlertComposerSheet
import com.coinepro.feature.notifications.NotificationSection
import com.coinepro.feature.notifications.NotificationSettingsScreen
import com.coinepro.feature.papertrade.PaperTradeScreen
import com.coinepro.feature.portfolio.PortfolioScreen
import com.coinepro.feature.profile.AvatarComposerSheet
import com.coinepro.feature.profile.ProfileAction
import com.coinepro.feature.profile.ProfileScreen
import com.coinepro.feature.script.ScriptScreen
import com.coinepro.feature.search.MarketsScreen
import com.coinepro.feature.search.MarketsSignalStrip
import com.coinepro.feature.search.SearchScreen
import com.coinepro.feature.search.SurfaceAccess
import com.coinepro.feature.signaldetail.SignalChartController
import com.coinepro.feature.signaldetail.SignalDetailScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.terminal.TerminalController
import com.coinepro.feature.terminal.TerminalScreen
import com.coinepro.feature.tools.ToolsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val SIGNAL_DETAIL_PATTERN = "signal/{signalId}"
private const val EXECUTION_PATTERN = "execution/{signalId}"
private const val CONNECTIONS_ROUTE = "connections"
private const val AI_VISION_ROUTE = "ai/vision"
private const val AI_ASSISTANT_ROUTE = "ai/assistant"
private const val MARKET_SEARCH_ROUTE = "market/search"
/**
 * The chart, with the bar as an optional query rather than a second path segment.
 *
 * A query and not a segment because `chart/{symbol}/{timeframe}` would collide with the three
 * routes that already spend the second segment — the studio, the two-pane screen and depth — and
 * because a chart opened without one is the ordinary case, which a required argument cannot say.
 */
private const val CHART_PATTERN = "chart/{symbol}?timeframe={timeframe}"
private const val PORTFOLIO_ROUTE = "portfolio"
private const val ACADEMY_ROUTE = "academy"
private const val TERMINAL_ROUTE = "terminal"
private const val LESSON_PATTERN = "academy/lesson/{slug}"
private const val NEWS_ROUTE = "market/news"
private const val CALENDAR_ROUTE = "market/calendar"
private const val LAUNCH_READINESS_ROUTE = "launch-readiness"
private const val ADMIN_ROUTE = "diagnostics"
private const val PROFILE_ROUTE = "profile"
private const val NOTIFICATIONS_ROUTE = "notifications"
private const val MEMBERSHIP_ROUTE = "membership"
private const val KYC_ROUTE = "account/verify"
private const val DELETE_ACCOUNT_ROUTE = "account/delete"
private const val ALERTS_ROUTE = "alerts"
private const val JOURNAL_ROUTE = "journal"
private const val PAPER_TRADE_ROUTE = "paper-trade"

/**
 * Two destinations that used to be tabs.
 *
 * The routes keep their old spelling so a saved back stack and every deep link that named them
 * still land — what changed is that they are reached from Home rather than from the bar.
 */
private const val TOOLS_ROUTE = "tools"

/**
 * The market heat map.
 *
 * No account needed and none asked for: it is drawn from the public catalogue, which is the same
 * feed a guest's own home screen already reads.
 */
private const val HEATMAP_ROUTE = "market/heatmap"

/**
 * The screener: every market the catalogue carries, filtered by what it is doing.
 *
 * Guest-safe like the heat map, and for the same reason — it is the public catalogue plus the
 * public quote feed, neither of which needs an account.
 */
private const val SCREENER_ROUTE = "screener"

/** The portfolio's own report: the curve, the attribution, the month matrix and the export. */
private const val PORTFOLIO_REPORT_ROUTE = "portfolio-report"
private const val ACTIVITY_ROUTE = "activity"
private const val SCRIPT_PATTERN = "script/{symbol}"
private const val STUDIO_PATTERN = "chart/{symbol}/studio"

/**
 * Two charts, one above the other, on the symbol the reader split from.
 *
 * A sibling of the studio's route rather than a mode flag on the chart's, because it is a
 * different screen with a different back stack entry: leaving it must put the reader back on the
 * single chart they came from, and a flag on `chart/{symbol}` would have made "back" mean
 * "close the app" for anyone who arrived here first.
 */
private const val PANES_PATTERN = "chart/{symbol}/panes"

/**
 * The order-book ladder for the symbol the reader is charting.
 *
 * A sibling of the studio's and the two-pane screen's routes rather than a sheet over the chart,
 * and for the reason those two are routes: leaving it must put the reader back on the bars they
 * came from, and the ladder polls a venue — a destination stops when it leaves the back stack,
 * where a sheet dismissed behind a recomposition can keep asking.
 *
 * The symbol is in the path even though neither backend serves depth today, because the route is
 * what makes the screen answerable at all: `docs/SERVER_ASKS_DOM.md` is the ask, and the honest
 * state on crypto until it lands is «هنوز سرو نمی‌شود», which is a thing this screen says about a
 * named market rather than a spinner about nothing.
 */
private const val DOM_PATTERN = "chart/{symbol}/dom"
private fun signalDetailRoute(signalId: Long) = "signal/$signalId"
private fun executionRoute(signalId: Long) = "execution/$signalId"

/**
 * A ticker is safe in a path segment — both feeds spell them in ASCII letters and digits — but
 * encoding it costs nothing and a symbol that ever grows a slash would otherwise route nowhere.
 */
private fun chartRoute(symbol: String, timeframe: String? = null): String {
    val base = "chart/" + Uri.encode(symbol)
    val bar = timeframe?.trim()?.takeIf(String::isNotEmpty) ?: return base
    return base + "?timeframe=" + Uri.encode(bar)
}

/**
 * The script studio, on a symbol.
 *
 * A symbol in the path rather than a screen that picks one, because every way into this screen
 * already knows which instrument the reader is looking at — the chart's toolbar, and the toolkit
 * card, which passes the first symbol on the watchlist.
 */
private fun scriptRoute(symbol: String) = "script/" + Uri.encode(symbol)

/** The chart's working surface, on a symbol. */
private fun studioRoute(symbol: String) = "chart/" + Uri.encode(symbol) + "/studio"

/**
 * Where a search result for one of the app's own sections sends the reader.
 *
 * The catalogue in `feature:search` holds ids rather than routes on purpose. Three of these open a
 * screen that needs a symbol in its path, and that module has no business knowing how this graph is
 * spelled or which market to pick — that decision is the same one the chart tab and the toolkit
 * already make, and it is made here, once.
 *
 * Exhaustive rather than a map with a fallback: an id with no case here would quietly navigate
 * somewhere wrong, and a section added without a route should be found by whoever adds it.
 */
private fun surfaceRoute(id: String, platform: MarketPlatform, watchlist: List<String>): String =
    when (id) {
        "academy" -> ACADEMY_ROUTE
        "journal" -> JOURNAL_ROUTE
        "paper-trade" -> PAPER_TRADE_ROUTE
        "backtest" -> scriptRoute(defaultScriptSymbol(platform, watchlist))
        "screener" -> SCREENER_ROUTE
        "heatmap" -> HEATMAP_ROUTE
        "tools" -> TOOLS_ROUTE
        "chart-studio" -> studioRoute(defaultScriptSymbol(platform, watchlist))
        "alerts" -> ALERTS_ROUTE
        // The watchlist is a segment inside the markets tab, not a destination of its own.
        "watchlist" -> AppDestination.MARKETS.route
        "news" -> NEWS_ROUTE
        "calendar" -> CALENDAR_ROUTE
        "portfolio" -> PORTFOLIO_ROUTE
        "signals" -> AppDestination.SIGNALS.route
        "ai" -> AppDestination.AI.route
        "ai-vision" -> AI_VISION_ROUTE
        "ai-assistant" -> AI_ASSISTANT_ROUTE
        "terminal" -> TERMINAL_ROUTE
        "connections" -> CONNECTIONS_ROUTE
        "activity" -> ACTIVITY_ROUTE
        "membership" -> MEMBERSHIP_ROUTE
        "verify" -> KYC_ROUTE
        "notifications" -> NOTIFICATIONS_ROUTE
        "profile" -> PROFILE_ROUTE
        else -> PROFILE_ROUTE
    }

/** The two-chart screen, on the symbol its upper pane opens with. */
private fun panesRoute(symbol: String) = "chart/" + Uri.encode(symbol) + "/panes"

/** The depth ladder, on the symbol the chart had in front of the reader when they pressed it. */
private fun domRoute(symbol: String) = "chart/" + Uri.encode(symbol) + "/dom"

/**
 * Which symbol the studio opens on when it was not reached from a chart.
 *
 * The reader's own first watchlist entry, and the platform's first quoted instrument otherwise.
 * Both are real symbols on the active backend, which matters: opening the studio on a ticker this
 * platform does not carry would greet a first-time reader with an empty chart and an error.
 */
private fun defaultScriptSymbol(platform: MarketPlatform, watchlist: List<String>): String =
    watchlist.firstOrNull() ?: MarketDataSymbols.forPlatform(platform).first()

/**
 * Which domain a route belongs to.
 *
 * Analysis is anything whose whole job is reading the market — the market list, a chart, a search,
 * news, the calendar, the AI screens. Social is copy trading. Premium is nothing yet: the
 * subscription screen is not built, and listing a route here that does not exist would be a rule
 * with no case. Everything else is brand gold, which is the app acting on the reader's account.
 *
 * Home is deliberately **not** analysis, even though it carries a market list. Its hero is the
 * balance and its primary action is "generate a signal" — the screen is about the account, and the
 * one gold object on it should be the thing that acts. The market list is a passenger there; the
 * dedicated markets surface is the search route, and that one is blue.
 */
/**
 * Routes whose screen carries its own heading.
 *
 * Both visual voices define themselves by a heading — the gold one by a title over a fading rule
 * and a large figure, the terminal one by a title over its column names — so on these the app bar
 * is reduced to the back arrow.
 */
private val SELF_TITLED: Set<String> = setOf(
    CHART_PATTERN,
    STUDIO_PATTERN,
    PANES_PATTERN,
    DOM_PATTERN,
    SCRIPT_PATTERN,
    SIGNAL_DETAIL_PATTERN,
    PORTFOLIO_ROUTE,
    NEWS_ROUTE,
    CALENDAR_ROUTE,
    ACTIVITY_ROUTE,
    MARKET_SEARCH_ROUTE,
    PROFILE_ROUTE,
    NOTIFICATIONS_ROUTE,
    MEMBERSHIP_ROUTE,
    HEATMAP_ROUTE,
    SCREENER_ROUTE,
)

private fun accentFor(route: String?): PageAccent = when (route) {
    MARKET_SEARCH_ROUTE,
    CHART_PATTERN,
    NEWS_ROUTE,
    CALENDAR_ROUTE,
    AI_VISION_ROUTE,
    AI_ASSISTANT_ROUTE,
    SCRIPT_PATTERN,
    STUDIO_PATTERN,
    PANES_PATTERN,
    DOM_PATTERN,
    HEATMAP_ROUTE,
    SCREENER_ROUTE,
    AppDestination.MARKETS.route,
    AppDestination.CHART.route,
    AppDestination.AI.route,
    -> PageAccent.ANALYSIS

    CONNECTIONS_ROUTE -> PageAccent.SOCIAL

    else -> PageAccent.BRAND
}
private fun lessonRoute(slug: String) = "academy/lesson/" + Uri.encode(slug)

@Composable
fun CoineProApp(
    sessionController: SessionController,
    emailAuthController: EmailAuthController,
    guestController: GuestController,
    /** The public feed, which is what makes the guest experience the app rather than a teaser. */
    guestGateway: GuestGateway,
    membershipController: MembershipController,
    profileStore: ProfileStore,
    /** Device-wide preferences — currently the theme. Survives sign-out; see the Hilt provider. */
    userPreferencesStore: UserPreferencesStore,
    /** Whether the phone has a network. Drawn as one bar at the top of the shell. */
    networkStatus: NetworkStatus,
    /** The bars already held, so a chart draws before it fetches. */
    candleCache: CandleCache,
    /** Every bar ever fetched, so paging back deepens across sessions. See [CandleArchive]. */
    candleArchive: CandleArchive,
    notificationSettingsStore: NotificationSettingsStore,
    localAlertStore: LocalAlertStore,
    localAlertScheduler: LocalAlertScheduler,
    watchlistStore: WatchlistStore,
    watchlistSyncController: WatchlistSyncController,
    chartLayoutStore: ChartLayoutStore,
    chartDrawingStore: ChartDrawingStore,
    /** Where the image drawing tool's pictures live. See `DrawingImageStore`. */
    drawingImageStore: DrawingImageStore,
    /** Where each symbol's own chart settings live. See the Hilt provider for why it is separate. */
    symbolChartStateStore: SymbolChartStateStore,
    /**
     * The reader's saved per-tool drawing styles, for the chart and the studio both.
     *
     * Handed to the screens as the store rather than as a list, because they read templates for
     * the armed tool and for the drawing being edited — two queries that change as the reader
     * works, and hoisting either would put the screen's own state up here.
     */
    drawingTemplateStore: DrawingTemplateStore,
    indicatorTemplateStore: IndicatorTemplateStore,
    drawingSyncStore: DrawingSyncStore,
    timeZonePrefStore: TimeZonePrefStore,
    intervalFavouritesStore: IntervalFavouritesStore,
    /**
     * How the chart screen itself is arranged: the split with the watchlist, and what the two
     * panes tie together. Without it a drag on the divider is forgotten the moment the chart is
     * left, and the second pane opens on nothing every time.
     */
    chartWorkspaceStore: ChartWorkspaceStore,
    /** The reader's saved screens. One file for both platforms — a filter is not per backend. */
    screenerStore: ScreenerStore,
    journalController: JournalController,
    paperTradeController: PaperTradeController,
    scriptController: ScriptController,
    marketDataControllers: Map<MarketPlatform, MarketDataController>,
    marketSearchControllers: Map<MarketPlatform, MarketSearchController>,
    /**
     * The day's open, high, low, change and turnover, per platform.
     *
     * Keyed rather than singular for the reason every other map here is — exactly one feed runs at
     * a time — and CoinePro-FX's entry is a store over a gateway that reports `supported = false`.
     * That is what keeps the gainers and losers off that platform rather than putting tabs there
     * which could only ever be empty.
     */
    marketTickerStores: Map<MarketPlatform, MarketTickerStore>,
    screenerControllers: Map<MarketPlatform, ScreenerController>,
    candleGateways: Map<MarketPlatform, CandleGateway>,
    /**
     * Depth, per platform, and the two entries are not the same kind of thing.
     *
     * Crypto is a real relay of LBank's book; forex is a documented refusal, because MetaTrader 5
     * publishes Level II only where the broker has switched it on and most have not. Threaded as a
     * map for the same reason the candles are: the ladder has to follow the platform on screen.
     */
    orderBookGateways: Map<MarketPlatform, OrderBookGateway>,
    portfolioControllers: Map<MarketPlatform, PortfolioController>,
    academyController: AcademyController,
    terminalController: TerminalController,
    accountControllers: Map<MarketPlatform, AccountController>,
    adminController: AdminController,
    /** The app's narrative log, for the crash report and the diagnostics screen. */
    appLog: AppLog,
    platformSessions: PlatformSessions,
    platformCapabilities: PlatformCapabilities,
    marketDataCache: MarketDataCache,
    activePlatformStore: ActivePlatformStore,
    signalControllers: Map<MarketPlatform, SignalController>,
    notificationControllers: Map<MarketPlatform, NotificationController>,
    /** The alert centre's own controller. Device-local, so one for both platforms. */
    alertsController: AlertsController,
    /**
     * Firings offered to whatever is on screen, for the reader who chose «in the app» as a channel.
     *
     * Collected in the shell rather than by the alerts screen, because the whole point of the
     * channel is a reader who is looking at something else — usually the chart of the very symbol
     * that just moved. See [InAppAlertBus] for why an uncollected firing must not be recorded as
     * delivered.
     */
    inAppAlerts: InAppAlertBus,
    executionControllers: Map<MarketPlatform, ExecutionController>,
    copyTradeControllers: Map<MarketPlatform, CopyTradeController>,
    aiSignalControllers: Map<MarketPlatform, AiSignalController>,
    aiVisionControllers: Map<MarketPlatform, AiVisionController>,
    aiAssistantController: AiAssistantController,
    marketIntelControllers: Map<MarketPlatform, MarketIntelController>,
    /** The announcements channel, keyed by platform. Empty on a build with no TradeYar. */
    announcementsControllers: Map<MarketPlatform, AnnouncementsController>,
    /** The chart's axis marks, per platform — items 118 and 119. Same rule as the news readers. */
    chartEventControllers: Map<MarketPlatform, ChartEventController>,
    /** Cleared on sign-out — a derived credential that would otherwise outlive the session. */
    academyTokenStore: AcademyTokenStore,
    pushCoordinator: PushCoordinator,
    backgroundSyncScheduler: BackgroundSyncScheduler,
    launchSignalId: Long?,
    launchActivity: Boolean,
    /** Set when the recovery App Link opened the app; null on every other launch. */
    launchResetToken: String?,
    /** A market to open, from a row of the home-screen widget. See `MarketsWidget`. */
    launchSymbol: String?,
    /** The bar a deep link asked for, or null. Consumed with [launchSymbol]. See `AlertDeepLink`. */
    launchTimeframe: String?,
    notificationPermissionState: NotificationPermissionUiState,
    onSignalLaunchConsumed: () -> Unit,
    onActivityLaunchConsumed: () -> Unit,
    onSymbolLaunchConsumed: () -> Unit,
    onResetTokenConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSendFeedback: () -> Unit,
) {
    // Every configured platform restores, not just the one the shell happens to open on.
    //
    // Sign-in can now land on either backend — a new account is TradeYar's, an account made before
    // 1.27.0 is CoinePro-FX's — so restoring only one of them on launch would leave a returning
    // reader signed out of the app while a perfectly good session sat in storage.
    LaunchedEffect(platformSessions) { platformSessions.start() }
    val sessionStates by platformSessions.states.collectAsStateWithLifecycle(initialValue = emptyMap())
    val emailAuthState by emailAuthController.state.collectAsStateWithLifecycle()
    val loginConfigState by sessionController.loginConfigState.collectAsStateWithLifecycle()
    // Exactly one feed runs at a time. Switching platform stops the old controller before the new
    // one starts, so two sockets are never open and the screen can never blend their quotes.
    val activePlatform by activePlatformStore.active
        .collectAsStateWithLifecycle(initialValue = activePlatformStore.available.first())
    // What gates the shell: the reader's session, and *not* the tab they are looking at.
    //
    // This was `sessionStates[activePlatform]`, and that is the forex bug. «فارکس» and «کریپتو» are
    // a market switch in the reader's hands; reading a session out of them made the tab an account
    // switch. Registration deliberately does not federate — see `FederatedEmailAuthGateway` — so a
    // sign-in mints exactly one session and every reader is signed out of exactly one of the two
    // platforms. Tapping the other tab therefore dropped a valid session into the guest branch
    // below, which draws no platform switcher, so the switch was one-way and read as being logged
    // out. From the admin panel it reads as a crash, because the whole `MainShell` holding the
    // diagnostics route is disposed and rebuilt at the start destination.
    //
    // `sessionForShell` asks the platform on screen first and falls back to the reader's other
    // session. It lends no token: the platform with no account still answers 401 to its own
    // screens, and each of them says so for itself — a gap the reader can see, rather than a
    // sign-out they cannot explain. See `PlatformSwitchTest`, and
    // `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` for the half that is the servers'.
    //
    // It also settles the teardown below. `signedIn` now means "this reader holds an account", so
    // the sign-out branch of that effect — which clears every controller on *both* platforms and
    // destroys the academy token — stops firing on a change of market.
    val session = sessionStates.sessionForShell(activePlatform)
    val marketDataController = marketDataControllers.getValue(activePlatform)
    val marketSearchController = marketSearchControllers.getValue(activePlatform)
    val marketTickerStore = marketTickerStores.getValue(activePlatform)
    val screenerController = screenerControllers.getValue(activePlatform)
    val marketState by marketDataController.state.collectAsStateWithLifecycle()
    // The account reads follow the same rule as the feed: one platform at a time, and the balance
    // on screen always belongs to the backend named above it.
    val accountController = accountControllers.getValue(activePlatform)
    // News and the calendar follow the platform for the same reason, and a stronger one: a rate
    // decision has no bearing on a listing and a token unlock has none on bullion, so the wrong
    // market's headlines are not a degraded answer but a misleading one.
    val marketIntelController = marketIntelControllers.getValue(activePlatform)
    // Null on CoinePro-FX, which has no such route, and on a build with no TradeYar base URL.
    // The news screen draws no entry for a null, so the feature is absent rather than broken.
    val announcementsController = announcementsControllers[activePlatform]
    // Absent where the platform is not configured for this build, which is why the map is keyed
    // rather than a pair. A null leaves the axis bare and the studio's section away, which is the
    // honest shape for a backend that is not there at all.
    val chartEventController = chartEventControllers[activePlatform]
    // Everything else that reads from a backend follows the same rule: the screen belongs to the
    // platform named above it, and no controller is ever handed the other one's data.
    val signalController = signalControllers.getValue(activePlatform)
    val notificationController = notificationControllers.getValue(activePlatform)
    val executionController = executionControllers.getValue(activePlatform)
    val copyTradeController = copyTradeControllers.getValue(activePlatform)
    val aiSignalController = aiSignalControllers.getValue(activePlatform)
    val aiVisionController = aiVisionControllers.getValue(activePlatform)
    val briefingState by accountController.briefing.collectAsStateWithLifecycle()
    val portfolioState by accountController.portfolio.collectAsStateWithLifecycle()
    // Read once per briefing rather than on every recomposition, so the age is fixed at the moment
    // the briefing arrived. It is deliberately not a ticking clock: the label is coarse enough that
    // a second-by-second update would buy nothing and would be continuous motion for its own sake.
    val briefingReadAt = remember(briefingState) { System.currentTimeMillis() / 1_000 }
    val scope = rememberCoroutineScope()
    val signedIn = session is SessionState.SignedIn
    val watchlist by watchlistStore.symbols.collectAsStateWithLifecycle(initialValue = emptyList())
    // The periodic check exists only while there is something to check. A worker that wakes every
    // quarter of an hour to read an empty list is a battery cost with no possible benefit — and it
    // is exactly the kind of thing that never shows up in testing and does show up in a review.
    val storedAlerts by localAlertStore.alerts.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(storedAlerts) {
        localAlertScheduler.sync(hasActiveAlerts = storedAlerts.any { it.active })
    }
    // Read here rather than inside the shell, because both branches need it: a guest has a profile
    // in this app and it is the same profile they keep when they sign in.
    val profile by profileStore.profile.collectAsStateWithLifecycle(initialValue = StoredProfile())

    // Sign-in, sign-out and the platform the session belongs to. No email, no token, no name: the
    // question a log answers here is *whether* there is a session and on which backend, and the
    // rest is the thing that must never be in a file attached to a crash report.
    LaunchedEffect(session) {
        appLog.info(
            tag = LogTag.AUTH,
            message = "session " + session::class.java.simpleName,
            fields = mapOf("platform" to activePlatform.name),
        )
    }
    val notificationSettings by notificationSettingsStore.settings
        .collectAsStateWithLifecycle(initialValue = NotificationSettings())
    val chartLayouts by chartLayoutStore.layouts().collectAsStateWithLifecycle(initialValue = emptyList())

    val capabilities by platformCapabilities.state.collectAsStateWithLifecycle()
    // What each deployment offers. Read once on sign-in: it is server configuration, not live
    // state, so re-reading it per screen would spend a request to be told the same thing.
    LaunchedEffect(signedIn) {
        if (signedIn) platformCapabilities.refresh() else platformCapabilities.clear()
    }
    val methods = capabilities[activePlatform]
    // Only the two flags a single server reports are assumed present when unheard. Everything else
    // stays hidden until a server has confirmed it — a button certain to fail is worse than one
    // that appears a moment late.
    val chartVisionAvailable = methods?.chartVision == true
    val pushAvailable = methods?.push == true
    val assistantAvailable = methods?.assistant ?: true
    val aiSignalsAvailable = methods?.aiSignals ?: true
    // Off unless the server said yes. A delete button that does nothing is the worst button in the
    // app; where this is false the screen shows the published out-of-app route, which works today.
    val accountDeletionAvailable = methods?.accountDeletion == true
    // Asking spends the one prompt Android grants, and it is spent for good: a reader who declines
    // is not asked again. A deployment that cannot deliver a push would spend it on nothing, and
    // one who granted it and then never heard anything has been told something untrue by the
    // request itself. Unconfigured is already the case for a build without Firebase and reads the
    // same way here, so it is reused rather than given a second name.
    val deliverablePermissionState = if (pushAvailable) {
        notificationPermissionState
    } else {
        NotificationPermissionUiState.NOT_CONFIGURED
    }

    LaunchedEffect(signedIn, activePlatform) {
        marketDataControllers.forEach { (platform, controller) ->
            if (platform != activePlatform) controller.stop()
        }
        if (signedIn) {
            marketDataController.start()
            accountController.refresh()
            pushCoordinator.registerCurrentToken()
            backgroundSyncScheduler.enableForAuthenticatedSession()
        } else {
            backgroundSyncScheduler.disable()
            marketDataController.stop()
            signalControllers.values.forEach(SignalController::clear)
            notificationControllers.values.forEach(NotificationController::clear)
            executionControllers.values.forEach(ExecutionController::clear)
            copyTradeControllers.values.forEach(CopyTradeController::clear)
            aiSignalControllers.values.forEach(AiSignalController::clear)
            aiVisionControllers.values.forEach(AiVisionController::clear)
            aiAssistantController.clear()
            marketIntelControllers.values.forEach(MarketIntelController::clear)
            announcementsControllers.values.forEach(AnnouncementsController::clear)
            chartEventControllers.values.forEach(ChartEventController::clear)
            // The academy token is a second credential, derived from the mobile one and held only
            // in memory. Without this it outlives the sign-out by up to twelve hours — and after a
            // *deletion* it is a live bearer for an account that no longer exists.
            academyTokenStore.clear()
        }
    }

    // The three flags the server understands, kept in step with the fifteen switches on the phone.
    //
    // It matters that this is derived rather than mirrored. Two of the categories map onto a server
    // flag and the rest are the app's own, so what is sent is the *consequence* of the reader's
    // choices — see `NotificationSettings.serverPreferences`, and in particular why one wanted
    // update keeps a flag on that two unwanted ones would otherwise turn off at the source.
    //
    // Keyed on the derived value, not the settings: flipping a switch the server has never heard of
    // must not spend a request telling it something it already knows.
    val serverPushPreferences = remember(notificationSettings) { notificationSettings.serverPreferences() }
    LaunchedEffect(signedIn, notificationController, serverPushPreferences) {
        if (signedIn) notificationController.updatePreferences(serverPushPreferences)
    }

    // Refreshed here rather than in onResume, so a platform switch reads that platform's news
    // instead of leaving the previous market's headlines under the new market's heading.
    LaunchedEffect(signedIn, marketIntelController) {
        if (signedIn) marketIntelController.refresh()
    }

    val notificationState by notificationController.state.collectAsStateWithLifecycle()
    val venueState by executionController.connections.collectAsStateWithLifecycle()
    // A cold start opens on the platform the reader's account is actually on.
    //
    // Narrower than it was, because the reason it was written for is gone. It used to be the guard
    // against a stored preference naming a platform with no token: the shell opened there, the
    // first request came back 401, and the app landed on the guest screen. `sessionForShell` above
    // is what stops that now, and it stops it properly rather than by moving the reader.
    //
    // What is left is still worth having, and only on a first frame: a reader whose one account is
    // on CoinePro-FX, opening a build whose fallback is TradeYar, would otherwise land on a crypto
    // shell where every controller answers 401. So — if the platform on screen has no session and
    // exactly one platform does, follow it. Only when it is unambiguous, and only until the reader
    // touches the switcher, because a deliberate choice is not a stale preference.
    var platformChosen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionStates, activePlatform) {
        if (platformChosen) return@LaunchedEffect
        if (sessionStates[activePlatform] is SessionState.SignedIn) return@LaunchedEffect
        activePlatformStore.available
            .filter { sessionStates[it] is SessionState.SignedIn }
            .singleOrNull()
            ?.let { activePlatformStore.setActive(it) }
    }
    val context = LocalContext.current
    val googleSignIn = remember(context) { GoogleSignInClient(context) }

    // Assembled here rather than inside the diagnostics module: every controller the hub reaches is
    // already in this scope, and giving core:diagnostics a dependency on all of them would make the
    // module that observes the app depend on nearly the whole app.
    // Only the relay's own status, not the whole table: the table is replaced on every five-second
    // poll and the hub would be rebuilt with it. The shell's bar collects the same store further
    // down, mapped the same way and for the same reason.
    val relayStatus by remember(marketTickerStore) {
        marketTickerStore.state.map { it.table.priceFeed }
    }.collectAsStateWithLifecycle(null)
    val hub = ControlHub(
        sessions = activePlatformStore.available.map { platform ->
            SessionRow(
                platform = platform,
                signedIn = sessionStates[platform] is SessionState.SignedIn,
                detail = (sessionStates[platform] as? SessionState.RevalidationRequired)?.message?.resolve(),
            )
        },
        feed = FeedStatus(
            tone = marketState.connection.tone(),
            label = stringResource(marketState.connection.labelRes()),
            subscribedSymbols = marketState.quotes.size,
            cacheAgeLabel = marketState.cacheStoredAtEpochMillis?.let { BidiText.isolateLtr(it.toString()) },
        ),
        push = PushStatus(
            permission = notificationPermissionState.toHubPermission(),
            // Null rather than false: the app has not asked the server yet, and reporting an
            // unasked capability as off would put words in the server's mouth.
            serverEnabled = null,
            newSignals = notificationState.preferences.newSignals,
            signalUpdates = notificationState.preferences.signalUpdates,
            priceAlerts = notificationState.preferences.priceAlerts,
        ),
        venue = venueState.forPlatform(activePlatform),
        // The exchange's own relay, as the server reports it on the ticker envelope. Absent — not
        // green — where the server does not send the field, which is every deployment older than
        // 2026-08-29 and is a state an operator has to be able to tell from health.
        relay = relayStatus?.let { feed ->
            RelayStatus(
                tier = feed.tier.name.lowercase(),
                socketsUp = feed.socketsUp,
                socketsTotal = feed.socketsTotal,
                tickAgeMillis = feed.tickAgeMillis,
                degraded = feed.degraded,
                fullOutage = feed.fullOutage,
            )
        },
        // What each server said about itself, per platform. Null inside a row means that server
        // has not answered yet, which the panel draws differently from a capability reported off —
        // the whole point of asking is to tell those two apart.
        capabilities = capabilities.mapValues { (_, methods) ->
            ServerCapabilities(
                emailPassword = methods.emailPassword,
                google = methods.google,
                telegram = methods.telegram,
                push = methods.push,
                chartVision = methods.chartVision,
            )
        },
    )

    val hubActions = HubActions(
        onSelectPlatform = { platform ->
            adminController.select(platform)
            platformChosen = true
            scope.launch { activePlatformStore.setActive(platform) }
        },
        onSignOut = { platform -> scope.launch { platformSessions.logout(platform) } },
        onSignOutEverywhere = { scope.launch { platformSessions.logoutAll() } },
        onRestartFeed = marketDataController::retry,
        onSyncNow = backgroundSyncScheduler::requestImmediate,
        onClearMarketCache = { scope.launch { marketDataCache.clear() } },
        onRequestPushPermission = onRequestNotificationPermission,
        onOpenPushSettings = onOpenNotificationSettings,
        onReRegisterPushToken = { scope.launch { pushCoordinator.registerCurrentToken() } },
        onSetPushPreference = { key, value ->
            val current = notificationState.preferences
            notificationController.updatePreferences(
                when (key) {
                    PushPreferenceKey.NEW_SIGNALS -> current.copy(newSignals = value)
                    PushPreferenceKey.SIGNAL_UPDATES -> current.copy(signalUpdates = value)
                    PushPreferenceKey.PRICE_ALERTS -> current.copy(priceAlerts = value)
                },
            )
        },
        onProbe = adminController::probe,
        onToggleFailuresOnly = adminController::toggleFailuresOnly,
        onClearRequests = adminController::clearRequests,
        // The panel's own levers. The clipboard and the file are done inside `feature:admin`,
        // which owns the FileProvider write and the document picker — this side only names the
        // controller calls, so the shell has no opinion about how a report leaves the device.
        onUnlock = adminController::unlock,
        onCredentialEdited = adminController::editingCredentials,
        onLock = adminController::lock,
        onShowSection = adminController::show,
        onSetMinimumLevel = adminController::setMinimumLevel,
        onToggleTag = adminController::toggleTag,
        onSetQuery = adminController::setQuery,
        onSetWindow = adminController::setWindow,
        onClearFilter = adminController::clearFilter,
        onExpandEntry = adminController::expand,
        onClearLog = adminController::clearLog,
        onSetVerbosity = adminController::setVerbosity,
        onClearCrash = adminController::clearCrash,
        onExported = adminController::exported,
        onObserveInstall = adminController::observe,
    )

    // Collected here rather than inside the theme so the whole tree — including the sign-in and
    // guest branches below — reads one value. `SYSTEM` is the initial, which is also what an
    // install predating this setting has, so nothing flickers on first frame.
    val themeMode by userPreferencesStore.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    // `true` initially rather than false: the first frame arrives before the callback does, and an
    // offline bar that flashes on every cold start would be the app crying wolf once per launch.
    val online by networkStatus.online.collectAsStateWithLifecycle(initialValue = true)
    val marketColors by userPreferencesStore.marketColors
        .collectAsStateWithLifecycle(MarketColorScheme.GREEN_UP)
    // `false` initially, which is also the stored default. Starting `true` would flash a lock
    // screen at every reader who has never turned it on.
    val appLockEnabled by userPreferencesStore.appLockEnabled.collectAsStateWithLifecycle(false)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    CoineProTheme(
        darkTheme = darkTheme,
        risingIsGreen = marketColors == MarketColorScheme.GREEN_UP,
    ) {
        // One toaster for the whole tree, so a composable anywhere below can report a finished
        // action without a `Scaffold` and a `SnackbarHostState` being threaded to it. See
        // `CoineProToast`.
        ProvideToaster {
        // Inside the theme, so the lock screen is drawn in the reader's own palette, and around
        // *everything*, so nothing of the app composes behind it — a screenshot in the recents
        // list cannot show a balance that is supposed to be behind a fingerprint.
        BiometricGate(
            enabled = appLockEnabled,
            lockedContent = { unlock -> LockScreen(onUnlock = unlock) },
        ) {
        when (val current = session) {
            is SessionState.SignedIn -> MainShell(
                guest = false,
                profile = profile,
                notificationSettingsStore = notificationSettingsStore,
                localAlertStore = localAlertStore,
                localAlertScheduler = localAlertScheduler,
                accountName = current.profile.name,
                accountEmail = current.profile.email,
                onSetDisplayName = { name -> scope.launch { profileStore.setDisplayName(name) } },
                onSetTagline = { line -> scope.launch { profileStore.setTagline(line) } },
                onSetAvatar = { spec -> scope.launch { profileStore.setAvatar(spec) } },
                onToggleBalanceHidden = {
                    scope.launch { profileStore.setBalanceHidden(!profile.balanceHidden) }
                },
                onSignIn = null,
                guestController = guestController,
                membershipController = membershipController,
                marketState = marketState,
                marketSearchController = marketSearchController,
                screenerController = screenerController,
                candleGateway = candleGateways.getValue(activePlatform),
                orderBookGateway = orderBookGateways.getValue(activePlatform),
                candleCache = candleCache,
                candleArchive = candleArchive,
                chartDrawingStore = chartDrawingStore,
                drawingImageStore = drawingImageStore,
                chartLayoutStore = chartLayoutStore,
                symbolChartStateStore = symbolChartStateStore,
                drawingTemplateStore = drawingTemplateStore,
                indicatorTemplateStore = indicatorTemplateStore,
                drawingSyncStore = drawingSyncStore,
                timeZonePrefStore = timeZonePrefStore,
                intervalFavouritesStore = intervalFavouritesStore,
                chartWorkspaceStore = chartWorkspaceStore,
                portfolioController = portfolioControllers.getValue(activePlatform),
                academyController = academyController,
                terminalController = terminalController,
                hasAcademy = activePlatform == MarketPlatform.COINEPRO_FX,
                adminController = adminController,
                appLog = appLog,
                hub = hub,
                hubActions = hubActions,
                briefing = briefingState.toHomeBriefing(briefingReadAt),
                portfolio = portfolioState.toHomePortfolio(),
                subscription = current.entitlement.toHomeSubscription(),
                watchlist = watchlist,
                watchlistStore = watchlistStore,
                onToggleWatch = { symbol -> scope.launch { watchlistStore.toggle(symbol) } },
                onRefreshAccount = accountController::refresh,
                signalController = signalController,
                notificationController = notificationController,
                alertsController = alertsController,
                inAppAlerts = inAppAlerts,
                executionController = executionController,
                copyTradeController = copyTradeController,
                aiSignalController = aiSignalController,
                aiVisionController = aiVisionController,
                aiAssistantController = aiAssistantController,
                marketIntelController = marketIntelController,
                announcementsController = announcementsController,
                marketTickerStore = marketTickerStore,
                watchlistSyncController = watchlistSyncController,
                chartEventController = chartEventController,
                accountController = accountController,
                launchSignalId = launchSignalId,
                launchActivity = launchActivity,
                launchSymbol = launchSymbol,
                launchTimeframe = launchTimeframe,
                onSymbolLaunchConsumed = onSymbolLaunchConsumed,
                notificationPermissionState = deliverablePermissionState,
                chartVisionAvailable = chartVisionAvailable,
                pushAvailable = pushAvailable,
                assistantAvailable = assistantAvailable,
                aiSignalsAvailable = aiSignalsAvailable,
                accountDeletionAvailable = accountDeletionAvailable,
                journalController = journalController,
                paperTradeController = paperTradeController,
                scriptController = scriptController,
                chartLayouts = chartLayouts,
                onSaveLayout = { layout -> scope.launch { chartLayoutStore.save(layout) } },
                onDeleteLayout = { id -> scope.launch { chartLayoutStore.delete(id) } },
                onSignalLaunchConsumed = onSignalLaunchConsumed,
                onActivityLaunchConsumed = onActivityLaunchConsumed,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onSendFeedback = onSendFeedback,
                onMarketRetry = marketDataController::retry,
                onSubscribeSymbols = marketDataController::subscribe,
                platforms = activePlatformStore.available,
                activePlatform = activePlatform,
                onSelectPlatform = { platform ->
                    // Recorded before the switch, so the follow-the-session effect above sees the
                    // choice on the same frame it sees the new platform.
                    platformChosen = true
                    scope.launch { activePlatformStore.setActive(platform) }
                },
                onLogout = {
                    scope.launch {
                        pushCoordinator.unregisterCurrentToken()
                        // The name and the face go with the session. The next person to open this
                        // app on this phone is not necessarily the same person, and a stranger's
                        // photograph over a stranger's name is the loudest possible way to tell
                        // them the sign-out did not work.
                        profileStore.clear()
                        // Every platform, not the one on screen. «خروج» means leaving, and a reader
                        // who holds a session on the other backend would otherwise be signed out
                        // and then silently moved onto it by the effect that follows a session.
                        platformSessions.logoutAll()
                    }
                },
                appLockEnabled = appLockEnabled,
                onSetAppLockEnabled = { on -> scope.launch { userPreferencesStore.setAppLockEnabled(on) } },
                themeMode = themeMode,
                onSetThemeMode = { mode -> scope.launch { userPreferencesStore.setThemeMode(mode) } },
                marketColors = marketColors,
                onSetMarketColors = { scheme -> scope.launch { userPreferencesStore.setMarketColors(scheme) } },
                online = online,
            )
            // Signing in is the email flow's job now. The other two states are not sign-in at all —
            // one is a session being restored, the other a session that exists but could not be
            // revalidated — and putting credential fields in front of either would ask the reader
            // to solve a problem that is not theirs.
            SessionState.SignedOut -> {
                LaunchedEffect(emailAuthController) {
                    emailAuthController.loadMethods()
                    // Picks up a registration that was started and not finished — the reader left
                    // for their inbox and the process was killed while they were gone.
                    emailAuthController.resume()
                }
                // Arriving on a recovery link means the reader is mid-recovery, so the screen opens
                // where they left off rather than on a sign-in form they cannot yet complete.
                LaunchedEffect(launchResetToken) {
                    if (launchResetToken != null) {
                        emailAuthController.goTo(EmailAuthStep.RESET_PASSWORD)
                    }
                }

                // Signed out is not the same as unwelcome, and it is no longer a smaller app.
                //
                // This was a sign-in form, then a single scrolling page of prices in front of one.
                // Both were the same mistake at different sizes: they made somebody who had not
                // signed in look at a *preview* of the product rather than the product. What runs
                // here now is the app — the same shell, the same bottom bar, the same markets list
                // over the same several hundred instruments, the same chart on the same candles,
                // the same toolkit, the same profile with the reader's own avatar in it.
                //
                // What makes that honest rather than a trick is that TradeYar publishes the routes
                // for it. `api/v1/public/prices` is the whole universe, `api/v1/public/candles`
                // runs the *same server code* as the signed-in route — their note, and it is why
                // the numbers agree — and the news and the closed-signal record are published too.
                // So the guest is not being shown a mock: they are being shown the product, as
                // much of it as can honestly be given away.
                //
                // Two tabs need an account and say so once, without a wall and without anything
                // blurred behind them. Nobody is pushed: «به زور کسی رو ما ثبت نام نمی‌کنیم».
                //
                // Saveable, so a rotation mid-password does not throw the reader back to the
                // market with a half-typed form gone.
                var signingIn by rememberSaveable { mutableStateOf(false) }
                val showForm = signingIn || launchResetToken != null

                if (!showForm) {
                    // Built here rather than in Hilt because they are the guest's alone and their
                    // lifetime is this branch: signing in disposes them along with the shell.
                    val guestCatalog = remember(guestGateway) { GuestMarketCatalogGateway(guestGateway) }
                    val guestCandles = remember(guestGateway) { GuestCandleGateway(guestGateway) }
                    val guestSearch = remember(guestCatalog, scope) {
                        MarketSearchController(gateway = guestCatalog, scope = scope)
                    }
                    // The guest's own screener, on the guest's own catalogue and candles. The
                    // signed-in one reads authenticated routes, which for a guest is a 401 worded
                    // as an outage over a screen that needs no account at all. No live quote feed
                    // here — the catalogue's prices simply do not tick, which is the honest
                    // degradation rather than a broken screen. Saved screens are the same file,
                    // because a filter belongs to the phone rather than to a session.
                    val guestScreener = remember(guestCatalog, guestCandles, screenerStore, scope) {
                        ScreenerController(
                            gateway = guestCatalog,
                            scope = scope,
                            barSource = CandleScreenerBarSource(guestCandles),
                            store = screenerStore,
                        )
                    }
                    MainShell(
                        guest = true,
                        profile = profile,
                        notificationSettingsStore = notificationSettingsStore,
                        localAlertStore = localAlertStore,
                        localAlertScheduler = localAlertScheduler,
                        accountName = null,
                        accountEmail = null,
                        onSetDisplayName = { name -> scope.launch { profileStore.setDisplayName(name) } },
                        onSetTagline = { line -> scope.launch { profileStore.setTagline(line) } },
                        onSetAvatar = { spec -> scope.launch { profileStore.setAvatar(spec) } },
                        onToggleBalanceHidden = {
                            scope.launch { profileStore.setBalanceHidden(!profile.balanceHidden) }
                        },
                        onSignIn = { signingIn = true },
                        guestController = guestController,
                        membershipController = membershipController,
                        // Empty rather than the signed-in feed's, which is stopped while signed
                        // out. Nothing a guest reaches reads it: their home is the public one and
                        // their markets list reads the catalogue below.
                        marketState = MarketDataState(),
                        marketSearchController = guestSearch,
                        screenerController = guestScreener,
                        candleGateway = guestCandles,
                        // The signed-in gateway, not a guest one, because there is no guest depth
                        // route to build one against. On crypto it answers 404 today and the
                        // ladder says «هنوز سرو نمی‌شود» — the same sentence a member gets, which
                        // is the truth for both of them.
                        orderBookGateway = orderBookGateways.getValue(activePlatform),
                        candleCache = candleCache,
                        candleArchive = candleArchive,
                        chartDrawingStore = chartDrawingStore,
                        drawingImageStore = drawingImageStore,
                        chartLayoutStore = chartLayoutStore,
                        symbolChartStateStore = symbolChartStateStore,
                        drawingTemplateStore = drawingTemplateStore,
                        indicatorTemplateStore = indicatorTemplateStore,
                        drawingSyncStore = drawingSyncStore,
                        timeZonePrefStore = timeZonePrefStore,
                        intervalFavouritesStore = intervalFavouritesStore,
                        chartWorkspaceStore = chartWorkspaceStore,
                        portfolioController = portfolioControllers.getValue(activePlatform),
                        academyController = academyController,
                        terminalController = terminalController,
                        // No academy for a guest: its routes are behind the academy scope, which is
                        // minted from a mobile token nobody here holds.
                        hasAcademy = false,
                        adminController = adminController,
                        appLog = appLog,
                        hub = hub,
                        hubActions = hubActions,
                        briefing = HomeBriefing.Resting,
                        portfolio = null,
                        subscription = null,
                        watchlist = watchlist,
                        watchlistStore = watchlistStore,
                        onToggleWatch = { symbol -> scope.launch { watchlistStore.toggle(symbol) } },
                        onRefreshAccount = {},
                        signalController = signalController,
                        notificationController = notificationController,
                        alertsController = alertsController,
                        inAppAlerts = inAppAlerts,
                        executionController = executionController,
                        copyTradeController = copyTradeController,
                        aiSignalController = aiSignalController,
                        aiVisionController = aiVisionController,
                        aiAssistantController = aiAssistantController,
                        marketIntelController = marketIntelController,
                        announcementsController = announcementsController,
                        marketTickerStore = marketTickerStore,
                        watchlistSyncController = watchlistSyncController,
                        chartEventController = chartEventController,
                        accountController = accountController,
                        launchSignalId = null,
                        launchActivity = false,
                        notificationPermissionState = deliverablePermissionState,
                        chartVisionAvailable = false,
                        pushAvailable = false,
                        assistantAvailable = false,
                        aiSignalsAvailable = false,
                        accountDeletionAvailable = false,
                        journalController = journalController,
                        paperTradeController = paperTradeController,
                        scriptController = scriptController,
                        chartLayouts = chartLayouts,
                        onSaveLayout = { layout -> scope.launch { chartLayoutStore.save(layout) } },
                        onDeleteLayout = { id -> scope.launch { chartLayoutStore.delete(id) } },
                        onSignalLaunchConsumed = onSignalLaunchConsumed,
                        onActivityLaunchConsumed = onActivityLaunchConsumed,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onSendFeedback = onSendFeedback,
                        onMarketRetry = { guestSearch.refresh() },
                        onSubscribeSymbols = {},
                        // One platform, and no switcher. The public routes are TradeYar's; a
                        // control offering CoinePro-FX would offer a feed with no public side.
                        platforms = listOf(MarketPlatform.TRADEYAR),
                        activePlatform = MarketPlatform.TRADEYAR,
                        onSelectPlatform = {},
                        onLogout = { signingIn = true },
                        appLockEnabled = appLockEnabled,
                        onSetAppLockEnabled = { on -> scope.launch { userPreferencesStore.setAppLockEnabled(on) } },
                        themeMode = themeMode,
                        onSetThemeMode = { mode -> scope.launch { userPreferencesStore.setThemeMode(mode) } },
                        marketColors = marketColors,
                        onSetMarketColors = { scheme -> scope.launch { userPreferencesStore.setMarketColors(scheme) } },
                        online = online,
                        launchSymbol = launchSymbol,
                        launchTimeframe = launchTimeframe,
                        onSymbolLaunchConsumed = onSymbolLaunchConsumed,
                    )
                    return@BiometricGate
                }

                EmailAuthScreen(
                    state = emailAuthState,
                    onSignIn = emailAuthController::signIn,
                    onRegister = emailAuthController::startRegistration,
                    onVerify = emailAuthController::verifyCode,
                    onStartOver = emailAuthController::startOver,
                    onRequestReset = emailAuthController::requestPasswordReset,
                    onResetPassword = { token, password ->
                        onResetTokenConsumed()
                        emailAuthController.resetPassword(token, password)
                    },
                    onGoTo = emailAuthController::goTo,
                    onRetryMethods = emailAuthController::loadMethods,
                    onGoogleSignIn = {
                        // The audience is the server's own client id, not one compiled in: the two
                        // deployments have separate Google configuration, and a token minted for
                        // one carries an `aud` the other refuses.
                        val audience = emailAuthState.methods.googleClientId
                        if (!audience.isNullOrBlank()) {
                            scope.launch {
                                when (val outcome = googleSignIn.requestIdToken(audience)) {
                                    is GoogleSignInOutcome.Token ->
                                        emailAuthController.signInWithGoogle(outcome.idToken)
                                    // Closing the sheet is a decision, not a failure. Saying
                                    // anything here would report a problem where there was none.
                                    GoogleSignInOutcome.Cancelled -> Unit
                                    is GoogleSignInOutcome.Failed ->
                                        emailAuthController.reportGoogleFailure(outcome.message)
                                }
                            }
                        }
                    },
                    initialResetToken = launchResetToken.orEmpty(),
                )
            }
            else -> AuthScreen(
                state = session,
                loginConfigState = loginConfigState,
                onRetryLoginConfig = { scope.launch { sessionController.prepareLogin() } },
                onRetry = { scope.launch { platformSessions.controller(activePlatform).restore() } },
                onLogout = { scope.launch { platformSessions.logoutAll() } },
            )
        }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    /**
     * Whether nobody is signed in.
     *
     * This is the whole of the guest work in one parameter, and the point of putting it here rather
     * than in a second shell is that there is no second shell: a guest gets this Scaffold, this
     * bottom bar, this navigation graph and these screens. Five destinations change what they draw
     * — Home becomes the public one, Signals and the AI become an offer rather than a wall, the
     * toolkit hides the three cards that need a session, and the profile loses the account rows —
     * and everything else is byte-for-byte the app a member uses.
     *
     * A separate guest shell would drift within a release, and the one that drifted would be the
     * one nobody on the team looks at.
     */
    guest: Boolean,
    /** The reader's own name, face and line, whether or not they have an account. */
    profile: StoredProfile,
    notificationSettingsStore: NotificationSettingsStore,
    localAlertStore: LocalAlertStore,
    localAlertScheduler: LocalAlertScheduler,
    /** What the server calls this reader, and where to reach them. Null for a guest. */
    accountName: String?,
    accountEmail: String?,
    onSetDisplayName: (String?) -> Unit,
    onSetTagline: (String?) -> Unit,
    onSetAvatar: (AvatarSpec) -> Unit,
    /** Puts the balance behind dots, or takes it back out. */
    onToggleBalanceHidden: () -> Unit,
    /** Offered on the guest surfaces. Null when there is already a session. */
    onSignIn: (() -> Unit)?,
    /** The public feed's controller — Home and the two gates read it when [guest] is true. */
    guestController: GuestController,
    membershipController: MembershipController,
    marketState: MarketDataState,
    marketSearchController: MarketSearchController,
    /** The screener for the platform on screen. Its saved screens are shared across both. */
    screenerController: ScreenerController,
    /** The candle source for the platform on screen. See the chart route below. */
    candleGateway: CandleGateway,
    /** The order book for the platform on screen. See [DOM_PATTERN]. */
    orderBookGateway: OrderBookGateway,
    /** The bars already held, so a chart draws before it fetches. */
    candleCache: CandleCache,
    /** Every bar ever fetched, so paging back deepens across sessions. See [CandleArchive]. */
    candleArchive: CandleArchive,
    chartDrawingStore: ChartDrawingStore,
    /** Where the image drawing tool's pictures live. See `DrawingImageStore`. */
    drawingImageStore: DrawingImageStore,
    /** For the two things the layout list cannot answer: which one was last applied, and recording it. */
    chartLayoutStore: ChartLayoutStore,
    /** Threaded to the chart routes, which hand it to the controller before its first fetch. */
    symbolChartStateStore: SymbolChartStateStore,
    /**
     * The reader's saved per-tool drawing styles, for the chart and the studio both.
     *
     * Handed to the screens as the store rather than as a list, because they read templates for
     * the armed tool and for the drawing being edited — two queries that change as the reader
     * works, and hoisting either would put the screen's own state up here.
     */
    drawingTemplateStore: DrawingTemplateStore,
    indicatorTemplateStore: IndicatorTemplateStore,
    drawingSyncStore: DrawingSyncStore,
    timeZonePrefStore: TimeZonePrefStore,
    intervalFavouritesStore: IntervalFavouritesStore,
    /**
     * How the chart screen itself is arranged: the split with the watchlist, and what the two
     * panes tie together. Without it a drag on the divider is forgotten the moment the chart is
     * left, and the second pane opens on nothing every time.
     */
    chartWorkspaceStore: ChartWorkspaceStore,
    portfolioController: PortfolioController,
    academyController: AcademyController,
    terminalController: TerminalController,
    /**
     * Whether this platform has an academy at all.
     *
     * CoinePro-FX does; TradeYar has no `/academy` surface. Offering the entry on both and letting
     * the second fail would report an absent feature as an outage.
     */
    hasAcademy: Boolean,
    signalController: SignalController,
    notificationController: NotificationController,
    /** The alert centre, at [ALERTS_ROUTE]. Not per platform: these alerts are the phone's own. */
    alertsController: AlertsController,
    /** Firings to show while the app is open. See the collector below. */
    inAppAlerts: InAppAlertBus,
    executionController: ExecutionController,
    copyTradeController: CopyTradeController,
    aiSignalController: AiSignalController,
    aiVisionController: AiVisionController,
    aiAssistantController: AiAssistantController,
    marketIntelController: MarketIntelController,
    /** Null where the platform has no announcements route. See the shell's own note. */
    announcementsController: AnnouncementsController?,
    /** The day's figures for the platform on screen. See the shell's own note. */
    marketTickerStore: MarketTickerStore,
    /** Null-free: the controller reports the feature absent where the platform has no route. */
    watchlistSyncController: WatchlistSyncController,
    /** This platform's chart-axis events, or null when the platform is not configured. */
    chartEventController: ChartEventController?,
    accountController: AccountController,
    adminController: AdminController,
    appLog: AppLog,
    hub: ControlHub,
    hubActions: HubActions,
    briefing: HomeBriefing,
    portfolio: HomePortfolio?,
    subscription: HomeSubscription?,
    onRefreshAccount: () -> Unit,
    launchSignalId: Long?,
    launchActivity: Boolean,
    /** A market to open, from a widget row. Consumed once so a rotation does not re-navigate. */
    launchSymbol: String?,
    /** The bar a deep link asked for, or null. Consumed with [launchSymbol]. See `AlertDeepLink`. */
    launchTimeframe: String?,
    onSymbolLaunchConsumed: () -> Unit,
    notificationPermissionState: NotificationPermissionUiState,
    /** What this deployment reports it can do. A feature it does not offer is not drawn. */
    chartVisionAvailable: Boolean,
    pushAvailable: Boolean,
    assistantAvailable: Boolean,
    aiSignalsAvailable: Boolean,
    accountDeletionAvailable: Boolean,
    journalController: JournalController,
    paperTradeController: PaperTradeController,
    scriptController: ScriptController,
    chartLayouts: List<ChartLayout>,
    onSaveLayout: (ChartLayout) -> Unit,
    onDeleteLayout: (String) -> Unit,
    watchlist: List<String>,
    /**
     * The store behind [watchlist], for the surfaces that need more than the active list's tickers.
     *
     * Both, not one: nearly every caller wants only the flat list of the active list's symbols, and
     * making them all collect it themselves would put a `Flow` read in a dozen composables. The
     * markets tab is the one that needs the lists, the flags and the columns, so it takes the store.
     */
    watchlistStore: WatchlistStore,
    onToggleWatch: (String) -> Unit,
    onSignalLaunchConsumed: () -> Unit,
    onActivityLaunchConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSendFeedback: () -> Unit,
    onMarketRetry: () -> Unit,
    /** Narrows the live price feed to what the markets list is showing. */
    onSubscribeSymbols: (Set<String>) -> Unit = {},
    onLogout: () -> Unit,
    /** Whether the app asks for a fingerprint when it opens, and how to change it. */
    appLockEnabled: Boolean,
    onSetAppLockEnabled: (Boolean) -> Unit,
    /** Which palette this reader pinned, and how to change it. See [ThemeMode]. */
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    /** Which colour a rise is drawn in. See `MarketColorScheme`. */
    marketColors: MarketColorScheme,
    onSetMarketColors: (MarketColorScheme) -> Unit,
    /** Whether the phone has a network at all. See [CoineProOfflineBar]. */
    online: Boolean,
    platforms: List<MarketPlatform>,
    activePlatform: MarketPlatform,
    onSelectPlatform: (MarketPlatform) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val sparklineScope = rememberCoroutineScope()
    // Two overlays the whole shell owns, because both are reached from the profile list and
    // neither belongs to a navigation destination: a sheet a reader picks a palette in, and the
    // question asked before a sign-out throws away both platforms' tokens.
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var appLockOpen by rememberSaveable { mutableStateOf(false) }
    /** The symbol and price a reader asked to be alerted about, from the chart. */
    var alertFromChart by remember { mutableStateOf<Pair<String, Double>?>(null) }

    /**
     * The instrument the chart destination currently has in front of the reader, hoisted here.
     *
     * The bar needs it and the bar is outside the destination. It cannot be taken from the route
     * argument: the watchlist strip under the chart swaps the symbol *in place* without
     * navigating, so `chart/{symbol}` keeps naming the market the reader started from. The chart
     * route below reports its live symbol into this, which is what lets the depth entry open the
     * ladder on the market actually on screen rather than on one several taps ago.
     *
     * Saveable, so a rotation on the chart does not empty the bar.
     */
    var chartSymbolOnScreen by rememberSaveable { mutableStateOf("") }
    val shellScope = rememberCoroutineScope()
    // What this phone can do about proving who is holding it. Read once — it changes only when
    // somebody enrols a fingerprint, which happens outside this app.
    val lockCapability = rememberLockCapability()
    var signOutAsked by rememberSaveable { mutableStateOf(false) }
    // What a finished action says back. Before this, no successful action anywhere in the app gave
    // any feedback at all — a saved layout, a created alert, a copied fingerprint all completed in
    // silence, which is indistinguishable from a tap that missed. See `CoineProToast`.
    val toaster = LocalToaster.current
    // Resolved once, here, rather than at each call site: `stringResource` is a composable and the
    // places these are used are lambdas that are not.
    val copiedMessage = stringResource(R.string.toast_copied)
    val alertSavedMessage = stringResource(R.string.toast_alert_saved)
    val deletedMessage = stringResource(R.string.toast_deleted)
    val layoutSavedMessage = stringResource(R.string.toast_layout_saved)
    val undoLabel = stringResource(R.string.action_undo)
    val openChartLabel = stringResource(R.string.alert_toast_open)

    // The layout callbacks, with a sentence added. Wrapped once here rather than at the two
    // screens that take them, so the chart and the studio cannot disagree about whether saving
    // says anything.
    val onSaveLayoutAnnounced: (ChartLayout) -> Unit = { layout ->
        onSaveLayout(layout)
        toaster.show(layoutSavedMessage, ToastTone.SUCCESS)
    }
    // Deleting one offers it straight back rather than asking first.
    //
    // That is the rule `CoineProConfirmDialog` states, applied the other way round: a question is
    // a tax on everybody who meant it, and it is only worth charging where recovery is otherwise
    // impossible. A layout is a name, a timeframe, a chart type and a list of indicator ids — all
    // of it still in hand at the moment of deletion — so an undo recovers it exactly and costs the
    // reader who meant it nothing at all.
    val onDeleteLayoutAnnounced: (String) -> Unit = { id ->
        val removed = chartLayouts.firstOrNull { it.id == id }
        onDeleteLayout(id)
        toaster.show(
            CoineProToast(
                message = deletedMessage,
                tone = ToastTone.NEUTRAL,
                actionLabel = removed?.let { undoLabel },
                onAction = removed?.let { { onSaveLayout(it) } },
            ),
        )
    }
    // Keyed on the gateway, so switching platform builds a new store rather than drawing a
    // forex line beside a crypto price. The scope is the composition's: leaving the app cancels
    // whatever is in flight.
    val sparklineStore = remember(candleGateway) { SparklineStore(candleGateway, sparklineScope) }
    // The charts, held here rather than inside their own destinations. See `ChartControllers`:
    // one controller per destination is what made every drawing tool in the app inert.
    val chartControllers = rememberChartControllers(
        gateway = candleGateway,
        scope = sparklineScope,
        drawings = chartDrawingStore,
        images = drawingImageStore,
        log = appLog,
        cache = candleCache,
        archive = candleArchive,
    )

    // The prices the chart's watchlist strip and the two-pane pickers put beside their tickers,
    // taken from the feed already running for the platform on screen rather than from a second
    // subscription. Keyed uppercase because that is how those strips look a symbol up: a reader
    // who starred «btcusdt» from a search result must not get a blank row for it.
    //
    // A guest's shell passes an empty [MarketDataState] — see the note at that call site — so the
    // strips there draw tickers with no figures, which is what they are built to do.
    val watchlistQuotes: Map<String, WatchlistQuote> = remember(marketState.quotes) {
        marketState.quotes.entries.associate { (ticker, quote) ->
            ticker.uppercase() to WatchlistQuote(
                symbol = ticker.uppercase(),
                price = quote.price,
                changePercent = quote.changePercent,
            )
        }
    }
    val currentRoute = backStackEntry?.destination?.route

    // Every screen the reader reaches, in sequence. It is two lines and it is the single most
    // useful thing in a bug report: "it crashed" becomes "it crashed on the chart, having come
    // from search, forty seconds after a socket drop".
    //
    // The route *pattern* is logged, never the filled path — `chart/{symbol}` and not
    // `chart/BTCUSDT`. A path carries arguments, and an argument on some other route is an id or
    // an email; the pattern says which screen without ever saying whose data.
    LaunchedEffect(currentRoute) {
        currentRoute?.let { appLog.info(LogTag.NAVIGATION, it) }
    }
    val isSubScreen = currentRoute in setOf(
        SIGNAL_DETAIL_PATTERN,
        EXECUTION_PATTERN,
        CONNECTIONS_ROUTE,
        MARKET_SEARCH_ROUTE,
        CHART_PATTERN,
        PROFILE_ROUTE,
        NOTIFICATIONS_ROUTE,
        MEMBERSHIP_ROUTE,
        PORTFOLIO_ROUTE,
        PORTFOLIO_REPORT_ROUTE,
        ACADEMY_ROUTE,
        LESSON_PATTERN,
        TERMINAL_ROUTE,
        AI_VISION_ROUTE,
        AI_ASSISTANT_ROUTE,
        KYC_ROUTE,
        DELETE_ACCOUNT_ROUTE,
        ALERTS_ROUTE,
        JOURNAL_ROUTE,
        PAPER_TRADE_ROUTE,
        SCRIPT_PATTERN,
        STUDIO_PATTERN,
        PANES_PATTERN,
        DOM_PATTERN,
        // Tools, Activity, News and the calendar are **not** sub-screens and used to be listed
        // here. A sub-screen loses the bottom bar, which is right for a chart or a lesson — a
        // place you are inside and leave by going back. These four are places a reader *goes*,
        // and stripping the bar made Tools in particular a dead end: it fans out to eight
        // destinations, so a reader who wanted Markets from there had to go back first. That is
        // most of why the toolkit felt buried.
        LAUNCH_READINESS_ROUTE,
        ADMIN_ROUTE,
    )
    // How much glass there is, read once for the whole shell. `CoineProTheme` provides it, so this
    // is the same answer every screen underneath gets — which is what stops a rail and a bottom bar
    // being drawn at the same time.
    val window = coineProWindowClass()

    val subTitleRes = when (currentRoute) {
        ADMIN_ROUTE -> R.string.screen_diagnostics
        SIGNAL_DETAIL_PATTERN -> R.string.screen_signal_detail
        EXECUTION_PATTERN -> R.string.screen_execution
        // Named for what the screen actually is on each platform. "Connections" over a
        // copy-trading screen is not wrong so much as unhelpful: it is the reader's word for the
        // wrong feature.
        CONNECTIONS_ROUTE -> when (activePlatform) {
            MarketPlatform.COINEPRO_FX -> R.string.screen_copy_trading
            MarketPlatform.TRADEYAR -> R.string.screen_connections
        }
        PROFILE_ROUTE -> R.string.screen_profile
        NOTIFICATIONS_ROUTE -> R.string.screen_notifications
        MEMBERSHIP_ROUTE -> R.string.screen_membership
        KYC_ROUTE -> R.string.screen_kyc
        DELETE_ACCOUNT_ROUTE -> R.string.screen_delete_account
        ALERTS_ROUTE -> R.string.screen_alerts
        JOURNAL_ROUTE -> R.string.screen_journal
        PAPER_TRADE_ROUTE -> R.string.screen_paper_trade
        SCRIPT_PATTERN -> R.string.screen_script
        STUDIO_PATTERN -> R.string.screen_chart_studio
        TOOLS_ROUTE -> R.string.screen_tools
        ACTIVITY_ROUTE -> R.string.screen_activity
        AI_VISION_ROUTE -> R.string.screen_ai_vision
        AI_ASSISTANT_ROUTE -> R.string.screen_ai_assistant
        MARKET_SEARCH_ROUTE -> R.string.screen_market_search
        // The chart names itself: its header is the symbol, which is more use than the word
        // "chart" over a screen that is obviously one.
        CHART_PATTERN -> R.string.screen_chart
        // The report is a reading of the portfolio, so the bar keeps saying «سبد» rather than
        // introducing a second word for the same account.
        PORTFOLIO_ROUTE, PORTFOLIO_REPORT_ROUTE -> R.string.screen_portfolio
        // The lesson names itself in its own heading, so the bar carries the section instead.
        ACADEMY_ROUTE, LESSON_PATTERN -> R.string.screen_academy
        TERMINAL_ROUTE -> R.string.screen_terminal
        NEWS_ROUTE -> R.string.screen_news
        CALENDAR_ROUTE -> R.string.screen_calendar
        LAUNCH_READINESS_ROUTE -> R.string.screen_launch_readiness
        else -> R.string.app_name
    }
    // Home draws its own header — the greeting and the balance are the page's title — so a bar on
    // top of it would be a second one saying less.
    val showTopBar = isSubScreen || currentRoute != AppDestination.HOME.route

    /**
     * Whether this destination has somewhere to go back **to**.
     *
     * The five bottom-bar roots do not: they are reached by tapping the bar, and an arrow on one of
     * them would pop to whichever root the reader happened to visit before, which is not "back" in
     * any sense they would recognise. Everything else was navigated into and needs the arrow —
     * including the six screens that keep the bottom bar, which is where this was wrong.
     */
    val canGoBack = currentRoute != null && AppDestination.entries.none { it.route == currentRoute }

    LaunchedEffect(launchSignalId) {
        launchSignalId?.let { signalId ->
            navController.navigate(signalDetailRoute(signalId)) { launchSingleTop = true }
            onSignalLaunchConsumed()
        }
    }
    // A widget row. The symbol has already been shape-checked in `parseCoineProDeepLink` — this
    // scheme is unverified, so what arrives is an arbitrary string from an untrusted sender.
    LaunchedEffect(launchSymbol) {
        launchSymbol?.let { symbol ->
            navController.navigate(chartRoute(symbol, launchTimeframe)) { launchSingleTop = true }
            onSymbolLaunchConsumed()
        }
    }
    // The in-app delivery channel, which is this collector and nothing else.
    //
    // `AlertChannel.IN_APP` is a reader saying «tell me here, where I am already looking» — usually
    // at the chart of the very symbol that moved — and it is a separate choice from a push, not a
    // fallback for one. `InAppAlertBus` refuses to record a firing as delivered when nobody is
    // collecting, so without this the channel wrote «برنامه باز نبود» into the audit log every time,
    // including while the reader was watching the price.
    //
    // The toaster is the app's one message surface and this uses it rather than adding a second: a
    // banner that only alerts can produce is a banner nobody has learned to dismiss. The tap-through
    // is the point of the interruption — the alert says a level was reached, and the next thing
    // anybody wants is the bars around it.
    LaunchedEffect(inAppAlerts) {
        inAppAlerts.fired.collect { firing ->
            toaster.show(
                CoineProToast(
                    message = firing.body,
                    tone = ToastTone.NEUTRAL,
                    actionLabel = openChartLabel,
                    onAction = { navController.navigate(chartRoute(firing.symbol)) },
                ),
            )
        }
    }
    LaunchedEffect(launchActivity) {
        if (launchActivity) {
            navController.navigate(ACTIVITY_ROUTE) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onActivityLaunchConsumed()
        }
    }

    Scaffold(
        containerColor = CoineProColors.Stage,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    // A screen that draws its own heading gets a bar with nothing in it but the way
                    // back. Otherwise the reader is told what they are looking at twice — once by
                    // the bar and once by the page — and the duplicate costs the fifty-six points
                    // that the page's own heading needs.
                    title = {
                        if (currentRoute !in SELF_TITLED) Text(stringResource(subTitleRes))
                    },
                    navigationIcon = {
                        // Anything that is not one of the five bottom-bar roots gets the way back.
                        //
                        // It used to be `isSubScreen`, which is a different question: that set says
                        // *whether the bottom bar goes away*, and Tools, the screener, the heat map,
                        // news, the calendar and activity are deliberately not in it because they
                        // keep the bar. The two got conflated, so those six drew a top bar with a
                        // title and no arrow — «داخل بخش اسکرینر دکمه برگشت نذاشتی», and it was true
                        // of five more besides. Keeping the bottom bar is not a reason to take away
                        // the way back; a reader on a screen they navigated into needs both.
                        if (canGoBack) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    painter = painterResource(CoineProIcons.Back),
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = CoineProColors.TextPrimary,
                                )
                            }
                        }
                    },
                    actions = {
                        // The way into the depth ladder, and today the only one.
                        //
                        // It belongs on the chart's own surface, beside the studio and the panes
                        // entries — but those live inside `feature:chart`, which this file does
                        // not own, and a route nothing opens is a screen nobody reaches. So it
                        // sits in the corner of the chart's bar, which is otherwise empty: the
                        // chart is self-titled, so there is no heading here to crowd, and the
                        // control appears on that one route and nowhere else.
                        //
                        // Worded rather than drawn, because the icon set has no ladder in it and
                        // the nearest shape — the table glyph — is already the chart's own «جدول»
                        // drawing tool two taps away on the same screen.
                        //
                        // The route argument is the fallback rather than the answer: it is right
                        // until the watchlist strip swaps the symbol in place, and it is what the
                        // bar has on the first frame of a chart, before that destination has
                        // reported its live symbol up.
                        val depthSymbol = chartSymbolOnScreen.ifBlank {
                            backStackEntry?.arguments?.getString("symbol").orEmpty()
                        }
                        // Crypto only, and that is settled rather than pending.
                        //
                        // CoinePro-FX's MetaTrader 5 broker does not publish Level II, so
                        // `NoDepthGateway` answers `FEED_PUBLISHES_NO_DEPTH` for every forex symbol
                        // and always will — it is the broker's decision, not the backend's. This
                        // entry used to be on every chart, so a reader on gold pressed «عمق بازار»
                        // and got one sentence saying there is none. A button whose only
                        // destination is a refusal is worse than an absent one; see
                        // `docs/SERVER_ASKS_DOM.md`, section two.
                        //
                        // Read from the platform rather than by probing the gateway, because the
                        // answer is a property of the venue and is known before any request. The
                        // route itself stays reachable — a saved back stack or an old link still
                        // lands on the screen, which still says the true thing.
                        val depthAvailable = activePlatform.marketType == MarketType.CRYPTO
                        if (currentRoute == CHART_PATTERN && depthSymbol.isNotBlank() && depthAvailable) {
                            TextButton(onClick = { navController.navigate(domRoute(depthSymbol)) }) {
                                Text(
                                    text = stringResource(DomR.string.dom_title),
                                    color = CoineProColors.TextPrimary,
                                )
                            }
                        }
                        // One control where there were two text buttons.
                        //
                        // «ایمنی» and «خروج» were a pair of words in the corner of every screen,
                        // and neither is something a reader reaches for often. What they do want in
                        // that corner is themselves — so the corner is now the avatar, and safety,
                        // sign-out, verification and deletion are rows on the page it opens. A
                        // guest gets the same control with the same avatar; what differs is that
                        // their page offers an account instead of listing one.
                        if (!isSubScreen) {
                            IconButton(onClick = { navController.navigate(PROFILE_ROUTE) }) {
                                CoineProAvatar(
                                    spec = profile.avatar,
                                    initial = (profile.displayName ?: accountName)?.take(1) ?: "",
                                    size = 30.dp,
                                    contentDescription = stringResource(R.string.screen_profile),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CoineProColors.Stage,
                        titleContentColor = CoineProColors.TextPrimary,
                        actionIconContentColor = CoineProColors.TextSecondary,
                    ),
                )
            }
        },
        bottomBar = {
            if (!isSubScreen && !window.showsNavigationRail) {
                CoineProBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            tabSwitch(navController, destination.route)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        // A box, so the toast host at the bottom of it can overlay whatever screen is up without
        // that screen having to know it exists. See `CoineProToastHost`.
        Box(modifier = Modifier.fillMaxSize()) {
        // The rail first, so it takes the *start* edge — the right in Persian. Nothing here names
        // a side and nothing must: a rail pinned to a physical edge is the first thing a reader of
        // a right-to-left app sees, and it is wrong in a way that looks deliberate.
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (!isSubScreen && window.showsNavigationRail) {
            CoineProNavigationRail(
                items = coineProRailItems(),
                selectedKey = currentRoute,
                onSelect = { item ->
                    navController.navigate(item.key) { tabSwitch(navController, item.key) }
                },
                labelled = window.prefersLabelledRail,
                header = {
                    CoineProRailHeader {
                        // Only on the labelled rail. The wordmark's smallest legible width is
                        // 160dp and the glyph rail is 80dp wide; scaling it to fit would put an
                        // illegible logo on screen, which is worse than no logo.
                        if (window.prefersLabelledRail) {
                            ProChartWordmark(modifier = Modifier.width(160.dp))
                        }
                    }
                },
            )
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
        // Above every screen and below the bar, because being offline changes what every one of
        // them is showing. It takes its own row rather than floating, so it never covers a line.
        CoineProOfflineBar(online = online)
        // The venue's relay, which is a different fact from the phone's network and reads as one.
        // Only where the server actually reports it: a deployment without `price_feed` draws
        // nothing rather than a reassuring line. See `CoineProPriceFeedBar`.
        //
        // **Mapped to the reading before it is collected, and that is not tidiness.** The ticker
        // table is replaced on every five-second poll — eight hundred rows, and a tick age that
        // moves every time — so collecting it whole here would recompose the whole shell around
        // the NavHost twelve times a minute for a bar that changes about once a week. The reading
        // is equal across those polls, so the collected state never publishes and nothing
        // recomposes. Nothing else in this scope may read the status itself for the same reason.
        val feedReading by remember(marketTickerStore) {
            marketTickerStore.state.map { it.table.priceFeed?.reading() }
        }.collectAsStateWithLifecycle(null)
        CoineProPriceFeedBar(status = feedReading)
        // And into the exportable log, because the bar is only the half a reader sees. The outage
        // this field exists for lasted forty-five hours precisely because nothing wrote it down;
        // an operator handed the log now finds the transition in it with the counts attached.
        //
        // Keyed on the reading, so one line is written when the state changes rather than one
        // every five seconds — and the numbers are read from the store *inside* the effect, where
        // reading them is not a composition read and cannot drag the recomposition back in.
        LaunchedEffect(feedReading, activePlatform) {
            val feed = marketTickerStore.state.value.table.priceFeed ?: return@LaunchedEffect
            val fields = mapOf(
                "platform" to activePlatform.name,
                "tier" to feed.tier.name,
                "sockets" to (feed.socketsUp?.toString() ?: "—") + "/" + (feed.socketsTotal?.toString() ?: "—"),
                "tick_age_ms" to (feed.tickAgeMillis?.toString() ?: "—"),
            )
            if (feedReading == null) {
                appLog.info(LogTag.SOCKET, "venue price feed healthy", fields)
            } else {
                appLog.warn(LogTag.SOCKET, "venue price feed degraded", fields)
            }
        }
        // The accent for whatever is on screen, set once here rather than by every screen.
        //
        // Wrapping the NavHost rather than each destination means a screen cannot forget: the
        // accent is a property of the route, and the route is what changed. Anything not named
        // below is brand gold, which is the right default for the parts of the app that act on an
        // account rather than analyse a market.
        ProvidePageAccent(accentFor(currentRoute)) {
        /**
         * The chart, as a value, so the tablet's detail pane draws the same one the route does.
         *
         * Hoisted out of `composable(CHART_PATTERN)` and called from it, rather than copied: a
         * second chart written beside the first is a second set of callbacks to keep in step, and
         * the one that fell behind would be the one nobody was testing on a phone. Everything it
         * reads — the controller holder, the stores, the alert composer — already belongs to the
         * shell, so this is a move and not a lift.
         *
         * `symbol` is the instrument to open; `timeframe` is the bar length a fired alert was
         * decided on, or null for the reader's own last one.
         */
        val chartPane: @Composable (symbol: String, timeframe: String?) -> Unit =
            @Composable { routeSymbol, routeTimeframe ->
            /**
             * The instrument actually in front of the reader.
             *
             * Not the route argument, and this is the whole reason it exists: the watchlist
             * strip under the chart swaps the symbol *in place* without navigating, so the
             * path still names the market they started from. Every entry that leaves this
             * screen *on a symbol* reads this instead — the studio above all, and through it
             * the two-pane screen — or it would open on the chart the reader stopped looking
             * at several taps ago. The terminal is not one of them: its address carries no
             * instrument. `onSelectSymbol` below is the deliberate exception, because that is
             * the fallback path that really does navigate.
             *
             * Saved rather than remembered, so a rotation does not undo the switch.
             */
            var activeChartSymbol by rememberSaveable { mutableStateOf(routeSymbol) }
            // What the bar's depth entry opens on. Keyed on the symbol rather than set inside
            // `onSymbolChanged`, because that callback only fires on a *change* — a reader who
            // opens a chart and presses depth straight away has never changed anything.
            LaunchedEffect(activeChartSymbol) { chartSymbolOnScreen = activeChartSymbol }
            val chartController = chartControllers.controllerFor(routeSymbol)
            // The bar a fired alert was decided on. Recorded rather than applied, because the
            // controller's own restore reads a stored interval for this symbol and whichever
            // ran last would win — see `ChartController.openAt`.
            LaunchedEffect(chartController, routeTimeframe) { chartController.openAt(routeTimeframe) }
            ChartScreen(
                layouts = chartLayouts,
                onSaveLayout = onSaveLayoutAnnounced,
                onDeleteLayout = onDeleteLayoutAnnounced,
                watchlist = watchlist,
                onPaperTrade = { symbol, buy, entry, size, stopLoss, takeProfit ->
                    paperTradeController.place(
                        PaperOrderRequest(
                            symbol = symbol,
                            side = if (buy) PaperSide.BUY else PaperSide.SELL,
                            // A limit at the entry the reader drew, not a market order.
                            //
                            // The two are the same thing whenever the price is already there:
                            // `PaperFills.marketable` fills a limit that the book has reached
                            // at the book's own price, capped at the limit. They differ where
                            // the setup is a level the market has not come to yet, and there a
                            // market order would take a trade at a price the reader did not
                            // choose — which is the opposite of what drawing an entry means.
                            type = PaperOrderType.LIMIT,
                            size = size,
                            limitPrice = entry,
                            // Both lines they drew. A setup that arrives without its stop is
                            // one they have to protect a second time, and the second time is
                            // the one that gets skipped.
                            stopLoss = stopLoss,
                            takeProfit = takeProfit,
                        ),
                    )
                },
                // The chart says which price; the composer asks the rest. Opened here rather
                // than inside `feature:chart` so the sheet keeps one owner.
                onCreateAlert = { symbol, price -> alertFromChart = symbol to price },
                onSelectSymbol = { symbol ->
                    // Replaces the chart rather than stacking one on top of another: flipping
                    // through six symbols must not build a six-deep back stack that takes six
                    // presses to leave.
                    navController.navigate(chartRoute(symbol)) {
                        popUpTo(CHART_PATTERN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                controller = chartController,
                // What turns a tap in the strip into a switch rather than a navigation: the
                // holder already keeps a controller per symbol, with that symbol's own
                // drawings and its own restored timeframe.
                controllerFor = chartControllers::controllerFor,
                onSymbolChanged = { activeChartSymbol = it },
                watchlistQuotes = watchlistQuotes,
                workspace = chartWorkspaceStore,
                drawingTemplates = drawingTemplateStore,
                symbolChartStates = symbolChartStateStore,
                chartLayoutStore = chartLayoutStore,
                intervalFavourites = intervalFavouritesStore,
                drawingSync = drawingSyncStore,
                events = chartEventController,
                timeZones = timeZonePrefStore,
                onOpenStudio = { navController.navigate(studioRoute(activeChartSymbol)) },
                onOpenTerminal = if (terminalController.isConfigured) {
                    { navController.navigate(TERMINAL_ROUTE) }
                } else {
                    null
                },
            )
        }

        /**
         * One signal's page, as a value, so the tablet draws it beside the list it came from.
         *
         * The same move as [chartPane] and for the same reason: a reader working through six
         * open signals on a tablet should not push and pop six times to compare them.
         */
        val signalPane: @Composable (signalId: Long) -> Unit =
            @Composable { signalId ->
            val chartScope = rememberCoroutineScope()
            val signalChartController = remember(candleGateway) {
                SignalChartController(gateway = candleGateway, scope = chartScope)
            }
            SignalDetailScreen(
                controller = signalController,
                marketIntelController = marketIntelController,
                signalId = signalId,
                chartController = signalChartController,
                // Null where the platform places no orders. CoinePro-FX is the case: its
                // signals reach a reader's account through copy trading, so the button that
                // used to sit here led to a screen that could only say the feature was absent.
                onExecute = if (activePlatform == MarketPlatform.TRADEYAR) {
                    { id -> navController.navigate(executionRoute(id)) }
                } else {
                    null
                },
                onOpenCopyTrading = if (activePlatform == MarketPlatform.COINEPRO_FX) {
                    { navController.navigate(CONNECTIONS_ROUTE) }
                } else {
                    null
                },
            )
        }

        NavHost(
            navController = navController,
            startDestination = AppDestination.HOME.route,
            modifier = Modifier.weight(1f),
        ) {
            composable(AppDestination.HOME.route) {
                // The same tab, two homes. A guest's is the public feed's — real prices, the real
                // track record, the real headlines — with their own avatar at the top of it, and
                // the market rows open the same chart a member's do.
                if (guest) {
                    GuestScreen(
                        controller = guestController,
                        onSignIn = onSignIn ?: {},
                        avatar = profile.avatar,
                        onOpenProfile = { navController.navigate(PROFILE_ROUTE) },
                        onOpenSymbol = { navController.navigate(chartRoute(it)) },
                        onOpenMarket = { navController.navigate(AppDestination.MARKETS.route) },
                        onOpenTools = { navController.navigate(TOOLS_ROUTE) },
                    )
                    return@composable
                }
                // The account's own equity, from the same closed trades the portfolio screen
                // charts. Started here because Home is where it is drawn and a controller nobody
                // started has no history to hand over; `start()` is a no-op after the first call.
                LaunchedEffect(portfolioController) { portfolioController.start() }
                val equityState by portfolioController.state.collectAsStateWithLifecycle()

                HomeScreen(
                    state = marketState,
                    // The reader's own name if they chose one, the server's otherwise. Without this
                    // the greeting row does not render at all — and with it goes the avatar, which
                    // is Home's only way into the account.
                    displayName = profile.displayName ?: accountName,
                    watchlist = watchlist,
                    onToggleWatch = onToggleWatch,
                    onVisibleSymbols = onSubscribeSymbols,
                    onOpenSymbol = { navController.navigate(chartRoute(it)) },
                    briefing = briefing,
                    portfolio = portfolio?.copy(
                        equity = equityState.stats.equity.map { it.equity },
                    ),
                    subscription = subscription,
                    onRetry = {
                        onMarketRetry()
                        onRefreshAccount()
                    },
                    // Both pills lead to the AI section, which is where the work actually happens.
                    onOpenTools = { navController.navigate(TOOLS_ROUTE) },
                    onOpenActivity = { navController.navigate(ACTIVITY_ROUTE) },
                    onOpenNews = { navController.navigate(NEWS_ROUTE) },
                    onGenerateSignal = { navController.navigate(AppDestination.AI.route) },
                    // Chart analysis is optional per deployment. Sending the reader to a screen the
                    // server has switched off is a wait that ends in an error every time, so the
                    // action falls back to the AI studio the server does serve.
                    onSendChart = {
                        navController.navigate(
                            if (chartVisionAvailable) AI_VISION_ROUTE else AppDestination.AI.route,
                        )
                    },
                    // The market card's own destination is the market list, not the signals
                    // feed. They were the same route while the app knew eight markets and there
                    // was no list worth opening.
                    onOpenMarket = { navController.navigate(MARKET_SEARCH_ROUTE) },
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    // Home carries no top bar, so the avatar is the way into the account — and it
                    // now opens the profile page rather than a four-item dropdown.
                    avatar = profile.avatar,
                    onOpenProfile = { navController.navigate(PROFILE_ROUTE) },
                    platforms = platforms,
                    activePlatform = activePlatform,
                    onSelectPlatform = onSelectPlatform,
                    balanceHidden = profile.balanceHidden,
                    onToggleBalanceHidden = onToggleBalanceHidden,
                    onOpenPortfolio = { navController.navigate(PORTFOLIO_ROUTE) },
                )
            }
            composable(ADMIN_ROUTE) {
                val adminState by adminController.state.collectAsStateWithLifecycle()
                AdminScreen(
                    state = adminState,
                    hub = hub,
                    actions = hubActions,
                    // Lambdas rather than values: building the report walks the whole ring and
                    // every recorded request, and doing that on each recomposition of a screen
                    // that has a search field on it would cost the panel its own responsiveness.
                    report = {
                        DiagnosticExport.render(
                            adminController.exportContext(hub, marketState.connection.name),
                            System.currentTimeMillis(),
                        )
                    },
                    logText = adminController::logText,
                )
            }
            composable(AppDestination.SIGNALS.route) {
                if (guest) {
                    GuestGateScreen(
                        gate = GuestGate.SIGNALS,
                        controller = guestController,
                        onSignIn = onSignIn ?: {},
                    )
                    return@composable
                }
                var pairedSignal by rememberSaveable { mutableStateOf<Long?>(null) }
                CoineProListDetail(
                    detail = pairedSignal?.let { id -> { signalPane(id) } },
                ) { twoPane ->
                SignalsScreen(
                    controller = signalController,
                    onOpenSignal = {
                        if (twoPane) pairedSignal = it else navController.navigate(signalDetailRoute(it))
                    },
                    platform = activePlatform,
                    // The locked state is the membership journey now, not a card naming a Telegram
                    // channel. Somebody who installed this from Google Play has never heard of that
                    // channel; being sent to it is where they leave. Both controllers are read only
                    // when the server refuses the list.
                    membershipController = membershipController,
                    guestController = guestController,
                )
                }
            }
            composable(
                route = SIGNAL_DETAIL_PATTERN,
                arguments = listOf(navArgument("signalId") { type = NavType.LongType }),
            ) { entry ->
                signalPane(entry.arguments?.getLong("signalId") ?: return@composable)
            }
            composable(
                route = EXECUTION_PATTERN,
                arguments = listOf(navArgument("signalId") { type = NavType.LongType }),
            ) { entry ->
                val signalId = entry.arguments?.getLong("signalId") ?: return@composable
                ExecutionScreen(
                    signalId = signalId,
                    signalController = signalController,
                    executionController = executionController,
                    onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                )
            }
            composable(PROFILE_ROUTE) {
                // The one page in the app that is about the reader rather than about a market.
                //
                // A guest reaches it from the same corner, sees the same hero, edits the same
                // avatar and keeps the same name — what they do not get is the account rows,
                // because there is no account to act on. They get the offer instead, once.
                var composing by rememberSaveable { mutableStateOf(false) }
                val initial = (profile.displayName ?: accountName)?.trim()?.take(1)
                    ?: stringResource(R.string.profile_initial_fallback)

                ProfileScreen(
                    profile = profile,
                    accountName = accountName,
                    email = accountEmail,
                    guest = guest,
                    planLabel = subscription?.planLabel,
                    platformLabel = stringResource(activePlatform.labelRes()),
                    // Three figures, and every one of them is about this reader: what they chose
                    // to watch, what they wrote down, what they practised. No market number
                    // belongs on this page.
                    readings = listOf(
                        CoineProReading(
                            label = stringResource(R.string.profile_reading_watchlist),
                            value = watchlist.size.toPersianDigits(),
                        ),
                    ),
                    onEditAvatar = { composing = true },
                    onSetDisplayName = onSetDisplayName,
                    onSetTagline = onSetTagline,
                    onSignIn = onSignIn.takeIf { guest },
                    actions = if (guest) {
                        // Safety is offered to a guest too. It is where the crash report and the
                        // version live, and a reader who has hit a bug should not have to make an
                        // account to tell us about it.
                        listOf(
                            // Offered to a guest too, and not as a courtesy: their price alerts
                            // run on this device and need no account, so the screen is as real
                            // for them as it is for a member.
                            ProfileAction(
                                label = stringResource(R.string.screen_notifications),
                                note = stringResource(R.string.profile_action_notifications_note),
                                icon = CoineProIcons.Bell,
                                onClick = { navController.navigate(NOTIFICATIONS_ROUTE) },
                            ),
                            ProfileAction(
                                label = stringResource(R.string.profile_action_safety),
                                note = stringResource(R.string.profile_action_safety_note),
                                icon = CoineProIcons.Secure,
                                onClick = { navController.navigate(LAUNCH_READINESS_ROUTE) },
                            ),
                        )
                    } else {
                        buildList {
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.screen_membership),
                                    note = stringResource(R.string.profile_action_membership_note),
                                    icon = CoineProIcons.Wallet,
                                    onClick = { navController.navigate(MEMBERSHIP_ROUTE) },
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_verification),
                                    icon = CoineProIcons.IdentityCard,
                                    onClick = { navController.navigate(KYC_ROUTE) },
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.screen_notifications),
                                    note = stringResource(R.string.profile_action_notifications_note),
                                    icon = CoineProIcons.Bell,
                                    onClick = { navController.navigate(NOTIFICATIONS_ROUTE) },
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_alerts),
                                    icon = CoineProIcons.Alarm,
                                    onClick = { navController.navigate(ALERTS_ROUTE) },
                                ),
                            )
                            // Only where the phone can carry a lock at all. On a device with no
                            // screen lock the switch would be a question the reader cannot
                            // answer — this app cannot add a lock the device does not have.
                            if (lockCapability.offerable) {
                                add(
                                    ProfileAction(
                                        label = stringResource(R.string.profile_action_lock),
                                        icon = CoineProIcons.Locked,
                                        value = stringResource(
                                            if (appLockEnabled) {
                                                R.string.profile_action_lock_on
                                            } else {
                                                R.string.profile_action_lock_off
                                            },
                                        ),
                                        onClick = { appLockOpen = true },
                                    ),
                                )
                            }
                            // Above the safety row rather than buried under it: in a corpus of
                            // Persian-language reviews of this category of app, an explicit theme
                            // control is the single most asked-for thing — ahead of chart
                            // features, ahead of speed. A setting people go looking for belongs
                            // where they will look.
                            add(
                                ProfileAction(
                                    label = stringResource(AppearanceTitle),
                                    icon = CoineProIcons.Visible,
                                    value = stringResource(themeMode.labelRes()),
                                    onClick = { appearanceOpen = true },
                                ),
                            )
                            // A row of its own, reaching the share sheet in one tap.
                            //
                            // Support was already correct in the one way that matters most —
                            // nothing in this app puts an AI between a reader and a person, which
                            // in a corpus of reviews of this category is the worst-rated thing any
                            // of them does — but it was *inside* «ایمنی و نسخه», two taps down a
                            // row named after something else. Reachable and honest are different
                            // properties and this product only had the second.
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_support),
                                    note = stringResource(R.string.profile_action_support_note),
                                    icon = CoineProIcons.Help,
                                    onClick = onSendFeedback,
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_safety),
                                    note = stringResource(R.string.profile_action_safety_note),
                                    icon = CoineProIcons.Secure,
                                    onClick = { navController.navigate(LAUNCH_READINESS_ROUTE) },
                                ),
                            )
                            // Asks first. Signing out throws away every token on both backends
                            // and clears the stored name and face; there is no undo, and this row
                            // sits one line above «حذف حساب» where a thumb that lands low on a
                            // moving bus used to lose a session without being asked anything.
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.action_logout),
                                    icon = CoineProIcons.SignOut,
                                    onClick = { signOutAsked = true },
                                ),
                            )
                            // Last, and drawn in the refusal colour. It is the one row here that
                            // cannot be undone.
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_delete),
                                    destructive = true,
                                    icon = CoineProIcons.Delete,
                                    onClick = { navController.navigate(DELETE_ACCOUNT_ROUTE) },
                                ),
                            )
                        }
                    },
                )

                if (composing) {
                    AvatarComposerSheet(
                        current = profile.avatar,
                        initial = initial,
                        onSave = { spec ->
                            onSetAvatar(spec)
                            composing = false
                        },
                        onDismiss = { composing = false },
                    )
                }
            }
            composable(MEMBERSHIP_ROUTE) {
                // The screen that step three of the membership card has always pointed at and
                // that nothing in the app reached. A reader could register on an exchange, fund
                // it, and then find no way to tell CoinePro their UID — which is the one step the
                // whole arrangement turns on.
                //
                // Which exchanges accept a UID is the server's answer, not a constant here: it is
                // deliberately a superset of the copy-trading list, because Ourbit earns membership
                // and is never traded on. Compiling either list in would eventually offer somebody
                // an exchange the platform had stopped taking.
                val terms by guestController.membership.collectAsStateWithLifecycle()
                // Asked for here, not assumed. The terms come from a public route that only the
                // guest home polls, and a signed-in reader never renders that screen — so without
                // this the exchange picker would be empty for exactly the people who need it.
                LaunchedEffect(guestController) { guestController.refreshMembership() }
                MembershipScreen(
                    controller = membershipController,
                    uidExchanges = (terms as? GuestMembershipState.Ready)
                        ?.terms
                        ?.uidExchanges
                        .orEmpty(),
                )
            }
            composable(NOTIFICATIONS_ROUTE) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val current by notificationSettingsStore.settings
                    .collectAsStateWithLifecycle(initialValue = NotificationSettings())
                val localAlerts by localAlertStore.alerts
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                var composing by rememberSaveable { mutableStateOf(false) }

                // Only the categories that can ever fire for this reader. A guest shown a switch
                // for "a copy trade opened" is being offered control over something that cannot
                // happen to them, which makes the whole screen read as decoration.
                val sections = notificationSections(guest = guest)

                NotificationSettingsScreen(
                    settings = current,
                    sections = sections,
                    alerts = localAlerts,
                    systemPermissionGranted =
                        notificationPermissionState != NotificationPermissionUiState.DENIED,
                    onOpenSystemSettings = onOpenNotificationSettings,
                    onSetEnabled = { on -> scope.launch { notificationSettingsStore.setEnabled(on) } },
                    onSetCategory = { category, on ->
                        scope.launch { notificationSettingsStore.setCategory(category, on) }
                    },
                    onSetQuietHours = { on, from, to ->
                        scope.launch { notificationSettingsStore.setQuietHours(on, from, to) }
                    },
                    onAddAlert = { composing = true },
                    onToggleAlert = { alert, active ->
                        scope.launch {
                            localAlertStore.setActive(alert.id, active)
                            localAlertScheduler.sync(hasActiveAlerts = active || localAlerts.any { it.active && it.id != alert.id })
                        }
                    },
                    onDeleteAlert = { alert ->
                        scope.launch {
                            localAlertStore.remove(alert.id)
                            localAlertScheduler.sync(hasActiveAlerts = localAlerts.any { it.active && it.id != alert.id })
                        }
                        // The whole alert is in hand here, so the undo restores it exactly — the
                        // symbol, the condition, the price the reader typed and whether it
                        // repeats. Nothing to ask about.
                        toaster.show(
                            CoineProToast(
                                message = deletedMessage,
                                tone = ToastTone.NEUTRAL,
                                actionLabel = undoLabel,
                                onAction = {
                                    scope.launch {
                                        localAlertStore.add(alert)
                                        localAlertScheduler.sync(hasActiveAlerts = true)
                                    }
                                },
                            ),
                        )
                    },
                    labelFor = { category -> context.getString(category.channelNameRes()) },
                    noteFor = { category -> context.getString(category.channelDescriptionRes()) },
                )

                if (composing) {
                    // The reader's own first market, or the platform's — the same rule the chart
                    // tab and the script studio already follow, so "new alert" never opens on a
                    // ticker this backend does not carry.
                    val symbol = defaultScriptSymbol(activePlatform, watchlist)
                    AlertComposerSheet(
                        symbol = symbol,
                        currentPrice = marketState.quotes[symbol]?.price,
                        full = localAlerts.size >= LocalPriceAlert.MAX_ALERTS,
                        onCreate = { alert ->
                            scope.launch {
                                localAlertStore.add(alert)
                                localAlertScheduler.sync(hasActiveAlerts = true)
                            }
                            composing = false
                            // The sheet closes on create, so without this the reader watches the
                            // screen they were on come back and has no way to tell whether the
                            // alert was made. The list behind it is the proof, but it is below the
                            // fold on a full list.
                            toaster.show(alertSavedMessage, ToastTone.SUCCESS)
                        },
                        onDismiss = { composing = false },
                    )
                }
            }
            composable(KYC_ROUTE) {
                KycScreen(controller = accountController)
            }
            composable(PAPER_TRADE_ROUTE) {
                PaperTradeScreen(
                    controller = paperTradeController,
                    // The same feed the market list is showing. A second source would let this
                    // screen and the row above it disagree about one instrument's price.
                    priceFor = { symbol -> marketState.quotes[symbol]?.price },
                    // The whole observation where the feed sent one: both sides of the book and
                    // the feed's own judgement of freshness. Without it the fill rules widen an
                    // assumed spread around last and say on screen that they assumed it; with it
                    // they cross the spread the venue actually quoted.
                    quoteFor = { symbol -> marketState.quotes[symbol]?.asPaperQuote() },
                    onOpenSymbol = { navController.navigate(chartRoute(it)) },
                )
            }
            composable(JOURNAL_ROUTE) {
                JournalScreen(controller = journalController)
            }
            composable(ALERTS_ROUTE) {
                AlertCenterScreen(controller = alertsController)
            }
            composable(DELETE_ACCOUNT_ROUTE) {
                DeleteAccountScreen(
                    controller = accountController,
                    supported = accountDeletionAvailable,
                    // Signing out on the way rather than after: the token the app is holding names
                    // an account that no longer exists, and the next request with it would be
                    // answered 401 and reported to the reader as an expired session.
                    onDeleted = onLogout,
                )
            }
            composable(CONNECTIONS_ROUTE) {
                // One route, two entirely different surfaces, because the two products are
                // different: TradeYar takes an exchange key and places orders per signal, while
                // CoinePro-FX links a broker account once and a service trades it. Sending an FX
                // reader to the venue form gave them a screen whose every field was answered by a
                // 404, worded as though the connection had failed.
                when (activePlatform) {
                    MarketPlatform.COINEPRO_FX -> CopyTradeScreen(controller = copyTradeController)
                    MarketPlatform.TRADEYAR ->
                        ConnectionsScreen(controller = executionController, platform = activePlatform)
                }
            }
            composable(AppDestination.AI.route) {
                if (guest) {
                    GuestGateScreen(
                        gate = GuestGate.AI,
                        controller = guestController,
                        onSignIn = onSignIn ?: {},
                    )
                    return@composable
                }
                // AiStudioScreen, not the older AiScreen: the two carried the same generator, and
                // only this one shows the evidence the server returns alongside the verdict.
                AiStudioScreen(
                    controller = aiSignalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    chartVisionAvailable = chartVisionAvailable,
                    assistantAvailable = assistantAvailable,
                    aiSignalsAvailable = aiSignalsAvailable,
                    onOpenChartAnalysis = { navController.navigate(AI_VISION_ROUTE) },
                    onOpenAssistant = { navController.navigate(AI_ASSISTANT_ROUTE) },
                    platform = activePlatform,
                )
            }
            composable(AI_VISION_ROUTE) {
                AiVisionScreen(
                    controller = aiVisionController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                )
            }
            composable(AI_ASSISTANT_ROUTE) {
                AiAssistantScreen(
                    controller = aiAssistantController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    available = assistantAvailable,
                )
            }
            composable(AppDestination.MARKETS.route) {
                val signals by signalController.state.collectAsStateWithLifecycle()
                // The instrument in the detail pane, or null where there is only one pane and a
                // row tap is still a navigation. Saveable, so a rotation on a tablet does not
                // close the chart the reader is looking at.
                var pairedSymbol by rememberSaveable { mutableStateOf<String?>(null) }
                CoineProListDetail(
                    detail = pairedSymbol?.let { symbol -> { chartPane(symbol, null) } },
                ) { twoPane ->
                MarketsScreen(
                    controller = marketSearchController,
                    sparklines = sparklineStore,
                    // The day's figures, which is what the gainers, losers and «داغ» tabs are made
                    // of. Passed as the store rather than a table so the screen starts and stops
                    // the poll with its own lifetime — it is reference counted, so the heat map
                    // reading the same table keeps it running when this screen leaves.
                    tickers = marketTickerStore,
                    watchlist = watchlist,
                    watchlistStore = watchlistStore,
                    watchlistSync = watchlistSyncController,
                    // Two panes: the chart appears beside the list and the list stays where it
                    // is. One pane: exactly what it always did. See `CoineProListDetail` — the
                    // decision is made on the width this layout was given, not on the window.
                    onOpenSymbol = { symbol ->
                        if (twoPane) pairedSymbol = symbol else navController.navigate(chartRoute(symbol))
                    },
                    onOpenSearch = { navController.navigate(MARKET_SEARCH_ROUTE) },
                    // The same hoisted composer the chart uses, at the price the preview showed —
                    // the shell's live map carries only the subscribed handful, and looking the
                    // price up again here would find nothing for most of the list.
                    onCreateAlert = { symbol, price -> alertFromChart = symbol to price },
                    // Only when there is something to say. A strip reading «۰ سیگنال باز» is a row
                    // of chrome reporting the absence of news.
                    openSignals = signals.items.takeIf { it.isNotEmpty() }?.let { open ->
                        MarketsSignalStrip(
                            count = open.size,
                            summary = open.take(2).joinToString(" · ") { signal ->
                                signal.symbol + " " + if (signal.direction == SignalDirection.BUY) "خرید" else "فروش"
                            },
                            onClick = { navController.navigate(AppDestination.SIGNALS.route) },
                        )
                    },
                )
                }
            }
            composable(AppDestination.CHART.route) {
                // The tab opens the reader's own first market, or the platform's first quoted one.
                // A tab that asked which symbol before showing anything would be a picker with a
                // chart behind it, which is not what a chart tab is for.
                val symbol = defaultScriptSymbol(activePlatform, watchlist)
                LaunchedEffect(symbol) {
                    navController.navigate(chartRoute(symbol)) {
                        popUpTo(AppDestination.CHART.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable(MARKET_SEARCH_ROUTE) {
                SearchScreen(
                    watchlist = watchlist,
                    onToggleWatch = onToggleWatch,
                    controller = marketSearchController,
                    onOpenSymbol = { navController.navigate(chartRoute(it)) },
                    // What this reader can actually reach, so a section is never an invitation to
                    // a wall. `absent` is the deployment's own answer rather than a guess: a
                    // server that reports no chart vision has no vision screen to name, and a
                    // terminal with no configured URL is a WebView pointed at nothing.
                    access = SurfaceAccess(
                        platform = activePlatform,
                        signedIn = !guest,
                        absent = buildSet {
                            if (!chartVisionAvailable) add("ai-vision")
                            if (!assistantAvailable) add("ai-assistant")
                            if (!aiSignalsAvailable) add("ai")
                            if (!terminalController.isConfigured) add("terminal")
                        },
                    ),
                    onOpenSurface = { id ->
                        navController.navigate(surfaceRoute(id, activePlatform, watchlist))
                    },
                    onSignIn = onSignIn,
                    // Read, never requested: the preview draws whatever line the markets tab has
                    // already fetched for a symbol and asks for nothing of its own.
                    sparklines = sparklineStore,
                    onCreateAlert = { symbol, price -> alertFromChart = symbol to price },
                )
            }
            composable(
                route = CHART_PATTERN,
                arguments = listOf(
                    navArgument("symbol") { type = NavType.StringType },
                    // Nullable *and* defaulted. Without the default, navigating to the bare
                    // `chart/{symbol}` — which every other entry point in the app does — fails to
                    // match this destination at all.
                    navArgument("timeframe") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                chartPane(
                    entry.arguments?.getString("symbol").orEmpty(),
                    entry.arguments?.getString("timeframe"),
                )
            }
            composable(
                route = SCRIPT_PATTERN,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { entry ->
                val symbol = entry.arguments?.getString("symbol").orEmpty()
                val scope = rememberCoroutineScope()
                // A chart controller purely to fetch bars: the studio draws its own preview, and
                // reusing the chart's loader means the studio's candles and the chart's candles
                // come from one place. A second fetcher here would be a second thing to keep in
                // step with paging, timeframes and the academy-token failure modes.
                val previewController = remember(symbol, candleGateway) {
                    ChartController(symbol = symbol, gateway = candleGateway, scope = scope)
                }
                val previewState by previewController.state.collectAsStateWithLifecycle()
                LaunchedEffect(previewController) { previewController.start() }
                ScriptScreen(
                    controller = scriptController,
                    symbol = symbol,
                    series = previewState.series,
                    loading = previewState.loading,
                )
            }
            composable(
                route = STUDIO_PATTERN,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { entry ->
                val symbol = entry.arguments?.getString("symbol").orEmpty()
                // The chart's own controller, not a second one. Two instances is what made the
                // studio useless: arming a tool, toggling an indicator or choosing a chart type
                // wrote to an object the chart could not see, and the studio's copy was thrown
                // away on the way back. See `ChartControllers`.
                val studioController = chartControllers.controllerFor(symbol)
                ChartStudioScreen(
                    controller = studioController,
                    // The studio starts the controller itself, after binding the stores — so a
                    // deep link that opens the studio first still restores this symbol's own
                    // settings before the first fetch rather than after it.
                    symbolChartStates = symbolChartStateStore,
                    chartLayoutStore = chartLayoutStore,
                    indicatorTemplates = indicatorTemplateStore,
                    drawingSync = drawingSyncStore,
                    events = chartEventController,
                    layouts = chartLayouts,
                    onSaveLayout = onSaveLayoutAnnounced,
                    onDeleteLayout = onDeleteLayoutAnnounced,
                    drawingTemplates = drawingTemplateStore,
                    onOpenScript = { navController.navigate(scriptRoute(symbol)) },
                    onOpenPanes = { navController.navigate(panesRoute(symbol)) },
                    onOpenChartVision = if (chartVisionAvailable) {
                        { navController.navigate(AI_VISION_ROUTE) }
                    } else {
                        null
                    },
                    onBackToChart = { navController.popBackStack() },
                )
            }
            composable(
                route = PANES_PATTERN,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { entry ->
                val symbol = entry.arguments?.getString("symbol").orEmpty()
                ChartPanesScreen(
                    firstSymbol = symbol,
                    // The same holder both other chart routes use, so a pane opened on a symbol
                    // the reader has already charted arrives with that symbol's drawings and its
                    // own timeframe rather than on the defaults.
                    controllerFor = chartControllers::controllerFor,
                    watchlist = watchlist,
                    quotes = watchlistQuotes,
                    workspace = chartWorkspaceStore,
                    symbolChartStates = symbolChartStateStore,
                    chartLayoutStore = chartLayoutStore,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = DOM_PATTERN,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { entry ->
                // The market this ladder belongs to. The entry that reaches here is built from the
                // chart's *live* symbol rather than its route argument — see `chartSymbolOnScreen`
                // — so this is the instrument that was in front of the reader when they pressed it.
                val activeChartSymbol = entry.arguments?.getString("symbol").orEmpty()
                val depthScope = rememberCoroutineScope()
                // One controller per visit, on the gateway of the platform on screen. Not held for
                // the shell's life like the chart controllers are: those exist to keep a symbol's
                // drawings and timeframe across navigations, and a book has nothing worth keeping
                // — a snapshot from the last time this screen was open is a stale ladder, which is
                // the one thing a depth reader must never be shown.
                //
                // The screen starts it and stops it; the scope here is only what outlives the
                // in-flight request when the destination leaves.
                val depthController = remember(orderBookGateway, depthScope) {
                    OrderBookController(gateway = orderBookGateway, scope = depthScope)
                }
                val depthPreferences = remember(symbolChartStateStore) {
                    SymbolChartDepthPreferences(symbolChartStateStore)
                }
                DepthOfMarketScreen(
                    controller = depthController,
                    symbol = activeChartSymbol,
                    preferences = depthPreferences,
                    // A tapped level goes to the alert composer, never to an order. We do not place
                    // orders against a ladder this app cannot fill into — and a level a reader
                    // stopped on is exactly the price they want to be told about later, which is
                    // what the composer already does with a price picked off the chart.
                    onPickPrice = { price -> alertFromChart = activeChartSymbol to price },
                )
            }
            composable(NEWS_ROUTE) {
                // A guest reads the public headline route, which needs no account and which their
                // own home screen was already showing twelve of. Pointing them at the members'
                // screen would hand them a 401 worded as an outage, on content the server
                // publishes to anybody.
                if (guest) {
                    // The same card and the same reading page a member gets, over the public
                    // headline route. A guest is the one reader who has to find the product
                    // attractive, and until now they were the one reader who could not open a
                    // story at all.
                    PublicNewsScreen(controller = guestController)
                    return@composable
                }
                NewsScreen(
                    platform = activePlatform,
                    controller = marketIntelController,
                    onOpenCalendar = { navController.navigate(CALENDAR_ROUTE) },
                    // Null draws no entry at all, which is how «this platform has no announcements»
                    // is said: absent, rather than a button that opens a screen answering 404.
                    announcements = announcementsController,
                )
            }
            composable(CALENDAR_ROUTE) {
                EconomicCalendarScreen(
                    controller = marketIntelController,
                    onOpenNews = { navController.navigate(NEWS_ROUTE) },
                )
            }
            composable(HEATMAP_ROUTE) {
                // The catalogue controller the search screen already uses, rather than a second
                // one: two would fetch the same several thousand symbols twice and could disagree
                // about which of them the app has artwork for.
                // And the candles, because without a bar source the map has no second variable
                // and draws itself entirely hatched — honest, and useless. `candleGateway` is
                // whichever one this shell was built with, so the guest's map reads the guest's
                // candles without a second branch here.
                HeatmapScreen(
                    controller = marketSearchController,
                    onOpenSymbol = { navController.navigate(chartRoute(it)) },
                    bars = remember(candleGateway) { CandleHeatmapBarSource(candleGateway) },
                    // The whole catalogue's day in one request, from the store the market list is
                    // already reading. Reference counted, so the map holds it only while it is
                    // open — and the candles above stay, for the two figures a rolling
                    // twenty-four hours cannot carry: the period return and the median daily range.
                    tickers = remember(marketTickerStore) { MarketTickerHeatmapSource(marketTickerStore) },
                )
            }
            composable(SCREENER_ROUTE) {
                var pairedSymbol by rememberSaveable { mutableStateOf<String?>(null) }
                CoineProListDetail(
                    detail = pairedSymbol?.let { symbol -> { chartPane(symbol, null) } },
                ) { twoPane ->
                    ScreenerScreen(
                        controller = screenerController,
                        // A screener is a list of instruments to look at, so the reader who has
                        // just filtered forty of them down to three should be able to look at all
                        // three without losing the filter that found them.
                        onOpenSymbol = {
                            if (twoPane) pairedSymbol = it else navController.navigate(chartRoute(it))
                        },
                    )
                }
            }
            composable(TOOLS_ROUTE) {
                ToolsScreen(
                    onOpenHeatmap = { navController.navigate(HEATMAP_ROUTE) },
                    onOpenScreener = { navController.navigate(SCREENER_ROUTE) },
                    onOpenJournal = { navController.navigate(JOURNAL_ROUTE) },
                    onOpenPaperTrade = { navController.navigate(PAPER_TRADE_ROUTE) },
                    // The studio needs a symbol to run against, and from here there is no chart to
                    // take one from. The watchlist's first entry is the reader's own choice; the
                    // platform's default is the fallback for somebody who has not made one yet.
                    onOpenScript = {
                        navController.navigate(scriptRoute(defaultScriptSymbol(activePlatform, watchlist)))
                    },
                    platform = activePlatform,
                    // Null for a guest, on the three that read a signed-in route. Everything else
                    // on this screen is local to the phone and opens for anybody.
                    onOpenConnections = if (guest) null else ({ navController.navigate(CONNECTIONS_ROUTE) }),
                    onOpenNews = { navController.navigate(NEWS_ROUTE) },
                    onOpenCalendar = if (guest) null else ({ navController.navigate(CALENDAR_ROUTE) }),
                    onOpenPortfolio = if (guest) null else ({ navController.navigate(PORTFOLIO_ROUTE) }),
                    onOpenAcademy = if (hasAcademy && !guest) {
                        { navController.navigate(ACADEMY_ROUTE) }
                    } else {
                        null
                    },
                )
            }
            composable(TERMINAL_ROUTE) {
                TerminalScreen(
                    controller = terminalController,
                    onClose = { navController.popBackStack() },
                )
            }
            composable(ACADEMY_ROUTE) {
                AcademyScreen(
                    controller = academyController,
                    onOpenLesson = { navController.navigate(lessonRoute(it)) },
                    onOpenProfile = { navController.navigate(KYC_ROUTE) },
                )
            }
            composable(
                route = LESSON_PATTERN,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                LessonScreen(
                    controller = academyController,
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    onClose = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(KYC_ROUTE) },
                )
            }
            composable(PORTFOLIO_ROUTE) {
                PortfolioScreen(
                    controller = portfolioController,
                    onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                    onOpenReport = { navController.navigate(PORTFOLIO_REPORT_ROUTE) },
                )
            }
            composable(PORTFOLIO_REPORT_ROUTE) {
                // The same controller as the list above it, not a second one: the report is a
                // reading of the trades already loaded, and a second fetch would let the two
                // screens disagree about what the account did.
                PortfolioReportScreen(controller = portfolioController)
            }
            composable(ACTIVITY_ROUTE) {
                ActivityScreen(
                    onOpenAlerts = { navController.navigate(ALERTS_ROUTE) },
                    controller = notificationController,
                    executionController = executionController,
                    signalController = signalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    platform = activePlatform,
                )
            }
            composable(LAUNCH_READINESS_ROUTE) {
                val context = LocalContext.current
                val crashes = remember(context, appLog) { CrashReport(context, appLog) }
                // Read once per visit rather than watched: a crash file cannot change while the
                // app that would write it is the one on screen.
                var lastCrash by remember { mutableStateOf(crashes.last()) }
                LaunchReadinessScreen(
                    notificationPermissionState = notificationPermissionState,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onSendFeedback = onSendFeedback,
                    versionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onOpenDiagnostics = { navController.navigate(ADMIN_ROUTE) },
                    lastCrash = lastCrash,
                    onCopyCrash = { trace ->
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText(
                                "CoinePro crash",
                                "CoinePro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n" + trace,
                            ),
                        )
                    },
                    onClearCrash = {
                        crashes.clear()
                        lastCrash = null
                    },
                )
            }
        }
        }
        }
        }
        // Above every screen and below nothing. Placed inside the scaffold's content rather than
        // over the whole window so a message never covers the bottom bar a reader is aiming at.
        CoineProToastHost(modifier = Modifier.padding(innerPadding))
        }
    }

    if (appearanceOpen) {
        // The language lives on this sheet now rather than in the diagnostics panel, where it was
        // behind five taps on a version number — see `AppearanceSheet`'s own note. Read here rather
        // than hoisted because the store is the only thing that knows it, and the recreate below is
        // what makes the change land: the locale is applied in `attachBaseContext`, so nothing
        // already composed would pick it up.
        val appearanceContext = LocalContext.current
        AppearanceSheet(
            selected = themeMode,
            onSelect = { mode ->
                onSetThemeMode(mode)
                // Not dismissed on the colour choice below, only on the theme: the two are
                // different questions and a reader who came here for one often answers both.
                appearanceOpen = false
            },
            onDismiss = { appearanceOpen = false },
            colours = marketColors,
            onSelectColours = onSetMarketColors,
            language = AppLanguageStore.current(appearanceContext),
            onSelectLanguage = { chosen ->
                AppLanguageStore.set(appearanceContext, chosen)
                (appearanceContext as? Activity)?.recreate()
            },
        )
    }

    alertFromChart?.let { (symbol, price) ->
        val localAlerts by localAlertStore.alerts.collectAsStateWithLifecycle(initialValue = emptyList())
        AlertComposerSheet(
            symbol = symbol,
            currentPrice = price,
            full = localAlerts.size >= LocalPriceAlert.MAX_ALERTS,
            onCreate = { alert ->
                shellScope.launch {
                    localAlertStore.add(alert)
                    localAlertScheduler.sync(hasActiveAlerts = true)
                }
                alertFromChart = null
                toaster.show(alertSavedMessage, ToastTone.SUCCESS)
            },
            onDismiss = { alertFromChart = null },
        )
    }

    if (appLockOpen) {
        AppLockSheet(
            enabled = appLockEnabled,
            capability = lockCapability,
            onSetEnabled = onSetAppLockEnabled,
            onDismiss = { appLockOpen = false },
        )
    }

    if (signOutAsked) {
        CoineProConfirmDialog(
            title = stringResource(R.string.logout_confirm_title),
            message = stringResource(R.string.logout_confirm_body),
            confirmLabel = stringResource(R.string.action_logout),
            dismissLabel = stringResource(R.string.action_cancel),
            icon = CoineProIcons.SignOut,
            onConfirm = {
                signOutAsked = false
                onLogout()
            },
            onDismiss = { signOutAsked = false },
        )
    }
}


/**
 * The categories a given reader can actually receive, grouped the way every app in this market
 * groups them.
 *
 * A guest is shown the market group and nothing else. The other three need an account to fire at
 * all, and a switch for an event that cannot happen turns the whole screen into decoration —
 * which is the fastest way to teach somebody that this app's settings do not mean anything.
 */
@Composable
private fun notificationSections(guest: Boolean): List<NotificationSection> {
    val market = NotificationSection(
        title = stringResource(R.string.channel_group_market),
        categories = listOfNotNull(
            NotificationCategory.PRICE_ALERT,
            NotificationCategory.WATCHLIST_MOVE,
            NotificationCategory.NEWS,
            NotificationCategory.ANNOUNCEMENT,
            NotificationCategory.CALENDAR,
            NotificationCategory.AI_SETUP.takeUnless { guest },
        ),
    )
    if (guest) {
        return listOf(
            market,
            NotificationSection(
                title = stringResource(R.string.channel_group_other),
                categories = listOf(NotificationCategory.MARKETING),
            ),
        )
    }
    return listOf(
        NotificationSection(
            title = stringResource(R.string.channel_group_trading),
            categories = listOf(
                NotificationCategory.NEW_SIGNAL,
                NotificationCategory.TARGET_HIT,
                NotificationCategory.STOP_HIT,
                NotificationCategory.SIGNAL_CLOSED,
                NotificationCategory.COPY_OPENED,
                NotificationCategory.COPY_CLOSED,
                NotificationCategory.COPY_FAILED,
            ),
        ),
        market,
        NotificationSection(
            title = stringResource(R.string.channel_group_account),
            categories = listOf(NotificationCategory.SECURITY, NotificationCategory.ACCOUNT),
        ),
        NotificationSection(
            title = stringResource(R.string.channel_group_other),
            categories = listOf(NotificationCategory.MARKETING),
        ),
    )
}

/* -------------------------------------------------------------- hub glue */

/**
 * The feed's own state, in the hub's four grades.
 *
 * Degraded is a warning rather than a failure on purpose: the socket is down but the HTTP snapshot
 * is carrying quotes, so the screen is still telling the truth — just less often.
 */
private fun MarketConnectionState.tone(): HubTone = when (this) {
    MarketConnectionState.LIVE -> HubTone.GOOD
    MarketConnectionState.CONNECTING, MarketConnectionState.DEGRADED -> HubTone.WARN
    MarketConnectionState.OFFLINE -> HubTone.BAD
    MarketConnectionState.IDLE -> HubTone.IDLE
}

/** The platform's own short name, for the badge on the profile hero. */
@StringRes
private fun MarketPlatform.labelRes(): Int = when (this) {
    MarketPlatform.TRADEYAR -> R.string.platform_crypto
    MarketPlatform.COINEPRO_FX -> R.string.platform_forex
}

@StringRes
private fun MarketConnectionState.labelRes(): Int = when (this) {
    MarketConnectionState.LIVE -> R.string.hub_feed_live
    MarketConnectionState.CONNECTING -> R.string.hub_feed_connecting
    MarketConnectionState.DEGRADED -> R.string.hub_feed_degraded
    MarketConnectionState.OFFLINE -> R.string.hub_feed_offline
    MarketConnectionState.IDLE -> R.string.hub_feed_idle
}

private fun NotificationPermissionUiState.toHubPermission(): PushPermission = when (this) {
    NotificationPermissionUiState.NOT_CONFIGURED -> PushPermission.NOT_CONFIGURED
    NotificationPermissionUiState.NOT_REQUIRED -> PushPermission.NOT_REQUIRED
    NotificationPermissionUiState.AVAILABLE_TO_REQUEST -> PushPermission.AVAILABLE
    NotificationPermissionUiState.DENIED -> PushPermission.DENIED
    NotificationPermissionUiState.GRANTED -> PushPermission.GRANTED
}

/**
 * The venue that executes for one platform — MetaTrader 5 for forex, LBank for crypto.
 *
 * Never both. Showing a reader the other platform's broker is the same mixing bug as showing its
 * symbols, and here it would invite someone to judge their execution readiness from an account
 * this session is not even signed in to.
 */
private fun ConnectionsState.forPlatform(platform: MarketPlatform): VenueStatus {
    val connection = when (platform) {
        MarketPlatform.COINEPRO_FX -> mt5
        MarketPlatform.TRADEYAR -> lbank
    }
    return VenueStatus(
        name = if (platform == MarketPlatform.COINEPRO_FX) "MetaTrader 5" else "LBank",
        configured = connection != null,
        connected = connection?.connected == true,
    )
}

/**
 * The depth ladder's preference port, over the store that already holds per-symbol view state.
 *
 * `SymbolChartStateStore` is where "how this symbol was last being looked at" lives, and an
 * aggregation step and a size column are that kind of fact. A second preferences key for the same
 * reader on the same symbol would be a second thing to evict, migrate and keep in step with the
 * first.
 *
 * [save] reads the row before writing it because `put` replaces rather than merges — that is its
 * documented contract, and a fresh `SymbolChartState` written from here would silently throw away
 * the symbol's timeframe, indicators and drawings the moment somebody touched the aggregation
 * chips. `updatedAt` is bumped for the reason the chart bumps it: this symbol has just been looked
 * at, and the store evicts by that.
 */
private class SymbolChartDepthPreferences(
    private val store: SymbolChartStateStore,
) : DepthLadderPreferences {

    override suspend fun load(symbol: String): DepthLadderPreference? {
        val row = store.state(symbol).first() ?: return null
        val step = row.domStep?.toDoubleOrNull()
        val figure = row.domFigure?.let { id -> LadderFigure.entries.firstOrNull { it.name == id } }
        // Nothing stored for the ladder specifically. A default-valued preference here would be
        // indistinguishable from a reader who had deliberately chosen the raw book, which matters
        // the day the ladder's own default changes.
        if (step == null && figure == null) return null
        return DepthLadderPreference(step = step, figure = figure ?: LadderFigure.AMOUNT)
    }

    override suspend fun save(symbol: String, preference: DepthLadderPreference) {
        val existing = store.state(symbol).first() ?: SymbolChartState(symbol = symbol)
        store.put(
            existing.copy(
                symbol = symbol,
                domStep = preference.step?.toString(),
                domFigure = preference.figure.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

/**
 * The relay's health as the one sentence a reader needs, or null when there is nothing to say.
 *
 * Here rather than in `core:designsystem`, which cannot see `core:marketdata`, and rather than in
 * `core:marketdata`, which has no business owning a sentence. The reading rule itself belongs to
 * `PriceFeedStatus` — this only chooses which of the three cases applies, healthy included, and
 * healthy is null because a bar that says «همه‌چیز خوب است» is a bar nobody reads.
 */
private fun PriceFeedStatus.reading(): PriceFeedReading? = when {
    !degraded -> null
    tier == PriceFeedTier.UNKNOWN -> PriceFeedReading.UNKNOWN
    fullOutage -> PriceFeedReading.FULL
    else -> PriceFeedReading.PARTIAL
}

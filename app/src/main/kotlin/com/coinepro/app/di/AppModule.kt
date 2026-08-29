package com.coinepro.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.coinepro.app.BuildConfig
import com.coinepro.app.alerts.AlertFireStateStore
import com.coinepro.app.alerts.GatewayServerAlerts
import com.coinepro.app.auth.RegistrationStore
import com.coinepro.core.academy.AcademyController
import com.coinepro.core.academy.AcademyGateway
import com.coinepro.core.academy.NetworkAcademyGateway
import com.coinepro.core.account.AccountController
import com.coinepro.core.account.AccountGateway
import com.coinepro.core.account.NetworkAccountGateway
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aiassistant.AiAssistantGateway
import com.coinepro.core.aiassistant.NetworkAiAssistantGateway
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aisignal.AiSignalGateway
import com.coinepro.core.aisignal.MarketCatalogAiSymbolCatalog
import com.coinepro.core.aisignal.NetworkAiSignalGateway
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.aivision.AiVisionGateway
import com.coinepro.core.aivision.NetworkAiVisionGateway
import com.coinepro.core.auth.AuthGateway
import com.coinepro.core.auth.EmailAuthController
import com.coinepro.core.auth.EmailAuthGateway
import com.coinepro.core.auth.FederatedEmailAuthGateway
import com.coinepro.core.auth.NetworkAuthGateway
import com.coinepro.core.auth.NetworkEmailAuthGateway
import com.coinepro.core.auth.PlatformCapabilities
import com.coinepro.core.auth.PlatformSessions
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionMemory
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.chartevents.ChartEventController
import com.coinepro.core.chartevents.MarketIntelChartEventFeed
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.copytrade.CopyTradeGateway
import com.coinepro.core.copytrade.NetworkCopyTradeGateway
import com.coinepro.core.database.CoineProDatabase
import com.coinepro.core.database.CoineProDatabaseFactory
import com.coinepro.core.database.RoomCandleCache
import com.coinepro.core.database.RoomMarketDataCache
import com.coinepro.core.database.RoomSignalHistoryCache
import com.coinepro.core.datastore.ActivePlatformSelector
import com.coinepro.core.datastore.ActivePlatformStore
import com.coinepro.core.datastore.AlertAuditStore
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.datastore.ChartEventPrefsStore
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.DrawingImageStore
import com.coinepro.core.datastore.DrawingSyncStore
import com.coinepro.core.datastore.DrawingTemplateStore
import com.coinepro.core.datastore.IndicatorTemplateStore
import com.coinepro.core.datastore.InstallIdStore
import com.coinepro.core.datastore.IntervalFavouritesStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.PaperLedgerPrefStore
import com.coinepro.core.datastore.ProfileStore
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.datastore.TimeZonePrefStore
import com.coinepro.core.datastore.UserPreferencesStore
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.datastore.WidgetSnapshotStore
import com.coinepro.core.diagnostics.AdminBuildInfo
import com.coinepro.core.diagnostics.AdminController
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.EndpointProber
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.diagnostics.PlatformBuildInfo
import com.coinepro.core.diagnostics.RequestLog
import com.coinepro.core.diagnostics.RequestLogInterceptor
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.ExecutionGateway
import com.coinepro.core.execution.NetworkExecutionGateway
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.NetworkGuestGateway
import com.coinepro.core.journal.JournalController
import com.coinepro.core.marketdata.AcademyTokenStore
import com.coinepro.core.marketdata.CandleCache
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CoineProFxCandleGateway
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketSnapshotGateway
import com.coinepro.core.marketdata.NetworkAcademyTokenStore
import com.coinepro.core.marketdata.NetworkMarketCatalogGateway
import com.coinepro.core.marketdata.NetworkMarketSnapshotGateway
import com.coinepro.core.marketdata.TradeYarCandleGateway
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketIntelGateway
import com.coinepro.core.marketintel.NetworkMarketIntelGateway
import com.coinepro.core.membership.MembershipController
import com.coinepro.core.membership.MembershipGateway
import com.coinepro.core.membership.NetworkMembershipGateway
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.network.NetworkFactory
import com.coinepro.core.network.NetworkStatus
import com.coinepro.core.notifications.NetworkNotificationGateway
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.NotificationGateway
import com.coinepro.core.orderbook.DepthUnavailableReason
import com.coinepro.core.orderbook.NoDepthGateway
import com.coinepro.core.orderbook.OrderBookGateway
import com.coinepro.core.orderbook.TradeYarOrderBookGateway
import com.coinepro.core.papertrade.PaperLedgerStore
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.portfolio.PortfolioController
import com.coinepro.core.portfolio.PortfolioGateway
import com.coinepro.core.portfolio.PortfolioGatewayFactory
import com.coinepro.core.script.ScriptController
import com.coinepro.core.security.KeystoreSessionTokenStorage
import com.coinepro.core.signals.NetworkSignalGateway
import com.coinepro.core.signals.SignalController
import com.coinepro.core.signals.SignalGateway
import com.coinepro.core.signals.SignalHistoryCache
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.webhook.WebhookDispatcher
import com.coinepro.core.webhook.WebhookStore
import com.coinepro.feature.alerts.AlertsController
import com.coinepro.feature.alerts.StoredWebhooks
import com.coinepro.feature.chart.ChartWorkspaceStore
import com.coinepro.feature.screener.CandleScreenerBarSource
import com.coinepro.feature.screener.ScreenerController
import com.coinepro.feature.screener.ScreenerStore
import com.coinepro.feature.terminal.TerminalController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * App-wide preferences. Deliberately a separate store from the encrypted session one: this holds
 * choices a reader made, not credentials, and the two have different backup and clearing rules.
 */
private val Context.appPreferences by preferencesDataStore(name = "coinepro_preferences")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.appPreferences

    @Provides
    @Singleton
    fun installIdStore(preferences: DataStore<Preferences>): InstallIdStore =
        InstallIdStore(preferences)

    /**
     * One log for both platforms, with each entry naming which one made the call.
     *
     * Shared rather than split because the admin panel's most useful view is the whole timeline in
     * order — a crypto call failing right after a platform switch is the kind of thing two separate
     * logs would hide.
     */
    @Provides
    @Singleton
    fun requestLog(): RequestLog = RequestLog()

    /**
     * The app's narrative log, one instance for the process.
     *
     * A singleton because its whole value is that everything lands in *one* sequence: a socket drop,
     * the reconnect, the 401 on the next call and the sign-out that followed are one story, and
     * three separate logs is three stories nobody can line up.
     */
    @Provides
    @Singleton
    fun appLog(): AppLog = AppLog()

    /**
     * The panel's own view of the build.
     *
     * Assembled here because BuildConfig belongs to the application, and a core module reaching
     * into generated application code would tie the diagnostics to one app's build script.
     */
    @Provides
    @Singleton
    fun adminController(
        @ForexPlatform forexClient: OkHttpClient,
        @CryptoPlatform cryptoClient: OkHttpClient,
        requestLog: RequestLog,
        appLog: AppLog,
        activePlatformStore: ActivePlatformStore,
        scope: CoroutineScope,
    ): AdminController = AdminController(
        build = AdminBuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toString(),
            environment = BuildConfig.BUILD_ENVIRONMENT,
            applicationId = BuildConfig.APPLICATION_ID,
            debuggable = BuildConfig.DEBUG,
            firebaseConfigured = BuildConfig.FIREBASE_PROJECT_ID.isNotBlank(),
        ),
        platforms = listOf(
            PlatformBuildInfo(MarketPlatform.COINEPRO_FX, BuildConfig.API_BASE_URL),
            PlatformBuildInfo(MarketPlatform.TRADEYAR, BuildConfig.TRADEYAR_API_BASE_URL),
        ),
        probers = mapOf(
            MarketPlatform.COINEPRO_FX to EndpointProber(
                forexClient,
                BuildConfig.API_BASE_URL,
                MarketPlatform.COINEPRO_FX,
            ),
            MarketPlatform.TRADEYAR to EndpointProber(
                cryptoClient,
                BuildConfig.TRADEYAR_API_BASE_URL,
                MarketPlatform.TRADEYAR,
            ),
        ),
        requestLog = requestLog,
        appLog = appLog,
        scope = scope,
        initialPlatform = activePlatformStore.available.first(),
    )

    // ── CoinePro-FX (Forex) ────────────────────────────────────────────────────────────────────
    // The unqualified bindings stay pointed at CoinePro-FX so every existing gateway keeps working
    // unchanged while the crypto side is wired up screen by screen.

    @Provides
    @Singleton
    @ForexPlatform
    fun forexSessionMemory(): SessionMemory = SessionMemory()

    @Provides
    @Singleton
    fun sessionMemory(@ForexPlatform memory: SessionMemory): SessionMemory = memory

    @Provides
    @Singleton
    @ForexPlatform
    fun forexTokenStorage(@ApplicationContext context: Context): SessionTokenStorage =
        KeystoreSessionTokenStorage(context, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    fun tokenStorage(@ForexPlatform storage: SessionTokenStorage): SessionTokenStorage = storage

    @Provides
    @Singleton
    @ForexPlatform
    fun forexOkHttp(
        @ForexPlatform memory: SessionMemory,
        installIds: InstallIdStore,
        requestLog: RequestLog,
        appLog: AppLog,
    ): OkHttpClient = NetworkFactory.okHttpClient(
        bearerToken = memory::token,
        onUnauthorized = memory::notifyUnauthorized,
        installId = installIds.providerFor(MarketPlatform.COINEPRO_FX),
        appVersion = BuildConfig.VERSION_NAME,
        recorder = RequestLogInterceptor(requestLog, MarketPlatform.COINEPRO_FX, appLog = appLog),
        enableHttpLogging = BuildConfig.DEBUG,
    )

    @Provides
    @Singleton
    fun okHttp(@ForexPlatform client: OkHttpClient): OkHttpClient = client

    @Provides
    @Singleton
    @ForexPlatform
    fun forexRetrofit(@ForexPlatform client: OkHttpClient): Retrofit =
        NetworkFactory.retrofit(BuildConfig.API_BASE_URL, client)

    @Provides
    @Singleton
    fun retrofit(@ForexPlatform retrofit: Retrofit): Retrofit = retrofit

    // ── TradeYar (Crypto) ──────────────────────────────────────────────────────────────────────
    // A separate client and token: the two platforms are separate accounts, and sharing either
    // would send one platform's credential to the other's host.

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoSessionMemory(): SessionMemory = SessionMemory()

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoTokenStorage(@ApplicationContext context: Context): SessionTokenStorage =
        KeystoreSessionTokenStorage(context, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoOkHttp(
        @CryptoPlatform memory: SessionMemory,
        installIds: InstallIdStore,
        requestLog: RequestLog,
        appLog: AppLog,
    ): OkHttpClient = NetworkFactory.okHttpClient(
        bearerToken = memory::token,
        onUnauthorized = memory::notifyUnauthorized,
        installId = installIds.providerFor(MarketPlatform.TRADEYAR),
        appVersion = BuildConfig.VERSION_NAME,
        recorder = RequestLogInterceptor(requestLog, MarketPlatform.TRADEYAR, appLog = appLog),
        enableHttpLogging = BuildConfig.DEBUG,
    )

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoRetrofit(@CryptoPlatform client: OkHttpClient): Retrofit =
        NetworkFactory.retrofit(BuildConfig.TRADEYAR_API_BASE_URL, client)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CoineProDatabase =
        CoineProDatabaseFactory.create(context)

    @Provides
    @Singleton
    fun marketDataCache(database: CoineProDatabase): MarketDataCache =
        RoomMarketDataCache(database.cacheDao())

    @Provides
    @Singleton
    fun signalHistoryCache(database: CoineProDatabase): SignalHistoryCache =
        RoomSignalHistoryCache(database.cacheDao())

    @Provides
    @Singleton
    @ForexPlatform
    fun forexAuthGateway(@ForexPlatform retrofit: Retrofit): AuthGateway =
        NetworkAuthGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    fun authGateway(@ForexPlatform gateway: AuthGateway): AuthGateway = gateway

    @Provides
    @Singleton
    @ForexPlatform
    fun forexAccountGateway(@ForexPlatform retrofit: Retrofit): AccountGateway =
        NetworkAccountGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAccountGateway(@CryptoPlatform retrofit: Retrofit): AccountGateway =
        NetworkAccountGateway.create(retrofit, MarketPlatform.TRADEYAR)

    /**
     * The public surface, on the crypto retrofit.
     *
     * Not keyed by platform, because there is only one: TradeYar publishes its `api/v1/public` routes for
     * its own web site and CoinePro-FX publishes nothing without a token. Making this a map of two
     * with one entry that always fails would put an empty market in front of every guest on the
     * forex platform and call it a feature. `docs/REQUEST4_ACCOUNT_DELETION.md` §2 asks CoinePro-FX
     * for either a public read or a guest token; until one arrives, a guest sees the crypto market,
     * and that is stated rather than disguised.
     *
     * The crypto client is reused rather than a bare one built: with nobody signed in there is no
     * token for the auth interceptor to attach, and the install-id header these calls do carry is
     * exactly what the server's rate limiter wants from an anonymous caller.
     */
    /**
     * The reader's watchlist, on the same preferences file as every other local choice.
     *
     * Not per platform. A reader who stars gold on the forex side and bitcoin on the crypto side
     * has one list of things they are watching, and splitting it in two would mean the star they
     * pressed vanishing when they switched tabs.
     */
    /**
     * The journal, on the app-wide scope.
     *
     * Not per platform and not per session: a trading diary belongs to the person, not to whichever
     * backend they were looking at when they wrote the entry, and signing out must not take it away.
     */
    @Provides
    @Singleton
    fun journalController(database: CoineProDatabase, scope: CoroutineScope): JournalController =
        JournalController(database.journalDao(), scope)

    @Provides
    @Singleton
    fun paperLedgerStore(dataStore: DataStore<Preferences>): PaperLedgerStore =
        PaperLedgerPrefStore(dataStore)

    @Provides
    @Singleton
    fun paperTradeController(
        store: PaperLedgerStore,
        database: CoineProDatabase,
        scope: CoroutineScope,
    ): PaperTradeController =
        // Stored rather than in memory: the paper account is now an account, and one that Android
        // empties when it reclaims the process is not one anybody would trade a month on.
        //
        // The Room table is still read, once, on the first launch that finds nothing stored, so a
        // reader's existing paper trades survive the rebuild rather than being deleted by an
        // update they did not ask for. See `PaperMigration`.
        PaperTradeController(store, scope, legacy = database.paperTradeDao())

    /**
     * The script studio, on the app-wide scope and not per platform.
     *
     * A script the reader wrote is theirs, not the backend's: the same moving-average cross means
     * the same thing on gold and on bitcoin, and switching platform must not take it away. One
     * instance rather than one per chart, so opening the studio from a second symbol keeps the
     * script that was already in the editor.
     */
    @Provides
    @Singleton
    fun scriptController(database: CoineProDatabase, scope: CoroutineScope): ScriptController =
        ScriptController(database.savedScriptDao(), scope)

    @Provides
    @Singleton
    fun watchlistStore(dataStore: DataStore<Preferences>): WatchlistStore = WatchlistStore(dataStore)

    /**
     * Device-wide preferences that belong to the phone rather than to whoever is signed in.
     *
     * Deliberately not folded into [ProfileStore], which is cleared on sign-out: a reader who
     * pinned the app dark did not ask for it to go light again because they signed out.
     */
    @Provides
    @Singleton
    fun userPreferencesStore(dataStore: DataStore<Preferences>): UserPreferencesStore =
        UserPreferencesStore(dataStore)

    /**
     * What the home-screen widget last knew.
     *
     * In the same preferences file as everything else, so the widget's process and the app's read
     * one store rather than two that can disagree. See [WidgetSnapshotStore].
     */
    @Provides
    @Singleton
    fun widgetSnapshotStore(dataStore: DataStore<Preferences>): WidgetSnapshotStore =
        WidgetSnapshotStore(dataStore)

    /**
     * The bars already held, so a chart draws before it fetches.
     *
     * Not cleared on sign-out, deliberately: a candle is a public fact about a market. The price of
     * gold at ten o'clock is not the reader's private data, and discarding it would slow the next
     * chart open to protect nothing. See [CandleCache].
     */
    @Provides
    @Singleton
    fun candleCache(database: CoineProDatabase): CandleCache = RoomCandleCache(database.candleCacheDao())

    /**
     * Whether the phone has a network at all.
     *
     * Application-scoped and stateless — it registers its listener per collector, so a singleton
     * here holds nothing but a context. See [NetworkStatus] for why this is not "can we reach our
     * servers".
     */
    @Provides
    @Singleton
    fun networkStatus(@ApplicationContext context: Context): NetworkStatus = NetworkStatus(context)

    @Provides
    @Singleton
    fun chartLayoutStore(dataStore: DataStore<Preferences>): ChartLayoutStore = ChartLayoutStore(dataStore)

    /**
     * How each symbol was last being looked at — its timeframe, its chart type, its indicators.
     *
     * Beside the layout store rather than folded into it, because the two answer opposite
     * questions. A layout is the apparatus a reader chose and *carries* from instrument to
     * instrument; this is what one particular instrument was left on, and carrying it anywhere
     * would be the bug it exists to fix. The loudest small complaint about the large mobile
     * terminals is that chart settings are global: change the timeframe while reading gold and
     * every other chart changes with it. Keyed per symbol, that stops happening.
     *
     * The same preferences file as everything else here. It is a local preference and nothing in
     * it is sent to either backend.
     */
    @Provides
    @Singleton
    fun symbolChartStateStore(dataStore: DataStore<Preferences>): SymbolChartStateStore =
        SymbolChartStateStore(dataStore)

    /**
     * Where a reader's drawings live between sessions.
     *
     * Separate from the layout store and keyed per symbol, because they answer opposite questions:
     * a layout is the apparatus a reader looks through and travels between instruments, and a
     * drawing is a mark on one instrument's prices that means nothing on another.
     */
    @Provides
    @Singleton
    fun chartDrawingStore(dataStore: DataStore<Preferences>): ChartDrawingStore =
        ChartDrawingStore(dataStore)

    /**
     * Where the image drawing tool's pictures live — files, not preferences.
     *
     * Every other chart store packs its rows into one preferences string, and a bitmap must never
     * go there: a photo is hundreds of kilobytes, DataStore rewrites the whole file on every edit,
     * and a reader with three pictures on a chart would pay for all three on every drawing they
     * moved. So the drawing carries an opaque id and this owns the bytes.
     *
     * Under `filesDir` rather than the cache directory, deliberately. The system may empty a cache
     * at any moment, and a drawing whose picture vanished because the phone needed space is a
     * drawing the reader did not delete — `DrawingImage.Gone` exists for a file that is genuinely
     * gone, not as somewhere to put a routine eviction.
     */
    @Provides
    @Singleton
    fun drawingImageStore(@ApplicationContext context: Context): DrawingImageStore =
        DrawingImageStore(File(context.filesDir, "chart-images"))

    /**
     * Saved per-tool drawing styles.
     *
     * The same `DataStore` every other chart store uses, deliberately: these are all one reader's
     * chart preferences and splitting them across files would mean a restore that half worked.
     */
    @Provides
    @Singleton
    fun drawingTemplateStore(dataStore: DataStore<Preferences>): DrawingTemplateStore =
        DrawingTemplateStore(dataStore)

    /**
     * Which intervals the reader keeps on the chart's own bar.
     *
     * Stored rather than hard-coded because the six the app shipped with are a guess, and a trader
     * who works one instrument on two timeframes should not scroll past four they never open.
     */
    @Provides
    @Singleton
    fun intervalFavouritesStore(dataStore: DataStore<Preferences>): IntervalFavouritesStore =
        IntervalFavouritesStore(dataStore)

    /**
     * A named set of indicators, applied without disturbing anything else.
     *
     * Distinct from a saved layout on purpose: applying a layout also replaces the timeframe, the
     * chart type, the scale mode and the colours, which is right for "restore my workspace" and
     * wrong for "put my four oscillators on whatever I am looking at now".
     */
    @Provides
    @Singleton
    fun indicatorTemplateStore(dataStore: DataStore<Preferences>): IndicatorTemplateStore =
        IndicatorTemplateStore(dataStore)

    /** Whether a drawing belongs to one chart, to the layout, or to every layout on that symbol. */
    @Provides
    @Singleton
    fun drawingSyncStore(dataStore: DataStore<Preferences>): DrawingSyncStore =
        DrawingSyncStore(dataStore)

    /** Which kinds of event draw a glyph on the time axis. News on, the rest off. */
    @Provides
    @Singleton
    fun chartEventPrefsStore(dataStore: DataStore<Preferences>): ChartEventPrefsStore =
        ChartEventPrefsStore(dataStore)

    /**
     * The zone the chart's clock is read in.
     *
     * Tehran by default, and the reason is arithmetic rather than patriotism: UTC+3:30 is a
     * half-hour offset, and code that cuts daily buckets from epoch seconds lands them half an hour
     * out for every reader here.
     */
    @Provides
    @Singleton
    fun timeZonePrefStore(dataStore: DataStore<Preferences>): TimeZonePrefStore =
        TimeZonePrefStore(dataStore)

    /**
     * Where an alert can be sent besides this phone, and what happened when it was.
     *
     * The store keeps the targets and the delivery log together on purpose: a webhook that fails
     * silently is the same failure as an alert that fails silently, and the log is the only thing
     * that tells the difference between "the market never got there" and "we could not reach you".
     */
    @Provides
    @Singleton
    fun webhookStore(dataStore: DataStore<Preferences>): WebhookStore = WebhookStore(dataStore)

    /** Fans one fired alert out to every enabled target, recording each attempt. */
    @Provides
    @Singleton
    fun webhookDispatcher(store: WebhookStore): WebhookDispatcher = WebhookDispatcher(store)

    /**
     * How the reader has arranged the chart screen itself: where the split with the watchlist
     * sits, what the two panes tie together, and which instrument the second pane was left on.
     *
     * The same `DataStore` as every other chart store, and deliberately so — see
     * [drawingTemplateStore]. These are all one reader's chart preferences, and splitting them
     * across files would mean a restore that half worked: a divider back where it was above a
     * second pane that had forgotten its symbol.
     */
    @Provides
    @Singleton
    fun chartWorkspaceStore(dataStore: DataStore<Preferences>): ChartWorkspaceStore =
        ChartWorkspaceStore(dataStore)

    /**
     * The reader's own name and face.
     *
     * The same preferences file as the watchlist, and for the same reason: it is a local
     * preference, not account data. Nothing in it is sent to either backend — see [ProfileStore].
     */
    @Provides
    @Singleton
    fun profileStore(dataStore: DataStore<Preferences>): ProfileStore = ProfileStore(dataStore)

    /**
     * What the reader agreed to be interrupted about.
     *
     * Local, like the profile and for a stronger reason: of the fifteen categories, two map onto a
     * flag either backend accepts and the rest are the app's own. Sending the whole set to one of
     * two servers would leave somebody who switches platform with their choices half-applied.
     */
    @Provides
    @Singleton
    fun notificationSettingsStore(dataStore: DataStore<Preferences>): NotificationSettingsStore =
        NotificationSettingsStore(dataStore)

    /** Price alerts that need no account. See [LocalAlertStore] for why they are on the device. */
    @Provides
    @Singleton
    fun localAlertStore(dataStore: DataStore<Preferences>): LocalAlertStore = LocalAlertStore(dataStore)

    /**
     * Every firing an alert has ever had, for the sheet that explains itself.
     *
     * From the graph rather than built where it is needed, so the screen that *shows* the log and
     * the evaluator that *writes* it are looking at one store. The store holds no state of its own —
     * everything it knows is in the preferences file — so a second instance over the same file
     * would behave identically; sharing it is about the two never drifting apart in future, not
     * about correctness today.
     */
    @Provides
    @Singleton
    fun alertAuditStore(dataStore: DataStore<Preferences>): AlertAuditStore = AlertAuditStore(dataStore)

    /**
     * The alert centre's controller, and the three things it cannot find out for itself.
     *
     * **The catalogue** it offers in the symbol picker is every market either backend quotes, so an
     * alert can be put on gold and on Bitcoin from one screen — alerts are evaluated over the public
     * guest route and are not a platform's property the way a position is. See
     * [AlertSymbolCatalogue] for why it is a supplier and when it loads.
     *
     * **The timeframe** of an alert is not stored on the alert. It is whatever bar the reader left
     * that symbol's chart on, which is exactly what the evaluator reads when it runs the condition,
     * so the label on the row and the bar it fires on come from one place. A symbol the reader has
     * never charted has no timeframe and the row then says nothing rather than inventing a default.
     *
     * **Forgetting the fire state** is the deletion the store cannot do for itself: the evaluator's
     * per-symbol stamps live in the application module and would otherwise outlive the alert they
     * belong to. See [AlertsController] for what a new alert inheriting them would do.
     */
    @Provides
    @Singleton
    fun alertsController(
        store: LocalAlertStore,
        audit: AlertAuditStore,
        fireStates: AlertFireStateStore,
        chartStates: SymbolChartStateStore,
        marketCache: MarketDataCache,
        @ForexPlatform forexCatalog: MarketCatalogGateway,
        @CryptoPlatform cryptoCatalog: MarketCatalogGateway,
        drawings: ChartDrawingStore,
        watchlist: WatchlistStore,
        serverAlerts: GatewayServerAlerts,
        webhookStore: WebhookStore,
        webhookDispatcher: WebhookDispatcher,
        scope: CoroutineScope,
    ): AlertsController {
        val catalogue = AlertSymbolCatalogue(
            gateways = listOf(forexCatalog, cryptoCatalog),
            cache = marketCache,
            scope = scope,
        )
        val timeframes = SymbolTimeframes(chartStates, scope)
        return AlertsController(
            store = store,
            audit = audit,
            catalogOf = catalogue::symbols,
            scope = scope,
            timeframeOf = { alert -> timeframes.of(alert.symbol) },
            forgetFireState = fireStates::forget,
            // Four features are built and dark without these four lines. A drawing alert has a
            // trigger, a codec and a level resolver and no way to be created; a watchlist alert
            // has an evaluator that expands it per symbol and a draft that always writes a single
            // ticker; the venue control shows one venue; and the webhook sheet lists nothing.
            drawingsOf = { symbol -> drawings.drawings(symbol).first() },
            watchlists = watchlist.lists(),
            server = serverAlerts,
            webhooks = StoredWebhooks(webhookStore, webhookDispatcher),
        )
    }

    @Provides
    @Singleton
    fun guestGateway(@CryptoPlatform retrofit: Retrofit): GuestGateway =
        NetworkGuestGateway.create(retrofit)

    @Provides
    @Singleton
    fun guestController(gateway: GuestGateway, scope: CoroutineScope): GuestController =
        GuestController(gateway, scope)

    /**
     * Membership is TradeYar's, and only TradeYar's.
     *
     * The affiliate arrangement, the UID check and the deposit threshold all live on the crypto
     * side; CoinePro-FX sells subscriptions instead. Binding this per-platform would offer a UID
     * form on a server that has no route for it.
     */
    @Provides
    @Singleton
    fun membershipGateway(@CryptoPlatform retrofit: Retrofit): MembershipGateway =
        NetworkMembershipGateway.create(retrofit)

    @Provides
    @Singleton
    fun membershipController(gateway: MembershipGateway, scope: CoroutineScope): MembershipController =
        MembershipController(gateway, scope)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexAccountController(
        @ForexPlatform gateway: AccountGateway,
        scope: CoroutineScope,
    ): AccountController = AccountController(gateway, scope)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAccountController(
        @CryptoPlatform gateway: AccountGateway,
        scope: CoroutineScope,
    ): AccountController = AccountController(gateway, scope)

    /**
     * Keyed by platform so the screen reads the controller for whichever backend is on screen. The
     * two hold different accounts, and showing one platform's balance under the other's name would
     * be the same class of bug as mixing their symbols.
     */
    @Provides
    @Singleton
    fun accountControllers(
        @ForexPlatform forex: AccountController,
        @CryptoPlatform crypto: AccountController,
    ): Map<MarketPlatform, AccountController> = mapOf(
        MarketPlatform.COINEPRO_FX to forex,
        MarketPlatform.TRADEYAR to crypto,
    )

    // The email-first flow, per platform: the two backends have separate accounts, and a token
    // minted by one is meaningless to the other.
    @Provides
    @Singleton
    @ForexPlatform
    fun forexEmailAuthGateway(@ForexPlatform retrofit: Retrofit): EmailAuthGateway =
        NetworkEmailAuthGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoEmailAuthGateway(@CryptoPlatform retrofit: Retrofit): EmailAuthGateway =
        NetworkEmailAuthGateway.create(retrofit, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAuthGateway(@CryptoPlatform retrofit: Retrofit): AuthGateway =
        NetworkAuthGateway.create(retrofit, MarketPlatform.TRADEYAR)

    /**
     * The sign-in flow's own controller, one per platform.
     *
     * It hands a completed sign-in to that platform's [SessionController] and keeps nothing: the
     * app's idea of who is signed in stays in one place, and the screens most likely to be redrawn
     * do not become a second one.
     */
    @Provides
    @Singleton
    @ForexPlatform
    fun forexEmailAuthController(
        @ForexPlatform gateway: EmailAuthGateway,
        @ForexPlatform session: SessionController,
        scope: CoroutineScope,
    ): EmailAuthController = EmailAuthController(gateway, scope, session::adoptSession)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoEmailAuthController(
        @CryptoPlatform gateway: EmailAuthGateway,
        @CryptoPlatform session: SessionController,
        scope: CoroutineScope,
    ): EmailAuthController = EmailAuthController(gateway, scope, session::adoptSession)

    /**
     * The sign-in screen's controller: one screen, two user tables.
     *
     * **New accounts are TradeYar's.** A reader registering here is registering with *CoinePro*,
     * not with one of its two backends — and the CoinePro-FX server signs its mail as "CoinePro Fx"
     * and files the account in the forex product's user table. The name in the reader's inbox is
     * the product they think they joined, and the row belongs in the system that owns the account.
     *
     * **Existing accounts are wherever they already are.** Until 1.27.0 this was CoinePro-FX, so
     * every account made before that release lives there, with a correct password TradeYar has
     * never heard of. Sign-in therefore federates — see [FederatedEmailAuthGateway] for the whole
     * argument, including what deliberately does not.
     *
     * **The session goes to the backend that issued it.** That is what the `when` below is for, and
     * it is not a detail: a CoinePro-FX token written into TradeYar's storage makes an app that
     * believes it is signed in and is answered 401 by everything, which a reader experiences as
     * being thrown straight back to the guest screen the instant they get in.
     */
    @Provides
    @Singleton
    fun emailAuthController(
        @CryptoPlatform home: EmailAuthGateway,
        @ForexPlatform legacy: EmailAuthGateway,
        @CryptoPlatform homeSession: SessionController,
        @ForexPlatform legacySession: SessionController,
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
    ): EmailAuthController {
        // New accounts are TradeYar's; an account made before 1.27.0 is CoinePro-FX's and its owner
        // must still be able to get in. See [FederatedEmailAuthGateway] for the whole argument,
        // including what deliberately does not federate.
        val gateway = FederatedEmailAuthGateway(home = home, legacy = listOf(legacy))
        return EmailAuthController(
            gateway = gateway,
            scope = scope,
            // Survives the process being killed while the reader is in their inbox looking for the
            // code. Without it they come back to a sign-in screen for an account that was never
            // created — see [RegistrationMemory].
            memory = RegistrationStore(dataStore),
            onAuthenticated = { session ->
            // The token goes to the session that can use it. A CoinePro-FX token written into
            // TradeYar's storage produces a signed-in app whose every request comes back 401 —
            // which the reader experiences as being thrown straight back to the guest screen.
                when (session.platform) {
                    MarketPlatform.TRADEYAR -> homeSession.adoptSession(session)
                    MarketPlatform.COINEPRO_FX -> legacySession.adoptSession(session)
                }
            },
        )
    }

    /**
     * What each server says it can do, for every screen that fronts an optional feature.
     *
     * The same read the sign-in screen makes, kept rather than discarded: without it the app asks
     * for a notification permission a server may not be able to use, and offers AI screens that a
     * deployment has switched off.
     */
    @Provides
    @Singleton
    fun platformCapabilities(
        @ForexPlatform forex: EmailAuthGateway,
        @CryptoPlatform crypto: EmailAuthGateway,
        scope: CoroutineScope,
    ): PlatformCapabilities = PlatformCapabilities(platformMap(forex, crypto), scope)

    // ── One pair of bindings per surface ───────────────────────────────────────────────────────
    // Each of these used to exist once, against the CoinePro-FX client, which is why the whole
    // crypto half of the app reached nothing: a path built for one backend is not an error on the
    // other, it is a 404 worded like an outage. Every gateway now takes the platform it is for and
    // builds that platform's own addresses.

    @Provides
    @Singleton
    @ForexPlatform
    fun forexSignalGateway(@ForexPlatform retrofit: Retrofit): SignalGateway =
        NetworkSignalGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoSignalGateway(@CryptoPlatform retrofit: Retrofit): SignalGateway =
        NetworkSignalGateway.create(retrofit, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexMarketSnapshotGateway(@ForexPlatform retrofit: Retrofit): MarketSnapshotGateway =
        NetworkMarketSnapshotGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoMarketSnapshotGateway(@CryptoPlatform retrofit: Retrofit): MarketSnapshotGateway =
        NetworkMarketSnapshotGateway.create(retrofit, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    fun marketSnapshotGateways(
        @ForexPlatform forex: MarketSnapshotGateway,
        @CryptoPlatform crypto: MarketSnapshotGateway,
    ): Map<MarketPlatform, MarketSnapshotGateway> = platformMap(forex, crypto)

    @Provides
    @Singleton
    fun signalGateways(
        @ForexPlatform forex: SignalGateway,
        @CryptoPlatform crypto: SignalGateway,
    ): Map<MarketPlatform, SignalGateway> = platformMap(forex, crypto)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexNotificationGateway(@ForexPlatform retrofit: Retrofit): NotificationGateway =
        NetworkNotificationGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoNotificationGateway(@CryptoPlatform retrofit: Retrofit): NotificationGateway =
        NetworkNotificationGateway.create(retrofit, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    fun notificationGateway(@ForexPlatform gateway: NotificationGateway): NotificationGateway =
        gateway

    @Provides
    @Singleton
    @ForexPlatform
    fun forexExecutionGateway(@ForexPlatform retrofit: Retrofit): ExecutionGateway =
        NetworkExecutionGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoExecutionGateway(@CryptoPlatform retrofit: Retrofit): ExecutionGateway =
        NetworkExecutionGateway.create(retrofit, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexCopyTradeGateway(@ForexPlatform retrofit: Retrofit): CopyTradeGateway =
        NetworkCopyTradeGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoCopyTradeGateway(@CryptoPlatform retrofit: Retrofit): CopyTradeGateway =
        NetworkCopyTradeGateway.create(retrofit, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexAiSignalGateway(@ForexPlatform retrofit: Retrofit): AiSignalGateway =
        NetworkAiSignalGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAiSignalGateway(@CryptoPlatform retrofit: Retrofit): AiSignalGateway =
        NetworkAiSignalGateway.create(retrofit, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexAiVisionGateway(@ForexPlatform retrofit: Retrofit): AiVisionGateway =
        NetworkAiVisionGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAiVisionGateway(@CryptoPlatform retrofit: Retrofit): AiVisionGateway =
        NetworkAiVisionGateway.create(retrofit, MarketPlatform.TRADEYAR)

    /**
     * The conversational assistant exists on CoinePro-FX only.
     *
     * TradeYar reports `assistant: false` and serves no such route, so there is nothing to bind for
     * it. The screen is gated on the capability flag instead of being handed a client that would
     * post into thin air.
     */
    @Provides
    @Singleton
    fun aiAssistantGateway(@ForexPlatform retrofit: Retrofit): AiAssistantGateway =
        NetworkAiAssistantGateway.create(retrofit)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexMarketIntelGateway(@ForexPlatform retrofit: Retrofit): MarketIntelGateway =
        NetworkMarketIntelGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoMarketIntelGateway(@CryptoPlatform retrofit: Retrofit): MarketIntelGateway =
        NetworkMarketIntelGateway.create(retrofit, MarketPlatform.TRADEYAR)

    /**
     * The one scope every controller in this app launches into — and the seatbelt on it.
     *
     * `SupervisorJob` stops one controller's failure cancelling its siblings, which is why it was
     * here. It does **not** stop a failure taking the process down: an uncaught exception in a
     * `launch` body reaches the thread's default handler, and on Android that is a crash.
     *
     * That was one `runCatching` away from happening all over the app. Nearly every controller
     * wraps its gateway call, then does real work in `.onSuccess { }` — which is *outside* the
     * `runCatching` — over data the server just sent. `PortfolioController` doing Jalali arithmetic
     * on a server timestamp is the case that was found; it will not be the last, because the shape
     * is "parse defensively, then compute trustingly" and that shape is everywhere.
     *
     * So the handler is here rather than at each call site. It is the last line, not the first: a
     * controller that can fail should still say so in its own state, and this exists for the ones
     * that forgot. It records the failure where the diagnostics screen can show it and lets the app
     * keep running — a screen that stays on a stale value is recoverable, and a dead process is not.
     */
    @Provides
    @Singleton
    fun appScope(log: AppLog): CoroutineScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Main.immediate +
            CoroutineExceptionHandler { context, failure ->
                log.error(
                    tag = LogTag.LIFECYCLE,
                    message = "Uncaught failure on the shared app scope",
                    error = failure,
                    fields = mapOf("coroutine" to context[CoroutineName]?.name.orEmpty()),
                )
            },
    )

    @Provides
    @Singleton
    @ForexPlatform
    fun forexSessionController(
        @ForexPlatform storage: SessionTokenStorage,
        @ForexPlatform memory: SessionMemory,
        @ForexPlatform gateway: AuthGateway,
        @ForexPlatform emailAuth: EmailAuthGateway,
        scope: CoroutineScope,
    ): SessionController = SessionController(storage, memory, gateway, scope, emailAuth)

    /**
     * The session that gates the shell.
     *
     * TradeYar, matched to the unqualified [emailAuthController]. See the note there for why the
     * account lives on that side; the pair moves together or the app locks itself out.
     */
    @Provides
    @Singleton
    fun sessionController(@CryptoPlatform controller: SessionController): SessionController =
        controller

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoSessionController(
        @CryptoPlatform storage: SessionTokenStorage,
        @CryptoPlatform memory: SessionMemory,
        @CryptoPlatform gateway: AuthGateway,
        @CryptoPlatform emailAuth: EmailAuthGateway,
        scope: CoroutineScope,
    ): SessionController = SessionController(storage, memory, gateway, scope, emailAuth)

    /**
     * Only platforms this build can actually reach are offered. A base URL left at its
     * non-routable placeholder means the environment was never configured for that platform, and
     * surfacing a sign-in that cannot complete is worse than not showing it.
     */
    @Provides
    @Singleton
    fun platformSessions(
        @ForexPlatform forex: SessionController,
        @CryptoPlatform crypto: SessionController,
        scope: CoroutineScope,
    ): PlatformSessions = PlatformSessions(
        controllers = buildMap {
            put(MarketPlatform.COINEPRO_FX, forex)
            if (isPlatformConfigured(BuildConfig.TRADEYAR_API_BASE_URL)) {
                put(MarketPlatform.TRADEYAR, crypto)
            }
        },
        scope = scope,
    )

    /**
     * One market feed per platform, each pinned to its own backend and its own symbol list.
     *
     * Not one controller with a symbol argument: the two feeds have different upstreams — LBank's
     * realtime socket for TradeYar, Finnhub for CoinePro-FX — different symbol spellings, and
     * different credentials. Sharing one controller would mean one socket carrying both, which is
     * exactly the arrangement that lets a metal appear in a crypto watchlist.
     */
    @Provides
    @Singleton
    @ForexPlatform
    fun forexMarketDataController(
        @ForexPlatform retrofit: Retrofit,
        @ForexPlatform client: OkHttpClient,
        scope: CoroutineScope,
        cache: MarketDataCache,
    ): MarketDataController = MarketDataController(
        retrofit = retrofit,
        client = client,
        scope = scope,
        platform = MarketPlatform.COINEPRO_FX,
        cache = cache,
    )

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoMarketDataController(
        @CryptoPlatform retrofit: Retrofit,
        @CryptoPlatform client: OkHttpClient,
        scope: CoroutineScope,
        cache: MarketDataCache,
    ): MarketDataController = MarketDataController(
        retrofit = retrofit,
        client = client,
        scope = scope,
        platform = MarketPlatform.TRADEYAR,
        cache = cache,
    )

    /**
     * The academy token, minted from the mobile one and held for the process's life.
     *
     * Singleton because the point of it is the cache. CoinePro-FX's chart routes sit behind a
     * separate scope, and a store per call site would mint a fresh twelve-hour token every time a
     * chart opened — which works, and is a request per chart that need not exist.
     */
    @Provides
    @Singleton
    fun academyTokenStore(@ForexPlatform retrofit: Retrofit): AcademyTokenStore =
        NetworkAcademyTokenStore(retrofit)

    /**
     * Candles, per platform, because the two routes are not the same route.
     *
     * TradeYar serves them on a plain mobile path. CoinePro-FX serves them behind the academy
     * scope, so its gateway takes the token store above. The asymmetry is in the constructors
     * rather than hidden behind a flag, which is why there are two providers here and not one.
     */
    @Provides
    @Singleton
    @ForexPlatform
    fun forexCandleGateway(
        @ForexPlatform retrofit: Retrofit,
        tokens: AcademyTokenStore,
    ): CandleGateway = CoineProFxCandleGateway(retrofit, tokens)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoCandleGateway(@CryptoPlatform retrofit: Retrofit): CandleGateway =
        TradeYarCandleGateway(retrofit)

    /**
     * Closed-trade history, per platform.
     *
     * Both are read-only and both are slow on their own terms — CoinePro-FX pages a broker ledger,
     * TradeYar walks LBank's order log in 48-hour slices — so the controller below is created once
     * and refreshes only when asked.
     */
    /**
     * The academy, which exists on one platform only.
     *
     * Nullable rather than a stub: TradeYar has no `/academy` surface at all, and a gateway wired
     * to routes that answer 404 would turn a feature that is absent into one that looks broken.
     */
    @Provides
    @Singleton
    fun academyGateway(
        @ForexPlatform retrofit: Retrofit,
        tokens: AcademyTokenStore,
    ): AcademyGateway = NetworkAcademyGateway(retrofit, tokens)

    /**
     * The web terminal.
     *
     * The address comes from the server's own capability answer, and falls back to
     * `BuildConfig.TERMINAL_URL` only where a deployment does not report one. That order is not a
     * preference — the address this app used to compile in pointed at a host that had since been
     * decommissioned, so the button would have opened a browser error and no release could have
     * known. A server always knows where it is serving from.
     *
     * With neither, the controller reports itself unconfigured and the entry is not drawn, which is
     * better than a button that opens a blank page.
     */
    @Provides
    @Singleton
    fun terminalController(
        tokens: AcademyTokenStore,
        capabilities: PlatformCapabilities,
        scope: CoroutineScope,
    ): TerminalController = TerminalController(
        baseUrl = {
            capabilities.state.value[MarketPlatform.COINEPRO_FX]?.terminalUrl
                ?: BuildConfig.TERMINAL_URL
        },
        tokens = tokens,
        scope = scope,
    )

    @Provides
    @Singleton
    fun academyController(gateway: AcademyGateway, scope: CoroutineScope): AcademyController =
        AcademyController(gateway, scope)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexPortfolioGateway(@ForexPlatform retrofit: Retrofit): PortfolioGateway =
        PortfolioGatewayFactory.create(MarketPlatform.COINEPRO_FX, retrofit)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoPortfolioGateway(@CryptoPlatform retrofit: Retrofit): PortfolioGateway =
        PortfolioGatewayFactory.create(MarketPlatform.TRADEYAR, retrofit)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexPortfolioController(
        @ForexPlatform gateway: PortfolioGateway,
        scope: CoroutineScope,
    ): PortfolioController = PortfolioController(gateway, scope)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoPortfolioController(
        @CryptoPlatform gateway: PortfolioGateway,
        scope: CoroutineScope,
    ): PortfolioController = PortfolioController(gateway, scope)

    @Provides
    @Singleton
    fun portfolioControllers(
        @ForexPlatform forex: PortfolioController,
        @CryptoPlatform crypto: PortfolioController,
    ): Map<MarketPlatform, PortfolioController> = platformMap(forex, crypto)

    @Provides
    @Singleton
    fun candleGateways(
        @ForexPlatform forex: CandleGateway,
        @CryptoPlatform crypto: CandleGateway,
    ): Map<MarketPlatform, CandleGateway> = platformMap(forex, crypto)

    /**
     * Order-book depth, per platform, where the two answers are not the same *kind* of answer.
     *
     * TradeYar relays LBank, which publishes a public book, so crypto gets a real network gateway
     * pointed at the depth route asked for in `docs/SERVER_ASKS_DOM.md`. Until that route is
     * relayed it answers 404, which [TradeYarOrderBookGateway] reads as
     * [DepthUnavailableReason.ENDPOINT_NOT_SERVED] — «هنوز سرو نمی‌شود», not «در حال دریافت».
     *
     * The default poll cadence is taken rather than tuned here: the ladder's refresh rate is a
     * property of the feed, and a number chosen in the injector is a number nobody reading the
     * gateway would think to look for.
     */
    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoOrderBookGateway(@CryptoPlatform retrofit: Retrofit): OrderBookGateway =
        TradeYarOrderBookGateway(retrofit)

    /**
     * CoinePro-FX has no order book, and that is the finished answer rather than a gap to fill.
     *
     * MetaTrader 5 exposes depth only through `MarketBookAdd`/`MarketBookGet`, and those return
     * nothing unless the broker has switched Level II publication on for the symbol; most retail
     * forex brokers have not. So this is not a placeholder awaiting a real gateway — no backend
     * work downstream can produce depth the venue never publishes. [NoDepthGateway] says so and
     * completes, and the screen renders it as one short Persian sentence naming the broker.
     *
     * The source name is the terminal's own spelling, so the sentence names something the reader
     * can go and check rather than an internal label for a refusal.
     */
    @Provides
    @Singleton
    @ForexPlatform
    fun forexOrderBookGateway(): OrderBookGateway = NoDepthGateway(
        reason = DepthUnavailableReason.FEED_PUBLISHES_NO_DEPTH,
        sourceName = "MetaTrader 5",
    )

    /**
     * Both, keyed by platform, so the shell can follow whichever one is on screen.
     *
     * Same shape as [candleGateways] deliberately: the depth ladder must switch platform with the
     * chart it was opened from, and a single gateway behind a flag is how the crypto book ends up
     * drawn under a forex heading.
     */
    @Provides
    @Singleton
    fun orderBookGateways(
        @ForexPlatform forex: OrderBookGateway,
        @CryptoPlatform crypto: OrderBookGateway,
    ): Map<MarketPlatform, OrderBookGateway> = platformMap(forex, crypto)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexMarketCatalogGateway(@ForexPlatform retrofit: Retrofit): MarketCatalogGateway =
        NetworkMarketCatalogGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoMarketCatalogGateway(@CryptoPlatform retrofit: Retrofit): MarketCatalogGateway =
        NetworkMarketCatalogGateway.create(retrofit, MarketPlatform.TRADEYAR)

    /**
     * Search is per platform because the catalogue is.
     *
     * The two backends quote different universes and spell their symbols differently, so one search
     * over both would offer a market the active session cannot open. The live quotes are handed in
     * from the same platform's feed, so a row a reader is already watching keeps ticking while the
     * rest show the catalogue's price.
     */
    @Provides
    @Singleton
    @ForexPlatform
    fun forexMarketSearchController(
        @ForexPlatform gateway: MarketCatalogGateway,
        @ForexPlatform feed: MarketDataController,
        scope: CoroutineScope,
    ): MarketSearchController = MarketSearchController(gateway, scope) { feed.state.value.quotes }

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoMarketSearchController(
        @CryptoPlatform gateway: MarketCatalogGateway,
        @CryptoPlatform feed: MarketDataController,
        scope: CoroutineScope,
    ): MarketSearchController = MarketSearchController(gateway, scope) { feed.state.value.quotes }

    @Provides
    @Singleton
    fun marketSearchControllers(
        @ForexPlatform forex: MarketSearchController,
        @CryptoPlatform crypto: MarketSearchController,
    ): Map<MarketPlatform, MarketSearchController> = platformMap(forex, crypto)

    /**
     * The screener, one per platform, for the same reason search is.
     *
     * Each takes its own platform's catalogue, its own live quotes and its own candle feed, so a
     * screen run on one backend never lists a market the active session cannot open. The saved
     * screens are deliberately *not* per platform: a filter is «RSI زیر ۳۰», which means the same
     * thing on gold as on Bitcoin, and making a reader re-enter it after switching backend would
     * be asking them to keep two copies of one idea.
     */
    @Provides
    @Singleton
    @ForexPlatform
    fun forexScreenerController(
        @ForexPlatform catalog: MarketCatalogGateway,
        @ForexPlatform quotes: MarketSnapshotGateway,
        @ForexPlatform candles: CandleGateway,
        store: ScreenerStore,
        scope: CoroutineScope,
    ): ScreenerController = ScreenerController(
        gateway = catalog,
        scope = scope,
        quotes = quotes,
        barSource = CandleScreenerBarSource(candles),
        store = store,
    )

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoScreenerController(
        @CryptoPlatform catalog: MarketCatalogGateway,
        @CryptoPlatform quotes: MarketSnapshotGateway,
        @CryptoPlatform candles: CandleGateway,
        store: ScreenerStore,
        scope: CoroutineScope,
    ): ScreenerController = ScreenerController(
        gateway = catalog,
        scope = scope,
        quotes = quotes,
        barSource = CandleScreenerBarSource(candles),
        store = store,
    )

    @Provides
    @Singleton
    fun screenerControllers(
        @ForexPlatform forex: ScreenerController,
        @CryptoPlatform crypto: ScreenerController,
    ): Map<MarketPlatform, ScreenerController> = platformMap(forex, crypto)

    /** Where a reader's own saved screens live. One file, shared by both platforms — see above. */
    @Provides
    @Singleton
    fun screenerStore(dataStore: DataStore<Preferences>): ScreenerStore = ScreenerStore(dataStore)

    /**
     * The feeds keyed by platform, so the shell can start and stop whichever one is on screen
     * without knowing how either was built.
     */
    @Provides
    @Singleton
    fun marketDataControllers(
        @ForexPlatform forex: MarketDataController,
        @CryptoPlatform crypto: MarketDataController,
    ): Map<MarketPlatform, MarketDataController> = buildMap {
        put(MarketPlatform.COINEPRO_FX, forex)
        if (isPlatformConfigured(BuildConfig.TRADEYAR_API_BASE_URL)) {
            put(MarketPlatform.TRADEYAR, crypto)
        }
    }

    @Provides
    @Singleton
    fun activePlatformStore(
        preferences: DataStore<Preferences>,
        controllers: Map<MarketPlatform, MarketDataController>,
    ): ActivePlatformStore = ActivePlatformStore(
        dataStore = preferences,
        available = MarketPlatform.entries.filter { it in controllers },
        // TradeYar where this build can reach it, because that is where the account lives — see
        // the note on `emailAuthController`. A reader who has never chosen a platform should be
        // looking at the one their sign-in belongs to.
        fallback = MarketPlatform.TRADEYAR
            .takeIf { it in controllers }
            ?: MarketPlatform.entries.first { it in controllers },
    )

    /**
     * Keys a pair of per-platform instances, offering TradeYar only where this build can reach it.
     *
     * The same filter as [platformSessions] and for the same reason: a base URL still on its
     * non-routable placeholder means the environment was never configured for that platform, and
     * offering a screen that cannot load is worse than not offering it.
     */
    private fun <T> platformMap(forex: T, crypto: T): Map<MarketPlatform, T> = buildMap {
        put(MarketPlatform.COINEPRO_FX, forex)
        if (isPlatformConfigured(BuildConfig.TRADEYAR_API_BASE_URL)) {
            put(MarketPlatform.TRADEYAR, crypto)
        }
    }

    @Provides
    @Singleton
    fun activePlatformSelector(store: ActivePlatformStore): ActivePlatformSelector = store.selector()

    // Each controller holds the last thing it read, so there is one per platform rather than one
    // with a switch: a shared instance would either drop that on every platform change or leave
    // the previous market's signals on screen under the new market's heading while a read is in
    // flight. The maps below are what let the shell follow whichever platform is on screen.

    @Provides
    @Singleton
    @ForexPlatform
    fun forexSignalController(
        @ForexPlatform gateway: SignalGateway,
        scope: CoroutineScope,
        historyCache: SignalHistoryCache,
    ): SignalController = SignalController(gateway, scope, historyCache)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoSignalController(
        @CryptoPlatform gateway: SignalGateway,
        scope: CoroutineScope,
        historyCache: SignalHistoryCache,
    ): SignalController = SignalController(gateway, scope, historyCache)

    @Provides
    @Singleton
    fun signalControllers(
        @ForexPlatform forex: SignalController,
        @CryptoPlatform crypto: SignalController,
    ): Map<MarketPlatform, SignalController> = platformMap(forex, crypto)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexNotificationController(
        @ForexPlatform gateway: NotificationGateway,
        scope: CoroutineScope,
    ): NotificationController = NotificationController(gateway, scope, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoNotificationController(
        @CryptoPlatform gateway: NotificationGateway,
        scope: CoroutineScope,
    ): NotificationController = NotificationController(gateway, scope, MarketPlatform.TRADEYAR)

    @Provides
    @Singleton
    fun notificationControllers(
        @ForexPlatform forex: NotificationController,
        @CryptoPlatform crypto: NotificationController,
    ): Map<MarketPlatform, NotificationController> = platformMap(forex, crypto)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexExecutionController(
        @ForexPlatform gateway: ExecutionGateway,
        scope: CoroutineScope,
    ): ExecutionController = ExecutionController(gateway, scope)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoExecutionController(
        @CryptoPlatform gateway: ExecutionGateway,
        scope: CoroutineScope,
    ): ExecutionController = ExecutionController(gateway, scope)

    @Provides
    @Singleton
    fun executionControllers(
        @ForexPlatform forex: ExecutionController,
        @CryptoPlatform crypto: ExecutionController,
    ): Map<MarketPlatform, ExecutionController> = platformMap(forex, crypto)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexCopyTradeController(
        @ForexPlatform gateway: CopyTradeGateway,
        scope: CoroutineScope,
    ): CopyTradeController = CopyTradeController(gateway, scope)

    // Bound for TradeYar as well, although its gateway has no paths and answers "unsupported" to
    // everything. A controller that simply did not exist for one platform would make the screen's
    // caller do the branching, and the one place that must never guess which platform it is on is
    // the wiring — that is how the crypto gateways ended up asking CoinePro-FX's addresses.
    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoCopyTradeController(
        @CryptoPlatform gateway: CopyTradeGateway,
        scope: CoroutineScope,
    ): CopyTradeController = CopyTradeController(gateway, scope)

    @Provides
    @Singleton
    fun copyTradeControllers(
        @ForexPlatform forex: CopyTradeController,
        @CryptoPlatform crypto: CopyTradeController,
    ): Map<MarketPlatform, CopyTradeController> = platformMap(forex, crypto)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexAiSignalController(
        @ForexPlatform gateway: AiSignalGateway,
        @ForexPlatform catalog: MarketCatalogGateway,
        scope: CoroutineScope,
    ): AiSignalController = AiSignalController(
        gateway = gateway,
        scope = scope,
        // The whole universe this platform serves, not the nine tickers the screen used to hold.
        // Precedence is the catalogue's own: a list the server states wins over it, and the
        // hand-written fallback is reached only when neither has answered yet.
        catalog = MarketCatalogAiSymbolCatalog(catalog),
        platform = MarketPlatform.COINEPRO_FX,
    )

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAiSignalController(
        @CryptoPlatform gateway: AiSignalGateway,
        @CryptoPlatform catalog: MarketCatalogGateway,
        scope: CoroutineScope,
    ): AiSignalController = AiSignalController(
        gateway = gateway,
        scope = scope,
        catalog = MarketCatalogAiSymbolCatalog(catalog),
        platform = MarketPlatform.TRADEYAR,
    )

    @Provides
    @Singleton
    fun aiSignalControllers(
        @ForexPlatform forex: AiSignalController,
        @CryptoPlatform crypto: AiSignalController,
    ): Map<MarketPlatform, AiSignalController> = platformMap(forex, crypto)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexAiVisionController(
        @ForexPlatform gateway: AiVisionGateway,
        scope: CoroutineScope,
    ): AiVisionController = AiVisionController(gateway, scope)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAiVisionController(
        @CryptoPlatform gateway: AiVisionGateway,
        scope: CoroutineScope,
    ): AiVisionController = AiVisionController(gateway, scope)

    @Provides
    @Singleton
    fun aiVisionControllers(
        @ForexPlatform forex: AiVisionController,
        @CryptoPlatform crypto: AiVisionController,
    ): Map<MarketPlatform, AiVisionController> = platformMap(forex, crypto)

    @Provides
    @Singleton
    fun aiAssistantController(
        gateway: AiAssistantGateway,
        scope: CoroutineScope,
    ): AiAssistantController = AiAssistantController(gateway, scope)

    @Provides
    @Singleton
    @ForexPlatform
    fun forexMarketIntelController(
        @ForexPlatform gateway: MarketIntelGateway,
        scope: CoroutineScope,
    ): MarketIntelController = MarketIntelController(gateway, scope)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoMarketIntelController(
        @CryptoPlatform gateway: MarketIntelGateway,
        scope: CoroutineScope,
    ): MarketIntelController = MarketIntelController(gateway, scope)

    /**
     * The chart's own reader of the document the news screen reads — items 118 and 119.
     *
     * One per platform for the same reason the news readers are: the gold calendar has no business
     * under a crypto chart, and a single reader behind a flag is how it ends up there.
     *
     * A singleton and not a per-screen object, because it caches by symbol: flipping between two
     * instruments and back costs no second read, which on a chart is the whole difference between
     * marks that are there when you pan and marks that flicker.
     */
    @Provides
    @Singleton
    @ForexPlatform
    fun forexChartEventController(
        @ForexPlatform gateway: MarketIntelGateway,
        scope: CoroutineScope,
    ): ChartEventController = ChartEventController(MarketIntelChartEventFeed(gateway), scope)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoChartEventController(
        @CryptoPlatform gateway: MarketIntelGateway,
        scope: CoroutineScope,
    ): ChartEventController = ChartEventController(MarketIntelChartEventFeed(gateway), scope)

    @Provides
    @Singleton
    fun chartEventControllers(
        @ForexPlatform forex: ChartEventController,
        @CryptoPlatform crypto: ChartEventController,
    ): Map<MarketPlatform, ChartEventController> = buildMap {
        put(MarketPlatform.COINEPRO_FX, forex)
        if (isPlatformConfigured(BuildConfig.TRADEYAR_API_BASE_URL)) {
            put(MarketPlatform.TRADEYAR, crypto)
        }
    }

    /**
     * The news readers keyed by platform, so the screen shows the market it is named after.
     *
     * Two controllers rather than one with a switch: each holds the last snapshot it read, and a
     * shared one would either drop that on every platform change or — worse — leave yesterday's
     * gold headlines on screen under a crypto heading while the new read is in flight.
     */
    @Provides
    @Singleton
    fun marketIntelControllers(
        @ForexPlatform forex: MarketIntelController,
        @CryptoPlatform crypto: MarketIntelController,
    ): Map<MarketPlatform, MarketIntelController> = buildMap {
        put(MarketPlatform.COINEPRO_FX, forex)
        if (isPlatformConfigured(BuildConfig.TRADEYAR_API_BASE_URL)) {
            put(MarketPlatform.TRADEYAR, crypto)
        }
    }

    /**
     * A base URL still on its `.invalid` placeholder was never supplied for this build.
     * `NetworkFactory` would accept it and every call would simply fail DNS.
     */
    private fun isPlatformConfigured(baseUrl: String): Boolean =
        baseUrl.isNotBlank() && !baseUrl.contains(".invalid")
}

/**
 * The tickers the alert editor's symbol picker may offer, as a value that can be read synchronously.
 *
 * ### Why this exists rather than the search controller's own catalogue
 *
 * `MarketSearchController` loads the same lists and keeps them to itself, and it keeps one platform's
 * — it is the markets screen's search, and that screen belongs to the platform named above it. An
 * alert does not: it is evaluated over the public guest route, so a reader may perfectly well hold an
 * alert on gold and one on Bitcoin at the same time, and a picker that offered only the platform they
 * happen to be signed into would hide half of their own alerts' instruments from them.
 *
 * ### When it loads, and what the picker shows before then
 *
 * On the first read, not at startup: two catalogue requests on every launch to fill a picker most
 * readers never open is network nobody asked for. Until the load lands the answer is whatever the
 * market cache already holds from the last time the app read quotes, which on any launch after the
 * first is the whole universe and costs one query against a local table. A first-ever launch with the
 * editor opened immediately shows an empty browse list for as long as one request takes.
 *
 * The same list instance is returned until a load replaces it, deliberately: `AlertsController` memos
 * its classification on the list's identity, and a fresh copy per call would re-classify a few
 * thousand tickers on every keystroke.
 */
private class AlertSymbolCatalogue(
    private val gateways: List<MarketCatalogGateway>,
    private val cache: MarketDataCache,
    private val scope: CoroutineScope,
) {

    @Volatile
    private var symbols: List<String> = emptyList()

    private var loadJob: Job? = null

    /** Every ticker known so far. Empty only before anything has been read at all. */
    fun symbols(): List<String> {
        val running = loadJob
        // A read while a load is in flight must not start a second one, and a read after a load
        // that came back with nothing must start another: the first attempt failing is the case
        // where the reader is most likely to open the picker again in a moment.
        if (running == null || (!running.isActive && symbols.isEmpty())) {
            loadJob = scope.launch { load() }
        }
        return symbols
    }

    private suspend fun load() {
        if (symbols.isEmpty()) {
            val cached = runCatching { cache.read() }.getOrNull()
            val fromCache = cached?.quotes?.map { it.instrument.symbol }.orEmpty()
            if (fromCache.isNotEmpty()) symbols = fromCache.distinct().sorted()
        }
        // Each gateway on its own: one backend being unreachable is no reason to offer none of the
        // other's markets, and a reader with no session at all still has both catalogues, which are
        // public reads.
        val loaded = gateways.flatMap { gateway ->
            runCatching { gateway.load().markets.map(SymbolMeta::symbol) }.getOrDefault(emptyList())
        }
        if (loaded.isNotEmpty()) symbols = loaded.distinct().sorted()
    }
}

/**
 * The bar each symbol was last charted on, readable without suspending.
 *
 * `SymbolChartStateStore` answers in a `Flow`, and the alerts list needs the answer while it is
 * building a row — during composition, for every alert on screen. Following the store once and
 * keeping the last emission is what turns that into a lookup; it also means a reader who changes the
 * timeframe on a chart and returns to the alert centre sees the new label, which a value read once at
 * construction would not give them.
 *
 * A symbol with no entry answers null, and the row omits the label. That is the honest answer: an
 * alert made from a market row was never given a timeframe, and the evaluator has its own documented
 * default for that case rather than one this class should guess at.
 */
private class SymbolTimeframes(store: SymbolChartStateStore, scope: CoroutineScope) {

    @Volatile
    private var timeframes: Map<String, String> = emptyMap()

    init {
        scope.launch {
            store.all().collect { states ->
                timeframes = states.mapNotNull { state ->
                    state.timeframe?.takeIf(String::isNotBlank)?.let { state.symbol to it }
                }.toMap()
            }
        }
    }

    /** The bar this symbol was left on, or null where the reader has never charted it. */
    fun of(symbol: String): String? = timeframes[symbol.uppercase()]
}

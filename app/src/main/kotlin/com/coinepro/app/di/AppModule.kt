package com.coinepro.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.coinepro.app.BuildConfig
import com.coinepro.core.account.AccountController
import com.coinepro.core.account.AccountGateway
import com.coinepro.core.account.NetworkAccountGateway
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.UserPreferencesStore
import com.coinepro.core.datastore.WidgetSnapshotStore
import com.coinepro.core.network.NetworkStatus
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.ProfileStore
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.guest.GuestController
import com.coinepro.core.journal.JournalController
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.script.ScriptController
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.NetworkGuestGateway
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aiassistant.AiAssistantGateway
import com.coinepro.core.aiassistant.NetworkAiAssistantGateway
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aisignal.AiSignalGateway
import com.coinepro.core.aisignal.NetworkAiSignalGateway
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.aivision.AiVisionGateway
import com.coinepro.core.aivision.NetworkAiVisionGateway
import com.coinepro.core.auth.AuthGateway
import com.coinepro.core.auth.EmailAuthController
import com.coinepro.app.auth.RegistrationStore
import com.coinepro.core.auth.FederatedEmailAuthGateway
import com.coinepro.core.auth.EmailAuthGateway
import com.coinepro.core.auth.NetworkEmailAuthGateway
import com.coinepro.core.auth.NetworkAuthGateway
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionMemory
import com.coinepro.core.auth.PlatformCapabilities
import com.coinepro.core.auth.PlatformSessions
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.database.CoineProDatabase
import com.coinepro.core.database.CoineProDatabaseFactory
import com.coinepro.core.database.RoomMarketDataCache
import com.coinepro.core.database.RoomSignalHistoryCache
import com.coinepro.core.datastore.ActivePlatformSelector
import com.coinepro.core.datastore.ActivePlatformStore
import com.coinepro.core.datastore.InstallIdStore
import com.coinepro.core.diagnostics.AdminBuildInfo
import com.coinepro.core.diagnostics.AdminController
import com.coinepro.core.diagnostics.EndpointProber
import com.coinepro.core.diagnostics.PlatformBuildInfo
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.RequestLog
import com.coinepro.core.diagnostics.RequestLogInterceptor
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.copytrade.CopyTradeGateway
import com.coinepro.core.copytrade.NetworkCopyTradeGateway
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.ExecutionGateway
import com.coinepro.core.execution.NetworkExecutionGateway
import com.coinepro.core.marketdata.AcademyTokenStore
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CoineProFxCandleGateway
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.marketdata.NetworkAcademyTokenStore
import com.coinepro.core.marketdata.TradeYarCandleGateway
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.NetworkMarketCatalogGateway
import com.coinepro.core.marketdata.MarketSnapshotGateway
import com.coinepro.core.marketdata.NetworkMarketSnapshotGateway
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketIntelGateway
import com.coinepro.core.marketintel.NetworkMarketIntelGateway
import com.coinepro.core.membership.MembershipController
import com.coinepro.core.membership.MembershipGateway
import com.coinepro.core.membership.NetworkMembershipGateway
import com.coinepro.core.academy.AcademyController
import com.coinepro.feature.terminal.TerminalController
import com.coinepro.core.academy.AcademyGateway
import com.coinepro.core.academy.NetworkAcademyGateway
import com.coinepro.core.network.NetworkFactory
import com.coinepro.core.portfolio.PortfolioController
import com.coinepro.core.portfolio.PortfolioGateway
import com.coinepro.core.portfolio.PortfolioGatewayFactory
import com.coinepro.core.notifications.NetworkNotificationGateway
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.NotificationGateway
import com.coinepro.core.security.KeystoreSessionTokenStorage
import com.coinepro.core.signals.NetworkSignalGateway
import com.coinepro.core.signals.SignalController
import com.coinepro.core.signals.SignalGateway
import com.coinepro.core.signals.SignalHistoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    fun paperTradeController(database: CoineProDatabase, scope: CoroutineScope): PaperTradeController =
        PaperTradeController(database.paperTradeDao(), scope)

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

    @Provides
    @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        scope: CoroutineScope,
    ): AiSignalController = AiSignalController(gateway, scope)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAiSignalController(
        @CryptoPlatform gateway: AiSignalGateway,
        scope: CoroutineScope,
    ): AiSignalController = AiSignalController(gateway, scope)

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

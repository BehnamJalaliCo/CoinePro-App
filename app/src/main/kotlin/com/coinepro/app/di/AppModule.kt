package com.coinepro.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.coinepro.app.BuildConfig
import com.coinepro.core.account.AccountController
import com.coinepro.core.account.AccountGateway
import com.coinepro.core.account.NetworkAccountGateway
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
import com.coinepro.core.auth.EmailAuthGateway
import com.coinepro.core.auth.NetworkEmailAuthGateway
import com.coinepro.core.auth.NetworkAuthGateway
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionMemory
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
import com.coinepro.core.diagnostics.RequestLog
import com.coinepro.core.diagnostics.RequestLogInterceptor
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.ExecutionGateway
import com.coinepro.core.execution.NetworkExecutionGateway
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketSnapshotGateway
import com.coinepro.core.marketdata.NetworkMarketSnapshotGateway
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketIntelGateway
import com.coinepro.core.marketintel.NetworkMarketIntelGateway
import com.coinepro.core.network.NetworkFactory
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
    ): OkHttpClient = NetworkFactory.okHttpClient(
        bearerToken = memory::token,
        onUnauthorized = memory::notifyUnauthorized,
        installId = installIds.providerFor(MarketPlatform.COINEPRO_FX),
        appVersion = BuildConfig.VERSION_NAME,
        recorder = RequestLogInterceptor(requestLog, MarketPlatform.COINEPRO_FX),
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
    ): OkHttpClient = NetworkFactory.okHttpClient(
        bearerToken = memory::token,
        onUnauthorized = memory::notifyUnauthorized,
        installId = installIds.providerFor(MarketPlatform.TRADEYAR),
        appVersion = BuildConfig.VERSION_NAME,
        recorder = RequestLogInterceptor(requestLog, MarketPlatform.TRADEYAR),
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
        NetworkAuthGateway.create(retrofit)

    @Provides
    @Singleton
    fun authGateway(@ForexPlatform gateway: AuthGateway): AuthGateway = gateway

    @Provides
    @Singleton
    @ForexPlatform
    fun forexAccountGateway(@ForexPlatform retrofit: Retrofit): AccountGateway =
        NetworkAccountGateway.create(retrofit)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAccountGateway(@CryptoPlatform retrofit: Retrofit): AccountGateway =
        NetworkAccountGateway.create(retrofit)

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
        NetworkEmailAuthGateway.create(retrofit)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoEmailAuthGateway(@CryptoPlatform retrofit: Retrofit): EmailAuthGateway =
        NetworkEmailAuthGateway.create(retrofit)

    @Provides
    @Singleton
    @CryptoPlatform
    fun cryptoAuthGateway(@CryptoPlatform retrofit: Retrofit): AuthGateway =
        NetworkAuthGateway.create(retrofit)

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
     * The sign-in screen's controller.
     *
     * CoinePro-FX, matching the unqualified [SessionController]: that session is what gates the
     * shell, so the screen that opens it must be the one that fills it. Signing in to TradeYar
     * instead would leave the reader looking at a completed sign-in and a locked app.
     */
    @Provides
    @Singleton
    fun emailAuthController(@ForexPlatform controller: EmailAuthController): EmailAuthController =
        controller

    @Provides
    @Singleton
    fun signalGateway(retrofit: Retrofit): SignalGateway = NetworkSignalGateway.create(retrofit)

    @Provides
    @Singleton
    fun marketSnapshotGateway(retrofit: Retrofit): MarketSnapshotGateway =
        NetworkMarketSnapshotGateway.create(retrofit)

    @Provides
    @Singleton
    // Still the unqualified (CoinePro-FX) client, so the platform is named to match rather than
    // left to a default. When the crypto notification surface is wired, this becomes a pair of
    // qualified bindings like the account and auth gateways above it.
    fun notificationGateway(retrofit: Retrofit): NotificationGateway =
        NetworkNotificationGateway.create(retrofit, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    fun executionGateway(retrofit: Retrofit): ExecutionGateway =
        NetworkExecutionGateway.create(retrofit)

    @Provides
    @Singleton
    fun aiSignalGateway(retrofit: Retrofit): AiSignalGateway =
        NetworkAiSignalGateway.create(retrofit)

    @Provides
    @Singleton
    fun aiVisionGateway(retrofit: Retrofit): AiVisionGateway =
        NetworkAiVisionGateway.create(retrofit)

    @Provides
    @Singleton
    fun aiAssistantGateway(retrofit: Retrofit): AiAssistantGateway =
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

    @Provides
    @Singleton
    fun sessionController(@ForexPlatform controller: SessionController): SessionController = controller

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
    )

    @Provides
    @Singleton
    fun activePlatformSelector(store: ActivePlatformStore): ActivePlatformSelector = store.selector()

    @Provides
    @Singleton
    fun signalController(
        gateway: SignalGateway,
        scope: CoroutineScope,
        historyCache: SignalHistoryCache,
    ): SignalController = SignalController(gateway, scope, historyCache)

    @Provides
    @Singleton
    fun notificationController(
        gateway: NotificationGateway,
        scope: CoroutineScope,
    ): NotificationController = NotificationController(gateway, scope, MarketPlatform.COINEPRO_FX)

    @Provides
    @Singleton
    fun executionController(
        gateway: ExecutionGateway,
        scope: CoroutineScope,
    ): ExecutionController = ExecutionController(gateway, scope)

    @Provides
    @Singleton
    fun aiSignalController(
        gateway: AiSignalGateway,
        scope: CoroutineScope,
    ): AiSignalController = AiSignalController(gateway, scope)

    @Provides
    @Singleton
    fun aiVisionController(
        gateway: AiVisionGateway,
        scope: CoroutineScope,
    ): AiVisionController = AiVisionController(gateway, scope)

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

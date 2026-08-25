package com.coinepro.app.di

import android.content.Context
import com.coinepro.app.BuildConfig
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
import com.coinepro.core.auth.NetworkAuthGateway
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionMemory
import com.coinepro.core.auth.PlatformSessions
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.database.CoineProDatabase
import com.coinepro.core.database.CoineProDatabaseFactory
import com.coinepro.core.database.RoomMarketDataCache
import com.coinepro.core.database.RoomSignalHistoryCache
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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
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
    fun forexOkHttp(@ForexPlatform memory: SessionMemory): OkHttpClient = NetworkFactory.okHttpClient(
        bearerToken = memory::token,
        onUnauthorized = memory::notifyUnauthorized,
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
    fun cryptoOkHttp(@CryptoPlatform memory: SessionMemory): OkHttpClient = NetworkFactory.okHttpClient(
        bearerToken = memory::token,
        onUnauthorized = memory::notifyUnauthorized,
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
    @CryptoPlatform
    fun cryptoAuthGateway(@CryptoPlatform retrofit: Retrofit): AuthGateway =
        NetworkAuthGateway.create(retrofit)

    @Provides
    @Singleton
    fun signalGateway(retrofit: Retrofit): SignalGateway = NetworkSignalGateway.create(retrofit)

    @Provides
    @Singleton
    fun marketSnapshotGateway(retrofit: Retrofit): MarketSnapshotGateway =
        NetworkMarketSnapshotGateway.create(retrofit)

    @Provides
    @Singleton
    fun notificationGateway(retrofit: Retrofit): NotificationGateway =
        NetworkNotificationGateway.create(retrofit)

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
    fun marketIntelGateway(retrofit: Retrofit): MarketIntelGateway =
        NetworkMarketIntelGateway.create(retrofit)

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
        scope: CoroutineScope,
    ): SessionController = SessionController(storage, memory, gateway, scope)

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
        scope: CoroutineScope,
    ): SessionController = SessionController(storage, memory, gateway, scope)

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

    @Provides
    @Singleton
    fun marketDataController(
        retrofit: Retrofit,
        client: OkHttpClient,
        scope: CoroutineScope,
        cache: MarketDataCache,
    ): MarketDataController = MarketDataController(
        retrofit = retrofit,
        client = client,
        scope = scope,
        cache = cache,
    )

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
    ): NotificationController = NotificationController(gateway, scope)

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
    fun marketIntelController(
        gateway: MarketIntelGateway,
        scope: CoroutineScope,
    ): MarketIntelController = MarketIntelController(gateway, scope)

    /**
     * A base URL still on its `.invalid` placeholder was never supplied for this build.
     * `NetworkFactory` would accept it and every call would simply fail DNS.
     */
    private fun isPlatformConfigured(baseUrl: String): Boolean =
        baseUrl.isNotBlank() && !baseUrl.contains(".invalid")
}

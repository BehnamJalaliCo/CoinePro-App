package com.coinepro.app.di

import android.content.Context
import com.coinepro.app.BuildConfig
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
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.ExecutionGateway
import com.coinepro.core.execution.NetworkExecutionGateway
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.network.NetworkFactory
import com.coinepro.core.notifications.NetworkNotificationGateway
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.NotificationGateway
import com.coinepro.core.security.KeystoreSessionTokenStorage
import com.coinepro.core.signals.NetworkSignalGateway
import com.coinepro.core.signals.SignalController
import com.coinepro.core.signals.SignalGateway
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
    @Provides
    @Singleton
    fun sessionMemory(): SessionMemory = SessionMemory()

    @Provides
    @Singleton
    fun tokenStorage(@ApplicationContext context: Context): SessionTokenStorage =
        KeystoreSessionTokenStorage(context)

    @Provides
    @Singleton
    fun okHttp(memory: SessionMemory): OkHttpClient = NetworkFactory.okHttpClient(
        bearerToken = memory::token,
        onUnauthorized = memory::notifyUnauthorized,
    )

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient): Retrofit =
        NetworkFactory.retrofit(BuildConfig.API_BASE_URL, client)

    @Provides
    @Singleton
    fun authGateway(retrofit: Retrofit): AuthGateway = NetworkAuthGateway.create(retrofit)

    @Provides
    @Singleton
    fun signalGateway(retrofit: Retrofit): SignalGateway = NetworkSignalGateway.create(retrofit)

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
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun sessionController(
        storage: SessionTokenStorage,
        memory: SessionMemory,
        gateway: AuthGateway,
        scope: CoroutineScope,
    ): SessionController = SessionController(storage, memory, gateway, scope)

    @Provides
    @Singleton
    fun marketDataController(
        retrofit: Retrofit,
        client: OkHttpClient,
        scope: CoroutineScope,
    ): MarketDataController = MarketDataController(retrofit, client, scope)

    @Provides
    @Singleton
    fun signalController(
        gateway: SignalGateway,
        scope: CoroutineScope,
    ): SignalController = SignalController(gateway, scope)

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
}

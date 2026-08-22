package com.coinepro.app.di

import android.content.Context
import com.coinepro.app.BuildConfig
import com.coinepro.core.auth.AuthGateway
import com.coinepro.core.auth.NetworkAuthGateway
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionMemory
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.network.NetworkFactory
import com.coinepro.core.security.KeystoreSessionTokenStorage
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
}

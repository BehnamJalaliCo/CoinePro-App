package com.coinepro.app.di

import javax.inject.Qualifier

/**
 * CoinePro-FX and TradeYar each need their own OkHttp client, Retrofit and session, because each
 * carries a different bearer token for a different account. Without qualifiers Hilt would supply
 * one shared stack and the wrong credential would reach the wrong host.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForexPlatform

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CryptoPlatform

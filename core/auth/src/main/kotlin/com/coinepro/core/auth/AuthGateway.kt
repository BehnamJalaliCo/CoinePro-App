package com.coinepro.core.auth

import com.coinepro.core.common.AppResult

interface AuthGateway {
    suspend fun authConfig(): AppResult<AuthConfig>
    suspend fun loginTelegram(payload: TelegramAuthPayload): AppResult<AuthSession>
    suspend fun me(): AppResult<UserProfile>
}

/**
 * Where one platform's credentials live between launches.
 *
 * Two tokens rather than one, because the email flow issues two with very different lifetimes: an
 * access token measured in minutes and a refresh token measured in weeks. Keeping only the first
 * would sign the reader out roughly every hour with no explanation they could act on — which looks
 * exactly like the app losing their account. Telegram sign-in issues no refresh token at all, so
 * [readRefreshToken] simply answers null there and the session behaves as it always has.
 */
interface SessionTokenStorage {
    suspend fun readToken(): String?
    suspend fun writeToken(token: String)
    suspend fun readRefreshToken(): String?
    suspend fun writeRefreshToken(token: String)
    suspend fun clear()
}

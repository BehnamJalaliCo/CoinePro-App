package com.coinepro.core.auth

import com.coinepro.core.common.AppResult

interface AuthGateway {
    suspend fun authConfig(): AppResult<AuthConfig>
    suspend fun loginTelegram(payload: TelegramAuthPayload): AppResult<AuthSession>
    suspend fun me(): AppResult<UserProfile>
}

interface SessionTokenStorage {
    suspend fun readToken(): String?
    suspend fun writeToken(token: String)
    suspend fun clear()
}

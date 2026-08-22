package com.coinepro.core.auth

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

internal data class AuthConfigDto(val botUsername: String)
internal data class LoginResponseDto(val token: String, val profile: UserProfile)

internal interface AuthApi {
    @GET("user/auth/config")
    suspend fun authConfig(): AuthConfigDto

    @POST("user/auth/telegram")
    suspend fun loginTelegram(@Body payload: TelegramAuthPayload): LoginResponseDto

    @GET("user/me")
    suspend fun me(): UserProfile
}

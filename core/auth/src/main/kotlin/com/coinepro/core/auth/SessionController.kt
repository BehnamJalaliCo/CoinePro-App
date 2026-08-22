package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(
        val profile: UserProfile,
        val entitlement: EntitlementSnapshot,
    ) : SessionState
    data class RevalidationRequired(val message: String) : SessionState
}

class SessionController(
    private val storage: SessionTokenStorage,
    private val memory: SessionMemory,
    private val gateway: AuthGateway,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val stateMutable = MutableStateFlow<SessionState>(SessionState.Loading)
    private val botUsernameMutable = MutableStateFlow<String?>(null)

    val state: StateFlow<SessionState> = stateMutable.asStateFlow()
    val botUsername: StateFlow<String?> = botUsernameMutable.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            memory.unauthorized.collect { expireSession() }
        }
        scope.launch { restore() }
    }

    suspend fun restore() {
        stateMutable.value = SessionState.Loading
        val token = storage.readToken()
        if (token.isNullOrBlank()) {
            memory.setToken(null)
            stateMutable.value = SessionState.SignedOut
            prepareLogin()
            return
        }

        memory.setToken(token)
        when (val result = gateway.me()) {
            is AppResult.Success -> stateMutable.value = result.value.asSignedIn()
            is AppResult.Failure -> {
                if (result.kind == ErrorKind.AUTH) {
                    expireSession()
                } else {
                    stateMutable.value = SessionState.RevalidationRequired(
                        "Session exists but could not be revalidated. Protected features stay locked until the server is reachable.",
                    )
                }
            }
        }
    }

    suspend fun prepareLogin() {
        if (botUsernameMutable.value != null) return
        when (val result = gateway.authConfig()) {
            is AppResult.Success -> botUsernameMutable.value = result.value.botUsername
            is AppResult.Failure -> Unit
        }
    }

    suspend fun completeTelegramLogin(payload: TelegramAuthPayload) {
        stateMutable.value = SessionState.Loading
        when (val result = gateway.loginTelegram(payload)) {
            is AppResult.Success -> {
                storage.writeToken(result.value.token)
                memory.setToken(result.value.token)
                stateMutable.value = result.value.profile.asSignedIn()
            }
            is AppResult.Failure -> {
                memory.setToken(null)
                stateMutable.value = SessionState.SignedOut
            }
        }
    }

    suspend fun logout() {
        storage.clear()
        memory.setToken(null)
        stateMutable.value = SessionState.SignedOut
        prepareLogin()
    }

    suspend fun expireSession() {
        storage.clear()
        memory.setToken(null)
        stateMutable.value = SessionState.SignedOut
    }

    private fun UserProfile.asSignedIn() = SessionState.SignedIn(
        profile = this,
        entitlement = toEntitlementSnapshot(),
    )
}

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

sealed interface LoginConfigState {
    data object Loading : LoginConfigState
    data class Ready(val botUsername: String) : LoginConfigState
    data class Error(val message: String) : LoginConfigState
}

class SessionController(
    private val storage: SessionTokenStorage,
    private val memory: SessionMemory,
    private val gateway: AuthGateway,
    private val scope: CoroutineScope,
    /**
     * How an expired access token is exchanged for a fresh one.
     *
     * Null means this session has no way to renew itself and a 401 ends it — which is the truth for
     * a Telegram sign-in, since that flow issues no refresh token at all.
     */
    private val emailAuth: EmailAuthGateway? = null,
) {
    private val started = AtomicBoolean(false)
    private val stateMutable = MutableStateFlow<SessionState>(SessionState.Loading)
    private val loginConfigStateMutable = MutableStateFlow<LoginConfigState>(LoginConfigState.Loading)

    val state: StateFlow<SessionState> = stateMutable.asStateFlow()
    val loginConfigState: StateFlow<LoginConfigState> = loginConfigStateMutable.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            memory.unauthorized.collect { renewOrExpire() }
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
        if (loginConfigStateMutable.value is LoginConfigState.Ready) return
        loginConfigStateMutable.value = LoginConfigState.Loading
        when (val result = gateway.authConfig()) {
            is AppResult.Success -> {
                val botUsername = result.value.botUsername.trim()
                loginConfigStateMutable.value = if (botUsername.isNotEmpty()) {
                    LoginConfigState.Ready(botUsername)
                } else {
                    LoginConfigState.Error("Telegram sign-in is not configured by the server.")
                }
            }
            is AppResult.Failure -> {
                loginConfigStateMutable.value = LoginConfigState.Error(
                    "Could not load Telegram sign-in configuration. Check the server connection and retry.",
                )
            }
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

    /**
     * Takes over a session the email flow already obtained.
     *
     * The credential work happened in [EmailAuthController]; this is the single place that decides
     * the app is signed in, so that a second screen never gets to hold a second opinion about it.
     * Both tokens are written before the state changes: a screen that reacts to [SignedIn] by
     * making a request must not find storage half-populated.
     */
    suspend fun adoptSession(session: EmailAuthSession) {
        storage.writeToken(session.tokens.accessToken)
        storage.writeRefreshToken(session.tokens.refreshToken)
        memory.setToken(session.tokens.accessToken)
        stateMutable.value = session.profile.asSignedIn()
    }

    /**
     * Answers a 401 by renewing rather than by signing out, when renewal is possible.
     *
     * An access token that has simply aged out is the ordinary case, not a failure, and ending the
     * session for it would log the reader out roughly as often as the token expires — which reads
     * as the app losing their account. Only a refusal to renew is treated as the session being
     * genuinely over.
     *
     * A burst of parallel requests produces a burst of 401s, and these do not race: the unauthorized
     * signal is collected one at a time, so a second pass reads whatever the first pass stored
     * rather than re-spending a refresh token that has already been rotated.
     */
    private suspend fun renewOrExpire() {
        val gateway = emailAuth ?: return expireSession()
        val refreshToken = storage.readRefreshToken()
        if (refreshToken.isNullOrBlank()) return expireSession()

        when (val result = gateway.refresh(refreshToken)) {
            is AppResult.Success -> {
                storage.writeToken(result.value.accessToken)
                storage.writeRefreshToken(result.value.refreshToken)
                memory.setToken(result.value.accessToken)
                // The profile is not re-fetched. Renewal changes which token is current and nothing
                // about who the reader is, and a request here would put a network round trip
                // between them and a screen that was already correct.
            }
            is AppResult.Failure -> {
                // Anything other than a refusal leaves the session alone. A refresh that never
                // reached the server proves nothing about whether the session is still valid, and
                // signing out on a dropped connection would end sessions the server still honours.
                if (result.kind == ErrorKind.AUTH) expireSession()
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
        prepareLogin()
    }

    private fun UserProfile.asSignedIn() = SessionState.SignedIn(
        profile = this,
        entitlement = toEntitlementSnapshot(),
    )
}

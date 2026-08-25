package com.coinepro.core.auth

import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The user's standing with every platform at once.
 *
 * CoinePro-FX and TradeYar are separate systems with separate accounts, so "signed in" is not a
 * single boolean: a person can hold one, both, or neither. Screens that ask "is the user signed
 * in?" without saying *where* get the wrong answer for the platform they are about to call, which
 * is why [signedIn] is a set rather than a flag.
 *
 * Only platforms the build was configured for appear here — a build with no TradeYar base URL must
 * not offer a TradeYar sign-in it cannot complete.
 */
class PlatformSessions(
    private val controllers: Map<MarketPlatform, SessionController>,
    scope: CoroutineScope,
) {
    init {
        require(controllers.isNotEmpty()) { "At least one platform must be configured." }
    }

    /** Configured platforms, in declaration order so the UI is deterministic. */
    val platforms: List<MarketPlatform> = MarketPlatform.entries.filter { it in controllers }

    /** Session state per configured platform. Always contains an entry for every platform. */
    val states: StateFlow<Map<MarketPlatform, SessionState>> =
        combine(platforms.map { platform -> controllers.getValue(platform).state.map { platform to it } }) {
            it.toMap()
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = platforms.associateWith { SessionState.Loading },
        )

    /** The platforms the user currently holds a valid session on. */
    val signedIn: StateFlow<Set<MarketPlatform>> = states
        .map { snapshot ->
            snapshot.filterValues { it is SessionState.SignedIn }.keys
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet(),
        )

    fun controller(platform: MarketPlatform): SessionController =
        controllers[platform] ?: error("${platform.id} is not configured in this build.")

    fun controllerOrNull(platform: MarketPlatform): SessionController? = controllers[platform]

    fun isConfigured(platform: MarketPlatform): Boolean = platform in controllers

    /** Begins restore on every configured platform. Safe to call more than once. */
    fun start() {
        controllers.values.forEach(SessionController::start)
    }

    /** Signs out of one platform, leaving any other session untouched. */
    suspend fun logout(platform: MarketPlatform) {
        controller(platform).logout()
    }

    suspend fun logoutAll() {
        controllers.values.forEach { it.logout() }
    }
}

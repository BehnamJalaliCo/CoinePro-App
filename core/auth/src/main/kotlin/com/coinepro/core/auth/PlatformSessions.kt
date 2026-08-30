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

/**
 * The session the shell gates on, given the platform currently on screen.
 *
 * ### The bug this function is
 *
 * «فارکس» and «کریپتو» are a **market** switch in the reader's hands and were an **account** switch
 * in the code: the shell read `states[activePlatform]`, so tapping a platform the reader held no
 * token for put the whole app into its signed-out branch. That is not a corner case, it is the only
 * case — registration deliberately does not federate (see [FederatedEmailAuthGateway]), so a sign-in
 * mints exactly one session and *every* reader is signed out of exactly one of the two platforms.
 * Tapping the other tab therefore dropped a perfectly good session to the guest shell, which draws
 * no platform switcher, so the switch was one-way and read as being logged out.
 *
 * ### The rule
 *
 * A session belongs to the *reader*, not to the tab they are looking at. So the platform on screen
 * is asked first — somebody signed in to both must see the account that matches the market above it
 * — and where that platform has no session the reader's other one answers instead. Switching market
 * can then change what is on screen and can never change whether there is anybody there.
 *
 * A platform with no entry yet reads as [SessionState.Loading] rather than signed out: the map is
 * empty for the first frame of a cold start, and answering "signed out" there would flash the guest
 * shell at every reader on every launch.
 *
 * What this deliberately does **not** do is lend one platform's token to the other. Each backend
 * still holds its own credential and refuses what it does not know; a screen on a platform this
 * reader has no account with reports that for itself, which is a gap the reader can see and act on
 * rather than a sign-out they cannot explain.
 */
fun Map<MarketPlatform, SessionState>.sessionForShell(active: MarketPlatform): SessionState {
    val here = this[active] ?: SessionState.Loading
    if (here is SessionState.SignedIn) return here
    // Declaration order rather than map order, so a reader holding both always resolves to the same
    // one and the shell does not depend on how the map happened to be built.
    return MarketPlatform.entries.firstNotNullOfOrNull { this[it] as? SessionState.SignedIn } ?: here
}

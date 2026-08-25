package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform

/**
 * Every route the app calls, listed per platform.
 *
 * This exists because of a failure that happened twice. The app called
 * `user/signals/mobile/alerts` and `user/ai/vision/jobs` for a long time; neither address existed,
 * and neither was noticed, because a wrong path arrives as an HTTP error indistinguishable from a
 * server having a bad day — inside one feature, worded as a status line. Both were found only when
 * a backend published a route table and someone compared the two lists by hand.
 *
 * A catalogue makes that comparison a button instead of an afternoon. It is also the honest answer
 * to "what does this app actually talk to", which nothing else in the codebase could answer without
 * a grep across fifteen modules.
 *
 * It has to be maintained by hand, and that is the cost. A route added to a gateway and not added
 * here is a route the prober cannot check — so the rule is that the two change together.
 */
data class CatalogedEndpoint(
    val method: String,
    /** Relative to the platform's base URL, with any path parameter left as a literal sample. */
    val path: String,
    /** The feature a reader would go looking in when this one is broken. */
    val area: String,
    val requiresAuth: Boolean = true,
    /**
     * Whether probing it is safe to do unprompted.
     *
     * False for anything that writes, costs quota, or spends one of a small number of attempts.
     * The prober still lists those, so a reader sees the whole surface — it just refuses to fire
     * them, because a diagnostic that creates a price alert or burns an AI credit is not a
     * diagnostic.
     */
    val safeToProbe: Boolean = true,
)

object EndpointCatalog {

    fun forPlatform(platform: MarketPlatform): List<CatalogedEndpoint> = when (platform) {
        MarketPlatform.COINEPRO_FX -> coineProFx
        MarketPlatform.TRADEYAR -> tradeYar
    }

    /**
     * CoinePro-FX, as its published contract documents it.
     *
     * The paths below were verified against that document; the ones marked unverified are the ones
     * the contract never mentioned, which is exactly where the two dead endpoints were found.
     */
    private val coineProFx: List<CatalogedEndpoint> = listOf(
        CatalogedEndpoint("GET", "user/auth/methods", AREA_AUTH, requiresAuth = false),
        CatalogedEndpoint("GET", "user/auth/config", AREA_AUTH, requiresAuth = false),
        CatalogedEndpoint("GET", "user/me", AREA_AUTH),
        CatalogedEndpoint("POST", "user/auth/login", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "user/auth/register/start", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "user/auth/register/verify", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "user/auth/password/forgot", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "user/auth/password/reset", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "user/auth/refresh", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "user/auth/logout", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "user/auth/google", AREA_AUTH, requiresAuth = false, safeToProbe = false),

        CatalogedEndpoint("GET", "user/mobile/kyc", AREA_ACCOUNT),
        CatalogedEndpoint("POST", "user/mobile/kyc/level1", AREA_ACCOUNT, safeToProbe = false),
        CatalogedEndpoint("GET", "user/mobile/briefing", AREA_HOME),
        CatalogedEndpoint("GET", "user/mobile/portfolio", AREA_HOME),

        CatalogedEndpoint("GET", "user/mobile/notifications", AREA_NOTIFICATIONS),
        CatalogedEndpoint("POST", "user/mobile/notifications/read", AREA_NOTIFICATIONS, safeToProbe = false),
        CatalogedEndpoint("GET", "user/mobile/push/preferences", AREA_NOTIFICATIONS),
        CatalogedEndpoint("POST", "user/mobile/push/devices", AREA_NOTIFICATIONS, safeToProbe = false),
        CatalogedEndpoint("GET", "user/mobile/alerts", AREA_ALERTS),
        CatalogedEndpoint("POST", "user/mobile/alerts", AREA_ALERTS, safeToProbe = false),

        CatalogedEndpoint("GET", "ws/snapshot", AREA_MARKET, requiresAuth = false),

        CatalogedEndpoint("GET", "user/ai-signal/quota", AREA_AI),
        CatalogedEndpoint("POST", "user/ai-signal/generate", AREA_AI, safeToProbe = false),
        CatalogedEndpoint("POST", "user/ai-vision/jobs", AREA_AI, safeToProbe = false),

        // Not in the published contract. Listed so the prober can answer the question the contract
        // could not, which is how both known dead endpoints would have been caught on day one.
        CatalogedEndpoint("GET", "user/signals", AREA_SIGNALS),
        CatalogedEndpoint("GET", "user/signals/execution/connections", AREA_EXECUTION),
        CatalogedEndpoint("GET", "user/signals/execution/executions", AREA_EXECUTION),
        CatalogedEndpoint("GET", "user/market-intelligence", AREA_NEWS),
        CatalogedEndpoint("POST", "user/ai/assistant/messages", AREA_AI, safeToProbe = false),
    )

    /**
     * TradeYar, which has not published its route table to this side yet.
     *
     * Its own summary says the mobile surface lives under `api/mobile/v1` and the older one under
     * `api/user/v1`, while the app currently builds every crypto call from CoinePro-FX's paths and
     * only swaps the base URL. So this list is what the app *sends today*, not what TradeYar
     * answers — and probing it is expected to fail almost completely until the contract arrives.
     * Leaving it accurate rather than aspirational is the point: the prober's job is to show the
     * gap, not to hide it behind paths nobody has confirmed.
     */
    private val tradeYar: List<CatalogedEndpoint> = coineProFx.map { endpoint ->
        endpoint.copy(area = endpoint.area)
    }

    private const val AREA_AUTH = "auth"
    private const val AREA_ACCOUNT = "account"
    private const val AREA_HOME = "home"
    private const val AREA_NOTIFICATIONS = "notifications"
    private const val AREA_ALERTS = "alerts"
    private const val AREA_MARKET = "market"
    private const val AREA_AI = "ai"
    private const val AREA_SIGNALS = "signals"
    private const val AREA_EXECUTION = "execution"
    private const val AREA_NEWS = "news"
}

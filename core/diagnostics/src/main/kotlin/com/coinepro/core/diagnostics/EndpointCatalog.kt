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

        CatalogedEndpoint("POST", "user/ai/chat", AREA_AI, safeToProbe = false),

        // Where the signal list actually lives. Under /public rather than /user, and still behind
        // VIP — `showcase` is the one that is not, and it serves a closed signal on purpose.
        CatalogedEndpoint("GET", "public/signals/active", AREA_SIGNALS),
        CatalogedEndpoint("GET", "public/signals/recent", AREA_SIGNALS),

        // Listed although the server has confirmed all four are absent. The panel's job is to say
        // which of the app's calls reach something, and a row that is missing from the list cannot
        // report 404 — which is how these went unnoticed in the first place.
        CatalogedEndpoint("GET", "user/signals", AREA_SIGNALS),
        CatalogedEndpoint("GET", "user/signals/execution/connections", AREA_EXECUTION),
        CatalogedEndpoint("GET", "user/signals/execution/executions", AREA_EXECUTION),
        CatalogedEndpoint("GET", "user/market-intelligence", AREA_NEWS),
    )

    /**
     * TradeYar, from its published contract.
     *
     * Every path carries the full `api/mobile/v1` prefix, and that is the whole story of this list:
     * the app's crypto gateways still build CoinePro-FX's `user/…` paths against TradeYar's base
     * URL, so every crypto call currently reaches nothing. Probing this catalogue against the app's
     * own client is what makes that visible — the rows below are what TradeYar serves, and until
     * the gateways are rewritten the app is asking for something else entirely.
     *
     * Three of them differ beyond the path and are the dangerous ones, because they answer rather
     * than 404: the venue read is a single object where the app expects a list, executing a signal
     * moves the id from the path into the body, and the briefing replaces market-intelligence with
     * a different shape and a 204.
     */
    private val tradeYar: List<CatalogedEndpoint> = listOf(
        CatalogedEndpoint("GET", "api/mobile/v1/auth/methods", AREA_AUTH, requiresAuth = false),
        CatalogedEndpoint("POST", "api/mobile/v1/auth/login", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "api/mobile/v1/auth/register/start", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "api/mobile/v1/auth/register/verify", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "api/mobile/v1/auth/password/forgot", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "api/mobile/v1/auth/password/reset", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "api/mobile/v1/auth/google", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "api/mobile/v1/auth/refresh", AREA_AUTH, requiresAuth = false, safeToProbe = false),
        CatalogedEndpoint("POST", "api/mobile/v1/auth/logout", AREA_AUTH, safeToProbe = false),
        CatalogedEndpoint("GET", "api/mobile/v1/me", AREA_AUTH),

        CatalogedEndpoint("GET", "api/mobile/v1/kyc", AREA_ACCOUNT),
        CatalogedEndpoint("POST", "api/mobile/v1/kyc/level1", AREA_ACCOUNT, safeToProbe = false),
        CatalogedEndpoint("GET", "api/mobile/v1/briefing", AREA_HOME),
        CatalogedEndpoint("GET", "api/mobile/v1/portfolio", AREA_HOME),

        CatalogedEndpoint("GET", "api/mobile/v1/ws/snapshot", AREA_MARKET),
        CatalogedEndpoint("GET", "api/mobile/v1/market-intelligence", AREA_NEWS),
        CatalogedEndpoint("GET", "api/mobile/v1/signals", AREA_SIGNALS),

        CatalogedEndpoint("GET", "api/mobile/v1/executions", AREA_EXECUTION),
        CatalogedEndpoint("POST", "api/mobile/v1/executions", AREA_EXECUTION, safeToProbe = false),
        CatalogedEndpoint("GET", "api/mobile/v1/venues/lbank", AREA_EXECUTION),
        CatalogedEndpoint("POST", "api/mobile/v1/venues/lbank", AREA_EXECUTION, safeToProbe = false),

        CatalogedEndpoint("GET", "api/mobile/v1/alerts", AREA_ALERTS),
        CatalogedEndpoint("POST", "api/mobile/v1/alerts", AREA_ALERTS, safeToProbe = false),
        CatalogedEndpoint("GET", "api/mobile/v1/notifications", AREA_NOTIFICATIONS),
        CatalogedEndpoint("POST", "api/mobile/v1/notifications/read", AREA_NOTIFICATIONS, safeToProbe = false),
        CatalogedEndpoint("GET", "api/mobile/v1/push/preferences", AREA_NOTIFICATIONS),
        CatalogedEndpoint("POST", "api/mobile/v1/push/devices", AREA_NOTIFICATIONS, safeToProbe = false),

        CatalogedEndpoint("GET", "api/mobile/v1/ai/quota", AREA_AI),
        CatalogedEndpoint("POST", "api/mobile/v1/ai/generate", AREA_AI, safeToProbe = false),
        CatalogedEndpoint("POST", "api/mobile/v1/ai/vision/jobs", AREA_AI, safeToProbe = false),
    )

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

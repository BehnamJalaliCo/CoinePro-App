package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform

/**
 * Everything the control hub shows and every lever it offers.
 *
 * Pure data on purpose. The hub reaches into sessions, the market feed, push, the exchange venue,
 * caches and both servers' capability flags — and if this module took those controllers as
 * dependencies, `core:diagnostics` would end up depending on nearly the whole app, and every
 * feature would gain a reason to import the diagnostics module back. So the app layer, which
 * already holds all of them, assembles this and hands it over.
 *
 * The consequence worth naming: the hub cannot invent state. It shows what the app layer could
 * observe at that moment, and where the app could observe nothing, the field is null and the hub
 * draws a dash rather than a confident-looking default.
 */

/** How a subsystem is doing, in the only four grades a colour can carry honestly. */
enum class HubTone { GOOD, WARN, BAD, IDLE }

enum class HubSection { SESSION, FEED, PUSH, VENUE, ROUTES, REQUESTS }

/** One tile in the overview grid: a subsystem, its current reading, and how alarming it is. */
data class HubTile(
    val section: HubSection,
    val value: String,
    val tone: HubTone,
)

data class SessionRow(
    val platform: MarketPlatform,
    val signedIn: Boolean,
    /** Server-supplied or app-owned detail — a plan name, a revalidation notice, or null. */
    val detail: String? = null,
)

data class FeedStatus(
    val tone: HubTone,
    /** Already-resolved copy: the feed's own state strings are owned by the market-data layer. */
    val label: String,
    val subscribedSymbols: Int = 0,
    val cacheAgeLabel: String? = null,
)

/**
 * Whether a notification can be delivered, which needs both halves to be true.
 *
 * [serverEnabled] is what `/auth/methods` reported. It is separate from the Android permission
 * because the two fail differently and the fix differs: a denied permission is the reader's to
 * change, a server with push switched off is not. Null means the app has not asked the server yet.
 */
data class PushStatus(
    val permission: PushPermission,
    val serverEnabled: Boolean?,
    val tokenHint: String = ABSENT,
    val newSignals: Boolean = true,
    val signalUpdates: Boolean = true,
    val priceAlerts: Boolean = true,
) {
    val deliverable: Boolean get() = permission == PushPermission.GRANTED && serverEnabled == true
}

enum class PushPermission { NOT_CONFIGURED, NOT_REQUIRED, AVAILABLE, DENIED, GRANTED }

/**
 * The exchange or broker link for the platform on screen.
 *
 * [configured] and [connected] are deliberately two fields rather than one status. Credentials
 * stored on the server is not the same fact as the venue having verified them, and the product's
 * whole execution safety rests on never collapsing the two.
 */
data class VenueStatus(
    val name: String,
    val configured: Boolean,
    val connected: Boolean,
) {
    val tone: HubTone get() = when {
        connected -> HubTone.GOOD
        configured -> HubTone.WARN
        else -> HubTone.IDLE
    }
}

/**
 * What one server says it has switched on, read from its own capability endpoint.
 *
 * Every field is nullable because "not asked yet" and "the server said no" are different, and a
 * hub that showed an unasked capability as off would be reporting a server's answer it never heard.
 */
data class ServerCapabilities(
    val emailPassword: Boolean? = null,
    val google: Boolean? = null,
    val telegram: Boolean? = null,
    val push: Boolean? = null,
    val chartVision: Boolean? = null,
    val symbolCount: Int? = null,
)

data class Appearance(val languageTag: String)

data class ControlHub(
    val sessions: List<SessionRow> = emptyList(),
    val feed: FeedStatus? = null,
    val push: PushStatus? = null,
    val venue: VenueStatus? = null,
    val capabilities: Map<MarketPlatform, ServerCapabilities> = emptyMap(),
    val appearance: Appearance? = null,
)

/**
 * Every action the hub can take.
 *
 * Gathered into one object rather than twenty parameters so that adding a lever is one edit in
 * three places instead of a signature change rippling through the screen, its preview and its
 * render test. Each defaults to doing nothing, so a caller that cannot offer a lever — a render
 * test, a build where a subsystem is absent — simply leaves it out and the control is drawn
 * disabled rather than drawn and dead.
 */
data class HubActions(
    val onSelectPlatform: (MarketPlatform) -> Unit = {},
    val onSignOut: (MarketPlatform) -> Unit = {},
    val onSignOutEverywhere: () -> Unit = {},
    val onRestartFeed: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onClearMarketCache: () -> Unit = {},
    val onRequestPushPermission: () -> Unit = {},
    val onOpenPushSettings: () -> Unit = {},
    val onReRegisterPushToken: () -> Unit = {},
    val onSetPushPreference: (PushPreferenceKey, Boolean) -> Unit = { _, _ -> },
    val onSetLanguage: (String) -> Unit = {},
    val onProbe: (MarketPlatform) -> Unit = {},
    val onRefreshCapabilities: (MarketPlatform) -> Unit = {},
    val onToggleFailuresOnly: () -> Unit = {},
    val onClearRequests: () -> Unit = {},
)

enum class PushPreferenceKey { NEW_SIGNALS, SIGNAL_UPDATES, PRICE_ALERTS }

/**
 * The overview grid, derived rather than supplied.
 *
 * Building it here keeps one rule enforceable: a tile's colour and the section it summarises can
 * never disagree, because both read the same field. A hub whose green tile sat above a red section
 * would be worse than no tile at all.
 */
fun AdminUiState.hubTiles(hub: ControlHub): List<HubTile> {
    val missing = panels[selected]?.probes.orEmpty().count { it.outcome == ProbeOutcome.NOT_FOUND }
    val failed = requests.count { it.platform == selected && it.failed }
    val session = hub.sessions.firstOrNull { it.platform == selected }

    return listOf(
        HubTile(
            section = HubSection.SESSION,
            value = session?.let { if (it.signedIn) HUB_ON else HUB_OFF } ?: ABSENT,
            tone = when (session?.signedIn) {
                true -> HubTone.GOOD
                false -> HubTone.IDLE
                null -> HubTone.IDLE
            },
        ),
        HubTile(HubSection.FEED, hub.feed?.label ?: ABSENT, hub.feed?.tone ?: HubTone.IDLE),
        HubTile(
            section = HubSection.PUSH,
            value = hub.push?.let { if (it.deliverable) HUB_ON else HUB_OFF } ?: ABSENT,
            tone = when {
                hub.push == null -> HubTone.IDLE
                hub.push.deliverable -> HubTone.GOOD
                hub.push.permission == PushPermission.DENIED -> HubTone.WARN
                else -> HubTone.IDLE
            },
        ),
        HubTile(
            section = HubSection.VENUE,
            value = hub.venue?.let { if (it.connected) HUB_ON else HUB_OFF } ?: ABSENT,
            tone = hub.venue?.tone ?: HubTone.IDLE,
        ),
        HubTile(
            section = HubSection.ROUTES,
            value = missing.toString(),
            tone = if (missing > 0) HubTone.BAD else HubTone.GOOD,
        ),
        HubTile(
            section = HubSection.REQUESTS,
            value = failed.toString(),
            tone = if (failed > 0) HubTone.WARN else HubTone.GOOD,
        ),
    )
}

/**
 * Sentinels the UI swaps for translated words.
 *
 * Kept out of the tile values as raw copy because this module cannot reach string resources, and a
 * hub that shipped English "on" into a Persian screen would be the same bug as showing pydantic's
 * error text.
 */
const val HUB_ON: String = "on"
const val HUB_OFF: String = "off"

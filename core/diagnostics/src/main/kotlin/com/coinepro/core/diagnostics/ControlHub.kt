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

/**
 * What the panel can see, all of it supplied by the app layer.
 *
 * ### Language used to be here, and is not
 *
 * There was an `appearance` field carrying the app's language, drawn as a two-segment control on
 * this screen. It was wrong twice over. It is the *reader's* setting, not an operator's — nobody
 * diagnoses anything by switching the app to English — and putting it behind an admin door meant
 * the one control every reader might genuinely want was the one control no reader could reach. It
 * now lives on the profile screen, where a setting belongs, and it is deliberately gone from here
 * rather than duplicated: two places to change one thing is how the two end up disagreeing.
 */
data class ControlHub(
    val sessions: List<SessionRow> = emptyList(),
    val feed: FeedStatus? = null,
    val push: PushStatus? = null,
    val venue: VenueStatus? = null,
    val capabilities: Map<MarketPlatform, ServerCapabilities> = emptyMap(),
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
    val onProbe: (MarketPlatform) -> Unit = {},
    val onRefreshCapabilities: (MarketPlatform) -> Unit = {},
    val onToggleFailuresOnly: () -> Unit = {},
    val onClearRequests: () -> Unit = {},

    /* ------------------------------------------------------------------ the door */

    /**
     * One unlock attempt. Returns whether it opened, so the screen can clear the field it typed in.
     *
     * Refusing by default matters: a caller that forgets to wire this up gets a panel that never
     * opens, rather than one that opens to anybody.
     */
    val onUnlock: (String, String) -> Boolean = { _, _ -> false },
    val onCredentialEdited: () -> Unit = {},
    /** Closes it again. A door that only opens is a door in name. */
    val onLock: () -> Unit = {},

    /* ------------------------------------------------------------- the log itself */

    val onShowSection: (AdminSection) -> Unit = {},
    val onSetMinimumLevel: (LogLevel) -> Unit = {},
    val onToggleTag: (LogTag) -> Unit = {},
    val onSetQuery: (String) -> Unit = {},
    val onSetWindow: (LogWindow) -> Unit = {},
    val onClearFilter: () -> Unit = {},
    /** Opens one entry to show its fields, or closes the one already open. */
    val onExpandEntry: (Long) -> Unit = {},
    /** Wipes the ring and the file together — see [AppLog.clear] for why both. */
    val onClearLog: () -> Unit = {},
    /** How much the app writes: TRACE to reproduce something, WARN to leave it running. */
    val onSetVerbosity: (LogLevel) -> Unit = {},
    val onClearCrash: () -> Unit = {},
    /**
     * The handset reading and the last crash, handed up from the screen when the panel opens.
     *
     * Pushed in rather than read here because both need a `Context`, and both are read once per
     * visit rather than watched: a crash file cannot change while the app that would write it is
     * the one on screen, and a memory reading taken when the panel opened is the reading the
     * operator is looking at.
     */
    val onObserveInstall: (DeviceReport, Crash?) -> Unit = { _, _ -> },
    /** What the export did, recorded in the log so the file names its own export. */
    val onExported: (ExportOutcome) -> Unit = {},
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
    // Unexplained failures only. A 401 while signed out is the server working correctly, and a
    // tile that counted those was red on every install that had not signed in yet — which is what
    // made the whole panel read as broken the moment it opened. See [findings].
    val failed = requests.count { it.platform == selected && it.failed && it.status !in EXPECTED }
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

/**
 * The two statuses that are not faults.
 *
 * A refusal proves a server is there and listening, and while signed out it is the normal answer to
 * every authenticated route in both catalogues. Counting them was what made the panel open onto its
 * own error state on an install nobody had signed into yet.
 */
private val EXPECTED = setOf(401, 403)

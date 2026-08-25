package com.coinepro.core.copytrade

import java.time.Instant

/**
 * Copy trading is what CoinePro-FX has instead of order execution, and the difference is not
 * cosmetic.
 *
 * Under execution the reader sends one order per signal and the app can say what happened to it.
 * Under copy trading they link a broker account once, a service trades it, and the reader's whole
 * relationship with it is a switch and a window: is it on, is the terminal alive, what is open right
 * now, and — the part nobody else could answer — why did the last signal not open on *my* account.
 *
 * That last question is why [CopyTradeStatus.events] exists at the same level as the positions
 * rather than buried in a support channel. A copy-trading account that silently takes none of the
 * trades looks identical to a quiet market from the outside, and readers spent days waiting on it.
 */
data class CopyTradeStatus(
    /** The linked broker account, or null when nothing has been linked yet. */
    val account: CopyAccount?,
    val preferences: CopyPreferences,
    /** What the service's own account is holding — the book being copied from. */
    val master: CopyBook,
    /** What this reader's account is holding as a result. */
    val mirrored: List<CopyPosition>,
    /**
     * Whether the reader's terminal is reporting live, as the server words it ("live"), or null when
     * nothing has reported at all. Null is not an error: it means no terminal has checked in.
     */
    val mode: String?,
    /**
     * The terminal is logged into a different account than the linked one.
     *
     * The server withholds the positions when this is true, and it is right to: they belong to
     * whatever account the terminal actually opened, so showing them here would attribute someone
     * else's trades — or a demo's — to the reader's balance. Nothing on screen may imply the copy is
     * working while this is set.
     */
    val accountMismatch: Boolean,
    /** The account number the terminal is actually on, when it differs. Shown to explain the above. */
    val liveAccount: String?,
    val events: List<CopyExecutionEvent>,
    /** Set only when the reader's terminal slot is in trouble; absent while it is healthy. */
    val slotState: CopySlotState?,
)

/**
 * The linked broker account as the server currently sees it.
 *
 * Every figure here is nullable on purpose. They come from a terminal that may not have checked in
 * for hours, and a balance the app invented — or carried over from an earlier reading — would be a
 * number someone might act on. Absent is shown as absent.
 */
data class CopyAccount(
    val broker: String?,
    val server: String?,
    /** Masked by the server; the app never receives or stores the full login. */
    val loginMasked: String?,
    /** The server's own status word, shown as written — only the broker knows why it is not connected. */
    val status: String?,
    /** The server's explanation when the link is unhealthy. Persian, and rendered verbatim. */
    val lastError: String?,
    /** Whether the terminal has checked in recently enough for the rest of this to mean anything. */
    val alive: Boolean,
    val balance: Double?,
    val equity: Double?,
    val marginLevel: Double?,
    val floatingPnl: Double?,
    val openCount: Int,
    val currency: String?,
    val lastSeen: Instant?,
)

/** One open position, on either side of the copy. */
data class CopyPosition(
    val symbol: String,
    /** The broker's own word for the side; not translated, since only it knows what it opened. */
    val direction: String,
    val lots: Double,
    val profit: Double,
    val stopLoss: Double? = null,
    /** Which signal this position came from, when the terminal reported one. */
    val signalId: Long? = null,
)

/** The service's book: how many positions it holds, and which. */
data class CopyBook(
    val open: Int = 0,
    val positions: List<CopyPosition> = emptyList(),
)

/**
 * The copy settings, as stored.
 *
 * The app reads all of them and writes exactly one — [enabled]. The rest are risk parameters whose
 * bounds, interactions and consequences live in the web panel beside the explanations of what they
 * do; offering a lot-size stepper on a phone with none of that context would be handing someone a
 * dial without a gauge. They are read so the screen can state the terms the copy is running under.
 */
data class CopyPreferences(
    val enabled: Boolean = false,
    val riskMode: String? = null,
    val riskValue: Double? = null,
    val maxLot: Double? = null,
    val maxOpenTrades: Int? = null,
    /** Whether the reader's positions carry the signal's stop and targets. */
    val copyStopAndTargets: Boolean = true,
    val maxDailyLossPercent: Double? = null,
    /** Empty means every supported symbol, which is the server's own meaning for an empty list. */
    val symbols: List<String> = emptyList(),
)

/**
 * One thing the terminal did, or refused to do, with a signal.
 *
 * [message] arrives from the server already written in Persian and already carrying the technical
 * cause and the broker's return code. It is shown exactly as sent: the app cannot improve on an
 * explanation assembled from a broker error it never saw, and rewording it would lose the one
 * detail support needs.
 */
data class CopyExecutionEvent(
    val at: Instant?,
    val signalId: Long?,
    /** The machine-readable event code, e.g. `open_failed`. Kept for support, not for display. */
    val code: String,
    /** How it ended, in the server's words. Drives the colour, nothing else. */
    val outcome: String?,
    val symbol: String?,
    val message: String,
)

/**
 * The reader's terminal slot is in a state worth interrupting them about.
 *
 * The server sends this only when something is actually wrong — a healthy or recovered slot sends
 * nothing — so its presence is the signal and there is no "everything is fine" case to render.
 */
data class CopySlotState(
    val state: String,
    /** Server text, in Persian, shown verbatim. */
    val message: String?,
    val at: Instant?,
)

/**
 * What the copy-trading screen is showing right now.
 *
 * [membershipRequired] is separated from [error] for the reason the whole subscription model turns
 * on: a refusal for want of a subscription is not a fault. It has an answer the reader can act on,
 * and rendering it as a failure would send someone to support over a product boundary.
 */
data class CopyTradeState(
    val loading: Boolean = false,
    val status: CopyTradeStatus? = null,
    /**
     * The platform has no copy-trading surface — TradeYar, which executes orders instead.
     *
     * A state and not an error, for the same reason as [com.coinepro.core.execution.ConnectionsState]
     * carries one: nothing went wrong and no retry will change it.
     */
    val unsupported: Boolean = false,
    val membershipRequired: Boolean = false,
    /** The server's own wording for the refusal, when it sent any. */
    val membershipMessage: String? = null,
    /** A write is in flight; the switch is held rather than allowed to bounce. */
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

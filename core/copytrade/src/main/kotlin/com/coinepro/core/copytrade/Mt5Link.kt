package com.coinepro.core.copytrade

/**
 * The MetaTrader 5 account link, reduced to what a connections surface has to draw.
 *
 * ### Why this lives here and not in the screen
 *
 * CoinePro-FX has no venue-connection route. What it has is the copy-trading link:
 * `POST user/account/link` with `{broker, server, login, password}`, `DELETE user/account`, and
 * `GET user/copy-status`, whose `account` object is the only place the server ever says what
 * happened to that login. So the MT5 connection *is* the copy-trade account link seen from a
 * different screen, and the mapping from a copy-trade state to a connection state is the one piece
 * of logic both screens would otherwise each invent.
 *
 * ### The distinction the stages exist to keep
 *
 * A linked account and a working account are not the same claim, and the server is careful to
 * separate them: `account.alive` says a terminal has checked in recently, `account.status` is the
 * broker-side word, and `account_mismatch` says the terminal is logged into somebody else's
 * account. Collapsing those into a boolean would let the screen print "connected" over a link that
 * has never once opened a trade — which is the failure readers spent days waiting on.
 */
enum class Mt5LinkStage {
    /** The platform has no copy-trading routes at all — TradeYar. Nothing to offer, and no error. */
    UNAVAILABLE,

    /**
     * The server refused the *read* for want of a subscription.
     *
     * `user/copy-status` sits behind `require_vip`; `user/account/link` does not — it is gated on a
     * verified email and an accepted disclaimer instead. So this is the one stage where an account
     * can still be linked while its state cannot be read, and the screen has to say exactly that
     * rather than pretending either half is missing.
     */
    LOCKED,

    /** Read succeeded and no account is linked. */
    NOT_LINKED,

    /** Linked, but no terminal has checked in yet. Setup done, connection unproven. */
    PENDING,

    /** Linked and the terminal is reporting live. */
    CONNECTED,

    /** Linked and the server is complaining — a broker-side error, or a terminal on another account. */
    ATTENTION,
}

/**
 * What the connections screen shows for MetaTrader 5.
 *
 * Every server-authored field is carried verbatim and nullable. [serverStatus] and [serverNote] are
 * the broker's and the server's own words — the app never saw the broker's refusal and cannot word
 * it better — and an absent one is shown as absent rather than filled in with a guess.
 */
data class Mt5Link(
    val stage: Mt5LinkStage,
    val broker: String? = null,
    val server: String? = null,
    /** Masked by the server. The app never receives the full login and never stores it. */
    val loginMasked: String? = null,
    /** The server's own status word for the link, e.g. `login_failed`. Shown as written. */
    val serverStatus: String? = null,
    /** The server's explanation, already in Persian, rendered verbatim. */
    val serverNote: String? = null,
    /** The account the terminal is really on, set only when it differs from the linked one. */
    val liveAccount: String? = null,
    /** A link or unlink is in flight. */
    val busy: Boolean = false,
    /** A read is in flight and nothing is known yet. */
    val loading: Boolean = false,
) {
    /** Whether an account is linked, whatever shape that link is in. */
    val linked: Boolean
        get() = stage == Mt5LinkStage.PENDING ||
            stage == Mt5LinkStage.CONNECTED ||
            stage == Mt5LinkStage.ATTENTION

    /**
     * Whether the form is worth drawing.
     *
     * Offered while [LOCKED][Mt5LinkStage.LOCKED] on purpose: the link route is not the route the
     * subscription gates, so a reader without one can still connect their account — they just
     * cannot yet see what became of it. Withholding the form there would be the app inventing a
     * gate the server does not have.
     */
    val canLink: Boolean
        get() = stage != Mt5LinkStage.UNAVAILABLE

    /**
     * Whether unlinking may be offered.
     *
     * Only where the app has actually read an account. Under [LOCKED][Mt5LinkStage.LOCKED] nothing
     * is known, and a disconnect button over an unknown account is a destructive action offered on
     * a guess.
     */
    val canUnlink: Boolean
        get() = linked
}

/**
 * Reads a copy-trading state as a connection.
 *
 * Precedence is the point. Absence outranks refusal, refusal outranks emptiness, and a server
 * complaint outranks a healthy-looking terminal — so nothing on the screen can say "connected"
 * while the server is holding a reason it is not.
 */
fun CopyTradeState.toMt5Link(): Mt5Link {
    if (unsupported) return Mt5Link(stage = Mt5LinkStage.UNAVAILABLE)
    if (membershipRequired) {
        return Mt5Link(
            stage = Mt5LinkStage.LOCKED,
            serverNote = membershipMessage?.trim()?.takeIf(String::isNotEmpty),
            busy = saving,
            loading = loading,
        )
    }
    val snapshot = status
    val account = snapshot?.account
        ?: return Mt5Link(stage = Mt5LinkStage.NOT_LINKED, busy = saving, loading = loading)

    val mismatch = snapshot.accountMismatch
    val note = account.lastError
    return Mt5Link(
        stage = when {
            mismatch || note != null -> Mt5LinkStage.ATTENTION
            account.alive -> Mt5LinkStage.CONNECTED
            else -> Mt5LinkStage.PENDING
        },
        broker = account.broker,
        server = account.server,
        loginMasked = account.loginMasked,
        serverStatus = account.status,
        serverNote = note,
        liveAccount = if (mismatch) snapshot.liveAccount else null,
        busy = saving,
        loading = loading,
    )
}

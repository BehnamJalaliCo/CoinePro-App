package com.coinepro.core.membership

/**
 * The five things that have to be true before a reader sees a signal, in the order they happen.
 *
 * They are the published terms' own list — §۶-۲ — plus the one the terms take for granted because
 * they are read on a web page: being signed in to the app at all. The order is not a presentation
 * choice. [EXCHANGE_ACCOUNT] is first because it is the only one that cannot be undone: an account
 * opened without Pro Chart's link is not a sub-account in the exchange's own system and no later
 * step can rescue it, so a reader who does the others first has lost the work.
 */
enum class MembershipStep {
    /** Signed in to Pro Chart. */
    SIGN_IN,

    /** Registered at LBank or Ourbit **through the referral link**. */
    EXCHANGE_ACCOUNT,

    /** Funded to the threshold the server publishes. */
    FUNDING,

    /** The exchange UID handed to Pro Chart. */
    UID,

    /** The exchange's own API asked, and its answer recorded. */
    VERIFICATION,
}

/**
 * How far along one step is — and, just as importantly, when the answer is not known.
 *
 * [UNKNOWN] is the state this type exists for. The server verifies a reader's exchange account only
 * once a UID has been submitted and checked, so for most of the journey it genuinely cannot say
 * whether somebody has registered or funded anything. The old screen had no way to express that and
 * so said nothing at all; a checklist that guesses instead would be worse, because a reader shown
 * «انجام شد» beside a step they never took stops looking for the step that is actually blocking
 * them.
 */
enum class MembershipStepState {
    /** The server's answer implies this is behind the reader. Never a client guess. */
    DONE,

    /** The one thing to do now. Exactly one step carries it, or none when access is open. */
    CURRENT,

    /** Not reached yet, and not reachable until the current one is done. */
    AHEAD,

    /** The server has not said, and this build will not guess on its behalf. */
    UNKNOWN,

    /** The server says this one failed in a way the next step cannot repair. */
    BLOCKED,
}

/**
 * The single thing the screen should offer, chosen from the server's answer.
 *
 * There is deliberately no "nothing to do" member. A locked screen whose action is absent is the
 * screen this whole type was written to replace — every state below, including the ones nobody
 * planned for, ends at something a reader can press.
 */
enum class MembershipAction {
    /** Hand over to the exchange's registration page, carrying the referral link. */
    OPEN_EXCHANGE,

    /** Also an exchange hop, but for a reader who already has a verified sub-account. */
    FUND_ACCOUNT,

    /** The UID form, which is in the app. */
    SUBMIT_UID,

    /** The server is working. Nothing for the reader to do but come back. */
    WAIT,

    /** The read failed, or the state is one this build has never heard of. Ask again. */
    RETRY,

    /** Access is open. The surface that was locked should now load. */
    RELOAD,
}

/**
 * Where the reader stands, ready to draw.
 *
 * [serverMessage] is the server's own sentence and the only prose about *this account* the screen
 * may print — the same rule `MembershipScreen` has always followed, hoisted here so the signals
 * screen cannot accidentally invent its own. [note] is not carried at all: it is triage, the server
 * asked for it never to be shown, and a field that is not here cannot be rendered by mistake.
 */
data class MembershipJourney(
    val steps: List<MembershipStepProgress>,
    val action: MembershipAction,
    /** Whether the UID form should be drawn. The server's instruction, not the action's opinion. */
    val uidFormOffered: Boolean,
    val status: MembershipStatus?,
    val serverMessage: String?,
    val uidOnFile: String?,
    val exchangeOnFile: String?,
    /**
     * The first read has not come back yet.
     *
     * Distinct from an unreadable status and the distinction is not academic: both draw every step
     * as unknown, but one of them is a screen that failed and the other is a screen that has been
     * open for three hundred milliseconds. Telling a reader «سرور چیزی نگفته» before the request
     * has finished is a lie that corrects itself, which is the kind readers remember.
     */
    val loading: Boolean = false,
) {
    val current: MembershipStep?
        get() = steps.firstOrNull { it.state == MembershipStepState.CURRENT }?.step

    /** True where nothing about this account could be read. The screen must then claim nothing. */
    val statusKnown: Boolean get() = status != null && status != MembershipStatus.UNKNOWN
}

data class MembershipStepProgress(val step: MembershipStep, val state: MembershipStepState)

/**
 * Reads a controller state into a journey.
 *
 * Every [MembershipStepState.DONE] below is a deduction from something the server said, and each
 * one is worth naming because the temptation to guess is real:
 *
 * * `approved` and `pending_deposit` both mean the exchange confirmed the account is a Pro Chart
 *   sub-account — that is what the verifier checks first — so [MembershipStep.EXCHANGE_ACCOUNT] is
 *   genuinely done in both. `pending_deposit` additionally means the balance was read and fell
 *   short, which is why funding becomes the current step rather than an unknown one.
 * * A `uid` echoed back is the server confirming it holds one. Its absence is not proof of the
 *   opposite, so a missing UID under a status that implies one leaves the step unknown rather than
 *   undone.
 * * `verifying` says a check is running, which means a UID reached the server — but not yet
 *   anything about the account behind it. So the two exchange steps stay unknown there, and a
 *   reader in that state is told the check is running rather than that they have passed it.
 *
 * [MembershipStep.SIGN_IN] is the one step read from the caller rather than the payload, and it is
 * sound: these routes carry the reader's bearer token and answer about their account, so a status —
 * of any kind — is itself proof of a session. An unreadable status leaves even that unknown.
 */
fun membershipJourney(state: MembershipUiState): MembershipJourney = when (state) {
    is MembershipUiState.Ready -> state.state.toJourney()
    is MembershipUiState.Unavailable -> unreadable(state.message)
    MembershipUiState.Idle, MembershipUiState.Loading -> unreadable(message = null, loading = true)
}

private fun MembershipState.toJourney(): MembershipJourney {
    val done = MembershipStepState.DONE
    val current = MembershipStepState.CURRENT
    val ahead = MembershipStepState.AHEAD
    val unknown = MembershipStepState.UNKNOWN
    val blocked = MembershipStepState.BLOCKED
    // A UID echoed back is the server saying it holds one. Its absence proves nothing — the field
    // is optional on the wire — so a missing UID is unknown rather than undone.
    val uidHeld = if (uid != null) done else unknown

    val steps = when (status) {
        MembershipStatus.APPROVED -> progress(done, done, done, done, done)
        MembershipStatus.PENDING_DEPOSIT -> progress(done, done, current, uidHeld, ahead)
        MembershipStatus.VERIFYING, MembershipStatus.PENDING ->
            progress(done, unknown, unknown, uidHeld, current)
        MembershipStatus.AWAITING_UID -> progress(done, unknown, unknown, current, ahead)
        // Not recoverable by going forward: the exchange says this account was never linked, and
        // no deposit or resubmission of the same UID changes that. The step is marked blocked and
        // the ones behind it are put back to "not yet", because for the account that will actually
        // work — a new one, opened through the link — none of them has happened.
        MembershipStatus.REJECTED_REFERRAL -> progress(done, blocked, ahead, ahead, ahead)
        // The check failed rather than refused. Nothing was learned about the account, so nothing
        // is marked either way, and the current step is the check itself — which is what the retry
        // re-runs.
        MembershipStatus.ERROR -> progress(done, unknown, unknown, uidHeld, current)
        MembershipStatus.UNKNOWN -> progress(done, unknown, unknown, unknown, unknown)
    }

    return MembershipJourney(
        steps = steps,
        action = action(),
        uidFormOffered = awaitsReader,
        status = status,
        serverMessage = messageFa,
        uidOnFile = uid,
        exchangeOnFile = exchange,
    )
}

/**
 * The five states in one call, named rather than positional.
 *
 * A `listOf(done, done, current, ...)` zipped against the enum reads the same and breaks silently
 * the day a sixth step is added: the zip truncates, and one step quietly stops being drawn.
 */
private fun progress(
    signIn: MembershipStepState,
    exchangeAccount: MembershipStepState,
    funding: MembershipStepState,
    uid: MembershipStepState,
    verification: MembershipStepState,
): List<MembershipStepProgress> = listOf(
    MembershipStepProgress(MembershipStep.SIGN_IN, signIn),
    MembershipStepProgress(MembershipStep.EXCHANGE_ACCOUNT, exchangeAccount),
    MembershipStepProgress(MembershipStep.FUNDING, funding),
    MembershipStepProgress(MembershipStep.UID, uid),
    MembershipStepProgress(MembershipStep.VERIFICATION, verification),
)

/**
 * Which single action to offer.
 *
 * Status wins over `next_step` for the two states whose meaning is unambiguous, and `next_step`
 * wins everywhere else. That order matters for `pending_deposit`: it is the reader's turn, so a
 * server that marks it `uid` would otherwise put a UID form in front of somebody whose UID is
 * already on file and whose actual problem is a balance.
 *
 * The final branch is [MembershipAction.RETRY] rather than nothing, and that is the point of the
 * whole enum — a status added server-side after this build shipped lands here, and a reader who
 * meets it still has a button that does something real.
 */
private fun MembershipState.action(): MembershipAction = when {
    status == MembershipStatus.APPROVED -> MembershipAction.RELOAD
    status == MembershipStatus.PENDING_DEPOSIT -> MembershipAction.FUND_ACCOUNT
    status == MembershipStatus.REJECTED_REFERRAL ->
        if (canResubmit) MembershipAction.SUBMIT_UID else MembershipAction.OPEN_EXCHANGE
    awaitsReader -> MembershipAction.SUBMIT_UID
    nextStep == "wait" -> MembershipAction.WAIT
    status == MembershipStatus.VERIFYING || status == MembershipStatus.PENDING -> MembershipAction.WAIT
    else -> MembershipAction.RETRY
}

/**
 * The journey when the status could not be read at all.
 *
 * Every step is unknown, including the sign-in the reader has plainly completed — because what
 * failed may well *be* the session, and a screen that draws «انجام شد» beside sign-in while the
 * server is refusing the reader's token is arguing with the only evidence it has.
 */
private fun unreadable(message: String?, loading: Boolean = false) = MembershipJourney(
    steps = MembershipStep.entries.map { MembershipStepProgress(it, MembershipStepState.UNKNOWN) },
    action = MembershipAction.RETRY,
    uidFormOffered = false,
    status = null,
    serverMessage = message,
    uidOnFile = null,
    exchangeOnFile = null,
    loading = loading,
)

package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform

/**
 * The one thing an operator opening this panel wants answered: is anything actually wrong.
 *
 * ### The bug this file exists to end
 *
 * The panel used to greet everyone with a red verdict, and it was right almost never. It counted a
 * failure as any recorded call outside the 2xx–3xx range, across both platforms, for the whole
 * session — so a `401` on `user/me` while signed out, which is the server behaving exactly as
 * designed, made the screen say the install was broken. An operator cannot debug anything from a
 * panel that is already claiming a fault before they have done anything, and after the second time
 * they stop reading the verdict at all. Which is worse than having none.
 *
 * So a finding here is something that is genuinely, specifically wrong, and every kind below can be
 * named in one sentence and acted on:
 *
 *  * a route the app calls that the server does not serve — the app is asking at a wrong address;
 *  * a 5xx — the server broke, and the operator's next move is the server's own log;
 *  * a call that never reached a status at all — a timeout, DNS, TLS, no route;
 *  * a platform whose base URL this build was never given — the app cannot talk to it at all;
 *  * an uncaught exception since the last time the crash file was cleared;
 *  * errors written to the log in the last hour, which is the catch-all for everything the four
 *    above do not name.
 *
 * And what is deliberately *not* a finding: a 401 or a 403. Those prove a server is there and
 * listening, and while signed out they are the normal answer to every authenticated route. The
 * distinction is the same one [ProbeOutcome.UNAUTHORIZED] draws, and it is the whole difference
 * between a verdict worth reading and a red light that is always on.
 */
enum class FindingKind {
    CRASH,
    ROUTE_MISSING,
    SERVER_ERROR,
    TRANSPORT_FAILURE,
    GATEWAY_UNCONFIGURED,
    ERRORS_LOGGED,
}

/** One thing that is wrong, and how many times. The UI supplies the sentence. */
data class HealthFinding(val kind: FindingKind, val count: Int)

/**
 * How alarming a finding is.
 *
 * A missing route and an unconfigured gateway are BAD because the app cannot work at all until
 * somebody changes something; a 5xx or a timeout is WARN because it may already have passed.
 */
fun FindingKind.tone(): HubTone = when (this) {
    FindingKind.CRASH, FindingKind.ROUTE_MISSING, FindingKind.GATEWAY_UNCONFIGURED -> HubTone.BAD
    FindingKind.SERVER_ERROR, FindingKind.TRANSPORT_FAILURE, FindingKind.ERRORS_LOGGED -> HubTone.WARN
}

/**
 * Worst first, which is not the tone's own order.
 *
 * [HubTone] is declared GOOD, WARN, BAD because that is the order a legend reads in; severity runs
 * the other way. Sorting findings by the enum's ordinal put the warnings above the failures — the
 * verdict line would then name a slow request while a dead route sat under it.
 */
private fun FindingKind.severity(): Int = when (tone()) {
    HubTone.BAD -> 0
    HubTone.WARN -> 1
    else -> 2
}

/**
 * Everything wrong with the platform on screen, worst first.
 *
 * Scoped to the selected platform for the same reason every other section is: the two backends are
 * separate systems, and "three failures" that turns out to be one server having a bad afternoon
 * while the other is fine is a sentence that has to say which.
 */
fun AdminUiState.findings(now: Long): List<HealthFinding> {
    val panel = panels[selected]
    val platformRequests = requests.filter { it.platform == selected }

    val missingRoutes = panel?.probes.orEmpty().count { it.outcome == ProbeOutcome.NOT_FOUND }
    val serverErrors = platformRequests.count { (it.status ?: 0) in 500..599 }
    val transport = platformRequests.count { it.status == null && it.failure != null }
    val unconfigured = if (panel?.build?.configured == false) 1 else 0
    val loggedErrors = log.count {
        it.level == LogLevel.ERROR && now - it.epochMillis <= ERROR_WINDOW_MILLIS
    }

    return listOf(
        HealthFinding(FindingKind.CRASH, if (crash != null) 1 else 0),
        HealthFinding(FindingKind.GATEWAY_UNCONFIGURED, unconfigured),
        HealthFinding(FindingKind.ROUTE_MISSING, missingRoutes),
        HealthFinding(FindingKind.SERVER_ERROR, serverErrors),
        HealthFinding(FindingKind.TRANSPORT_FAILURE, transport),
        HealthFinding(FindingKind.ERRORS_LOGGED, loggedErrors),
    ).filter { it.count > 0 }.sortedBy { it.kind.severity() }
}

/** The colour of the verdict line: the worst finding's, or good when there is nothing to say. */
fun List<HealthFinding>.verdictTone(): HubTone =
    minByOrNull { it.kind.severity() }?.kind?.tone() ?: HubTone.GOOD

/**
 * Failed calls, grouped.
 *
 * A raw list of the last two hundred requests was the second thing the owner named as useless, and
 * the criticism was fair: when a route is broken it fails on every retry, so the list becomes forty
 * copies of one fact and the *other* two failures are somewhere below the fold. Grouping by what
 * actually distinguishes a failure — the platform, the verb, the path and what came back — turns
 * forty rows into one row saying "forty times, last one a minute ago", and puts the second problem
 * back on screen.
 *
 * The path is grouped as it was recorded, without collapsing numeric segments. `signals/detail/1`
 * and `signals/detail/2` failing are two facts when only one id is broken and one fact when the
 * route is; guessing which would sometimes hide the more interesting of the two.
 */
data class FailureGroup(
    val platform: MarketPlatform?,
    val method: String,
    val path: String,
    val status: Int?,
    val failure: String?,
    val count: Int,
    val lastAtEpochMillis: Long,
    val slowestMillis: Long,
) {
    /** Whether this group is one of the ones [findings] would call a fault. See the note there. */
    val expected: Boolean
        get() = status == 401 || status == 403
}

fun List<RecordedRequest>.failureGroups(): List<FailureGroup> = filter(RecordedRequest::failed)
    .groupBy { listOf(it.platform, it.method, it.path, it.status, it.failure) }
    .map { (_, group) ->
        val first = group.first()
        FailureGroup(
            platform = first.platform,
            method = first.method,
            path = first.path,
            status = first.status,
            failure = first.failure,
            count = group.size,
            lastAtEpochMillis = group.maxOf(RecordedRequest::elapsedRealtimeMillis),
            slowestMillis = group.maxOf(RecordedRequest::durationMillis),
        )
    }
    // Unexplained failures above the ones a signed-out session explains, then by how often.
    .sortedWith(compareBy<FailureGroup> { it.expected }.thenByDescending { it.count })

/**
 * One hour.
 *
 * Long enough that an operator who reproduced a fault, put the phone down and went to find the
 * panel still sees it; short enough that yesterday's transient does not colour today's verdict.
 */
private const val ERROR_WINDOW_MILLIS = 60L * 60L * 1_000L

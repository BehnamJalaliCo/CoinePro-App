package com.coinepro.core.aisignal

/**
 * Why an AI request did not produce a setup.
 *
 * One entry per thing that can actually go wrong on this screen, because "something failed" is not
 * an explanation and the reader's next action is different in every case: a refused request wants a
 * changed control, an exhausted quota wants tomorrow, an expired job wants a retry, and a dead
 * connection wants nothing at all except being told the truth.
 *
 * The screen owns the Persian sentence for each. This enum owns only the distinction — a controller
 * in an Android library module cannot reach `stringResource`, and a controller that writes its own
 * copy is how the English sentences got in front of a Persian reader in the first place.
 */
enum class AiSignalFailure {
    /**
     * 422 — the server would not accept the request as shaped.
     *
     * The one that shipped. `AiSignalCreateJobDto` sent a `risk` field neither contract lists and
     * spelled `risk_percent` as `risk_pct`, and every press of «ساخت ستاپ» came back 422 and was
     * rendered to the reader as the English exception text.
     */
    REQUEST_REJECTED,

    /** 403 — this account does not hold the entitlement the AI endpoints sit behind. */
    ENTITLEMENT_REQUIRED,

    /** 429, or a quota response with nothing left. Says when it comes back, when the server said. */
    QUOTA_EXHAUSTED,

    /** 410 — the job outlived its TTL on the server before it was polled. */
    JOB_EXPIRED,

    /** The job reported `failed`, and the server gave no reader-facing reason of its own. */
    GENERATION_FAILED,

    /** `done`, but the result was missing a level the setup cannot be read without. */
    RESULT_UNUSABLE,

    /** The symbol in the box is not a ticker. Caught before the request leaves the phone. */
    SYMBOL_UNSUPPORTED,

    /** The quota call itself did not come back. The allowance is unknown, not spent. */
    QUOTA_UNAVAILABLE,

    /** Nothing reached a verdict — no route, no network, a timeout. */
    NETWORK_UNAVAILABLE,
}

/**
 * A failure as the screen will show it.
 *
 * [serverText] is the server's own reader-facing sentence when it sent one, and it takes precedence
 * over this app's copy: both backends write their refusals in Persian, and restating the server's
 * reason in our own words is the one thing this app is built not to do. `core:network`'s `ApiErrors`
 * has already decided whether a body's text was written for a reader or is a FastAPI English
 * default, so what arrives here is never `"Field required"`.
 *
 * [serverCode] is machine-readable and never drawn. It is carried so a failure can be quoted in a
 * bug report and so a later branch can be written against a code rather than against a sentence.
 */
data class AiSignalError(
    val reason: AiSignalFailure,
    val serverText: String? = null,
    val serverCode: String? = null,
    /**
     * When the allowance refills, on a [AiSignalFailure.QUOTA_EXHAUSTED], as the server wrote it.
     *
     * "You have none left" is a dead end; "you have none left until tomorrow at nine" is an answer.
     */
    val resetAt: String? = null,
    /**
     * The request field the server blamed, on a [AiSignalFailure.REQUEST_REJECTED].
     *
     * Worth more than the sentence: "the server refused this" sends a reader back to guess which of
     * nine controls was wrong, and "the server refused this and named `timeframe`" does not.
     */
    val serverField: String? = null,
) {
    companion object {
        fun of(reason: AiSignalFailure): AiSignalError = AiSignalError(reason)
    }
}

package com.coinepro.feature.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coinepro.core.aisignal.AiSignalError
import com.coinepro.core.aisignal.AiSignalFailure
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.parseWireInstant

/**
 * Every way this screen can fail, said in Persian.
 *
 * ### What this replaces
 *
 * «ساخت ستاپ» answered every press with **"AI Signal request was rejected by server validation"** —
 * an English sentence, authored in a gateway, carried on an exception's `message`, handed to
 * `UiMessage.fromServer` which could not tell it from something a server had written, and rendered
 * verbatim to a Persian reader. The sentence was also useless in either language: it named no
 * parameter and suggested no action.
 *
 * ### The rule
 *
 * Where the **server** wrote a reader-facing sentence, that sentence wins and is shown exactly as
 * it arrived. Both backends write their refusals in Persian, and restating a provider's reason in
 * our own words is the one thing this app is built not to do; `core:network`'s `ApiErrors` has
 * already separated those from FastAPI's English defaults, so nothing here has to guess.
 *
 * Where the server wrote nothing, the app speaks — and says what happened *and* what to do about
 * it, because a refusal a reader cannot act on is only marginally better than no refusal at all.
 */
@Composable
internal fun AiSignalError.sentence(): String {
    serverText?.takeIf { it.isNotBlank() }?.let { return it }
    return when (reason) {
        AiSignalFailure.REQUEST_REJECTED -> serverField?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.ai_error_rejected_field, it) }
            ?: stringResource(R.string.ai_error_rejected)
        AiSignalFailure.ENTITLEMENT_REQUIRED -> stringResource(R.string.ai_error_entitlement)
        AiSignalFailure.QUOTA_EXHAUSTED -> resetMoment()
            ?.let { stringResource(R.string.ai_error_quota_reset, it) }
            ?: stringResource(R.string.ai_error_quota)
        AiSignalFailure.JOB_EXPIRED -> stringResource(R.string.ai_error_expired)
        AiSignalFailure.GENERATION_FAILED -> stringResource(R.string.ai_error_failed)
        AiSignalFailure.RESULT_UNUSABLE -> stringResource(R.string.ai_error_unusable)
        AiSignalFailure.SYMBOL_UNSUPPORTED -> stringResource(R.string.ai_error_symbol)
        AiSignalFailure.QUOTA_UNAVAILABLE -> stringResource(R.string.ai_error_quota_unknown)
        AiSignalFailure.NETWORK_UNAVAILABLE -> stringResource(R.string.ai_error_network)
    }
}

/**
 * The machine code under the sentence, when the server sent one.
 *
 * Not decoration and not a leak: it is the only string a reader can quote into a support message
 * that means the same thing on the server's side. Null when there is nothing to quote, so no line
 * reading "Error code: —" ever appears.
 */
internal fun AiSignalError.codeLine(): String? = serverCode?.trim()?.takeIf { it.isNotEmpty() }

/** The refill moment as Persian prose, or null when the server did not say or sent nonsense. */
@Composable
internal fun AiSignalError.resetMoment(): String? = resetAt.asMoment()

/**
 * An ISO instant as a Persian date and time.
 *
 * Shown rather than a bare countdown because a quota that refills "in ۱۴ ساعت" is a number a reader
 * has to do arithmetic on, and one that refills «فردا ۰۳:۳۰» is an answer. Unparseable text is
 * dropped rather than shown raw — an ISO string in front of a reader is a leak, not information.
 */
internal fun String?.asMoment(): String? {
    val instant = parseWireInstant(this) ?: return null
    return PersianDateTime.moment(instant)
}

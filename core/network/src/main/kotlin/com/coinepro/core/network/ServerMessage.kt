package com.coinepro.core.network

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import java.io.IOException
import retrofit2.HttpException

/**
 * Turns a thrown request into something a reader can be shown.
 *
 * The reason this exists rather than each controller reaching for `error.message`: on an
 * [HttpException] that property is the status line — "HTTP 404 Not Found" — in English, and every
 * controller that pushed it into state was putting an English protocol string in front of a Persian
 * reader and calling it an explanation. Both servers write real refusals in Persian; [ApiErrors]
 * knows where in each of their four envelope shapes to find one.
 *
 * A connection that never reached a verdict has no server text by definition, so it falls to
 * [fallback] — the app's own copy, which is the one case where the app is entitled to speak.
 */
fun Throwable.toServerMessage(fallback: MessageKey): UiMessage = when (this) {
    is HttpException -> UiMessage.fromServer(ApiErrors.from(this).message, fallback)
    is IOException -> UiMessage.Local(fallback)
    else -> UiMessage.Local(fallback)
}

/**
 * The server's own refusal, or null where it did not give one.
 *
 * For the screens that already carry their own Persian copy for the null case. Null is the honest
 * answer for a request that never reached a verdict, and for one whose only text was the status
 * line — `error.message` on an [HttpException] is "HTTP 404 Not Found", which is not an
 * explanation, is not in the reader's language, and reads as though the service said it.
 */
fun Throwable.serverTextOrNull(): String? =
    (this as? HttpException)?.let { ApiErrors.from(it).message?.trim()?.takeIf(String::isNotEmpty) }

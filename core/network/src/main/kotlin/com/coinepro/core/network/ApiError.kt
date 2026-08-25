package com.coinepro.core.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import retrofit2.HttpException

/**
 * What a failed CoinePro request said, once every error shape the two backends use is accounted for.
 *
 * The split between [message] and [untranslatedDetail] is the load-bearing part. The app's rule is
 * that server text is shown to the reader exactly as written — which is right when the server wrote
 * it for a reader, and wrong when it did not. Both backends carry endpoints that predate the mobile
 * API and answer with FastAPI's English defaults: `"Field required"`, `"Unauthorized"`. Rendering
 * those verbatim to a Persian reader is not honesty, it is a language failure wearing honesty's
 * clothes. So text believed to be reader-facing lands in [message], and text known to be
 * diagnostic lands in [untranslatedDetail], which callers log and never draw.
 */
data class ApiError(
    /** Stable and machine-readable — `TYR-017`, `invalid_credentials`. Branch on this, never on text. */
    val code: String? = null,
    /** Reader-facing text in the reader's language, or null when the server had none. */
    val message: String? = null,
    /** From a body's `retry_after`. Some deployments send only the `Retry-After` header. */
    val retryAfterSeconds: Int? = null,
    /**
     * The request field at fault, when the server named one.
     *
     * Worth more than the sentence on a form: it lets the app mark the offending input in place,
     * which is the difference between "something was wrong" and "this box was wrong".
     */
    val field: String? = null,
    /** Every field a validation failure named, when it named several. */
    val fields: List<String> = emptyList(),
    /** Quote it in a bug report; it appears in the server's logs. */
    val traceId: String? = null,
    /** English server text — for a log line or the diagnostics panel. Never for the reader. */
    val untranslatedDetail: String? = null,
)

/**
 * Reads the error body of a CoinePro response.
 *
 * Four shapes are live across the two backends, and none is going away — each belongs to a surface
 * the web portal or the Telegram bot already depends on:
 *
 * 1. RFC 7807 — TradeYar's mobile routes. `code`, `detail`, `field` and `trace_id` at the top
 *    level; `title` is English and for logs.
 * 2. `{"detail": {"code", "message"}}` — CoinePro-FX's mobile routes.
 * 3. `{"detail": "<string>"}` — the older panel routes on both servers. The string is Persian on
 *    one and English on the other, so it is classified rather than assumed.
 * 4. `{"detail": [ … ]}` — FastAPI validation, an array whose `msg` values are English defaults
 *    and whose `loc` names the field.
 *
 * Anything unparseable yields an [ApiError] with no message rather than an exception or an invented
 * one. A failure to read the reason is not itself a reason.
 */
object ApiErrors {
    fun from(error: HttpException): ApiError = parse(runCatching {
        error.response()?.errorBody()?.string()
    }.getOrNull())

    fun parse(body: String?): ApiError {
        if (body.isNullOrBlank()) return ApiError()
        return runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            val detail = root.get("detail")

            val code = root.string("code")
            val traceId = root.string("trace_id")
            val field = root.string("field")
            val retry = root.retryAfter()
            val title = root.string("title")

            when {
                detail == null || detail.isJsonNull -> ApiError(
                    code = code,
                    // A 7807 body without `detail` has only its English title left, which belongs
                    // in a log rather than on screen.
                    untranslatedDetail = title,
                    retryAfterSeconds = retry,
                    field = field,
                    traceId = traceId,
                )

                detail.isJsonPrimitive -> detail.asString.classify().let { (readable, diagnostic) ->
                    ApiError(code, readable, retry, field, emptyList(), traceId, diagnostic ?: title)
                }

                detail.isJsonObject -> detail.asJsonObject.let { fields ->
                    val (readable, diagnostic) = (fields.string("message") ?: fields.string("msg"))
                        .orEmpty()
                        .classify()
                    ApiError(
                        code = fields.string("code") ?: code,
                        message = readable,
                        retryAfterSeconds = fields.retryAfter() ?: retry,
                        field = fields.string("field") ?: field,
                        traceId = fields.string("trace_id") ?: traceId,
                        untranslatedDetail = diagnostic,
                    )
                }

                detail.isJsonArray -> detail.asJsonArray.let { array ->
                    val (readable, diagnostic) = array.messages().orEmpty().classify()
                    val named = array.fieldNames()
                    ApiError(
                        code = code,
                        message = readable,
                        retryAfterSeconds = retry,
                        field = field ?: named.firstOrNull(),
                        fields = named,
                        traceId = traceId,
                        untranslatedDetail = diagnostic,
                    )
                }

                else -> ApiError(code, null, retry, field, emptyList(), traceId, title)
            }
        }.getOrDefault(ApiError())
    }

    /**
     * Decides whether a server string was written for this app's reader.
     *
     * The test is whether it contains Arabic-script letters, which is what Persian copy is written
     * in and what neither pydantic nor Starlette ever produces. It is a heuristic and it is meant
     * to be: the alternative is trusting a per-endpoint promise that the older surfaces cannot make,
     * and the failure mode of guessing wrong is a generic Persian sentence instead of a specific
     * English one — which is the better of the two errors for the reader this app is built for.
     *
     * @return the text as reader-facing copy, or as diagnostic text; never both.
     */
    private fun String.classify(): Pair<String?, String?> {
        val text = trim().takeIf { it.isNotEmpty() } ?: return null to null
        val readable = text.any { it in '؀'..'ۿ' }
        return if (readable) text to null else null to text
    }

    /**
     * Joins a validation array's messages.
     *
     * Every complaint is kept rather than only the first: a form refused for two reasons that
     * reports one sends the reader back a second time for something they were never told about.
     */
    private fun JsonArray.messages(): String? = mapNotNull { element ->
        when {
            element.isJsonPrimitive -> element.asString.takeIf(String::isNotBlank)
            element.isJsonObject -> element.asJsonObject.let { it.string("msg") ?: it.string("message") }
            else -> null
        }
    }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")

    /**
     * The field each validation entry blamed, from the tail of its `loc`.
     *
     * `loc` is `["body", "email"]` — the head names where the value came from and the rest names
     * the field. The tail is what a form can highlight.
     */
    private fun JsonArray.fieldNames(): List<String> = mapNotNull { element ->
        element.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("loc")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.lastOrNull()
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf(String::isNotBlank)
    }.distinct()

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)

    private fun JsonObject.retryAfter(): Int? = get("retry_after")
        ?.takeIf { it.isJsonPrimitive }
        ?.runCatching { asInt }
        ?.getOrNull()
        ?.takeIf { it >= 0 }
}

package com.coinepro.core.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import retrofit2.HttpException

/**
 * What a failed CoinePro request said, once every error shape the two backends use is accounted for.
 *
 * [code] is what the client branches on; [message] is text written for a reader and shown exactly as
 * received. Never branch on [message] — it is the half most likely to be reworded.
 */
data class ApiError(
    val code: String? = null,
    val message: String? = null,
    /** From a 429 body's `retry_after`, in seconds. Some deployments send only the header instead. */
    val retryAfterSeconds: Int? = null,
)

/**
 * Reads the error body of a CoinePro response.
 *
 * Four shapes are live across the two backends, and this is not a mess awaiting a cleanup — each
 * belongs to a surface that predates the mobile API and cannot be changed without breaking the web
 * clients already using it:
 *
 * 1. `{"detail": {"code", "message"}}` — CoinePro-FX's mobile routes.
 * 2. `{"detail": "<Persian string>"}` — the panel routes both apps still call.
 * 3. RFC 7807 — TradeYar's mobile routes, where `detail` is the human sentence and `code` sits
 *    beside it at the top level rather than inside it.
 * 4. `{"detail": [ … ]}` — FastAPI's validation errors, where `detail` is an array of objects each
 *    carrying a `msg`.
 *
 * Handling only one of them is not a partial success: on the surfaces it misses, the reader gets a
 * blank refusal while the server's own explanation is discarded a layer below.
 *
 * Anything unparseable yields an [ApiError] with no message rather than an exception or a
 * fabricated one. A failure to read the reason is not itself a reason.
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

            // Read at the top level first: RFC 7807 puts the machine-readable code beside `detail`,
            // not inside it, and the retry hint can sit at either level depending on the backend.
            val topLevelCode = root.string("code")
            val topLevelRetry = root.retryAfter()

            when {
                detail == null || detail.isJsonNull ->
                    // A 7807 body with no `detail` still carries `title`, which is the sentence a
                    // reader is meant to see.
                    ApiError(topLevelCode, root.string("title"), topLevelRetry)

                detail.isJsonPrimitive ->
                    ApiError(topLevelCode, detail.asString.takeIf(String::isNotBlank), topLevelRetry)

                detail.isJsonObject -> detail.asJsonObject.let { fields ->
                    ApiError(
                        code = fields.string("code") ?: topLevelCode,
                        message = fields.string("message") ?: fields.string("msg"),
                        retryAfterSeconds = fields.retryAfter() ?: topLevelRetry,
                    )
                }

                detail.isJsonArray ->
                    ApiError(topLevelCode, detail.asJsonArray.messages(), topLevelRetry)

                else -> ApiError(topLevelCode, null, topLevelRetry)
            }
        }.getOrDefault(ApiError())
    }

    /**
     * Joins a validation array into one sentence.
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

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)

    private fun JsonObject.retryAfter(): Int? = get("retry_after")
        ?.takeIf { it.isJsonPrimitive }
        ?.runCatching { asInt }
        ?.getOrNull()
        ?.takeIf { it >= 0 }
}

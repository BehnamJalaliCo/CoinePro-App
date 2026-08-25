package com.coinepro.core.network

import com.google.gson.JsonParser
import retrofit2.HttpException

/**
 * What a failed CoinePro request said, once both of the server's error shapes are accounted for.
 *
 * [code] is what the client branches on; [message] is Persian text written for a reader and shown
 * exactly as received. Never branch on [message] — it is the half most likely to be reworded.
 */
data class ApiError(
    val code: String? = null,
    val message: String? = null,
    /** From the 429 body's `retry_after`, in seconds. */
    val retryAfterSeconds: Int? = null,
)

/**
 * Reads the error body of a CoinePro response.
 *
 * The backend carries two shapes at once, and this is not a transitional accident to be cleaned up
 * later — the mobile routes return `{"detail": {"code", "message"}}` while the panel routes the app
 * also calls still return `{"detail": "<Persian string>"}`. Handling only the structured one would
 * silently drop the message on exactly the endpoints that have been in production longest.
 *
 * Anything unparseable yields an [ApiError] with no message rather than an exception or a
 * fabricated one. A failure to read the reason is not itself a reason, and the caller's generic
 * line is the honest thing to show.
 */
object ApiErrors {
    fun from(error: HttpException): ApiError = parse(runCatching {
        error.response()?.errorBody()?.string()
    }.getOrNull())

    fun parse(body: String?): ApiError {
        if (body.isNullOrBlank()) return ApiError()
        return runCatching {
            val detail = JsonParser.parseString(body).asJsonObject.get("detail")
            when {
                detail == null || detail.isJsonNull -> ApiError()
                // The panel routes' shape: the whole reason is one already-translated string.
                detail.isJsonPrimitive -> ApiError(message = detail.asString.takeIf(String::isNotBlank))
                detail.isJsonObject -> detail.asJsonObject.let { fields ->
                    ApiError(
                        code = fields.string("code"),
                        message = fields.string("message"),
                        // Carried in the body here rather than in a Retry-After header, so a
                        // client that only reads the header sees no wait at all.
                        retryAfterSeconds = fields.get("retry_after")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.runCatching { asInt }
                            ?.getOrNull()
                            ?.takeIf { it >= 0 },
                    )
                }
                else -> ApiError()
            }
        }.getOrDefault(ApiError())
    }

    private fun com.google.gson.JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
}

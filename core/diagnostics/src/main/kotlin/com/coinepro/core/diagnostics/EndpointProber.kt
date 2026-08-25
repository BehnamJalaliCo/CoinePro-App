package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** What came back when one catalogued route was actually called. */
data class EndpointProbe(
    val endpoint: CatalogedEndpoint,
    val outcome: ProbeOutcome,
    val status: Int? = null,
    val durationMillis: Long = 0,
    val detail: String? = null,
)

enum class ProbeOutcome {
    /** The route exists and answered. */
    REACHED,

    /**
     * The route answered 401 or 403.
     *
     * This is a **success** for the prober's purpose: a refusal proves something is listening at
     * that address. It is the single most useful outcome when signed out, because it separates
     * "wrong path" from "not signed in" — the distinction that hid two dead endpoints for months.
     */
    UNAUTHORIZED,

    /** Nothing is served here. This is what a wrong path looks like. */
    NOT_FOUND,

    /** The route exists and something behind it broke. */
    SERVER_ERROR,

    /** The request never reached a verdict: no connectivity, a timeout, TLS. */
    UNREACHABLE,

    /** Not fired, because firing it would write, spend quota, or burn an attempt. */
    SKIPPED,
}

/**
 * Calls each catalogued route once and reports what answered.
 *
 * Only `GET` routes are fired, and only those marked safe. Everything else is listed as
 * [ProbeOutcome.SKIPPED] rather than hidden: a reader needs to see the whole surface to know what
 * was and was not checked, and a prober that quietly omitted half the routes would give the same
 * false confidence as no prober at all.
 *
 * The bearer token comes from the same source the real client uses, so a probe run while signed in
 * exercises the authenticated path. Signed out, the useful signal is
 * [ProbeOutcome.UNAUTHORIZED] versus [ProbeOutcome.NOT_FOUND] — the first means the address is
 * real, the second means it is not.
 */
class EndpointProber(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val platform: MarketPlatform,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun endpoints(): List<CatalogedEndpoint> = EndpointCatalog.forPlatform(platform)

    suspend fun probe(endpoint: CatalogedEndpoint): EndpointProbe = withContext(Dispatchers.IO) {
        if (!endpoint.safeToProbe || endpoint.method != "GET") {
            return@withContext EndpointProbe(endpoint, ProbeOutcome.SKIPPED)
        }

        val started = clock()
        val url = baseUrl.trimEnd('/') + "/" + endpoint.path.trimStart('/')

        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                EndpointProbe(
                    endpoint = endpoint,
                    outcome = response.code.toOutcome(),
                    status = response.code,
                    durationMillis = clock() - started,
                )
            }
        } catch (error: IOException) {
            EndpointProbe(
                endpoint = endpoint,
                outcome = ProbeOutcome.UNREACHABLE,
                durationMillis = clock() - started,
                // Class name only: an exception message can carry the full URL, and this is drawn
                // on screen next to everything else.
                detail = error::class.simpleName,
            )
        } catch (error: IllegalArgumentException) {
            // A base URL the build never configured produces a malformed URL rather than a request.
            EndpointProbe(endpoint, ProbeOutcome.UNREACHABLE, detail = "malformed-url")
        }
    }

    private fun Int.toOutcome(): ProbeOutcome = when (this) {
        401, 403 -> ProbeOutcome.UNAUTHORIZED
        404, 405 -> ProbeOutcome.NOT_FOUND
        in 500..599 -> ProbeOutcome.SERVER_ERROR
        else -> ProbeOutcome.REACHED
    }
}

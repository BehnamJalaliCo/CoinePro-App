package com.coinepro.core.watchlistsync

import com.coinepro.core.model.MarketPlatform
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Url

/**
 * The stored watchlist document, exactly as the route describes it.
 *
 * [version] is the server's counter. A `PUT` sends back the version the write was built on, and a
 * write built on a version that has since moved is refused — see [WatchlistSyncConflict].
 *
 * [updatedAtMs] is the **server's** clock at the last accepted write, and is the one timestamp in
 * this feature that two devices can agree on. It is carried for display and not used in the merge,
 * because the merge has to reason about individual lists and this stamp describes the whole
 * document.
 *
 * [maxBytes] comes back on every response, and nothing in this app hard-codes 64 KB as a result.
 * The cap is a server-side decision; a client constant for it would be a second copy that goes
 * wrong the day the server raises it, and it would go wrong in the direction of refusing to sync a
 * document the server would have accepted.
 */
data class WatchlistDocument(
    val version: Long,
    val payload: JsonObject,
    val updatedAtMs: Long? = null,
    val maxBytes: Int? = null,
) {
    companion object {
        /**
         * Reads a document out of a JSON object.
         *
         * One reader for both places a document arrives, and that is the point of it existing at
         * all: the happy path returns one, and so does the body of the `409`. A second reader for
         * the refusal path would be exercised only when two devices disagree — the rarest path in
         * the feature and the one that must not be the one that is wrong.
         *
         * A body with no readable `payload` object yields an empty payload rather than null. The
         * route answers a reader who has never synced with `payload: {}` and `version: 0`, so an
         * absent payload and an empty one mean the same thing and the caller should not have to
         * write the same `?: emptyObject()` at both call sites.
         */
        fun from(body: JsonObject?): WatchlistDocument? {
            if (body == null) return null
            val version = body.get(VERSION)
                ?.takeIf { it.isJsonPrimitive }
                ?.runCatching { asLong }
                ?.getOrNull()
                ?: return null
            return WatchlistDocument(
                version = version,
                payload = body.get(PAYLOAD)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject(),
                updatedAtMs = body.get(UPDATED_AT)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.runCatching { asLong }
                    ?.getOrNull(),
                maxBytes = body.get(MAX_BYTES)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.runCatching { asInt }
                    ?.getOrNull()
                    ?.takeIf { it > 0 },
            )
        }

        private const val VERSION = "version"
        private const val PAYLOAD = "payload"
        private const val UPDATED_AT = "updated_at_ms"
        private const val MAX_BYTES = "max_bytes"
    }
}

/**
 * This platform serves no watchlist document at all.
 *
 * Distinct from a failure, exactly as `ExecutionUnsupportedException` is: nothing went wrong,
 * retrying will not help, and the screen shows the control as absent rather than as broken. The
 * feature has to be *missing* on CoinePro-FX rather than present and failing — a sync button that
 * answers with an error every time is worse than no sync button, because the reader has to try it
 * to find out.
 */
class WatchlistSyncUnsupportedException : Exception("Watchlist sync is not available on this platform")

/**
 * The write was built on a document the server has since replaced.
 *
 * [current] is the whole document the server is holding *now*, lifted out of the body of the
 * `409`. That the refusal carries it is the reason this feature merges rather than overwrites: the
 * alternative shape of this API would force a second `GET` on a connection that has just proved
 * unreliable, and the honest thing to do on a connection like that is to give up and take the last
 * write. Because the document is already in hand, the caller merges and writes again.
 *
 * Null [current] means the refusal arrived without one — a proxy that rewrote the body, or a
 * deployment that has not shipped the behaviour yet. The caller re-reads in that case rather than
 * treating it as a lost cause, but it is the slow path and it is not the one the route promises.
 */
class WatchlistSyncConflict(val current: WatchlistDocument?) : Exception("Watchlist document has moved on")

/**
 * The document is larger than the server will store.
 *
 * [maxBytes] is what the server said the cap is, never a constant of ours. Nothing was written:
 * the route refuses the whole document rather than truncating it, which is the only safe answer —
 * a partially written watchlist is indistinguishable on the next read from a watchlist somebody
 * emptied.
 */
class WatchlistSyncTooLargeException(val maxBytes: Int?) : Exception("Watchlist document exceeds the server cap")

interface WatchlistSyncGateway {
    /** Whether this platform serves the route at all. False means the feature is absent, not broken. */
    val supported: Boolean

    /** The stored document. A reader who has never synced gets version zero and an empty payload. */
    suspend fun read(): WatchlistDocument

    /**
     * Stores [payload] against [version].
     *
     * @throws WatchlistSyncConflict when [version] is stale, carrying what the server now holds.
     * @throws WatchlistSyncTooLargeException when the document is over the cap. Nothing was written.
     */
    suspend fun write(version: Long, payload: JsonObject): WatchlistDocument
}

internal interface WatchlistSyncApi {
    /**
     * Deliberately typed as a raw [JsonObject] rather than as a data class.
     *
     * The payload is opaque to the server and is built and read by hand in [WatchlistPayload]; a
     * generated Gson mapping over it would apply this app's `LOWER_CASE_WITH_UNDERSCORES` field
     * policy to keys that are not fields, and would have to be unwrapped back into a `JsonObject`
     * immediately anyway. Reading the envelope by hand also means the `200` body and the `409`
     * body go through one reader — see [WatchlistDocument.from].
     */
    @GET
    suspend fun document(@Url path: String): JsonObject

    @PUT
    suspend fun putDocument(@Url path: String, @Body body: JsonObject): JsonObject
}

/**
 * Where the watchlist document lives — on both platforms now, at each one's own prefix.
 *
 * TradeYar mounts it at `api/mobile/v1/watchlists`, beside the notification and alert routes that
 * already sit under that prefix; CoinePro-FX at `user/mobile/watchlists`, beside the rest of its
 * app surface. The two prefixes are the servers' own and nothing here tries to reconcile them.
 *
 * The forex side used to be null, with a note saying the route was never built there. What that
 * cost was not a missing setting: this audience installs from outside Google Play, so a reinstall
 * is an ordinary event, and a forex reader's watchlist — several lists, colour flags, chosen
 * columns, all of it built by hand — lived on one handset and went with it. A crypto reader's did
 * not. The route is the same contract on both, down to the 409 that carries the winning document.
 *
 * Null is still a state this class can be in, for a build with no configured platform, and the
 * gateway still reports the feature absent rather than posting a reader's watchlist to an address
 * that answers 404 in wording that reads like an outage.
 */
internal class WatchlistSyncPaths(prefix: String) {
    val document = "$prefix/watchlists"

    companion object {
        fun of(platform: MarketPlatform): WatchlistSyncPaths? = when (platform) {
            MarketPlatform.TRADEYAR -> WatchlistSyncPaths("api/mobile/v1")
            MarketPlatform.COINEPRO_FX -> WatchlistSyncPaths("user/mobile")
        }
    }
}

class NetworkWatchlistSyncGateway private constructor(
    private val api: WatchlistSyncApi,
    private val paths: WatchlistSyncPaths?,
) : WatchlistSyncGateway {

    override val supported: Boolean get() = paths != null

    override suspend fun read(): WatchlistDocument = translate {
        val path = paths?.document ?: throw WatchlistSyncUnsupportedException()
        // A body this reader cannot make sense of is a document of version zero, not a crash. The
        // caller's next move either way is to merge against what it holds and write, and writing
        // against version zero is refused with a 409 that carries the real document — so the
        // unreadable case self-corrects on the very next request rather than needing its own path.
        WatchlistDocument.from(api.document(path)) ?: WatchlistDocument(version = 0L, payload = JsonObject())
    }

    override suspend fun write(version: Long, payload: JsonObject): WatchlistDocument = translate {
        val path = paths?.document ?: throw WatchlistSyncUnsupportedException()
        val body = JsonObject().apply {
            addProperty(VERSION_FIELD, version)
            add(PAYLOAD_FIELD, payload)
        }
        WatchlistDocument.from(api.putDocument(path, body))
            ?: WatchlistDocument(version = version + 1L, payload = payload)
    }

    /**
     * Turns the two refusals this route makes deliberately into types the caller can act on.
     *
     * Everything else is rethrown untouched. A 401 is a session problem and belongs to the session
     * layer; a 500 is an outage and the honest response to one is to keep the local watchlist and
     * try later. Naming them here would mean this file deciding what an outage means to a reader,
     * which is the controller's job and is done in one place for all of them.
     */
    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        when (error.code()) {
            409 -> throw WatchlistSyncConflict(conflictDocument(error))
            413 -> throw WatchlistSyncTooLargeException(capFrom(error))
            else -> throw error
        }
    }

    /**
     * The current document, out of the body of a `409`.
     *
     * Looked for at the top level and then inside `detail`, because TradeYar's mobile routes answer
     * in RFC 7807 — `code`, `detail`, `trace_id` — and a document delivered alongside a refusal
     * could reasonably be placed either way round. Reading both costs four lines and removes the
     * only reason this feature would ever need a second round trip; guessing one and being wrong
     * would silently downgrade every conflict to a re-fetch, on exactly the connections that
     * cannot afford one.
     */
    private fun conflictDocument(error: HttpException): WatchlistDocument? {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull() ?: return null
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        return WatchlistDocument.from(root)
            ?: WatchlistDocument.from(root.get("detail")?.takeIf { it.isJsonObject }?.asJsonObject)
    }

    /**
     * The cap the server named in its refusal.
     *
     * Read off the body rather than assumed, for the same reason `max_bytes` is carried on every
     * successful response: the number belongs to the server. When the refusal did not spell it out
     * this is null, and the reader is told the document is too large without being told a figure
     * this app made up.
     */
    private fun capFrom(error: HttpException): Int? {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull() ?: return null
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val direct = root.get("max_bytes") ?: root.get("detail")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("max_bytes")
        return direct?.takeIf { it.isJsonPrimitive }?.runCatching { asInt }?.getOrNull()?.takeIf { it > 0 }
    }

    companion object {
        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkWatchlistSyncGateway =
            create(retrofit.create(WatchlistSyncApi::class.java), platform)

        /**
         * The same gateway over a hand-built API.
         *
         * Internal, and it exists so that the two refusals this file translates — the `409` that
         * carries a document and the `413` that names a cap — are exercised against real
         * `HttpException`s rather than only through a fake of the interface. Those two branches are
         * the whole reason this class is more than three lines, and they are the branches a reader
         * only reaches when two of their devices disagree.
         */
        internal fun create(api: WatchlistSyncApi, platform: MarketPlatform?): NetworkWatchlistSyncGateway =
            NetworkWatchlistSyncGateway(
                api = api,
                // Null platform is the no-route case, which both backends have grown out of — see
                // [WatchlistSyncPaths] — and which a build with nothing configured is still in.
                paths = platform?.let(WatchlistSyncPaths::of),
            )

        private const val VERSION_FIELD = "version"
        private const val PAYLOAD_FIELD = "payload"
    }
}
